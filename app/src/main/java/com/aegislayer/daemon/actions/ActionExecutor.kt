package com.aegislayer.daemon.actions

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.aegislayer.daemon.trace.TraceEngine
import com.aegislayer.daemon.trace.TraceLevel

/**
 * The hands of the daemon — this class actually touches the phone's settings.
 *
 * When the RuleEngine decides "mute the phone", it sends that instruction here.
 * ActionExecutor translates rule action strings (like "SET_VOLUME:0") into
 * real Android API calls that change system settings.
 *
 * Every action checks its required permission first and logs the result,
 * so if something fails, you'll see exactly why in the trace log.
 */
class ActionExecutor(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Takes a list of action strings and executes each one.
     * Format: "ACTION_NAME" or "ACTION_NAME:parameter"
     * Example: "SET_BRIGHTNESS:50" or "ENABLE_DND"
     */
    fun execute(actions: List<String>) {
        actions.forEach { action ->
            try {
                val parts = action.split(":")
                val actionName = parts[0]
                val param = parts.getOrNull(1)

                when (actionName) {
                    "ENABLE_DND"         -> enableDnd()
                    "DISABLE_DND"        -> disableDnd()
                    "BOOST_PERFORMANCE"  -> boostPerformance()
                    "SET_BRIGHTNESS"     -> setBrightness(param?.toInt() ?: 128)
                    "SET_VOLUME"         -> setVolume(param?.toInt() ?: 50)
                    "TOGGLE_BLUETOOTH"   -> toggleBluetooth(param?.toBoolean() ?: false)
                    "SET_AUTO_ROTATE"    -> setAutoRotate(param?.toBoolean() ?: true)
                    else -> Log.w("AegisLayer", "Unknown action: $actionName")
                }
            } catch (e: Exception) {
                TraceEngine.log(TraceLevel.ACTION, "ActionExecutor", "Error executing $action: ${e.message}")
            }
        }
    }

    /** Enables Do Not Disturb — silences all notifications and calls */
    private fun enableDnd() {
        if (notificationManager.isNotificationPolicyAccessGranted) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            TraceEngine.log(TraceLevel.ACTION, "ActionExecutor", "ENABLE_DND — DND activated")
        } else {
            TraceEngine.log(TraceLevel.ACTION, "ActionExecutor", "ENABLE_DND — FAILED: 'Do Not Disturb' access missing")
        }
    }

    private fun disableDnd() {
        if (notificationManager.isNotificationPolicyAccessGranted) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            TraceEngine.log(TraceLevel.ACTION, "ActionExecutor", "DISABLE_DND — DND lifted")
        } else {
            TraceEngine.log(TraceLevel.ACTION, "ActionExecutor", "DISABLE_DND — FAILED: 'Do Not Disturb' access missing")
        }
    }

    /** Mutes notification sounds specifically — a lighter touch than full DND */
    private fun boostPerformance() {
        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
        TraceEngine.log(TraceLevel.ACTION, "ActionExecutor", "BOOST_PERFORMANCE — muted notifications")
    }

    /**
     * Sets screen brightness directly.
     * Value range: 0 (pitch black) to 255 (blinding).
     * Requires WRITE_SETTINGS permission — the user grants this manually.
     */
    private fun setBrightness(level: Int) {
        if (android.provider.Settings.System.canWrite(context)) {
            val clamped = level.coerceIn(0, 255)
            android.provider.Settings.System.putInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, clamped)
            TraceEngine.log(TraceLevel.ACTION, "ActionExecutor", "SET_BRIGHTNESS — adjusted to $clamped")
        } else {
            TraceEngine.log(TraceLevel.ACTION, "ActionExecutor", "SET_BRIGHTNESS — FAILED: Write Settings permission missing")
        }
    }

    /**
     * Sets media volume as a percentage (0-100).
     * We calculate the actual volume step from the device's max volume.
     */
    private fun setVolume(percentage: Int) {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (percentage.coerceIn(0, 100) * maxVol) / 100
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        TraceEngine.log(TraceLevel.ACTION, "ActionExecutor", "SET_VOLUME — music volume at $percentage%")
    }

    /**
     * Attempts to toggle Bluetooth.
     * Note: Android 13+ blocks apps from toggling Bluetooth directly
     * unless they're system apps. We log the attempt but can't force it.
     */
    private fun toggleBluetooth(on: Boolean) {
        // Note: On Android 13+, apps cannot toggle Bluetooth without user interaction 
        // unless they are system apps or using CompanionDeviceManager. 
        // For now, we log the attempt.
        TraceEngine.log(TraceLevel.ACTION, "ActionExecutor", "TOGGLE_BLUETOOTH($on) — Manual interaction required on newer Android")
    }

    /** Toggles the auto-rotate setting. Requires WRITE_SETTINGS permission. */
    private fun setAutoRotate(on: Boolean) {
        if (android.provider.Settings.System.canWrite(context)) {
            val value = if (on) 1 else 0
            android.provider.Settings.System.putInt(context.contentResolver, android.provider.Settings.System.ACCELEROMETER_ROTATION, value)
            TraceEngine.log(TraceLevel.ACTION, "ActionExecutor", "SET_AUTO_ROTATE — ${if (on) "Enabled" else "Disabled"}")
        }
    }
}
