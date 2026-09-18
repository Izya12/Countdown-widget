package io.github.countdown.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Serializable data class BackupSettings(val theme: String = "SYSTEM")
@Serializable data class BackupPayload(
    val schemaVersion: Int,
    val exportedAt: String,
    val events: List<EventEntity>,
    val categories: List<CategoryEntity>,
    val settings: BackupSettings = BackupSettings(),
    val reminders: List<ReminderEntity> = emptyList(),
)
data class BackupPreview(val payload: BackupPayload, val added: Int, val identical: Int, val conflicts: Int)

object BackupCodec {
    const val MAX_BYTES = 10 * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val seeds = setOf("birthday", "holiday", "travel", "work", "personal", "anniversary", "other")
    fun seedId(key: String) = UUID.nameUUIDFromBytes("countdown.category.$key".toByteArray(Charsets.UTF_8)).toString()

    fun decode(input: InputStream): BackupPayload {
        val bytes = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (true) {
            val count = input.read(chunk)
            if (count < 0) break
            require(bytes.size() + count <= MAX_BYTES) { "Backup exceeds size limit" }
            bytes.write(chunk, 0, count)
        }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
        checkNesting(text)
        return json.decodeFromString<BackupPayload>(text).also(::validate)
    }
    fun encode(payload: BackupPayload): ByteArray {
        validate(payload)
        return json.encodeToString(payload).toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) }
    }
    fun validate(payload: BackupPayload) {
        require(payload.schemaVersion == 1) { "Unsupported schema" }
        Instant.parse(payload.exportedAt)
        require(payload.events.size <= 10_000 && payload.categories.size <= 1_000)
        require(payload.reminders.size <= 100_000)
        require(payload.reminders.map { it.id }.toSet().size == payload.reminders.size)
        require(payload.reminders.groupingBy { it.eventId }.eachCount().values.all { it <= 16 })
        val eventsById = payload.events.associateBy { it.id }
        payload.reminders.forEach { rule ->
            Instant.parse(rule.createdAt)
            rule.domain().validate(requireNotNull(eventsById[rule.eventId]).domain())
        }
        require(payload.reminders.map { rule ->
            listOf(rule.eventId, rule.kind, rule.offsetSeconds.toString(), rule.daysBefore.toString(),
                if (eventsById[rule.eventId]?.timeMode == "ALL_DAY") rule.localTime else "")
        }.toSet().size == payload.reminders.size) { "Duplicate reminder rules" }
        ThemeMode.valueOf(payload.settings.theme)
        require(payload.events.map { it.id }.toSet().size == payload.events.size)
        require(payload.categories.map { it.id }.toSet().size == payload.categories.size)
        val categoryIds = payload.categories.map { it.id }.toSet()
        payload.categories.forEach {
            require(UUID.fromString(it.id).toString() == it.id)
            require(it.name.length <= 60)
            if (it.seedKey != null) require(it.seedKey in seeds && it.id == seedId(it.seedKey))
            else require(it.name.isNotBlank())
        }
        payload.events.forEach {
            require(UUID.fromString(it.id).toString() == it.id)
            require(it.categoryId == null || it.categoryId in categoryIds)
            require(it.icon.length <= 60)
            val event = it.domain()
            event.validate()
            event.fixedInstant?.let { instant -> require(instant.atZone(event.zone).year in 1900..9999) }
        }
    }
    private fun checkNesting(text: String) {
        var quoted = false
        var escaped = false
        var depth = 0
        text.forEach { char ->
            if (quoted) {
                if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 32) { "JSON nesting exceeds limit" } }
                '}', ']' -> { depth--; require(depth >= 0) }
            }
        }
        require(depth == 0 && !quoted)
    }
}

class BackupRepository(private val db: CountdownDatabase, private val settings: Settings, private val clock: Clock) {
    private val settingsMutex = Mutex()
    suspend fun export(): ByteArray {
        val theme = settings.theme.first()
        val snapshot = db.withTransaction {
            BackupPayload(1, clock.instant().toString(), db.dao().events().map { it.copy(scheduleVersion = 0) }, db.dao().categories(), BackupSettings(theme.name), db.dao().reminders())
        }
        return BackupCodec.encode(snapshot)
    }
    suspend fun preview(payload: BackupPayload): BackupPreview {
        BackupCodec.validate(payload)
        val existing = db.dao().events().associateBy { it.id }
        val localRules = db.dao().reminders().groupBy { it.eventId }
        val importedRules = payload.reminders.groupBy { it.eventId }
        val added = payload.events.count { it.id !in existing }
        val same = payload.events.count {
            existing[it.id]?.copy(scheduleVersion = 0) == it.copy(scheduleVersion = 0) &&
                localRules[it.id].orEmpty().map { rule -> rule.copy(createdAt = "") }.toSet() == importedRules[it.id].orEmpty().map { rule -> rule.copy(createdAt = "") }.toSet()
        }
        return BackupPreview(payload, added, same, payload.events.size - added - same)
    }
    suspend fun apply(payload: BackupPayload) {
        BackupCodec.validate(payload)
        db.withTransaction {
            // Re-read inside the transaction: the database can change while preview is open.
            val eventIds = db.dao().events().map { it.id }.toSet()
            val categoryIds = db.dao().categories().map { it.id }.toSet()
            payload.categories.filter { it.id !in categoryIds }.forEach { db.dao().saveCategory(it) }
            payload.events.filter { it.id !in eventIds }.forEach { db.dao().save(it.copy(scheduleVersion = 0)) }
            // Rules belong to the event merge unit: keep existing event and all its local rules.
            val existingRuleIds = db.dao().reminders().map { it.id }.toSet()
            payload.reminders.filter { it.eventId !in eventIds }.forEach {
                require(it.id !in existingRuleIds) { "Reminder ID conflicts with a different local event" }
                db.dao().saveReminder(it.copy(createdAt = clock.instant().toString()))
            }
            db.dao().stageSettings(PendingSettings(theme = payload.settings.theme, token = UUID.randomUUID().toString()))
        }
        resumeSettings()
    }
    suspend fun resumeSettings() = settingsMutex.withLock {
        val pending = db.dao().pendingSettings() ?: return@withLock
        settings.theme(ThemeMode.valueOf(pending.theme))
        db.dao().clearPendingSettings(pending.token)
    }
}
