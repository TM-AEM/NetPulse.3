package com.example.data.source

import com.example.model.NetworkStatsDebugInfo
import com.example.model.NetworkType
import com.example.model.NetworkUsage

interface NetworkStatsDataSource {
    fun hasUsageStatsPermission(): Boolean
    suspend fun getUsageForNetwork(
        networkType: NetworkType,
        startTimeMs: Long,
        endTimeMs: Long
    ): NetworkUsage
    suspend fun getDebugInfo(
        networkType: NetworkType,
        startTimeMs: Long,
        endTimeMs: Long
    ): NetworkStatsDebugInfo
}
