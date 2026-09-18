@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.countdown.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import io.github.countdown.R
import io.github.countdown.data.CategoryEntity
import io.github.countdown.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

@Composable fun EditorScreen(vm: EventsViewModel, original: CountdownEvent?, categories: List<CategoryEntity>, onClose: () -> Unit) {
    val context = LocalContext.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    val base = remember(original?.id) { original ?: CountdownEvent(title = "", date = LocalDate.now().plusDays(7), createdAt = vm.app.clock.instant()) }
    var id by rememberSaveable { mutableStateOf(base.id) }
    var created by rememberSaveable { mutableStateOf(base.createdAt.toString()) }
    var title by rememberSaveable { mutableStateOf(base.title) }
    var description by rememberSaveable { mutableStateOf(base.description) }
    var date by rememberSaveable { mutableStateOf(base.date.toString()) }
    var time by rememberSaveable { mutableStateOf(base.time.toString()) }
    var zone by rememberSaveable { mutableStateOf(base.zone.id) }
    var allDay by rememberSaveable { mutableStateOf(base.timeMode == TimeMode.ALL_DAY) }
    var fixed by rememberSaveable { mutableStateOf(base.timeMode == TimeMode.FIXED_INSTANT) }
    var repeatIndex by rememberSaveable { mutableIntStateOf(base.repeat.ordinal) }
    var interval by rememberSaveable { mutableStateOf(base.repeatDays.toString()) }
    var categoryId by rememberSaveable { mutableStateOf(base.categoryId) }
    var icon by rememberSaveable { mutableStateOf(base.icon) }
    var color by rememberSaveable { mutableLongStateOf(base.color) }
    var format by rememberSaveable { mutableIntStateOf(base.format.ordinal) }
    var completion by rememberSaveable { mutableIntStateOf(base.completion.ordinal) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var appearance by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }

    fun draft(): CountdownEvent {
        val localDate = LocalDate.parse(date)
        val localTime = LocalTime.parse(time)
        val zoneId = ZoneId.of(zone.trim())
        require(localDate.year in 1900..9999)
        require(interval.toInt() in 1..36500)
        val isFixed = fixed && !allDay && repeatIndex == 0
        val instant = if (!isFixed) null else if (base.fixedInstant != null && date == base.date.toString() && time == base.time.toString()) base.fixedInstant else localDate.atTime(localTime).atZone(zoneId).toInstant()
        return base.copy(id = id, title = title.trim(), description = description, date = localDate, time = localTime, zone = zoneId,
            timeMode = if (allDay) TimeMode.ALL_DAY else if (isFixed) TimeMode.FIXED_INSTANT else TimeMode.ZONED_LOCAL,
            fixedInstant = instant, repeat = Repeat.entries[repeatIndex], repeatDays = interval.toInt(), categoryId = categoryId,
            icon = icon, color = color, format = CountdownFormat.entries[format], completion = CompletionPolicy.entries[completion], createdAt = Instant.parse(created))
    }
    val preview = runCatching { draft() }.getOrNull()
    val dirty = title != base.title || description != base.description || date != base.date.toString() || time != base.time.toString() || zone != base.zone.id ||
        allDay != (base.timeMode == TimeMode.ALL_DAY) || fixed != (base.timeMode == TimeMode.FIXED_INSTANT) || repeatIndex != base.repeat.ordinal || interval != base.repeatDays.toString() ||
        categoryId != base.categoryId || icon != base.icon || color != base.color || format != base.format.ordinal || completion != base.completion.ordinal
    fun close() { if (busy) return; if (dirty) discard = true else onClose() }
    BackHandler { close() }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(if (original == null) R.string.new_event else R.string.edit_event)) },
        navigationIcon = { BackButton { close() } }, actions = {
            TextButton(enabled = !busy, modifier = Modifier.testTag("saveEvent"), onClick = {
                val event = runCatching { draft().also { it.validate() } }.getOrNull()
                if (event == null) error = R.string.invalid else {
                    focus.clearFocus()
                    keyboard?.hide()
                    busy = true; error = null
                    scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
                        try { vm.app.repository.save(event); onClose() }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) {
                            if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) android.util.Log.e("CountdownSave", "Save failed", e)
                            error = R.string.error
                        }
                        finally { busy = false }
                    }
                }
            }) { Text(stringResource(R.string.save)) }
        }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("editorError")) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedTextField(title, { title = it.take(120) }, label = { Text(stringResource(R.string.title)) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("eventTitle"), isError = error != null && title.isBlank())
            OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MaterialTheme.shapes.small) {
                Icon(painterResource(R.drawable.ic_event), null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.date) + ": " + (runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) }.getOrDefault(date)))
            }
            Toggle(stringResource(R.string.all_day), allDay) { allDay = it }
            if (allDay && original != null && original.timeMode != TimeMode.ALL_DAY) Text(stringResource(R.string.all_day_reminder_change), style = MaterialTheme.typography.bodySmall)
            if (!allDay) {
                OutlinedTextField(time, { time = it }, label = { Text(stringResource(R.string.time)) }, placeholder = { Text("09:00") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), modifier = Modifier.fillMaxWidth().testTag("eventTime"))
            }
            ChoiceField(stringResource(R.string.category), categories.indexOfFirst { it.id == categoryId } + 1,
                listOf(stringResource(R.string.cat_other)) + categories.map { it.label(context) }) { categoryId = categories.getOrNull(it - 1)?.id }
            preview?.let { event ->
                val snapshot = CountdownEngine.calculate(event, vm.app.clock.instant())
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.preview), style = MaterialTheme.typography.labelMedium)
                        Text(countdownLabel(context, event, snapshot), style = MaterialTheme.typography.headlineMedium)
                        Text(dateLabel(context, event, snapshot), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            TextButton(onClick = { expanded = !expanded }) { Text(stringResource(R.string.repeat)) }
            if (expanded) {
                ChoiceField(stringResource(R.string.repeat), repeatIndex, repeatLabels.map { stringResource(it) }) { repeatIndex = it; if (it != 0) fixed = false }
                if (repeatIndex == Repeat.EVERY_N_DAYS.ordinal) OutlinedTextField(interval, { interval = it.take(5) }, label = { Text(stringResource(R.string.interval)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                if (repeatIndex != 0) {
                    Text(stringResource(R.string.repeat_hint), style = MaterialTheme.typography.bodySmall)
                    preview?.let { e ->
                        val dates = runCatching {
                            var after = vm.app.clock.instant()
                            buildList { repeat(3) { Recurrence.next(e, after)?.let { add(it.date.toString()); after = maxOf(it.start, it.end).plusMillis(1) } } }.joinToString(", ")
                        }.getOrDefault("")
                        Text(stringResource(R.string.next_dates, dates), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            TextButton(onClick = { appearance = !appearance }) { Text(stringResource(R.string.appearance)) }
            if (appearance) {
                Text(stringResource(R.string.icon), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    iconResources.entries.forEachIndexed { index, entry ->
                        val name = stringResource(iconLabels[index])
                        IconButton(onClick = { icon = entry.key }, modifier = Modifier.semantics { selected = icon == entry.key; contentDescription = name }
                            .then(if (icon == entry.key) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier)) { Icon(painterResource(entry.value), null) }
                    }
                }
                Text(stringResource(R.string.accent), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    accents.forEachIndexed { index, accent ->
                        FilterChip(selected = accent == color, onClick = { color = accent }, label = { Text(stringResource(accentLabels[index])) })
                    }
                }
                ChoiceField(stringResource(R.string.format), format, formatLabels.map { stringResource(it) }) { format = it }
            }
            OutlinedTextField(description, { description = it.take(4000) }, label = { Text(stringResource(R.string.notes)) }, minLines = 2, maxLines = 6, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { advanced = !advanced }) { Text(stringResource(R.string.advanced)) }
            if (advanced) {
                OutlinedTextField(zone, { zone = it }, label = { Text(stringResource(R.string.timezone)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (!allDay && repeatIndex == 0) {
                    Toggle(stringResource(R.string.fixed_time), fixed) { fixed = it }
                    Text(stringResource(R.string.fixed_hint), style = MaterialTheme.typography.bodySmall)
                }
                if (repeatIndex == 0) ChoiceField(stringResource(R.string.after_event), completion, policyLabels.map { stringResource(it) }) { completion = it }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (showDate) {
        val initial = runCatching { LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial, yearRange = 1900..9999)
        DatePickerDialog(onDismissRequest = { showDate = false }, confirmButton = { TextButton(onClick = {
            state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }; showDate = false
        }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.cancel)) } }) { DatePicker(state) }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text(stringResource(R.string.discard_title)) }, text = { Text(stringResource(R.string.discard_body)) },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.discard)) } }, dismissButton = { TextButton(onClick = { discard = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 16.dp))
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = label })
    }
}
