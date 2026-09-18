package io.github.countdown.data

import android.appwidget.*
import android.content.ComponentName
import android.graphics.*
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.glance.appwidget.GlanceAppWidgetManager
import io.github.countdown.CountdownApp
import io.github.countdown.MainActivity
import io.github.countdown.domain.*
import io.github.countdown.widget.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule

@RunWith(AndroidJUnit4::class)
@androidx.test.filters.SdkSuppress(minSdkVersion = 29)
class CircleWidgetTest {
    @get:org.junit.Rule val ui = createEmptyComposeRule()
    @Test fun configurationSavesIndependentUnitsAndSurvivesRecreation(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<CountdownApp>()
        val manager = AppWidgetManager.getInstance(app)
        val host = AppWidgetHost(app, 7302)
        val first = host.allocateAppWidgetId()
        val second = host.allocateAppWidgetId()
        val event = CountdownEvent(title = "Circle configuration fixture", date = LocalDate.now().plusDays(22))
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        var scenario: ActivityScenario<WidgetConfigurationActivity>? = null
        try {
            automation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
            for (id in listOf(first, second)) assertTrue(manager.bindAppWidgetIdIfAllowed(id, ComponentName(app, CircleCountdownWidgetReceiver::class.java)))
            automation.dropShellPermissionIdentity()
            app.database.dao().save(EventEntity.from(event))
            app.database.dao().saveWidget(WidgetConfig(first, event.id, "CIRCLE_DAYS"))
            val intent = android.content.Intent(app, WidgetConfigurationActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, second)
            scenario = ActivityScenario.launch(intent)
            ui.waitUntil(10_000) { ui.onAllNodesWithText(app.getString(io.github.countdown.R.string.choose_event)).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText(app.getString(io.github.countdown.R.string.choose_event)).performClick()
            ui.onNodeWithText(event.title).performClick()
            ui.onNodeWithText(app.getString(io.github.countdown.R.string.circle_units)).performScrollTo().performClick()
            ui.onNodeWithText(app.getString(io.github.countdown.R.string.circle_weeks)).performClick()
            scenario.recreate()
            ui.onNodeWithText(app.getString(io.github.countdown.R.string.circle_weeks)).assertExists()
            ui.onNodeWithText(app.getString(io.github.countdown.R.string.save)).performScrollTo().performClick()
            ui.waitUntil(10_000) { runBlocking { app.database.dao().widget(second)?.style == "CIRCLE_WEEKS" } }
            ui.waitUntil(10_000) { scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
            assertEquals("CIRCLE_DAYS", app.database.dao().widget(first)!!.style)
            assertEquals(event.id, app.database.dao().widget(second)!!.eventId)
        } finally {
            automation.dropShellPermissionIdentity()
            scenario?.close()
            for (id in listOf(first, second)) { host.deleteAppWidgetId(id); app.database.dao().deleteWidget(id) }
            app.database.dao().delete(event.id)
        }
    }
    @Test fun transparentCircleFitsOneCellAndHostRendersIt(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<CountdownApp>()
        val event = CountdownEvent(title = "На вахту", date = LocalDate.now().plusDays(22), color = 0xFFD52C32)
        val snapshot = CountdownEngine.calculate(event, app.clock.instant())
        val bitmap = RoundWidgetRenderer.render(app, event, snapshot, true, 72f, 96f)
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
        assertEquals(0, Color.alpha(bitmap.getPixel(bitmap.width - 1, 0)))
        assertEquals(Color.WHITE, bitmap.getPixel(72, 36))
        val elapsed = event.copy(date = LocalDate.now().minusDays(50), format = CountdownFormat.WEEKS)
        val weeks = RoundWidgetRenderer.render(app, elapsed, CountdownEngine.calculate(elapsed, app.clock.instant()), true, 72f, 96f)
        java.io.File(app.getExternalFilesDir(null), "circle-widget-weeks.png").outputStream().use { weeks.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val manager = AppWidgetManager.getInstance(app)
        val provider = ComponentName(app, CircleCountdownWidgetReceiver::class.java)
        val info = manager.installedProviders.first { it.provider == provider }
        if (android.os.Build.VERSION.SDK_INT >= 31) { assertEquals(1, info.targetCellWidth); assertEquals(1, info.targetCellHeight) }
        val host = AppWidgetHost(app, 7301)
        val id = host.allocateAppWidgetId()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            automation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
            assertTrue(manager.bindAppWidgetIdIfAllowed(id, provider, Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 72); putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 72)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 96); putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 96)
            }))
            automation.dropShellPermissionIdentity()
            app.database.dao().save(EventEntity.from(event))
            app.database.dao().saveWidget(WidgetConfig(id, event.id, "CIRCLE"))
            var hostView: AppWidgetHostView? = null
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity { activity ->
                val root = FrameLayout(activity).apply { setBackgroundColor(Color.DKGRAY) }
                hostView = host.createView(activity, id, info)
                val density = activity.resources.displayMetrics.density
                root.addView(hostView, FrameLayout.LayoutParams((72 * density).toInt(), (96 * density).toInt(), android.view.Gravity.CENTER))
                activity.setContentView(root)
                host.startListening()
            }
            CircleCountdownWidget().update(app, GlanceAppWidgetManager(app).getGlanceIdBy(id))
            fun containsImage(view: View): Boolean = view.contentDescription?.contains(event.title) == true || (view is ViewGroup && (0 until view.childCount).any { containsImage(view.getChildAt(it)) })
            var rendered = false
            for (attempt in 0..99) {
                scenario.onActivity { rendered = hostView?.let(::containsImage) == true }
                if (rendered) break
                android.os.SystemClock.sleep(100)
            }
            assertTrue("Glance content reached AppWidgetHost", rendered)
            scenario.onActivity {
                val view = requireNotNull(hostView)
                val capture = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(capture))
                java.io.File(app.getExternalFilesDir(null), "circle-widget-host.png").outputStream().use { capture.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            val instrument = InstrumentationRegistry.getInstrumentation()
            val monitor = instrument.addMonitor(MainActivity::class.java.name, null, false)
            fun clickableImage(view: View): View? {
                if (view is ViewGroup) for (child in 0 until view.childCount) clickableImage(view.getChildAt(child))?.let { return it }
                if (view.isClickable && containsImage(view)) return view
                return null
            }
            try {
                scenario.onActivity { assertTrue(requireNotNull(clickableImage(requireNotNull(hostView))).performClick()) }
                val opened = requireNotNull(monitor.waitForActivityWithTimeout(5000))
                assertEquals(event.id, opened.intent.getStringExtra("eventId"))
                instrument.runOnMainSync { opened.finish() }
            } finally { instrument.removeMonitor(monitor) }
        } finally {
            automation.dropShellPermissionIdentity()
            scenario?.close()
            host.stopListening()
            host.deleteAppWidgetId(id)
            app.database.dao().deleteWidget(id)
            app.database.dao().delete(event.id)
        }
    }
}
