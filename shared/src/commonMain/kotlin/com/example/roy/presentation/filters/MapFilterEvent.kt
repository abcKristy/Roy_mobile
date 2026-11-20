package com.example.roy.presentation.filters


internal sealed class MapFilterEvent {
    data class OnTopic(val key: String?) : MapFilterEvent()
    data class OnCustomLabel(val label: String) : MapFilterEvent()
    data class OnPeopleMin(val value: String) : MapFilterEvent()
    data class OnPeopleMax(val value: String) : MapFilterEvent()
    data object OnRetryPresets : MapFilterEvent()
    data object OnApply : MapFilterEvent()
    data object OnReset : MapFilterEvent()
    data object OnBack : MapFilterEvent()
}
