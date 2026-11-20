package com.example.roy.presentation.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.roy.domain.profile.ProfileAvatar
import com.example.roy.domain.profile.ProfileProblem
import com.example.roy.presentation.components.DestructiveButton
import com.example.roy.presentation.components.PrimaryButton
import com.example.roy.presentation.components.SecondaryButton
import com.example.roy.presentation.components.TertiaryButton
import com.example.roy.presentation.components.feedback.ErrorState
import com.example.roy.presentation.components.feedback.LoadingMode
import com.example.roy.presentation.components.feedback.LoadingState
import com.example.roy.presentation.components.fields.RoyTextField
import com.example.roy.presentation.theme.RoyTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.auth_logout
import roy.shared.generated.resources.auth_network
import roy.shared.generated.resources.profile_access_denied
import roy.shared.generated.resources.profile_age
import roy.shared.generated.resources.profile_avatar_removed
import roy.shared.generated.resources.profile_avatar_saved
import roy.shared.generated.resources.profile_cached
import roy.shared.generated.resources.profile_cancel
import roy.shared.generated.resources.profile_change_password
import roy.shared.generated.resources.profile_check_deletion
import roy.shared.generated.resources.profile_current_password
import roy.shared.generated.resources.profile_delete_account
import roy.shared.generated.resources.profile_delete_body
import roy.shared.generated.resources.profile_delete_title
import roy.shared.generated.resources.profile_deletion_currently_available
import roy.shared.generated.resources.profile_deletion_uncertain
import roy.shared.generated.resources.profile_image_too_large
import roy.shared.generated.resources.profile_invalid_current
import roy.shared.generated.resources.profile_invalid_image
import roy.shared.generated.resources.profile_invalid_name
import roy.shared.generated.resources.profile_loading
import roy.shared.generated.resources.profile_logout_body
import roy.shared.generated.resources.profile_logout_offline_body
import roy.shared.generated.resources.profile_logout_title
import roy.shared.generated.resources.profile_name
import roy.shared.generated.resources.profile_name_saved
import roy.shared.generated.resources.profile_new_password
import roy.shared.generated.resources.profile_new_password_length
import roy.shared.generated.resources.profile_notification_settings
import roy.shared.generated.resources.profile_notifications
import roy.shared.generated.resources.profile_offline
import roy.shared.generated.resources.profile_password_heading
import roy.shared.generated.resources.profile_phone
import roy.shared.generated.resources.profile_photo
import roy.shared.generated.resources.profile_pick_photo
import roy.shared.generated.resources.profile_policy
import roy.shared.generated.resources.profile_policy_retry
import roy.shared.generated.resources.profile_remove_photo
import roy.shared.generated.resources.profile_remove_photo_body
import roy.shared.generated.resources.profile_save_name
import roy.shared.generated.resources.profile_unavailable
import roy.shared.generated.resources.tab_profile

