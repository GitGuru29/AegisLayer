package com.aegislayer.daemon.models

/**
 * A Rule is an automation instruction: "WHEN these conditions are true, DO these actions."
 *
 * Example: When YouTube is in the foreground AND the phone is charging,
 *          set brightness to low AND enable DND.
 *
 * Rules have a priority (higher = evaluated first) so conflicting rules
 * resolve predictably — the most important one wins.
 */
data class Rule(
    val ruleId: String,
    val conditions: List<Condition>,
    val actions: List<String>,
    val priority: Int = 0
) {
    /**
     * Checks whether ALL conditions of this rule match the current device state.
     * Uses AND logic — every single condition must be true for the rule to fire.
     * Even one mismatch means the rule stays dormant.
     */
    fun evaluate(context: Map<String, Any>): Boolean {
        // All conditions must match for rule to trigger
        return conditions.all { it.matches(context) }
    }
}

/**
 * A single "if" check within a rule.
 *
 * For example: Condition(type = "SCREEN_ON", value = false)
 * means "is the screen currently off?"
 *
 * The 'type' maps to a key in the context snapshot (built by ContextBuilder),
 * and 'value' is what we expect that key to equal.
 */
data class Condition(
    val type: String,
    val value: Any
) {
    /**
     * Compares this condition's expected value against the actual device state.
     * Simple equality check: does context["SCREEN_ON"] == false? Yes → match.
     */
    fun matches(context: Map<String, Any>): Boolean {
        val contextValue = context[type]
        return contextValue == value
    }
}
