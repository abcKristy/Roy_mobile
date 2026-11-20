package com.example.roy.data.auth

internal enum class AuthHttpMethod {
    Get,
    Post,
    Put,
    Patch,
    Delete,
}

internal class AuthHttpRequest(
    val method: AuthHttpMethod,
    val url: String,
    val body: String?,
    val accessToken: String?,
    val maxResponseBytes: Int = MAX_AUTH_RESPONSE_BYTES,
    val headers: Map<String, String> = emptyMap(),
    val isBinaryResponseExpected: Boolean = false,
    val requestBytes: ByteArray? = null,
    val requestContentType: String? = null,
)

internal class AuthHttpResponse(
    val status: Int,
    val body: String,
    val retryAfter: String? = null,
    val binaryBody: ByteArray? = null,
    val contentType: String? = null,
    val etag: String? = null,
)

/** Returns null when no HTTP response is available. Implementations never follow redirects. */
internal fun interface AuthHttpTransport {
    suspend fun execute(request: AuthHttpRequest): AuthHttpResponse?
}

internal const val MAX_AUTH_RESPONSE_BYTES = 64 * 1024
