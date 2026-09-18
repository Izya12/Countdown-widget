package io.github.countdown.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.countdown.domain.CountdownEvent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CountdownDatabase::class.java).build()
    @After fun close() = db.close()
    @Test fun deletingEventKeepsWidgetConfigurationButClearsReference() = runBlocking {
        val event = EventEntity.from(CountdownEvent(title = "Trip", date = LocalDate.of(2027, 1, 1)))
        db.dao().save(event)
        db.dao().saveWidget(WidgetConfig(42, event.id, "PROGRESS"))
        db.dao().delete(event.id)
        assertNull(db.dao().widget(42)!!.eventId)
        assertEquals("PROGRESS", db.dao().widget(42)!!.style)
    }
    @Test fun deletingCustomCategoryDoesNotDeleteEvent() = runBlocking {
        val category = CategoryEntity(UUID.randomUUID().toString(), "Custom")
        db.dao().saveCategory(category)
        val event = EventEntity.from(CountdownEvent(title = "Trip", date = LocalDate.of(2027,1,1), categoryId = category.id))
        db.dao().save(event)
        db.dao().deleteCategory(category.id)
        assertNotNull(db.dao().event(event.id))
        assertNull(db.dao().event(event.id)!!.categoryId)
    }
    @Test fun upsertDoesNotTriggerDeleteCascade() = runBlocking {
        val event = EventEntity.from(CountdownEvent(title = "Trip", date = LocalDate.of(2027,1,1)))
        db.dao().save(event)
        db.dao().saveWidget(WidgetConfig(42,event.id))
        db.dao().save(event.copy(title = "New name"))
        assertEquals(event.id, db.dao().widget(42)!!.eventId)
        assertEquals("New name", db.dao().event(event.id)!!.title)
    }
}
