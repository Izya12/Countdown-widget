package io.github.countdown.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.countdown.CountdownApp
import io.github.countdown.MainActivity
import io.github.countdown.R
import io.github.countdown.data.*
import io.github.countdown.domain.CountdownEvent
import io.github.countdown.widget.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Opt-in capture of real screens using synthetic events; excluded from normal test runs. */
@RunWith(AndroidJUnit4::class)
@androidx.test.filters.SdkSuppress(minSdkVersion = 29)
class StoreScreenshotsTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun capture(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("storeScreenshots") == "true")
        val app = ApplicationProvider.getApplicationContext<CountdownApp>()
        val ru = app.resources.configuration.locales[0].language == "ru"
        // Never publish a screenshot containing an existing user's events.
        check(app.database.dao().events().isEmpty()) { "Use a fresh app installation for store screenshots" }
        val titles = if (ru) listOf("Отпуск у моря", "День рождения", "Начало путешествия")
            else listOf("Seaside holiday", "Birthday", "Adventure begins")
        val events = titles.mapIndexed { index, title -> CountdownEvent(title = title,
            date = LocalDate.now().plusDays(listOf(22L, 45L, 90L)[index]), color = listOf(0xFFD52C32, 0xFF318C43, 0xFF1976D2)[index]) }
        val theme = app.settings.theme.first()
        val host = AppWidgetHost(app, 7601)
        var id: Int? = null
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val output = File(app.getExternalFilesDir(null), "store").apply { mkdirs() }
        fun capture(name: String) {
            ui.waitForIdle()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
            File(output, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        try {
            app.settings.theme(ThemeMode.DARK)
            events.forEach { app.database.dao().save(EventEntity.from(it)) }
            ActivityScenario.launch(MainActivity::class.java).use {
                ui.waitUntil(10_000) { ui.onAllNodesWithText(titles[0]).fetchSemanticsNodes().isNotEmpty() }
                capture("1.png")
                ui.onNodeWithText(titles[0]).performClick()
                ui.waitUntil(10_000) { ui.onAllNodesWithTag("detailTitle").fetchSemanticsNodes().isNotEmpty() }
                capture("2.png")
            }
            id = host.allocateAppWidgetId()
            automation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
            check(AppWidgetManager.getInstance(app).bindAppWidgetIdIfAllowed(id, ComponentName(app, CircleCountdownWidgetReceiver::class.java)))
            automation.dropShellPermissionIdentity()
            app.database.dao().saveWidget(WidgetConfig(id, events[0].id, "CIRCLE_DAYS"))
            ActivityScenario.launch<WidgetConfigurationActivity>(Intent(app, WidgetConfigurationActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)).use {
                ui.waitUntil(10_000) { ui.onAllNodesWithText(titles[0]).fetchSemanticsNodes().isNotEmpty() }
                capture("3.png")
            }
            val icon = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            requireNotNull(app.getDrawable(R.drawable.ic_launcher)).apply { setBounds(0, 0, 512, 512); draw(Canvas(icon)) }
            File(output, "icon.png").outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
            icon.recycle()
        } finally {
            automation.dropShellPermissionIdentity()
            id?.let { host.deleteAppWidgetId(it); app.database.dao().deleteWidget(it) }
            events.forEach { app.database.dao().delete(it.id) }
            app.settings.theme(theme)
        }
    }
}
