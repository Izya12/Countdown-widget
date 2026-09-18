package io.github.countdown.data

import androidx.room.*
import io.github.countdown.domain.*
import kotlinx.serialization.Serializable
import java.time.Clock
import java.time.LocalTime
import java.util.UUID

@Serializable
@Entity(tableName = "reminder_rules", foreignKeys = [ForeignKey(entity = EventEntity::class, parentColumns = ["id"], childColumns = ["eventId"], onDelete = ForeignKey.CASCADE)], indices = [Index("eventId")])
data class ReminderEntity(@PrimaryKey val id: String = UUID.randomUUID().toString(), val eventId: String,
    val kind: String, val offsetSeconds: Long = 0, val daysBefore: Int = 0, val localTime: String = "09:00",
    val enabled: Boolean = true, val createdAt: String) {
    fun domain() = ReminderRule(id, eventId, ReminderKind.valueOf(kind), offsetSeconds, daysBefore, LocalTime.parse(localTime), enabled)
    fun sameRule(other: ReminderEntity) = eventId == other.eventId && kind == other.kind && offsetSeconds == other.offsetSeconds && daysBefore == other.daysBefore && (kind == "DURATION_BEFORE" || localTime == other.localTime)
}

@Entity(tableName = "reminder_deliveries", foreignKeys = [
    ForeignKey(entity = EventEntity::class, parentColumns = ["id"], childColumns = ["eventId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = ReminderEntity::class, parentColumns = ["id"], childColumns = ["ruleId"], onDelete = ForeignKey.CASCADE)], indices = [Index("eventId"), Index("ruleId")])
data class ReminderDelivery(@PrimaryKey val deliveryKey: String, val eventId: String, val ruleId: String, val dueAt: String, val state: String)

@Entity(tableName = "reminder_runtime")
data class ReminderRuntime(@PrimaryKey val id: Int = 0, val allowed: Boolean, val enabledSince: String)

class ReminderRepository(private val db: CountdownDatabase, private val clock: Clock, private val onChanged: () -> Unit) {
    suspend fun add(rule: ReminderEntity) {
        db.withTransaction {
            val event = requireNotNull(db.dao().event(rule.eventId))
            rule.domain().validate(event.domain())
            val current = db.dao().reminders().filter { it.eventId == rule.eventId }
            if (current.any { it.sameRule(if (event.timeMode == "ALL_DAY") rule else rule.copy(localTime = it.localTime)) }) return@withTransaction
            require(current.size < 16)
            db.dao().saveReminder(rule.copy(createdAt = clock.instant().toString()))
            db.dao().save(event.copy(scheduleVersion = event.scheduleVersion + 1))
            db.dao().resetReminderBaseline(event.id, clock.instant().toString())
        }
        onChanged()
    }
    suspend fun remove(rule: ReminderEntity) {
        db.withTransaction {
            val event = db.dao().event(rule.eventId) ?: return@withTransaction
            db.dao().deleteReminder(rule.id)
            db.dao().save(event.copy(scheduleVersion = event.scheduleVersion + 1))
            db.dao().resetReminderBaseline(event.id, clock.instant().toString())
        }
        onChanged()
    }
}
