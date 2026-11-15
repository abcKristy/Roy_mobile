package com.example.roy.presentation.points.create

import kotlin.time.Instant
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterShortStyle

internal actual fun formatCreateBlockDeadline(instant: Instant): String {
    val date = NSDate(timeIntervalSince1970 = instant.epochSeconds.toDouble())
    return NSDateFormatter().apply {
        dateStyle = NSDateFormatterMediumStyle
        timeStyle = NSDateFormatterShortStyle
    }.stringFromDate(date)
}
