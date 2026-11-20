package com.example.roy.presentation.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

abstract class BaseViewModel<State : Any, Event : Any, Action : Any>(initialState: State) : ViewModel() {
    private val state = MutableStateFlow(initialState)
    private val actions = Channel<Action>(Channel.BUFFERED)

    val viewState: StateFlow<State> = state.asStateFlow()

    /** One-shot UI effects for a single Screen collector. Consumed effects are never replayed. */
    val viewAction: Flow<Action> = actions.receiveAsFlow()

    abstract fun handleEvent(event: Event)

    protected fun updateState(transform: (State) -> State) {
        state.update(transform)
    }

    protected suspend fun sendAction(action: Action) {
        actions.send(action)
    }

    override fun onCleared() {
        actions.cancel()
        super.onCleared()
    }
}

@Composable
fun <Action : Any> CollectMviActions(actions: Flow<Action>, onAction: (Action) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnAction by rememberUpdatedState(onAction)
    LaunchedEffect(lifecycle, actions) {
        actions.collectStarted(lifecycle) { currentOnAction(it) }
    }
}

internal suspend fun <Action : Any> Flow<Action>.collectStarted(lifecycle: Lifecycle, onAction: (Action) -> Unit) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { collect { onAction(it) } }
}
