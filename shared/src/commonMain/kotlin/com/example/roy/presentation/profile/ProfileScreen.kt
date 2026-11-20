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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.example.roy.presentation.core.CollectMviActions
import com.example.roy.presentation.theme.RoyTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.*

@Composable
internal fun ProfileScreen(
    model: ProfileViewModel,
    onOpenNotifications: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
) {
    val state by model.viewState.collectAsStateWithLifecycle()
    val eventHandler = remember(model) { model::handleEvent }
    val uri = LocalUriHandler.current
    val pickImage = rememberProfileImagePicker { eventHandler(ProfileViewEvent.AvatarPicked(it)) }
    LaunchedEffect(model) { eventHandler(ProfileViewEvent.Load) }
    DisposableEffect(model) { onDispose { eventHandler(ProfileViewEvent.ScreenClosed) } }
    CollectMviActions(model.viewAction) { action ->
        when (action) {
            ProfileViewAction.OpenImagePicker -> pickImage()
            is ProfileViewAction.OpenPublishedPolicy -> runCatching { uri.openUri(action.url) }
        }
    }
    ProfileView(state, eventHandler, onOpenNotifications, onOpenNotificationSettings)
}

@Composable
internal fun ProfileView(
    state: ProfileViewState,
    eventHandler: (ProfileViewEvent) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    val spacing = RoyTheme.dimensions.spacing
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(spacing.comfortable),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        ProfileHeading(stringResource(Res.string.tab_profile))
        val user = state.user
        if (!state.isOnline) ProfileText(stringResource(Res.string.profile_offline), live = true)
        if (state.isCached) ProfileText(stringResource(Res.string.profile_cached))
        if (user == null && state.operation == ProfileOperation.Load) {
            LoadingState(LoadingMode.Inline, stringResource(Res.string.profile_loading))
        }
        if (state.error != null && (user == null || state.failedOperation == ProfileOperation.Load)) {
            ErrorState(stringResource(state.error.resource), state.isOnline, { eventHandler(ProfileViewEvent.Load) })
        }
        if (user != null) {
            ProfileAvatarImage(user.displayName, state.avatar)
            SecondaryButton(
                stringResource(Res.string.profile_pick_photo),
                { eventHandler(ProfileViewEvent.PickAvatarRequested) },
                isEnabled = state.canSubmit,
            )
            if (state.operation == ProfileOperation.Avatar) {
                LoadingState(LoadingMode.Inline, stringResource(Res.string.profile_photo))
            }
            if (state.hasAvatar) TertiaryButton(
                stringResource(Res.string.profile_remove_photo),
                { eventHandler(ProfileViewEvent.DeleteAvatarRequested) },
                isEnabled = state.canSubmit,
            )
            state.error?.takeIf { state.failedOperation == ProfileOperation.Avatar && state.dialog == null }
                ?.let { ProfileText(stringResource(it.resource), live = true) }
            RoyTextField(
                state.nameDraft, stringResource(Res.string.profile_name), { eventHandler(ProfileViewEvent.NameChanged(it)) },
                errorText = state.errorTextFor(ProfileOperation.Name) ?: if (state.isNameInvalid) {
                    stringResource(Res.string.profile_invalid_name)
                } else null,
                isEnabled = state.canSubmit,
            )
            PrimaryButton(
                stringResource(Res.string.profile_save_name), { eventHandler(ProfileViewEvent.SaveName) },
                isEnabled = state.canSaveName, isSubmitting = state.operation == ProfileOperation.Name,
            )
            ProfileText(stringResource(Res.string.profile_phone, user.phone))
            ProfileText(stringResource(Res.string.profile_age, user.ageYears))
            TertiaryButton(stringResource(Res.string.profile_notifications), onOpenNotifications)
            TertiaryButton(stringResource(Res.string.profile_notification_settings), onOpenNotificationSettings)
            ProfileHeading(stringResource(Res.string.profile_password_heading))
            ProfilePassword(
                state.currentPassword, stringResource(Res.string.profile_current_password),
                state.canSubmit,
                state.errorTextFor(ProfileOperation.Password, ProfileProblem.InvalidCurrentPassword),
            ) { eventHandler(ProfileViewEvent.CurrentPasswordChanged(it)) }
            ProfilePassword(
                state.newPassword, stringResource(Res.string.profile_new_password),
                state.canSubmit,
                state.errorTextFor(ProfileOperation.Password, ProfileProblem.InvalidNewPassword),
            ) { eventHandler(ProfileViewEvent.NewPasswordChanged(it)) }
            ProfileText(stringResource(Res.string.profile_new_password_length))
            PrimaryButton(
                stringResource(Res.string.profile_change_password), { eventHandler(ProfileViewEvent.ChangePasswordSubmitted) },
                isEnabled = state.canChangePassword, isSubmitting = state.operation == ProfileOperation.Password,
            )
            val error = state.error
            if (error != null && state.dialog == null &&
                state.failedOperation !in setOf(ProfileOperation.Load, ProfileOperation.Name, ProfileOperation.Avatar) &&
                (state.failedOperation != ProfileOperation.Password ||
                    error !in setOf(ProfileProblem.InvalidCurrentPassword, ProfileProblem.InvalidNewPassword))) {
                ProfileText(stringResource(error.resource), live = true)
            }
            state.notice?.let {
                val message = when (it) {
                    ProfileNotice.NameSaved -> Res.string.profile_name_saved
                    ProfileNotice.AvatarSaved -> Res.string.profile_avatar_saved
                    ProfileNotice.AvatarRemoved -> Res.string.profile_avatar_removed
                    ProfileNotice.DeletionCheckAvailable -> null
                }
                if (message != null) ProfileText(stringResource(message), live = true)
            }
        }
        val policy = state.policy
        if (policy != null) {
            TertiaryButton(
                stringResource(Res.string.profile_policy, policy.version), { eventHandler(ProfileViewEvent.OpenPolicy) },
            )
        } else if (state.isOnline && user != null) {
            TertiaryButton(
                stringResource(Res.string.profile_policy_retry), { eventHandler(ProfileViewEvent.Load) },
                isEnabled = state.operation == null,
            )
        }
        DestructiveButton(
            stringResource(Res.string.auth_logout), { eventHandler(ProfileViewEvent.LogoutRequested) },
            isEnabled = state.operation == null && state.dialog == null && !state.isPickerPending,
        )
        if (user != null) DestructiveButton(
            stringResource(Res.string.profile_delete_account), { eventHandler(ProfileViewEvent.DeleteAccountRequested) },
            isEnabled = state.canSubmit,
        )
        if (state.isDeletionUncertain && state.dialog == null) {
            ProfileDeletionStatus(state)
            PrimaryButton(
                stringResource(Res.string.profile_check_deletion), { eventHandler(ProfileViewEvent.CheckDeletion) },
                isEnabled = state.isOnline && state.operation == null,
                isSubmitting = state.operation == ProfileOperation.Deletion,
            )
        }
    }
    state.dialog?.let { ProfileConfirmation(state, it, eventHandler) }
}

