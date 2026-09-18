package io.github.countdown.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.room.withTransaction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.countdown.CountdownApp
import io.github.countdown.R
import io.github.countdown.data.ThemeMode
import io.github.countdown.data.WidgetConfig
import io.github.countdown.ui.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class WidgetConfigurationActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)
        val circular = info?.provider == ComponentName(this, CircleCountdownWidgetReceiver::class.java)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || (!circular && info?.provider != ComponentName(this, CountdownWidgetReceiver::class.java))) { finish(); return }
        enableEdgeToEdge()
        val app = application as CountdownApp
        setContent {
            val events by app.repository.events.collectAsStateWithLifecycle(initialValue = emptyList())
            val theme by app.settings.theme.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            var config by rememberSaveable(stateSaver = listSaver<WidgetConfig, Any>(
                save = { listOf(it.appWidgetId, it.eventId.orEmpty(), it.style, it.showDate, it.showProgress) },
                restore = { WidgetConfig(it[0] as Int, (it[1] as String).ifEmpty { null }, it[2] as String, it[3] as Boolean, it[4] as Boolean) }
            )) { mutableStateOf(WidgetConfig(id, null, style = if (circular) "CIRCLE" else "STANDARD")) }
            var loaded by rememberSaveable { mutableStateOf(false) }
            var busy by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            LaunchedEffect(id) {
                try { if (!loaded) { config = app.database.dao().widget(id) ?: config; loaded = true } }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { error = true }
            }
            CountdownTheme(theme) {
                Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.widgets)) }) }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (error) Text(stringResource(R.string.error), color = MaterialTheme.colorScheme.error)
                        if (events.isEmpty()) {
                            Text(stringResource(R.string.empty_body))
                            Button(onClick = { startActivity(Intent(this@WidgetConfigurationActivity, io.github.countdown.MainActivity::class.java)) }) { Text(stringResource(R.string.add)) }
                        } else {
                            ChoiceField(stringResource(R.string.choose_event), events.indexOfFirst { it.id == config.eventId }, events.map { it.title }) { config = config.copy(eventId = events[it].id) }
                            if (!circular) {
                            val styles = listOf("MINIMAL", "STANDARD", "PROGRESS", "CALENDAR")
                            ChoiceField(stringResource(R.string.widget_style), styles.indexOf(config.style), listOf(R.string.style_minimal, R.string.style_standard, R.string.style_progress, R.string.style_calendar).map { stringResource(it) }) { config = config.copy(style = styles[it]) }
                            Toggle(stringResource(R.string.show_date), config.showDate) { config = config.copy(showDate = it) }
                            } else {
                                Text(stringResource(R.string.circle_widget_description))
                                val circleStyles = listOf("CIRCLE", "CIRCLE_DAYS", "CIRCLE_WEEKS")
                                ChoiceField(stringResource(R.string.circle_units), circleStyles.indexOf(config.style).coerceAtLeast(0),
                                    listOf(R.string.circle_event_format, R.string.circle_days, R.string.circle_weeks).map { stringResource(it) }) { config = config.copy(style = circleStyles[it]) }
                            }
                            Toggle(stringResource(R.string.show_progress), config.showProgress) { config = config.copy(showProgress = it) }
                            events.find { it.id == config.eventId }?.let { event ->
                                val snapshot = io.github.countdown.domain.CountdownEngine.calculate(event, app.clock.instant())
                                if (circular) {
                                    val bitmap = remember(event, snapshot, config.showProgress, config.style) { RoundWidgetRenderer.render(this@WidgetConfigurationActivity, circleEvent(event, config.style), snapshot, config.showProgress) }
                                    Surface(color = androidx.compose.ui.graphics.Color(0xFF454548), shape = MaterialTheme.shapes.medium) {
                                        androidx.compose.foundation.Image(bitmap.asImageBitmap(), event.title, Modifier.padding(20.dp).size(72.dp, 96.dp))
                                    }
                                } else {
                                Card {
                                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(stringResource(R.string.preview), style = MaterialTheme.typography.labelMedium)
                                        Text(event.title, style = MaterialTheme.typography.titleMedium)
                                        Text(countdownLabel(this@WidgetConfigurationActivity, event, snapshot, widget = true), style = MaterialTheme.typography.headlineMedium)
                                        if (config.showDate) Text(dateLabel(this@WidgetConfigurationActivity, event, snapshot))
                                        snapshot.progress?.let { if (config.style == "PROGRESS" && config.showProgress) LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                                    }
                                }
                                }
                            }
                            Text(stringResource(R.string.widget_delay), style = MaterialTheme.typography.bodySmall)
                            Button(enabled = loaded && !busy && events.any { it.id == config.eventId }, onClick = {
                                busy = true; error = false
                                scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
                                    val pending = config
                                    var previous: WidgetConfig? = null
                                    var committed = false
                                    try {
                                        app.database.withTransaction {
                                            previous = app.database.dao().widget(id)
                                            check(app.database.dao().event(requireNotNull(pending.eventId)) != null)
                                            app.database.dao().saveWidget(pending)
                                        }
                                        committed = true
                                        val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                                            component = ComponentName(this@WidgetConfigurationActivity, if (circular) CircleCountdownWidgetReceiver::class.java else CountdownWidgetReceiver::class.java)
                                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(id))
                                        }
                                        sendBroadcast(updateIntent)
                                        WidgetUpdates.request(this@WidgetConfigurationActivity)
                                        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                                        finish()
                                    } catch (e: Exception) {
                                        if (committed) withContext(NonCancellable) {
                                            app.database.withTransaction {
                                                if (app.database.dao().widget(id) == pending) {
                                                    val old = previous
                                                    if (old == null) app.database.dao().deleteWidget(id)
                                                    else app.database.dao().saveWidget(old.copy(eventId = old.eventId?.takeIf { app.database.dao().event(it) != null }))
                                                }
                                            }
                                            WidgetUpdates.request(this@WidgetConfigurationActivity)
                                        }
                                        if (e is kotlinx.coroutines.CancellationException) throw e
                                        error = true
                                    }
                                    finally { busy = false }
                                }
                            }) { Text(stringResource(R.string.save)) }
                        }
                        TextButton(enabled = !busy, onClick = { finish() }) { Text(stringResource(R.string.cancel)) }
                    }
                }
            }
        }
    }
}
