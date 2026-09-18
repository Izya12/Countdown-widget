package io.github.countdown.domain

import java.time.Duration
import java.time.Instant
import java.time.Period
import java.time.temporal.ChronoUnit

enum class EventStatus { UPCOMING, TODAY, STARTED, PAST, COMPLETED, ARCHIVED, EXHAUSTED }
data class CountdownSnapshot(
    val occurrence: Occurrence?,
    val status: EventStatus,
    val calendarDays: Long,
    val seconds: Long,
    val period: Period,
    val progress: Float?,
    val nextChange: Instant?,
)

object CountdownEngine {
    fun calculate(event: CountdownEvent, now: Instant): CountdownSnapshot {
        val occurrence = Recurrence.next(event, now)
            ?: return CountdownSnapshot(null, EventStatus.EXHAUSTED, 0, 0, Period.ZERO, null, null)
        val today = now.atZone(event.zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(today, occurrence.date)
        val duration = Duration.between(now, occurrence.start)
        val seconds = if (duration.isNegative) -Duration.between(occurrence.start, now).seconds else duration.seconds + if (duration.nano > 0) 1 else 0
        val expired = if (event.timeMode == TimeMode.ALL_DAY) now >= occurrence.end else now > occurrence.start
        val status = when {
            event.archived || (expired && event.completion == CompletionPolicy.ARCHIVE) -> EventStatus.ARCHIVED
            expired && event.completion == CompletionPolicy.COMPLETE -> EventStatus.COMPLETED
            expired -> EventStatus.PAST
            event.timeMode == TimeMode.ALL_DAY && days == 0L -> EventStatus.TODAY
            now == occurrence.start -> EventStatus.STARTED
            else -> EventStatus.UPCOMING
        }
        val period = if (days >= 0) Period.between(today, occurrence.date) else Period.between(occurrence.date, today)
        val start = event.progressStart ?: if (event.repeat == Repeat.NONE) event.createdAt else maxOf(event.createdAt, previousStart(event, occurrence))
        val total = Duration.between(start, occurrence.start).toMillis()
        val progress = if (total <= 0) null else (Duration.between(start, now).toMillis().toDouble() / total).coerceIn(0.0, 1.0).toFloat()
        val tomorrow = today.plusDays(1).atStartOfDay(event.zone).toInstant()
        val tick = when {
            event.timeMode == TimeMode.ALL_DAY -> tomorrow
            event.format == CountdownFormat.DETAILED -> now.truncatedTo(ChronoUnit.SECONDS).plusSeconds(1)
            event.format == CountdownFormat.AUTO && seconds in 1..172799 -> {
                val unit = if (seconds >= 3600) 3600L else 60L
                now.plusSeconds((seconds % unit).takeIf { it > 0 } ?: unit)
            }
            event.format == CountdownFormat.AUTO && seconds <= 0 && seconds > -172800 -> {
                val elapsed = -seconds
                val unit = if (elapsed >= 3600) 3600L else 60L
                now.plusSeconds(unit - elapsed % unit)
            }
            else -> tomorrow
        }
        val boundary = if (event.timeMode == TimeMode.ALL_DAY) occurrence.end else occurrence.start.plusMillis(1)
        val autoThreshold = if (event.timeMode != TimeMode.ALL_DAY && event.format == CountdownFormat.AUTO && seconds >= 172800)
            occurrence.start.minusSeconds(172800).plusMillis(1) else tick
        val next = if (status in setOf(EventStatus.ARCHIVED, EventStatus.COMPLETED)) null else listOf(tick, boundary, autoThreshold).filter { it > now }.minOrNull()
        return CountdownSnapshot(occurrence, status, days, seconds, period, progress, next)
    }

    private fun previousStart(event: CountdownEvent, occurrence: Occurrence): Instant {
        val d = occurrence.date
        val previousDate = when (event.repeat) {
            Repeat.DAILY -> d.minusDays(1)
            Repeat.WEEKLY -> d.minusWeeks(1)
            Repeat.EVERY_N_DAYS -> d.minusDays(event.repeatDays.toLong())
            Repeat.MONTH_END -> java.time.YearMonth.from(d).minusMonths(1).atEndOfMonth()
            Repeat.MONTHLY -> java.time.YearMonth.from(d).minusMonths(1).let { it.atDay(minOf(event.date.dayOfMonth, it.lengthOfMonth())) }
            Repeat.YEARLY -> java.time.YearMonth.of(d.year - 1, event.date.month).let { it.atDay(minOf(event.date.dayOfMonth, it.lengthOfMonth())) }
            Repeat.WEEKDAYS -> generateSequence(d.minusDays(1)) { it.minusDays(1) }.first { it.dayOfWeek.value <= 5 }
            Repeat.NONE -> event.date
        }
        return Recurrence.occurrence(event, previousDate).start
    }
}
