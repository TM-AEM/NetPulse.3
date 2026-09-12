package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.preferences.AppSettingsPreferences
import com.example.data.preferences.DateRangePreferences
import com.example.data.preferences.DeveloperPreferences
import com.example.data.repository.NetworkStatsRepositoryImpl
import com.example.data.source.NetworkStatsDataSourceImpl
import com.example.util.ConnectivityObserver

class NetworkUsageViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NetworkUsageViewModel::class.java)) {
            val dataSource = NetworkStatsDataSourceImpl(context.applicationContext)
            val repository = NetworkStatsRepositoryImpl(dataSource)
            val dateRangePrefs = DateRangePreferences(context.applicationContext)
            val appSettingsPrefs = AppSettingsPreferences(context.applicationContext)
            val devPrefs = DeveloperPreferences(context.applicationContext)
            val connectivity = ConnectivityObserver(context.applicationContext)

            return NetworkUsageViewModel(
                repository = repository,
                dateRangePreferences = dateRangePrefs,
                appSettingsPreferences = appSettingsPrefs,
                developerPreferences = devPrefs,
                connectivityObserver = connectivity
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
