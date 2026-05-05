package com.aegislayer.daemon.models

sealed class SystemEvent {
    data class AppForeground(val packageName: String, val timestamp: Long) : SystemEvent()
    data class BatteryState(val level: Int, val isCharging: Boolean) : SystemEvent()
    data class ScreenState(val isOn: Boolean) : SystemEvent()
    data class NotificationPosted(val packageName: String, val title: String?) : SystemEvent()
    data class WifiState(val ssid: String?, val isConnected: Boolean) : SystemEvent()
}
