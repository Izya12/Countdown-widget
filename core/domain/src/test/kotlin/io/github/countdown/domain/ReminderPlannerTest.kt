package io.github.countdown.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderPlannerTest {
    private val event = CountdownEvent(title = "Test", date = LocalDate.of(2026, 1, 1), time = LocalTime.of(12, 0), zone = ZoneId.of("UTC"), timeMode = TimeMode.ZONED_LOCAL, repeat = Repeat.DAILY)
    @Test fun offsetLongerThanPeriodFindsFutureOccurrence() {
        val rule = ReminderRule(eventId = event.id, kind = ReminderKind.DURATION_BEFORE, offsetSeconds = 3 * 86400)
        val next = ReminderPlanner.next(event, rule, Instant.parse("2026-02-01T13:00:00Z"))!!
        assertEquals(Instant.parse("2026-02-02T12:00:00Z"), next.due)
        assertEquals(LocalDate.of(2026, 2, 5), next.occurrence.date)
    }
    @Test fun calendarDayAnd24HoursDifferAtDst() {
        val e = event.copy(date = LocalDate.of(2026, 3, 8), time = LocalTime.of(9, 0), zone = ZoneId.of("America/New_York"), repeat = Repeat.NONE)
        val from = Instant.parse("2026-03-01T00:00:00Z")
        val calendar = ReminderPlanner.next(e, ReminderRule(eventId = e.id, kind = ReminderKind.CALENDAR_DAYS_BEFORE, daysBefore = 1), from)!!
        val duration = ReminderPlanner.next(e, ReminderRule(eventId = e.id, kind = ReminderKind.DURATION_BEFORE, offsetSeconds = 86400), from)!!
        assertEquals(Instant.parse("2026-03-07T14:00:00Z"), calendar.due)
        assertEquals(Instant.parse("2026-03-07T13:00:00Z"), duration.due)
    }
    @Test fun allDayReminderUsesSpecifiedLocalTime() {
        val e = event.copy(timeMode = TimeMode.ALL_DAY)
        val next = ReminderPlanner.next(e, ReminderRule(eventId = e.id, kind = ReminderKind.CALENDAR_DAYS_BEFORE, localTime = LocalTime.of(18, 30)), Instant.parse("2026-02-01T12:00:00Z"))!!
        assertEquals(Instant.parse("2026-02-01T18:30:00Z"), next.due)
    }
    @Test fun allDayRejectsDuration() {
        assertThrows(IllegalArgumentException::class.java) { ReminderPlanner.next(event.copy(timeMode = TimeMode.ALL_DAY), ReminderRule(eventId = event.id, kind = ReminderKind.DURATION_BEFORE), Instant.now()) }
    }
    @Test fun pastSingleOccurrenceDoesNotRepeat() {
        assertNull(ReminderPlanner.next(event.copy(repeat = Repeat.NONE), ReminderRule(eventId = event.id, kind = ReminderKind.DURATION_BEFORE), Instant.parse("2026-02-01T00:00:00Z")))
    }
    @Test fun exactBoundaryIsInclusive() {
        val now = Instant.parse("2026-02-01T12:00:00Z")
        assertEquals(now, ReminderPlanner.next(event, ReminderRule(eventId = event.id, kind = ReminderKind.DURATION_BEFORE), now)!!.due)
    }
    @Test fun archiveAndDisabledRulesDoNotSchedule() {
        val rule = ReminderRule(eventId = event.id, kind = ReminderKind.DURATION_BEFORE)
        assertNull(ReminderPlanner.next(event.copy(archived = true), rule, Instant.now()))
        assertNull(ReminderPlanner.next(event, rule.copy(enabled = false), Instant.now()))
    }
}
