package io.github.tetratheta.npviewer.filter

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

object FilterPreferences {
  const val KEY_ENABLED = "filters_enabled"
  const val KEY_AUTO_UPDATE = "filters_auto_update"
  const val KEY_SUBSCRIPTIONS = "filters_subscriptions"
  const val KEY_USER_RULES = "filters_user_rules"
  const val KEY_LAST_UPDATED_AT = "filters_last_updated_at"
  const val KEY_LAST_UPDATE_ERROR = "filters_last_update_error"

  const val DEFAULT_SUBSCRIPTIONS = "https://cdn.jsdelivr.net/npm/@list-kr/filterslists@latest/dist/filterslist-AdGuard.txt"
  private const val UPDATE_INTERVAL_MS = 24L * 60L * 60L * 1000L

  fun prefs(context: Context): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

  fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

  fun isAutoUpdateEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_UPDATE, true)

  fun getSubscriptionUrls(context: Context): List<String> =
    prefs(context).getString(KEY_SUBSCRIPTIONS, DEFAULT_SUBSCRIPTIONS).orEmpty().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
      .distinct().toList()

  fun getUserRules(context: Context): String = prefs(context).getString(KEY_USER_RULES, "").orEmpty().trim()

  fun getLastUpdatedAt(context: Context): Long = prefs(context).getLong(KEY_LAST_UPDATED_AT, 0L)

  fun getLastUpdateError(context: Context): String? = prefs(context).getString(KEY_LAST_UPDATE_ERROR, null)

  fun shouldRefresh(context: Context, now: Long = System.currentTimeMillis()): Boolean {
    if (!isEnabled(context) || !isAutoUpdateEnabled(context)) return false
    return now - getLastUpdatedAt(context) >= UPDATE_INTERVAL_MS
  }
}
