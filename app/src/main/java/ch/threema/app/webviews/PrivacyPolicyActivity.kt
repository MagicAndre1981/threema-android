package ch.threema.app.webviews

import android.content.Context
import android.os.Bundle
import androidx.core.view.isInvisible
import ch.threema.android.buildActivityIntent
import ch.threema.app.R
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("PrivacyPolicyActivity")

class PrivacyPolicyActivity : SimpleWebViewActivity() {
    init {
        logScreenVisibility(logger)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.extras?.getBoolean(HIDE_CONNECTION_INDICATOR, false) == true) {
            connectionIndicator?.isInvisible = true
        }
    }

    override fun getWebViewTitle() = R.string.privacy_policy

    override fun getWebViewUrl(isDarkTheme: Boolean) = ConfigUtils.getPrivacyPolicyURL(
        /* context = */
        this,
        /* isDarkTheme = */
        isDarkTheme,
    )

    companion object {
        private const val HIDE_CONNECTION_INDICATOR = "hideConnectionIndicator"

        @JvmStatic
        fun createIntent(context: Context, hideConnectionIndicator: Boolean = false) = buildActivityIntent<PrivacyPolicyActivity>(context) {
            putExtra(HIDE_CONNECTION_INDICATOR, hideConnectionIndicator)
        }
    }
}
