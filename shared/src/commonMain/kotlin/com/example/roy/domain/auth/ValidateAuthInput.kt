package com.example.roy.domain.auth

internal enum class AuthMode {
    Login,
    Register,
}

internal enum class AuthField {
    Phone,
    Password,
    DisplayName,
    AgeYears,
    Consent,
}

internal enum class AuthFieldError {
    InvalidPhone,
    PasswordRequired,
    PasswordLength,
    DisplayNameLength,
    InvalidAge,
    ConsentRequired,
}

internal class AuthFormInput(
    val mode: AuthMode,
    val phone: String,
    val password: String,
    val displayName: String = "",
    val ageInput: String = "",
    val acceptedPrivacyPolicyVersion: String? = null,
)

internal sealed interface AuthCredentials {
    val phone: String
    val password: String

    class Login(override val phone: String, override val password: String) : AuthCredentials

    class Register(
        override val phone: String,
        override val password: String,
        val displayName: String,
        val ageYears: Int,
        val acceptedPrivacyPolicyVersion: String,
    ) : AuthCredentials
}

internal sealed interface AuthValidationResult {
    data class Valid(val credentials: AuthCredentials) : AuthValidationResult
    data class Invalid(val errors: Map<AuthField, AuthFieldError>) : AuthValidationResult
}

internal class ValidateAuthInput {
    operator fun invoke(input: AuthFormInput): AuthValidationResult {
        val phone = input.phone.trim()
        val errors = mutableMapOf<AuthField, AuthFieldError>()
        if (!E164.matches(phone)) errors[AuthField.Phone] = AuthFieldError.InvalidPhone
        if (input.password.isEmpty()) errors[AuthField.Password] = AuthFieldError.PasswordRequired

        if (input.mode == AuthMode.Login) {
            return if (errors.isEmpty()) AuthValidationResult.Valid(AuthCredentials.Login(phone, input.password))
            else AuthValidationResult.Invalid(errors)
        }

        if (input.password.isNotEmpty() && input.password.unicodeLength() !in 8..128) {
            errors[AuthField.Password] = AuthFieldError.PasswordLength
        }
        val name = input.displayName.trim()
        if (name.unicodeLength(rejectInvalidNameCharacters = true) !in 1..80) {
            errors[AuthField.DisplayName] = AuthFieldError.DisplayNameLength
        }
        val age = input.ageInput.trim().toIntOrNull()
        if (age == null || age !in 12..Short.MAX_VALUE.toInt()) {
            errors[AuthField.AgeYears] = AuthFieldError.InvalidAge
        }
        val version = input.acceptedPrivacyPolicyVersion?.takeIf { it.isNotBlank() }
        if (version == null) errors[AuthField.Consent] = AuthFieldError.ConsentRequired

        if (errors.isNotEmpty()) return AuthValidationResult.Invalid(errors)
        return AuthValidationResult.Valid(
            AuthCredentials.Register(phone, input.password, name, requireNotNull(age), requireNotNull(version))
        )
    }

    private companion object {
        val E164 = Regex("^\\+[1-9][0-9]{1,14}$")
    }
}

private fun String.unicodeLength(rejectInvalidNameCharacters: Boolean = false): Int {
    var count = 0
    var index = 0
    while (index < length) {
        val character = this[index++]
        val pair = character in '\uD800'..'\uDBFF' && index < length && this[index] in '\uDC00'..'\uDFFF'
        if (pair) index++
        if (rejectInvalidNameCharacters && !pair && (character == '\u0000' || character in '\uD800'..'\uDFFF')) {
            return -1
        }
        count++
    }
    return count
}
