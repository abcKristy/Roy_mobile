package com.example.roy.presentation.meetings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.roy.presentation.components.PrimaryButton
import com.example.roy.presentation.components.SecondaryButton
import com.example.roy.presentation.components.TertiaryButton
import com.example.roy.presentation.components.cards.RoyCard
import com.example.roy.presentation.components.fields.RoyTextField
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.meetings_choose
import roy.shared.generated.resources.meetings_choose_date
import roy.shared.generated.resources.meetings_choose_time
import roy.shared.generated.resources.meetings_date
import roy.shared.generated.resources.meetings_day
import roy.shared.generated.resources.meetings_decrease
import roy.shared.generated.resources.meetings_duration
import roy.shared.generated.resources.meetings_duration_custom
import roy.shared.generated.resources.meetings_duration_minutes
import roy.shared.generated.resources.meetings_hour
import roy.shared.generated.resources.meetings_increase
import roy.shared.generated.resources.meetings_minute
import roy.shared.generated.resources.meetings_minutes
import roy.shared.generated.resources.meetings_month
import roy.shared.generated.resources.meetings_submit
import roy.shared.generated.resources.meetings_time
import roy.shared.generated.resources.meetings_year
import roy.shared.generated.resources.point_detail_cancel

@Composable
internal fun MeetingCreateFields(state: MeetingViewState, eventHandler: (MeetingViewEvent) -> Unit) {
    val draft = state.draft
    val spacing = RoyTheme.dimensions.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.compact)) {
        MeetingDateTimeFields(draft, isEnabled = !state.isCreating, eventHandler = eventHandler)
        BasicText(
            text = stringResource(Res.string.meetings_duration),
            style = RoyTheme.typography.body.copy(color = RoyTheme.colors.textPrimary),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            for (minutes in listOf(60, 90, 120, 180)) {
                MeetingDurationOption(
                    label = stringResource(Res.string.meetings_minutes, minutes),
                    isSelected = draft.durationMinutes == minutes,
                    isEnabled = !state.isCreating,
                    onSelect = { eventHandler(MeetingViewEvent.OnDurationChanged(minutes)) },
                )
            }
            MeetingDurationOption(
                label = stringResource(Res.string.meetings_duration_custom),
                isSelected = draft.durationMinutes == null,
                isEnabled = !state.isCreating,
                onSelect = { eventHandler(MeetingViewEvent.OnDurationChanged(null)) },
            )
        }
        if (draft.durationMinutes == null) {
            RoyTextField(
                value = draft.customMinutes,
                label = stringResource(Res.string.meetings_duration_minutes),
                onValueChange = { eventHandler(MeetingViewEvent.OnCustomDurationChanged(it)) },
                errorText = draft.durationIssue?.let { stringResource(it.resource) },
                isEnabled = !state.isCreating,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        } else {
            draft.durationIssue?.let { MeetingIssueText(it) }
        }
        PrimaryButton(
            text = stringResource(Res.string.meetings_submit),
            onClick = { eventHandler(MeetingViewEvent.OnCreateConfirmed) },
            modifier = Modifier.fillMaxWidth(),
            isEnabled = state.canCreate,
            isSubmitting = state.isCreating,
        )
    }
}

@Composable
private fun MeetingDurationOption(label: String, isSelected: Boolean, isEnabled: Boolean, onSelect: () -> Unit) {
    RoyCard(isSelected = isSelected, onClick = onSelect.takeIf { isEnabled }) {
        BasicText(label, style = RoyTheme.typography.bodySmall.copy(color = RoyTheme.colors.textPrimary))
    }
}

