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
    private var heartbeatCount = 0

    fun startMonitoring() {
        Log.d("AegisLayer", "AppUsageMonitor started")
        // Poll every 1 second for foreground app changes
        timer = Timer()
        timer?.scheduleAtFixedRate(timerTask {
            heartbeatCount++
            if (heartbeatCount >= 10) {
                heartbeatCount = 0
                com.aegislayer.daemon.trace.TraceEngine.log(
                    com.aegislayer.daemon.trace.TraceLevel.INFO,
                    "Daemon",
                    "System Monitor Heartbeat — Service Active"
                )
            }
            checkForegroundApp()
        }, 0, 1000)
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
            
            // Log to both system log and our internal TraceEngine
            Log.d("AegisLayer", "AppUsageMonitor: $systemEvent")
            com.aegislayer.daemon.trace.TraceEngine.log(
                com.aegislayer.daemon.trace.TraceLevel.INFO, 
                "Monitor", 
                "Foreground App: $currentForegroundPackage"
            )
            
            com.aegislayer.daemon.engine.EventDispatcher.dispatch(systemEvent)
        }
    }
}
