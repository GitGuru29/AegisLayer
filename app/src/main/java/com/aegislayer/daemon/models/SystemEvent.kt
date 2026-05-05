package com.aegislayer.daemon.models

/**
 * All the different types of "something just happened on the phone" events.
 *
 * Each variant carries the relevant data for that event type.
 * These flow through the EventDispatcher and get consumed by the
 * ContextBuilder to build up the current device state snapshot.
 */
sealed class SystemEvent {
    /** A new app just came to the foreground (user switched apps) */
    data class AppForeground(val packageName: String, val timestamp: Long) : SystemEvent()

    /** Battery level or charging status changed */
    data class BatteryState(val level: Int, val isCharging: Boolean) : SystemEvent()

    /** Screen turned on or off */
    data class ScreenState(val isOn: Boolean) : SystemEvent()

    /** A notification just appeared from another app */
    data class NotificationPosted(val packageName: String, val title: String?) : SystemEvent()

    /** WiFi connection state changed */
    data class WifiState(val ssid: String?, val isConnected: Boolean) : SystemEvent()
}
