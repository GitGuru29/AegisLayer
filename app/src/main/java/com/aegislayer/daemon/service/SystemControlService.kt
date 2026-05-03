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
import com.aegislayer.daemon.receivers.AppUsageMonitor
import com.aegislayer.daemon.receivers.EventProcessor

class SystemControlService : Service() {

    private lateinit var appUsageMonitor: AppUsageMonitor
    private val eventProcessor = EventProcessor()
    private val channelId = "AegisLayerServiceChannel"

    override fun onCreate() {
        super.onCreate()
        Log.d("AegisLayer", "SystemControlService: onCreate")
        
        createNotificationChannel()
        startForeground(1, createNotification())

        appUsageMonitor = AppUsageMonitor(this)
        appUsageMonitor.startMonitoring()
        
        registerReceiver(eventProcessor, EventProcessor.getIntentFilter())
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
