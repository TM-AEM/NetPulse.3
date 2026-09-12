package com.example.data.source

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build
import android.os.Process
import com.example.model.DiscrepancyReason
import com.example.model.NetworkStatsDebugInfo
import com.example.model.NetworkType
import com.example.model.NetworkUsage
import com.example.model.RawBucketDetail
import com.example.util.AppLogger
import com.example.util.DateTimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NetworkStatsDataSourceImpl(
    private val context: Context
) : NetworkStatsDataSource {
    private val tag = "NetworkStatsDataSourceImpl"
    private val networkStatsManager: NetworkStatsManager? =
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager

    override fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override suspend fun getUsageForNetwork(
        networkType: NetworkType,
        startTimeMs: Long,
        endTimeMs: Long
    ): NetworkUsage = withContext(Dispatchers.IO) {
        val manager = networkStatsManager
            ?: throw IllegalStateException("NetworkStatsManager is not available on this device")
        if (!hasUsageStatsPermission()) {
            throw SecurityException("PACKAGE_USAGE_STATS permission is not granted")
        }

        val networkTypeInt = when (networkType) {
            NetworkType.WIFI -> ConnectivityManager.TYPE_WIFI
            NetworkType.MOBILE -> ConnectivityManager.TYPE_MOBILE
            NetworkType.TOTAL -> throw IllegalArgumentException("Query individual types to aggregate TOTAL")
        }

        try {
            val bucket = manager.querySummaryForDevice(networkTypeInt, null, startTimeMs, endTimeMs)
            NetworkUsage(
                downloadBytes = bucket.rxBytes,
                uploadBytes = bucket.txBytes,
                startTime = startTimeMs,
                endTime = endTimeMs,
                networkType = networkType
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            AppLogger.e(tag, "SecurityException while querying network stats", e)
            throw e
        } catch (e: Exception) {
            AppLogger.e(tag, "Exception querying NetworkStatsManager for $networkType", e)
            throw e
        }
    }

    override suspend fun getDebugInfo(
        networkType: NetworkType,
        startTimeMs: Long,
        endTimeMs: Long
    ): NetworkStatsDebugInfo = withContext(Dispatchers.IO) {
        val manager = networkStatsManager
            ?: throw IllegalStateException("NetworkStatsManager is not available")

        var rxTotal = 0L
        var txTotal = 0L
        try {
            val wifiBucket = manager.querySummaryForDevice(ConnectivityManager.TYPE_WIFI, null, startTimeMs, endTimeMs)
            val mobileBucket = manager.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, startTimeMs, endTimeMs)
            rxTotal = wifiBucket.rxBytes + mobileBucket.rxBytes
            txTotal = wifiBucket.txBytes + mobileBucket.txBytes
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(tag, "Could not query device summary for debug info", e)
        }

        val detailedBuckets = mutableListOf<RawBucketDetail>()
        try {
            val stats: NetworkStats? = manager.queryDetails(
                ConnectivityManager.TYPE_WIFI,
                null,
                startTimeMs,
                endTimeMs
            )
            stats?.use { s ->
                val bucket = NetworkStats.Bucket()
                var count = 0
                while (s.hasNextBucket() && count < 30) {
                    s.getNextBucket(bucket)
                    detailedBuckets.add(
                        RawBucketDetail(
                            uid = bucket.uid,
                            state = if (bucket.state == NetworkStats.Bucket.STATE_FOREGROUND) "FOREGROUND" else "DEFAULT",
                            rxBytes = bucket.rxBytes,
                            txBytes = bucket.txBytes,
                            rxPackets = bucket.rxPackets,
                            txPackets = bucket.txPackets,
                            startTime = bucket.startTimeStamp,
                            endTime = bucket.endTimeStamp
                        )
                    )
                    count++
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(tag, "Could not query detailed buckets (developer diagnostics only)", e)
        }

        val bootTotal = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()

        val discrepancyReasons = listOf(
            DiscrepancyReason(
                title = "دورة الفاتورة (Billing Cycle)",
                description = "قد تحتسب شركة الاتصالات البيانات بناءً على تواريخ تجديد مختلفة لباقة الجوال."
            ),
            DiscrepancyReason(
                title = "تأخير تفريغ الكيرنل (Kernel Flush / Poll Interval)",
                description = "يقوم الكيرنل (netfilter) بتجميع الحزم وتفريغها إلى NetworkStatsManager كل بضع دقائق لتوفير البطارية."
            ),
            DiscrepancyReason(
                title = "استهلاك النظام ونقطة الاتصال (System UID / Tethering)",
                description = "استهلاك نقطة الاتصال (Hotspot) وتحديثات النظام قد تُسجل تحت UIDs منفصلة."
            )
        )

        NetworkStatsDebugInfo(
            queryStartMs = startTimeMs,
            queryEndMs = endTimeMs,
            queryStartFormatted = DateTimeUtils.formatEpochTime(startTimeMs),
            queryEndFormatted = DateTimeUtils.formatEpochTime(endTimeMs),
            rawRxBytes = rxTotal,
            rawTxBytes = txTotal,
            rawTotalBytes = rxTotal + txTotal,
            rawRxPackets = 0L,
            rawTxPackets = 0L,
            trafficStatsBootTotalBytes = bootTotal,
            trafficStatsComparisonNote = "ملاحظة: TrafficStats تقيس الإجمالي منذ إقلاع الجهاز فقط، بينما NetworkStatsManager يدعم الفترات التاريخية بدقة.",
            detailedBuckets = detailedBuckets,
            discrepancyAnalysis = discrepancyReasons
        )
    }
}
