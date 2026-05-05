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
    
    // Tracks which rules are currently "met" so we only trigger them once
    private val activeRuleIds = mutableSetOf<String>()

    /**
     * Swaps out the current rules for a new set.
     * Called on startup and whenever the user adds/deletes rules.
     */
    fun loadRules(newRules: List<Rule>) {
        rules.clear()
        rules.addAll(newRules)
        activeRuleIds.clear()
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
        val newlyActiveIds = mutableSetOf<String>()
        
        // Simple priority-based evaluation
        rules.sortedByDescending { it.priority }.forEach { rule ->
            if (rule.evaluate(contextSnapshot)) {
                newlyActiveIds.add(rule.ruleId)
                
                // Only execute actions if this rule just became active (edge-triggered)
                // This prevents spamming "DISABLE_DND" every single second just because the screen is on.
                if (!activeRuleIds.contains(rule.ruleId)) {
                    actionsToTake.addAll(rule.actions)
                }
            }
        }
        
        activeRuleIds.clear()
        activeRuleIds.addAll(newlyActiveIds)
        
        // Return a distinct list so we don't accidentally run "ENABLE_DND" twice
        // if two different rules both triggered it
        return actionsToTake.distinct()
    }
}
