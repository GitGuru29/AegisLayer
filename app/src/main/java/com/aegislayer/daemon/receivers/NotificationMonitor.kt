package com.aegislayer.daemon.receivers

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.aegislayer.daemon.models.SystemEvent

class NotificationMonitor : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("AegisLayer", "NotificationMonitor: Listener Connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let {
            val packageName = it.packageName
            val title = it.notification.extras.getString("android.title")
            
            val event = SystemEvent.NotificationPosted(packageName, title)
            Log.d("AegisLayer", "NotificationMonitor: $event")
            
            com.aegislayer.daemon.engine.EventDispatcher.dispatch(event)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Optional: Handle notification removed
    }
}
