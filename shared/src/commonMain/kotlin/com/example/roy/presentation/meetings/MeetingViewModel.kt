package com.example.roy.presentation.meetings

import androidx.lifecycle.viewModelScope
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.auth.SessionCoordinator
import com.example.roy.domain.auth.SessionStatus
import com.example.roy.domain.chat.ChatHistoryRepository
import com.example.roy.domain.chat.ChatReadReply
import com.example.roy.domain.meetings.MeetingProposal
import com.example.roy.domain.meetings.MeetingRejection
import com.example.roy.domain.meetings.MeetingReply
import com.example.roy.domain.meetings.MeetingRepository
import com.example.roy.presentation.core.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class MeetingViewModel(
    private val scopeId: Long,
    private val chatId: String,
    private val sessions: SessionCoordinator,
    private val history: ChatHistoryRepository,
    private val meetings: MeetingRepository,
    initialProposalId: String? = null,
    private val clock: Clock = Clock.System,
    private val makeKey: () -> String = { Uuid.random().toString() },
) : BaseViewModel<MeetingViewState, MeetingViewEvent, MeetingViewAction>(MeetingViewState()) {
    private var access = false
    private var accessEpoch = 0L
    private var initialTarget = initialProposalId
    private var pinId: String? = null
    private var proposalsCursor: String? = null
    private var hasLoadedOlderProposals = false
    private var votersCursor: String? = null
    private var refreshJob: Job? = null
    private var pageJob: Job? = null
    private var votersJob: Job? = null
    private var mutationJob: Job? = null
    private var refreshQueued = false
    private val changedIds = mutableSetOf<String>()
    private val snapshots = mutableMapOf<String, MeetingProposal>()
    private var createAttempt: CreateAttempt? = null
    private var commandAttempt: CommandAttempt? = null

    init {
        viewModelScope.launch {
            sessions.state.collect { status ->
                if (status != SessionStatus.Active(scopeId)) clear()
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(15.seconds)
                if (access) {
                    val pin = viewState.value.currentProposal
                    updateEligibility()
                    if (pin != null && viewState.value.currentProposal == null) refresh()
                }
            }
        }
    }

    override fun handleEvent(event: MeetingViewEvent) {
        when (event) {
            is MeetingViewEvent.OnAccessChanged -> onAccessChanged(event)
            MeetingViewEvent.OnRefresh -> onRefresh()
            MeetingViewEvent.OnMeetingsRequested -> onMeetingsRequested()
            MeetingViewEvent.OnDismiss -> onDismiss()
            MeetingViewEvent.OnCreateRequested -> onCreateRequested()
            is MeetingViewEvent.OnDateChanged -> onDateChanged(event)
            is MeetingViewEvent.OnTimeChanged -> onTimeChanged(event)
            is MeetingViewEvent.OnDurationChanged -> onDurationChanged(event)
            is MeetingViewEvent.OnCustomDurationChanged -> onCustomDurationChanged(event)
            MeetingViewEvent.OnCreateConfirmed -> onCreateConfirmed()
            MeetingViewEvent.OnProposalsLoadMore -> onProposalsLoadMore()
            MeetingViewEvent.OnVotersLoadMore -> onVotersLoadMore()
            MeetingViewEvent.OnVotersRetry -> onVotersRetry()
            is MeetingViewEvent.OnProposalSelected -> onProposalSelected(event)
            is MeetingViewEvent.OnVote -> onVote(event.proposalId, event.going)
            is MeetingViewEvent.OnVotersRequested -> onVotersRequested(event)
            is MeetingViewEvent.OnBookRequested -> onBookRequested(event)
            MeetingViewEvent.OnBookConfirmed -> onBookConfirmed()
            MeetingViewEvent.OnOverlapConfirmed -> onOverlapConfirmed()
            is MeetingViewEvent.OnCloseRequested -> onCloseRequested(event)
            MeetingViewEvent.OnCloseConfirmed -> onCloseConfirmed()
        }
    }

    private fun onAccessChanged(event: MeetingViewEvent.OnAccessChanged) {
        changeAccess(event)
    }

    private fun onRefresh() {
        if (viewState.value.isOnline) {
            updateState { it.copy(issue = null) }
            refresh()
        }
    }

    private fun onMeetingsRequested() {
        if (authorized() && !pending()) {
            open(MeetingSheet.List)
            if (viewState.value.isOnline) refresh() else showIssue(MeetingIssue.Offline)
        }
    }

    private fun onCreateRequested() {
        openCreate()
    }

    private fun onDateChanged(event: MeetingViewEvent.OnDateChanged) {
        editDraft { copy(date = event.date, dateIssue = null) }
    }

    private fun onTimeChanged(event: MeetingViewEvent.OnTimeChanged) {
        editDraft { copy(time = event.time, timeIssue = null) }
    }

    private fun onDurationChanged(event: MeetingViewEvent.OnDurationChanged) {
        editDraft { copy(durationMinutes = event.minutes, durationIssue = null) }
    }

    private fun onCustomDurationChanged(event: MeetingViewEvent.OnCustomDurationChanged) {
        editDraft { copy(customMinutes = event.minutes, durationIssue = null) }
    }

    private fun onCreateConfirmed() {
        create()
    }

    private fun onProposalsLoadMore() {
        loadMoreProposals()
    }

    private fun onVotersLoadMore() {
        loadVoters(append = true)
    }

    private fun onVotersRetry() {
        loadVoters()
    }

    private fun onProposalSelected(event: MeetingViewEvent.OnProposalSelected) {
        select(event.proposalId)
    }

    private fun onVotersRequested(event: MeetingViewEvent.OnVotersRequested) {
        if (authorized() && event.proposalId in snapshots) {
            open(MeetingSheet.Voters(event.proposalId))
            loadVoters()
        }
    }

    private fun onBookRequested(event: MeetingViewEvent.OnBookRequested) {
        if (checkAction(event.proposalId) { bookableIds }) {
            open(MeetingSheet.Booking(event.proposalId))
        }
    }

    private fun onBookConfirmed() {
        book(confirmOverlap = false)
    }

    private fun onOverlapConfirmed() {
        book(confirmOverlap = true)
    }

    private fun onCloseRequested(event: MeetingViewEvent.OnCloseRequested) {
        if (checkAction(event.proposalId) { closableIds }) {
            open(MeetingSheet.Close(event.proposalId))
        }
    }

    private fun onCloseConfirmed() {
        close()
    }

    fun onRealtimeChange(id: String? = null) {
        if (!authorized()) return
        id?.let { changedIds.add(it) }
        refresh()
    }

    private fun authorized() = access && sessions.state.value == SessionStatus.Active(scopeId)

    private fun changeAccess(event: MeetingViewEvent.OnAccessChanged) {
        if (!event.canAccess || event.viewerId == null || sessions.state.value != SessionStatus.Active(scopeId)) {
            clear()
            return
        }
        if (access && viewState.value.viewerId != event.viewerId) clear()
        val reconnect = !viewState.value.isOnline && event.isOnline
        val newlyAuthorized = !access
        access = true
        updateState { it.copy(
            viewerId = event.viewerId, isOnline = event.isOnline,
            issue = when {
                !event.isOnline && it.issue == null -> MeetingIssue.Offline
                reconnect && it.issue == MeetingIssue.Offline -> null
                else -> it.issue
            },
        ) }
        if (newlyAuthorized) {
            initialTarget?.let {
                initialTarget = null
                select(it)
            }
        }
        updateEligibility()
        if (newlyAuthorized || reconnect) refresh()
    }

    private fun clear() {
        access = false
        accessEpoch++
        refreshJob?.cancel()
        pageJob?.cancel()
        votersJob?.cancel()
        mutationJob?.cancel()
        refreshQueued = false
        changedIds.clear()
        snapshots.clear()
        pinId = null
        proposalsCursor = null
        hasLoadedOlderProposals = false
        votersCursor = null
        createAttempt = null
        commandAttempt = null
        updateState { MeetingViewState() }
    }

    private fun refresh() {
        if (!authorized() || !viewState.value.isOnline) return
        refreshQueued = true
        if (refreshJob?.isActive == true) return
        pageJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (refreshQueued && authorized() && viewState.value.isOnline) {
                refreshQueued = false
                refreshProposals()
            }
        }
    }

    private suspend fun refreshProposals() {
        val epoch = accessEpoch
        updateState { it.copy(isLoading = true) }
        val needed = changedIds.toSet()
        changedIds.removeAll(needed)
        val unresolved = needed.toMutableSet()
        try {
            val detail = sessions.readProtected(scopeId) { history.detail(chatId, it) }
            currentCoroutineContext().ensureActive()
            if (!authorized()) return
            when (detail) {
                is AuthResult.Failure -> showIssue(detail.error.code.toMeetingIssue())
                is AuthResult.Success -> when (val value = detail.value) {
                    is ChatReadReply.Data -> pinId = value.value.currentProposalId
                    ChatReadReply.Gone -> { loseAccess(MeetingIssue.Gone); return }
                    ChatReadReply.NotMember -> { loseAccess(MeetingIssue.NotMember); return }
                }
            }
            val page = read({ meetings.list(chatId, it) }, roomScoped = true, failure = ::showIssue) ?: return
            val previous = viewState.value.proposals
            val loadedMore = hasLoadedOlderProposals
            val onFirstPage = page.items.mapTo(mutableSetOf(), MeetingProposal::id)
            unresolved.removeAll(onFirstPage)
            val ids = (needed + listOfNotNull(pinId, viewState.value.sheet?.proposalId)) - onFirstPage
            page.items.forEach(::accept)
            proposalsCursor = if (loadedMore) proposalsCursor else page.nextCursor
            val visible = (page.items + if (loadedMore) previous else emptyList()).distinctBy(MeetingProposal::id)
            updateState { it.copy(
                proposals = visible.mapNotNull { proposal -> snapshots[proposal.id] },
                hasMore = proposalsCursor != null,
            ) }
            for (id in ids) {
                val proposal = read({ meetings.detail(id, it) }) { issue ->
                    val isSelected = viewState.value.sheet?.proposalId == id
                    if (isSelected || pinId == id) showIssue(issue)
                    if (issue == MeetingIssue.Gone) {
                        unresolved.remove(id)
                        snapshots.remove(id)
                        updateState { it.copy(proposals = it.proposals.filterNot { row -> row.id == id }) }
                    }
                }
                if (proposal != null) {
                    unresolved.remove(id)
                    if (!accept(proposal, expectedId = id) && viewState.value.sheet?.proposalId == id) {
                        showIssue(MeetingIssue.Gone)
                    }
                }
            }
            updateEligibility()
            val voters = viewState.value.sheet as? MeetingSheet.Voters
            if (voters != null) loadVoters()
        } finally {
            if (accessEpoch == epoch) {
                changedIds.addAll(unresolved)
                updateState { it.copy(isLoading = false) }
            }
        }
    }

    private fun loadMoreProposals() {
        val cursor = proposalsCursor ?: return
        if (!authorized() || viewState.value.isLoading || !viewState.value.isOnline) return
        updateState { it.copy(isLoading = true, issue = null) }
        val epoch = accessEpoch
        pageJob = viewModelScope.launch {
            try {
                val page = read({ meetings.list(chatId, it, cursor) }, roomScoped = true) { issue ->
                    showIssue(issue)
                    if (issue == MeetingIssue.InvalidCursor) {
                        hasLoadedOlderProposals = false
                        proposalsCursor = null
                        updateState { it.copy(hasMore = false) }
                    }
                }
                    ?: return@launch
                page.items.forEach(::accept)
                proposalsCursor = page.nextCursor
                hasLoadedOlderProposals = true
                updateState { state -> state.copy(
                    proposals = (state.proposals + page.items).distinctBy(MeetingProposal::id)
                        .mapNotNull { snapshots[it.id] },
                    hasMore = proposalsCursor != null,
                ) }
                updateEligibility()
            } finally {
                if (accessEpoch == epoch && refreshJob?.isActive != true) updateState { it.copy(isLoading = false) }
            }
        }
    }

    private fun loadVoters(append: Boolean = false) {
        val id = (viewState.value.sheet as? MeetingSheet.Voters)?.proposalId ?: return
        val cursor = if (append) votersCursor ?: return else null
        if (!authorized()) return
        if (!viewState.value.isOnline) {
            updateState { it.copy(isLoadingVoters = false, votersIssue = MeetingIssue.Offline) }
            return
        }
        if (append && viewState.value.isLoadingVoters) return
        votersJob?.cancel()
        if (!append) votersCursor = null
        updateState { it.copy(isLoadingVoters = true, hasMoreVoters = append && it.hasMoreVoters, votersIssue = null) }
        votersJob = viewModelScope.launch {
            val page = read({ meetings.votes(id, it, cursor) }) { issue ->
                updateState { it.copy(isLoadingVoters = false, votersIssue = issue) }
            } ?: return@launch
            if (viewState.value.sheet != MeetingSheet.Voters(id)) return@launch
            votersCursor = page.nextCursor
            updateState { it.copy(
                voters = ((if (append) it.voters else emptyList()) + page.items)
                    .distinctBy { voter -> voter.userId ?: voter },
                hasMoreVoters = page.nextCursor != null, isLoadingVoters = false,
            ) }
        }
    }

    private fun open(sheet: MeetingSheet) {
        if (pending()) return
        votersJob?.cancel()
        votersCursor = null
        updateState { it.copy(
            sheet = sheet, selectedProposal = sheet.proposalId?.let(snapshots::get), issue = null,
            voters = emptyList(), isLoadingVoters = false, hasMoreVoters = false, votersIssue = null,
        ) }
    }

    private fun onDismiss() {
        if (pending()) return
        votersJob?.cancel()
        votersCursor = null
        val sheet = viewState.value.sheet
        updateState { it.copy(
            sheet = when (sheet) {
                is MeetingSheet.Overlap, is MeetingSheet.Booking, is MeetingSheet.Close ->
                    sheet.proposalId?.let { MeetingSheet.Detail(it) }
                else -> null
            },
            issue = null, voters = emptyList(), isLoadingVoters = false, hasMoreVoters = false, votersIssue = null,
        ) }
    }

    private fun select(id: String) {
        if (!authorized() || pending()) return
        open(MeetingSheet.Detail(id))
        if (id !in snapshots && !viewState.value.isOnline) showIssue(MeetingIssue.Offline)
        onRealtimeChange(id)
    }

    private fun openCreate() {
        if (!authorized() || pending()) return
        if (!viewState.value.isOnline) { showIssue(MeetingIssue.Offline); return }
        val (date, time) = meetingLocalFields(clock.now())
        val draft = viewState.value.draft.takeIf { it.date.isNotBlank() } ?: MeetingDraft(date, time)
        open(MeetingSheet.Create)
        updateState { it.copy(draft = draft) }
    }

    private fun editDraft(change: MeetingDraft.() -> MeetingDraft) {
        if (pending() || viewState.value.sheet != MeetingSheet.Create) return
        updateState { it.copy(draft = it.draft.change(), issue = null) }
    }

    private fun create() {
        val state = viewState.value
        if (!canMutate() || state.sheet != MeetingSheet.Create) return
        val draft = state.draft
        val starts = parseMeetingLocalTime(draft.date, draft.time)
        val minutes = draft.durationMinutes ?: draft.customMinutes.toIntOrNull()
        val dateIssue = when {
            draft.date.isBlank() -> MeetingIssue.Required
            parseMeetingLocalTime(draft.date, "12:00") == null -> MeetingIssue.InvalidDate
            else -> null
        }
        val timeIssue = when {
            draft.time.isBlank() -> MeetingIssue.Required
            dateIssue == null && (starts == null || starts <= clock.now()) -> MeetingIssue.InvalidTime
            else -> null
        }
        val durationIssue = if (minutes == null || minutes <= 0) MeetingIssue.InvalidDuration else null
        if (dateIssue != null || timeIssue != null || durationIssue != null || starts == null || minutes == null) {
            updateState { it.copy(draft = draft.copy(
                dateIssue = dateIssue, timeIssue = timeIssue, durationIssue = durationIssue,
            )) }
            return
        }
        val ends = starts + minutes.minutes
        val attempt = createAttempt?.takeIf { it.start == starts && it.end == ends }
            ?: CreateAttempt(starts, ends, makeKey()).also { createAttempt = it }
        mutate(null, { meetings.create(chatId, starts, ends, attempt.key, it) }) { proposal ->
            createAttempt = null
            updateState { it.copy(
                draft = MeetingDraft(), sheet = MeetingSheet.Detail(proposal.id),
                proposals = (listOf(proposal) + it.proposals).distinctBy(MeetingProposal::id),
            ) }
        }
    }

    private fun onVote(id: String, going: Boolean) {
        if (!checkAction(id) { votableIds }) return
        val proposal = snapshots.getValue(id)
        // A recent server update may already satisfy the action shown to the user.
        if (proposal.viewerGoing != going) mutate(id, { meetings.vote(id, going, it) })
    }

    private fun book(confirmOverlap: Boolean) {
        val sheet = viewState.value.sheet
        val id = when {
            confirmOverlap && sheet is MeetingSheet.Overlap -> sheet.proposalId
            !confirmOverlap && sheet is MeetingSheet.Booking -> sheet.proposalId
            else -> return
        }
        if (!canMutate()) return
        val proposal = snapshots[id] ?: return
        val attempt = if (confirmOverlap) {
            commandAttempt?.takeIf { it.id == id && it.kind == CommandKind.Book } ?: return
        } else {
            if (!checkAction(id) { bookableIds }) return
            command(proposal, CommandKind.Book)
        }
        mutate(id, { meetings.book(id, confirmOverlap, attempt.revision, attempt.key, it) }) {
            commandAttempt = null
            updateState { state -> state.copy(sheet = MeetingSheet.Detail(id)) }
        }
    }

    private fun close() {
        val id = (viewState.value.sheet as? MeetingSheet.Close)?.proposalId ?: return
        if (!checkAction(id) { closableIds }) return
        val attempt = command(snapshots.getValue(id), CommandKind.Close)
        mutate(id, { meetings.close(id, attempt.revision, attempt.key, it) }) {
            commandAttempt = null
            updateState { state -> state.copy(sheet = MeetingSheet.Detail(id)) }
        }
    }

    private fun command(proposal: MeetingProposal, kind: CommandKind): CommandAttempt =
        commandAttempt?.takeIf { it.id == proposal.id && it.kind == kind && it.revision == proposal.revision }
            ?: CommandAttempt(proposal.id, proposal.revision, kind, makeKey()).also { commandAttempt = it }

    private fun mutate(
        id: String?,
        call: suspend (String) -> AuthResult<MeetingReply<MeetingProposal>>,
        onSuccess: (MeetingProposal) -> Unit = {},
    ) {
        updateState { it.copy(busyProposalId = id, isCreating = id == null, issue = null) }
        val epoch = accessEpoch
        mutationJob = viewModelScope.launch {
            try {
                val result = sessions.mutateProtected(scopeId, call)
                currentCoroutineContext().ensureActive()
                if (!authorized()) return@launch
                when (result) {
                    is AuthResult.Failure -> {
                        showIssue(result.error.code.toMeetingIssue())
                        onRealtimeChange(id)
                    }
                    is AuthResult.Success -> when (val reply = result.value) {
                        is MeetingReply.Data -> reconcileWrite(reply.value, id, onSuccess)
                        is MeetingReply.OverlapRequired -> if (id != null) {
                            updateState { it.copy(sheet = MeetingSheet.Overlap(id, reply.overlaps)) }
                        }
                        is MeetingReply.Rejected -> rejectMutation(id, reply.reason.toIssue())
                    }
                }
            } finally {
                if (accessEpoch == epoch) {
                    updateState { it.copy(busyProposalId = null, isCreating = false) }
                    updateEligibility()
                }
            }
        }
    }

    private suspend fun reconcileWrite(
        reply: MeetingProposal,
        id: String?,
        onSuccess: (MeetingProposal) -> Unit,
    ) {
        if (reply.chatId != chatId || (id != null && id != reply.id)) {
            showIssue(MeetingIssue.Gone)
            return
        }
        // A success replay can carry an old projection even if membership changed after the commit.
        val current = read({ meetings.detail(reply.id, it) }, failure = ::showIssue)
        if (current != null && accept(current, reply.id)) {
            onSuccess(snapshots.getValue(reply.id))
            updateEligibility()
        } else if (id == null && authorized()) {
            createAttempt = null
            updateState { it.copy(draft = MeetingDraft(), sheet = MeetingSheet.Detail(reply.id)) }
        }
        sendAction(MeetingViewAction.RefreshChatHistory)
        onRealtimeChange(reply.id)
    }

    private suspend fun rejectMutation(id: String?, issue: MeetingIssue) {
        if (issue == MeetingIssue.NotMember) {
            showIssue(MeetingIssue.Gone)
            sendAction(MeetingViewAction.RefreshChatHistory)
            onRealtimeChange(id)
            return
        }
        showIssue(issue)
        if (issue == MeetingIssue.InvalidField && id == null) {
            updateState { it.copy(draft = it.draft.copy(timeIssue = MeetingIssue.InvalidTime)) }
        }
        if (issue == MeetingIssue.KeyReused) {
            createAttempt = null
            commandAttempt = null
        }
        if (issue in refreshIssues) {
            commandAttempt = null
            if (id != null) updateState { it.copy(sheet = MeetingSheet.Detail(id)) }
            onRealtimeChange(id)
        }
    }

    private suspend fun loseAccess(issue: MeetingIssue) {
        // Chat detail will decide whether the room or membership disappeared.
        updateState { MeetingViewState(issue = issue) }
        sendAction(MeetingViewAction.RefreshChatHistory)
        clear()
        updateState { it.copy(issue = issue) }
    }

    private suspend fun <T> read(
        call: suspend (String) -> AuthResult<MeetingReply<T>>,
        roomScoped: Boolean = false,
        failure: (MeetingIssue) -> Unit,
    ): T? {
        val result = sessions.readProtected(scopeId, call)
        currentCoroutineContext().ensureActive()
        if (!authorized()) return null
        return when (result) {
            is AuthResult.Failure -> null.also { failure(result.error.code.toMeetingIssue()) }
            is AuthResult.Success -> when (val reply = result.value) {
                is MeetingReply.Data -> reply.value
                is MeetingReply.Rejected -> null.also {
                    when (reply.reason) {
                        MeetingRejection.NotMember, MeetingRejection.Gone -> {
                            if (roomScoped) loseAccess(reply.reason.toIssue()) else failure(MeetingIssue.Gone)
                        }
                        else -> failure(reply.reason.toIssue())
                    }
                }
                is MeetingReply.OverlapRequired -> null.also { failure(MeetingIssue.Unavailable) }
            }
        }
    }

    private fun accept(proposal: MeetingProposal, expectedId: String? = null): Boolean {
        if (proposal.chatId != chatId || (expectedId != null && proposal.id != expectedId)) return false
        val old = snapshots[proposal.id]
        if (old == null || proposal.revision >= old.revision) {
            snapshots[proposal.id] = proposal
            if (old?.booking?.status == "active" && proposal.booking?.status == "cancelled") {
                showIssue(MeetingIssue.Cancelled)
            }
        }
        return true
    }

    private fun checkAction(id: String, eligible: MeetingViewState.() -> Set<String>): Boolean {
        if (!canMutate()) return false
        updateEligibility()
        if (id in viewState.value.eligible()) return true
        if (snapshots[id]?.startsAt?.let { it <= clock.now() } == true) showIssue(MeetingIssue.Started)
        return false
    }

    private fun canMutate(): Boolean {
        if (!authorized() || pending()) return false
        if (viewState.value.isOnline) return true
        showIssue(MeetingIssue.Offline)
        return false
    }

    private fun pending() = viewState.value.let { it.isCreating || it.busyProposalId != null }

    private fun updateEligibility() {
        val now = clock.now()
        updateState { state ->
            val upcoming = snapshots.values.filter { it.status != "closed" && it.startsAt > now }
            val canWrite = authorized() && state.isOnline
            val selected = state.sheet?.proposalId?.let(snapshots::get)
            val confirmation = if (selected != null && state.busyProposalId == null) {
                reconcileConfirmation(state, selected)
            } else state
            confirmation.copy(
                currentProposal = pinId?.let(snapshots::get)?.takeIf { it.startsAt > now },
                selectedProposal = selected,
                proposals = state.proposals.mapNotNull { snapshots[it.id] },
                canCreate = canWrite,
                votableIds = if (canWrite) upcoming.mapTo(mutableSetOf(), MeetingProposal::id) else emptySet(),
                bookableIds = if (canWrite) upcoming.filter { it.status == "ready_to_book" && it.viewerGoing }
                    .mapTo(mutableSetOf(), MeetingProposal::id) else emptySet(),
                closableIds = if (canWrite) upcoming.filter { it.proposedByUserId == state.viewerId }
                    .mapTo(mutableSetOf(), MeetingProposal::id) else emptySet(),
            )
        }
    }

    private fun reconcileConfirmation(state: MeetingViewState, proposal: MeetingProposal): MeetingViewState {
        val booking = proposal.booking
        val issue = when (state.sheet) {
            is MeetingSheet.Booking, is MeetingSheet.Overlap -> when {
                booking?.status == "active" && booking.bookedByUserId == state.viewerId -> null
                booking?.status == "active" -> MeetingIssue.AlreadyBooked
                proposal.status == "closed" -> MeetingIssue.Closed
                proposal.status != "ready_to_book" -> MeetingIssue.InsufficientVotes
                !proposal.viewerGoing -> MeetingIssue.VoteRequired
                else -> return state
            }
            is MeetingSheet.Close -> if (proposal.status == "closed") null else return state
            else -> return state
        }
        return state.copy(sheet = MeetingSheet.Detail(proposal.id), issue = issue)
    }

    private fun showIssue(issue: MeetingIssue) = updateState { it.copy(issue = issue) }

    private data class CreateAttempt(val start: Instant, val end: Instant, val key: String)
    private data class CommandAttempt(val id: String, val revision: Long, val kind: CommandKind, val key: String)
    private enum class CommandKind { Book, Close }

    private companion object {
        val refreshIssues = setOf(
            MeetingIssue.Stale, MeetingIssue.Started, MeetingIssue.Closed, MeetingIssue.Gone,
            MeetingIssue.AlreadyBooked, MeetingIssue.InsufficientVotes, MeetingIssue.VoteRequired,
        )
    }
}

private fun AuthErrorCode.toMeetingIssue() = when (this) {
    AuthErrorCode.Network -> MeetingIssue.Offline
    else -> MeetingIssue.Unavailable
}

private fun MeetingRejection.toIssue() = when (this) {
    MeetingRejection.NotMember -> MeetingIssue.NotMember
    MeetingRejection.Gone -> MeetingIssue.Gone
    MeetingRejection.Forbidden -> MeetingIssue.Forbidden
    MeetingRejection.InvalidField -> MeetingIssue.InvalidField
    MeetingRejection.InvalidCursor -> MeetingIssue.InvalidCursor
    MeetingRejection.KeyReused -> MeetingIssue.KeyReused
    MeetingRejection.Stale -> MeetingIssue.Stale
    MeetingRejection.Started -> MeetingIssue.Started
    MeetingRejection.VoteRequired -> MeetingIssue.VoteRequired
    MeetingRejection.InsufficientVotes -> MeetingIssue.InsufficientVotes
    MeetingRejection.Closed -> MeetingIssue.Closed
    MeetingRejection.AlreadyBooked -> MeetingIssue.AlreadyBooked
}
