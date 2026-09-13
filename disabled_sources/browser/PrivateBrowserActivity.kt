package com.calcvault.ui.browser

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.*
import android.webkit.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.calcvault.emotional.ThemeEngine
import com.calcvault.emotional.themes.ThemeApplicator
import com.calcvault.messaging.AppendOnlyMessageDB
import com.calcvault.storage.USBStorageEngine
import com.calcvault.storage.provider.StorageManager
import com.calcvault.utils.SessionManager
import java.io.File

/**
 * PrivateBrowserActivity
 *
 * Built-in private browser.
 */
class PrivateBrowserActivity : AppCompatActivity() {

    private lateinit var webView    : WebView
    private lateinit var etUrl      : EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var btnBack    : ImageButton
    private lateinit var btnForward : ImageButton
    private lateinit var btnRefresh : ImageButton
    private lateinit var btnClose   : ImageButton
    private lateinit var tvPartnerMood: TextView

    private lateinit var storageEngine: USBStorageEngine
    private lateinit var messageDB    : AppendOnlyMessageDB
    private lateinit var themeEngine  : ThemeEngine

    private val homeUrl = "https://www.google.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE)

        storageEngine = USBStorageEngine.getInstance(this)
        messageDB     = AppendOnlyMessageDB(storageEngine)
        themeEngine   = ThemeEngine(this)

        if (storageEngine.state != USBStorageEngine.State.UNLOCKED && !StorageManager.isReady()) {
            finish()
            return
        }

        buildUI()
        
        // Fix: Apply theme AFTER buildUI (which calls setContentView)
        ThemeApplicator.applyActive(this)
        
        configureWebView()
        webView.loadUrl(homeUrl)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun buildUI() {
        val theme = themeEngine.getCurrentTheme()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (ThemeApplicator.activeTheme == ThemeApplicator.ThemeChoice.NONE) theme.backgroundStart else android.graphics.Color.TRANSPARENT)
        }
        setContentView(root)

        val statusBar = RelativeLayout(this).apply {
            setBackgroundColor(if (ThemeApplicator.activeTheme == ThemeApplicator.ThemeChoice.NONE) theme.surfaceColor else 0x88000000.toInt())
            setPadding(32, 48, 32, 16)
        }
        root.addView(statusBar)

        tvPartnerMood = TextView(this).apply {
            val partnerId = SessionManager.partnerUserId.ifBlank { "USER_B" }
            val mood = messageDB.getLatestMood(partnerId) ?: "Neutral"
            text = "Partner: $mood"
            setTextColor(theme.secondaryText)
            textSize = 12f
        }
        statusBar.addView(tvPartnerMood)

        val urlBar = LinearLayout(this).apply {
            orientation    = LinearLayout.HORIZONTAL
            setBackgroundColor(if (ThemeApplicator.activeTheme == ThemeApplicator.ThemeChoice.NONE) theme.surfaceColor else 0x88000000.toInt())
            setPadding(8, 8, 8, 8)
            gravity        = Gravity.CENTER_VERTICAL
        }
        root.addView(urlBar)

        btnBack = ImageButton(this).apply {
            setBackgroundColor(0x00000000)
            setImageResource(android.R.drawable.ic_media_previous)
            setColorFilter(theme.primaryText)
            setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        }
        urlBar.addView(btnBack)

        btnForward = ImageButton(this).apply {
            setBackgroundColor(0x00000000)
            setImageResource(android.R.drawable.ic_media_next)
            setColorFilter(theme.primaryText)
            setOnClickListener { if (webView.canGoForward()) webView.goForward() }
        }
        urlBar.addView(btnForward)

        etUrl = EditText(this).apply {
            hint        = "Search or enter URL"
            setTextColor(theme.primaryText)
            setHintTextColor(theme.secondaryText)
            background  = null
            textSize    = 14f
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(16, 0, 16, 0)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                    navigate(text.toString())
                    true
                } else false
            }
        }
        urlBar.addView(etUrl)

        btnRefresh = ImageButton(this).apply {
            setBackgroundColor(0x00000000)
            setImageResource(android.R.drawable.ic_popup_sync)
            setColorFilter(theme.primaryText)
            setOnClickListener { webView.reload() }
        }
        urlBar.addView(btnRefresh)

        btnClose = ImageButton(this).apply {
            setBackgroundColor(0x00000000)
            setImageResource(android.R.drawable.ic_delete)
            setColorFilter(theme.primaryText)
            setOnClickListener { finish() }
        }
        urlBar.addView(btnClose)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 6)
            max          = 100
            progressTintList = android.content.res.ColorStateList.valueOf(theme.accentColor)
            visibility   = View.GONE
        }
        root.addView(progressBar)

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            setBackgroundColor(android.graphics.Color.WHITE)
        }
        root.addView(webView)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val ws = webView.settings
        ws.javaScriptEnabled = true
        ws.domStorageEnabled = true
        ws.cacheMode = WebSettings.LOAD_DEFAULT
        ws.setSupportZoom(true)
        ws.builtInZoomControls = true
        ws.displayZoomControls = false

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                progressBar.visibility = View.VISIBLE
                etUrl.setText(url)
                btnBack.isEnabled = view.canGoBack()
                btnForward.isEnabled = view.canGoForward()
            }

            override fun onPageFinished(view: WebView, url: String) {
                progressBar.visibility = View.GONE
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar.progress = newProgress
                if (newProgress == 100) progressBar.visibility = View.GONE
            }
        }
    }

    private fun navigate(input: String) {
        var url = input.trim()
        if (url.isEmpty()) return
        if (!url.contains(".") || url.contains(" ")) {
            url = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(url, "UTF-8")
        } else if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        webView.loadUrl(url)
    }

    override fun onResume() {
        super.onResume()
        ThemeApplicator.applyActive(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.apply {
            stopLoading()
            clearCache(true)
            destroy()
        }
        ThemeApplicator.detach(this)
    }
}
