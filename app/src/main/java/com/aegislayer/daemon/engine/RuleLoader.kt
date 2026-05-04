package com.aegislayer.daemon.engine

import android.content.Context
import android.util.Log
import com.aegislayer.daemon.models.Condition
import com.aegislayer.daemon.models.Rule
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject

object RuleLoader {

    fun loadFromAssets(context: Context): List<Rule> {
        return try {
            val json = context.assets.open("rules.json")
                .bufferedReader()
                .use { it.readText() }

            val gson = Gson()
            val jsonArray = gson.fromJson(json, JsonArray::class.java)
            val rules = mutableListOf<Rule>()

            jsonArray.forEach { element ->
                val obj = element.asJsonObject
                val ruleId = obj.get("ruleId").asString
                val priority = obj.get("priority").asInt
                val actions = obj.getAsJsonArray("actions").map { it.asString }

                val conditions = obj.getAsJsonArray("conditions").map { condEl ->
                    val condObj = condEl.asJsonObject
                    val type = condObj.get("type").asString
                    val rawValue = condObj.get("value")
                    val value: Any = when {
                        rawValue.isJsonPrimitive && rawValue.asJsonPrimitive.isBoolean -> rawValue.asBoolean
                        rawValue.isJsonPrimitive && rawValue.asJsonPrimitive.isNumber  -> rawValue.asInt
                        else -> rawValue.asString
                    }
                    Condition(type, value)
                }

                rules.add(Rule(ruleId, conditions, actions, priority))
            }

            Log.d("AegisLayer", "RuleLoader: Loaded ${rules.size} rules from assets")
            rules
        } catch (e: Exception) {
            Log.e("AegisLayer", "RuleLoader: Failed to load rules - ${e.message}")
            emptyList()
        }
    }
}
