package com.aegislayer.daemon.engine

import com.aegislayer.daemon.models.Condition
import com.aegislayer.daemon.models.Rule
import java.util.*

class AIPromptProcessor {

    /**
     * Translates a natural language prompt into a structured Rule.
     * In a production app, this would call Gemini Nano or a Cloud LLM.
     * For now, we use a sophisticated keyword-based heuristic.
     */
    fun processPrompt(prompt: String): Rule? {
        val lower = prompt.lowercase(Locale.ROOT)
        
        val conditions = mutableListOf<Condition>()
        val actions = mutableListOf<String>()
        var priority = 5

        // --- Parsing Conditions ---
        if (lower.contains("screen is off") || lower.contains("screen off")) {
            conditions.add(Condition("SCREEN_ON", false))
        } else if (lower.contains("screen is on") || lower.contains("screen on")) {
            conditions.add(Condition("SCREEN_ON", true))
        }

        if (lower.contains("charging")) {
            conditions.add(Condition("IS_CHARGING", true))
        }

        // App detection (Heuristic: Look for words starting with upper case or specific app names)
        // Simplified for this demo:
        if (lower.contains("open youtube")) {
            conditions.add(Condition("APP_FOREGROUND", "com.google.android.youtube"))
        } else if (lower.contains("open instagram")) {
            conditions.add(Condition("APP_FOREGROUND", "com.instagram.android"))
        }

        // --- Parsing Actions ---
        if (lower.contains("mute") || lower.contains("silent")) {
            actions.add("SET_VOLUME:0")
            actions.add("ENABLE_DND")
        }

        if (lower.contains("brightness to")) {
            val level = Regex("\\d+").find(lower.substringAfter("brightness to"))?.value?.toIntOrNull()
            if (level != null) actions.add("SET_BRIGHTNESS:$level")
        }

        if (lower.contains("volume to")) {
            val level = Regex("\\d+").find(lower.substringAfter("volume to"))?.value?.toIntOrNull()
            if (level != null) actions.add("SET_VOLUME:$level")
        }

        if (lower.contains("rotate")) {
            actions.add("SET_AUTO_ROTATE:${!lower.contains("disable")}")
        }

        if (lower.contains("dnd") || lower.contains("do not disturb")) {
            if (lower.contains("disable") || lower.contains("off")) {
                actions.add("DISABLE_DND")
            } else {
                actions.add("ENABLE_DND")
            }
        }

        if (conditions.isEmpty() || actions.isEmpty()) return null

        return Rule(
            ruleId = "ai_rule_" + System.currentTimeMillis().toString().takeLast(4),
            conditions = conditions,
            actions = actions,
            priority = priority
        )
    }
}
