package com.aegislayer.daemon.engine

import android.content.Context
import com.aegislayer.daemon.models.Condition
import com.aegislayer.daemon.models.Rule

/**
 * The translator between human language and machine rules.
 *
 * When a user types something like "dim the screen when YouTube opens",
 * this class does three things:
 * 1. Passes the text to the ML classifier to figure out what they mean
 * 2. Maps the predicted intent tags to concrete Conditions and Actions
 * 3. Wraps everything into a Rule object that the daemon can execute
 *
 * It's the glue between the "understanding" (ML) and the "doing" (RuleEngine).
 */
class AIPromptProcessor(private val context: Context? = null) {

    /**
     * Takes a natural language prompt and tries to build a Rule from it.
     *
     * Returns null if the ML model couldn't identify both a trigger condition
     * AND an action — because a rule without both is meaningless.
     * (e.g., "dim brightness" alone has no trigger, so we can't automate it)
     */
    fun processPrompt(prompt: String): Rule? {
        // Ask the ML model: "what intents does this sentence contain?"
        val classifier = RuleMLTrainer.getClassifier(context)
        val predictedTags = classifier.predict(prompt)
        return processTags(predictedTags)
    }

    /**
     * Converts abstract ML tags directly into concrete rule components.
     * Used by the ML engine to autonomously generate rules from observed patterns.
     */
    fun processTags(tags: List<String>): Rule? {

        val conditions = mutableListOf<Condition>()
        val actions = mutableListOf<String>()
        val priority = 5

        // Convert abstract ML tags into concrete rule components
        for (tag in tags) {
            when (tag) {
                // --- Conditions: "WHEN should this rule fire?" ---
                "COND_SCREEN_OFF"     -> conditions.add(Condition("SCREEN_ON", false))
                "COND_SCREEN_ON"      -> conditions.add(Condition("SCREEN_ON", true))
                "COND_CHARGING"       -> conditions.add(Condition("IS_CHARGING", true))
                "COND_APP_YOUTUBE"    -> conditions.add(Condition("APP_FOREGROUND", "com.google.android.youtube"))
                "COND_APP_INSTAGRAM"  -> conditions.add(Condition("APP_FOREGROUND", "com.instagram.android"))

                // --- Actions: "WHAT should the phone do?" ---
                // Brightness values are on Android's raw 0-255 scale
                "ACT_MUTE"            -> actions.add("SET_VOLUME:0")
                "ACT_DND_ON"          -> actions.add("ENABLE_DND")
                "ACT_DND_OFF"         -> actions.add("DISABLE_DND")
                "ACT_BRIGHTNESS_LOW"  -> actions.add("SET_BRIGHTNESS:10")   // Very dim, not fully black
                "ACT_BRIGHTNESS_HIGH" -> actions.add("SET_BRIGHTNESS:250")  // Near max
                "ACT_ROTATE_ON"       -> actions.add("SET_AUTO_ROTATE:true")
                "ACT_ROTATE_OFF"      -> actions.add("SET_AUTO_ROTATE:false")
            }
        }

        // A rule needs at least one trigger AND one action to be useful
        if (conditions.isEmpty() || actions.isEmpty()) return null

        return Rule(
            ruleId = "ml_rule_" + System.currentTimeMillis().toString().takeLast(4),
            conditions = conditions,
            actions = actions,
            priority = priority
        )
    }
}
