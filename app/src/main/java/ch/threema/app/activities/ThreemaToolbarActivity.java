/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.activities;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.wizard.WizardIntroActivity;
import ch.threema.app.emojis.EmojiPicker;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.LockAppService;
import ch.threema.app.services.NotificationPreferenceService;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ConnectionIndicatorUtil;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.connection.ServerConnection;
import ch.threema.domain.protocol.connection.ConnectionState;
import ch.threema.domain.protocol.connection.ConnectionStateListener;
import ch.threema.localcrypto.MasterKey;

import static ch.threema.app.startup.AppStartupUtilKt.finishAndRestartLaterIfNotReady;

/**
 * Helper class for activities that use the new toolbar
 */
public abstract class ThreemaToolbarActivity extends ThreemaActivity implements ConnectionStateListener {
    private static final Logger logger = LoggingUtil.getThreemaLogger("ThreemaToolbarActivity");

    private AppBarLayout appBarLayout;
    private Toolbar toolbar;
    private View connectionIndicator;
    protected ServiceManager serviceManager;
    protected LockAppService lockAppService;
    protected PreferenceService preferenceService;
    private NotificationPreferenceService notificationPreferenceService;
    protected ServerConnection connection;

    @Override
    protected void onResume() {
        if (connection != null) {
            connection.addConnectionStateListener(this);
            ConnectionState connectionState = connection.getConnectionState();
            ConnectionIndicatorUtil.getInstance().updateConnectionIndicator(connectionIndicator, connectionState);
        }
        super.onResume();
    }

    @Override
    protected void onPause() {
        if (connection != null) {
            connection.removeConnectionStateListener(this);
        }
        super.onPause();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        logger.debug("onCreate");
        resetKeyboard();

        super.onCreate(savedInstanceState);

        //check master key
        MasterKey masterKey = ThreemaApplication.getMasterKey();

        if (masterKey.isLocked()) {
            startActivityForResult(new Intent(this, UnlockMasterKeyActivity.class), ThreemaActivity.ACTIVITY_ID_UNLOCK_MASTER_KEY);
            return;
        } else {
            if (ConfigUtils.isSerialLicensed() && !ConfigUtils.isSerialLicenseValid()) {
                startActivity(new Intent(this, EnterSerialActivity.class));
                finish();
                return;
            }
        }

        initServices();

        if (!(this instanceof ComposeMessageActivity)) {
            if (!this.initActivity(savedInstanceState)) {
                finish();
            }
        }

        handleDeviceInsets();
    }

    /**
     * Applies left, top and right device insets padding to the AppBarLayout {@code R.id.appbar} if present.
     */
    protected void handleDeviceInsets() {
        final @Nullable AppBarLayout appBarLayout = findViewById(R.id.appbar);
        if (appBarLayout != null) {
            ViewExtensionsKt.applyDeviceInsetsAsPadding(appBarLayout, InsetSides.ltr());
        }
    }

    protected void initServices() {
        if (serviceManager == null) {
            serviceManager = ThreemaApplication.getServiceManager();
            if (serviceManager == null) {
                // app is probably locked. This might not be possible to ever happen
                finish();
                return;
            }

            lockAppService = serviceManager.getLockAppService();
            preferenceService = serviceManager.getPreferenceService();
            notificationPreferenceService = serviceManager.getNotificationPreferenceService();
        }
    }

    /**
     * This method sets up the layout, the connection indicator, language override and screenshot blocker. It is called from onCreate() after all the basic initialization has been done.
     * Override this to do your own initialization, such as instantiating services
     *
     * @param savedInstanceState the bundle provided to onCreate()
     * @return true on success, false otherwise
     */
    protected boolean initActivity(@Nullable Bundle savedInstanceState) {
        logger.debug("initActivity");

        @LayoutRes int layoutResource = getLayoutResource();

        initServices();

        try {
            connection = serviceManager.getConnection();
        } catch (Exception e) {
            logger.info("Unable to get Threema connection.");
            finish();
        }

        if (notificationPreferenceService != null && notificationPreferenceService.getWizardRunning()) {
            startActivity(new Intent(this, WizardIntroActivity.class));
            return false;
        }

        // hide contents in app switcher and inhibit screenshots
        ConfigUtils.setScreenshotsAllowed(this, preferenceService, lockAppService);

        if (layoutResource != 0) {
            logger.debug("setContentView");

            setContentView(layoutResource);

            this.appBarLayout = findViewById(R.id.appbar);

            this.toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) {
                setSupportActionBar(toolbar);
            }
            if (appBarLayout != null) {
                MaterialToolbar materialToolbar = appBarLayout.findViewById(R.id.material_toolbar);
                if (materialToolbar != null) {
                    materialToolbar.setNavigationContentDescription(R.string.abc_action_bar_up_description);
                }
            }

            connectionIndicator = findViewById(R.id.connection_indicator);
        }

