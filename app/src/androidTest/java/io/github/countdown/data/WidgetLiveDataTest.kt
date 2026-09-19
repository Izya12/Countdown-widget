package io.github.countdown.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.countdown.CountdownApp
import io.github.countdown.MainActivity
import io.github.countdown.R
import io.github.countdown.domain.*
import io.github.countdown.ui.countdownLabel
import io.github.countdown.widget.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@androidx.test.filters.SdkSuppress(minSdkVersion = 29)
class WidgetLiveDataTest {
    @Test fun circularWidgetObservesConfigurationEditsAndDeletion(): Unit = verify(true)
    @Test fun standardWidgetObservesConfigurationEditsAndDeletion(): Unit = verify(false)

    private fun verify(circular: Boolean): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<CountdownApp>()
        val manager = AppWidgetManager.getInstance(app)
        val host = AppWidgetHost(app, if (circular) 7401 else 7402)
        val id = host.allocateAppWidgetId()
        val provider = ComponentName(app, if (circular) CircleCountdownWidgetReceiver::class.java else CountdownWidgetReceiver::class.java)
        val widget = if (circular) CircleCountdownWidget() else CountdownWidget()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val event = CountdownEvent(title = "Live widget fixture", date = LocalDate.now().plusDays(21))
        var scenario: ActivityScenario<MainActivity>? = null
        var view: AppWidgetHostView? = null
        fun text(v: View): String = buildString {
            append(v.contentDescription ?: "")
            if (v is TextView) append(v.text)
            if (v is ViewGroup) for (i in 0 until v.childCount) append(text(v.getChildAt(i)))
        }
        fun awaitText(expected: String) {
            var actual = ""
            repeat(100) {
                requireNotNull(scenario).onActivity { actual = text(requireNotNull(view)) }
                if (actual.contains(expected)) return
                android.os.SystemClock.sleep(100)
            }
            fail("Widget did not display '$expected'; actual: '$actual'")
        }
        try {
            automation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
            assertTrue(manager.bindAppWidgetIdIfAllowed(id, provider, Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 180)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110)
            }))
            automation.dropShellPermissionIdentity()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity { activity ->
                view = host.createView(activity, id, manager.getAppWidgetInfo(id))
                val density = activity.resources.displayMetrics.density
                val root = FrameLayout(activity)
                root.addView(view, FrameLayout.LayoutParams((180 * density).toInt(), (110 * density).toInt()))
                activity.setContentView(root)
                host.startListening()
            }
            val glanceId = GlanceAppWidgetManager(app).getGlanceIdBy(id)
            widget.update(app, glanceId)
            awaitText(app.getString(if (circular) R.string.choose_event else R.string.widget_missing))
            app.database.dao().save(EventEntity.from(event))
            app.database.dao().saveWidget(WidgetConfig(id, event.id, if (circular) "CIRCLE_DAYS" else "STANDARD"))
            widget.update(app, glanceId)
            awaitText(countdownLabel(app, event, CountdownEngine.calculate(event, app.clock.instant()), widget = true))
            val changed = event.copy(title = "Changed live widget", date = event.date.plusDays(7))
            app.database.dao().save(EventEntity.from(changed))
            widget.update(app, glanceId)
            awaitText(countdownLabel(app, changed, CountdownEngine.calculate(changed, app.clock.instant()), widget = true))
            if (circular) {
                awaitText(changed.title)
                app.database.dao().saveWidget(WidgetConfig(id, event.id, "CIRCLE_WEEKS"))
                widget.update(app, glanceId)
                val weeks = changed.copy(format = CountdownFormat.WEEKS)
                awaitText(countdownLabel(app, weeks, CountdownEngine.calculate(weeks, app.clock.instant()), widget = true))
            }
            app.database.dao().delete(event.id)
            widget.update(app, glanceId)
            awaitText(app.getString(if (circular) R.string.choose_event else R.string.widget_missing))
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
