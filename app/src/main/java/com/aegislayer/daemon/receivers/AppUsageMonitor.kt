package com.aegislayer.daemon.receivers

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.aegislayer.daemon.models.SystemEvent
import java.util.Timer
import kotlin.concurrent.timerTask

/**
 * The daemon's eyes on the screen — watches which app the user is actively using.
 *
 * Android doesn't broadcast an event when the foreground app changes (for privacy reasons),
 * so we have to poll the UsageStatsManager every second to see what's on top.
 *
 * Requires the special PACKAGE_USAGE_STATS permission to work.
 */
class AppUsageMonitor(private val context: Context) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private var currentForegroundPackage = ""
    private var timer: Timer? = null
    private var heartbeatCount = 0

    /**
     * Starts the polling loop.
     * We check the foreground app every 1 second.
     * Every 10 seconds, we emit a heartbeat log so developers know the daemon is still alive.
     */
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

    /**
     * Queries Android's UsageStatsManager to see which app was last resumed (brought to the front).
     * We look at the last 10 seconds of usage events to find the most recent one.
     */
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

        // If the app changed since our last check, broadcast an event
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
