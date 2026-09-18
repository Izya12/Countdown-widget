@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.countdown.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import io.github.countdown.R
import io.github.countdown.domain.*
import io.github.countdown.data.ThemeMode
import java.text.Collator

@Composable fun AppScreens(vm: EventsViewModel, initialEventId: String? = null) {
    val nav = rememberNavController()
    val events by vm.events.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(initialEventId) { if (initialEventId != null) nav.navigate("detail/$initialEventId") }
    if (error) AlertDialog(onDismissRequest = { vm.error.value = false }, title = { Text(stringResource(R.string.error)) },
        confirmButton = { TextButton(onClick = { vm.error.value = false }) { Text(stringResource(R.string.cancel)) } })
    NavHost(nav, startDestination = "events") {
        composable("events") {
            EventsScreen(vm, events, ready, onAdd = { nav.navigate("edit/new") }, onOpen = { nav.navigate("detail/$it") }, onSettings = { nav.navigate("settings") })
        }
        composable("detail/{id}") { entry ->
            val event = events.find { it.id == entry.arguments?.getString("id") }
            if (!ready) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else if (event == null) MissingScreen { nav.popBackStack() }
            else DetailScreen(vm, event, onBack = { nav.popBackStack() }, onEdit = { nav.navigate("edit/${event.id}") })
        }
        composable("edit/{id}") { entry ->
            val id = entry.arguments?.getString("id")
            val existing = events.find { it.id == id }
            if (!ready) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else if (id != "new" && existing == null) MissingScreen { nav.popBackStack() }
            else EditorScreen(vm, existing, categories) { nav.popBackStack() }
        }
        composable("settings") {
            var categoryName by rememberSaveable { mutableStateOf("") }
            val theme by vm.theme.collectAsStateWithLifecycle()
            Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings)) }, navigationIcon = { BackButton { nav.popBackStack() } }) }) { padding ->
                Column(Modifier.padding(padding).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Spacer(Modifier.height(4.dp))
                    ChoiceField(stringResource(R.string.theme), ThemeMode.entries.indexOf(theme), listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark).map { stringResource(it) }) { index -> vm.action { vm.app.settings.theme(ThemeMode.entries[index]) } }
                    Text(stringResource(R.string.categories), style = MaterialTheme.typography.titleLarge)
                    categories.forEach { category ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(category.label(context), Modifier.weight(1f))
                            if (category.seedKey == null) TextButton(onClick = { vm.action { vm.app.repository.dao.deleteCategory(category.id) } }) { Text(stringResource(R.string.delete)) }
                        }
                    }
                    OutlinedTextField(categoryName, { categoryName = it.take(60) }, label = { Text(stringResource(R.string.category_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedButton(onClick = { vm.addCategory(categoryName) { categoryName = "" } }, enabled = categoryName.isNotBlank()) { Text(stringResource(R.string.new_category)) }
                    HorizontalDivider()
                    BackupControls(vm)
                    HorizontalDivider()
                    Text(stringResource(R.string.privacy), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable fun BackButton(onBack: () -> Unit) { TextButton(onClick = onBack) { Text("‹", fontSize = 30.sp); Text(stringResource(R.string.back)) } }

@Composable fun ChoiceField(label: String, selected: Int, options: List<String>, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MaterialTheme.shapes.small) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(options.getOrElse(selected) { "" }, style = MaterialTheme.typography.bodyLarge)
        }
        Text("⌄", Modifier.padding(start = 8.dp))
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(label) }, text = {
        LazyColumn { items(options.size) { index ->
            Row(Modifier.fillMaxWidth().clickable { onSelect(index); open = false }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected == index, onClick = { onSelect(index); open = false })
                Text(options[index], Modifier.padding(end = 8.dp))
            }
        } }
    }, confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun EventsScreen(vm: EventsViewModel, events: List<CountdownEvent>, ready: Boolean, onAdd: () -> Unit, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val categories by vm.categories.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val locale = LocalConfiguration.current.locales[0]
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var favoriteOnly by rememberSaveable { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var showSort by remember { mutableStateOf(false) }
    // List intentionally has no per-row seconds display or ticker.
    val now = countdownNow(events.map { if (it.format == CountdownFormat.DETAILED) it.copy(format = CountdownFormat.AUTO) else it }, vm.app.clock)
    val results = remember(events, now, query, filter, favoriteOnly, category, sort, locale) {
        val selected = events.filter { e ->
            val state = CountdownEngine.calculate(e, now).status
            val match = when (filter) {
                2 -> state == EventStatus.ARCHIVED
                1 -> state in setOf(EventStatus.PAST, EventStatus.COMPLETED, EventStatus.EXHAUSTED)
                else -> state in setOf(EventStatus.UPCOMING, EventStatus.TODAY, EventStatus.STARTED)
            }
            match && (!favoriteOnly || e.favorite) && (category == null || e.categoryId == category) &&
                (e.title.contains(query, true) || e.description.contains(query, true))
        }
        val collator = Collator.getInstance(locale)
        val order = when (sort) {
            1 -> compareByDescending<CountdownEvent> { Recurrence.next(it, now)?.start }
            2 -> Comparator { a: CountdownEvent, b: CountdownEvent -> collator.compare(a.title, b.title) }
            3 -> compareByDescending { e: CountdownEvent -> e.createdAt }
            4 -> compareBy { e: CountdownEvent -> e.sortRank }
            else -> compareBy { e: CountdownEvent -> Recurrence.next(e, now)?.start }
        }
        selected.sortedWith(compareByDescending<CountdownEvent> { it.pinned }.then(order).thenBy { it.id })
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge) },
            actions = { TextButton(onClick = onSettings) { Text(stringResource(R.string.settings)) } })
    }, floatingActionButton = { ExtendedFloatingActionButton(onClick = onAdd, modifier = Modifier.testTag("addEvent")) { Text("+", fontSize = 24.sp); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.add)) } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.search)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.upcoming, R.string.past, R.string.archive).forEachIndexed { index, label ->
                        FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(stringResource(label)) })
                    }
                    FilterChip(selected = favoriteOnly, onClick = { favoriteOnly = !favoriteOnly }, label = { Text(stringResource(R.string.favorites)) })
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        ChoiceField(stringResource(R.string.category), categories.indexOfFirst { it.id == category } + 1,
                            listOf(stringResource(R.string.all_categories)) + categories.map { it.label(context) }) { category = categories.getOrNull(it - 1)?.id }
                    }
                    TextButton(onClick = { showSort = true }) { Text(stringResource(R.string.sort)) }
                }
            }
            if (!ready) item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            else if (results.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(R.drawable.ic_event), null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(if (events.isEmpty()) R.string.empty_title else R.string.no_results), style = MaterialTheme.typography.headlineSmall)
                    if (events.isEmpty()) Text(stringResource(R.string.empty_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(results, key = { it.id }) { event ->
                val value = if (event.format == CountdownFormat.DETAILED) event.copy(format = CountdownFormat.AUTO) else event
                val snapshot = CountdownEngine.calculate(value, now)
                Card(onClick = { onOpen(event.id) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(iconResources[event.icon] ?: R.drawable.ic_event), null, tint = Color(event.color), modifier = Modifier.size(24.dp))
                            Text(event.title, Modifier.padding(start = 12.dp).weight(1f), style = MaterialTheme.typography.titleMedium)
                            if (event.favorite) Icon(painterResource(R.drawable.ic_star), stringResource(R.string.favorite), Modifier.size(20.dp))
                        }
                        Text(countdownLabel(context, value, snapshot), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                        Text(dateLabel(context, value, snapshot), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        snapshot.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = Color(event.color)) }
                    }
                }
            }
        }
    }
    if (showSort) AlertDialog(onDismissRequest = { showSort = false }, title = { Text(stringResource(R.string.sort)) }, text = {
        Column { listOf(R.string.nearest, R.string.farthest, R.string.alphabet, R.string.created, R.string.manual).forEachIndexed { index, label ->
            TextButton(onClick = { sort = index; showSort = false }) { Text(stringResource(label)) }
        } }
    }, confirmButton = { TextButton(onClick = { showSort = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun DetailScreen(vm: EventsViewModel, event: CountdownEvent, onBack: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val now = countdownNow(listOf(event), vm.app.clock)
    val snapshot = CountdownEngine.calculate(event, now)
    var deleting by remember { mutableStateOf(false) }
    var widgetHint by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.details)) }, navigationIcon = { BackButton(onBack) }, actions = { TextButton(onClick = onEdit) { Text(stringResource(R.string.edit)) } }) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Icon(painterResource(iconResources[event.icon] ?: R.drawable.ic_event), null, Modifier.size(48.dp), tint = Color(event.color))
            Text(event.title, modifier = Modifier.testTag("detailTitle"), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(if (snapshot.status == EventStatus.PAST) R.string.elapsed else R.string.remaining), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(countdownLabel(context, event, snapshot), fontSize = 44.sp, lineHeight = 50.sp, fontWeight = FontWeight.SemiBold)
            Text(dateLabel(context, event, snapshot), style = MaterialTheme.typography.titleMedium)
            snapshot.progress?.let {
                LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth(), color = Color(event.color))
                Text(stringResource(R.string.progress, (it * 100).toInt()), style = MaterialTheme.typography.labelLarge)
            }
            if (event.description.isNotEmpty()) Text(event.description, style = MaterialTheme.typography.bodyLarge)
            if (event.repeat != Repeat.NONE) Text(stringResource(repeatLabels[event.repeat.ordinal]))
            ReminderControls(vm, event)
            HorizontalDivider()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.update(event.copy(favorite = !event.favorite)) }) { Text(stringResource(if (event.favorite) R.string.unfavorite else R.string.favorite)) }
                OutlinedButton(onClick = { vm.update(event.copy(pinned = !event.pinned)) }) { Text(stringResource(if (event.pinned) R.string.unpin else R.string.pin)) }
                OutlinedButton(onClick = { vm.update(event.copy(archived = !event.archived, completion = if (event.archived) CompletionPolicy.ELAPSED else event.completion)) }) { Text(stringResource(if (event.archived) R.string.restore else R.string.archive)) }
            }
            OutlinedButton(onClick = { widgetHint = true }) { Text(stringResource(R.string.widgets)) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { vm.move(event.id, -1) }) { Text(stringResource(R.string.move_up)) }
                TextButton(onClick = { vm.move(event.id, 1) }) { Text(stringResource(R.string.move_down)) }
            }
            TextButton(onClick = { deleting = true }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text(stringResource(R.string.delete_title)) }, text = { Text(stringResource(R.string.delete_body)) },
        confirmButton = { TextButton(onClick = { vm.action { vm.app.repository.dao.delete(event.id); onBack() } }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } })
    if (widgetHint) AlertDialog(onDismissRequest = { widgetHint = false }, title = { Text(stringResource(R.string.widgets)) }, text = { Text(stringResource(R.string.widget_hint)) }, confirmButton = { TextButton(onClick = { widgetHint = false }) { Text(stringResource(R.string.back)) } })
}

@Composable private fun MissingScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) { Text(stringResource(R.string.widget_missing)); BackButton(onBack) }
}
