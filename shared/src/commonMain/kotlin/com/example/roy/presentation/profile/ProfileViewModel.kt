package com.example.roy.presentation.profile

import androidx.lifecycle.viewModelScope
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.AuthUser
import com.example.roy.domain.auth.PrivacyPolicy
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
internal enum class ProfileNotice { NameSaved, AvatarSaved, AvatarRemoved }

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

internal sealed class ProfileViewEvent {
    data object Load : ProfileViewEvent()
    data object ScreenClosed : ProfileViewEvent()
    data class NameChanged(val value: String) : ProfileViewEvent()
    data object SaveName : ProfileViewEvent()
    data object PickAvatarRequested : ProfileViewEvent()
    data class AvatarPicked(val result: PickImageResult) : ProfileViewEvent()
    data object DeleteAvatarRequested : ProfileViewEvent()
    class CurrentPasswordChanged(val value: String) : ProfileViewEvent()
    class NewPasswordChanged(val value: String) : ProfileViewEvent()
    class DeletionPasswordChanged(val value: String) : ProfileViewEvent()
    data object ChangePasswordSubmitted : ProfileViewEvent()
    data object LogoutRequested : ProfileViewEvent()
    data object DeleteAccountRequested : ProfileViewEvent()
    data object DialogConfirmed : ProfileViewEvent()
    data object DialogDismissed : ProfileViewEvent()
    data object CheckDeletion : ProfileViewEvent()
    data object OpenPolicy : ProfileViewEvent()
}

internal sealed class ProfileViewAction {
    data object OpenImagePicker : ProfileViewAction()
    data class OpenPublishedPolicy(val url: String) : ProfileViewAction()
}

