package com.aegislayer.daemon.engine

import android.util.Log
import com.aegislayer.daemon.models.Rule

class RuleEngine {

    private val rules = mutableListOf<Rule>()

    fun loadRules(newRules: List<Rule>) {
        rules.clear()
        rules.addAll(newRules)
        Log.d("AegisLayer", "RuleEngine: Loaded ${rules.size} rules")
    }

    fun evaluateContext(contextSnapshot: Map<String, Any>): List<String> {
        val actionsToTake = mutableListOf<String>()
        
        // Simple priority-based evaluation
        rules.sortedByDescending { it.priority }.forEach { rule ->
            if (rule.evaluate(contextSnapshot)) {
                actionsToTake.addAll(rule.actions)
            }
        }
        
        return actionsToTake.distinct()
    }
}
