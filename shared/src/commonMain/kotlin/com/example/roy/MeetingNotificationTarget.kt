package com.example.roy

import kotlin.uuid.Uuid

/** The chat and proposal are read under the current session. */
class MeetingNotificationTarget private constructor(
    val pointId: String,
    val chatId: String,
    val proposalId: String,
) {
    companion object {
        fun fromPayload(
            version: String?, kind: String?, pointId: String?, chatId: String?, proposalId: String?,
        ): MeetingNotificationTarget? {
            if (version != "1" || kind != "reminder_1h") return null
            return MeetingNotificationTarget(
                pointId.canonicalId() ?: return null,
                chatId.canonicalId() ?: return null,
                proposalId.canonicalId() ?: return null,
            )
        }

        private fun String?.canonicalId(): String? = this?.let { value ->
            if (value.length != 36) null else runCatching { Uuid.parse(value).toString() }.getOrNull()
        }
    }
}
