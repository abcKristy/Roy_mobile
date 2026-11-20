package com.example.roy.presentation.profile

import com.example.roy.domain.auth.AuthUser
import com.example.roy.domain.auth.PrivacyPolicy
import com.example.roy.domain.profile.ProfileAvatar
import com.example.roy.domain.profile.ProfileProblem

internal data class ProfileViewState(
    val user: AuthUser? = null,
    val nameDraft: String = "",
    val currentPassword: String = "",
    val newPassword: String = "",
    val deletionPassword: String = "",
    val avatar: ProfileAvatar.Image? = null,
    val policy: PrivacyPolicy? = null,
    val operation: ProfileOperation? = null,
    val dialog: ProfileDialog? = null,
    val error: ProfileProblem? = null,
    val failedOperation: ProfileOperation? = null,
    val notice: ProfileNotice? = null,
    val isPickerPending: Boolean = false,
    val isOnline: Boolean = true,
    val hasAvatar: Boolean = false,
    val isDeletionUncertain: Boolean = false,
    val isCached: Boolean = false,
) {
    val canEdit get() = user != null && operation == null && !isPickerPending && !isDeletionUncertain
    val canSubmit get() = canEdit && isOnline && !isCached
    val canSaveName get() = canSubmit && nameDraft.trim() != user?.displayName && nameDraft.trim().validSize(1..80)
    val isNameInvalid get() = user != null && nameDraft != user.displayName && !nameDraft.trim().validSize(1..80)
    val canChangePassword get() = canSubmit && currentPassword.isNotEmpty() && newPassword.validSize(8..128)
    val canDelete get() = canSubmit && deletionPassword.length in 1..1024

    override fun toString() = "ProfileViewState(redacted)"
}
