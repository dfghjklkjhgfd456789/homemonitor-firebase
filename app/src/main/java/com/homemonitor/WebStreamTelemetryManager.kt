package com.google.android.system

import android.os.SystemClock
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class TelemetrySummary(
    val totalReceived: Long,
    val totalCompleted: Long,
    val totalCancelled: Long,
    val totalRejected: Long,
    val medianServerLatencyMs: Long,
    val p95ServerLatencyMs: Long,
    val completionRatePct: Float
)

/**
 * WebStreamTelemetryManager
 *
 * Server-side monotonic time telemetry tracker measuring gesture stage durations,
 * completion rates, and dispatch latencies.
 */
object WebStreamTelemetryManager {

    private val totalReceived = AtomicLong(0)
    private val totalCompleted = AtomicLong(0)
    private val totalCancelled = AtomicLong(0)
    private val totalRejected = AtomicLong(0)

    private val requestStartTimes = ConcurrentHashMap<String, Long>()
    private val serverLatencies = ConcurrentHashMap.newKeySet<Long>()

    fun recordRequestReceived(requestId: String) {
        totalReceived.incrementAndGet()
        if (requestId.isNotEmpty()) {
            requestStartTimes[requestId] = SystemClock.elapsedRealtime()
        }
    }

    fun recordRequestCompleted(requestId: String) {
        totalCompleted.incrementAndGet()
        val start = requestStartTimes.remove(requestId)
        if (start != null) {
            val elapsed = SystemClock.elapsedRealtime() - start
            if (serverLatencies.size < 1000) {
                serverLatencies.add(elapsed)
            }
        }
    }

    fun recordRequestCancelled(requestId: String) {
        totalCancelled.incrementAndGet()
        requestStartTimes.remove(requestId)
    }

    fun recordRequestRejected(requestId: String) {
        totalRejected.incrementAndGet()
        requestStartTimes.remove(requestId)
    }

    fun getSummary(): TelemetrySummary {
        val rec = totalReceived.get()
        val comp = totalCompleted.get()
        val canc = totalCancelled.get()
        val rej = totalRejected.get()

        val rate = if (rec > 0) (comp.toFloat() / rec.toFloat()) * 100f else 100f

        val list = serverLatencies.toList().sorted()
        val median = if (list.isNotEmpty()) list[list.size / 2] else 0L
        val p95 = if (list.isNotEmpty()) list[(list.size * 0.95).toInt().coerceAtMost(list.size - 1)] else 0L

        return TelemetrySummary(
            totalReceived = rec,
            totalCompleted = comp,
            totalCancelled = canc,
            totalRejected = rej,
            medianServerLatencyMs = median,
            p95ServerLatencyMs = p95,
            completionRatePct = rate
        )
    }

    fun reset() {
        totalReceived.set(0)
        totalCompleted.set(0)
        totalCancelled.set(0)
        totalRejected.set(0)
        requestStartTimes.clear()
        serverLatencies.clear()
    }
}
