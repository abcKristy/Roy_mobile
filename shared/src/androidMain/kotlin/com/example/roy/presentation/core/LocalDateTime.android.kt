package com.example.roy.presentation.core

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Instant

internal actual fun formatLocalDateTime(instant: Instant): String {
    val localTime = java.time.Instant.ofEpochSecond(instant.epochSeconds, instant.nanosecondsOfSecond.toLong())
        .atZone(ZoneId.systemDefault())
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(localTime)
}
