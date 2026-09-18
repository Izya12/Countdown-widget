package io.github.countdown.data

import io.github.countdown.domain.CountdownEvent
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import androidx.room.withTransaction

class EventRepository(private val db: CountdownDatabase, private val clock: Clock, private val onChanged: () -> Unit = {}) {
    val dao get() = db.dao()
    val events = dao.observeEvents().map { rows -> rows.map { it.domain() } }
    val categories = dao.observeCategories()
    suspend fun save(event: CountdownEvent) {
        val clean = event.copy(title = event.title.trim(), modifiedAt = clock.instant())
        clean.validate()
        db.withTransaction {
            val old = dao.event(clean.id)
            val row = EventEntity.from(clean)
            val changed = old != null && (old.date != row.date || old.time != row.time || old.zone != row.zone || old.timeMode != row.timeMode || old.fixedInstant != row.fixedInstant || old.repeat != row.repeat || old.repeatDays != row.repeatDays || old.archived != row.archived || old.completion != row.completion)
            dao.save(row.copy(scheduleVersion = (old?.scheduleVersion ?: 0) + if (changed) 1 else 0))
            if (changed) dao.resetReminderBaseline(clean.id, clock.instant().toString())
            if (clean.timeMode == io.github.countdown.domain.TimeMode.ALL_DAY) dao.deleteDurationReminders(clean.id)
        }
        onChanged()
    }
    suspend fun seed() = dao.seed(listOf("birthday", "holiday", "travel", "work", "personal", "anniversary", "other").map {
        CategoryEntity(UUID.nameUUIDFromBytes("countdown.category.$it".toByteArray()).toString(), seedKey = it)
    })
}
