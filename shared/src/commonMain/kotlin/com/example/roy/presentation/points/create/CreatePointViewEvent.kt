package com.example.roy.presentation.points.create


internal sealed class CreatePointViewEvent {
    data object OnClose : CreatePointViewEvent()
    data object OnReloadPresets : CreatePointViewEvent()
    data object OnReloadContext : CreatePointViewEvent()
    data class OnSelectPreset(val key: String) : CreatePointViewEvent()
    data object OnAcceptSuggestion : CreatePointViewEvent()
    data object OnEditAddress : CreatePointViewEvent()
    data class OnEditDialogText(val text: String) : CreatePointViewEvent()
    data object OnConfirmDialog : CreatePointViewEvent()
    data object OnCancelDialog : CreatePointViewEvent()
    data object OnPublish : CreatePointViewEvent()
}
