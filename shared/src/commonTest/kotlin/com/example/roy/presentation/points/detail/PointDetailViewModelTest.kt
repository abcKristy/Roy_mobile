package com.example.roy.presentation.points.detail

import com.example.roy.domain.auth.AuthError
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.map.Point
import com.example.roy.domain.map.PointError
import com.example.roy.domain.map.PointResult
import com.example.roy.domain.map.PointTopic
import com.example.roy.domain.map.Position
import com.example.roy.domain.points.DetailReply
import com.example.roy.domain.points.PointDetailGateway
import com.example.roy.domain.points.PointTopicInput
import com.example.roy.domain.points.TopicPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class PointDetailViewModelTest {
    @Test
    fun duplicateRefreshesAndSelectsExistingWithoutJoin() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gateway = Gateway()
            val model = PointDetailViewModel(PointId, null, 1, true, gateway)
            runCurrent()
            model.handleEvent(PointDetailEvent.OnPresetSelected("other"))
            model.handleEvent(PointDetailEvent.OnCustomLabelChanged("  шахматы  "))
            gateway.point = PointResult.Success(sample(Original, Existing))
            model.handleEvent(PointDetailEvent.OnAddConfirmed)
            runCurrent()

            assertEquals(listOf(Original, Existing), model.viewState.value.point?.topics)
            assertEquals(Existing.id, model.viewState.value.selectedTopicId)
            assertFalse(model.viewState.value.isAddSheetOpen)
            assertEquals(0, gateway.joins)
            assertEquals(listOf(PointTopicInput("other", "шахматы")), gateway.adds)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun leaveCancelKeepsMembershipAndConfirmedGoneInvalidatesPoint() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gateway = Gateway()
            val model = PointDetailViewModel(PointId, Original.id, 1, false, gateway)
            val actions = mutableListOf<PointDetailAction>()
            backgroundScope.launch { model.viewAction.collect { actions += it } }
            runCurrent()
            model.handleEvent(PointDetailEvent.OnLeaveRequested)
            model.handleEvent(PointDetailEvent.OnDialogDismissed)
            assertEquals(0, gateway.leaves)
            assertTrue(Original.chatId in model.viewState.value.memberChatIds)

            gateway.point = PointResult.Failure(PointError.ResourceGone)
            model.handleEvent(PointDetailEvent.OnLeaveRequested)
            model.handleEvent(PointDetailEvent.OnLeaveConfirmed)
            model.handleEvent(PointDetailEvent.OnLeaveConfirmed)
            runCurrent()

            assertEquals(1, gateway.leaves)
            assertTrue(model.viewState.value.isGone)
            assertTrue(PointDetailAction.PointChanged(PointId, true) in actions)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun uncertainAddReusesKeyOnlyOnExplicitConfirm() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gateway = Gateway().apply {
                addReply = DetailReply.AuthFailure(AuthError(AuthErrorCode.Network))
            }
            val model = PointDetailViewModel(PointId, null, 1, true, gateway)
            runCurrent()
            model.handleEvent(PointDetailEvent.OnPresetSelected("other"))
            model.handleEvent(PointDetailEvent.OnCustomLabelChanged("Шахматы"))
            model.handleEvent(PointDetailEvent.OnAddConfirmed)
            model.handleEvent(PointDetailEvent.OnAddConfirmed)
            runCurrent()
            assertEquals(1, gateway.addKeys.size)
            assertEquals(PointDetailError.Network, model.viewState.value.sheetError)

            model.handleEvent(PointDetailEvent.OnAddConfirmed)
            runCurrent()
            assertEquals(2, gateway.addKeys.size)
            assertEquals(1, gateway.addKeys.distinct().size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun guestJoinEmitsGuardWithSelectedTopicAndDoesNotMutate() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gateway = Gateway()
            val model = PointDetailViewModel(PointId, null, null, false, gateway)
            val actions = mutableListOf<PointDetailAction>()
            backgroundScope.launch { model.viewAction.collect { actions += it } }
            runCurrent()
            model.handleEvent(PointDetailEvent.OnJoinRequested)
            runCurrent()
            assertEquals(listOf<PointDetailAction>(PointDetailAction.RequireJoin(PointId, Original.id, Original.chatId)), actions)
            assertEquals(0, gateway.joins)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class Gateway : PointDetailGateway {
        var point: PointResult<Point> = PointResult.Success(sample(Original))
        val adds = mutableListOf<PointTopicInput>()
        val addKeys = mutableListOf<String>()
        var addReply: DetailReply<PointTopic> = DetailReply.Duplicate
        var joins = 0
        var leaves = 0

        override suspend fun point(pointId: String) = point
        override suspend fun memberships(pointId: String, scopeId: Long, chatIds: Set<String>) =
            AuthResult.Success(setOf(Original.chatId))
        override suspend fun presets() = AuthResult.Success(listOf(TopicPreset("other", "Другое")))
        override suspend fun add(pointId: String, scopeId: Long, topic: PointTopicInput, key: String): DetailReply<PointTopic> {
            adds += topic
            addKeys += key
            return addReply
        }
        override suspend fun join(chatId: String, scopeId: Long): DetailReply<Unit> {
            joins++
            return DetailReply.Success(Unit)
        }
        override suspend fun leave(chatId: String, scopeId: Long): DetailReply<Unit> {
            leaves++
            return DetailReply.Success(Unit)
        }
    }

    private companion object {
        const val PointId = "point"
        val Original = PointTopic("topic-1", "other", "Бег", 2, "chat-1")
        val Existing = PointTopic("topic-2", "other", "Шахматы", 1, "chat-2")
        val Time = Instant.parse("2026-11-15T00:00:00Z")
        fun sample(vararg topics: PointTopic) = Point(
            PointId, Position(55.7, 37.6), "Адрес", "manual", 2, topics.toList(), Time, Time,
        )
    }
}
