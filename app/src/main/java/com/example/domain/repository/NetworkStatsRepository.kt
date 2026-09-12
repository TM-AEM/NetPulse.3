package com.example.domain.repository

import com.example.model.DailyNetworkUsage
import com.example.model.DateRange
import com.example.model.NetworkStatsDebugInfo
import com.example.model.NetworkType
import com.example.model.NetworkUsageSummary
import java.time.LocalDate

class LargeDateRangeBreakdownUnavailableException(message: String) : Exception(message)

interface NetworkStatsRepository {
    fun hasUsageStatsPermission(): Boolean
    suspend fun getUsageForRange(dateRange: DateRange): Result<NetworkUsageSummary>
    suspend fun getDailyUsageBreakdown(
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<List<DailyNetworkUsage>>
    suspend fun getDebugInfo(
        networkType: NetworkType,
        startTimeMs: Long,
        endTimeMs: Long
    ): Result<NetworkStatsDebugInfo>
}
