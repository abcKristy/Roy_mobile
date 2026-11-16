package com.example.roy.data.auth

import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink

internal class AndroidAuthHttpTransport : AuthHttpTransport {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    override suspend fun execute(request: AuthHttpRequest): AuthHttpResponse? =
        suspendCancellableCoroutine { continuation ->
            val wireRequest = Request.Builder().url(request.url).header("Accept", "application/json")
            request.accessToken?.let { wireRequest.header("Authorization", "Bearer $it") }
            request.headers.forEach { (name, value) -> wireRequest.header(name, value) }
            when (request.method) {
                AuthHttpMethod.Get -> wireRequest.get()
                AuthHttpMethod.Post -> wireRequest.post(OneShotAuthBody(request.body))
                AuthHttpMethod.Put -> wireRequest.put(OneShotAuthBody(request.body))
                AuthHttpMethod.Delete -> wireRequest.delete(OneShotAuthBody(request.body))
            }

            val call = client.newCall(wireRequest.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = continuation.resume(null)

                override fun onResponse(call: Call, response: Response) {
                    val result = try {
                        response.use {
                            val payload = it.body?.source()?.takeUnless { source ->
                                source.request(request.maxResponseBytes.toLong() + 1)
                            }
                            AuthHttpResponse(
                                status = it.code,
                                body = if (request.isBinaryResponseExpected) "" else payload?.readUtf8().orEmpty(),
                                retryAfter = it.header("Retry-After"),
                                binaryBody = if (request.isBinaryResponseExpected && it.isSuccessful) payload?.readByteArray() else null,
                                contentType = it.header("Content-Type"),
                            )
                        }
                    } catch (_: IOException) {
                        null
                    }
                    continuation.resume(result)
                }
            })
        }

    private class OneShotAuthBody(json: String?) : RequestBody() {
        private val bytes = json?.encodeToByteArray() ?: byteArrayOf()
        private val mediaType = json?.let { "application/json".toMediaType() }
        override fun contentType() = mediaType
        override fun contentLength() = bytes.size.toLong()
        override fun isOneShot() = true
        override fun writeTo(sink: BufferedSink) { sink.write(bytes) }
    }
}
