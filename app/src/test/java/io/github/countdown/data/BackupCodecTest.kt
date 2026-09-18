package io.github.countdown.data

import io.github.countdown.domain.CountdownEvent
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BackupCodecTest {
    private val event = EventEntity.from(CountdownEvent(title = "Поездка / Trip", date = LocalDate.of(2027, 1, 1)))
    private val payload = BackupPayload(1, "2026-09-16T00:00:00Z", listOf(event), emptyList())
    @Test fun roundTripPreservesUnicodeAndAllFields() {
        assertEquals(payload, BackupCodec.decode(BackupCodec.encode(payload).inputStream()))
    }
    @Test fun roundTripPreservesReminderRules() {
        val rule = ReminderEntity(eventId = event.id, kind = "CALENDAR_DAYS_BEFORE", daysBefore = 1, localTime = "18:30", createdAt = payload.exportedAt)
        val backup = payload.copy(reminders = listOf(rule))
        assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup).inputStream()))
    }
    @Test fun orphanReminderIsRejected() {
        val rule = ReminderEntity(eventId = java.util.UUID.randomUUID().toString(), kind = "CALENDAR_DAYS_BEFORE", createdAt = payload.exportedAt)
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.validate(payload.copy(reminders = listOf(rule))) }
    }
    @Test fun duplicateIdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.validate(payload.copy(events = listOf(event, event))) }
    }
    @Test fun missingCategoryIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.validate(payload.copy(events = listOf(event.copy(categoryId = "missing")))) }
    }
    @Test fun newerVersionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.validate(payload.copy(schemaVersion = 2)) }
    }
    @Test fun invalidUtf8IsRejected() {
        assertThrows(java.nio.charset.CharacterCodingException::class.java) { BackupCodec.decode(byteArrayOf(0xC3.toByte(), 0x28).inputStream()) }
    }
    @Test fun deeplyNestedUnknownDataIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(("[".repeat(33) + "]".repeat(33)).byteInputStream()) }
    }
    @Test fun oversizedFileIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(ByteArray(BackupCodec.MAX_BYTES + 1).inputStream()) }
    }
}
