package io.github.pini236.skiapp.telemetry

import io.sentry.AsyncHttpTransportFactory
import io.sentry.DataCategory
import io.sentry.Hint
import io.sentry.ITransportFactory
import io.sentry.RequestDetails
import io.sentry.SentryEnvelope
import io.sentry.SentryItemType
import io.sentry.SentryOptions
import io.sentry.transport.ITransport
import io.sentry.transport.RateLimiter
import io.sentry.hints.Retryable
import io.sentry.hints.SubmissionResult
import io.sentry.util.HintUtils
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import javax.net.ssl.HttpsURLConnection

/**
 * Only replay uses this bounded, best-effort sender. It has no disk cache or retry: consent revoked while offline
 * must not revive a video on the next launch. Ordinary crash reports keep Sentry's supported cached transport.
 * These SDK envelope types and serializer signatures are pinned to Sentry 8.59.0 and covered by unit tests.
 */
internal class ReplayTransportFactory(private val privacy: ReplayPrivacy) : ITransportFactory {
    override fun create(options: SentryOptions, requestDetails: RequestDetails): ITransport =
        ReplayTransport(options, requestDetails, privacy, AsyncHttpTransportFactory().create(options, requestDetails))
}

internal class ReplayTransport(
    private val options: SentryOptions,
    private val details: RequestDetails,
    private val privacy: ReplayPrivacy,
    private val crashes: ITransport,
) : ITransport {
    private val executor = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(8),
        { work -> Thread(work, "sentry-replay-privacy").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())

    override fun send(envelope: SentryEnvelope, hint: Hint) {
        if (!containsReplay(envelope)) { crashes.send(envelope, hint); return }
        // A cached envelope from an earlier process has no permission in this process, even after opt-in.
        val id = envelope.header.eventId?.toString()
        if (!privacy.maySend(id)) { discard(envelope, hint); return }
        try { executor.execute { try { upload(envelope, id) } finally { discard(envelope, hint) } } }
        catch (_: RejectedExecutionException) { discard(envelope, hint) }
    }

    private fun upload(envelope: SentryEnvelope, id: String?) {
        if (!privacy.maySend(id) || crashes.rateLimiter?.isActiveForCategory(DataCategory.Replay) == true) return
        val connection = details.url.openConnection() as? HttpsURLConnection ?: return
        val cancel: () -> Unit = { connection.disconnect() }
        if (!privacy.beginSend(id, cancel)) { connection.disconnect(); return }
        try {
            details.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = minOf(options.connectionTimeoutMillis, 5000)
            connection.readTimeout = minOf(options.readTimeoutMillis, 5000)
            connection.setRequestProperty("Content-Encoding", "gzip")
            connection.setRequestProperty("Content-Type", "application/x-sentry-envelope")
            connection.setRequestProperty("Connection", "close")
            // Use the platform's normal TLS/hostname verification. Never install a permissive socket factory.
            if (!privacy.maySend(id)) return
            connection.outputStream.use { output ->
                if (!privacy.maySend(id)) return
                GZIPOutputStream(output).use { options.serializer.serialize(envelope, it) }
            }
            // Best effort: no retry and no retained replay payload, including rate limits/network failures.
            val status = connection.responseCode
            crashes.rateLimiter?.updateRetryAfterLimits(connection.getHeaderField("X-Sentry-Rate-Limits"), connection.getHeaderField("Retry-After"), status)
        } catch (_: Exception) {
            // Do not log request headers, video contents or authentication. Crashes use the normal SDK transport.
        } finally {
            privacy.endSend(cancel)
            connection.disconnect()
        }
    }

    private fun discard(envelope: SentryEnvelope, hint: Hint) {
        options.envelopeDiskCache.discard(envelope)
        // Acknowledge intentional best-effort drop; cached/outbox replay must not be scheduled again.
        HintUtils.runIfHasType(hint, Retryable::class.java) { it.setRetry(false) }
        HintUtils.runIfHasType(hint, SubmissionResult::class.java) { it.setResult(true) }
    }

    override fun flush(timeoutMillis: Long) {
        crashes.flush(timeoutMillis)
        if (timeoutMillis > 0 && !executor.isShutdown) runCatching { executor.submit {}.get(timeoutMillis, TimeUnit.MILLISECONDS) }
    }
    override fun getRateLimiter(): RateLimiter? = crashes.rateLimiter
    override fun isHealthy(): Boolean = crashes.isHealthy
    override fun close() = close(false)
    override fun close(isRestarting: Boolean) { executor.shutdownNow(); crashes.close(isRestarting) }

    companion object {
        internal fun containsReplay(envelope: SentryEnvelope): Boolean = envelope.items.any {
            it.header.type in setOf(SentryItemType.ReplayEvent, SentryItemType.ReplayRecording, SentryItemType.ReplayVideo)
        }
    }
}