@Composable
internal fun ProfileView(
    viewState: ProfileViewState,
    eventHandler: (ProfileViewEvent) -> Unit,
    policyOpenError: String? = null,
) {
    val spacing = RoyTheme.dimensions.spacing
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(spacing.comfortable),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        ProfileHeading(stringResource(Res.string.tab_profile))
        val user = viewState.user
        if (!viewState.isOnline) ProfileText(stringResource(Res.string.profile_offline), live = true)
        if (viewState.isCached) ProfileText(stringResource(Res.string.profile_cached))
        if (user == null && viewState.operation == ProfileOperation.Load) {
            LoadingState(LoadingMode.Inline, stringResource(Res.string.profile_loading))
        }
        if (viewState.error != null && (user == null || viewState.failedOperation == ProfileOperation.Load)) {
            ErrorState(stringResource(viewState.error.resource), viewState.isOnline, { eventHandler(ProfileViewEvent.OnLoad) })
        }
        if (user != null) {
            ProfileAvatarImage(user.displayName, viewState.avatar)
            SecondaryButton(
                stringResource(Res.string.profile_pick_photo),
                { eventHandler(ProfileViewEvent.OnPickAvatarRequested) },
                isEnabled = viewState.canSubmit,
            )
            if (viewState.operation == ProfileOperation.Avatar) {
                LoadingState(LoadingMode.Inline, stringResource(Res.string.profile_photo))
            }
            if (viewState.hasAvatar) TertiaryButton(
                stringResource(Res.string.profile_remove_photo),
                { eventHandler(ProfileViewEvent.OnDeleteAvatarRequested) },
                isEnabled = viewState.canSubmit,
            )
            viewState.error?.takeIf { viewState.failedOperation == ProfileOperation.Avatar && viewState.dialog == null }
                ?.let { ProfileText(stringResource(it.resource), live = true) }
            RoyTextField(
                viewState.nameDraft, stringResource(Res.string.profile_name), { eventHandler(ProfileViewEvent.OnNameChanged(it)) },
                errorText = viewState.errorTextFor(ProfileOperation.Name) ?: if (viewState.isNameInvalid) {
                    stringResource(Res.string.profile_invalid_name)
                } else null,
                isEnabled = viewState.canSubmit,
            )
            PrimaryButton(
                stringResource(Res.string.profile_save_name), { eventHandler(ProfileViewEvent.OnSaveName) },
                isEnabled = viewState.canSaveName, isSubmitting = viewState.operation == ProfileOperation.Name,
            )
            ProfileText(stringResource(Res.string.profile_phone, user.phone))
            ProfileText(stringResource(Res.string.profile_age, user.ageYears))
            TertiaryButton(stringResource(Res.string.profile_notifications), { eventHandler(ProfileViewEvent.OnNotificationsRequested) })
            TertiaryButton(stringResource(Res.string.profile_notification_settings), { eventHandler(ProfileViewEvent.OnNotificationSettingsRequested) })
            ProfileHeading(stringResource(Res.string.profile_password_heading))
            ProfilePassword(
                viewState.currentPassword, stringResource(Res.string.profile_current_password),
                viewState.canSubmit,
                viewState.errorTextFor(ProfileOperation.Password, ProfileProblem.InvalidCurrentPassword),
            ) { eventHandler(ProfileViewEvent.OnCurrentPasswordChanged(it)) }
            ProfilePassword(
                viewState.newPassword, stringResource(Res.string.profile_new_password),
                viewState.canSubmit,
                viewState.errorTextFor(ProfileOperation.Password, ProfileProblem.InvalidNewPassword),
            ) { eventHandler(ProfileViewEvent.OnNewPasswordChanged(it)) }
            ProfileText(stringResource(Res.string.profile_new_password_length))
            PrimaryButton(
                stringResource(Res.string.profile_change_password), { eventHandler(ProfileViewEvent.OnChangePasswordSubmitted) },
                isEnabled = viewState.canChangePassword, isSubmitting = viewState.operation == ProfileOperation.Password,
            )
            val error = viewState.error
            if (error != null && viewState.dialog == null &&
                viewState.failedOperation !in setOf(ProfileOperation.Load, ProfileOperation.Name, ProfileOperation.Avatar) &&
                (viewState.failedOperation != ProfileOperation.Password ||
                    error !in setOf(ProfileProblem.InvalidCurrentPassword, ProfileProblem.InvalidNewPassword))) {
                ProfileText(stringResource(error.resource), live = true)
            }
            viewState.notice?.let {
                val message = when (it) {
                    ProfileNotice.NameSaved -> Res.string.profile_name_saved
                    ProfileNotice.AvatarSaved -> Res.string.profile_avatar_saved
                    ProfileNotice.AvatarRemoved -> Res.string.profile_avatar_removed
                    ProfileNotice.DeletionCheckAvailable -> null
                }
                if (message != null) ProfileText(stringResource(message), live = true)
            }
        }
        policyOpenError?.let { ProfileText(it, live = true) }
        val policy = viewState.policy
        if (policy != null) {
            TertiaryButton(
                stringResource(Res.string.profile_policy, policy.version), { eventHandler(ProfileViewEvent.OnOpenPolicy) },
            )
        } else if (viewState.isOnline && user != null) {
            TertiaryButton(
                stringResource(Res.string.profile_policy_retry), { eventHandler(ProfileViewEvent.OnLoad) },
                isEnabled = viewState.operation == null,
            )
        }
        DestructiveButton(
            stringResource(Res.string.auth_logout), { eventHandler(ProfileViewEvent.OnLogoutRequested) },
            isEnabled = viewState.operation == null && viewState.dialog == null && !viewState.isPickerPending,
        )
        if (user != null) DestructiveButton(
            stringResource(Res.string.profile_delete_account), { eventHandler(ProfileViewEvent.OnDeleteAccountRequested) },
            isEnabled = viewState.canSubmit,
        )
        if (viewState.isDeletionUncertain && viewState.dialog == null) {
            ProfileDeletionStatus(viewState)
            PrimaryButton(
                stringResource(Res.string.profile_check_deletion), { eventHandler(ProfileViewEvent.OnCheckDeletion) },
                isEnabled = viewState.isOnline && viewState.operation == null,
                isSubmitting = viewState.operation == ProfileOperation.Deletion,
            )
        }
    }
}

