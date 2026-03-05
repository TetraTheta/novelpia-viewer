package io.github.tetratheta.novelpiaviewer

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

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
        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit { putBoolean("pending_clear_cache", true) }
        Toast.makeText(context, R.string.msg_clear_cache, Toast.LENGTH_SHORT).show()
        true
      }

      findPreference<Preference>("clear_webstorage")?.setOnPreferenceClickListener {
        WebStorage.getInstance().deleteAllData()
        Toast.makeText(context, R.string.msg_clear_webstorage, Toast.LENGTH_SHORT).show()
        true
      }

      findPreference<Preference>("clear_cookie")?.setOnPreferenceClickListener {
        AlertDialog.Builder(requireContext()).setTitle(R.string.title_clear_cookie)
          .setMessage(R.string.msg_clear_cookie_warning).setPositiveButton(R.string.btn_delete) { _, _ ->
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            Toast.makeText(context, R.string.msg_clear_cookie, Toast.LENGTH_SHORT).show()
          }.setNegativeButton(R.string.btn_cancel, null).show()
        true
      }
    }
  }
}
