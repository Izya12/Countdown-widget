package io.github.countdown.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.countdown.MainActivity
import io.github.countdown.CountdownApp
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Test fun createEventPersistsAndOpensDetails() {
        val title = "UI test ${System.nanoTime()}"
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("addEvent").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("addEvent").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("eventTitle").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("eventTitle").performTextInput(title)
        ui.onNodeWithTag("saveEvent").performClick()
        try {
            ui.waitUntil(10_000) { ui.onAllNodesWithTag("addEvent").fetchSemanticsNodes().isNotEmpty() && ui.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            throw AssertionError(ui.onRoot().printToString())
        }
        ui.onNodeWithText(title).performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("detailTitle").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("detailTitle").assertTextEquals(title)
        val dao = (ui.activity.application as CountdownApp).database.dao()
        if (android.os.Build.VERSION.SDK_INT >= 33) androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(ui.activity.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        ui.onNodeWithText(ui.activity.getString(io.github.countdown.R.string.add_reminder)).performScrollTo().performClick()
        ui.onNodeWithText(ui.activity.getString(io.github.countdown.R.string.save)).performClick()
        ui.waitUntil(10_000) { runBlocking { dao.reminders().any { rule -> dao.event(rule.eventId)?.title == title } } }
        if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("captureScreens") == "true") {
            ui.waitForIdle()
            val file = java.io.File(ui.activity.getExternalFilesDir(null), "reminders.png")
            file.outputStream().use { ui.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        runBlocking {
            val created = dao.events().filter { it.title == title }
            org.junit.Assert.assertEquals(1, created.size)
            created.forEach { dao.delete(it.id) }
        }
    }
    @Test fun invalidFormRemainsOpen() {
        ui.onNodeWithTag("addEvent").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("eventTitle").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("saveEvent").performClick()
        ui.onNodeWithTag("editorError").assertExists()
        ui.onNodeWithTag("eventTitle").assertExists()
    }
}
