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

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager.widget.PagerAdapter;

import org.slf4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import ch.threema.app.AppConstants;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.ExpandableTextEntryDialog;
import ch.threema.app.emojis.EmojiMarkupUtil;
import ch.threema.app.fragments.mediaviews.AudioViewFragment;
import ch.threema.app.fragments.mediaviews.FileViewFragment;
import ch.threema.app.fragments.mediaviews.ImageViewFragment;
import ch.threema.app.fragments.mediaviews.MediaPlayerViewFragment;
import ch.threema.app.fragments.mediaviews.MediaViewFragment;
import ch.threema.app.fragments.mediaviews.VideoViewFragment;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.MessageService;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.LockableViewPager;
import ch.threema.app.restrictions.AppRestrictionUtil;
import ch.threema.app.ui.SpacingValues;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.data.MessageContentsType;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class MediaViewerActivity extends ThreemaToolbarActivity implements ExpandableTextEntryDialog.ExpandableTextEntryDialogClickListener {

    private static final Logger logger = LoggingUtil.getThreemaLogger("MediaViewerActivity");

    private static final int PERMISSION_REQUEST_SAVE_MESSAGE = 1;
    private static final long LOADING_DELAY = 600;
    public static final int ACTIONBAR_TIMEOUT = 4000;

    public static final String EXTRA_ID_IMMEDIATE_PLAY = "play";
    public static final String EXTRA_ID_REVERSE_ORDER = "reverse";
    public static final String EXTRA_FILTER = "filter";
    public static final String EXTRA_IS_VOICEMESSAGE = "vm";

    private LockableViewPager pager;
    private File currentMediaFile;
    private ActionBar actionBar;

    private AbstractMessageModel currentMessageModel;
    private MessageReceiver currentReceiver;

    private FileService fileService;
    private MessageService messageService;
    private ContactService contactService;
    private EmojiMarkupUtil emojiMarkupUtil;

    private List<AbstractMessageModel> messageModels;
    private int currentPosition = -1;
    private MediaViewFragment[] fragments;
    private File[] decryptedFileCache;

    private View captionContainer;
    private TextView caption;
    private final Handler loadingFragmentHandler = new Handler();
    private MenuItem saveMenuItem, shareMenuItem, viewMenuItem;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
    }

    @Override
    protected void handleDeviceInsets() {
        super.handleDeviceInsets();
        ViewExtensionsKt.applyDeviceInsetsAsMargin(
            findViewById(R.id.caption_container),
            InsetSides.bottom(),
            SpacingValues.bottom(R.dimen.mediaviewer_caption_border_bottom)
        );
    }

    @Override
    protected boolean initActivity(Bundle savedInstanceState) {
        logger.debug("initActivity");

        showSystemUi();

        if (!super.initActivity(savedInstanceState)) {
            finish();
            return false;
        }

        if (!this.requiredInstances()) {
            finish();
            return false;
        }
        Intent intent = getIntent();

        String t = IntentDataUtil.getAbstractMessageType(intent);
        int i = IntentDataUtil.getAbstractMessageId(intent);
        if (TestUtil.isEmptyOrNull(t) || i <= 0) {
            finish();
            return false;
        }

        this.emojiMarkupUtil = EmojiMarkupUtil.getInstance();

        this.actionBar = getSupportActionBar();
        if (this.actionBar == null) {
            finish();
            return false;
        }
        this.actionBar.setDisplayHomeAsUpEnabled(true);
        this.actionBar.setTitle(" ");

        getToolbar().setTitleTextAppearance(this, R.style.Threema_TextAppearance_MediaViewer_Title);
        getToolbar().setSubtitleTextAppearance(this, R.style.Threema_TextAppearance_MediaViewer_SubTitle);

        this.caption = findViewById(R.id.caption);
        ViewCompat.setOnApplyWindowInsetsListener(this.caption, (v, insets) -> {
            Insets systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());

            // limit height so that caption doesn't overlap UI elements such as the play button
            final int lineHeight = ((TextView) v).getLineHeight();
            final int halfWindowHeight = ConfigUtils.getRealWindowHeight(getWindowManager()) / 2;
            final int maxTextViewHeight = halfWindowHeight
                - systemInsets.bottom
                - getResources().getDimensionPixelSize(R.dimen.mediaviewer_play_button_radius)
                - getResources().getDimensionPixelSize(R.dimen.mediaviewer_caption_border_bottom)
                - (getResources().getDimensionPixelSize(R.dimen.mediaviewer_caption_container_padding_vertical) * 2);
            ((TextView) v).setMaxLines(maxTextViewHeight / lineHeight);
            ((TextView) v).setEllipsize(TextUtils.TruncateAt.END);

            return insets;
        });

        this.captionContainer = findViewById(R.id.caption_container);

        this.currentMessageModel = IntentDataUtil.getAbstractMessageModel(intent, messageService);
        try {
            this.currentReceiver = messageService.getMessageReceiver(this.currentMessageModel);
        } catch (ThreemaException e) {
            logger.error("Exception", e);
            finish();
            return false;
        }

        if (!TestUtil.required(this.currentMessageModel, this.currentReceiver)) {
            finish();
            return false;
        }

        final @MessageContentsType int[] filter = intent.hasExtra(EXTRA_FILTER)
            ? intent.getIntArrayExtra(EXTRA_FILTER)
            : null;

        //load all records of receiver to support list pager
        try {
            this.messageModels = this.currentReceiver.loadMessages(new MessageService.MessageFilter() {
                @Override
                public long getPageSize() {
                    return 0;
                }

                @Override
                public Integer getPageReferenceId() {
                    return null;
                }

                @Override
                public boolean withStatusMessages() {
                    return false;
                }

                @Override
                public boolean withUnsaved() {
                    return false;
                }

                @Override
                public boolean onlyUnread() {
                    return false;
                }

                @Override
                public boolean onlyDownloaded() {
                    return true;
                }

                @Override
                public MessageType[] types() {
                    return new MessageType[]{MessageType.IMAGE, MessageType.VIDEO, MessageType.FILE, MessageType.VOICEMESSAGE};
                }

                @Override
                @MessageContentsType
                public int[] contentTypes() {
                    return filter;
                }

                @Override
                public int[] displayTags() {
                    return null;
                }
            });
        } catch (Exception x) {
            logger.error("Exception", x);
            finish();
            return false;
        }

        if (intent.getBooleanExtra(EXTRA_ID_REVERSE_ORDER, false)) {
            // reverse order
            Collections.reverse(messageModels);
            for (int n = messageModels.size() - 1; n >= 0; n--) {
                if (this.messageModels.get(n).getId() == this.currentMessageModel.getId()) {
                    this.currentPosition = n;
                    break;
                }
            }
        } else {
            for (int n = 0; n < this.messageModels.size(); n++) {
                if (this.messageModels.get(n).getId() == this.currentMessageModel.getId()) {
                    this.currentPosition = n;
                    break;
                }
            }
        }

        if (currentPosition == -1) {
            Toast.makeText(this, R.string.media_file_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return false;
        }

        //create array
        this.fragments = new MediaViewFragment[this.messageModels.size()];
        this.decryptedFileCache = new File[this.fragments.length];

        // Instantiate a ViewPager and a PagerAdapter.
        this.pager = findViewById(R.id.pager);
        this.pager.setOnPageChangeListener(new LockableViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int i, float v, int i2) {
            }

            @Override
            public void onPageSelected(int i) {
                currentFragmentChanged(i);
            }

            @Override
            public void onPageScrollStateChanged(int i) {
            }
        });

        this.attachAdapter();

        return true;
    }

    @Override
    public int getLayoutResource() {
        return R.layout.activity_media_viewer;
    }

    private void updateActionBarTitle(AbstractMessageModel messageModel) {
        String title = NameUtil.getDisplayNameOrNickname(this, messageModel, contactService);
        String subtitle = MessageUtil.getDisplayDate(this, messageModel, true);

        logger.debug("show updateActionBarTitle: " + title + " " + subtitle);

        if (TestUtil.required(getToolbar(), title, subtitle)) {
            getToolbar().setTitle(title);
            getToolbar().setSubtitle(subtitle);
        } else {
            getToolbar().setTitle(null);
        }

        String captionText = MessageUtil.getCaptionText(messageModel);
        if (!TestUtil.isEmptyOrNull(captionText)) {
            this.caption.setText(emojiMarkupUtil.addMarkup(this, captionText));
        } else {
            this.caption.setText("");
        }
        this.captionContainer.setVisibility(TestUtil.isEmptyOrNull(captionText) ? View.GONE : View.VISIBLE);
    }

    private void updateMenus() {
        boolean visibility = currentMediaFile != null && !AppRestrictionUtil.isShareMediaDisabled(ThreemaApplication.getAppContext());

        if (saveMenuItem != null) {
            saveMenuItem.setVisible(visibility);
            shareMenuItem.setVisible(visibility);
            viewMenuItem.setVisible(visibility);
        }
    }

    private void hideCurrentFragment() {
        if (this.currentPosition >= 0 && this.currentPosition < this.messageModels.size()) {
            MediaViewFragment f = this.getFragmentByPosition(this.currentPosition);
            if (f != null) {
                f.hide();
            }
        }
    }

    private void currentFragmentChanged(final int imagePos) {
        this.loadingFragmentHandler.removeCallbacksAndMessages(null);
        this.loadingFragmentHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                loadCurrentFrame(imagePos);
            }
        }, LOADING_DELAY);
    }

    private void loadCurrentFrame(int imagePos) {
        this.hideCurrentFragment();

        if (imagePos >= 0 && imagePos < this.messageModels.size()) {
            this.currentPosition = imagePos;
            this.currentMessageModel = this.messageModels.get(this.currentPosition);

            updateActionBarTitle(this.currentMessageModel);

            final MediaViewFragment f = this.getCurrentFragment();
            if (f != null) {
                RuntimeUtil.runOnUiThread(() -> {
                    logger.debug("showUI - loadCurrentFrame");
                    showUi();
                });
                f.showDecrypted();
            }
        }
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.activity_media_viewer, menu);

        try {
            MenuBuilder menuBuilder = (MenuBuilder) menu;
            menuBuilder.setOptionalIconsVisible(true);
        } catch (Exception ignored) {
        }

        saveMenuItem = menu.findItem(R.id.menu_save);
        shareMenuItem = menu.findItem(R.id.menu_share);
        viewMenuItem = menu.findItem(R.id.menu_view);

        if (getToolbar().getNavigationIcon() != null) {
            getToolbar().getNavigationIcon().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        } else if (itemId == R.id.menu_save) {
            if (ConfigUtils.requestWriteStoragePermissions(this, null, PERMISSION_REQUEST_SAVE_MESSAGE)) {
                saveMedia();
            }
            return true;
        } else if (itemId == R.id.menu_view) {
            viewMediaInGallery();
            return true;
        } else if (itemId == R.id.menu_share) {
            shareMedia();
            return true;
        } else if (itemId == R.id.menu_gallery) {
            showGallery();
            return true;
        } else if (itemId == R.id.menu_show_in_chat) {
            showInChat(this.currentMessageModel);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void saveMedia() {
        AbstractMessageModel messageModel = this.getCurrentMessageModel();
        if (TestUtil.required(this.fileService, messageModel)) {
            if (currentMediaFile == null) {
                Toast.makeText(this, R.string.media_file_not_found, Toast.LENGTH_LONG).show();
            } else {
                this.fileService.saveMedia(this, null, new CopyOnWriteArrayList<>(Collections.singletonList(messageModel)), true);
            }
        }
    }

    private void shareMedia() {
        final AbstractMessageModel messageModel = this.getCurrentMessageModel();
        if (messageModel != null) {
            final ExpandableTextEntryDialog alertDialog = ExpandableTextEntryDialog.newInstance(
                getString(R.string.share_media),
                R.string.add_caption_hint, messageModel.getCaption(),
                R.string.next, R.string.cancel, true);
            alertDialog.setData(messageModel);
            alertDialog.show(getSupportFragmentManager(), null);
        } else {
            logger.error("shareMedia: messageModel is null");
        }
    }

    @Override
    public void onYes(String tag, Object data, String text) {
        AbstractMessageModel messageModel = (AbstractMessageModel) data;
        Uri shareUri = fileService.copyToShareFile(messageModel, currentMediaFile);
        messageService.shareMediaMessages(this,
            new ArrayList<>(Collections.singletonList(messageModel)),
            new ArrayList<>(Collections.singletonList(shareUri)), text);
    }

    @Override
    public void onNo(String tag) {
    }

    public void viewMediaInGallery() {
        AbstractMessageModel messageModel = this.getCurrentMessageModel();
        Uri shareUri = fileService.copyToShareFile(messageModel, currentMediaFile);
        messageService.viewMediaMessage(this, messageModel, shareUri);
    }

    private void showGallery() {
        AbstractMessageModel messageModel = this.getCurrentMessageModel();
        if (messageModel != null) {
            Intent mediaGalleryIntent = new Intent(this, MediaGalleryActivity.class);
            mediaGalleryIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            switch (this.currentReceiver.getType()) {
                case MessageReceiver.Type_CONTACT:
                    mediaGalleryIntent.putExtra(AppConstants.INTENT_DATA_CONTACT, messageModel.getIdentity());
                    break;
                case MessageReceiver.Type_GROUP:
                    mediaGalleryIntent.putExtra(AppConstants.INTENT_DATA_GROUP_DATABASE_ID, ((GroupMessageModel) messageModel).getGroupId());
                    break;
                case MessageReceiver.Type_DISTRIBUTION_LIST:
                    mediaGalleryIntent.putExtra(AppConstants.INTENT_DATA_DISTRIBUTION_LIST_ID, ((DistributionListMessageModel) messageModel).getDistributionListId());
                    break;
            }
            IntentDataUtil.append(messageModel, mediaGalleryIntent);
            startActivity(mediaGalleryIntent);
            finish();
        }
    }

    private void showInChat(AbstractMessageModel messageModel) {
        if (messageModel == null) {
            return;
        }
        startActivityForResult(IntentDataUtil.getJumpToMessageIntent(this, messageModel), ThreemaActivity.ACTIVITY_ID_COMPOSE_MESSAGE);
        finish();
    }

    private void hideSystemUi() {
        logger.debug("hideSystemUi");
        if (getWindow() != null) {
            if (!isDestroyed()) {
                getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                        | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                        | View.SYSTEM_UI_FLAG_IMMERSIVE);
            } else {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
            }
        }
    }

    private void showSystemUi() {
        logger.debug("showSystemUi");
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    public void hideUi() {
        hideSystemUi();
        actionBar.hide();
        if (this.captionContainer != null) {
            this.captionContainer.setVisibility(View.GONE);
        }
    }

    public void showUi() {
        logger.debug("showUI");

        showSystemUi();
        actionBar.show();
        if (this.captionContainer != null && !TestUtil.isBlankOrNull(caption.getText())) {
            this.captionContainer.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected boolean checkInstances() {
        return TestUtil.required(this.messageService, this.fileService, this.contactService);
    }

    @Override
    protected void instantiate() {
        try {
            this.messageService = ThreemaApplication.getServiceManager()
                .getMessageService();

            this.fileService = ThreemaApplication.getServiceManager()
                .getFileService();

            this.contactService = ThreemaApplication.getServiceManager()
                .getContactService();
        } catch (ThreemaException e) {
            logger.error("Exception", e);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        // fixes https://code.google.com/p/android/issues/detail?id=19917
        super.onSaveInstanceState(outState);
        if (outState.isEmpty()) {
            outState.putBoolean("bug:fix", true);
        }
    }

    private AbstractMessageModel getCurrentMessageModel() {
        if (this.messageModels != null && this.currentPosition >= 0 && this.currentPosition < this.messageModels.size()) {
            return this.messageModels.get(this.currentPosition);
        }
        return null;
    }

    private MediaViewFragment getCurrentFragment() {
        return this.getFragmentByPosition(this.currentPosition);
    }

    private MediaViewFragment getFragmentByPosition(int position) {
        if (this.fragments != null && position >= 0 && position < this.fragments.length) {
            return this.fragments[position];
        }
        return null;
    }

    private void attachAdapter() {
        PagerAdapter pageAdapter = new ScreenSlidePagerAdapter(this, getSupportFragmentManager());
        this.pager.setAdapter(pageAdapter);
        this.pager.setCurrentItem(this.currentPosition);

        currentFragmentChanged(this.currentPosition);
    }

    @Override
    protected void onDestroy() {
        //cleanup file cache
        loadingFragmentHandler.removeCallbacksAndMessages(null);

        if (decryptedFileCache != null) {
            for (int n = 0; n < this.decryptedFileCache.length; n++) {
                if (this.decryptedFileCache[n] != null && this.decryptedFileCache[n].exists()) {
                    FileUtil.deleteFileOrWarn(this.decryptedFileCache[n], "MediaViewerCache", logger);
                    this.decryptedFileCache[n] = null;
                }
            }
        }
        super.onDestroy();
    }

    public AbstractMessageModel getMessageModel(int position) {
        return messageModels.get(position);
    }

    public File[] getDecryptedFileCache() {
        return this.decryptedFileCache;
    }

    /**
     * Page Adapter that instantiates ImageViewFragments
     */
    public static class ScreenSlidePagerAdapter extends FragmentStatePagerAdapter {

        private final MediaViewerActivity a;
        private final FragmentManager fragmentManager;
        private final SparseArray<Fragment> fragments;
        private FragmentTransaction curTransaction;

        public ScreenSlidePagerAdapter(MediaViewerActivity a, FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            this.a = a;
            fragmentManager = fm;
            fragments = new SparseArray<>();
        }

        @NonNull
        @SuppressLint("CommitTransaction")
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            Fragment fragment = getItem(position);
            if (curTransaction == null) {
                curTransaction = fragmentManager.beginTransaction();
            }
            curTransaction.add(container.getId(), fragment, "fragment:" + position);
            fragments.put(position, fragment);
            return fragment;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object fragment) {
            return ((Fragment) fragment).getView() == view;
        }

        @NonNull
        @Override
        public Fragment getItem(final int position) {
            logger.debug("getItem " + position);

            if (a.fragments[position] == null) {
                final AbstractMessageModel messageModel = a.messageModels.get(position);
                MediaViewFragment f;
                Bundle args = new Bundle();

                // check if caller wants the item to be played immediately
                Intent intent = a.getIntent();
                if (intent.getExtras().getBoolean(EXTRA_ID_IMMEDIATE_PLAY, false)) {
                    args.putBoolean(EXTRA_ID_IMMEDIATE_PLAY, true);
                    intent.removeExtra(EXTRA_ID_IMMEDIATE_PLAY);
                }

                switch (messageModel.getType()) {
                    case VIDEO:
                        f = new VideoViewFragment();
                        break;
                    case FILE:
                        String mimeType = messageModel.getFileData().getMimeType();
                        if (MimeUtil.isSupportedImageFile(mimeType)) {
                            f = new ImageViewFragment();
                        } else if (MimeUtil.isVideoFile(mimeType)) {
                            f = new VideoViewFragment();
                        } else if (MimeUtil.isAudioFile(mimeType)) {
                            if (MimeUtil.isMidiFile(mimeType) || MimeUtil.isFlacFile(mimeType)) {
                                f = new MediaPlayerViewFragment();
                            } else {
                                args.putBoolean(EXTRA_IS_VOICEMESSAGE, messageModel.getMessageContentsType() == MessageContentsType.VOICE_MESSAGE);
                                f = new AudioViewFragment();
                            }
                        } else {
                            f = new FileViewFragment();
                        }
                        break;
                    case VOICEMESSAGE:
                        f = new AudioViewFragment();
                        break;
                    default:
                        f = new ImageViewFragment();
                }

                args.putInt("position", position);
                f.setArguments(args);

                f.setOnImageLoaded(new MediaViewFragment.OnMediaLoadListener() {
                    @Override
                    public void decrypting() {
                        a.currentMediaFile = null;
                    }

                    @Override
                    public void decrypted(boolean success) {
                        if (!success) {
                            a.currentMediaFile = null;
                            a.updateMenus();
                        }
                    }

                    @Override
                    public void loaded(File file) {
                        a.currentMediaFile = file;
                        a.updateMenus();
                    }

                    @Override
                    public void thumbnailLoaded(Drawable bitmap) {
                        //do nothing!
                    }
                });
                a.fragments[position] = f;
            }

            return a.fragments[position];
        }

        @SuppressLint("CommitTransaction")
        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            logger.debug("destroyItem " + position);

            if (curTransaction == null) {
                curTransaction = fragmentManager.beginTransaction();
            }
            curTransaction.detach(fragments.get(position));
            fragments.remove(position);

            if (position >= 0 && position < a.fragments.length) {
                if (TestUtil.required(a.fragments[position])) {
                    //free memory
                    a.fragments[position].destroy();

                    //remove from array
                    a.fragments[position] = null;
                }
            }
        }

        @Override
        public void finishUpdate(@NonNull ViewGroup container) {
            if (curTransaction != null) {
                curTransaction.commitAllowingStateLoss();
                curTransaction = null;
                fragmentManager.executePendingTransactions();
            }
        }

        @Override
        public int getCount() {
            return a.messageModels.size();
        }

        @Override
        public Parcelable saveState() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // fix TransactionTooLargeException
                Bundle bundle = (Bundle) super.saveState();
                if (bundle != null) {
                    bundle.putParcelableArray("states", null);
                }
                return bundle;
            } else {
                return super.saveState();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_REQUEST_SAVE_MESSAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    saveMedia();
                } else {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                        ConfigUtils.showPermissionRationale(this, findViewById(R.id.pager), R.string.permission_storage_required);
                    }
                }
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        ConfigUtils.adjustToolbar(this, getToolbar());
    }
}
