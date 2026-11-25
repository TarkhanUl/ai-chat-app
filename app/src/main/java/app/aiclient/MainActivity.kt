package app.aiclient

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val prefs by lazy { getSharedPreferences("cfg", MODE_PRIVATE) }

    private val picker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        fileCallback?.onReceiveValue(
            WebChromeClient.FileChooserParams.parseResult(res.resultCode, res.data)
        )
        fileCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            fitsSystemWindows = true
        }
        setContentView(web)

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(false)
            userAgentString = userAgentString.replace("; wv", "")
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val scheme = r.url.scheme ?: return false
                if (scheme == "http" || scheme == "https") return false
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, r.url)) }
                return true
            }

            override fun onPageStarted(v: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(v, url, favicon)
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                v: WebView,
                cb: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = cb
                return try {
                    picker.launch(params.createIntent())
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    false
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState)
        } else {
            startFlow(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_CHANGE_URL) askUrl()
    }

    private fun startFlow(i: Intent?) {
        val saved = prefs.getString("url", null)
        when {
            i?.action == ACTION_CHANGE_URL || saved == null -> askUrl()
            else -> web.loadUrl(saved)
        }
    }

    private fun askUrl() {
        val input = EditText(this).apply {
            hint = "https://..."
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setText(prefs.getString("url", ""))
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("Адрес ИИ-сайта")
            .setView(input)
            .setCancelable(prefs.contains("url"))
            .setPositiveButton("Открыть") { _, _ ->
                var u = input.text.toString().trim()
                if (u.isNotEmpty()) {
                    if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
                    prefs.edit().putString("url", u).apply()
                    web.loadUrl(u)
                } else if (prefs.contains("url")) {
                    web.loadUrl(prefs.getString("url", "")!!)
                } else askUrl()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CHANGE_URL = "app.aiclient.CHANGE_URL"
    }
}