internal class ProfileViewModel(
    private val scopeId: Long,
    private val session: SessionCoordinator,
    private val repository: ProfileRepository,
    connectivity: Flow<Boolean>,
    private val logout: suspend () -> AuthResult<Unit> = session::logout,
) : BaseViewModel<ProfileViewState, ProfileViewEvent, ProfileViewAction>(ProfileViewState()) {
    private val isCurrent get() = session.state.value == SessionStatus.Active(scopeId)

    init {
        viewModelScope.launch {
            session.state.collect { if (!isCurrent) updateState { ProfileViewState(isOnline = it.isOnline) } }
        }
        viewModelScope.launch {
            connectivity.collect { online ->
                val wasOnline = viewState.value.isOnline
                updateState { it.copy(isOnline = online) }
                if (online && !wasOnline && (viewState.value.isCached || viewState.value.user == null)) load()
            }
        }
    }

    override fun handleEvent(event: ProfileViewEvent) {
        if (!isCurrent) return
        val state = viewState.value
        when (event) {
            ProfileViewEvent.Load -> load()
            ProfileViewEvent.ScreenClosed -> updateState {
                it.copy(currentPassword = "", newPassword = "", deletionPassword = "", isPickerPending = false)
            }
            is ProfileViewEvent.NameChanged -> edit(ProfileOperation.Name) { it.copy(nameDraft = event.value) }
            ProfileViewEvent.SaveName -> saveName()
            ProfileViewEvent.PickAvatarRequested -> pickAvatar()
            is ProfileViewEvent.AvatarPicked -> onAvatarPicked(event.result)
            ProfileViewEvent.DeleteAvatarRequested -> openDialog(ProfileDialog.AvatarRemoval)
            is ProfileViewEvent.CurrentPasswordChanged -> edit(ProfileOperation.Password) { it.copy(currentPassword = event.value) }
            is ProfileViewEvent.NewPasswordChanged -> edit(ProfileOperation.Password) { it.copy(newPassword = event.value) }
            is ProfileViewEvent.DeletionPasswordChanged -> edit(ProfileOperation.Deletion) { it.copy(deletionPassword = event.value) }
            ProfileViewEvent.ChangePasswordSubmitted -> changePassword()
            ProfileViewEvent.LogoutRequested -> openDialog(ProfileDialog.Logout)
            ProfileViewEvent.DeleteAccountRequested -> openDialog(ProfileDialog.Deletion)
            ProfileViewEvent.DialogConfirmed -> confirmDialog()
            ProfileViewEvent.DialogDismissed -> if (state.operation == null) {
                updateState { it.copy(dialog = null, deletionPassword = "", error = null, failedOperation = null) }
            }
            ProfileViewEvent.CheckDeletion -> checkDeletion()
            ProfileViewEvent.OpenPolicy -> state.policy?.let { policy ->
                viewModelScope.launch { sendAction(ProfileViewAction.OpenPublishedPolicy(policy.url)) }
            }
        }
    }

    private fun load() {
        val state = viewState.value
        if (state.isDeletionUncertain) return
        runOperation(ProfileOperation.Load) {
            if (!state.isOnline) {
                loadCached()
                return@runOperation
            }
            val user = session.readProtected(scopeId, repository::getProfile).resolve(ProfileOperation.Load)
            if (user == null) {
                if (viewState.value.error == ProfileProblem.Network) loadCached()
                return@runOperation
            }
            updateState {
                val draft = it.nameDraft.takeIf { current -> it.user != null && current != it.user.displayName }
                it.copy(user = user, nameDraft = draft ?: user.displayName, hasAvatar = user.avatarUrl != null, isCached = false)
            }
            if (user.avatarUrl != null) readAvatar(user.id, state.avatar?.etag)
            else updateState { it.copy(avatar = null) }
            val policy = (session.policy() as? AuthResult.Success)?.value
            if (isCurrent) updateState { it.copy(policy = policy ?: it.policy) }
        }
    }

    private suspend fun loadCached() {
        if (viewState.value.user != null) return
        val user = session.cachedUser(scopeId) ?: return
        if (isCurrent) updateState { it.copy(user = user, nameDraft = user.displayName, isCached = true) }
    }

    private fun edit(operation: ProfileOperation, change: (ProfileViewState) -> ProfileViewState) {
        if (!viewState.value.canEdit) return
        updateState {
            val edited = change(it)
            if (it.failedOperation == operation) edited.copy(error = null, failedOperation = null) else edited
        }
    }

    private fun saveName() {
        val state = viewState.value
        if (!state.canSaveName) return
        runOperation(ProfileOperation.Name) {
            val updated = session.mutateProtected(scopeId) { repository.updateName(state.nameDraft.trim(), it) }
                .resolve(ProfileOperation.Name) ?: return@runOperation
            if (updated.id == state.user?.id) {
                updateState { it.copy(user = updated, nameDraft = updated.displayName, notice = ProfileNotice.NameSaved) }
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
        val failure = image.validationError() ?: ProfileProblem.Network.takeUnless { viewState.value.isOnline }
        if (failure != null) {
            fail(ProfileOperation.Avatar, failure)
            return
        }
        runOperation(ProfileOperation.Avatar) {
            val url = session.mutateProtected(scopeId) { repository.uploadAvatar(image, it) }
                .resolve(ProfileOperation.Avatar) ?: return@runOperation
            updateState { it.copy(user = user.withAvatar(url), hasAvatar = true, notice = ProfileNotice.AvatarSaved) }
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
                session.mutateProtected(scopeId, repository::deleteAvatar)
                    .resolve(ProfileOperation.Avatar) ?: return@runOperation
                updateState { it.copy(
                    user = it.user?.withAvatar(null), hasAvatar = false, avatar = null, dialog = null,
                    notice = ProfileNotice.AvatarRemoved,
                ) }
            }
            ProfileDialog.Logout -> runOperation(ProfileOperation.Logout) {
                val result = if (state.isOnline) logout() else session.logout()
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

    private fun checkDeletion() {
        if (!viewState.value.isDeletionUncertain || !viewState.value.isOnline) return
        runOperation(ProfileOperation.Deletion) {
            val result = session.readProtected(scopeId, repository::getProfile)
            if (!isCurrent) return@runOperation
            val user = result.resolve(ProfileOperation.Deletion) ?: return@runOperation
            updateState { it.copy(isDeletionUncertain = false, user = user, dialog = null, deletionPassword = "") }
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
                if (isCurrent) updateState { it.copy(operation = null) }
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

private fun String.validSize(range: IntRange): Boolean {
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
