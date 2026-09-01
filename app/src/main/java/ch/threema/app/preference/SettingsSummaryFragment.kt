package ch.threema.app.preference

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.launch
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.children
import ch.threema.android.ToastDuration
import ch.threema.android.getSerializableExtraCompat
import ch.threema.android.showToast
import ch.threema.app.BuildConfig
import ch.threema.app.BuildFlavor
import ch.threema.app.R
import ch.threema.app.activities.ComposeMessageActivity
import ch.threema.app.activities.referral.ReferralActivity
import ch.threema.app.applock.CheckAppLockContract
import ch.threema.app.apptaskexecutor.AppTaskExecutor
import ch.threema.app.asynctasks.AddContactRestrictionPolicy
import ch.threema.app.asynctasks.BasicAddOrUpdateContactBackgroundTask
import ch.threema.app.asynctasks.ContactAvailable
import ch.threema.app.asynctasks.ContactCreated
import ch.threema.app.asynctasks.ContactModified
import ch.threema.app.asynctasks.ContactResult
import ch.threema.app.backupcenter.BackupCenterActivity
import ch.threema.app.di.awaitAppFullyReady
import ch.threema.app.dialogs.BottomSheetAbstractDialog.BottomSheetDialogCallback
import ch.threema.app.dialogs.BottomSheetGridDialog
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.dialogs.GenericProgressDialog
import ch.threema.app.home.usecases.SetUpThreemaChannelUseCase
import ch.threema.app.multidevice.LinkedDevicesActivity
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.preference.SettingsActivity.Companion.EXTRA_INITIAL_SCREEN
import ch.threema.app.preference.SettingsActivity.InitialScreen
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.protocolsteps.ValidContactsLookupSteps
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.usecases.CheckBackupsFeatureEnabledUseCase
import ch.threema.app.usecases.GetBottomSheetAppShareTargetsUseCase
import ch.threema.app.usecases.GetInviteFriendIntentUseCase
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.DialogUtil
import ch.threema.app.utils.executor.BackgroundExecutor
import ch.threema.app.utils.logScreenVisibility
import ch.threema.app.webclient.activities.SessionsActivity
import ch.threema.app.webviews.WorkExplainActivity
import ch.threema.base.HAS_DEV_FEATURES
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.PredefinedContact
import ch.threema.data.datatypes.PredefinedContact.Companion.THREEMA_CHANNEL_IDENTITY
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.models.AcquaintanceLevel
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("SettingsSummaryFragment")

class SettingsSummaryFragment : ThreemaPreferenceFragment(), GenericAlertDialog.DialogClickListener, BottomSheetDialogCallback {
    init {
        logScreenVisibility(logger)
    }

    private val preferenceService: PreferenceService by inject()
    private val appRestrictions: AppRestrictions by inject()
    private val validContactsLookupSteps: ValidContactsLookupSteps by inject()
    private val appTaskExecutor: AppTaskExecutor by inject()
    private val contactModelRepository: ContactModelRepository by inject()
    private val multiDeviceManager: MultiDeviceManager by inject()
    private val setUpThreemaChannelUseCase: SetUpThreemaChannelUseCase by inject()
    private val getBottomSheetAppShareTargetsUseCase: GetBottomSheetAppShareTargetsUseCase by inject()
    private val getInviteFriendIntentUseCase: GetInviteFriendIntentUseCase by inject()
    private val checkBackupsFeatureEnabledUseCase: CheckBackupsFeatureEnabledUseCase by inject()
    private val dispatcherProvider: DispatcherProvider by inject()
    private val backgroundExecutor by lazy { BackgroundExecutor() }

    private val checkLockToOpenBackupCenter = registerForActivityResult(CheckAppLockContract()) { unlocked ->
        if (unlocked) {
            startActivity(BackupCenterActivity.createIntent(requireContext()))
        }
    }

    private var selectedPrefView: View? = null

