package com.example.roy.data.chat

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.Channel
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

internal actual fun createChatSocketTransport(): ChatSocketTransport = AndroidChatSocketTransport()

private class AndroidChatSocketTransport : ChatSocketTransport {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    override fun open(url: String, accessToken: String): ChatSocket {
        val frames = Channel<String>(128)
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer $accessToken")
            .build()
        val wire = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.length > 64 * 1024 || frames.trySend(text).isFailure) {
                    frames.close(ChatSocketEnded())
                    webSocket.cancel()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                frames.close(ChatSocketEnded(unauthorized = code == 4401))
                webSocket.close(1000, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                frames.close(ChatSocketEnded(unauthorized = response?.code == 401))
                response?.close()
            }
        })
        return object : ChatSocket {
            override val incoming = frames

            override fun send(text: String) = wire.send(text)

            override fun close() {
                frames.cancel()
                wire.cancel()
            }
        }
    }
}
