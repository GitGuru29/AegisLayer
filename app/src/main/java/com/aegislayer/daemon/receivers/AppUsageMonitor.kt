package com.aegislayer.daemon.receivers

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.aegislayer.daemon.models.SystemEvent
import java.util.Timer
import kotlin.concurrent.timerTask

class AppUsageMonitor(private val context: Context) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private var currentForegroundPackage = ""
    private var timer: Timer? = null

    fun startMonitoring() {
        Log.d("AegisLayer", "AppUsageMonitor started")
        // Poll every 2 seconds for foreground app changes
        timer = Timer()
        timer?.scheduleAtFixedRate(timerTask {
            checkForegroundApp()
        }, 0, 2000)
    }

    fun stopMonitoring() {
        Log.d("AegisLayer", "AppUsageMonitor stopped")
        timer?.cancel()
        timer = null
    }

    private fun checkForegroundApp() {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 10000 // Look back 10 seconds

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var lastPackage = currentForegroundPackage

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastPackage = event.packageName
            }
        }

        if (lastPackage != currentForegroundPackage && lastPackage.isNotEmpty()) {
            currentForegroundPackage = lastPackage
            val systemEvent = SystemEvent.AppForeground(currentForegroundPackage, System.currentTimeMillis())
            Log.d("AegisLayer", "AppUsageMonitor: $systemEvent")
            // TODO: Forward event to Rule Engine
        }
    }
}