    override fun initializePreferences() {
        getPrefKeyToSummaryMap().forEach { (prefKey, summary) ->
            getPref<Preference>(prefKey).summary = summary
        }

        lifecycleScope.launch {
            awaitAppFullyReady()

            setUpAppSettingsSection()
            setUpBackupCenter()
            setUpLinkedDevicesSection()
            setUpCommunityAndSharingSection()
            setUpAdvancedSection()
            setUpPromotionalSection()
            setUpReferralBanner()

            if (ConfigUtils.isTabletLayout()) {
                selectActivePreference()
            }
        }
    }

    private fun setUpAppSettingsSection() {
        if (appRestrictions.isCallsDisabled()) {
            getPref<Preference>("pref_key_calls").isVisible = false
        }
    }

    private fun setUpBackupCenter() {
        getPref<Preference>(R.string.preferences__backup_center).run {
            @Suppress("KotlinConstantConditions", "SimplifyBooleanWithConstants")
            if (BuildConfig.CROSS_PLATFORM_BACKUPS_ENABLED && checkBackupsFeatureEnabledUseCase.call()) {
                isVisible = true
                onClick {
                    checkLockToOpenBackupCenter.launch()
                }
            } else {
                isVisible = false
            }
        }
    }

    private fun setUpLinkedDevicesSection() {
        getPref<Preference>(R.string.preferences__multi_device).run {
            if (ConfigUtils.isMultiDeviceEnabled() || multiDeviceManager.isMultiDeviceActive) {
                onClick {
                    logger.info("MD clicked")
                    startActivity(LinkedDevicesActivity.createIntent(requireContext()))
                }
            } else {
                isVisible = false
            }
        }
        getPref<Preference>(R.string.preferences__web_desktop).run {
            if (appRestrictions.isWebDisabled()) {
                isVisible = false
            } else {
                onClick {
                    logger.info("Desktop/Web button clicked")
                    startActivity(SessionsActivity.createIntent(requireContext()))
                }
            }
        }
        hideCategoryIfNoVisiblePreferencesInside(R.string.preferences__linked_devices)
    }

    private fun hideCategoryIfNoVisiblePreferencesInside(@StringRes key: Int) {
        getPref<PreferenceCategory>(key).run {
            if (children.none { it.isVisible }) {
                isVisible = false
            }
        }
    }

    private fun setUpCommunityAndSharingSection() {
        if (ConfigUtils.isOnPremBuild()) {
            getPref<Preference>(getString(R.string.preferences__rate)).isVisible = false
        }

        getPref<Preference>(R.string.preferences__invite_a_friend).run {
            if (ConfigUtils.isWorkBuild()) {
                isVisible = false
            } else {
                onClick {
                    logger.info("'Invite a friend' clicked")
                    inviteFriend()
                }
            }
        }

        getPref<Preference>(R.string.preferences__threema_channel).run {
            if (ConfigUtils.isWorkBuild() || isThreemaChannelAlreadyAdded()) {
                isVisible = false
            } else {
                onClick {
                    logger.info("Threema Channel clicked")
                    startThreemaChannel()
                }
            }
        }

        hideCategoryIfNoVisiblePreferencesInside(R.string.preferences__community_and_sharing)
    }

    private fun setUpAdvancedSection() {
        @Suppress("SimplifyBooleanWithConstants")
        if (!HAS_DEV_FEATURES || !preferenceService.showDeveloperMenu()) {
            getPref<Preference>("pref_key_developers").isVisible = false
        }
    }

    private fun setUpPromotionalSection() {
        setUpWorkBanner()
        setUpReferralBanner()
    }

    private fun setUpWorkBanner() {
        getPref<Preference>("pref_key_work").run {
            if (ConfigUtils.isWorkBuild()) {
                isVisible = false
            } else {
                onClick {
                    startActivity(WorkExplainActivity.createIntent(requireContext()))
                }
            }
        }
    }

    private fun setUpReferralBanner() {
        getPref<Preference>(R.string.preferences__referral_banner).run {
            if (ConfigUtils.isReferralProgramEnabled()) {
                isVisible = true
                onClick {
                    startActivity(ReferralActivity.createIntent(requireContext()))
                }
            } else {
                isVisible = false
            }
        }
    }

