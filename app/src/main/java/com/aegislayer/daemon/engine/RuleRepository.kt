package com.aegislayer.daemon.engine

import android.content.Context
import com.aegislayer.daemon.models.Rule
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class RuleRepository(private val context: Context) {

    private val gson = Gson()
    private val ruleFile = File(context.filesDir, "user_rules.json")

    fun getAllRules(): List<Rule> {
        val assetRules = RuleLoader.loadFromAssets(context)
        val userRules = loadUserRules()
        return assetRules + userRules
    }

    fun saveUserRule(rule: Rule) {
        val current = loadUserRules().toMutableList()
        // Replace if exists, else add
        val index = current.indexOfFirst { it.ruleId == rule.ruleId }
        if (index != -1) {
            current[index] = rule
        } else {
            current.add(rule)
        }
        saveToInternal(current)
    }

    fun deleteUserRule(ruleId: String) {
        val current = loadUserRules().filter { it.ruleId != ruleId }
        saveToInternal(current)
    }

    private fun loadUserRules(): List<Rule> {
        if (!ruleFile.exists()) return emptyList()
        return try {
            val json = ruleFile.readText()
            val type = object : TypeToken<List<Rule>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveToInternal(rules: List<Rule>) {
        try {
            ruleFile.writeText(gson.toJson(rules))
        } catch (e: Exception) {
            // Log error
        }
    }
}
