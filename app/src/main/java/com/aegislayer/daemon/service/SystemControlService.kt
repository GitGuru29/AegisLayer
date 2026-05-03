package com.aegislayer.daemon.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class SystemControlService : Service() {

    override fun onCreate() {
        super.onCreate()
        Log.d("AegisLayer", "SystemControlService: onCreate")
        // Initialize Core Engine here
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("AegisLayer", "SystemControlService: onStartCommand")
        // Keep service running
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // We don't provide binding
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("AegisLayer", "SystemControlService: onDestroy")
    }
}
