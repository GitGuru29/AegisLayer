package com.aegislayer.daemon.engine

import com.aegislayer.daemon.models.SystemEvent

/**
 * The daemon's short-term memory.
 *
 * As events happen on the phone (screen turns off, WiFi disconnects, etc.),
 * this class collects them and builds a single, flat dictionary (map)
 * representing "what is happening right now."
 *
 * This snapshot is what the RuleEngine looks at to decide if any rules
 * should fire.
 */
class ContextBuilder {

    // Holds the current state of the device
    private val state = mutableMapOf<String, Any>()

    /**
     * Called every time a new event happens.
     * Updates our internal dictionary so it always reflects reality.
     */
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

    /**
     * Returns a read-only copy of the current state.
     * We return a copy so the RuleEngine can't accidentally change the state
     * while evaluating rules.
     */
    fun buildCurrentContext(): Map<String, Any> {
        return state.toMap()
    }
}
