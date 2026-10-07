package io.github.pini236.skiapp.telemetry

import io.sentry.Hint
import io.sentry.RequestDetails
import io.sentry.SentryEnvelope
import io.sentry.SentryItemType
import io.sentry.SentryOptions
import io.sentry.protocol.SentryId
import io.sentry.transport.ITransport
import io.sentry.transport.RateLimiter
import org.junit.Assert.*
import org.junit.Test

class ReplayTransportTest {
    private class CrashTransport : ITransport {
        val envelopes = ArrayList<SentryEnvelope>()
        override fun send(envelope: SentryEnvelope, hint: Hint) { envelopes.add(envelope) }
        override fun flush(timeoutMillis: Long) {}
        override fun getRateLimiter(): RateLimiter? = null
        override fun close() {}
        override fun close(isRestarting: Boolean) {}
    }
    private fun envelope(vararg types: SentryItemType): SentryEnvelope {
        // Parse actual wire envelopes through the pinned SDK; item constructors are package-private.
        val wire = buildString {
            append("{\"event_id\":\"${SentryId()}\"}\n")
            types.forEach { append("{\"type\":\"${it.itemType}\",\"length\":2}\n{}\n") }
        }
        return SentryOptions.empty().envelopeReader.read(wire.byteInputStream())!!
    }
    @Test fun allPinnedReplayPayloadTypesAreDetectedIncludingMixedEnvelopes() {
        listOf(SentryItemType.ReplayEvent, SentryItemType.ReplayRecording, SentryItemType.ReplayVideo).forEach {
            assertTrue(ReplayTransport.containsReplay(envelope(it)))
            assertTrue(ReplayTransport.containsReplay(envelope(SentryItemType.Event, it)))
        }
        assertFalse(ReplayTransport.containsReplay(envelope(SentryItemType.Event)))
    }
    @Test fun deniedOrRecoveredReplayCannotReachTheOrdinaryCachedTransport() {
        val normal = CrashTransport()
        val sender = ReplayTransport(SentryOptions.empty(), RequestDetails("https://example.invalid/", emptyMap()), ReplayPrivacy(), normal)
        try {
            sender.send(envelope(SentryItemType.ReplayVideo), Hint())
            sender.send(envelope(SentryItemType.Event, SentryItemType.ReplayRecording), Hint())
            assertTrue(normal.envelopes.isEmpty())
            val crash = envelope(SentryItemType.Event)
            sender.send(crash, Hint())
            assertEquals(listOf(crash), normal.envelopes)
        } finally { sender.close() }
    }
}
