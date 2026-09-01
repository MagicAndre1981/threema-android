package ch.threema.app.webviews

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.annotation.StringRes
import androidx.core.content.getSystemService
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import ch.threema.android.showToast
import ch.threema.app.R
import ch.threema.app.activities.ThreemaToolbarActivity
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.dialogs.GenericAlertDialog.DialogClickListener
import ch.threema.app.ui.InsetSides.Companion.lbr
import ch.threema.app.ui.applyDeviceInsetsAsPadding
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.consume
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.LinearProgressIndicator

private val logger = getThreemaLogger("SimpleWebViewActivity")

abstract class SimpleWebViewActivity : ThreemaToolbarActivity(), DialogClickListener {
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var webView: WebView

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val toolbar = findViewById<MaterialToolbar>(R.id.material_toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setTitle(getWebViewTitle())

        progressBar = findViewById(R.id.progress)
        webView = findViewById(R.id.simple_webview)
        webView.getSettings().javaScriptEnabled = requiresJavaScript()
        webView.setWebViewClient(ThreemaWebViewClient(::onNavigationRequest))

        if (requiresConnection()) {
            webView.setWebChromeClient(object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress >= 99) {
                        progressBar.isInvisible = true
                    } else {
                        progressBar.progress = newProgress
                    }
                }
            })
            checkConnection()
        } else {
            progressBar.isVisible = false
            loadWebView()
        }
    }

    override fun handleDeviceInsets() {
        super.handleDeviceInsets()
        findViewById<View?>(R.id.webview_scroller).applyDeviceInsetsAsPadding(lbr())
    }

    private fun onNavigationRequest(url: Uri): Boolean = consume {
        // We don't want to allow navigating to other pages within the web view,
        // so instead we open the URL in the external browser
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url))
        } catch (_: ActivityNotFoundException) {
            logger.error("No activity found to open URL (scheme={}, host={})", url.scheme, url.host)
            showToast(R.string.no_activity_for_intent)
        }
    }

    private fun loadWebView() {
        webView.loadUrl(getWebViewUrl(ConfigUtils.isTheDarkSide(this)))
    }

    private fun checkConnection() {
        if (getSystemService<ConnectivityManager>()?.activeNetworkInfo?.isConnected == true) {
            loadWebView()
        } else {
            GenericAlertDialog.newInstance(
                getWebViewTitle(),
                R.string.internet_connection_required,
                R.string.retry,
                R.string.cancel,
            )
                .show(supportFragmentManager, DIALOG_TAG_NO_CONNECTION)
        }
    }

    override fun getLayoutResource() = R.layout.activity_simple_webview

    override fun onYes(tag: String?, data: Any?) {
        checkConnection()
    }

    override fun onNo(tag: String?, data: Any?) {
        finish()
    }

    @StringRes
    protected abstract fun getWebViewTitle(): Int

    protected abstract fun getWebViewUrl(isDarkTheme: Boolean): String

    protected open fun requiresConnection(): Boolean = true

    protected open fun requiresJavaScript(): Boolean = false

    companion object {
        private const val DIALOG_TAG_NO_CONNECTION = "nc"
    }
}
