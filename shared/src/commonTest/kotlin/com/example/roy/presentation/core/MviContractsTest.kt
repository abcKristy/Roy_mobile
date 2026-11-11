package com.example.roy.presentation.core

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MviContractsTest {
    @Test
    fun queuesActionsUntilStartedAndDoesNotReplayOnRestart() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val viewModel = CounterViewModel().also { store.put("counter", it) }
            val owner = object : LifecycleOwner {
                override val lifecycle = LifecycleRegistry.createUnsafe(this)
            }
            val delivered = mutableListOf<Action>()
            owner.lifecycle.currentState = Lifecycle.State.CREATED
            backgroundScope.launch { viewModel.viewAction.collectStarted(owner.lifecycle, delivered::add) }

            viewModel.handleEvent(Event.Increment)
            runCurrent()
            assertEquals(State(1), viewModel.viewState.value)
            assertEquals(emptyList(), delivered)

            owner.lifecycle.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(listOf<Action>(Action.ShowCount(1)), delivered)
            owner.lifecycle.currentState = Lifecycle.State.CREATED
            runCurrent()
            viewModel.handleEvent(Event.Increment)
            runCurrent()
            assertEquals(State(2), viewModel.viewState.value)
            assertEquals(listOf<Action>(Action.ShowCount(1)), delivered)

            owner.lifecycle.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(listOf<Action>(Action.ShowCount(1), Action.ShowCount(2)), delivered)
            owner.lifecycle.currentState = Lifecycle.State.DESTROYED
            runCurrent()
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun removingViewModelCancelsItsRunningWork() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val viewModel = CounterViewModel().also { store.put("counter", it) }
            var isCancelled = false
            val work = viewModel.viewModelScope.launch {
                try {
                    awaitCancellation()
                } finally {
                    isCancelled = true
                }
            }
            runCurrent()
            assertFalse(isCancelled)

            store.clear()
            runCurrent()
            assertTrue(work.isCancelled)
            assertTrue(isCancelled)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    private data class State(val count: Int = 0)

    private sealed class Event {
        data object Increment : Event()
    }

    private sealed class Action {
        data class ShowCount(val count: Int) : Action()
    }

    private class CounterViewModel : BaseViewModel<State, Event, Action>(State()) {
        override fun handleEvent(event: Event) {
            when (event) {
                Event.Increment -> increment()
            }
        }

        private fun increment() {
            updateState { state -> state.copy(count = state.count + 1) }
            val count = viewState.value.count
            viewModelScope.launch { sendAction(Action.ShowCount(count)) }
        }
    }
}
