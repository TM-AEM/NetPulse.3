package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeveloperPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("netpulse_developer_prefs", Context.MODE_PRIVATE)

    private val _isDeveloperModeEnabled = MutableStateFlow(isDeveloperModeEnabled())
    val isDeveloperModeEnabled: StateFlow<Boolean> = _isDeveloperModeEnabled.asStateFlow()

    fun isDeveloperModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_DEV_MODE, false)
    }

    fun setDeveloperModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEV_MODE, enabled).apply()
        _isDeveloperModeEnabled.value = enabled
    }

    companion object {
        private const val KEY_DEV_MODE = "developer_mode_diagnostics"
    }
}
