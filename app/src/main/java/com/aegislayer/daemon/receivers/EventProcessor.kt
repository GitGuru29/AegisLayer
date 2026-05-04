package com.aegislayer.daemon.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.aegislayer.daemon.models.SystemEvent

class EventProcessor : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        
        when (action) {
            Intent.ACTION_BATTERY_CHANGED -> {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                 status == BatteryManager.BATTERY_STATUS_FULL
                                 
                val event = SystemEvent.BatteryState(level, isCharging)
                Log.d("AegisLayer", "EventProcessor: $event")
                // TODO: Forward event to ContextBuilder/RuleEngine
            }
            Intent.ACTION_SCREEN_ON -> {
                val event = SystemEvent.ScreenState(isOn = true)
                Log.d("AegisLayer", "EventProcessor: $event")
                // TODO: Forward
            }
            Intent.ACTION_SCREEN_OFF -> {
                val event = SystemEvent.ScreenState(isOn = false)
                Log.d("AegisLayer", "EventProcessor: $event")
                // TODO: Forward
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
            }
        }
    }
}
