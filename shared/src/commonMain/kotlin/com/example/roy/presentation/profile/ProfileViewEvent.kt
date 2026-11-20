package com.example.roy.presentation.profile

import com.example.roy.domain.profile.PickImageResult

internal sealed class ProfileViewEvent {
    data object OnNotificationsRequested : ProfileViewEvent()
    data object OnNotificationSettingsRequested : ProfileViewEvent()
    data object OnLoad : ProfileViewEvent()
    data object OnScreenClosed : ProfileViewEvent()
    data class OnNameChanged(val value: String) : ProfileViewEvent()
    data object OnSaveName : ProfileViewEvent()
    data object OnPickAvatarRequested : ProfileViewEvent()
    data class OnAvatarPicked(val result: PickImageResult) : ProfileViewEvent()
    data object OnDeleteAvatarRequested : ProfileViewEvent()
    class OnCurrentPasswordChanged(val value: String) : ProfileViewEvent()
    class OnNewPasswordChanged(val value: String) : ProfileViewEvent()
    class OnDeletionPasswordChanged(val value: String) : ProfileViewEvent()
    data object OnChangePasswordSubmitted : ProfileViewEvent()
    data object OnLogoutRequested : ProfileViewEvent()
    data object OnDeleteAccountRequested : ProfileViewEvent()
    data object OnDialogConfirmed : ProfileViewEvent()
    data object OnDialogDismissed : ProfileViewEvent()
    data object OnCheckDeletion : ProfileViewEvent()
    data object OnOpenPolicy : ProfileViewEvent()
}
