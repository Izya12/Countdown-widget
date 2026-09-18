package io.github.countdown.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.*
import androidx.room.withTransaction
import io.github.countdown.CountdownApp
import io.github.countdown.domain.CountdownEngine
import io.github.countdown.domain.CountdownFormat
import java.time.Duration
import java.util.concurrent.TimeUnit

object WidgetUpdates {
    fun request(context: Context) {
        io.github.countdown.reminders.ReminderScheduler.request(context)
        WorkManager.getInstance(context).enqueueUniqueWork("widget-refresh", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build())
    }
    fun restore(context: Context, old: IntArray, new: IntArray) {
        if (old.size != new.size || old.isEmpty()) return
        WorkManager.getInstance(context).enqueueUniqueWork("widget-restore", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<WidgetRestoreWorker>().setInputData(workDataOf("old" to old, "new" to new)).build())
    }
}

class WidgetRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val app = context as CountdownApp
        app.backup.resumeSettings()
        val manager = GlanceAppWidgetManager(context)
        val standardIds = manager.getGlanceIds(CountdownWidget::class.java)
        val circleIds = manager.getGlanceIds(CircleCountdownWidget::class.java)
        val ids = standardIds + circleIds
        val work = WorkManager.getInstance(context)
        val actualIds = ids.map(manager::getAppWidgetId).toSet()
        val dao = app.database.dao()
        // Do not prune old IDs here: Application startup can precede onRestored.
        // onDelete owns removal; dormant configs never participate in scheduling.
        if (ids.isEmpty()) {
            work.cancelUniqueWork("widget-boundary")
            work.cancelUniqueWork("widget-recovery")
            return Result.success()
        }
        try {
            val widget = CountdownWidget()
            standardIds.forEach { widget.update(context, it) }
            circleIds.forEach { CircleCountdownWidget().update(context, it) }
            val now = app.clock.instant()
            val eventIds = dao.widgets().filter { it.appWidgetId in actualIds }.mapNotNull { it.eventId }.toSet()
            val next = dao.events().filter { it.id in eventIds }.mapNotNull {
                // Widgets display calendar-day precision, including detailed-format events.
                CountdownEngine.calculate(it.domain().copy(format = CountdownFormat.DAYS), now).nextChange
            }.minOrNull()
            if (next != null) {
                work.enqueueUniqueWork("widget-boundary", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<WidgetBoundaryWorker>()
                    .setInitialDelay(Duration.between(now, next).toMillis().coerceAtLeast(TimeUnit.MINUTES.toMillis(15)), TimeUnit.MILLISECONDS).build())
            } else work.cancelUniqueWork("widget-boundary")
            work.enqueueUniquePeriodicWork("widget-recovery", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetBoundaryWorker>(24, TimeUnit.HOURS).build())
            return Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { return if (runAttemptCount < 3) Result.retry() else Result.failure() }
    }
}

/** Boundary work only dispatches; refresh never REPLACEs its own running work. */
class WidgetBoundaryWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result { WidgetUpdates.request(applicationContext); return Result.success() }
}

class WidgetRestoreWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val old = inputData.getIntArray("old") ?: return Result.failure()
        val new = inputData.getIntArray("new") ?: return Result.failure()
        if (old.size != new.size) return Result.failure()
        val db = (applicationContext as CountdownApp).database
        db.withTransaction {
            val configs = old.map { db.dao().widget(it) }
            val existing = new.map { db.dao().widget(it) }
            old.forEach { db.dao().deleteWidget(it) }
            new.forEachIndexed { index, id ->
                db.dao().saveWidget(configs[index]?.copy(appWidgetId = id) ?: existing[index] ?: io.github.countdown.data.WidgetConfig(id, null))
            }
        }
        val manager = android.appwidget.AppWidgetManager.getInstance(applicationContext)
        if (android.os.Build.VERSION.SDK_INT >= 30) new.forEach { id -> manager.updateAppWidgetOptions(id, android.os.Bundle().apply { putBoolean(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED, true) }) }
        WidgetUpdates.request(applicationContext)
        return Result.success()
    }
}

class SystemChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED)) {
            WidgetUpdates.request(context)
        }
    }
}
