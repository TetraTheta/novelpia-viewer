package io.github.tetratheta.novelpiaviewer

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.progressindicator.LinearProgressIndicator

class MainActivity : AppCompatActivity() {
  private lateinit var errorView: LinearLayout
  private lateinit var progressBar: LinearProgressIndicator
  private lateinit var retryButton: Button
  private lateinit var swipeRefresh: SwipeRefreshLayout
  private lateinit var webView: WebView
  private var lastBackPress = 0L
  private val scrollPositions = LinkedHashMap<String, Int>(16, 0.75f, true)

  companion object {
    private const val MAX_SCROLL_CACHE_SIZE = 50
  }

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContentView(R.layout.activity_main)

    errorView = findViewById(R.id.error_view)
    progressBar = findViewById(R.id.progress_bar)
    retryButton = findViewById(R.id.retry_button)
    swipeRefresh = findViewById(R.id.swipe_refresh)
    webView = findViewById(R.id.webview)

    ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
      val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
      insets
    }

    webView.settings.apply {
      javaScriptEnabled = true
      domStorageEnabled = true
    }

    webView.webChromeClient = object : WebChromeClient() {
      override fun onProgressChanged(view: WebView, newProgress: Int) {
        if (newProgress < 100) {
          progressBar.visibility = View.VISIBLE
          progressBar.progress = newProgress
        } else {
          progressBar.visibility = View.GONE
        }
      }
    }

    webView.webViewClient = object : WebViewClient() {
      override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val host = request.url.host ?: return false
        return if (host.endsWith("novelpia.com")) {
          saveScrollPosition()
          false
        } else {
          startActivity(Intent(Intent.ACTION_VIEW, request.url))
          true
        }
      }

      override fun onPageFinished(view: WebView, url: String?) {
        swipeRefresh.isRefreshing = false
        errorView.visibility = View.GONE
        swipeRefresh.visibility = View.VISIBLE

        url?.let { scrollPositions[it]?.let { y -> view.scrollTo(0, y) } }
      }

      override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (!request.isForMainFrame) return
        swipeRefresh.isRefreshing = false
        swipeRefresh.visibility = View.GONE
        errorView.visibility = View.VISIBLE
      }
    }

    retryButton.setOnClickListener {
      errorView.visibility = View.GONE
      swipeRefresh.visibility = View.VISIBLE
      webView.reload()
    }

    swipeRefresh.setOnRefreshListener {
      webView.reload()
    }

    webView.setOnLongClickListener {
      AlertDialog.Builder(this).setItems(arrayOf("설정")) { _, which ->
        if (which == 0) startActivity(Intent(this, SettingsActivity::class.java))
      }.show()
      true
    }

    if (savedInstanceState != null) {
      webView.restoreState(savedInstanceState)
    } else {
      val deepLink = intent?.data?.toString()
      webView.loadUrl(deepLink ?: "https://novelpia.com/mybook")
    }

    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        if (webView.canGoBack()) {
          saveScrollPosition()
          webView.goBack()
          return
        }
        val now = System.currentTimeMillis()
        if (now - lastBackPress < 2000) {
          finish()
        } else {
          lastBackPress = now
          Toast.makeText(this@MainActivity, R.string.press_twice_to_exit, Toast.LENGTH_SHORT).show()
        }
      }
    })
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    val url = webView.url ?: ""
    if (url.contains(Regex("novelpia\\.com/viewer/"))) {
      val prefs = PreferenceManager.getDefaultSharedPreferences(this)
      if (prefs.getString("volume_behavior", "move_page") == "move_page") {
        val upPrev = prefs.getString("volume_direction", "up_prev") == "up_prev"
        when (keyCode) {
          KeyEvent.KEYCODE_VOLUME_UP -> {
            val sel = if (upPrev) "#novel_drawing_left" else "#novel_drawing_right"
            webView.evaluateJavascript("document.querySelector('$sel')?.click()", null)
            return true
          }

          KeyEvent.KEYCODE_VOLUME_DOWN -> {
            val sel = if (upPrev) "#novel_drawing_right" else "#novel_drawing_left"
            webView.evaluateJavascript("document.querySelector('$sel')?.click()", null)
            return true
          }
        }
      }

    }
    return super.onKeyDown(keyCode, event)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    intent.data?.let { webView.loadUrl(it.toString()) }
  }

  override fun onResume() {
    super.onResume()
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    if (prefs.getBoolean("pending_clear_cache", false)) {
      webView.clearCache(true)
      WebStorage.getInstance().deleteAllData()
      prefs.edit { putBoolean("pending_clear_cache", false) }
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    webView.saveState(outState)
  }

  private fun saveScrollPosition() {
    val url = webView.url ?: return
    val scrollY = webView.scrollY
    if (scrollY > 0) {
      if (scrollPositions.size >= MAX_SCROLL_CACHE_SIZE) {
        scrollPositions.remove(scrollPositions.keys.first())
      }
      scrollPositions[url] = scrollY
    }
  }
}
