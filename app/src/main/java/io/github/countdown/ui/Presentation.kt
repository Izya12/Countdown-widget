package io.github.countdown.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import io.github.countdown.R
import io.github.countdown.data.CategoryEntity
import io.github.countdown.data.ThemeMode
import io.github.countdown.domain.*
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs

val iconResources = linkedMapOf("event" to R.drawable.ic_event, "travel" to R.drawable.ic_travel,
    "work" to R.drawable.ic_work, "heart" to R.drawable.ic_heart, "star" to R.drawable.ic_star, "home" to R.drawable.ic_home)
val iconLabels = listOf(R.string.icon_event, R.string.icon_travel, R.string.icon_work, R.string.icon_heart, R.string.icon_star, R.string.icon_home)
val accents = listOf(0xFF245C4C, 0xFF245E91, 0xFF9B364B, 0xFF825B17, 0xFF765087, 0xFF58646C)
val accentLabels = listOf(R.string.color_green, R.string.color_blue, R.string.color_red, R.string.color_gold, R.string.color_purple, R.string.color_gray)
val repeatLabels = listOf(R.string.repeat_none, R.string.repeat_daily, R.string.repeat_weekly, R.string.repeat_monthly,
    R.string.repeat_month_end, R.string.repeat_yearly, R.string.repeat_weekdays, R.string.repeat_custom)
val formatLabels = listOf(R.string.format_auto, R.string.format_days, R.string.format_weeks, R.string.format_calendar, R.string.format_detailed)
val policyLabels = listOf(R.string.policy_elapsed, R.string.policy_complete, R.string.policy_archive)
val categoryLabels = mapOf("birthday" to R.string.cat_birthday, "holiday" to R.string.cat_holiday,
    "travel" to R.string.cat_travel, "work" to R.string.cat_work, "personal" to R.string.cat_personal,
    "anniversary" to R.string.cat_anniversary, "other" to R.string.cat_other)
fun CategoryEntity.label(context: Context): String = seedKey?.let { categoryLabels[it] }?.let(context::getString) ?: name

@Composable fun CountdownTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())
    val context = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme(primary = Color(0xFFACD1B9), background = Color(0xFF141B17), surface = Color(0xFF141B17))
    else lightColorScheme(primary = Color(0xFF245C4C), background = Color(0xFFFAF9F6), surface = Color(0xFFFAF9F6))
    MaterialTheme(colorScheme = colors, content = content)
}

fun dateLabel(context: Context, e: CountdownEvent, s: CountdownSnapshot): String {
    val date = s.occurrence?.date ?: e.date
    val locale = context.resources.configuration.locales[0]
    val formatted = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    if (e.timeMode == TimeMode.ALL_DAY) return formatted
    val zoned = (s.occurrence?.start ?: Recurrence.occurrence(e).start).atZone(e.zone)
    val timePattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return "$formatted · ${zoned.format(DateTimeFormatter.ofPattern(timePattern, locale))} · ${e.zone.id}"
}

fun countdownLabel(context: Context, e: CountdownEvent, s: CountdownSnapshot, widget: Boolean = false): String {
    val res = context.resources
    fun plural(id: Int, n: Long) = res.getQuantityString(id, n.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(), n)
    when (s.status) {
        EventStatus.ARCHIVED -> return context.getString(R.string.archived)
        EventStatus.COMPLETED -> return context.getString(R.string.completed)
        EventStatus.EXHAUSTED -> return context.getString(R.string.exhausted)
        EventStatus.TODAY -> return context.getString(R.string.today)
        EventStatus.STARTED -> return context.getString(R.string.started)
        else -> Unit
    }
    val days = abs(s.calendarDays)
    val seconds = abs(s.seconds)
    val future = s.status != EventStatus.PAST
    val format = if (widget && e.format == CountdownFormat.DETAILED) CountdownFormat.DAYS else e.format
    val text = when (format) {
        CountdownFormat.DAYS -> plural(R.plurals.days, days)
        CountdownFormat.WEEKS -> context.getString(R.string.week_fraction,
            NumberFormat.getNumberInstance(res.configuration.locales[0]).apply { maximumFractionDigits = 1 }.format(days / 7.0))
        CountdownFormat.CALENDAR -> listOf(
            s.period.years.takeIf { it > 0 }?.let { plural(R.plurals.years, it.toLong()) },
            s.period.months.takeIf { it > 0 }?.let { plural(R.plurals.months, it.toLong()) },
            s.period.days.takeIf { it > 0 }?.let { plural(R.plurals.days, it.toLong()) },
        ).filterNotNull().joinToString(" ").ifEmpty { plural(R.plurals.days, 0) }
        CountdownFormat.DETAILED -> context.getString(R.string.detailed_value, seconds / 86400,
            String.format(res.configuration.locales[0], "%02d:%02d:%02d", seconds / 3600 % 24, seconds / 60 % 60, seconds % 60))
        CountdownFormat.AUTO -> when {
            widget -> when {
                future && days == 0L -> context.getString(R.string.today)
                future && days == 1L -> context.getString(R.string.tomorrow)
                else -> plural(R.plurals.days, days)
            }
            e.timeMode != TimeMode.ALL_DAY && seconds < 3600 -> plural(R.plurals.minutes, (seconds + if (future) 59 else 0) / 60)
            e.timeMode != TimeMode.ALL_DAY && seconds < 172800 -> plural(R.plurals.hours, (seconds + if (future) 3599 else 0) / 3600)
            future && days == 1L -> context.getString(R.string.tomorrow)
            else -> plural(R.plurals.days, days)
        }
    }
    return if (!future) context.getString(R.string.ago, text) else text
}
