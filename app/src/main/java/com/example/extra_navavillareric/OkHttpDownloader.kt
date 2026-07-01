package com.example.extra_navavillareric

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.extractor.downloader.Request as NPRequest
import org.schabi.newpipe.extractor.downloader.Response as NPResponse

// Implementación mínima del Downloader que NewPipeExtractor necesita para hacer sus
// propias peticiones HTTP (búsqueda y metadata de streams), respaldada por OkHttp.
class OkHttpDownloader private constructor(private val client: OkHttpClient) : Downloader() {

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

        @Volatile
        private var instance: OkHttpDownloader? = null

        fun getInstance(): OkHttpDownloader = instance ?: synchronized(this) {
            instance ?: OkHttpDownloader(
                OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build()
            ).also { instance = it }
        }
    }

    override fun execute(request: NPRequest): NPResponse {
        val dataToSend = request.dataToSend()
        val body = dataToSend?.toRequestBody()

        val requestBuilder = okhttp3.Request.Builder()
            .method(request.httpMethod(), body)
            .url(request.url())
            .addHeader("User-Agent", USER_AGENT)

        request.headers().forEach { (name, values) ->
            requestBuilder.removeHeader(name)
            values.forEach { value -> requestBuilder.addHeader(name, value) }
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            }
            val responseBody = response.body?.string() ?: ""
            val latestUrl = response.request.url.toString()
            return NPResponse(
                response.code,
                response.message,
                response.headers.toMultimap(),
                responseBody,
                latestUrl
            )
        }
    }
}
