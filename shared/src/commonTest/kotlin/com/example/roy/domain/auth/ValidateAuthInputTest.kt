package com.example.roy.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ValidateAuthInputTest {
    private val validate = ValidateAuthInput()

    @Test
    fun loginUsesOnlyPhoneAndPasswordWithoutRegistrationPasswordMinimum() {
        val result = assertIs<AuthValidationResult.Valid>(
            validate(AuthFormInput(AuthMode.Login, " +79991234567 ", "short", ageInput = "11"))
        )
        val login = assertIs<AuthCredentials.Login>(result.credentials)
        assertEquals("+79991234567", login.phone)
        assertEquals("short", login.password)
    }

    @Test
    fun registrationChecksRequiredFieldsAndContractRanges() {
        val result = assertIs<AuthValidationResult.Invalid>(
            validate(AuthFormInput(AuthMode.Register, "8999", "short", ageInput = "11"))
        )
        assertEquals(
            mapOf(
                AuthField.Phone to AuthFieldError.InvalidPhone,
                AuthField.Password to AuthFieldError.PasswordLength,
                AuthField.DisplayName to AuthFieldError.DisplayNameLength,
                AuthField.AgeYears to AuthFieldError.InvalidAge,
                AuthField.Consent to AuthFieldError.ConsentRequired,
            ),
            result.errors,
        )
    }

    @Test
    fun registrationCountsUnicodeCodePointsAndPreservesPasswordWhitespace() {
        val password = " 🔐🔐🔐🔐🔐🔐 "
        val name = "𐐀".repeat(80)
        val result = assertIs<AuthValidationResult.Valid>(
            validate(AuthFormInput(AuthMode.Register, "+79991234567", password, " $name ", "12", "local-policy"))
        )
        val register = assertIs<AuthCredentials.Register>(result.credentials)
        assertEquals(name, register.displayName)
        assertEquals(password, register.password)
        assertEquals(12, register.ageYears)
        assertEquals("local-policy", register.acceptedPrivacyPolicyVersion)
    }

    @Test
    fun registrationRejectsUnpairedSurrogateAndAgeAboveSmallInt() {
        val result = assertIs<AuthValidationResult.Invalid>(
            validate(AuthFormInput(AuthMode.Register, "+79991234567", "password", "\uD800", "32768", "policy"))
        )
        assertEquals(
            mapOf(
                AuthField.DisplayName to AuthFieldError.DisplayNameLength,
                AuthField.AgeYears to AuthFieldError.InvalidAge,
            ),
            result.errors,
        )
    }
}
