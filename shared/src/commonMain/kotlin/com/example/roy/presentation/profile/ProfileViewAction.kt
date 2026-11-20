package com.example.roy.presentation.profile


internal sealed class ProfileViewAction {
    data object OpenNotifications : ProfileViewAction()
    data object OpenNotificationSettings : ProfileViewAction()
    data object OpenImagePicker : ProfileViewAction()
    data class OpenPublishedPolicy(val url: String) : ProfileViewAction()
}
