package com.aegislayer.daemon.engine

import com.aegislayer.daemon.models.SystemEvent

class ContextBuilder {

    private val state = mutableMapOf<String, Any>()

    fun updateState(event: SystemEvent) {
        when (event) {
            is SystemEvent.AppForeground -> {
                state["APP_FOREGROUND"] = event.packageName
            }
            is SystemEvent.BatteryState -> {
                state["BATTERY_LEVEL"] = event.level
                state["IS_CHARGING"] = event.isCharging
            }
            is SystemEvent.ScreenState -> {
                state["SCREEN_ON"] = event.isOn
            }
            is SystemEvent.NotificationPosted -> {
                state["LAST_NOTIFICATION_PKG"] = event.packageName
            }
            is SystemEvent.WifiState -> {
                state["WIFI_CONNECTED"] = event.isConnected
                state["WIFI_SSID"] = event.ssid ?: ""
            }
        }
    }

    fun buildCurrentContext(): Map<String, Any> {
        return state.toMap()
    }
}
