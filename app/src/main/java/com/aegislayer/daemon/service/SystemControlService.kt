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
import com.aegislayer.daemon.R
import com.aegislayer.daemon.actions.ActionExecutor
import com.aegislayer.daemon.engine.ContextBuilder
import com.aegislayer.daemon.engine.EventDispatcher
import com.aegislayer.daemon.engine.RuleEngine
import com.aegislayer.daemon.engine.RuleLoader
import com.aegislayer.daemon.engine.RuleRepository
import com.aegislayer.daemon.receivers.AppUsageMonitor
import com.aegislayer.daemon.receivers.EventProcessor
import com.aegislayer.daemon.trace.TraceEngine
import com.aegislayer.daemon.trace.TraceLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SystemControlService : Service() {

    private lateinit var appUsageMonitor: AppUsageMonitor
    private val eventProcessor = EventProcessor()
    private val channelId = "AegisLayerServiceChannel"
    private val notificationId = 1

    private val contextBuilder = ContextBuilder()
    private val ruleEngine = RuleEngine()
    private lateinit var actionExecutor: ActionExecutor
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        const val ACTION_HALT = "com.aegislayer.daemon.ACTION_HALT"
        const val ACTION_RELOAD_RULES = "com.aegislayer.daemon.ACTION_RELOAD_RULES"
    }

    private val reloadReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == ACTION_RELOAD_RULES) {
                reloadRules()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("AegisLayer", "SystemControlService: onCreate")
        
        createNotificationChannel()
        startForeground(notificationId, createNotification("Monitoring system events..."))

        appUsageMonitor = AppUsageMonitor(this)
        appUsageMonitor.startMonitoring()
        
        val filter = EventProcessor.getIntentFilter()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(eventProcessor, filter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(reloadReceiver, IntentFilter(ACTION_RELOAD_RULES), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(eventProcessor, filter)
            registerReceiver(reloadReceiver, IntentFilter(ACTION_RELOAD_RULES))
        }

        actionExecutor = ActionExecutor(this)

        TraceEngine.init(this)
        TraceEngine.log(TraceLevel.INFO, "Service", "SystemControlService started")

        reloadRules()

        serviceScope.launch {
            EventDispatcher.events.collect { event ->
                TraceEngine.log(TraceLevel.INFO, "Event", event.toString())
                contextBuilder.updateState(event)
                val snapshot = contextBuilder.buildCurrentContext()
                val actions = ruleEngine.evaluateContext(snapshot)
                if (actions.isNotEmpty()) {
                    TraceEngine.log(TraceLevel.RULE, "RuleEngine", "Triggered: $actions")
                    actionExecutor.execute(actions)
                    updateNotification("Active Rule: $actions")
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HALT) {
            TraceEngine.log(TraceLevel.INFO, "Service", "HALT received from notification")
            stopSelf()
            return START_NOT_STICKY
        }
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
        unregisterReceiver(reloadReceiver)
    }

    private fun reloadRules() {
        val repository = RuleRepository(this)
        val rules = repository.getAllRules()
        ruleEngine.loadRules(rules)
        TraceEngine.log(TraceLevel.INFO, "RuleLoader", "Service reloaded ${rules.size} total rules")
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

    private fun createNotification(statusText: String): Notification {
        val haltIntent = Intent(this, SystemControlService::class.java).apply {
            action = ACTION_HALT
        }
        val haltPendingIntent = android.app.PendingIntent.getService(
            this, 0, haltIntent, 
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val remoteViews = android.widget.RemoteViews(packageName, R.layout.notification_custom)
        remoteViews.setTextViewText(R.id.notifStatus, statusText)
        remoteViews.setOnClickPendingIntent(R.id.btnHalt, haltPendingIntent)

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_secure)
            .setCustomContentView(remoteViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val notification = createNotification(statusText)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
    }
}