    private fun selectActivePreference() {
        // Select active preference. Retry it every 100ms (until layout is ready)
        val prefKey = when (requireActivity().intent.getSerializableExtraCompat<InitialScreen>(EXTRA_INITIAL_SCREEN)) {
            InitialScreen.NOTIFICATIONS -> "pref_key_notifications"
            InitialScreen.MEDIA -> "pref_key_particular_settings"
            InitialScreen.SECURITY -> "pref_key_security"
            null -> "pref_key_privacy"
        }
        lifecycleScope.launch {
            for (i in 0..10) {
                if (selectActivePreference(prefKey)) {
                    break
                }
                delay(100.milliseconds)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar?.setNavigationOnClickListener {
            requireActivity().finish()
        }
    }

    /**
     * This method sets the background color of the preference header on tablets.
     * The currently selected preference header is marked as selected and the previously selected
     * preference header is reset to the default color.
     *
     * @param prefKey the key of the preference
     * @return true if the preference background could be successfully set (or if a single pane is used), false otherwise
     */
    fun selectActivePreference(prefKey: String): Boolean {
        if (!ConfigUtils.isTabletLayout() || !isAdded || context == null) {
            return true
        }
        selectedPrefView?.setBackgroundColor(Color.TRANSPARENT)

        val preference = getPrefOrNull<Preference>(prefKey) ?: return false
        val listView = listView ?: return false
        val title = preference.title ?: return false
        selectedPrefView = listView.children.find { child ->
            child.findViewById<TextView>(android.R.id.title)?.text == title
        }
        selectedPrefView?.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.list_item_background_checked))
        return selectedPrefView != null
    }

    private fun inviteFriend() {
        lifecycleScope.launch {
            GenericProgressDialog.newInstance(R.string.title_invite_friend, R.string.please_wait)
                .show(parentFragmentManager, DIALOG_TAG_INVITE_A_FRIEND_PROGRESS)
            val bottomSheetItems = try {
                withContext(dispatcherProvider.worker) {
                    getBottomSheetAppShareTargetsUseCase.call()
                }
            } finally {
                DialogUtil.dismissDialog(parentFragmentManager, DIALOG_TAG_INVITE_A_FRIEND_PROGRESS, true)
            }
            if (bottomSheetItems.isEmpty()) {
                return@launch
            }
            val dialog = BottomSheetGridDialog.newInstance(R.string.invite_via, ArrayList(bottomSheetItems))
            dialog.setTargetFragment(this@SettingsSummaryFragment, 0)
            dialog.show(parentFragmentManager, DIALOG_TAG_SHARE_WITH)
        }
    }

    private fun isThreemaChannelAlreadyAdded(): Boolean =
        contactModelRepository.getByIdentity(THREEMA_CHANNEL_IDENTITY)
            ?.data
            ?.acquaintanceLevel != AcquaintanceLevel.GROUP_OR_DELETED

    private fun startThreemaChannel() {
        if (isThreemaChannelAlreadyAdded()) {
            launchThreemaChannelConversation()
        } else {
            GenericAlertDialog.newInstance(R.string.threema_channel, R.string.threema_channel_intro, R.string.ok, R.string.cancel, 0)
                .setTargetFragment(this)
                .show(parentFragmentManager, DIALOG_TAG_THREEMA_CHANNEL_VERIFY)
        }
    }

    private fun launchThreemaChannelConversation() {
        startActivity(
            ComposeMessageActivity.createIntent(
                context = requireContext(),
                conversationId = ContactConversationId(THREEMA_CHANNEL_IDENTITY),
            ),
        )
    }

    override fun onYes(tag: String?, data: Any?) {
        if (tag == DIALOG_TAG_THREEMA_CHANNEL_VERIFY) {
            addThreemaChannel()
        }
    }

