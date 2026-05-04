package com.aegislayer.daemon.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aegislayer.daemon.actions.ActionExecutor
import com.aegislayer.daemon.engine.ContextBuilder
import com.aegislayer.daemon.engine.EventDispatcher
import com.aegislayer.daemon.engine.RuleEngine
import com.aegislayer.daemon.engine.RuleLoader
import com.aegislayer.daemon.receivers.AppUsageMonitor
import com.aegislayer.daemon.receivers.EventProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SystemControlService : Service() {

    private lateinit var appUsageMonitor: AppUsageMonitor
    private val eventProcessor = EventProcessor()
    private val channelId = "AegisLayerServiceChannel"

    private val contextBuilder = ContextBuilder()
    private val ruleEngine = RuleEngine()
    private lateinit var actionExecutor: ActionExecutor
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onCreate() {
        super.onCreate()
        Log.d("AegisLayer", "SystemControlService: onCreate")
        
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, createNotification())
        }

        appUsageMonitor = AppUsageMonitor(this)
        appUsageMonitor.startMonitoring()
        
        val filter = EventProcessor.getIntentFilter()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(eventProcessor, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(eventProcessor, filter)
        }

        actionExecutor = ActionExecutor(this)

        // Load rules from assets/rules.json
        val rules = RuleLoader.loadFromAssets(this)
        ruleEngine.loadRules(rules)

        serviceScope.launch {
            EventDispatcher.events.collect { event ->
                Log.d("AegisLayer", "Service Received: $event")
                contextBuilder.updateState(event)
                val snapshot = contextBuilder.buildCurrentContext()
                val actions = ruleEngine.evaluateContext(snapshot)
                if (actions.isNotEmpty()) {
                    Log.d("AegisLayer", "RuleEngine Triggered Actions: $actions")
                    actionExecutor.execute(actions)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("AegisLayer", "SystemControlService: onStartCommand")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("AegisLayer", "SystemControlService: onDestroy")
        serviceJob.cancel()
        appUsageMonitor.stopMonitoring()
        unregisterReceiver(eventProcessor)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId,
                "AegisLayer Foreground Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("AegisLayer Active")
            .setContentText("System Control Daemon is running")
            .setSmallIcon(android.R.drawable.ic_secure)
            .build()
    }
}
