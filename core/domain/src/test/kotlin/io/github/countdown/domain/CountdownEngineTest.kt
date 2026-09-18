package io.github.countdown.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class CountdownEngineTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private fun event(date: String, repeat: Repeat = Repeat.NONE) = CountdownEvent(
        title = "Birthday", date = LocalDate.parse(date), zone = berlin, repeat = repeat,
        createdAt = Instant.parse("2025-01-01T00:00:00Z"),
    )
    @Test fun monthlyDoesNotDriftAfterFebruary() {
        val e = event("2026-01-31", Repeat.MONTHLY)
        assertEquals(LocalDate.parse("2026-02-28"), Recurrence.next(e, Instant.parse("2026-02-01T00:00:00Z"))!!.date)
        assertEquals(LocalDate.parse("2026-03-31"), Recurrence.next(e, Instant.parse("2026-03-01T00:00:00Z"))!!.date)
    }
    @Test fun leapDayReturnsInLeapYear() {
        val e = event("2024-02-29", Repeat.YEARLY)
        assertEquals(LocalDate.parse("2027-02-28"), Recurrence.next(e, Instant.parse("2027-02-01T00:00:00Z"))!!.date)
        assertEquals(LocalDate.parse("2028-02-29"), Recurrence.next(e, Instant.parse("2028-02-01T00:00:00Z"))!!.date)
    }
    @Test fun allDaySurvivesShortDay() {
        val e = event("2026-03-29")
        val item = Recurrence.occurrence(e)
        assertEquals(23, Duration.between(item.start, item.end).toHours())
        assertEquals(EventStatus.TODAY, CountdownEngine.calculate(e, Instant.parse("2026-03-29T21:59:00Z")).status)
        assertEquals(EventStatus.PAST, CountdownEngine.calculate(e, item.end).status)
    }
    @Test fun dstGapKeepsAnchorAndOverlapUsesEarlierOccurrence() {
        val gap = event("2026-03-29").copy(timeMode = TimeMode.ZONED_LOCAL, time = LocalTime.of(2,30))
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), Recurrence.occurrence(gap).start)
        val overlap = gap.copy(date = LocalDate.parse("2026-10-25"))
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), Recurrence.occurrence(overlap).start)
    }
    @Test fun fixedInstantDoesNotMoveOnZoneChange() {
        val e = event("2026-03-29").copy(timeMode = TimeMode.FIXED_INSTANT, fixedInstant = Instant.parse("2026-03-29T09:00:00Z"))
        assertEquals(Recurrence.occurrence(e).start, Recurrence.occurrence(e.copy(zone = ZoneId.of("Asia/Tokyo"))).start)
    }
    @Test fun weekdaysSkipWeekendAndHistoricalAnchorJumps() {
        val e = event("1900-01-01", Repeat.WEEKDAYS)
        assertEquals(LocalDate.parse("2026-09-21"), Recurrence.next(e, Instant.parse("2026-09-19T10:00:00Z"))!!.date)
    }
    @Test fun targetMomentAndPastAreDistinct() {
        val e = event("2026-09-21").copy(timeMode = TimeMode.ZONED_LOCAL, format = CountdownFormat.DETAILED)
        val now = Recurrence.occurrence(e).start
        assertEquals(EventStatus.STARTED, CountdownEngine.calculate(e, now).status)
        assertEquals(EventStatus.PAST, CountdownEngine.calculate(e, now.plusMillis(1)).status)
        assertEquals(1, CountdownEngine.calculate(e, now.minusMillis(1)).seconds)
    }
    @Test fun nextDeadlineAlwaysInFutureAndProgressClamped() {
        for (day in 1..365) {
            val now = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(day * 86400L)
            val result = CountdownEngine.calculate(event("2026-07-01"), now)
            assertTrue(result.nextChange == null || result.nextChange > now)
            assertTrue(result.progress == null || result.progress in 0f..1f)
        }
    }
    @Test fun exhaustedRecurrenceDoesNotWrap() {
        assertNull(Recurrence.next(event("9999-01-01", Repeat.YEARLY), Instant.parse("9999-12-31T12:00:00Z")))
    }
    @Test fun autoHoursDoNotWakeEveryMinute() {
        val e = event("2026-09-21").copy(timeMode = TimeMode.ZONED_LOCAL)
        val now = Recurrence.occurrence(e).start.minusSeconds(7200)
        assertEquals(now.plusSeconds(3600), CountdownEngine.calculate(e, now).nextChange)
    }
    @Test fun elapsedAutoRefreshesAtNextMinute() {
        val e = event("2026-09-21").copy(timeMode = TimeMode.ZONED_LOCAL)
        val target = Recurrence.occurrence(e).start
        assertEquals(target.plusSeconds(60), CountdownEngine.calculate(e, target.plusSeconds(10)).nextChange)
    }
}
