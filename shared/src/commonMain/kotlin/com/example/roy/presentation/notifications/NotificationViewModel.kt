package com.example.roy.presentation.notifications

import androidx.lifecycle.viewModelScope
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.SessionStatus
import com.example.roy.domain.map.Position
import com.example.roy.domain.notifications.NotificationChannel
import com.example.roy.domain.notifications.NotificationEntry
import com.example.roy.domain.notifications.NotificationRepository
import com.example.roy.domain.notifications.NotificationSettings
import com.example.roy.domain.notifications.NotificationSettingsWrite
import com.example.roy.domain.notifications.NotificationTarget
import com.example.roy.presentation.core.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

internal enum class NotificationMode { Settings, Center }
internal enum class NotificationIssue { Offline, Conflict, InvalidCursor, Unavailable, TargetUnavailable }

internal data class NotificationViewState(
    val settings: NotificationSettings? = null,
    val items: List<NotificationEntry> = emptyList(),
    val nextCursor: String? = null,
    val online: Boolean = false,
    val settingsLoading: Boolean = true,
    val centerLoading: Boolean = true,
    val loadingMore: Boolean = false,
    val saving: NotificationChannel? = null,
    val settingsIssue: NotificationIssue? = null,
    val centerIssue: NotificationIssue? = null,
    val permissionBannerDismissed: Boolean = false,
)

internal sealed class NotificationEvent {
    data class Refresh(val mode: NotificationMode) : NotificationEvent()
    data class Change(val channel: NotificationChannel, val enabled: Boolean) : NotificationEvent()
    data class Open(val entryId: String) : NotificationEvent()
    data object More : NotificationEvent()
    data object Back : NotificationEvent()
    data object RequestPermission : NotificationEvent()
    data class DismissPermission(val dismissed: Boolean) : NotificationEvent()
}

internal sealed class NotificationAction {
    data class Open(val target: NotificationTarget) : NotificationAction()
    data object Back : NotificationAction()
    data object RequestPermission : NotificationAction()
}

