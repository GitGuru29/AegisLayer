package com.aegislayer.daemon.engine

import android.util.Log
import com.aegislayer.daemon.models.Rule

/**
 * The decision maker of the daemon.
 *
 * This class holds all the active rules. Whenever the device state changes
 * (ContextBuilder creates a new snapshot), the service passes that snapshot
 * here. The RuleEngine evaluates every rule against the snapshot to see
 * if any of them should trigger.
 */
class RuleEngine {

    // All currently loaded rules (both from assets and user-created)
    private val rules = mutableListOf<Rule>()

    /**
     * Swaps out the current rules for a new set.
     * Called on startup and whenever the user adds/deletes rules.
     */
    fun loadRules(newRules: List<Rule>) {
        rules.clear()
        rules.addAll(newRules)
        Log.d("AegisLayer", "RuleEngine: Loaded ${rules.size} rules")
    }

    /**
     * Checks the current device state against all rules.
     *
     * Returns a list of actions that should be executed right now.
     * Rules are sorted by priority first (highest priority wins).
     */
    fun evaluateContext(contextSnapshot: Map<String, Any>): List<String> {
        val actionsToTake = mutableListOf<String>()
        
        // Simple priority-based evaluation
        rules.sortedByDescending { it.priority }.forEach { rule ->
            if (rule.evaluate(contextSnapshot)) {
                actionsToTake.addAll(rule.actions)
            }
        }
        
        // Return a distinct list so we don't accidentally run "ENABLE_DND" twice
        // if two different rules both triggered it
        return actionsToTake.distinct()
    }
}
