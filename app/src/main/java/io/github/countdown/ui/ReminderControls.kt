package io.github.countdown.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.countdown.R
import io.github.countdown.data.ReminderEntity
import io.github.countdown.domain.*
import io.github.countdown.reminders.ReminderScheduler
import java.time.LocalTime

@Composable fun ReminderControls(vm: EventsViewModel, event: CountdownEvent) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val rules by remember(event.id) { vm.app.database.dao().observeReminders(event.id) }.collectAsStateWithLifecycle(emptyList())
    var permitted by remember { mutableStateOf(ReminderScheduler.allowed(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permitted = ReminderScheduler.allowed(context)
        ReminderScheduler.request(context)
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, action -> if (action == Lifecycle.Event.ON_RESUME) permitted = ReminderScheduler.allowed(context) }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    var adding by rememberSaveable { mutableStateOf(false) }
    var preset by rememberSaveable { mutableIntStateOf(0) }
    var time by rememberSaveable { mutableStateOf("09:00") }
    var invalid by remember { mutableStateOf(false) }
    val allDay = event.timeMode == TimeMode.ALL_DAY
    val eventTime = event.fixedInstant?.atZone(event.zone)?.toLocalTime() ?: event.time
    val labels = if (allDay) listOf(R.string.reminder_today, R.string.reminder_day, R.string.reminder_week)
        else listOf(R.string.reminder_at_start, R.string.reminder_15min, R.string.reminder_hour, R.string.reminder_day, R.string.reminder_week)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.reminders), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.reminder_hint), style = MaterialTheme.typography.bodyMedium)
        if (!permitted && rules.isNotEmpty()) {
            Text(stringResource(R.string.reminder_disabled), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text(stringResource(R.string.settings)) }
        }
        rules.forEach { rule ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val label = when {
                    rule.kind == "CALENDAR_DAYS_BEFORE" && rule.daysBefore == 0 -> stringResource(R.string.reminder_on_date_time, if (allDay) rule.localTime else eventTime.toString())
                    rule.kind == "CALENDAR_DAYS_BEFORE" -> androidx.compose.ui.res.pluralStringResource(R.plurals.reminder_calendar_label, rule.daysBefore, rule.daysBefore, if (allDay) rule.localTime else eventTime.toString())
                    rule.offsetSeconds == 0L -> stringResource(R.string.reminder_at_start)
                    else -> androidx.compose.ui.res.pluralStringResource(R.plurals.reminder_minutes_label, (rule.offsetSeconds / 60).toInt(), rule.offsetSeconds / 60)
                }
                Text(label, Modifier.weight(1f).padding(vertical = 12.dp))
                TextButton(onClick = { vm.action { vm.app.reminders.remove(rule) } }) { Text(stringResource(R.string.delete)) }
            }
        }
        OutlinedButton(enabled = rules.size < 16 && !event.archived, onClick = { preset = 0; invalid = false; adding = true }) { Text(stringResource(R.string.add_reminder)) }
    }
    if (adding) AlertDialog(onDismissRequest = { adding = false }, title = { Text(stringResource(R.string.add_reminder)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceField(stringResource(R.string.reminders), preset, labels.map { stringResource(it) }) { preset = it }
            if (allDay) OutlinedTextField(time, { time = it }, label = { Text(stringResource(R.string.time)) }, singleLine = true, isError = invalid)
            if (invalid) Text(stringResource(R.string.invalid), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = {
        val localTime = runCatching { if (allDay) LocalTime.parse(time) else event.time }.getOrNull()
        if (localTime == null) invalid = true else {
            val calendar = allDay || preset >= 3
            val days = if (allDay) listOf(0, 1, 7)[preset] else if (preset == 3) 1 else if (preset == 4) 7 else 0
            val seconds = if (calendar) 0L else listOf(0L, 900L, 3600L)[preset]
            val rule = ReminderEntity(eventId = event.id, kind = if (calendar) "CALENDAR_DAYS_BEFORE" else "DURATION_BEFORE", offsetSeconds = seconds, daysBefore = days, localTime = localTime.toString(), createdAt = vm.app.clock.instant().toString())
            vm.action {
                vm.app.reminders.add(rule)
                adding = false
                ReminderScheduler.channel(context)
                if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                permitted = ReminderScheduler.allowed(context)
            }
        }
    }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.cancel)) } })
}
