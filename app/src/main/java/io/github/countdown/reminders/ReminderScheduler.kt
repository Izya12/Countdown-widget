package io.github.countdown.reminders

import android.app.*
import android.content.*
import android.net.Uri
import androidx.room.withTransaction
import androidx.work.*
import io.github.countdown.CountdownApp
import io.github.countdown.MainActivity
import io.github.countdown.R
import io.github.countdown.data.*
import io.github.countdown.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    const val CHANNEL = "event_reminders"
    private const val TAG_PREFIX = "countdown.reminder."
    private val mutex = Mutex()
    fun request(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork("reminder-reconcile", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>().build())
    }
    fun channel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reminders), NotificationManager.IMPORTANCE_DEFAULT))
    }
    fun allowed(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    private fun alarm(context: Context) = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java).setAction("io.github.countdown.REMINDER"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    suspend fun reconcile(app: CountdownApp) = mutex.withLock {
        channel(app)
        val alarmManager = app.getSystemService(AlarmManager::class.java)
        val manager = app.getSystemService(NotificationManager::class.java)
        val work = WorkManager.getInstance(app)
        val now = app.clock.instant()
        var next: Instant? = null
        var consumers = false
        app.database.withTransaction {
            val dao = app.database.dao()
            val permitted = allowed(app)
            val runtime = dao.reminderRuntime()
            val since = if (runtime == null || !runtime.allowed || !permitted) now else Instant.parse(runtime.enabledSince)
            dao.saveReminderRuntime(ReminderRuntime(allowed = permitted, enabledSince = since.toString()))
            val events = dao.events().associateBy { it.id }
            val rules = dao.reminders().filter { it.enabled }.groupBy { it.eventId }
            // Remove stale cards after edits/archive/delete or permission changes.
            manager.activeNotifications.filter { it.tag?.startsWith(TAG_PREFIX) == true }.forEach { active ->
                val event = events[active.tag.removePrefix(TAG_PREFIX)]
                if (!permitted || event == null || event.archived || CountdownEngine.calculate(event.domain(), now).status == EventStatus.ARCHIVED || event.scheduleVersion != active.notification.extras.getLong("scheduleVersion") || rules[event.id].isNullOrEmpty()) manager.cancel(active.tag, 1)
            }
            if (permitted) events.values.forEach { row ->
                val event = row.domain()
                if (event.archived || CountdownEngine.calculate(event, now).status == EventStatus.ARCHIVED) return@forEach
                val due = mutableListOf<Pair<ReminderEntity, ReminderOccurrence>>()
                rules[row.id].orEmpty().forEach { rule ->
                    val domain = rule.domain()
                    val from = maxOf(now.minusSeconds(7200), since, Instant.parse(rule.createdAt))
                    val candidate = ReminderPlanner.next(event, domain, from)
                    if (candidate != null && candidate.due <= now) {
                        val key = deliveryKey(row, rule, candidate)
                        if (dao.delivery(key) == null) due += rule to candidate
                    }
                    ReminderPlanner.next(event, domain, now.plusNanos(1))?.due?.let { future ->
                        consumers = true
                        next = next?.let { minOf(it, future) } ?: future
                    }
                }
                if (due.isNotEmpty() && allowed(app)) {
                    val occurrence = due.minBy { it.second.due }.second.occurrence
                    val intent = Intent(app, MainActivity::class.java).setData(Uri.parse("countdown://event/${row.id}"))
                        .putExtra("eventId", row.id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    val pending = PendingIntent.getActivity(app, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    val date = occurrence.start.atZone(event.zone).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(app.resources.configuration.locales[0]))
                    val notification = Notification.Builder(app, CHANNEL).setSmallIcon(R.drawable.ic_event)
                        .setContentTitle(event.title).setContentText(app.getString(R.string.reminder_notification, date))
                        .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true)
                        .addExtras(android.os.Bundle().apply { putLong("scheduleVersion", row.scheduleVersion) }).build()
                    // Stable event tag gives at-least-once retries one card, not duplicate cards.
                    manager.notify(TAG_PREFIX + row.id, 1, notification)
                    due.forEach { (rule, item) -> dao.saveDelivery(ReminderDelivery(deliveryKey(row, rule, item), row.id, rule.id, item.due.toString(), "DELIVERED")) }
                }
            }
        }
        alarmManager.cancel(alarm(app))
        next?.let { alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.toEpochMilli(), alarm(app)) }
        if (consumers) work.enqueueUniquePeriodicWork("reminder-recovery", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderRecoveryWorker>(24, TimeUnit.HOURS).build())
        else work.cancelUniqueWork("reminder-recovery")
    }
    internal fun deliveryKey(event: EventEntity, rule: ReminderEntity, item: ReminderOccurrence) = "${event.id}:${event.scheduleVersion}:${rule.id}:${item.occurrence.date}"
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        ReminderScheduler.reconcile(applicationContext as CountdownApp)
        Result.success()
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }
}
class ReminderRecoveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result { ReminderScheduler.request(applicationContext); return Result.success() }
}
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "io.github.countdown.REMINDER") return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { withTimeout(8_000) { ReminderScheduler.reconcile(context.applicationContext as CountdownApp) } }
            catch (_: Exception) { ReminderScheduler.request(context) }
            finally { pending.finish() }
        }
    }
}