    // TODO(ANDR-4481): This needs refactoring
    @SuppressLint("StaticFieldLeak")
    private fun addThreemaChannel() {
        val threemaChannelContact = PredefinedContact.threemaChannelContact
        if (threemaChannelContact == null) {
            logger.info("Cannot add threema channel contact in {} build environment", BuildFlavor.current.buildEnvironment.name)
            return
        }
        logger.info("Adding Threema channel")
        backgroundExecutor.execute(
            object : BasicAddOrUpdateContactBackgroundTask(
                identity = threemaChannelContact.identity,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
                validContactsLookupSteps = validContactsLookupSteps,
                contactModelRepository = contactModelRepository,
                addContactRestrictionPolicy = AddContactRestrictionPolicy.IGNORE,
                appRestrictions = appRestrictions,
                expectedPublicKey = threemaChannelContact.publicKey,
            ) {
                override fun onBefore() {
                    GenericProgressDialog.newInstance(R.string.threema_channel, R.string.please_wait)
                        .show(parentFragmentManager, DIALOG_TAG_THREEMA_CHANNEL_PROGRESS)
                }

                override fun onFinished(result: ContactResult) {
                    DialogUtil.dismissDialog(parentFragmentManager, DIALOG_TAG_THREEMA_CHANNEL_PROGRESS, true)

                    if (result is ContactAvailable) {
                        launchThreemaChannelConversation()
                        getPrefOrNull<Preference>(R.string.preferences__threema_channel)?.isVisible = false
                        if (result is ContactCreated || (result is ContactModified && result.acquaintanceLevelChanged)) {
                            appTaskExecutor.scheduleTask {
                                setUpThreemaChannelUseCase.call()
                            }
                        }
                    } else {
                        showToast(R.string.internet_connection_required, ToastDuration.LONG)
                    }
                }
            },
        )
    }

    override fun onSelected(tag: String?, data: String?) {
        if (!tag.isNullOrEmpty()) {
            sendInviteToOtherApp(packageName = tag)
        }
    }

    private fun sendInviteToOtherApp(packageName: String) {
        try {
            val intent = getInviteFriendIntentUseCase.call(packageName)
            startActivity(intent)
        } catch (e: Exception) {
            logger.error("Failed to send invite", e)
            showToast(R.string.no_activity_for_mime_type, ToastDuration.LONG)
        }
    }

    override fun getPreferenceTitleResource() = R.string.menu_settings

    override fun getPreferenceResource() = R.xml.preference_headers

    private fun getPrefKeyToSummaryMap(): Map<String, String> = mapOf(
        "pref_key_privacy" to getSummaryFromItems(
            R.string.prefs_header_contacts,
            R.string.prefs_header_chat,
            R.string.prefs_header_lists,
        ),
        "pref_key_security" to getSummaryFromItems(
            R.string.prefs_title_access_protection,
            R.string.prefs_masterkey,
        ),
        "pref_key_appearance" to getSummaryFromItems(
            R.string.prefs_theme,
            R.string.prefs_emoji_style,
            R.string.prefs_language_override,
            R.string.prefs_title_fontsize,
            R.string.prefs_contact_list_title,
        ),
        "pref_key_notifications" to getSummaryFromItems(
            R.string.prefs_voice_call_sound,
            R.string.prefs_vibrate,
        ),
        "pref_key_chatdisplay" to getSummaryFromItems(
            R.string.prefs_header_keyboard,
            R.string.media,
        ),
        "pref_key_particular_settings" to getSummaryFromItems(
            R.string.prefs_image_size,
            R.string.prefs_auto_download_title,
            R.string.prefs_storage_mgmt_title,
        ),
        "pref_key_calls" to getSummaryFromItems(
            R.string.prefs_title_voip,
            R.string.video_calls,
            R.string.group_calls,
        ),
    )

    // TODO(ANDR-4849): This string concatenation is not valid for all locales
    private fun getSummaryFromItems(vararg items: Int): String =
        items.joinToString(separator = ", ") { item ->
            getString(item)
        }

    companion object {
        private const val DIALOG_TAG_THREEMA_CHANNEL_VERIFY = "tcv"
        private const val DIALOG_TAG_THREEMA_CHANNEL_PROGRESS = "tcp"
        private const val DIALOG_TAG_INVITE_A_FRIEND_PROGRESS = "ifp"
        private const val DIALOG_TAG_SHARE_WITH = "wsw"
    }
}
