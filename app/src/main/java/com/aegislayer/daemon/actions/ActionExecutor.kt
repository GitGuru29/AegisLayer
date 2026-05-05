package com.aegislayer.daemon.actions

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.aegislayer.daemon.trace.TraceEngine
import com.aegislayer.daemon.trace.TraceLevel

class ActionExecutor(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun execute(actions: List<String>) {
        actions.forEach { action ->
            when (action) {
                "ENABLE_DND"         -> enableDnd()
                "DISABLE_DND"        -> disableDnd()
                "BOOST_PERFORMANCE"  -> boostPerformance()
                "BLOCK_NOTIFICATIONS" -> enableDnd()
                else -> Log.w("AegisLayer", "Unknown action: $action")
            }
        }
    }

    private fun enableDnd() {
        if (notificationManager.isNotificationPolicyAccessGranted) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            TraceEngine.log(TraceLevel.ACTION, "ActionExecutor", "ENABLE_DND — DND activated")
        } else {
            TraceEngine.log(TraceLevel.ACTION, "ActionExecutor", "ENABLE_DND — FAILED: 'Do Not Disturb' access not granted in system settings")
        }
    }

    private fun disableDnd() {
        if (notificationManager.isNotificationPolicyAccessGranted) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            TraceEngine.log(TraceLevel.ACTION, "ActionExecutor", "DISABLE_DND — DND lifted")
        } else {
            TraceEngine.log(TraceLevel.ACTION, "ActionExecutor", "DISABLE_DND — FAILED: 'Do Not Disturb' access not granted in system settings")
        }
    }

    private fun boostPerformance() {
        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
        TraceEngine.log(TraceLevel.ACTION, "ActionExecutor", "BOOST_PERFORMANCE — notification volume muted")
    }
}
