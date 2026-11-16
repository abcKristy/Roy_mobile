package com.example.roy.domain.meetings

/** Reads only the supplied member's avatar in the session that opened the voters sheet. */
internal fun interface MeetingVoterAvatarLoader {
    suspend fun load(userId: String, avatarPath: String): ByteArray?
}
