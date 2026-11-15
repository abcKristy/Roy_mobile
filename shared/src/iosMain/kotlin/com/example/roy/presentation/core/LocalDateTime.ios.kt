package com.example.roy.presentation.core

import kotlin.time.Instant
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterShortStyle

private val AppleReferenceDate = Instant.parse("2001-01-01T00:00:00Z")

internal actual fun formatLocalDateTime(instant: Instant): String {
    val referenceSeconds = (instant - AppleReferenceDate).inWholeMilliseconds / 1000.0
    val date = NSDate(timeIntervalSinceReferenceDate = referenceSeconds)
    return NSDateFormatter().apply {
        dateStyle = NSDateFormatterMediumStyle
        timeStyle = NSDateFormatterShortStyle
    }.stringFromDate(date)
}
