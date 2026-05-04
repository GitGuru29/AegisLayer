package com.aegislayer.daemon.actions

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.util.Log

class ActionExecutor(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun execute(actions: List<String>) {
        actions.forEach { action ->
            Log.d("AegisLayer", "Executing action: $action")
            when (action) {
                "ENABLE_DND"  -> enableDnd()
                "DISABLE_DND" -> disableDnd()
                "BOOST_PERFORMANCE" -> boostPerformance()
                "BLOCK_NOTIFICATIONS" -> enableDnd()
                else -> Log.w("AegisLayer", "Unknown action: $action")
            }
        }
    }

    private fun enableDnd() {
        if (notificationManager.isNotificationPolicyAccessGranted) {
            notificationManager.setInterruptionFilter(
                NotificationManager.INTERRUPTION_FILTER_NONE
            )
            Log.d("AegisLayer", "ActionExecutor: DND ENABLED")
        } else {
            Log.w("AegisLayer", "ActionExecutor: MANAGE_NOTIFICATION_POLICY not granted — cannot enable DND")
        }
    }

    private fun disableDnd() {
        if (notificationManager.isNotificationPolicyAccessGranted) {
            notificationManager.setInterruptionFilter(
                NotificationManager.INTERRUPTION_FILTER_ALL
            )
            Log.d("AegisLayer", "ActionExecutor: DND DISABLED")
        } else {
            Log.w("AegisLayer", "ActionExecutor: MANAGE_NOTIFICATION_POLICY not granted — cannot disable DND")
        }
    }

    private fun boostPerformance() {
        // Silence ringtone/notification volume to reduce system interruptions
        audioManager.setStreamVolume(
            AudioManager.STREAM_NOTIFICATION,
            0,
            0
        )
        Log.d("AegisLayer", "ActionExecutor: BOOST_PERFORMANCE — notification volume muted")
    }
}
