package com.example.roy.data.network

private const val Hex = "0123456789ABCDEF"

internal fun String.encodeQueryValue(): String = buildString {
    for (byte in encodeToByteArray()) {
        val value = byte.toInt() and 0xff
        val char = value.toChar()
        when (char) {
            in 'A'..'Z', in 'a'..'z', in '0'..'9', '-', '.', '_', '~' -> append(char)
            else -> append('%').append(Hex[value shr 4]).append(Hex[value and 0xf])
        }
    }
}