@Composable
private fun ProfileConfirmation(state: ProfileViewState, dialog: ProfileDialog, eventHandler: (ProfileViewEvent) -> Unit) {
    val busy = state.operation != null
    val dim = RoyTheme.dimensions
    val title = when (dialog) {
        ProfileDialog.AvatarRemoval -> Res.string.profile_remove_photo
        ProfileDialog.Logout -> Res.string.profile_logout_title
        ProfileDialog.Deletion -> Res.string.profile_delete_title
    }
    val body = when (dialog) {
        ProfileDialog.AvatarRemoval -> Res.string.profile_remove_photo_body
        ProfileDialog.Logout -> if (state.isOnline) Res.string.profile_logout_body else Res.string.profile_logout_offline_body
        ProfileDialog.Deletion -> Res.string.profile_delete_body
    }
    val confirm = when (dialog) {
        ProfileDialog.AvatarRemoval -> Res.string.profile_remove_photo
        ProfileDialog.Logout -> Res.string.auth_logout
        ProfileDialog.Deletion -> Res.string.profile_delete_account
    }
    val dismiss = { eventHandler(ProfileViewEvent.DialogDismissed) }
    Dialog(dismiss, properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = false)) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().imePadding()
                .background(RoyTheme.colors.surfaceElevated, RoundedCornerShape(dim.radii.dialog))
                .verticalScroll(rememberScrollState()).padding(dim.spacing.large),
            verticalArrangement = Arrangement.spacedBy(dim.spacing.compact),
        ) {
            ProfileHeading(stringResource(title))
            ProfileText(stringResource(body))
            if (dialog == ProfileDialog.Deletion && !state.isDeletionUncertain) {
                ProfilePassword(
                    state.deletionPassword, stringResource(Res.string.profile_current_password), !busy,
                    state.errorTextFor(ProfileOperation.Deletion, ProfileProblem.InvalidCurrentPassword),
                ) { eventHandler(ProfileViewEvent.DeletionPasswordChanged(it)) }
            }
            if (dialog == ProfileDialog.Deletion && state.isDeletionUncertain) {
                ProfileDeletionStatus(state)
                PrimaryButton(
                    stringResource(Res.string.profile_check_deletion), { eventHandler(ProfileViewEvent.CheckDeletion) },
                    isEnabled = state.isOnline && !busy, isSubmitting = busy,
                )
            } else {
                DestructiveButton(
                    stringResource(confirm), { eventHandler(ProfileViewEvent.DialogConfirmed) },
                    isEnabled = !busy && (dialog == ProfileDialog.Logout || state.canSubmit) &&
                        (dialog != ProfileDialog.Deletion || state.canDelete),
                    isSubmitting = busy,
                )
            }
            state.error?.takeUnless { it == ProfileProblem.InvalidCurrentPassword || it == ProfileProblem.DeletionUncertain }
                ?.let { ProfileText(stringResource(it.resource), live = true) }
            SecondaryButton(stringResource(Res.string.profile_cancel), dismiss, isEnabled = !busy)
        }
    }
}

@Composable
private fun ProfileDeletionStatus(state: ProfileViewState) {
    val message = if (state.notice == ProfileNotice.DeletionCheckAvailable) {
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
