package io.github.tetratheta.novelpiaviewer

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

class SettingsActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_settings)
    if (savedInstanceState == null) {
      supportFragmentManager.beginTransaction().replace(R.id.settings_container, SettingsFragment()).commit()
    }
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    title = getString(R.string.title_activity_settings)
  }

  override fun onSupportNavigateUp(): Boolean {
    finish()
    return true
  }

  class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
      setPreferencesFromResource(R.xml.root_preferences, rootKey)

      findPreference<Preference>("clear_cache")?.setOnPreferenceClickListener {
        lifecycleScope.launch {
          withContext(Dispatchers.IO) { requireContext().cacheDir.deleteRecursively() }
          Toast.makeText(context, R.string.msg_clear_cache, Toast.LENGTH_SHORT).show()
          updateCacheSummary()
        }
        true
      }

      findPreference<Preference>("clear_webstorage")?.setOnPreferenceClickListener {
        WebStorage.getInstance().deleteAllData()
        Toast.makeText(context, R.string.msg_clear_webstorage, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch { updateWebStorageSummary() }
        true
      }

      findPreference<Preference>("clear_cookie")?.setOnPreferenceClickListener {
        AlertDialog.Builder(requireContext()).setTitle(R.string.title_clear_cookie)
          .setMessage(R.string.msg_clear_cookie_warning).setPositiveButton(R.string.btn_delete) { _, _ ->
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            Toast.makeText(context, R.string.msg_clear_cookie, Toast.LENGTH_SHORT).show() //updateStorageSummaries()
            // This is lame
            // SQLite, where the cookies are saved, does not shrink after the cookie removal
            // But this is just saying: "cookies are deleted so it is now 0 B!"
            findPreference<Preference>("clear_cookie")?.summary = "${getString(R.string.pref_desc_clear_cookie)}\n0 B"
          }.setNegativeButton(R.string.btn_cancel, null).show()
        true
      }
    }

    override fun onResume() {
      super.onResume()
      lifecycleScope.launch { updateStorageSummaries() }
    }

    private suspend fun updateStorageSummaries() {
      coroutineScope {
        launch { updateCacheSummary() }
        launch { updateWebStorageSummary() }
        launch { updateCookieSummary() }
      }
    }

    private suspend fun updateCacheSummary() {
      val cachePref = findPreference<Preference>("clear_cache") ?: return
      val size = withContext(Dispatchers.IO) {
        requireContext().cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
      }
      cachePref.summary = "${getString(R.string.pref_desc_clear_cache)}\n${formatSize(size)}"
    }

    private suspend fun updateWebStorageSummary() {
      val webstoragePref = findPreference<Preference>("clear_webstorage") ?: return
      val size = suspendCancellableCoroutine { cont ->
        WebStorage.getInstance().getOrigins { origins ->
          val total = origins?.values?.filterIsInstance<WebStorage.Origin>()?.sumOf { it.usage } ?: 0L
          cont.resume(total)
        }
      }
      webstoragePref.summary = "${getString(R.string.pref_desc_clear_webstorage)}\n${formatSize(size)}"
    }

    private suspend fun updateCookieSummary() {
      val cookiePref = findPreference<Preference>("clear_cookie") ?: return
      val size = withContext(Dispatchers.IO) {
        val cookieFile = File(requireContext().dataDir, "app_webview/Default/Cookies")
        if (cookieFile.exists()) cookieFile.length() else 0L
      }
      cookiePref.summary = "${getString(R.string.pref_desc_clear_cookie)}\n${formatSize(size)}"
    }

    private fun formatSize(bytes: Long): String = when {
      bytes <= 0L -> "0 B"
      bytes < 1_024L -> "$bytes B"
      bytes < 1_048_576L -> "${bytes / 1_024} KB"
      else -> "%.1f MB".format(bytes / 1_048_576.0)
    }
  }
}
