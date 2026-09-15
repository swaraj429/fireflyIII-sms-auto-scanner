package com.swaraj429.firefly3smsscanner.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.swaraj429.firefly3smsscanner.debug.DebugLog
import com.swaraj429.firefly3smsscanner.model.ParsingRule

/**
 * Manages parsing rules — persisted in SharedPreferences as JSON.
 * Provides the live list to the UI and to the RuleEngine.
 */
class RulesViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "RulesVM"
    private val prefs = application.getSharedPreferences("firefly_rules", android.content.Context.MODE_PRIVATE)
    private val gson = Gson()

    val rules = mutableStateListOf<ParsingRule>()

    init {
        loadRules()
    }

    private fun loadRules() {
        val json = prefs.getString("rules_json", null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<ParsingRule>>() {}.type
                val loaded: List<ParsingRule> = gson.fromJson(json, type)
                rules.clear()
                rules.addAll(loaded)
                DebugLog.log(TAG, "Loaded ${loaded.size} rules from prefs")
            } catch (e: Exception) {
                DebugLog.log(TAG, "Error loading rules: ${e.message}")
            }
        } else {
            // Seed with example rules on first launch
            rules.addAll(listOf(
                ParsingRule(
                    keyword = "SWIGGY",
                    categoryName = "Food & Dining",
                    tags = listOf("food-delivery"),
                    descriptionTemplate = "{vendor} food order"
                ),
                ParsingRule(
                    keyword = "AMAZON",
                    categoryName = "Shopping",
                    tags = listOf("online"),
                    descriptionTemplate = "{vendor} spent {amount}"
                ),
                ParsingRule(
                    keyword = "UBER",
                    categoryName = "Transport",
                    tags = listOf("ride"),
                    descriptionTemplate = "{vendor} ride {amount}"
                ),
                ParsingRule(
                    keyword = "NETFLIX",
                    categoryName = "Entertainment",
                    tags = listOf("subscription"),
                    descriptionTemplate = "{vendor} subscription"
                ),
            ))
            saveRules()
            DebugLog.log(TAG, "Seeded ${rules.size} default rules")
        }
    }

    fun saveRules() {
        val json = gson.toJson(rules.toList())
        prefs.edit().putString("rules_json", json).apply()
        DebugLog.log(TAG, "Saved ${rules.size} rules to prefs")
    }

    fun addRule(rule: ParsingRule) {
        rules.add(rule)
        saveRules()
    }

    fun updateRule(rule: ParsingRule) {
        val idx = rules.indexOfFirst { it.id == rule.id }
        if (idx >= 0) {
            rules[idx] = rule
            saveRules()
        }
    }

    fun deleteRule(ruleId: String) {
        rules.removeAll { it.id == ruleId }
        saveRules()
    }

    fun toggleRule(ruleId: String, enabled: Boolean) {
        val idx = rules.indexOfFirst { it.id == ruleId }
        if (idx >= 0) {
            rules[idx] = rules[idx].copy(isEnabled = enabled)
            saveRules()
        }
    }

    /**
     * Checks if an active rule exists matching the given vendor name.
     */
    fun hasRuleForVendor(vendorName: String): Boolean {
        if (vendorName.isBlank()) return false
        val clean = vendorName.trim()
        return rules.any { rule ->
            rule.isEnabled && (
                rule.keyword.equals(clean, ignoreCase = true) ||
                clean.contains(rule.keyword, ignoreCase = true) ||
                rule.keyword.contains(clean, ignoreCase = true)
            )
        }
    }

    /**
     * Finds the first rule matching the given vendor name.
     */
    fun getRuleForVendor(vendorName: String): ParsingRule? {
        if (vendorName.isBlank()) return null
        val clean = vendorName.trim()
        return rules.firstOrNull { rule ->
            rule.keyword.equals(clean, ignoreCase = true) ||
            clean.contains(rule.keyword, ignoreCase = true) ||
            rule.keyword.contains(clean, ignoreCase = true)
        }
    }
}
