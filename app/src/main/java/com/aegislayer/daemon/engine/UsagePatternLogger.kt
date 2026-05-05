package com.aegislayer.daemon.engine

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * AegisLayer's memory — records everything the daemon does so the ML model
 * can learn from real behavior over time.
 *
 * Every time a rule fires (e.g., "YouTube opened → brightness dimmed"),
 * we save a snapshot of what was happening at that moment:
 * - What app was open
 * - What time it was
 * - Whether the phone was charging
 * - What actions were taken
 *
 * Later, the SelfLearningEngine reads these snapshots and looks for
 * patterns. If "dim brightness + YouTube" shows up 10 times, the model
 * gets more confident about that association.
 *
 * Privacy note: All data stays on-device in the app's private storage.
 * Nothing is ever transmitted externally.
 */
object UsagePatternLogger {

    // Where we save the observation history
    private const val PATTERN_FILE = "aegis_usage_patterns.json"

    // Cap the history at 500 entries to keep disk usage reasonable (~100KB)
    private const val MAX_PATTERNS = 500

    /**
     * A single snapshot of "what was happening when a rule fired".
     * This is the raw material the ML model learns from.
     */
    data class UsagePattern(
        val timestamp: Long,         // When this happened (epoch millis)
        val hour: Int,               // Hour of day (0-23) — useful for time-based patterns
        val foregroundApp: String,   // Which app was being used (package name)
        val isCharging: Boolean,     // Was the phone plugged in?
        val screenOn: Boolean,       // Was the screen awake?
        val batteryLevel: Int,       // Battery percentage at the time
        val activeActions: List<String>  // What actions were executed (e.g., "SET_VOLUME:0")
    )

    /**
     * Called every time the daemon executes a rule.
     *
     * We capture a snapshot of the current device state alongside the
     * actions that were taken. This gives us labeled training data:
     * "In this situation, these things happened."
     */
    fun recordRuleFired(context: Context, contextSnapshot: Map<String, Any>, actions: List<String>) {
        if (actions.isEmpty()) return

        val calendar = java.util.Calendar.getInstance()
        val pattern = UsagePattern(
            timestamp = System.currentTimeMillis(),
            hour = calendar.get(java.util.Calendar.HOUR_OF_DAY),
            foregroundApp = contextSnapshot["APP_FOREGROUND"] as? String ?: "",
            isCharging = contextSnapshot["IS_CHARGING"] as? Boolean ?: false,
            screenOn = contextSnapshot["SCREEN_ON"] as? Boolean ?: true,
            batteryLevel = contextSnapshot["BATTERY_LEVEL"] as? Int ?: -1,
            activeActions = actions
        )

        val patterns = loadPatterns(context).toMutableList()
        patterns.add(pattern)

        // If we've collected too many, trim the oldest ones
        val trimmed = if (patterns.size > MAX_PATTERNS) {
            patterns.takeLast(MAX_PATTERNS)
        } else {
            patterns
        }

        savePatterns(context, trimmed)
        Log.d("AegisLayer", "UsagePatternLogger: Recorded pattern (total: ${trimmed.size})")
    }