internal class NotificationViewModel(
    private val scopeId: Long,
    private val session: SessionCoordinator,
    private val repository: NotificationRepository,
    connectivity: Flow<Boolean>,
) : BaseViewModel<NotificationViewState, NotificationEvent, NotificationAction>(NotificationViewState()) {
    private var settingsJob: Job? = null
    private var centerJob: Job? = null
    private var locationDebounce: Job? = null
    private var locationWrite: Job? = null
    private var pendingLocation: Position? = null
    private var lastLocation: Position? = null
    private val reading = mutableSetOf<String>()
    private var centerVisible = false
    private var centerInvalidated = false

    init {
        viewModelScope.launch {
            session.state.collect { if (!current()) clear() }
        }
        viewModelScope.launch {
            connectivity.collect { online ->
                if (!current()) return@collect
                val wasOnline = viewState.value.online
                updateState { it.copy(online = online) }
                if (!online) {
                    locationDebounce?.cancel()
                    pendingLocation = null
                    updateState { it.copy(
                        settingsLoading = false, centerLoading = false, loadingMore = false,
                        settingsIssue = NotificationIssue.Offline, centerIssue = NotificationIssue.Offline,
                    ) }
                } else if (!wasOnline) {
                    loadSettings()
                    if (centerVisible) loadCenter()
                }
            }
        }
    }

    override fun handleEvent(event: NotificationEvent) {
        when (event) {
            is NotificationEvent.Refresh -> when (event.mode) {
                NotificationMode.Settings -> loadSettings()
                NotificationMode.Center -> loadCenter()
            }
            is NotificationEvent.Change -> change(event.channel, event.enabled)
            is NotificationEvent.Open -> open(event.entryId)
            NotificationEvent.More -> viewState.value.nextCursor?.let(::loadCenter)
            NotificationEvent.Back -> viewModelScope.launch { sendAction(NotificationAction.Back) }
            NotificationEvent.RequestPermission -> viewModelScope.launch {
                if (current()) sendAction(NotificationAction.RequestPermission)
            }
            is NotificationEvent.DismissPermission -> updateState { it.copy(permissionBannerDismissed = event.dismissed) }
        }
    }

    fun setCenterVisible(visible: Boolean) {
        centerVisible = visible
    }

    fun invalidateCenter() {
        if (centerVisible) {
            centerInvalidated = true
            loadCenter()
        }
    }

    fun markRead(id: String) {
        if (!current() || !viewState.value.online || viewState.value.items.any { it.id == id && it.readAt != null }) return
        if (!reading.add(id)) return
        viewModelScope.launch {
            try {
                val result = session.mutateProtected(scopeId) { repository.markRead(id, it) }
                currentCoroutineContext().ensureActive()
                if (result is AuthResult.Success && current()) {
                    updateState { state -> state.copy(items = state.items.map { if (it.id == id) result.value else it }) }
                }
            } finally {
                reading.remove(id)
            }
        }
    }

    fun onViewportSettled(position: Position) {
        locationDebounce?.cancel()
        pendingLocation = null
        if (!canSendLocation() || lastLocation == position) return
        locationDebounce = viewModelScope.launch {
            delay(500)
            if (!canSendLocation()) return@launch
            pendingLocation = position
            writeLocation()
        }
    }

    private fun writeLocation() {
        if (locationWrite?.isActive == true) return
        locationWrite = viewModelScope.launch {
            while (canSendLocation()) {
                val position = pendingLocation ?: break
                pendingLocation = null
                if (position == lastLocation) continue
                val result = session.mutateProtected(scopeId) { repository.setLocation(position.latitude, position.longitude, it) }
                if (result is AuthResult.Success && canSendLocation()) lastLocation = position
            }
        }
    }

    private fun canSendLocation() = current() && viewState.value.online &&
        viewState.value.settings?.let { it.extraPushEnabled || it.extraInAppEnabled } == true

    private fun current() = session.state.value == SessionStatus.Active(scopeId)

    private fun clear() {
        settingsJob?.cancel()
        centerJob?.cancel()
        locationDebounce?.cancel()
        locationWrite?.cancel()
        pendingLocation = null
        lastLocation = null
        centerInvalidated = false
        updateState { NotificationViewState() }
    }

    private fun loadSettings() {
        if (!current() || settingsJob?.isActive == true) return
        if (!viewState.value.online) {
            updateState { it.copy(settingsLoading = false, settingsIssue = NotificationIssue.Offline) }
            return
        }
        updateState { it.copy(settingsLoading = true) }
        settingsJob = viewModelScope.launch { readSettings() }
    }

    private suspend fun readSettings(conflict: Boolean = false) {
        val result = session.readProtected(scopeId) { repository.settings(it) }
        currentCoroutineContext().ensureActive()
        if (!current()) return
        when (result) {
            is AuthResult.Success -> {
                applySettings(result.value)
                updateState { state -> state.copy(
                    settingsLoading = false,
                    settingsIssue = when {
                        conflict -> NotificationIssue.Conflict
                        !state.online -> NotificationIssue.Offline
                        else -> null
                    },
                ) }
            }
            is AuthResult.Failure -> updateState { it.copy(
                settingsLoading = false,
                settingsIssue = if (conflict) NotificationIssue.Conflict else issue(result.error.code),
            ) }
        }
    }

    private fun applySettings(settings: NotificationSettings) {
        updateState { it.copy(settings = settings) }
        if (!settings.extraPushEnabled && !settings.extraInAppEnabled) {
            locationDebounce?.cancel()
            pendingLocation = null
            lastLocation = null
        }
    }

    private fun change(channel: NotificationChannel, enabled: Boolean) {
        val state = viewState.value
        val old = state.settings ?: return
        if (!current() || !state.online || settingsJob?.isActive == true) return
        val proposed = when (channel) {
            NotificationChannel.RemindersPush -> old.copy(remindersPushEnabled = enabled)
            NotificationChannel.PersonalPush -> old.copy(personalPushEnabled = enabled)
            NotificationChannel.NearbyPush -> old.copy(extraPushEnabled = enabled)
            NotificationChannel.NearbyInApp -> old.copy(extraInAppEnabled = enabled)
        }
        if (proposed == old) return
        updateState { it.copy(saving = channel, settingsIssue = null) }
        settingsJob = viewModelScope.launch {
            try {
                val result = session.mutateProtected(scopeId) { repository.update(proposed, it) }
                currentCoroutineContext().ensureActive()
                if (!current()) return@launch
                when (result) {
                    is AuthResult.Success -> when (val write = result.value) {
                        is NotificationSettingsWrite.Saved -> applySettings(write.value)
                        is NotificationSettingsWrite.Conflict -> {
                            write.current?.let(::applySettings)
                            readSettings(conflict = true)
                        }
                    }
                    is AuthResult.Failure -> updateState { it.copy(settingsIssue = issue(result.error.code)) }
                }
            } finally {
                if (current()) updateState { it.copy(saving = null) }
            }
        }
    }

    private fun loadCenter(cursor: String? = null) {
        if (!current() || centerJob?.isActive == true) return
        if (!viewState.value.online) {
            updateState { it.copy(centerLoading = false, centerIssue = NotificationIssue.Offline) }
            return
        }
        centerJob = viewModelScope.launch {
            var pageCursor = cursor
            do {
                centerInvalidated = false
                readCenter(pageCursor)
                pageCursor = null
            } while (centerInvalidated && current() && centerVisible && viewState.value.online)
        }
    }

    private suspend fun readCenter(cursor: String?) {
        updateState { it.copy(centerLoading = cursor == null, loadingMore = cursor != null, centerIssue = null) }
        val result = session.readProtected(scopeId) { repository.list(cursor, it) }
        currentCoroutineContext().ensureActive()
        if (!current()) return
        updateState { state ->
            val loaded = state.copy(centerLoading = false, loadingMore = false)
            when (result) {
                is AuthResult.Success -> {
                    val currentItems = state.items.associateBy(NotificationEntry::id)
                    val page = result.value.items.map { entry ->
                        currentItems[entry.id]?.takeIf { it.readAt != null && entry.readAt == null } ?: entry
                    }
                    val olderPage = page.associateBy(NotificationEntry::id)
                    loaded.copy(
                        items = if (cursor == null) page.distinctBy(NotificationEntry::id) else
                            (state.items.map { olderPage[it.id] ?: it } + page).distinctBy(NotificationEntry::id),
                        nextCursor = result.value.nextCursor,
                        centerIssue = NotificationIssue.Offline.takeUnless { state.online },
                    )
                }
                is AuthResult.Failure -> if (cursor != null && result.error.code == AuthErrorCode.InvalidField) {
                    loaded.copy(nextCursor = null, centerIssue = NotificationIssue.InvalidCursor)
                } else loaded.copy(centerIssue = issue(result.error.code))
            }
        }
    }

    private fun open(id: String) {
        if (!current() || !viewState.value.online) return
        val entry = viewState.value.items.firstOrNull { it.id == id } ?: return
        markRead(id)
        val target = entry.target
        if (target == null) {
            updateState { it.copy(centerIssue = NotificationIssue.TargetUnavailable) }
            return
        }
        viewModelScope.launch { if (current()) sendAction(NotificationAction.Open(target)) }
    }

    private fun issue(error: AuthErrorCode): NotificationIssue =
        if (error == AuthErrorCode.Network) NotificationIssue.Offline else NotificationIssue.Unavailable
}
