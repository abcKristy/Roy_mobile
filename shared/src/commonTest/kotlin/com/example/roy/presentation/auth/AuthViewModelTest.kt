package com.example.roy.presentation.auth

import com.example.roy.domain.auth.AuthCredentials
import com.example.roy.domain.auth.AuthMode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.SessionStatus
import com.example.roy.domain.auth.SessionTestRepository
import com.example.roy.domain.auth.SessionTestStore
import com.example.roy.domain.auth.ValidateAuthInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @Test
    fun rapidSubmitEventsStartOneServerRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fixture = SessionTestRepository()
            val complete = CompletableDeferred<Unit>()
            var loginCalls = 0
            val repository = object : com.example.roy.domain.auth.AuthRepository by fixture {
                override suspend fun login(credentials: AuthCredentials.Login): AuthResult.Success<com.example.roy.domain.auth.AuthSession> {
                    loginCalls++
                    complete.await()
                    return AuthResult.Success(fixture.first)
                }
            }
            val session = SessionCoordinator(repository, SessionTestStore())
            session.restore()
            val model = AuthViewModel(AuthMode.Login, session, ValidateAuthInput())
            model.handleEvent(AuthViewEvent.PhoneChanged("+123"))
            model.handleEvent(AuthViewEvent.Submit("fixture"))
            model.handleEvent(AuthViewEvent.Submit("fixture"))
            runCurrent()
            assertEquals(1, loginCalls)
            complete.complete(Unit)
            runCurrent()
            assertIs<SessionStatus.Active>(session.state.value)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
