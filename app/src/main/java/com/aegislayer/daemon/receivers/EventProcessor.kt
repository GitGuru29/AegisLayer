package com.aegislayer.daemon.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.aegislayer.daemon.models.SystemEvent

class EventProcessor : BroadcastReceiver() {

    // Cache last battery state to avoid flooding on sticky ACTION_BATTERY_CHANGED
    private var lastBatteryLevel: Int = -1
    private var lastIsCharging: Boolean? = null

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        
        when (action) {
            Intent.ACTION_BATTERY_CHANGED -> {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                 status == BatteryManager.BATTERY_STATUS_FULL

                // Only dispatch if something actually changed
                if (level == lastBatteryLevel && isCharging == lastIsCharging) return
                lastBatteryLevel = level
                lastIsCharging = isCharging

                val event = SystemEvent.BatteryState(level, isCharging)
                Log.d("AegisLayer", "EventProcessor: $event")
                com.aegislayer.daemon.engine.EventDispatcher.dispatch(event)
            }
            Intent.ACTION_SCREEN_ON -> {
                val event = SystemEvent.ScreenState(isOn = true)
                Log.d("AegisLayer", "EventProcessor: $event")
                com.aegislayer.daemon.engine.EventDispatcher.dispatch(event)
            }
            Intent.ACTION_SCREEN_OFF -> {
                val event = SystemEvent.ScreenState(isOn = false)
                Log.d("AegisLayer", "EventProcessor: $event")
                com.aegislayer.daemon.engine.EventDispatcher.dispatch(event)
            }
            android.net.wifi.WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                val wifiManager = context?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                val info = wifiManager?.connectionInfo
                val isConnected = info?.networkId != -1
                val event = SystemEvent.WifiState(info?.ssid, isConnected)
                Log.d("AegisLayer", "EventProcessor: $event")
                com.aegislayer.daemon.engine.EventDispatcher.dispatch(event)
            }
            else -> Log.d("AegisLayer", "EventProcessor received unknown action: $action")
        }
    }

    companion object {
        fun getIntentFilter(): IntentFilter {
            return IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(android.net.wifi.WifiManager.NETWORK_STATE_CHANGED_ACTION)
            }
        }
    }
}
