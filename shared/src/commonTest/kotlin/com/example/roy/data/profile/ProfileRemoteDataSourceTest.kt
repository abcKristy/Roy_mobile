package com.example.roy.data.profile

import com.example.roy.data.auth.AuthHttpMethod
import com.example.roy.data.auth.AuthHttpRequest
import com.example.roy.data.auth.AuthHttpResponse
import com.example.roy.data.auth.AuthHttpTransport
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.profile.MaxProfileAvatarBytes
import com.example.roy.domain.profile.PickImageResult
import com.example.roy.domain.profile.ProfileAvatar
import com.example.roy.domain.profile.ProfileProblem
import com.example.roy.domain.profile.ProfileResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProfileRemoteDataSourceTest {
    @Test
    fun nameWritesOnlyDisplayNameAndUsesCanonicalResponse() = runTest {
        val transport = RecordingTransport(AuthHttpResponse(200, """
            {"id":"$userId","displayName":"Каноническое имя","phone":"+447700900200","ageYears":25,"createdAt":"2026-11-16T00:00:00Z"}
        """.trimIndent()))
        val result = ProfileRemoteDataSource(origin, transport).updateName("Имя", "access")
        val request = transport.requests.single()
        assertEquals(AuthHttpMethod.Patch, request.method)
        assertEquals("$origin/api/v1/me", request.url)
        assertEquals("{\"displayName\":\"Имя\"}", request.body)
        val user = assertIs<ProfileResult.Value<com.example.roy.domain.auth.AuthUser>>(assertIs<AuthResult.Success<*>>(result).value).value
        assertEquals("Каноническое имя", user.displayName)
        assertEquals(25, user.ageYears)
    }

    @Test
    fun multipartUploadUsesOneBinaryFilePartAndRejectsOversizedInputBeforeNetwork() = runTest {
        val transport = RecordingTransport(AuthHttpResponse(413, ""))
        val profile = ProfileRemoteDataSource(origin, transport)
        val image = PickImageResult.Selected(byteArrayOf(-1, -40, -1, 0), "image/jpeg")
        assertEquals(AuthResult.Success(ProfileResult.Rejected(ProfileProblem.TooLarge)), profile.uploadAvatar(image, "access"))
        val request = transport.requests.single()
        val contentType = assertNotNull(request.requestContentType)
        val boundary = contentType.substringAfter("boundary=")
        val bytes = assertNotNull(request.requestBytes)
        val text = bytes.decodeToString()
        assertTrue(text.startsWith("--$boundary\r\nContent-Disposition: form-data; name=\"file\";"))
        assertTrue(text.endsWith("\r\n--$boundary--\r\n"))
        assertTrue(bytes.toList().windowed(image.bytes.size).any { it == image.bytes.toList() })
        val huge = PickImageResult.Selected(ByteArray(MaxProfileAvatarBytes + 1), "image/png")
        assertEquals(AuthResult.Success(ProfileResult.Rejected(ProfileProblem.TooLarge)), profile.uploadAvatar(huge, "access"))
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun conditionalAvatarReadAndAmbiguousDeletionHaveDistinctTypedResults() = runTest {
        val transport = RecordingTransport(AuthHttpResponse(304, ""))
        val profile = ProfileRemoteDataSource(origin, transport)
        assertEquals(AuthResult.Success(ProfileResult.Value(ProfileAvatar.Unchanged)), profile.readAvatar(userId, "\"v1\"", "access"))
        assertEquals("\"v1\"", transport.requests.single().headers["If-None-Match"])
        transport.response = null
        assertEquals(
            AuthResult.Success(ProfileResult.Rejected(ProfileProblem.DeletionUncertain)),
            profile.deleteAccount("current", true, "access"),
        )
        assertEquals(2, transport.requests.size)
        assertEquals(AuthHttpMethod.Post, transport.requests.last().method)
    }

    private class RecordingTransport(var response: AuthHttpResponse?) : AuthHttpTransport {
        val requests = mutableListOf<AuthHttpRequest>()
        override suspend fun execute(request: AuthHttpRequest): AuthHttpResponse? {
            requests += request
            return response
        }
    }

    private companion object {
        const val origin = "http://127.0.0.1:8083"
        const val userId = "75ebc250-29ba-4495-abea-e662559afecc"
    }
}
