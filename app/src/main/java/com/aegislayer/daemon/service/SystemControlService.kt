package com.aegislayer.daemon.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.aegislayer.daemon.engine.SelfLearningEngine
import com.aegislayer.daemon.engine.UsagePatternLogger
import com.aegislayer.daemon.receivers.AppUsageMonitor
import com.aegislayer.daemon.receivers.EventProcessor
import com.aegislayer.daemon.trace.TraceEngine
import com.aegislayer.daemon.trace.TraceLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The main daemon — AegisLayer's persistent background service.
 *
 * This is the backbone of the entire app. It runs as a Foreground Service
 * (meaning Android won't kill it) and orchestrates the full pipeline:
 *
 *   System Events → Context Snapshot → Rule Evaluation → Action Execution
 *
 * It also feeds the self-learning ML pipeline by recording every rule
 * that fires as a usage pattern, and keeps the SelfLearningEngine running
 * so the model can retrain itself during idle periods.
 *
 * Lifecycle:
 * - Starts on boot (via BootReceiver) or manually from the UI
 * - Runs indefinitely until the user hits HALT or force-stops the app
 * - Shows a persistent notification so the user knows it's active
 */
class SystemControlService : Service() {

    // Monitors which app is currently in the foreground (polls every second)
    private lateinit var appUsageMonitor: AppUsageMonitor

    // Listens for system broadcasts: battery changes, screen on/off, WiFi state
    private val eventProcessor = EventProcessor()

    private val channelId = "AegisLayerServiceChannel"
    private val notificationId = 1

    // Builds up a picture of "what's happening right now" from all incoming events
    private val contextBuilder = ContextBuilder()

    // Checks the current context against all loaded rules to see if anything triggers
    private val ruleEngine = RuleEngine()

    // Actually does things to the phone: changes volume, brightness, DND, etc.
    private lateinit var actionExecutor: ActionExecutor

    // The background ML retrainer — learns from usage patterns at night or while charging
    private lateinit var selfLearningEngine: SelfLearningEngine

    // Coroutine plumbing — lets us run async work without blocking the main thread
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        // Intent action to stop the service from the notification's HALT button
        const val ACTION_HALT = "com.aegislayer.daemon.ACTION_HALT"

        // Intent action sent by RuleManagerActivity when the user adds/deletes a rule
        const val ACTION_RELOAD_RULES = "com.aegislayer.daemon.ACTION_RELOAD_RULES"
    }

    /**
     * Listens for "reload rules" broadcasts from the UI.
     * When the user adds or deletes a rule in the RuleManager, we get a poke
     * here to re-read the rules file without restarting the whole service.
     */
    private val reloadReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == ACTION_RELOAD_RULES) {
                reloadRules()
            }
        }
    }

    /**
     * Service creation — this is where everything boots up.
     * Think of it as the daemon's "power on" sequence.
     */
    override fun onCreate() {
        super.onCreate()
        Log.d("AegisLayer", "SystemControlService: onCreate")
        
        // Step 1: Set up the persistent notification (required for foreground services)
        createNotificationChannel()
        startForeground(notificationId, createNotification("Monitoring system events..."))

        // Step 2: Start watching which app is in the foreground
        appUsageMonitor = AppUsageMonitor(this)
        appUsageMonitor.startMonitoring()
        
        // Step 3: Register broadcast receivers for battery, screen, WiFi events
        // On Android 13+ we need to explicitly mark receivers as not exported
        val filter = EventProcessor.getIntentFilter()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(eventProcessor, filter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(reloadReceiver, IntentFilter(ACTION_RELOAD_RULES), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(eventProcessor, filter)
            registerReceiver(reloadReceiver, IntentFilter(ACTION_RELOAD_RULES))
        }

        // Step 4: Initialize the action executor (needs context for system settings access)
        actionExecutor = ActionExecutor(this)

        // Step 5: Fire up the self-learning ML engine
        // It runs quietly in the background, retraining at night or while charging
        selfLearningEngine = SelfLearningEngine(this)
        selfLearningEngine.start()

        // Step 6: Set up logging and load all rules
        TraceEngine.init(this)
        TraceEngine.log(TraceLevel.INFO, "Service", "SystemControlService started (Self-Learning ML active)")
        reloadRules()

        // Step 7: Start the main event loop — this is where the magic happens
        // Every time a system event arrives, we:
        //   1. Update our understanding of the current state
        //   2. Check if any rules match the new state
        //   3. Execute the matched actions
        //   4. Record the event for the ML model to learn from
        serviceScope.launch {
            EventDispatcher.events.collect { event ->
                TraceEngine.log(TraceLevel.INFO, "Event", event.toString())

                // Update the "what's happening now" snapshot
                contextBuilder.updateState(event)
                val snapshot = contextBuilder.buildCurrentContext()

                // Check all rules: "does this new state trigger anything?"
                val actions = ruleEngine.evaluateContext(snapshot)
                if (actions.isNotEmpty()) {
                    TraceEngine.log(TraceLevel.RULE, "RuleEngine", "Triggered: $actions")

                    // Do the thing! (change brightness, mute, enable DND, etc.)
                    actionExecutor.execute(actions)
                    updateNotification("Active Rule: $actions")

                    // Save this moment for the ML model to study later
                    UsagePatternLogger.recordRuleFired(this@SystemControlService, snapshot, actions)
                }

                // If we just got a battery event, tell the learning engine
                // whether we're charging — it uses this to decide when to retrain
                if (event is com.aegislayer.daemon.models.SystemEvent.BatteryState) {
                    selfLearningEngine.onChargingStateChanged(event.isCharging)
                }
            }
        }
    }

    /**
     * Handles incoming intents while the service is running.
     * The main use case: the HALT button on the notification sends an intent here.
     *
     * START_STICKY means Android will try to restart us if we get killed.
     * START_NOT_STICKY (on halt) means "I'm done, don't bring me back."
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HALT) {
            TraceEngine.log(TraceLevel.INFO, "Service", "HALT received from notification")
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    // We don't support binding — this is a started service, not a bound one
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * Clean shutdown — unregister everything so we don't leak receivers or threads.
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.d("AegisLayer", "SystemControlService: onDestroy")
        serviceJob.cancel()                   // Stop the event collection coroutine
        selfLearningEngine.stop()             // Stop the ML retraining scheduler
        appUsageMonitor.stopMonitoring()      // Stop polling foreground apps
        unregisterReceiver(eventProcessor)    // Stop listening for system broadcasts
        unregisterReceiver(reloadReceiver)    // Stop listening for rule reload requests
    }

    /**
     * Re-reads all rules (both from assets/rules.json and user-created ones)
     * and loads them into the rule engine. Called on startup and whenever
     * the user modifies rules in the UI.
     */
    private fun reloadRules() {
        val repository = RuleRepository(this)
        val rules = repository.getAllRules()
        ruleEngine.loadRules(rules)
        TraceEngine.log(TraceLevel.INFO, "RuleLoader", "Service reloaded ${rules.size} total rules")
    }

    /**
     * Creates the notification channel required by Android 8+ for foreground services.
     * We use IMPORTANCE_LOW so the notification doesn't make sounds.
     */
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

    /**
     * Builds the persistent notification shown while the daemon is running.
     * Includes a custom layout with a HALT button to stop the service.
     */
    private fun createNotification(statusText: String): Notification {
        // Set up the HALT button — tapping it sends an intent back to this service
        val haltIntent = Intent(this, SystemControlService::class.java).apply {
            action = ACTION_HALT
        }
        val haltPendingIntent = android.app.PendingIntent.getService(
            this, 0, haltIntent, 
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Use our custom notification layout with the status text and HALT button
        val remoteViews = android.widget.RemoteViews(packageName, R.layout.notification_custom)
        remoteViews.setTextViewText(R.id.notifStatus, statusText)
        remoteViews.setOnClickPendingIntent(R.id.btnHalt, haltPendingIntent)

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_secure)
            .setCustomContentView(remoteViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)  // Can't be swiped away
            .build()
    }

    /**
     * Updates the notification text in real-time to show what rule just fired.
     * Gives the user a quick glance at what the daemon is doing without opening the app.
     */
    private fun updateNotification(statusText: String) {
        val notification = createNotification(statusText)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
    }
}
