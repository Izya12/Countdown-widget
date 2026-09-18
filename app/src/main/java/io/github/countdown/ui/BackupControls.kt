package io.github.countdown.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.countdown.R

@Composable fun BackupControls(vm: EventsViewModel) {
    val state by vm.backupState.collectAsStateWithLifecycle()
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::exportBackup) }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::prepareImport) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.backup_hint), style = MaterialTheme.typography.bodyMedium)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.message?.let { Text(stringResource(it), color = if (state.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
        OutlinedButton(enabled = !state.busy, onClick = { exportFile.launch("countdown-backup.json") }) { Text(stringResource(R.string.export)) }
        OutlinedButton(enabled = !state.busy, onClick = { importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text(stringResource(R.string.import_backup)) }
    }
    state.preview?.let { preview ->
        AlertDialog(onDismissRequest = { if (!state.busy) vm.cancelImport() }, title = { Text(stringResource(R.string.import_title)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.import_summary, preview.added, preview.identical, preview.conflicts))
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.failed) Text(stringResource(state.message ?: R.string.error), color = MaterialTheme.colorScheme.error)
            } },
            confirmButton = { TextButton(enabled = !state.busy, onClick = vm::applyImport) { Text(stringResource(R.string.import_backup)) } },
            dismissButton = { TextButton(enabled = !state.busy, onClick = vm::cancelImport) { Text(stringResource(R.string.cancel)) } })
    }
}
