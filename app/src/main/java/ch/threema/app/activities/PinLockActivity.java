/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.InputType;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.TextView;

import org.slf4j.Logger;

import java.security.MessageDigest;

import ch.threema.app.AppConstants;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.LockAppService;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.app.utils.NavigationUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class PinLockActivity extends ThreemaActivity {
    private static final Logger logger = LoggingUtil.getThreemaLogger("PinLockActivity");
    private static final long ERROR_MESSAGE_TIMEOUT = 3000;
    private static final int FAILED_ATTEMPTS_BEFORE_TIMEOUT = 3;
    private static final long FAILED_ATTEMPT_COUNTDOWN_INTERVAL_MS = 1000L;
    private static final int DEFAULT_LOCKOUT_TIMEOUT = 30 * 1000;

    private TextView passwordEntry;
    private TextView errorTextView;
    private int numWrongConfirmAttempts;
    private final Handler handler = new Handler();
    private CountDownTimer countDownTimer;
    private boolean isCheckOnly;
    private String pinPreset;

    private LockAppService lockAppService;
    private PreferenceService preferenceService;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        logger.debug("onCreate");

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            findViewById(R.id.topFrame),
            InsetSides.all()
        );

        isCheckOnly = getIntent().getBooleanExtra(AppConstants.INTENT_DATA_CHECK_ONLY, false);
        pinPreset = getIntent().getStringExtra(AppConstants.INTENT_DATA_PIN);

        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager == null) {
            finish();
            return;
        }

        preferenceService = serviceManager.getPreferenceService();
        lockAppService = serviceManager.getLockAppService();

        if (!lockAppService.isLocked() && !isCheckOnly) {
            finish();
        }

        numWrongConfirmAttempts = preferenceService.getLockoutAttempts();

        setContentView(R.layout.activity_pin_lock);

        passwordEntry = findViewById(R.id.password_entry);
        passwordEntry.setOnEditorActionListener((v, actionId, event) -> {
            // Check if this was the result of hitting the enter or "done" key
            if (actionId == EditorInfo.IME_NULL
                || actionId == EditorInfo.IME_ACTION_DONE
                || actionId == EditorInfo.IME_ACTION_NEXT) {
                handleNext();
                return true;
            }
            return false;
        });
        passwordEntry.setFilters(new InputFilter[]{new InputFilter.LengthFilter(AppConstants.MAX_PIN_LENGTH)});

        TextView headerTextView = findViewById(R.id.headerText);
        TextView detailsTextView = findViewById(R.id.detailsText);
        errorTextView = findViewById(R.id.errorText);

        headerTextView.setText(R.string.confirm_your_pin);
        detailsTextView.setText(getString(R.string.pinentry_enter_pin, getString(R.string.app_name)));
        passwordEntry.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);

        Button cancelButton = findViewById(R.id.cancelButton);
        cancelButton.setOnClickListener(v -> quit());
    }

    @Override
    protected boolean isPinLockable() {
        return false;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        overridePendingTransition(0, 0);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!lockAppService.isLocked() && !isCheckOnly) {
            finish();
        }

        long deadline = getLockoutAttemptDeadline();
        if (deadline != 0) {
            handleAttemptLockout(deadline);
        }
    }

    @Override
    protected boolean enableOnBackPressedCallback() {
        return true;
    }

    @Override
    protected void handleOnBackPressed() {
        quit();
    }

    private void quit() {
        EditTextUtil.hideSoftKeyboard(passwordEntry);

        if (isCheckOnly) {
            setResult(RESULT_CANCELED);
            finish();
        } else {
            NavigationUtil.navigateToLauncher(this);
        }
    }

    private void handleNext() {
        final String pin = passwordEntry.getText().toString();
        // use MessageDigest for a timing-safe comparison
        if (lockAppService.unlock(pin) || pinPreset != null && MessageDigest.isEqual(pin.getBytes(), pinPreset.getBytes())) {
            EditTextUtil.hideSoftKeyboard(passwordEntry);

            setResult(RESULT_OK);
            numWrongConfirmAttempts = 0;
            finish();
        } else {
            if (++numWrongConfirmAttempts >= FAILED_ATTEMPTS_BEFORE_TIMEOUT) {
                long deadline = setLockoutAttemptDeadline(DEFAULT_LOCKOUT_TIMEOUT);
                handleAttemptLockout(deadline);
            } else {
                showError(R.string.pinentry_wrong_pin);
            }

            if (isCheckOnly) {
                passwordEntry.setEnabled(false);

                handler.postDelayed(() -> RuntimeUtil.runOnUiThread(this::quit), 1000);
            }
        }
    }

    private void handleAttemptLockout(long elapsedRealtimeDeadline) {
        long elapsedRealtime = SystemClock.elapsedRealtime();
        passwordEntry.setEnabled(false);
        countDownTimer = new CountDownTimer(
            elapsedRealtimeDeadline - elapsedRealtime,
            FAILED_ATTEMPT_COUNTDOWN_INTERVAL_MS) {
            @Override
            public void onTick(long millisUntilFinished) {
                final int secondsCountdown = (int) (millisUntilFinished / 1000);
                showError(String.format(getString(R.string.too_many_incorrect_attempts), Integer.toString(secondsCountdown)), 0);
            }

            @Override
            public void onFinish() {
                passwordEntry.setEnabled(true);
                errorTextView.setText("");
                numWrongConfirmAttempts = 0;
            }
        }.start();
    }

    private void showError(int msg) {
        showError(msg, ERROR_MESSAGE_TIMEOUT);
    }

    private final Runnable resetErrorRunnable = new Runnable() {
        public void run() {
            errorTextView.setText("");
        }
    };

    private void showError(CharSequence msg, long timeout) {
        errorTextView.setText(msg);
        errorTextView.announceForAccessibility(errorTextView.getText());
        passwordEntry.setText(null);
        handler.removeCallbacks(resetErrorRunnable);
        if (timeout != 0) {
            handler.postDelayed(resetErrorRunnable, timeout);
        }
    }

    private void showError(int msg, long timeout) {
        showError(getText(msg), timeout);
    }

    /**
     * Set and store the lockout deadline, meaning the user can't attempt his/her unlock
     * pattern until the deadline has passed.
     *
     * @return the chosen deadline.
     */
    public long setLockoutAttemptDeadline(int timeoutMs) {
        final long deadline = SystemClock.elapsedRealtime() + timeoutMs;

        preferenceService.setLockoutDeadline(deadline);
        preferenceService.setLockoutTimeout(timeoutMs);

        return deadline;
    }

    /**
     * @return The elapsed time in millis in the future when the user is allowed to
     * attempt to enter his/her lock pattern, or 0 if the user is welcome to
     * enter a pattern.
     */
    public long getLockoutAttemptDeadline() {
        final long deadline = preferenceService.getLockoutDeadline();
        final long timeoutMs = preferenceService.getLockoutTimeout();

        final long now = SystemClock.elapsedRealtime();
        if (deadline < now || deadline > (now + timeoutMs)) {
            return 0L;
        }
        return deadline;
    }

    @Override
    protected void onDestroy() {
        if (preferenceService != null) {
            preferenceService.setLockoutAttempts(numWrongConfirmAttempts);
        }
        super.onDestroy();
    }
}
