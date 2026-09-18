package io.github.countdown.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import io.github.countdown.CountdownApp
import io.github.countdown.MainActivity
import io.github.countdown.R
import io.github.countdown.data.WidgetConfig
import io.github.countdown.domain.*
import io.github.countdown.ui.countdownLabel
import io.github.countdown.ui.dateLabel
import java.time.YearMonth
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.WeekFields

open class CountdownWidget(private val circular: Boolean = false) : GlanceAppWidget() {
    override val sizeMode = if (circular) SizeMode.Exact else SizeMode.Responsive(setOf(androidx.compose.ui.unit.DpSize(110.dp, 90.dp),
        androidx.compose.ui.unit.DpSize(180.dp, 110.dp), androidx.compose.ui.unit.DpSize(250.dp, 180.dp), androidx.compose.ui.unit.DpSize(250.dp, 250.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as CountdownApp
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val config = app.database.dao().widget(widgetId)
        val event = config?.eventId?.let { app.database.dao().event(it)?.domain() }?.let {
            if (circular) circleEvent(it, config.style) else it
        }
        val snapshot = event?.let { CountdownEngine.calculate(it, app.clock.instant()) }
        provideContent { GlanceTheme { WidgetContent(context, widgetId, config, event, snapshot, circular) } }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        (context.applicationContext as CountdownApp).database.dao().deleteWidget(widgetId)
        WidgetUpdates.request(context)
    }
}

internal fun circleEvent(event: CountdownEvent, style: String): CountdownEvent = when (style) {
    "CIRCLE_DAYS" -> event.copy(format = CountdownFormat.DAYS)
    "CIRCLE_WEEKS" -> event.copy(format = CountdownFormat.WEEKS)
    else -> event
}

class CircleCountdownWidget : CountdownWidget(true)
class CircleCountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = CircleCountdownWidget()
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        WidgetUpdates.restore(context, oldWidgetIds, newWidgetIds)
    }
}

class CountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = CountdownWidget()
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        WidgetUpdates.restore(context, oldWidgetIds, newWidgetIds)
    }
}

@Composable private fun WidgetContent(context: Context, widgetId: Int, config: WidgetConfig?, event: CountdownEvent?, snapshot: CountdownSnapshot?, circular: Boolean) {
    val size = LocalSize.current
    val small = size.width < 180.dp
    val large = size.height >= 180.dp
    val action = if (event != null) Intent(context, MainActivity::class.java).putExtra("eventId", event.id)
        else Intent(context, WidgetConfigurationActivity::class.java).setAction(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
    if (circular) {
        val bitmap = RoundWidgetRenderer.render(context, event, snapshot, config?.showProgress != false, size.width.value, size.height.value)
        Image(ImageProvider(bitmap), contentDescription = event?.let { it.title + ": " + countdownLabel(context, it, requireNotNull(snapshot), widget = true) } ?: context.getString(R.string.choose_event),
            modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(action)), contentScale = ContentScale.Fit)
        return
    }
    Column(GlanceModifier.fillMaxSize().appWidgetBackground().background(GlanceTheme.colors.widgetBackground)
        .cornerRadius(20.dp).padding(if (small) 12.dp else 16.dp).clickable(actionStartActivity(action)),
        verticalAlignment = Alignment.CenterVertically) {
        if (event == null || snapshot == null) {
            Text(context.getString(R.string.widget_missing), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp))
        } else {
            val textColor = GlanceTheme.colors.onSurface
            if (!small && config?.style != "MINIMAL") {
                Text(event.title, maxLines = 2, style = TextStyle(color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Medium))
                Spacer(GlanceModifier.height(8.dp))
            }
            Text(countdownLabel(context, event, snapshot, widget = true), maxLines = 2,
                style = TextStyle(color = textColor, fontSize = if (small) 22.sp else 28.sp, fontWeight = FontWeight.Bold))
            if (config?.showDate == true && !small) {
                Spacer(GlanceModifier.height(8.dp))
                Text(dateLabel(context, event, snapshot), maxLines = 2, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
            }
            val progress = snapshot.progress
            val occurrence = snapshot.occurrence
            if (config?.style == "PROGRESS" && config.showProgress && progress != null && !small) {
                Spacer(GlanceModifier.height(10.dp))
                LinearProgressIndicator(progress = progress, modifier = GlanceModifier.fillMaxWidth())
            }
            if (config?.style == "CALENDAR" && size.height >= 250.dp && occurrence != null) {
                Spacer(GlanceModifier.height(8.dp))
                CalendarGrid(context, occurrence.date)
            }
        }
    }
}

@Composable private fun CalendarGrid(context: Context, date: java.time.LocalDate) {
    val locale = context.resources.configuration.locales[0]
    val month = YearMonth.from(date)
    val start = WeekFields.of(locale).firstDayOfWeek.value
    val offset = (month.atDay(1).dayOfWeek.value - start + 7) % 7
    val rows = (offset + month.lengthOfMonth() + 6) / 7
    Row(GlanceModifier.fillMaxWidth()) {
        repeat(7) { col -> Text(java.time.DayOfWeek.of((start - 1 + col) % 7 + 1).getDisplayName(JavaTextStyle.NARROW, locale),
            GlanceModifier.defaultWeight(), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp)) }
    }
    repeat(rows) { row ->
        Row(GlanceModifier.fillMaxWidth()) {
            repeat(7) { col ->
                val day = row * 7 + col - offset + 1
                Text(if (day in 1..month.lengthOfMonth()) day.toString() else "", GlanceModifier.defaultWeight(),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 11.sp,
                        fontWeight = if (day == date.dayOfMonth) FontWeight.Bold else FontWeight.Normal))
            }
        }
    }
}
