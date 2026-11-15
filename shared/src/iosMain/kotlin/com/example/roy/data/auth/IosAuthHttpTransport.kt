package com.example.roy.data.auth

import kotlin.coroutines.resume
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.darwin.NSObject
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IosAuthHttpTransport : AuthHttpTransport {
    override suspend fun execute(request: AuthHttpRequest): AuthHttpResponse? = suspendCancellableCoroutine { continuation ->
        val url = NSURL.URLWithString(request.url)
        if (url == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val wireRequest = NSMutableURLRequest.requestWithURL(url)
        wireRequest.setHTTPMethod(when (request.method) {
            AuthHttpMethod.Get -> "GET"
            AuthHttpMethod.Post -> "POST"
            AuthHttpMethod.Put -> "PUT"
            AuthHttpMethod.Delete -> "DELETE"
        })
        wireRequest.setValue("application/json", forHTTPHeaderField = "Accept")
        request.accessToken?.let { wireRequest.setValue("Bearer $it", forHTTPHeaderField = "Authorization") }
        request.headers.forEach { (name, value) -> wireRequest.setValue(value, forHTTPHeaderField = name) }
        request.body?.let {
            wireRequest.setValue("application/json", forHTTPHeaderField = "Content-Type")
            val bytes = it.encodeToByteArray()
            wireRequest.setHTTPBody(bytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            })
        }
        val configuration = NSURLSessionConfiguration.ephemeralSessionConfiguration().apply {
            timeoutIntervalForRequest = 15.0
            timeoutIntervalForResource = 20.0
            HTTPShouldSetCookies = false
            URLCache = null
            URLCredentialStorage = null
        }
        val session = NSURLSession.sessionWithConfiguration(configuration, NoRedirects(), null)
        val task = session.dataTaskWithRequest(wireRequest, completionHandler = { data, response, error ->
            val http = response as? NSHTTPURLResponse
            continuation.resume(
                if (error == null && http != null) {
                    AuthHttpResponse(http.statusCode.toInt(), data?.utf8Text(request.maxResponseBytes).orEmpty())
                }
                else null
            )
            session.finishTasksAndInvalidate()
        })
        continuation.invokeOnCancellation { session.invalidateAndCancel() }
        task.resume()
    }

    private class NoRedirects : NSObject(), NSURLSessionTaskDelegateProtocol {
        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            willPerformHTTPRedirection: NSHTTPURLResponse,
            newRequest: NSURLRequest,
            completionHandler: (NSURLRequest?) -> Unit,
        ) {
            completionHandler(null)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.utf8Text(maxResponseBytes: Int): String {
    if (length == 0UL || length > maxResponseBytes.toULong()) return ""
    val result = ByteArray(length.toInt())
    result.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return result.decodeToString()
}
