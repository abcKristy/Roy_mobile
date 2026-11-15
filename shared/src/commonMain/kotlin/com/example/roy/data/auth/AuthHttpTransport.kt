package com.example.roy.data.auth

internal enum class AuthHttpMethod {
    Get,
    Post,
    Put,
    Delete,
}

internal class AuthHttpRequest(
    val method: AuthHttpMethod,
    val url: String,
    val body: String?,
    val accessToken: String?,
    val maxResponseBytes: Int = MaxAuthResponseBytes,
    val headers: Map<String, String> = emptyMap(),
)

internal class AuthHttpResponse(val status: Int, val body: String, val retryAfter: String? = null)

/** Returns null when no HTTP response is available. Implementations never follow redirects. */
internal fun interface AuthHttpTransport {
    suspend fun execute(request: AuthHttpRequest): AuthHttpResponse?
}

internal const val MaxAuthResponseBytes = 64 * 1024
