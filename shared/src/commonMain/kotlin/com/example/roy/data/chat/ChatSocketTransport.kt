package com.example.roy.data.chat

import kotlinx.coroutines.channels.ReceiveChannel

internal interface ChatSocketTransport {
    fun open(url: String, accessToken: String): ChatSocket
}

internal interface ChatSocket {
    val incoming: ReceiveChannel<String>
    fun send(text: String): Boolean
    fun close()
}

internal class ChatSocketEnded(val unauthorized: Boolean = false) : Exception()

internal expect fun createChatSocketTransport(): ChatSocketTransport