@Composable
private fun MeetingDateTimeFields(
    draft: MeetingDraft,
    isEnabled: Boolean,
    eventHandler: (MeetingViewEvent) -> Unit,
) {
    val initialDate = remember { draft.date.asCalendarValue() }
    val initialTime = remember { draft.time.asClockValue() }
    var datePicker by remember { mutableStateOf<CalendarValue?>(null) }
    var timePicker by remember { mutableStateOf<ClockValue?>(null) }
    val chooseDate = stringResource(Res.string.meetings_choose_date)
    val chooseTime = stringResource(Res.string.meetings_choose_time)
    val dateValue = draft.date.asCalendarValue() ?: initialDate
    val timeValue = draft.time.asClockValue() ?: initialTime
    RoyTextField(
        value = draft.date,
        label = stringResource(Res.string.meetings_date),
        onValueChange = { eventHandler(MeetingViewEvent.OnDateChanged(it)) },
        errorText = draft.dateIssue?.let { stringResource(it.resource) },
        isEnabled = isEnabled,
        trailingContent = {
            TertiaryButton(
                label = stringResource(Res.string.meetings_choose),
                onClick = { datePicker = dateValue },
                modifier = Modifier.semantics { contentDescription = chooseDate },
                isEnabled = isEnabled && dateValue != null,
            )
        },
    )
    RoyTextField(
        value = draft.time,
        label = stringResource(Res.string.meetings_time),
        onValueChange = { eventHandler(MeetingViewEvent.OnTimeChanged(it)) },
        errorText = draft.timeIssue?.let { stringResource(it.resource) },
        isEnabled = isEnabled,
        trailingContent = {
            TertiaryButton(
                label = stringResource(Res.string.meetings_choose),
                onClick = { timePicker = timeValue },
                modifier = Modifier.semantics { contentDescription = chooseTime },
                isEnabled = isEnabled && timeValue != null,
            )
        },
    )
    datePicker?.let { value ->
        MeetingValueDialog(
            title = stringResource(Res.string.meetings_choose_date),
            onDismiss = { datePicker = null },
            onConfirm = {
                eventHandler(MeetingViewEvent.OnDateChanged(value.toIsoDate()))
                datePicker = null
            },
        ) {
            Stepper(stringResource(Res.string.meetings_year), value.year.toString()) {
                datePicker = value.withCalendar(year = (value.year + it).coerceIn(1, 9999))
            }
            Stepper(stringResource(Res.string.meetings_month), value.month.twoDigits()) {
                datePicker = value.withCalendar(month = wrapped(value.month + it, 1, 12))
            }
            Stepper(stringResource(Res.string.meetings_day), value.day.twoDigits()) {
                datePicker = value.copy(day = wrapped(value.day + it, 1, daysInMonth(value.year, value.month)))
            }
        }
    }
    timePicker?.let { value ->
        MeetingValueDialog(
            title = stringResource(Res.string.meetings_choose_time),
            onDismiss = { timePicker = null },
            onConfirm = {
                eventHandler(MeetingViewEvent.OnTimeChanged("${value.hour.twoDigits()}:${value.minute.twoDigits()}"))
                timePicker = null
            },
        ) {
            Stepper(stringResource(Res.string.meetings_hour), value.hour.twoDigits()) {
                timePicker = value.copy(hour = wrapped(value.hour + it, 0, 23))
            }
            Stepper(stringResource(Res.string.meetings_minute), value.minute.twoDigits()) {
                timePicker = value.copy(minute = wrapped(value.minute + it, 0, 59))
            }
        }
    }
}

@Composable
private fun MeetingValueDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    fields: @Composable () -> Unit,
) {
    val dimensions = RoyTheme.dimensions
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()
                .background(RoyTheme.colors.surfaceElevated, RoundedCornerShape(dimensions.radii.dialog))
                .verticalScroll(rememberScrollState()).padding(dimensions.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(dimensions.spacing.small),
        ) {
            BasicText(
                title,
                modifier = Modifier.semantics { heading() },
                style = RoyTheme.typography.headingSmall.copy(color = RoyTheme.colors.textPrimary),
            )
            fields()
            PrimaryButton(stringResource(Res.string.meetings_choose), onConfirm, Modifier.fillMaxWidth())
            SecondaryButton(stringResource(Res.string.point_detail_cancel), onDismiss, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onStep: (Int) -> Unit) {
    val smaller = stringResource(Res.string.meetings_decrease, label)
    val larger = stringResource(Res.string.meetings_increase, label)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RoyTheme.dimensions.spacing.small),
    ) {
        BasicText("$label $value", modifier = Modifier.weight(1f),
            style = RoyTheme.typography.body.copy(color = RoyTheme.colors.textPrimary))
        TertiaryButton("−", { onStep(-1) }, Modifier.semantics { contentDescription = smaller })
        TertiaryButton("+", { onStep(1) }, Modifier.semantics { contentDescription = larger })
    }
}

private data class CalendarValue(val year: Int, val month: Int, val day: Int) {
    fun toIsoDate(): String = "${year.toString().padStart(4, '0')}-${month.twoDigits()}-${day.twoDigits()}"

    fun withCalendar(year: Int = this.year, month: Int = this.month): CalendarValue =
        CalendarValue(year, month, day.coerceAtMost(daysInMonth(year, month)))
}

private data class ClockValue(val hour: Int, val minute: Int)

private fun String.asCalendarValue(): CalendarValue? {
    val parts = split('-')
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull()?.takeIf { it in 1..9999 } ?: return null
    val month = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    val day = parts[2].toIntOrNull()?.takeIf { it in 1..daysInMonth(year, month) } ?: return null
    return CalendarValue(year, month, day)
}

private fun String.asClockValue(): ClockValue? {
    val parts = split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
    val minute = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
    return ClockValue(hour, minute)
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    4, 6, 9, 11 -> 30
    2 -> if (year % 400 == 0 || year % 4 == 0 && year % 100 != 0) 29 else 28
    else -> 31
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')
private fun wrapped(value: Int, min: Int, max: Int): Int = when {
    value < min -> max
    value > max -> min
    else -> value
}
