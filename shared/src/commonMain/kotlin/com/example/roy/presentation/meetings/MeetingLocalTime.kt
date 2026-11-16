package com.example.roy.presentation.meetings

import kotlin.time.Instant

internal expect fun meetingLocalFields(instant: Instant): Pair<String, String>
internal expect fun parsePlatformMeetingLocalTime(date: String, time: String): Instant?

private val meetingDateFormat = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
private val meetingTimeFormat = Regex("[0-9]{2}:[0-9]{2}")

internal fun parseMeetingLocalTime(date: String, time: String): Instant? =
    if (meetingDateFormat.matches(date) && meetingTimeFormat.matches(time)) parsePlatformMeetingLocalTime(date, time)
    else null
