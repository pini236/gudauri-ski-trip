package io.github.pini236.skiapp.server

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** One HTTP exchange with the server. The real one is [UrlTransport]; the unit tests pass a fake. */
fun interface Transport {
    /** Throws [Offline] when there is no answer at all (no signal, a timeout); any HTTP status is an answer. */
    fun send(req: Request): Response
}

data class Request(val method: String, val url: String, val headers: Map<String, String>, val body: String? = null)

data class Response(val status: Int, val body: String) {
    val ok: Boolean get() = status in 200..299
}

/** No answer from the server: the caller keeps what it has on the phone and tries again later. */
class Offline(cause: Throwable) : IOException(cause)

/**
 * Android's own HTTP client, so no library is added for a few small JSON requests. On a phone it also does PATCH
 * (the JVM's does not; the live test uses java.net.http instead).
 */
object UrlTransport : Transport {
    override fun send(req: Request): Response {
        val c = try {
            URL(req.url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw Offline(e)
        }
        try {
            c.requestMethod = req.method
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            c.useCaches = false
            req.headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (req.body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(req.body.toByteArray()) }
            }
            val status = c.responseCode
            val text = (if (status >= 400) c.errorStream else c.inputStream)?.bufferedReader()?.use { it.readText() } ?: ""
            return Response(status, text)
        } catch (e: IOException) {
            throw Offline(e)
        } finally {
            c.disconnect()
        }
    }
}
