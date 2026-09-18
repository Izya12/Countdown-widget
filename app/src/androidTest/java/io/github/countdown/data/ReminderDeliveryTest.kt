package io.github.countdown.data

import android.app.NotificationManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import io.github.countdown.CountdownApp
import io.github.countdown.domain.*
import io.github.countdown.reminders.ReminderScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*

@RunWith(AndroidJUnit4::class)
class ReminderDeliveryTest {
    @Test fun remindersOlderThanCatchUpWindowAreNotPublished() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<CountdownApp>()
        if (Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(app.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        val now = app.clock.instant()
        val target = now.minusSeconds(3 * 3600).atZone(ZoneOffset.UTC)
        val event = CountdownEvent(title = "Old notification fixture", date = target.toLocalDate(), time = target.toLocalTime(), zone = ZoneOffset.UTC, timeMode = TimeMode.ZONED_LOCAL)
        val dao = app.database.dao()
        val previous = dao.reminderRuntime()
        try {
            dao.save(EventEntity.from(event))
            dao.saveReminder(ReminderEntity(eventId = event.id, kind = "DURATION_BEFORE", createdAt = now.minusSeconds(86400).toString()))
            dao.saveReminderRuntime(ReminderRuntime(allowed = true, enabledSince = now.minusSeconds(86400).toString()))
            ReminderScheduler.reconcile(app)
            assertFalse(app.getSystemService(NotificationManager::class.java).activeNotifications.any { it.tag == "countdown.reminder.${event.id}" })
        } finally {
            dao.delete(event.id)
            if (previous != null) dao.saveReminderRuntime(previous)
        }
    }
    @Test fun dueReminderPublishesOnceAndDeletionCancelsCard() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<CountdownApp>()
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(app.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        }
        WorkManager.getInstance(app).cancelUniqueWork("reminder-reconcile").result.get()
        ReminderScheduler.channel(app)
        assertTrue(ReminderScheduler.allowed(app))
        val now = app.clock.instant()
        val target = now.minusSeconds(60).atZone(ZoneOffset.UTC)
        val event = CountdownEvent(title = "Notification fixture", date = target.toLocalDate(), time = target.toLocalTime(), zone = ZoneOffset.UTC, timeMode = TimeMode.ZONED_LOCAL)
        val row = EventEntity.from(event)
        val rule = ReminderEntity(eventId = event.id, kind = "DURATION_BEFORE", createdAt = now.minusSeconds(600).toString())
        val dao = app.database.dao()
        val previous = dao.reminderRuntime()
        val notifications = app.getSystemService(NotificationManager::class.java)
        val tag = "countdown.reminder.${event.id}"
        try {
            dao.save(row)
            dao.saveReminder(rule)
            dao.saveReminderRuntime(ReminderRuntime(allowed = true, enabledSince = now.minusSeconds(600).toString()))
            ReminderScheduler.reconcile(app)
            assertEquals(1, notifications.activeNotifications.count { it.tag == tag })
            val key = ReminderScheduler.deliveryKey(row, rule, ReminderPlanner.next(event, rule.domain(), now.minusSeconds(600))!!)
            assertNotNull(dao.delivery(key))
            ReminderScheduler.reconcile(app)
            assertEquals(1, notifications.activeNotifications.count { it.tag == tag })
            dao.delete(event.id)
            ReminderScheduler.reconcile(app)
            assertFalse(notifications.activeNotifications.any { it.tag == tag })
        } finally {
            notifications.cancel(tag, 1)
            dao.delete(event.id)
            if (previous != null) dao.saveReminderRuntime(previous)
        }
    }
}
