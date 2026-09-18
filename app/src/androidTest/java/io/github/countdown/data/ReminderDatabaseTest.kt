package io.github.countdown.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.countdown.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*

@RunWith(AndroidJUnit4::class)
class ReminderDatabaseTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CountdownDatabase::class.java).build()
    private val clock = Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC)
    private val repository = EventRepository(db, clock)
    private val reminders = ReminderRepository(db, clock) {}
    private val event = CountdownEvent(title = "Fixture", date = LocalDate.of(2027, 1, 1), timeMode = TimeMode.ZONED_LOCAL)
    private fun rule() = ReminderEntity(eventId = event.id, kind = "DURATION_BEFORE", offsetSeconds = 900, createdAt = clock.instant().toString())
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), CountdownDatabase::class.java)
    @After fun close() = db.close()
    @Test fun visualEditKeepsScheduleButDateEditInvalidatesIt() = runBlocking {
        repository.save(event)
        reminders.add(rule())
        val version = db.dao().event(event.id)!!.scheduleVersion
        repository.save(event.copy(title = "New title", favorite = true))
        assertEquals(version, db.dao().event(event.id)!!.scheduleVersion)
        repository.save(event.copy(date = event.date.plusDays(1)))
        assertEquals(version + 1, db.dao().event(event.id)!!.scheduleVersion)
    }
    @Test fun repeatedRuleIsDeduplicatedAndDeleteCascades() = runBlocking {
        repository.save(event)
        val rule = rule()
        reminders.add(rule)
        reminders.add(rule.copy(id = java.util.UUID.randomUUID().toString()))
        assertEquals(1, db.dao().reminders().size)
        db.dao().saveDelivery(ReminderDelivery("fixture", event.id, rule.id, clock.instant().toString(), "DELIVERED"))
        db.dao().delete(event.id)
        assertTrue(db.dao().reminders().isEmpty())
        assertNull(db.dao().delivery("fixture"))
    }
    @Test fun changingToAllDayRemovesIncompatibleDurationRules() = runBlocking {
        repository.save(event)
        reminders.add(rule())
        repository.save(event.copy(timeMode = TimeMode.ALL_DAY))
        assertTrue(db.dao().reminders().isEmpty())
    }
    @Test fun migrationFrom2PreservesEventAndAddsVersion() {
        val name = "reminder-migration-test"
        migration.createDatabase(name, 2).apply {
            execSQL("INSERT INTO categories (id, name, seedKey) VALUES ('fixture', 'Keep', NULL)")
            close()
        }
        migration.runMigrationsAndValidate(name, 3, true, CountdownDatabase.MIGRATION_2_3).apply {
            query("SELECT name FROM categories WHERE id='fixture'").use { assertTrue(it.moveToFirst()); assertEquals("Keep", it.getString(0)) }
            query("SELECT scheduleVersion FROM events").close()
            query("SELECT * FROM reminder_rules").close()
            close()
        }
    }
}
