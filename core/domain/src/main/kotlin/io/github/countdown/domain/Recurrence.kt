package io.github.countdown.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Calendar rules always expand from the original anchor, never a clamped occurrence. */
object Recurrence {
    fun occurrence(event: CountdownEvent, date: LocalDate = event.date): Occurrence {
        val start = when (event.timeMode) {
            TimeMode.ALL_DAY -> date.atStartOfDay(event.zone).toInstant()
            TimeMode.ZONED_LOCAL -> date.atTime(event.time).atZone(event.zone).toInstant()
            TimeMode.FIXED_INSTANT -> requireNotNull(event.fixedInstant)
        }
        val end = if (event.timeMode == TimeMode.ALL_DAY) date.plusDays(1).atStartOfDay(event.zone).toInstant() else start
        return Occurrence(if (event.timeMode == TimeMode.FIXED_INSTANT) start.atZone(event.zone).toLocalDate() else date, start, end)
    }

    fun next(event: CountdownEvent, now: Instant): Occurrence? {
        if (event.repeat == Repeat.NONE) return occurrence(event)
        val today = now.atZone(event.zone).toLocalDate()
        val anchor = event.date
        val days = ChronoUnit.DAYS.between(anchor, today).coerceAtLeast(0)
        var index = when (event.repeat) {
            Repeat.MONTHLY, Repeat.MONTH_END -> ChronoUnit.MONTHS.between(YearMonth.from(anchor), YearMonth.from(today)).coerceAtLeast(0)
            Repeat.YEARLY -> (today.year - anchor.year).toLong().coerceAtLeast(0)
            Repeat.WEEKLY -> days / 7
            Repeat.EVERY_N_DAYS -> days / event.repeatDays
            else -> days
        }
        // At most a skipped civil day, a weekend and the adjacent candidate need examining.
        repeat(10) {
            val candidate = when (event.repeat) {
                Repeat.MONTHLY -> YearMonth.from(anchor).plusMonths(index).let { it.atDay(minOf(anchor.dayOfMonth, it.lengthOfMonth())) }
                Repeat.MONTH_END -> YearMonth.from(anchor).plusMonths(index).atEndOfMonth()
                Repeat.YEARLY -> YearMonth.of(anchor.year + index.toInt(), anchor.month).let { it.atDay(minOf(anchor.dayOfMonth, it.lengthOfMonth())) }
                Repeat.WEEKLY -> anchor.plusDays(index * 7)
                Repeat.EVERY_N_DAYS -> anchor.plusDays(index * event.repeatDays)
                else -> anchor.plusDays(index)
            }
            if (candidate.year > 9999) return null
            val weekday = candidate.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
            val item = occurrence(event, candidate)
            val acceptable = if (event.timeMode == TimeMode.ALL_DAY) item.end > now && item.end > item.start else item.start >= now
            if (acceptable && (event.repeat != Repeat.WEEKDAYS || weekday)) return item
            index++
        }
        return null
    }
}
