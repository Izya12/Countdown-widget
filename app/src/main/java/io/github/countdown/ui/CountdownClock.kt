package io.github.countdown.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.countdown.domain.*
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** One deadline waiter for this visible screen, stopped with its lifecycle. */
@Composable fun countdownNow(events: List<CountdownEvent>, clock: Clock): Instant {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var now by remember { mutableStateOf(clock.instant()) }
    var generation by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { generation++ }
        }
        val filter = IntentFilter().apply { addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_DATE_CHANGED) }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(events, lifecycle, generation) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            do {
                now = clock.instant()
                val next = events.mapNotNull { CountdownEngine.calculate(it, now).nextChange }.minOrNull()
                if (next == null) return@repeatOnLifecycle
                delay(Duration.between(now, next).toMillis().coerceAtLeast(50))
            } while (isActive)
        }
    }
    return now
}
