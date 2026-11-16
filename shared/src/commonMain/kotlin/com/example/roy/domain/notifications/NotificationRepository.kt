package com.example.roy.domain.notifications

import com.example.roy.domain.auth.AuthResult
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal data class NotificationSettings(
    val remindersPushEnabled: Boolean,
    val personalPushEnabled: Boolean,
    val extraPushEnabled: Boolean,
    val extraInAppEnabled: Boolean,
    val revision: Long,
)

internal enum class NotificationChannel { RemindersPush, PersonalPush, NearbyPush, NearbyInApp }

internal sealed interface NotificationSettingsWrite {
    data class Saved(val value: NotificationSettings) : NotificationSettingsWrite
    data class Conflict(val current: NotificationSettings?) : NotificationSettingsWrite
}

enum class PushPermission { NotAsked, Denied, Allowed }
enum class NotificationKind { NearbyMeeting, Reminder, ChatMessage, ProposalVote, ProposalReady, BookingCreated, BookingCancelled }

@ConsistentCopyVisibility
data class NotificationTarget private constructor(
    val kind: NotificationKind,
    val notificationId: String,
    val pointId: String,
    val chatId: String?,
    val proposalId: String?,
) {
    companion object {
        fun fromPush(
            version: String?, kind: String?, notificationId: String?, pointId: String?, chatId: String?, proposalId: String?,
        ): NotificationTarget? =
            if (version == "1") parse(kind, notificationId, pointId, chatId, proposalId) else null

        internal fun parse(
            kind: String?, notificationId: String?, pointId: String?, chatId: String?, proposalId: String?,
        ): NotificationTarget? {
            val id = notificationId.notificationId() ?: return null
            val point = pointId.notificationId() ?: return null
            val type = when (kind) {
                "nearby_meeting" -> NotificationKind.NearbyMeeting
                "reminder_1h" -> NotificationKind.Reminder
                "chat_message" -> NotificationKind.ChatMessage
                "proposal_vote" -> NotificationKind.ProposalVote
                "proposal_ready" -> NotificationKind.ProposalReady
                "booking_created" -> NotificationKind.BookingCreated
                "booking_cancelled" -> NotificationKind.BookingCancelled
                else -> return null
            }
            if (type == NotificationKind.NearbyMeeting) {
                if (chatId != null || proposalId != null) return null
                return NotificationTarget(type, id, point, null, null)
            }
            val chat = chatId.notificationId() ?: return null
            if (type == NotificationKind.ChatMessage) {
                if (proposalId != null) return null
                return NotificationTarget(type, id, point, chat, null)
            }
            val proposal = proposalId.notificationId() ?: return null
            return NotificationTarget(type, id, point, chat, proposal)
        }
    }
}

internal fun String?.notificationId(): String? =
    this?.takeIf { it.length == 36 }?.let { runCatching { Uuid.parse(it).toString() }.getOrNull() }

internal data class NotificationEntry(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val target: NotificationTarget?,
    val createdAt: Instant,
    val readAt: Instant?,
)

internal data class NotificationPage(val items: List<NotificationEntry>, val nextCursor: String?)

internal interface NotificationRepository {
    suspend fun settings(token: String): AuthResult<NotificationSettings>
    suspend fun update(value: NotificationSettings, token: String): AuthResult<NotificationSettingsWrite>
    suspend fun list(cursor: String?, token: String): AuthResult<NotificationPage>
    suspend fun markRead(id: String, token: String): AuthResult<NotificationEntry>
    suspend fun setLocation(latitude: Double, longitude: Double, token: String): AuthResult<Unit>
    suspend fun removeLocation(token: String): AuthResult<Unit>
}
