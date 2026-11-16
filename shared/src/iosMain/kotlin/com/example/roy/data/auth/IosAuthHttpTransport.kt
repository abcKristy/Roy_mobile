package com.example.roy.data.auth

import kotlin.coroutines.resume
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableData
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.appendData
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
        val delegate = BoundedResponse(request, onComplete = continuation::resume)
        // Foundation creates a serial delegate queue when no queue is supplied.
        val session = NSURLSession.sessionWithConfiguration(configuration, delegate, null)
        val task = session.dataTaskWithRequest(wireRequest)
        continuation.invokeOnCancellation { session.invalidateAndCancel() }
        task.resume()
    }

    private class BoundedResponse(
        private val request: AuthHttpRequest,
        private val onComplete: (AuthHttpResponse?) -> Unit,
    ) : NSObject(), NSURLSessionDataDelegateProtocol {
        private val received = NSMutableData()
        private var oversizedResponse: NSHTTPURLResponse? = null

        override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
            if (oversizedResponse != null) return
            if (didReceiveData.length > request.maxResponseBytes.toULong() - received.length) {
                oversizedResponse = dataTask.response as? NSHTTPURLResponse
                dataTask.cancel()
                return
            }
            received.appendData(didReceiveData)
        }

        override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
            val http = oversizedResponse ?: (task.response as? NSHTTPURLResponse).takeIf { didCompleteWithError == null }
            val response = http?.let {
                val status = it.statusCode.toInt()
                val body = if (oversizedResponse == null) received.boundedBytes(request.maxResponseBytes) else null
                AuthHttpResponse(
                    status = status,
                    body = if (request.isBinaryResponseExpected) "" else body?.decodeToString().orEmpty(),
                    retryAfter = it.headerValue("Retry-After"),
                    binaryBody = if (request.isBinaryResponseExpected && status == 200) body else null,
                    contentType = it.headerValue("Content-Type"),
                )
            }
            onComplete(response)
            session.finishTasksAndInvalidate()
        }

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
private fun NSData.boundedBytes(maxResponseBytes: Int): ByteArray? {
    if (length == 0UL || length > maxResponseBytes.toULong()) return null
    val result = ByteArray(length.toInt())
    result.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return result
}

@OptIn(ExperimentalForeignApi::class)
private fun NSHTTPURLResponse.headerValue(name: String): String? =
    allHeaderFields.entries.firstOrNull { (it.key as? String)?.equals(name, ignoreCase = true) == true }
        ?.value as? String
