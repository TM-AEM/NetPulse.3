package com.example.data.repository

import com.example.data.source.NetworkStatsDataSource
import com.example.domain.repository.LargeDateRangeBreakdownUnavailableException
import com.example.domain.repository.NetworkStatsRepository
import com.example.model.DailyNetworkUsage
import com.example.model.DateRange
import com.example.model.NetworkStatsDebugInfo
import com.example.model.NetworkType
import com.example.model.NetworkUsageSummary
import com.example.util.AppLogger
import com.example.util.DateTimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.LocalDate

class NetworkStatsRepositoryImpl(
    private val dataSource: NetworkStatsDataSource
) : NetworkStatsRepository {

    companion object {
        const val MAX_DAILY_BREAKDOWN_DAYS = 60
    }

    private val tag = "NetworkStatsRepositoryImpl"

    override fun hasUsageStatsPermission(): Boolean {
        return dataSource.hasUsageStatsPermission()
    }

    override suspend fun getUsageForRange(dateRange: DateRange): Result<NetworkUsageSummary> =
        withContext(Dispatchers.IO) {
            try {
                val startMs = dateRange.getStartEpochMs()
                val endMs = dateRange.getEndEpochMs()
                val summary = querySummaryInternal(startMs, endMs, dateRange)
                Result.success(summary)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(tag, "Failed to get usage for range ${dateRange.startDate} -> ${dateRange.endDate}", e)
                Result.failure(e)
            }
        }

    private suspend fun querySummaryInternal(
        startMs: Long,
        endMs: Long,
        dateRange: DateRange
    ): NetworkUsageSummary = coroutineScope {
        val wifiDeferred = async {
            dataSource.getUsageForNetwork(NetworkType.WIFI, startMs, endMs)
        }
        val mobileDeferred = async {
            dataSource.getUsageForNetwork(NetworkType.MOBILE, startMs, endMs)
        }

        val wifiUsage = wifiDeferred.await()
        val mobileUsage = mobileDeferred.await()
        val totalUsage = wifiUsage + mobileUsage

        NetworkUsageSummary(
            wifi = wifiUsage,
            mobile = mobileUsage,
            total = totalUsage,
            dateRange = dateRange
        )
    }

    override suspend fun getDailyUsageBreakdown(
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<List<DailyNetworkUsage>> = withContext(Dispatchers.IO) {
        try {
            val zoneId = DateTimeUtils.getLocalZoneId()
            val today = DateTimeUtils.today(zoneId)
            val safeStart = if (startDate.isAfter(today)) today else startDate
            val safeEnd = if (endDate.isAfter(today)) today else endDate

            if (safeStart.isAfter(safeEnd)) {
                return@withContext Result.failure(
                    IllegalArgumentException("startDate ($startDate) cannot be after endDate ($endDate)")
                )
            }

            val days = DateTimeUtils.generateDaysBetween(safeStart, safeEnd)

            // High performance strategy for large ranges:
            // Prevent launching hundreds/thousands of queries while preserving full Range Total.
            if (days.size > MAX_DAILY_BREAKDOWN_DAYS) {
                return@withContext Result.failure(
                    LargeDateRangeBreakdownUnavailableException(
                        "التفصيل اليومي متاح للفترات حتى $MAX_DAILY_BREAKDOWN_DAYS يوماً فقط لحماية أداء الجهاز."
                    )
                )
            }

            val resultList = mutableListOf<DailyNetworkUsage>()
            for (day in days) {
                val (dayStartMs, dayEndMs) = DateTimeUtils.getDayRange(day, zoneId)
                val (wifiUsage, mobileUsage) = coroutineScope {
                    val w = async { dataSource.getUsageForNetwork(NetworkType.WIFI, dayStartMs, dayEndMs) }
                    val m = async { dataSource.getUsageForNetwork(NetworkType.MOBILE, dayStartMs, dayEndMs) }
                    Pair(w.await(), m.await())
                }
                val totalUsage = wifiUsage + mobileUsage
                resultList.add(
                    DailyNetworkUsage(
                        date = day,
                        wifi = wifiUsage,
                        mobile = mobileUsage,
                        total = totalUsage
                    )
                )
            }
            Result.success(resultList.sortedByDescending { it.date })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(tag, "Failed to get daily usage breakdown from $startDate to $endDate", e)
            Result.failure(e)
        }
    }

    override suspend fun getDebugInfo(
        networkType: NetworkType,
        startTimeMs: Long,
        endTimeMs: Long
    ): Result<NetworkStatsDebugInfo> = withContext(Dispatchers.IO) {
        try {
            val info = dataSource.getDebugInfo(networkType, startTimeMs, endTimeMs)
            Result.success(info)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(tag, "Failed to get debug info", e)
            Result.failure(e)
        }
    }
}
