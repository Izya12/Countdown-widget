package io.github.countdown

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import io.github.countdown.ui.*

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        io.github.countdown.reminders.ReminderScheduler.request(this)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: EventsViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = EventsViewModel(application as CountdownApp) as T
            })
            val theme by vm.theme.collectAsStateWithLifecycle()
            CountdownTheme(theme) { AppScreens(vm, intent.getStringExtra("eventId")) }
        }
    }
}
