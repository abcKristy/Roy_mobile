package com.example.roy.presentation.meetings

import androidx.lifecycle.viewModelScope
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.AuthError
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.SessionStatus
import com.example.roy.domain.auth.SessionTestRepository
import com.example.roy.domain.auth.SessionTestStore
import com.example.roy.domain.chat.ChatDetail
import com.example.roy.domain.chat.ChatHistoryRepository
import com.example.roy.domain.chat.ChatReadReply
import com.example.roy.domain.meetings.MeetingOverlap
import com.example.roy.domain.meetings.MeetingBooking
import com.example.roy.domain.meetings.MeetingPage
import com.example.roy.domain.meetings.MeetingProposal
import com.example.roy.domain.meetings.MeetingRejection
import com.example.roy.domain.meetings.MeetingReply
import com.example.roy.domain.meetings.MeetingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class MeetingViewModelTest {
    @Test
    fun missingNotificationTargetKeepsAuthorizedRoomAndServerPinAccessible() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        val model = fixture.model(initialProposal = "missing")
        try {
            model.handleEvent(access)
            runCurrent()
            assertEquals(MeetingSheet.Detail("missing"), model.viewState.value.sheet)
            assertEquals(MeetingIssue.Gone, model.viewState.value.issue)
            assertNull(model.viewState.value.selectedProposal)
            assertEquals("pin", model.viewState.value.currentProposal?.id)
            model.handleEvent(MeetingViewEvent.OnDismiss)
            model.handleEvent(MeetingViewEvent.OnMeetingsRequested)
            assertEquals(MeetingSheet.List, model.viewState.value.sheet)
            assertTrue(model.viewState.value.canCreate)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun proposalFromDifferentChatFinishesWithGone() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val model = Fixture().model(initialProposal = "foreign")
        try {
            model.handleEvent(access)
            runCurrent()
            assertEquals(MeetingIssue.Gone, model.viewState.value.issue)
            assertNull(model.viewState.value.selectedProposal)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun voteAlreadyAppliedByAnotherClientDoesNotRemoveIt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val model = Fixture().model()
        try {
            model.handleEvent(access)
            runCurrent()
            model.handleEvent(MeetingViewEvent.OnVote("row", going = true))
            runCurrent()
            assertTrue(model.viewState.value.proposals.single().viewerGoing)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun formRejectsSecondsAndNonIsoDate() {
        assertNull(parseMeetingLocalTime("2035-01-02", "10:15:30"))
        assertNull(parseMeetingLocalTime("2035-1-02", "10:15"))
        assertNotNull(parseMeetingLocalTime("2035-01-02", "10:15"))
    }

    @Test
    fun openingListRetriesAfterPreloadFailure() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture().apply { failList = true }
        val model = fixture.model()
        try {
            model.handleEvent(access)
            runCurrent()
            assertEquals(MeetingIssue.Offline, model.viewState.value.issue)
            fixture.failList = false
            model.handleEvent(MeetingViewEvent.OnMeetingsRequested)
            runCurrent()
            assertEquals("row", model.viewState.value.proposals.single().id)
            assertNull(model.viewState.value.issue)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun overlapConfirmationKeepsKeyAndWarnedRevision() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        val model = fixture.model()
        try {
            model.handleEvent(access)
            runCurrent()
            model.handleEvent(MeetingViewEvent.OnBookRequested("row"))
            model.handleEvent(MeetingViewEvent.OnBookConfirmed)
            runCurrent()
            assertTrue(model.viewState.value.sheet is MeetingSheet.Overlap)
            fixture.row = fixture.row.copy(revision = 7L)
            model.onRealtimeChange("row")
            runCurrent()
            model.handleEvent(MeetingViewEvent.OnOverlapConfirmed)
            runCurrent()
            assertEquals(listOf(Command(false, 3L, "key"), Command(true, 3L, "key")), fixture.commands)
            assertEquals(MeetingIssue.Stale, model.viewState.value.issue)
            assertEquals(7L, assertNotNull(model.viewState.value.selectedProposal).revision)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun lostBookingResponseOpensCanonicalBookingAfterRefreshWithoutAnotherWrite() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture().apply { loseBookingResponse = true }
        val model = fixture.model()
        try {
            model.handleEvent(access)
            runCurrent()
            model.handleEvent(MeetingViewEvent.OnBookRequested("row"))
            model.handleEvent(MeetingViewEvent.OnBookConfirmed)
            runCurrent()
            assertEquals(listOf(Command(false, 3L, "key")), fixture.commands)
            assertEquals(MeetingSheet.Detail("row"), model.viewState.value.sheet)
            assertEquals("booking", model.viewState.value.selectedProposal?.booking?.id)
            assertNull(model.viewState.value.issue)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private data class Command(val confirm: Boolean, val revision: Long, val key: String)

    private class Fixture : MeetingRepository {
        val commands = mutableListOf<Command>()
        var row = proposal("row")
        var loseBookingResponse = false
        var failList = false
        private val pin = proposal("pin")

        suspend fun model(initialProposal: String? = null): MeetingViewModel {
            val auth = SessionTestRepository()
            val sessions = SessionCoordinator(auth, SessionTestStore().apply { saved = auth.first })
            sessions.restore()
            val scope = (sessions.state.value as SessionStatus.Active).scopeId
            val clock = object : Clock {
                override fun now() = MeetingViewModelTest.now
            }
            return MeetingViewModel(scope, "chat", sessions, history, this, initialProposal, clock, { "key" })
        }

        private val history = object : ChatHistoryRepository {
            override suspend fun detail(chatId: String, token: String): AuthResult<ChatReadReply<ChatDetail>> =
                AuthResult.Success(ChatReadReply.Data(ChatDetail(chatId, "point", "topic", "", "", "viewer", 0, 0, pin.id)))

            override suspend fun history(chatId: String, token: String, beforeSeq: Long?, afterSeq: Long?) =
                error("Unexpected history read")
            override suspend fun search(chatId: String, token: String, query: String, cursor: String?) =
                error("Unexpected search")
        }

        override suspend fun list(chatId: String, token: String, cursor: String?) =
            if (failList) AuthResult.Failure(AuthError(AuthErrorCode.Network))
            else AuthResult.Success(MeetingReply.Data(MeetingPage(listOf(row), null)))

        override suspend fun votes(proposalId: String, token: String, cursor: String?) = error("Unexpected voter read")
        override suspend fun create(chatId: String, startsAt: Instant, endsAt: Instant, key: String, token: String) =
            error("Unexpected create")
        override suspend fun vote(proposalId: String, going: Boolean, token: String) = error("Unexpected vote")
        override suspend fun close(proposalId: String, expectedRevision: Long, key: String, token: String) =
            error("Unexpected close")

        override suspend fun book(
            proposalId: String, confirmOverlap: Boolean, expectedRevision: Long, key: String, token: String,
        ): AuthResult<MeetingReply<MeetingProposal>> {
            commands += Command(confirmOverlap, expectedRevision, key)
            if (loseBookingResponse) {
                val booking = MeetingBooking("booking", "row", "viewer", "active", 1L, emptyList(), now, null, null)
                row = row.copy(status = "booked", booking = booking, revision = row.revision + 1)
                return AuthResult.Failure(AuthError(AuthErrorCode.Network))
            }
            val conflict = MeetingOverlap("booking", "other", row.startsAt, row.endsAt, now, 1L)
            return AuthResult.Success(if (confirmOverlap) MeetingReply.Rejected(MeetingRejection.Stale)
                else MeetingReply.OverlapRequired(listOf(conflict)))
        }

        override suspend fun detail(proposalId: String, token: String): AuthResult<MeetingReply<MeetingProposal>> = AuthResult.Success(
            when (proposalId) {
                row.id -> MeetingReply.Data(row)
                pin.id -> MeetingReply.Data(pin)
                "foreign" -> MeetingReply.Data(proposal("foreign").copy(chatId = "another-chat"))
                else -> MeetingReply.Rejected(MeetingRejection.NotMember)
            },
        )
    }

    private companion object {
        val access = MeetingViewEvent.OnAccessChanged(true, "viewer", true)
        val now = Instant.parse("2035-01-01T00:00:00Z")
        fun proposal(id: String) = MeetingProposal(
            id, "chat", "author", Instant.parse("2035-01-02T00:00:00Z"), Instant.parse("2035-01-02T01:00:00Z"),
            "ready_to_book", 2, true, null, 3L, now, now,
        )
    }
}
