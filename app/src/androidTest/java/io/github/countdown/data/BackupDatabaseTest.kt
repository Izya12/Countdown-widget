package io.github.countdown.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.countdown.domain.CountdownEvent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BackupDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, CountdownDatabase::class.java).build()
    private val settings = Settings(context)
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), CountdownDatabase::class.java)
    @After fun close() = db.close()
    @Test fun importIsIdempotentAndKeepsExistingConflict() = runBlocking {
        val originalTheme = settings.theme.first()
        try {
            val existing = EventEntity.from(CountdownEvent(title = "Local", date = LocalDate.of(2027, 1, 1)))
            val added = EventEntity.from(CountdownEvent(title = "Imported", date = LocalDate.of(2028, 1, 1)))
            db.dao().save(existing)
            val exportedAt = Clock.systemUTC().instant().toString()
            val incomingRule = ReminderEntity(eventId = added.id, kind = "CALENDAR_DAYS_BEFORE", daysBefore = 1, createdAt = exportedAt)
            val ignoredRule = ReminderEntity(eventId = existing.id, kind = "CALENDAR_DAYS_BEFORE", createdAt = exportedAt)
            val payload = BackupPayload(1, exportedAt, listOf(existing.copy(title = "Conflict"), added), emptyList(), BackupSettings("DARK"), listOf(incomingRule, ignoredRule))
            val repository = BackupRepository(db, settings, Clock.systemUTC())
            assertEquals(1, repository.preview(payload).conflicts)
            repository.apply(payload)
            repository.apply(payload)
            assertEquals(2, db.dao().events().size)
            assertEquals(listOf(incomingRule.id), db.dao().reminders().map { it.id })
            assertEquals("Local", db.dao().event(existing.id)!!.title)
            assertEquals(ThemeMode.DARK, settings.theme.first())
            assertNull(db.dao().pendingSettings())
            assertEquals(2, BackupCodec.decode(repository.export().inputStream()).events.size)
        } finally { settings.theme(originalTheme) }
    }
    @Test fun invalidImportDoesNotWriteAnyRows() = runBlocking {
        val valid = EventEntity.from(CountdownEvent(title = "Valid", date = LocalDate.of(2027, 1, 1)))
        val payload = BackupPayload(1, Clock.systemUTC().instant().toString(), listOf(valid, valid.copy(id = "bad")), emptyList())
        try { BackupRepository(db, settings, Clock.systemUTC()).apply(payload); fail("Invalid import accepted") }
        catch (_: IllegalArgumentException) { assertTrue(db.dao().events().isEmpty()) }
    }
    @Test fun migrationPreservesCategoriesAndCreatesSettingsStage() {
        val name = "migration-backup-test"
        migration.createDatabase(name, 1).apply {
            execSQL("INSERT INTO categories (id, name, seedKey) VALUES ('fixture', 'Saved', NULL)")
            close()
        }
        migration.runMigrationsAndValidate(name, 2, true, CountdownDatabase.MIGRATION_1_2).apply {
            query("SELECT name FROM categories WHERE id = 'fixture'").use { assertTrue(it.moveToFirst()); assertEquals("Saved", it.getString(0)) }
            execSQL("INSERT INTO pending_settings (id, theme, token) VALUES (0, 'DARK', 'test')")
            close()
        }
    }
}
