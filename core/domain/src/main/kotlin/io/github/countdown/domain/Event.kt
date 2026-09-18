package io.github.countdown.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

enum class TimeMode { ALL_DAY, ZONED_LOCAL, FIXED_INSTANT }
enum class Repeat { NONE, DAILY, WEEKLY, MONTHLY, MONTH_END, YEARLY, WEEKDAYS, EVERY_N_DAYS }
enum class CountdownFormat { AUTO, DAYS, WEEKS, CALENDAR, DETAILED }
enum class CompletionPolicy { ELAPSED, COMPLETE, ARCHIVE }

data class CountdownEvent(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val date: LocalDate,
    val time: LocalTime = LocalTime.of(9, 0),
    val zone: ZoneId = ZoneId.systemDefault(),
    val timeMode: TimeMode = TimeMode.ALL_DAY,
    val fixedInstant: Instant? = null,
    val repeat: Repeat = Repeat.NONE,
    val repeatDays: Int = 1,
    val categoryId: String? = null,
    val icon: String = "event",
    val color: Long = 0xFF245C4C,
    val format: CountdownFormat = CountdownFormat.AUTO,
    val completion: CompletionPolicy = CompletionPolicy.ELAPSED,
    val createdAt: Instant = Instant.now(),
    val modifiedAt: Instant = createdAt,
    val progressStart: Instant? = null,
    val archived: Boolean = false,
    val favorite: Boolean = false,
    val pinned: Boolean = false,
    val sortRank: Long = 0,
) {
    fun validate() {
        UUID.fromString(id)
        require(title.isNotBlank() && title.length <= 120) { "title" }
        require(description.length <= 4000) { "description" }
        require(date.year in 1900..9999) { "date" }
        require(repeatDays in 1..36500) { "repeatDays" }
        require(color in 0..0xFFFFFFFF) { "color" }
        require((timeMode == TimeMode.FIXED_INSTANT) == (fixedInstant != null)) { "fixedInstant" }
        require(timeMode != TimeMode.FIXED_INSTANT || repeat == Repeat.NONE) { "repeat" }
    }
}

data class Occurrence(val date: LocalDate, val start: Instant, val end: Instant)
