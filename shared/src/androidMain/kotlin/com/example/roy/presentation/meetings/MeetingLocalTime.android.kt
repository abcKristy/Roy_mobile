package com.example.roy.presentation.meetings

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.time.Instant

internal actual fun meetingLocalFields(instant: Instant): Pair<String, String> {
    val local = java.time.Instant.ofEpochSecond(instant.epochSeconds)
        .atZone(ZoneId.systemDefault()).plusHours(1).withMinute(0).withSecond(0).toLocalDateTime()
    return local.toLocalDate().toString() to local.toLocalTime().toString()
}

internal actual fun parseMeetingLocalTime(date: String, time: String): Instant? = try {
    val local = LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time))
    val offset = ZoneId.systemDefault().rules.getValidOffsets(local).firstOrNull()
    offset?.let { Instant.fromEpochSeconds(local.toEpochSecond(it)) }
} catch (_: DateTimeException) {
    null
}
