package com.xelilovth.nota.data.online

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

object NewPipeDownloader : Downloader() {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val connection = (URL(request.url()).openConnection() as HttpURLConnection).apply {
            requestMethod = request.httpMethod()
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            request.headers().forEach { (key, values) ->
                if (values.isNotEmpty()) {
                    setRequestProperty(key, values.joinToString(", "))
                }
            }
            val payload = request.dataToSend()
            if (payload != null) {
                doOutput = true
                outputStream.use { it.write(payload) }
            }
        }

        return try {
            val statusCode = connection.responseCode
            val message = connection.responseMessage.orEmpty()
            if (statusCode == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            }
            val stream = if (statusCode in 200..399) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val responseHeaders = connection.headerFields
                .filterKeys { it != null }
                .mapKeys { it.key!! }
            Response(
                statusCode,
                message,
                responseHeaders,
                body,
                connection.url.toString(),
            )
        } finally {
            connection.disconnect()
        }
    }
}
