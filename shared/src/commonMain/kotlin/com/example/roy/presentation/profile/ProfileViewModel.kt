package com.example.roy.presentation.profile

import androidx.lifecycle.viewModelScope
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.AuthUser
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.SessionStatus
import com.example.roy.domain.profile.PickImageResult
import com.example.roy.domain.profile.ProfileAvatar
import com.example.roy.domain.profile.ProfileProblem
import com.example.roy.domain.profile.ProfileRepository
import com.example.roy.domain.profile.ProfileResult
import com.example.roy.domain.profile.validationError
import com.example.roy.presentation.core.BaseViewModel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

internal enum class ProfileOperation { Load, Name, Avatar, Password, Logout, Deletion }
internal enum class ProfileDialog { AvatarRemoval, Logout, Deletion }
internal enum class ProfileNotice { NameSaved, AvatarSaved, AvatarRemoved, DeletionCheckAvailable }

internal class ProfileViewModel(
    private val scopeId: Long,
    private val session: SessionCoordinator,
    private val repository: ProfileRepository,
    connectivity: Flow<Boolean>,
    private val logout: suspend () -> AuthResult<Unit> = { session.logout(scopeId) },
) : BaseViewModel<ProfileViewState, ProfileViewEvent, ProfileViewAction>(ProfileViewState()) {
    private val isCurrent get() = session.state.value == SessionStatus.Active(scopeId)
    private var reloadAfterReconnect = false

    init {
        viewModelScope.launch {
            session.state.collect { if (!isCurrent) updateState { ProfileViewState(isOnline = it.isOnline) } }
        }
        viewModelScope.launch {
            connectivity.collect { online ->
                val wasOnline = viewState.value.isOnline
                updateState { it.copy(isOnline = online, isCached = it.isCached || (!online && it.user != null)) }
                if (online && !wasOnline) {
                    reloadAfterReconnect = true
                    onLoad()
                }
            }
        }
    }

    override fun handleEvent(event: ProfileViewEvent) {
        if (!isCurrent) return
        when (event) {
            ProfileViewEvent.OnNotificationsRequested -> onNotificationsRequested()
            ProfileViewEvent.OnNotificationSettingsRequested -> onNotificationSettingsRequested()
            ProfileViewEvent.OnLoad -> onLoad()
            ProfileViewEvent.OnScreenClosed -> onScreenClosed()
            is ProfileViewEvent.OnNameChanged -> onNameChanged(event)
            ProfileViewEvent.OnSaveName -> onSaveName()
            ProfileViewEvent.OnPickAvatarRequested -> onPickAvatarRequested()
            is ProfileViewEvent.OnAvatarPicked -> onAvatarPicked(event.result)
            ProfileViewEvent.OnDeleteAvatarRequested -> onDeleteAvatarRequested()
            is ProfileViewEvent.OnCurrentPasswordChanged -> onCurrentPasswordChanged(event)
            is ProfileViewEvent.OnNewPasswordChanged -> onNewPasswordChanged(event)
            is ProfileViewEvent.OnDeletionPasswordChanged -> onDeletionPasswordChanged(event)
            ProfileViewEvent.OnChangePasswordSubmitted -> onChangePasswordSubmitted()
            ProfileViewEvent.OnLogoutRequested -> onLogoutRequested()
            ProfileViewEvent.OnDeleteAccountRequested -> onDeleteAccountRequested()
            ProfileViewEvent.OnDialogConfirmed -> onDialogConfirmed()
            ProfileViewEvent.OnDialogDismissed -> onDialogDismissed()
            ProfileViewEvent.OnCheckDeletion -> onCheckDeletion()
            ProfileViewEvent.OnOpenPolicy -> onOpenPolicy()
        }
    }

    private fun onNotificationsRequested() {
        openNotifications()
    }

    private fun onNotificationSettingsRequested() {
        openNotificationSettings()
    }

    private fun onScreenClosed() {
        updateState {
            it.copy(currentPassword = "", newPassword = "", deletionPassword = "", isPickerPending = false)
        }
    }

    private fun onNameChanged(event: ProfileViewEvent.OnNameChanged) {
        edit(ProfileOperation.Name) { it.copy(nameDraft = event.value) }
    }

    private fun onPickAvatarRequested() {
        pickAvatar()
    }

    private fun onDeleteAvatarRequested() {
        openDialog(ProfileDialog.AvatarRemoval)
    }

    private fun onCurrentPasswordChanged(event: ProfileViewEvent.OnCurrentPasswordChanged) {
        edit(ProfileOperation.Password) { it.copy(currentPassword = event.value) }
    }

    private fun onNewPasswordChanged(event: ProfileViewEvent.OnNewPasswordChanged) {
        edit(ProfileOperation.Password) { it.copy(newPassword = event.value) }
    }

    private fun onDeletionPasswordChanged(event: ProfileViewEvent.OnDeletionPasswordChanged) {
        edit(ProfileOperation.Deletion) { it.copy(deletionPassword = event.value) }
    }

    private fun onChangePasswordSubmitted() {
        changePassword()
    }

    private fun onLogoutRequested() {
        openDialog(ProfileDialog.Logout)
    }

    private fun onDeleteAccountRequested() {
        openDialog(ProfileDialog.Deletion)
    }

    private fun onDialogConfirmed() {
        confirmDialog()
    }

    private fun onDialogDismissed() {
        val state = viewState.value
        if (state.operation == null) {
            updateState { it.copy(dialog = null, deletionPassword = "", error = null, failedOperation = null) }
        }
    }

    private fun onOpenPolicy() {
        val state = viewState.value
        state.policy?.let { policy ->
            viewModelScope.launch { sendAction(ProfileViewAction.OpenPublishedPolicy(policy.url)) }
        }
    }

    private fun openNotifications() {
        viewModelScope.launch { sendAction(ProfileViewAction.OpenNotifications) }
    }

    private fun openNotificationSettings() {
        viewModelScope.launch { sendAction(ProfileViewAction.OpenNotificationSettings) }
    }

    private fun onLoad() {
        val state = viewState.value
        if (!isCurrent || state.operation != null) return
        reloadAfterReconnect = false
        if (state.isDeletionUncertain) return
        runOperation(ProfileOperation.Load) {
            if (!state.isOnline) {
                loadCached()
                return@runOperation
            }
            val user = session.readProtected(scopeId, repository::getProfile).resolve(ProfileOperation.Load)
            if (user == null) {
                loadCached()
                return@runOperation
            }
            if (!session.updateCachedUser(scopeId, user)) {
                if (isCurrent) {
                    fail(ProfileOperation.Load, ProfileProblem.UnexpectedResponse)
                    loadCached()
                }
                return@runOperation
            }
            updateState {
                val draft = it.nameDraft.takeIf { current -> it.user != null && current != it.user.displayName }
                it.copy(
                    user = user, nameDraft = draft ?: user.displayName,
                    hasAvatar = user.avatarUrl != null, isCached = !it.isOnline,
                )
            }
            if (user.avatarUrl != null) readAvatar(user.id, state.avatar?.etag)
            else updateState { it.copy(avatar = null) }
            val policy = (session.policy() as? AuthResult.Success)?.value
            if (isCurrent) updateState { it.copy(policy = policy ?: it.policy) }
        }
    }

    private suspend fun loadCached() {
        if (viewState.value.user != null) {
            updateState { it.copy(isCached = true) }
            return
        }
        val user = session.cachedUser(scopeId) ?: return
        if (isCurrent) updateState {
            it.copy(user = user, nameDraft = user.displayName, hasAvatar = user.avatarUrl != null, isCached = true)
        }
    }

    private fun edit(operation: ProfileOperation, change: (ProfileViewState) -> ProfileViewState) {
        if (!viewState.value.canEdit) return
        updateState {
            val edited = change(it)
            if (it.failedOperation == operation) edited.copy(error = null, failedOperation = null) else edited
        }
    }

    private fun onSaveName() {
        val state = viewState.value
        if (!state.canSaveName) return
        runOperation(ProfileOperation.Name) {
            val updated = session.mutateProtected(scopeId) { repository.updateName(state.nameDraft.trim(), it) }
                .resolve(ProfileOperation.Name) ?: return@runOperation
            if (session.updateCachedUser(scopeId, updated)) {
                updateState { it.copy(user = updated, nameDraft = updated.displayName, notice = ProfileNotice.NameSaved) }
            } else if (isCurrent) {
                fail(ProfileOperation.Name, ProfileProblem.UnexpectedResponse)
            }
        }
    }

    private fun pickAvatar() {
        if (!viewState.value.canSubmit || viewState.value.dialog != null) return
        updateState { it.copy(isPickerPending = true, error = null, failedOperation = null, notice = null) }
        viewModelScope.launch { sendAction(ProfileViewAction.OpenImagePicker) }
    }

    private fun onAvatarPicked(result: PickImageResult) {
        if (!viewState.value.isPickerPending) return
        updateState { it.copy(isPickerPending = false) }
        when (result) {
            PickImageResult.Cancelled -> Unit
            is PickImageResult.Failure -> fail(ProfileOperation.Avatar, result.problem)
            is PickImageResult.Selected -> uploadAvatar(result)
        }
    }

    private fun uploadAvatar(image: PickImageResult.Selected) {
        val user = viewState.value.user ?: return
        val failure = image.validationError() ?: ProfileProblem.Network.takeUnless { viewState.value.canSubmit }
        if (failure != null) {
            fail(ProfileOperation.Avatar, failure)
            return
        }
        runOperation(ProfileOperation.Avatar) {
            val url = session.mutateProtected(scopeId) { repository.uploadAvatar(image, it) }
                .resolve(ProfileOperation.Avatar) ?: return@runOperation
            val updated = user.withAvatar(url)
            if (!session.updateCachedUser(scopeId, updated)) return@runOperation
            updateState { it.copy(user = updated, hasAvatar = true, notice = ProfileNotice.AvatarSaved) }
            readAvatar(user.id, null)
        }
    }

    private suspend fun readAvatar(userId: String, etag: String?) {
        when (val avatar = session.readProtected(scopeId) { repository.readAvatar(userId, etag, it) }
            .resolve(ProfileOperation.Avatar)) {
            is ProfileAvatar.Image -> updateState { it.copy(avatar = avatar) }
            ProfileAvatar.Missing -> updateState { it.copy(avatar = null) }
            ProfileAvatar.Unchanged, null -> Unit
        }
    }

    private fun changePassword() {
        val state = viewState.value
        val user = state.user ?: return
        if (!state.canChangePassword || state.dialog != null) return
        runOperation(ProfileOperation.Password) {
            session.mutateProtected(scopeId) { repository.changePassword(state.currentPassword, state.newPassword, it) }
                .resolve(ProfileOperation.Password) ?: return@runOperation
            updateState { it.copy(currentPassword = "", newPassword = "") }
            finishRevocation(user.id, expired = true, ProfileOperation.Password)
        }
    }

    private fun openDialog(dialog: ProfileDialog) {
        val state = viewState.value
        if ((state.canSubmit || dialog == ProfileDialog.Logout && state.operation == null) && state.dialog == null) {
            updateState { it.copy(dialog = dialog, error = null, failedOperation = null, notice = null) }
        }
    }

    private fun confirmDialog() {
        val state = viewState.value
        if (!state.canSubmit && (state.dialog != ProfileDialog.Logout || state.operation != null)) return
        when (state.dialog) {
            ProfileDialog.AvatarRemoval -> runOperation(ProfileOperation.Avatar) {
                val user = state.user ?: return@runOperation
                session.mutateProtected(scopeId, repository::deleteAvatar)
                    .resolve(ProfileOperation.Avatar) ?: return@runOperation
                val updated = user.withAvatar(null)
                if (!session.updateCachedUser(scopeId, updated)) return@runOperation
                updateState { it.copy(
                    user = updated, hasAvatar = false, avatar = null, dialog = null,
                    notice = ProfileNotice.AvatarRemoved,
                ) }
            }
            ProfileDialog.Logout -> runOperation(ProfileOperation.Logout) {
                val result = if (state.isOnline) logout() else session.logout(scopeId)
                if (result is AuthResult.Failure && isCurrent) fail(ProfileOperation.Logout, result.error.code.problem())
            }
            ProfileDialog.Deletion -> deleteAccount()
            null -> Unit
        }
    }

    private fun deleteAccount() {
        val state = viewState.value
        val user = state.user ?: return
        if (!state.canDelete) return
        runOperation(ProfileOperation.Deletion) {
            val result = session.mutateProtected(scopeId) { repository.deleteAccount(state.deletionPassword, true, it) }
            val success = result.resolve(ProfileOperation.Deletion)
            if (success != null) {
                updateState { it.copy(deletionPassword = "") }
                finishRevocation(user.id, expired = false, ProfileOperation.Deletion)
            } else if ((result as? AuthResult.Success)?.value == ProfileResult.Rejected(ProfileProblem.DeletionUncertain) && isCurrent) {
                updateState { it.copy(isDeletionUncertain = true, deletionPassword = "") }
            }
        }
    }

    private fun onCheckDeletion() {
        if (!viewState.value.isDeletionUncertain || !viewState.value.isOnline) return
        runOperation(ProfileOperation.Deletion) {
            val result = session.readProtected(scopeId, repository::getProfile)
            if (!isCurrent) return@runOperation
            val user = result.resolve(ProfileOperation.Deletion) ?: return@runOperation
            if (user.id == viewState.value.user?.id) {
                updateState { it.copy(notice = ProfileNotice.DeletionCheckAvailable) }
            } else {
                fail(ProfileOperation.Deletion, ProfileProblem.UnexpectedResponse)
            }
        }
    }

    private suspend fun finishRevocation(userId: String, expired: Boolean, operation: ProfileOperation) {
        val result = session.completeRevokedProfileSession(scopeId, userId, expired)
        if (result is AuthResult.Failure && isCurrent) fail(operation, result.error.code.problem())
    }

    private fun runOperation(operation: ProfileOperation, execute: suspend () -> Unit) {
        if (!isCurrent || viewState.value.operation != null) return
        updateState { it.copy(operation = operation, error = null, failedOperation = null, notice = null) }
        viewModelScope.launch {
            try {
                execute()
            } finally {
                if (isCurrent) {
                    updateState { it.copy(operation = null) }
                    if (reloadAfterReconnect && viewState.value.isOnline) onLoad()
                }
            }
        }
    }

    private suspend fun <T> AuthResult<ProfileResult<T>>.resolve(operation: ProfileOperation): T? {
        viewModelScope.coroutineContext.ensureActive()
        if (!isCurrent) return null
        return when (this) {
            is AuthResult.Failure -> null.also { fail(operation, error.code.problem()) }
            is AuthResult.Success -> when (val response = value) {
                is ProfileResult.Value -> response.value
                is ProfileResult.Rejected -> null.also { fail(operation, response.problem) }
            }
        }
    }

    private fun fail(operation: ProfileOperation, problem: ProfileProblem) {
        updateState { it.copy(error = problem, failedOperation = operation) }
    }
}

internal fun String.validSize(range: IntRange): Boolean {
    if (length > range.last * 2) return false
    var index = 0
    var codePoints = 0
    while (index < length) {
        val char = this[index++]
        if (char == '\u0000' || char.isLowSurrogate()) return false
        if (char.isHighSurrogate() && getOrNull(index++)?.isLowSurrogate() != true) return false
        codePoints++
    }
    return codePoints in range
}

private fun AuthUser.withAvatar(url: String?) = AuthUser(id, displayName, url, phone, ageYears, messageMutedUntil, createdAt)

private fun AuthErrorCode.problem(): ProfileProblem = when (this) {
    AuthErrorCode.Network -> ProfileProblem.Network
    AuthErrorCode.AuthRequired, AuthErrorCode.SessionExpired -> ProfileProblem.AccessDenied
    AuthErrorCode.ServiceUnavailable, AuthErrorCode.RateLimited -> ProfileProblem.Unavailable
    else -> ProfileProblem.UnexpectedResponse
}
