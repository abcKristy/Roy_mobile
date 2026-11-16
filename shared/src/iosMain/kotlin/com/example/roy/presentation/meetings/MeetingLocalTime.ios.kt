package com.example.roy.presentation.meetings

import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone

private val appleEpoch = Instant.parse("2001-01-01T00:00:00Z")

internal actual fun meetingLocalFields(instant: Instant): Pair<String, String> {
    val date = NSDate(timeIntervalSinceReferenceDate = (instant + 1.hours - appleEpoch).inWholeSeconds.toDouble())
    return formatter("yyyy-MM-dd").stringFromDate(date) to formatter("HH':00'").stringFromDate(date)
}

internal actual fun parsePlatformMeetingLocalTime(date: String, time: String): Instant? {
    val input = "$date $time"
    val formatter = formatter("yyyy-MM-dd HH:mm")
    val parsed = formatter.dateFromString(input) ?: return null
    if (formatter.stringFromDate(parsed) != input) return null
    return Instant.fromEpochSeconds(appleEpoch.epochSeconds + parsed.timeIntervalSinceReferenceDate.toLong())
}

private fun formatter(pattern: String) = NSDateFormatter().apply {
    locale = NSLocale(localeIdentifier = "en_US_POSIX")
    timeZone = NSTimeZone.localTimeZone
    dateFormat = pattern
    lenient = false
}
