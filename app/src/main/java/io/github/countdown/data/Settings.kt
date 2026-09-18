package io.github.countdown.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.preferences by preferencesDataStore(name = "settings")
enum class ThemeMode { SYSTEM, LIGHT, DARK }
class Settings(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    val theme = context.preferences.data.map { data -> ThemeMode.entries.firstOrNull { it.name == data[themeKey] } ?: ThemeMode.SYSTEM }
    suspend fun theme(value: ThemeMode) { context.preferences.edit { it[themeKey] = value.name } }
}