@Composable
internal fun ProfileConfirmation(viewState: ProfileViewState, dialog: ProfileDialog, eventHandler: (ProfileViewEvent) -> Unit) {
    val busy = viewState.operation != null
    val dim = RoyTheme.dimensions
    val title = when (dialog) {
        ProfileDialog.AvatarRemoval -> Res.string.profile_remove_photo
        ProfileDialog.Logout -> Res.string.profile_logout_title
        ProfileDialog.Deletion -> Res.string.profile_delete_title
    }
    val body = when (dialog) {
        ProfileDialog.AvatarRemoval -> Res.string.profile_remove_photo_body
        ProfileDialog.Logout -> if (viewState.isOnline) Res.string.profile_logout_body else Res.string.profile_logout_offline_body
        ProfileDialog.Deletion -> Res.string.profile_delete_body
    }
    val confirm = when (dialog) {
        ProfileDialog.AvatarRemoval -> Res.string.profile_remove_photo
        ProfileDialog.Logout -> Res.string.auth_logout
        ProfileDialog.Deletion -> Res.string.profile_delete_account
    }
    val dismiss = { eventHandler(ProfileViewEvent.OnDialogDismissed) }
    Dialog(dismiss, properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = false)) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().imePadding()
                .background(RoyTheme.colors.surfaceElevated, RoundedCornerShape(dim.radii.dialog))
                .verticalScroll(rememberScrollState()).padding(dim.spacing.large),
            verticalArrangement = Arrangement.spacedBy(dim.spacing.compact),
        ) {
            ProfileHeading(stringResource(title))
            ProfileText(stringResource(body))
            if (dialog == ProfileDialog.Deletion && !viewState.isDeletionUncertain) {
                ProfilePassword(
                    viewState.deletionPassword, stringResource(Res.string.profile_current_password), !busy,
                    viewState.errorTextFor(ProfileOperation.Deletion, ProfileProblem.InvalidCurrentPassword),
                ) { eventHandler(ProfileViewEvent.OnDeletionPasswordChanged(it)) }
            }
            if (dialog == ProfileDialog.Deletion && viewState.isDeletionUncertain) {
                ProfileDeletionStatus(viewState)
                PrimaryButton(
                    stringResource(Res.string.profile_check_deletion), { eventHandler(ProfileViewEvent.OnCheckDeletion) },
                    isEnabled = viewState.isOnline && !busy, isSubmitting = busy,
                )
            } else {
                DestructiveButton(
                    stringResource(confirm), { eventHandler(ProfileViewEvent.OnDialogConfirmed) },
                    isEnabled = !busy && (dialog == ProfileDialog.Logout || viewState.canSubmit) &&
                        (dialog != ProfileDialog.Deletion || viewState.canDelete),
                    isSubmitting = busy,
                )
            }
            viewState.error?.takeUnless { it == ProfileProblem.InvalidCurrentPassword || it == ProfileProblem.DeletionUncertain }
                ?.let { ProfileText(stringResource(it.resource), live = true) }
            SecondaryButton(stringResource(Res.string.profile_cancel), dismiss, isEnabled = !busy)
        }
    }
}

@Composable
private fun ProfileDeletionStatus(viewState: ProfileViewState) {
    val message = if (viewState.notice == ProfileNotice.DeletionCheckAvailable) {
        Res.string.profile_deletion_currently_available
    } else {
        Res.string.profile_deletion_uncertain
    }
    ProfileText(stringResource(message), live = true)
}

@Composable
private fun ProfileAvatarImage(name: String, avatar: ProfileAvatar.Image?) {
    val image by produceState<ImageBitmap?>(null, avatar) {
        value = avatar?.let {
            try { withContext(Dispatchers.Default) { it.bytes.decodeToImageBitmap() } }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { null }
        }
    }
    val shape = CircleShape
    val size = RoyTheme.dimensions.avatarMedium
    val bitmap = image
    if (bitmap != null) {
        Image(bitmap, stringResource(Res.string.profile_photo), Modifier.size(size).clip(shape), contentScale = ContentScale.Crop)
    } else {
        val initials = name.split(Regex("\\s+")).mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")
        Box(Modifier.size(size).background(RoyTheme.colors.secondary, shape), contentAlignment = Alignment.Center) {
            BasicText(initials, style = RoyTheme.typography.headingMedium.copy(color = RoyTheme.colors.textPrimary))
        }
    }
}

@Composable
private fun ProfilePassword(value: String, label: String, enabled: Boolean, error: String?, onChange: (String) -> Unit) {
    RoyTextField(
        value, label, onChange, errorText = error, isEnabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = PasswordVisualTransformation(),
    )
}

@Composable
private fun ProfileViewState.errorTextFor(operation: ProfileOperation, expected: ProfileProblem? = null): String? =
    error?.takeIf { failedOperation == operation && (expected == null || expected == it) }?.let { stringResource(it.resource) }

@Composable
private fun ProfileText(text: String, live: Boolean = false) {
    BasicText(
        text,
        if (live) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
        style = RoyTheme.typography.body.copy(color = RoyTheme.colors.textSecondary),
    )
}

@Composable
private fun ProfileHeading(text: String) {
    BasicText(
        text, Modifier.semantics { heading() },
        style = RoyTheme.typography.headingMedium.copy(color = RoyTheme.colors.textPrimary),
    )
}

private val ProfileProblem.resource
    get() = when (this) {
        ProfileProblem.InvalidName -> Res.string.profile_invalid_name
        ProfileProblem.InvalidCurrentPassword -> Res.string.profile_invalid_current
        ProfileProblem.InvalidNewPassword -> Res.string.profile_new_password_length
        ProfileProblem.InvalidImage -> Res.string.profile_invalid_image
        ProfileProblem.TooLarge -> Res.string.profile_image_too_large
        ProfileProblem.AccessDenied -> Res.string.profile_access_denied
        ProfileProblem.Network -> Res.string.auth_network
        ProfileProblem.DeletionUncertain -> Res.string.profile_deletion_uncertain
        ProfileProblem.Unavailable, ProfileProblem.UnexpectedResponse -> Res.string.profile_unavailable
    }
