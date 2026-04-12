package io.github.tetratheta.npviewer.filter

import android.content.Context
import androidx.core.content.edit
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class FilterUpdateSummary(
  val updatedCount: Int, val failedCount: Int, val lastError: String? = null
)

class FilterRepository(private val context: Context) {
  private val prefs = FilterPreferences.prefs(context)
  private val filtersDir = File(context.filesDir, "filters").apply { mkdirs() }

  fun getSubscriptionUrls(): List<String> = FilterPreferences.getSubscriptionUrls(context)

  fun getUserRules(): String = FilterPreferences.getUserRules(context)

  fun hasAnyActiveSource(): Boolean = getSubscriptionUrls().isNotEmpty() || getUserRules().isNotBlank()

  fun loadRuleTexts(): List<String> {
    val rules = mutableListOf<String>()
    getSubscriptionUrls().forEach { url ->
      val file = fileForUrl(url)
      if (file.exists()) {
        val text = file.readText()
        if (text.isNotBlank()) rules += text
      }
    }
    val userRules = getUserRules()
    if (userRules.isNotBlank()) rules += userRules
    return rules
  }

  fun updateSubscriptions(force: Boolean = false): FilterUpdateSummary {
    if (!FilterPreferences.isEnabled(context)) {
      return FilterUpdateSummary(updatedCount = 0, failedCount = 0)
    }
    val urls = getSubscriptionUrls()
    if (urls.isEmpty()) {
      prefs.edit {
        putLong(FilterPreferences.KEY_LAST_UPDATED_AT, System.currentTimeMillis())
        remove(FilterPreferences.KEY_LAST_UPDATE_ERROR)
      }
      return FilterUpdateSummary(updatedCount = 0, failedCount = 0)
    }
    if (!force && !FilterPreferences.shouldRefresh(context)) {
      return FilterUpdateSummary(updatedCount = 0, failedCount = 0, lastError = FilterPreferences.getLastUpdateError(context))
    }

    var updated = 0
    var failed = 0
    var lastError: String? = null
    urls.forEach { url ->
      runCatching {
        val text = download(url)
        fileForUrl(url).writeText(text)
        updated += 1
      }.onFailure {
        failed += 1
        lastError = it.message ?: url
      }
    }

    prefs.edit {
      putLong(FilterPreferences.KEY_LAST_UPDATED_AT, System.currentTimeMillis())
      if (lastError == null) {
        remove(FilterPreferences.KEY_LAST_UPDATE_ERROR)
      } else {
        putString(FilterPreferences.KEY_LAST_UPDATE_ERROR, lastError)
      }
    }
    return FilterUpdateSummary(updated, failed, lastError)
  }

  private fun download(url: String): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
      connectTimeout = 15_000
      readTimeout = 30_000
      requestMethod = "GET"
      setRequestProperty("User-Agent", "NPViewer Filter Updater")
    }
    connection.connect()
    if (connection.responseCode !in 200..299) {
      error("HTTP ${connection.responseCode} for $url")
    }
    connection.inputStream.bufferedReader().use { reader ->
      return reader.readText()
    }
  }

  private fun fileForUrl(url: String): File = File(filtersDir, "${sha1(url)}.txt")

  private fun sha1(value: String): String = MessageDigest.getInstance("SHA-1").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
