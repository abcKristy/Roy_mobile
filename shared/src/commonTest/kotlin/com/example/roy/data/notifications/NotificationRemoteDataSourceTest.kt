package com.example.roy.data.notifications

import com.example.roy.data.auth.AuthHttpMethod
import com.example.roy.data.auth.AuthHttpRequest
import com.example.roy.data.auth.AuthHttpResponse
import com.example.roy.data.auth.AuthHttpTransport
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.notifications.NotificationKind
import com.example.roy.domain.notifications.NotificationSettings
import com.example.roy.domain.notifications.NotificationSettingsWrite
import com.example.roy.domain.notifications.NotificationTarget
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NotificationRemoteDataSourceTest {
    @Test
    fun updatesOnlyTheWireSettingsFieldsAndDecodesConflictSnapshot() = runTest {
        var request: AuthHttpRequest? = null
        val repository = NotificationRemoteDataSource("https://roy.test", AuthHttpTransport {
            request = it
            AuthHttpResponse(409, """{"error":{"code":"STALE_STATE","details":$settingsBody}}""")
        })
        val result = assertIs<AuthResult.Success<NotificationSettingsWrite>>(
            repository.update(NotificationSettings(true, false, true, false, 2), "access"),
        )
        val wire = assertNotNull(request)
        assertEquals(AuthHttpMethod.Put, wire.method)
        assertEquals("https://roy.test/api/v1/me/notification-settings", wire.url)
        assertEquals("access", wire.accessToken)
        val json = Json.parseToJsonElement(assertNotNull(wire.body)).jsonObject
        assertEquals("2", json.getValue("expectedRevision").jsonPrimitive.content)
        assertEquals(false, "revision" in json)
        assertEquals(3L, assertIs<NotificationSettingsWrite.Conflict>(result.value).current?.revision)
    }

    @Test
    fun notificationPageKeepsOpaqueCursorAndValidatedPublicTarget() = runTest {
        val repository = NotificationRemoteDataSource("https://roy.test/", AuthHttpTransport {
            assertEquals("https://roy.test/api/v1/me/notifications?limit=30&cursor=a%2Bb%26c%3D", it.url)
            AuthHttpResponse(
                200,
                """{"items":[{"id":"$notice","kind":"nearby_meeting","title":"Встреча рядом","body":"",
                   "target":{"pointId":"$point","chatId":null,"proposalId":null},
                   "createdAt":"2026-11-15T12:00:00Z","readAt":null}],"nextCursor":"next"}""",
            )
        })
        val result = assertIs<AuthResult.Success<*>>(repository.list("a+b&c=", "access"))
        val page = assertIs<com.example.roy.domain.notifications.NotificationPage>(result.value)
        assertEquals("next", page.nextCursor)
        val target = assertNotNull(page.items.single().target)
        assertEquals(NotificationKind.NearbyMeeting, target.kind)
        assertEquals(point, target.pointId)
        assertNull(target.chatId)
    }

    @Test
    fun pushParserRequiresVersionAndRejectsNearbyPrivateIdentifiers() {
        assertNull(NotificationTarget.fromPush("2", "reminder_1h", notice, point, notice, notice))
        assertNull(NotificationTarget.fromPush("1", "nearby_meeting", notice, point, notice, null))
        assertNull(NotificationTarget.fromPush("1", "reminder_1h", notice, point, notice, null))
        val reminder = assertNotNull(NotificationTarget.fromPush("1", "reminder_1h", notice, point, notice, point))
        assertEquals(NotificationKind.Reminder, reminder.kind)
        assertEquals(notice, reminder.notificationId)
    }

    private companion object {
        const val notice = "f1746fb6-6d93-402e-b3f6-291723e70801"
        const val point = "a5fd114d-21cb-44aa-9818-278aaf4a02af"
        const val settingsBody = """{"remindersPushEnabled":true,"personalPushEnabled":true,
            "extraPushEnabled":false,"extraInAppEnabled":false,"revision":3}"""
    }
}
