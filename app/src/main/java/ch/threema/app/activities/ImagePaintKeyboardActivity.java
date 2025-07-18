/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import org.slf4j.Logger;

import androidx.annotation.ColorInt;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatEditText;

import java.util.Objects;

import ch.threema.app.R;
import ch.threema.app.motionviews.widget.TextEntity;
import ch.threema.app.ui.SimpleTextWatcher;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class ImagePaintKeyboardActivity extends ThreemaToolbarActivity {
    private static final Logger logger = LoggingUtil.getThreemaLogger("ImagePaintKeyboardActivity");

    public final static String INTENT_EXTRA_TEXT = "text";
    public final static String INTENT_EXTRA_COLOR = "color"; // resolved color

    private int currentKeyboardHeight;
    private AppCompatEditText textEntry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        this.currentKeyboardHeight = 0;

        setSupportActionBar(getToolbar());
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            finish();
            return;
        }

        Drawable checkDrawable = AppCompatResources.getDrawable(this, R.drawable.ic_check);
        Objects.requireNonNull(checkDrawable).setColorFilter(ConfigUtils.getColorFromAttribute(this, R.attr.colorOnBackground), PorterDuff.Mode.SRC_IN);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeAsUpIndicator(checkDrawable);
        actionBar.setTitle("");

        final View rootView = findViewById(R.id.root_view);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // detect if keyboard was closed
                int navigationBarHeight = ConfigUtils.getNavigationBarHeight(ImagePaintKeyboardActivity.this);
                int statusBarHeight = ConfigUtils.getStatusBarHeight(ImagePaintKeyboardActivity.this);

                Rect rect = new Rect();
                getWindow().getDecorView().getWindowVisibleDisplayFrame(rect);

                int keyboardHeight = rootView.getRootView().getHeight() - (statusBarHeight + navigationBarHeight + rect.height());

                if ((currentKeyboardHeight - keyboardHeight) > getResources().getDimensionPixelSize(R.dimen.min_keyboard_height)) {
                    returnResult(textEntry.getText());
                }
                currentKeyboardHeight = keyboardHeight;
            }
        });
        rootView.setOnClickListener(v -> cancel());

        Intent intent = getIntent();
        @ColorInt int color = intent.getIntExtra(INTENT_EXTRA_COLOR, getResources().getColor(android.R.color.white));
        @ColorInt int hintColor;
        if (color > 0x1000000) {
            hintColor = color - 0xFF000000 + 0xA0000000;
        } else {
            hintColor = color + 0xA0000000;
        }

        textEntry = findViewById(R.id.edittext);
        textEntry.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_DONE) {
                    returnResult(textView.getText());
                }
                return false;
            }
        });
        textEntry.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                onUserInteraction();
            }
        });
        textEntry.setHorizontallyScrolling(false);
        textEntry.setMaxLines(3);
        textEntry.setTextColor(color);
        textEntry.setHintTextColor(hintColor);
        // offset values don't seem to have the same range as in a textpaint (we have to approx. quadruple them)
        textEntry.setShadowLayer(TextEntity.TEXT_SHADOW_RADIUS * 4, TextEntity.TEXT_SHADOW_OFFSET * 4, TextEntity.TEXT_SHADOW_OFFSET * 4, Color.BLACK);
        textEntry.postDelayed(() -> {
            textEntry.setVisibility(View.VISIBLE);
            textEntry.requestFocus();
            EditTextUtil.showSoftKeyboard(textEntry);
        }, 500);
    }

    @Override
    public int getLayoutResource() {
        return R.layout.activity_image_paint_keyboard;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case android.R.id.home:
                returnResult(textEntry.getText());
                return true;
            default:
                break;
        }
        return false;
    }

    private void returnResult(CharSequence text) {
        if (text != null && text.length() > 0) {
            if (textEntry != null) {
                EditTextUtil.hideSoftKeyboard(textEntry);
            }

            Intent resultIntent = new Intent();
            resultIntent.putExtra(INTENT_EXTRA_TEXT, text.toString());
            setResult(RESULT_OK, resultIntent);
            finish();
        } else {
            cancel();
        }
    }

    private void cancel() {
        if (textEntry != null) {
            EditTextUtil.hideSoftKeyboard(textEntry);
        }

        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    protected void onDestroy() {
        cancel();
        super.onDestroy();
    }

    @Override
    protected boolean enableOnBackPressedCallback() {
        return true;
    }

    @Override
    protected void handleOnBackPressed() {
        cancel();
    }
}
