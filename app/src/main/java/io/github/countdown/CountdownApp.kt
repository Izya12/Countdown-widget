package io.github.countdown

import android.app.Application
import androidx.room.Room
import io.github.countdown.data.*
import java.time.Clock

class CountdownApp : Application() {
    val clock: Clock = Clock.systemUTC()
    val database by lazy { Room.databaseBuilder(this, CountdownDatabase::class.java, "countdown.db").addMigrations(CountdownDatabase.MIGRATION_1_2, CountdownDatabase.MIGRATION_2_3).build() }
    val repository by lazy { EventRepository(database, clock) { io.github.countdown.widget.WidgetUpdates.request(this) } }
    val reminders by lazy { ReminderRepository(database, clock) { io.github.countdown.widget.WidgetUpdates.request(this) } }
    val settings by lazy { Settings(this) }
    val backup by lazy { BackupRepository(database, settings, clock) }
    override fun onCreate() { super.onCreate(); io.github.countdown.widget.WidgetUpdates.request(this) }
}