        return true;
    }

    public abstract @LayoutRes int getLayoutResource();

    public void setToolbar(Toolbar toolbar) {
        this.toolbar = toolbar;
    }

    @Nullable
    public AppBarLayout getAppBarLayout() {
        return this.appBarLayout;
    }

    public Toolbar getToolbar() {
        return this.toolbar;
    }

    protected View getConnectionIndicator() {
        return connectionIndicator;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ThreemaActivity.ACTIVITY_ID_UNLOCK_MASTER_KEY) {
            if (ThreemaApplication.getMasterKey().isLocked()) {
                new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.master_key_locked)
                    .setMessage(R.string.master_key_locked_want_exit)
                    .setPositiveButton(R.string.try_again, (dialog, whichButton) -> startActivityForResult(new Intent(ThreemaToolbarActivity.this, UnlockMasterKeyActivity.class), ThreemaActivity.ACTIVITY_ID_UNLOCK_MASTER_KEY))
                    .setNegativeButton(R.string.cancel, (dialog, which) -> finish()).show();
            } else {
                if (!finishAndRestartLaterIfNotReady(this)) {
                    this.initActivity(null);
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void updateConnectionState(final ConnectionState connectionState) {
        RuntimeUtil.runOnUiThread(() -> ConnectionIndicatorUtil.getInstance().updateConnectionIndicator(connectionIndicator, connectionState));
    }

    /* Soft keyboard tracking */

    private static final String PORTRAIT_HEIGHT = "kbd_portrait_height";
    private static final String LANDSCAPE_HEIGHT = "kbd_landscape_height";
    private final Set<OnSoftKeyboardChangedListener> softKeyboardChangedListeners = new HashSet<>();
    private boolean softKeyboardOpen = false;
    private int minEmojiPickerHeight;

    public interface OnSoftKeyboardChangedListener {
        void onKeyboardHidden();

        void onKeyboardShown();
    }

    public void addOnSoftKeyboardChangedListener(OnSoftKeyboardChangedListener listener) {
        softKeyboardChangedListeners.add(listener);
    }

    public void removeOnSoftKeyboardChangedListener(OnSoftKeyboardChangedListener listener) {
        softKeyboardChangedListeners.remove(listener);
    }

    public void removeAllListeners() {
        softKeyboardChangedListeners.clear();
    }

    public void notifySoftKeyboardHidden() {
        final Set<OnSoftKeyboardChangedListener> listeners = new HashSet<>(softKeyboardChangedListeners);
        for (OnSoftKeyboardChangedListener listener : listeners) {
            listener.onKeyboardHidden();
        }
    }

    public void notifySoftKeyboardShown() {
        final Set<OnSoftKeyboardChangedListener> listeners = new HashSet<>(softKeyboardChangedListeners);
        for (OnSoftKeyboardChangedListener listener : listeners) {
            listener.onKeyboardShown();
        }
    }

    public void onSoftKeyboardOpened(int softKeyboardHeight) {
        logger.debug("Soft keyboard open detected");

        if (!this.softKeyboardOpen) {
            this.softKeyboardOpen = true;
            saveSoftKeyboardHeight(softKeyboardHeight);
            notifySoftKeyboardShown();
        }
    }

    public void onSoftKeyboardClosed() {
        logger.debug("Soft keyboard closed");

        if (this.softKeyboardOpen) {
            this.softKeyboardOpen = false;
            notifySoftKeyboardHidden();
        }
    }

    public void runOnSoftKeyboardClose(final Runnable runnable) {
        if (this.softKeyboardOpen) {
            addOnSoftKeyboardChangedListener(new OnSoftKeyboardChangedListener() {
                @Override
                public void onKeyboardHidden() {
                    removeOnSoftKeyboardChangedListener(this);
                    runnable.run();
                }

                @Override
                public void onKeyboardShown() {
                }
            });
        } else {
            runnable.run();
        }
    }

    public void runOnSoftKeyboardOpen(@NonNull final Runnable runnable) {
        if (!isSoftKeyboardOpen()) {
            addOnSoftKeyboardChangedListener(new OnSoftKeyboardChangedListener() {
                @Override
                public void onKeyboardShown() {
                    removeOnSoftKeyboardChangedListener(this);
                    runnable.run();
                }

                @Override
                public void onKeyboardHidden() {
                }
            });
        } else {
            runnable.run();
        }
    }

    @UiThread
    public void openSoftKeyboard(@NonNull final EditText messageText) {
        EditTextUtil.focusWindowAndShowSoftKeyboard(messageText);
    }

    public boolean isSoftKeyboardOpen() {
        return softKeyboardOpen;
    }

    public void saveSoftKeyboardHeight(int softKeyboardHeight) {
        if (ConfigUtils.isLandscape(this)) {
            logger.info("Keyboard height (landscape): " + softKeyboardHeight);
            PreferenceManager.getDefaultSharedPreferences(this)
                .edit().putInt(LANDSCAPE_HEIGHT, softKeyboardHeight).apply();
        } else {
            logger.info("Keyboard height (portrait): " + softKeyboardHeight);
            PreferenceManager.getDefaultSharedPreferences(this)
                .edit().putInt(PORTRAIT_HEIGHT, softKeyboardHeight).apply();
        }
    }

    public int loadStoredSoftKeyboardHeight() {
        boolean isLandscape = ConfigUtils.isLandscape(this);

        int savedSoftKeyboardHeight = isLandscape ?
            PreferenceManager.getDefaultSharedPreferences(this).getInt(LANDSCAPE_HEIGHT, getResources().getDimensionPixelSize(R.dimen.default_emoji_picker_height_landscape)) :
            PreferenceManager.getDefaultSharedPreferences(this).getInt(PORTRAIT_HEIGHT, getResources().getDimensionPixelSize(R.dimen.default_emoji_picker_height));

        if (savedSoftKeyboardHeight < minEmojiPickerHeight) {
            return getResources().getDimensionPixelSize(isLandscape ?
                R.dimen.default_emoji_picker_height_landscape :
                R.dimen.default_emoji_picker_height);
        }

        return savedSoftKeyboardHeight;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        loadStoredSoftKeyboardHeight();

        super.onConfigurationChanged(newConfig);
    }

    public void resetKeyboard() {
        minEmojiPickerHeight = getResources().getDimensionPixelSize(R.dimen.min_emoji_keyboard_height);

        removeAllListeners();
        softKeyboardOpen = false;
    }

    @Override
    protected void onDestroy() {
        removeAllListeners();

        super.onDestroy();
    }
}
