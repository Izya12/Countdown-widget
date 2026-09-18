package io.github.countdown.data

import androidx.room.*
import io.github.countdown.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.time.*

@Serializable
@Entity(tableName = "categories")
data class CategoryEntity(@PrimaryKey val id: String, val name: String = "", val seedKey: String? = null)

@Serializable
@Entity(tableName = "events", foreignKeys = [ForeignKey(entity = CategoryEntity::class,
    parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("categoryId"), Index(value = ["archived", "sortRank"])])
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    val date: String,
    val time: String = "09:00",
    val zone: String,
    val timeMode: String = "ALL_DAY",
    val fixedInstant: String? = null,
    val repeat: String = "NONE",
    val repeatDays: Int = 1,
    val categoryId: String? = null,
    val icon: String = "event",
    val color: Long = 0xFF245C4C,
    val format: String = "AUTO",
    val completion: String = "ELAPSED",
    val createdAt: String,
    val modifiedAt: String,
    val progressStart: String? = null,
    val archived: Boolean = false,
    val favorite: Boolean = false,
    val pinned: Boolean = false,
    val sortRank: Long = 0,
    @ColumnInfo(defaultValue = "0") val scheduleVersion: Long = 0,
) {
    fun domain() = CountdownEvent(id, title, description, LocalDate.parse(date), LocalTime.parse(time),
        ZoneId.of(zone), TimeMode.valueOf(timeMode), fixedInstant?.let(Instant::parse), Repeat.valueOf(repeat),
        repeatDays, categoryId, icon, color, CountdownFormat.valueOf(format), CompletionPolicy.valueOf(completion),
        Instant.parse(createdAt), Instant.parse(modifiedAt), progressStart?.let(Instant::parse), archived, favorite, pinned, sortRank)

    companion object {
        fun from(e: CountdownEvent) = EventEntity(e.id, e.title, e.description, e.date.toString(), e.time.toString(),
            e.zone.id, e.timeMode.name, e.fixedInstant?.toString(), e.repeat.name, e.repeatDays, e.categoryId, e.icon,
            e.color, e.format.name, e.completion.name, e.createdAt.toString(), e.modifiedAt.toString(),
            e.progressStart?.toString(), e.archived, e.favorite, e.pinned, e.sortRank)
    }
}

@Serializable
@Entity(tableName = "widget_configs", foreignKeys = [ForeignKey(entity = EventEntity::class,
    parentColumns = ["id"], childColumns = ["eventId"], onDelete = ForeignKey.SET_NULL)], indices = [Index("eventId")])
data class WidgetConfig(@PrimaryKey val appWidgetId: Int, val eventId: String?, val style: String = "STANDARD",
    val showDate: Boolean = true, val showProgress: Boolean = true)

@Entity(tableName = "pending_settings")
data class PendingSettings(@PrimaryKey val id: Int = 0, val theme: String, val token: String)

@Dao
interface CountdownDao {
    @Query("SELECT * FROM reminder_rules WHERE eventId = :eventId ORDER BY createdAt, id") fun observeReminders(eventId: String): Flow<List<ReminderEntity>>
    @Query("SELECT * FROM reminder_rules") suspend fun reminders(): List<ReminderEntity>
    @Upsert suspend fun saveReminder(rule: ReminderEntity)
    @Query("DELETE FROM reminder_rules WHERE id = :id") suspend fun deleteReminder(id: String)
    @Query("DELETE FROM reminder_rules WHERE eventId = :eventId AND kind = 'DURATION_BEFORE'") suspend fun deleteDurationReminders(eventId: String)
    @Query("UPDATE reminder_rules SET createdAt = :now WHERE eventId = :eventId") suspend fun resetReminderBaseline(eventId: String, now: String)
    @Query("SELECT * FROM reminder_deliveries WHERE deliveryKey = :key") suspend fun delivery(key: String): ReminderDelivery?
    @Upsert suspend fun saveDelivery(delivery: ReminderDelivery)
    @Query("SELECT * FROM reminder_runtime WHERE id = 0") suspend fun reminderRuntime(): ReminderRuntime?
    @Upsert suspend fun saveReminderRuntime(runtime: ReminderRuntime)
    @Upsert suspend fun stageSettings(value: PendingSettings)
    @Query("SELECT * FROM pending_settings WHERE id = 0") suspend fun pendingSettings(): PendingSettings?
    @Query("DELETE FROM pending_settings WHERE token = :token") suspend fun clearPendingSettings(token: String)
    @Query("SELECT * FROM events ORDER BY sortRank, id") fun observeEvents(): Flow<List<EventEntity>>
    @Query("SELECT * FROM categories ORDER BY seedKey DESC, name") fun observeCategories(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM events ORDER BY sortRank, id") suspend fun events(): List<EventEntity>
    @Query("SELECT * FROM events WHERE id = :id") suspend fun event(id: String): EventEntity?
    @Query("SELECT * FROM categories") suspend fun categories(): List<CategoryEntity>
    @Upsert suspend fun save(event: EventEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun seed(categories: List<CategoryEntity>)
    @Upsert suspend fun saveCategory(category: CategoryEntity)
    @Query("DELETE FROM categories WHERE id = :id AND seedKey IS NULL") suspend fun deleteCategory(id: String)
    @Query("DELETE FROM events WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM widget_configs WHERE appWidgetId = :id") suspend fun widget(id: Int): WidgetConfig?
    @Query("SELECT * FROM widget_configs") suspend fun widgets(): List<WidgetConfig>
    @Upsert suspend fun saveWidget(config: WidgetConfig)
    @Query("DELETE FROM widget_configs WHERE appWidgetId = :id") suspend fun deleteWidget(id: Int)
}

@Database(entities = [EventEntity::class, CategoryEntity::class, WidgetConfig::class, PendingSettings::class, ReminderEntity::class, ReminderDelivery::class, ReminderRuntime::class], version = 3, exportSchema = true)
abstract class CountdownDatabase : RoomDatabase() {
    abstract fun dao(): CountdownDao
    companion object {
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN scheduleVersion INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS reminder_rules (id TEXT NOT NULL, eventId TEXT NOT NULL, kind TEXT NOT NULL, offsetSeconds INTEGER NOT NULL, daysBefore INTEGER NOT NULL, localTime TEXT NOT NULL, enabled INTEGER NOT NULL, createdAt TEXT NOT NULL, PRIMARY KEY(id), FOREIGN KEY(eventId) REFERENCES events(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reminder_rules_eventId ON reminder_rules(eventId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS reminder_deliveries (deliveryKey TEXT NOT NULL, eventId TEXT NOT NULL, ruleId TEXT NOT NULL, dueAt TEXT NOT NULL, state TEXT NOT NULL, PRIMARY KEY(deliveryKey), FOREIGN KEY(eventId) REFERENCES events(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(ruleId) REFERENCES reminder_rules(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reminder_deliveries_eventId ON reminder_deliveries(eventId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reminder_deliveries_ruleId ON reminder_deliveries(ruleId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS reminder_runtime (id INTEGER NOT NULL, allowed INTEGER NOT NULL, enabledSince TEXT NOT NULL, PRIMARY KEY(id))")
            }
        }
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS pending_settings (id INTEGER NOT NULL, theme TEXT NOT NULL, token TEXT NOT NULL, PRIMARY KEY(id))")
            }
        }
    }
}
