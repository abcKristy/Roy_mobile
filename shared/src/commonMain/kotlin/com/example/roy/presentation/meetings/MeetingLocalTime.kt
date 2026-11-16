package com.example.roy.presentation.meetings

import kotlin.time.Instant

internal expect fun meetingLocalFields(instant: Instant): Pair<String, String>
internal expect fun parseMeetingLocalTime(date: String, time: String): Instant?
