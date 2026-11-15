package com.example.roy.data.chat

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.Channel
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.Foundation.NSURLSessionWebSocketMessage
import platform.Foundation.setValue
import platform.darwin.NSObject

internal actual fun createChatSocketTransport(): ChatSocketTransport = IosChatSocketTransport()

@OptIn(ExperimentalForeignApi::class)
private class IosChatSocketTransport : ChatSocketTransport {
    override fun open(url: String, accessToken: String): ChatSocket {
        val request = NSMutableURLRequest.requestWithURL(requireNotNull(NSURL.URLWithString(url)))
        request.setValue("Bearer $accessToken", forHTTPHeaderField = "Authorization")
        val configuration = NSURLSessionConfiguration.ephemeralSessionConfiguration().apply {
            timeoutIntervalForRequest = 70.0
            HTTPShouldSetCookies = false
            URLCache = null
            URLCredentialStorage = null
        }
        val session = NSURLSession.sessionWithConfiguration(configuration, NoRedirects(), null)
        val task = session.webSocketTaskWithRequest(request)
        task.setMaximumMessageSize(64L * 1024L)
        val frames = Channel<String>(128)

        fun receiveNext() {
            task.receiveMessageWithCompletionHandler { message, error ->
                val text = message?.string()
                if (error != null) {
                    val unauthorized = (task.response() as? NSHTTPURLResponse)?.statusCode == 401L ||
                        task.closeCode.toLong() == 4401L
                    frames.close(ChatSocketEnded(unauthorized))
                } else if (text != null && frames.trySend(text).isSuccess) {
                    receiveNext()
                } else {
                    frames.close(ChatSocketEnded())
                }
            }
        }

        task.resume()
        receiveNext()
        return object : ChatSocket {
            override val incoming = frames

            override fun send(text: String): Boolean {
                task.sendMessage(NSURLSessionWebSocketMessage(text)) { error ->
                    if (error != null) frames.close(ChatSocketEnded())
                }
                return true
            }

            override fun close() {
                frames.cancel()
                session.invalidateAndCancel()
            }
        }
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
