package com.aegislayer.daemon.models

data class Rule(
    val ruleId: String,
    val conditions: List<Condition>,
    val actions: List<String>,
    val priority: Int = 0
) {
    fun evaluate(context: Map<String, Any>): Boolean {
        // All conditions must match for rule to trigger
        return conditions.all { it.matches(context) }
    }
}

data class Condition(
    val type: String,
    val value: Any
) {
    fun matches(context: Map<String, Any>): Boolean {
        val contextValue = context[type]
        return contextValue == value
    }
}
