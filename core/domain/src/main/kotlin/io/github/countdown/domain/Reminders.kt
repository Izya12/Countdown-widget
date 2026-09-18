package io.github.countdown.domain

import java.time.Instant
import java.time.LocalTime
import java.util.UUID

enum class ReminderKind { DURATION_BEFORE, CALENDAR_DAYS_BEFORE }
data class ReminderRule(
    val id: String = UUID.randomUUID().toString(),
    val eventId: String,
    val kind: ReminderKind,
    val offsetSeconds: Long = 0,
    val daysBefore: Int = 0,
    val localTime: LocalTime = LocalTime.of(9, 0),
    val enabled: Boolean = true,
) {
    fun validate(event: CountdownEvent) {
        require(UUID.fromString(id).toString() == id && eventId == event.id)
        require(offsetSeconds in 0..31_536_000L && daysBefore in 0..3650)
        require(if (kind == ReminderKind.DURATION_BEFORE) daysBefore == 0 && event.timeMode != TimeMode.ALL_DAY else offsetSeconds == 0L)
    }
}
data class ReminderOccurrence(val occurrence: Occurrence, val due: Instant)

object ReminderPlanner {
    /** Inclusive lower bound. Search in occurrence time, so long offsets never skip future reminders. */
    fun next(event: CountdownEvent, rule: ReminderRule, from: Instant): ReminderOccurrence? {
        rule.validate(event)
        if (!rule.enabled || event.archived) return null
        return try {
            var cursor = if (rule.kind == ReminderKind.DURATION_BEFORE) from.plusSeconds(rule.offsetSeconds)
            else from.atZone(event.zone).toLocalDate().plusDays(rule.daysBefore.toLong()).minusDays(2).atStartOfDay(event.zone).toInstant()
            repeat(8) {
                val occurrence = Recurrence.next(event, cursor) ?: return null
                val due = when (rule.kind) {
                    ReminderKind.DURATION_BEFORE -> occurrence.start.minusSeconds(rule.offsetSeconds)
                    ReminderKind.CALENDAR_DAYS_BEFORE -> occurrence.date.minusDays(rule.daysBefore.toLong())
                        .atTime(if (event.timeMode == TimeMode.ALL_DAY) rule.localTime else occurrence.start.atZone(event.zone).toLocalTime())
                        .atZone(event.zone).toInstant()
                }
                if (due >= from) return ReminderOccurrence(occurrence, due)
                if (event.repeat == Repeat.NONE) return null
                cursor = if (event.timeMode == TimeMode.ALL_DAY) occurrence.end else occurrence.start.plusNanos(1)
            }
            null
        } catch (_: java.time.DateTimeException) { null }
    }
}
