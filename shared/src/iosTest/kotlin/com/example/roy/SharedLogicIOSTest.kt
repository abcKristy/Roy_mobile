package com.example.roy

import com.example.roy.push.iosRecipientBinding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SharedLogicIOSTest {
    @Test
    fun recipientBindingMatchesBackendSha256Contract() {
        assertEquals(
            "90b3f6e0fe9888c051f30305c43f6b8ab04e99944980f8715cc9f6e6b7fe9503",
            iosRecipientBinding(
                "11111111-1111-4111-8111-111111111111",
                "22222222-2222-4222-8222-222222222222",
            ),
        )
    }

    @Test
    fun recipientBindingSeparatesBothAccountAndInstallation() {
        val original = iosRecipientBinding("installation-a", "account-a")
        assertNotEquals(original, iosRecipientBinding("installation-a", "account-b"))
        assertNotEquals(original, iosRecipientBinding("installation-b", "account-a"))
    }
}
