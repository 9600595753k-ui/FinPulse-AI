package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AlertFrequency
import com.example.domain.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "finpulse_settings")

data class UserSettings(
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val overallMonthlyBudget: Double = 45000.0,
    val largeTransactionThreshold: Double = 10000.0,
    val enableDeviceNotifications: Boolean = false,
    val alertFrequency: AlertFrequency = AlertFrequency.INSTANT,
    val carryForwardBudgets: Boolean = false,
    val allowAiAggregatedInsights: Boolean = true,
    val alertAt50: Boolean = true,
    val alertAt75: Boolean = true,
    val alertAt90: Boolean = true,
    val alertAt100: Boolean = true,
    val hasSeededDemoData: Boolean = false,
    val hasRequestedSmsPermission: Boolean = false
)

class SettingsDataStore(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme_preference")
        val OVERALL_BUDGET = doublePreferencesKey("overall_monthly_budget")
        val LARGE_TXN_THRESHOLD = doublePreferencesKey("large_txn_threshold")
        val DEVICE_NOTIFICATIONS = booleanPreferencesKey("enable_device_notifications")
        val ALERT_FREQUENCY = stringPreferencesKey("alert_frequency")
        val CARRY_FORWARD = booleanPreferencesKey("carry_forward_budgets")
        val ALLOW_AI_INSIGHTS = booleanPreferencesKey("allow_ai_aggregated_insights")
        val ALERT_50 = booleanPreferencesKey("alert_50")
        val ALERT_75 = booleanPreferencesKey("alert_75")
        val ALERT_90 = booleanPreferencesKey("alert_90")
        val ALERT_100 = booleanPreferencesKey("alert_100")
        val HAS_SEEDED_DEMO = booleanPreferencesKey("has_seeded_demo_data")
        val HAS_REQUESTED_SMS_PERMISSION = booleanPreferencesKey("has_requested_sms_permission")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            themePreference = prefs[Keys.THEME]?.let {
                runCatching { ThemePreference.valueOf(it) }.getOrNull()
            } ?: ThemePreference.SYSTEM,
            overallMonthlyBudget = prefs[Keys.OVERALL_BUDGET] ?: 45000.0,
            largeTransactionThreshold = prefs[Keys.LARGE_TXN_THRESHOLD] ?: 10000.0,
            enableDeviceNotifications = prefs[Keys.DEVICE_NOTIFICATIONS] ?: false,
            alertFrequency = prefs[Keys.ALERT_FREQUENCY]?.let {
                runCatching { AlertFrequency.valueOf(it) }.getOrNull()
            } ?: AlertFrequency.INSTANT,
            carryForwardBudgets = prefs[Keys.CARRY_FORWARD] ?: false,
            allowAiAggregatedInsights = prefs[Keys.ALLOW_AI_INSIGHTS] ?: true,
            alertAt50 = prefs[Keys.ALERT_50] ?: true,
            alertAt75 = prefs[Keys.ALERT_75] ?: true,
            alertAt90 = prefs[Keys.ALERT_90] ?: true,
            alertAt100 = prefs[Keys.ALERT_100] ?: true,
            hasSeededDemoData = prefs[Keys.HAS_SEEDED_DEMO] ?: false,
            hasRequestedSmsPermission = prefs[Keys.HAS_REQUESTED_SMS_PERMISSION] ?: false
        )
    }

    suspend fun setThemePreference(pref: ThemePreference) {
        context.dataStore.edit { it[Keys.THEME] = pref.name }
    }

    suspend fun setOverallMonthlyBudget(limit: Double) {
        context.dataStore.edit { it[Keys.OVERALL_BUDGET] = limit }
    }

    suspend fun setLargeTransactionThreshold(amount: Double) {
        context.dataStore.edit { it[Keys.LARGE_TXN_THRESHOLD] = amount }
    }

    suspend fun setDeviceNotifications(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DEVICE_NOTIFICATIONS] = enabled }
    }

    suspend fun setAlertFrequency(freq: AlertFrequency) {
        context.dataStore.edit { it[Keys.ALERT_FREQUENCY] = freq.name }
    }

    suspend fun setCarryForwardBudgets(carryForward: Boolean) {
        context.dataStore.edit { it[Keys.CARRY_FORWARD] = carryForward }
    }

    suspend fun setAllowAiAggregatedInsights(allowed: Boolean) {
        context.dataStore.edit { it[Keys.ALLOW_AI_INSIGHTS] = allowed }
    }

    suspend fun setThresholdAlerts(at50: Boolean, at75: Boolean, at90: Boolean, at100: Boolean) {
        context.dataStore.edit {
            it[Keys.ALERT_50] = at50
            it[Keys.ALERT_75] = at75
            it[Keys.ALERT_90] = at90
            it[Keys.ALERT_100] = at100
        }
    }

    suspend fun markDemoDataSeeded(seeded: Boolean) {
        context.dataStore.edit { it[Keys.HAS_SEEDED_DEMO] = seeded }
    }

    suspend fun markSmsPermissionRequested() {
        context.dataStore.edit { it[Keys.HAS_REQUESTED_SMS_PERMISSION] = true }
    }
}