    /**
     * The magic step: converts raw usage history into ML training data.
     *
     * Here's the strategy:
     * 1. Group all patterns by "what app + what actions" (the behavior signature)
     * 2. Ignore one-off behaviors — only learn from things that happened 3+ times
     *    (this filters out noise and accidental triggers)
     * 3. For each recurring behavior, create a synthetic training sentence
     *    like "youtube is open and dim brightness" with tags ["COND_APP_YOUTUBE", "ACT_BRIGHTNESS_LOW"]
     * 4. Behaviors that happen very frequently get extra weight (duplicate entries)
     *    so the model pays more attention to them
     */
    fun generateTrainingObservations(context: Context): List<Pair<String, List<String>>> {
        val patterns = loadPatterns(context)
        if (patterns.isEmpty()) return emptyList()

        val observations = mutableListOf<Pair<String, List<String>>>()

        // Group by behavior: "which app was open + which actions ran"
        // This tells us what recurring habits the user has
        val behaviorGroups = patterns.groupBy { 
            "${it.foregroundApp}|${it.activeActions.sorted().joinToString(",")}" 
        }

        for ((_, group) in behaviorGroups) {
            // The "3 strikes" rule: don't learn from things that only happened once or twice.
            // Those could be flukes, but 3+ times means it's probably a real habit.
            if (group.size < 3) continue

            val representative = group.first()
            val tags = mutableListOf<String>()
            val textParts = mutableListOf<String>()

            // Figure out the condition tag from what app was in use
            if (representative.foregroundApp.isNotEmpty()) {
                when {
                    representative.foregroundApp.contains("youtube") -> {
                        tags.add("COND_APP_YOUTUBE")
                        textParts.add("youtube is open")
                    }
                    representative.foregroundApp.contains("instagram") -> {
                        tags.add("COND_APP_INSTAGRAM")
                        textParts.add("instagram is open")
                    }
                }
            }

            if (!representative.screenOn) {
                tags.add("COND_SCREEN_OFF")
                textParts.add("screen is off")
            }

            if (representative.isCharging) {
                tags.add("COND_CHARGING")
                textParts.add("while charging")
            }

            // Figure out the action tags from what the daemon actually did
            for (action in representative.activeActions) {
                when {
                    action.startsWith("SET_VOLUME:0") -> {
                        tags.add("ACT_MUTE")
                        textParts.add("mute device")
                    }
                    action == "ENABLE_DND" -> {
                        tags.add("ACT_DND_ON")
                        textParts.add("enable do not disturb")
                    }
                    action == "DISABLE_DND" -> {
                        tags.add("ACT_DND_OFF")
                        textParts.add("disable do not disturb")
                    }
                    action.startsWith("SET_BRIGHTNESS") -> {
                        val level = action.substringAfter(":").toIntOrNull() ?: 128
                        if (level < 100) {
                            tags.add("ACT_BRIGHTNESS_LOW")
                            textParts.add("dim brightness")
                        } else {
                            tags.add("ACT_BRIGHTNESS_HIGH")
                            textParts.add("increase brightness")
                        }
                    }
                    action.startsWith("SET_AUTO_ROTATE:true") -> {
                        tags.add("ACT_ROTATE_ON")
                        textParts.add("enable rotation")
                    }
                    action.startsWith("SET_AUTO_ROTATE:false") -> {
                        tags.add("ACT_ROTATE_OFF")
                        textParts.add("disable rotation")
                    }
                }
            }

            if (tags.isNotEmpty() && textParts.isNotEmpty()) {
                // Stitch the parts together into a natural-sounding training sentence
                val sentence = textParts.joinToString(" and ")
                observations.add(sentence to tags)

                // If this behavior happened a LOT, give it extra weight.
                // 15 occurrences → 3 copies, 25 occurrences → 5 copies (capped at 3 extra)
                // This makes the model strongly favor well-established habits.
                val extraWeight = (group.size / 5).coerceAtMost(3)
                repeat(extraWeight) {
                    observations.add(sentence to tags)
                }
            }
        }

        Log.d("AegisLayer", "UsagePatternLogger: Generated ${observations.size} training observations from ${patterns.size} patterns")
        return observations
    }

    /**
     * Reads saved patterns from disk. Returns empty list if file doesn't exist yet.
     */
    fun loadPatterns(context: Context): List<UsagePattern> {
        return try {
            val file = File(context.filesDir, PATTERN_FILE)
            if (!file.exists()) return emptyList()
            val type = object : TypeToken<List<UsagePattern>>() {}.type
            Gson().fromJson(file.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun savePatterns(context: Context, patterns: List<UsagePattern>) {
        try {
            File(context.filesDir, PATTERN_FILE).writeText(Gson().toJson(patterns))
        } catch (e: Exception) {
            Log.e("AegisLayer", "UsagePatternLogger: Save failed — ${e.message}")
        }
    }
}
