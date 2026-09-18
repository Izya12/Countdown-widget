package io.github.countdown.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.countdown.CountdownApp
import io.github.countdown.domain.CountdownEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import androidx.room.withTransaction
import io.github.countdown.data.CategoryEntity
import io.github.countdown.data.BackupPreview
import io.github.countdown.data.BackupCodec
import io.github.countdown.R
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BackupUiState(val busy: Boolean = false, val preview: BackupPreview? = null, val message: Int? = null, val failed: Boolean = false)

class EventsViewModel(val app: CountdownApp) : ViewModel() {
    val error = MutableStateFlow(false)
    val ready = MutableStateFlow(false)
    val backupState = MutableStateFlow(BackupUiState())
    val events = app.repository.events.onEach { ready.value = true }
        .catch { error.value = true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = app.repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val theme = app.settings.theme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), io.github.countdown.data.ThemeMode.SYSTEM)
    init { action { app.repository.seed() } }
    fun action(block: suspend () -> Unit) { viewModelScope.launch { try { block(); io.github.countdown.widget.WidgetUpdates.request(app) } catch (e: CancellationException) { throw e } catch (_: Exception) { error.value = true } } }
    fun update(e: CountdownEvent) = action { app.repository.save(e) }
    fun delete(e: CountdownEvent) = action { app.repository.dao.delete(e.id) }
    fun cancelImport() { backupState.value = BackupUiState() }
    fun prepareImport(uri: Uri) {
        if (backupState.value.busy) return
        backupState.value = BackupUiState(busy = true)
        viewModelScope.launch {
            try {
                val preview = withContext(Dispatchers.IO) {
                    val payload = requireNotNull(app.contentResolver.openInputStream(uri)).use(BackupCodec::decode)
                    app.backup.preview(payload)
                }
                backupState.value = BackupUiState(preview = preview)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { backupState.value = BackupUiState(message = R.string.backup_invalid, failed = true) }
        }
    }
    fun applyImport() {
        val preview = backupState.value.preview ?: return
        if (backupState.value.busy) return
        backupState.value = backupState.value.copy(busy = true)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { app.backup.apply(preview.payload) }
                io.github.countdown.widget.WidgetUpdates.request(app)
                backupState.value = BackupUiState(message = R.string.import_done)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { backupState.value = BackupUiState(preview = preview, message = R.string.error, failed = true) }
        }
    }
    fun exportBackup(uri: Uri) {
        if (backupState.value.busy) return
        backupState.value = BackupUiState(busy = true)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val bytes = app.backup.export()
                    requireNotNull(app.contentResolver.openOutputStream(uri, "wt")).use { it.write(bytes); it.flush() }
                }
                backupState.value = BackupUiState(message = R.string.export_done)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { backupState.value = BackupUiState(message = R.string.error, failed = true) }
        }
    }
    fun addCategory(name: String, onSaved: () -> Unit) = action {
        if (name.isNotBlank() && name.trim().length <= 60) {
            app.repository.dao.saveCategory(CategoryEntity(UUID.randomUUID().toString(), name.trim()))
            onSaved()
        }
    }
    fun move(id: String, offset: Int) = action {
        app.database.withTransaction {
            val rows = app.repository.dao.events().toMutableList()
            val index = rows.indexOfFirst { it.id == id }
            val target = index + offset
            if (index >= 0 && target in rows.indices) {
                val row = rows.removeAt(index)
                rows.add(target, row)
                rows.forEachIndexed { rank, item -> app.repository.dao.save(item.copy(sortRank = rank.toLong())) }
            }
        }
    }
}
