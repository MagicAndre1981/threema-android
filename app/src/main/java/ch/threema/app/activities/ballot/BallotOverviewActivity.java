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

package ch.threema.app.activities.ballot;

import android.content.Intent;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.view.ActionMode;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.AppBarLayout;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import ch.threema.app.AppConstants;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.adapters.ballot.BallotOverviewListAdapter;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.SelectorDialog;
import ch.threema.app.exceptions.NotAllowedException;
import ch.threema.app.listeners.BallotListener;
import ch.threema.app.listeners.BallotVoteListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.ui.EmptyView;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.SelectorDialogItem;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.BallotUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.LogUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.storage.models.ballot.BallotModel;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class BallotOverviewActivity extends ThreemaToolbarActivity implements ListView.OnItemClickListener, GenericAlertDialog.DialogClickListener, SelectorDialog.SelectorDialogClickListener {
    private static final Logger logger = LoggingUtil.getThreemaLogger("BallotOverviewActivity");

    private static final String DIALOG_TAG_BALLOT_DELETE = "bd";
    private static final String DIALOG_TAG_CHOOSE_ACTION = "ca";
    private static final int SELECTOR_ID_VOTE = 1;
    private static final int SELECTOR_ID_RESULTS = 2;
    private static final int SELECTOR_ID_CLOSE = 3;

    private BallotService ballotService;
    private ContactService contactService;
    private GroupService groupService;
    private String myIdentity;

    private MessageReceiver<?> messageReceiver;
    private BallotOverviewListAdapter listAdapter = null;
    private List<BallotModel> ballots;
    private ListView listView;

    private ActionMode actionMode = null;
    private boolean enableBallotListeners = true;

    private final Runnable updateList = () -> {
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    };

    private final BallotVoteListener ballotVoteListener = new BallotVoteListener() {
        @Override
        public void onSelfVote(BallotModel ballotModel) {
            RuntimeUtil.runOnUiThread(updateList);
        }

        @Override
        public void onVoteChanged(BallotModel ballotModel, String votingIdentity, boolean isFirstVote) {
            RuntimeUtil.runOnUiThread(updateList);
        }

        @Override
        public void onVoteRemoved(BallotModel ballotModel, String votingIdentity) {
            RuntimeUtil.runOnUiThread(updateList);
        }

        @Override
        public boolean handle(BallotModel ballotModel) {
            return ballotListener.handle(ballotModel);
        }
    };

    private final BallotListener ballotListener = new BallotListener() {
        @Override
        public void onClosed(BallotModel ballotModel) {
            RuntimeUtil.runOnUiThread(updateList);
        }

        @Override
        public void onModified(BallotModel ballotModel) {
            RuntimeUtil.runOnUiThread(updateList);
        }

        @Override
        public void onCreated(BallotModel ballotModel) {
            RuntimeUtil.runOnUiThread(updateList);

        }

        @Override
        public void onRemoved(BallotModel ballotModel) {
            RuntimeUtil.runOnUiThread(updateList);

        }

        @Override
        public boolean handle(BallotModel ballotModel) {
            if (enableBallotListeners && requiredInstances() && messageReceiver != null) {
                try {
                    return ballotService.belongsToMe(ballotModel.getId(), messageReceiver);
                } catch (NotAllowedException e) {
                    logger.error("Exception", e);
                }
            }

            return false;
        }
    };

    @Override
    protected void handleDeviceInsets() {
        super.handleDeviceInsets();
        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            findViewById(android.R.id.list),
            InsetSides.lbr()
        );
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        if (!this.requireInstancesOrExit()) {
            return;
        }

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(R.string.ballot_overview);

        listView = this.findViewById(android.R.id.list);
        listView.setOnItemClickListener(this);
        EmptyView emptyView = new EmptyView(this);
        emptyView.setup(getString(R.string.ballot_no_ballots_yet));
        ((ViewGroup) listView.getParent()).addView(emptyView);
        listView.setEmptyView(emptyView);

        final AppBarLayout appBarLayout = findViewById(R.id.appbar);
        appBarLayout.setLiftable(true);
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                boolean isAtTop = firstVisibleItem == 0 && (view.getChildCount() == 0 || view.getChildAt(0).getTop() == 0);
                appBarLayout.setLifted(!isAtTop);
            }
        });

        Intent receivedIntent = getIntent();

        this.messageReceiver = IntentDataUtil.getMessageReceiverFromIntent(this, receivedIntent);
        if (this.messageReceiver == null) {
            logger.error("cannot instantiate receiver");
            finish();
            return;
        }

        this.setupList();
        this.updateList();

    }

    @Override
    protected void onResume() {
        super.onResume();
        ListenerManager.ballotListeners.add(this.ballotListener);
        ListenerManager.ballotVoteListeners.add(this.ballotVoteListener);
    }

    @Override
    public int getLayoutResource() {
        return R.layout.activity_list_toolbar;
    }

    @Override
    protected void onPause() {
        super.onPause();
        ListenerManager.ballotListeners.remove(this.ballotListener);
        ListenerManager.ballotVoteListeners.remove(this.ballotVoteListener);
    }

    private void setupList() {
        final ListView listView = this.listView;

        if (listView != null) {
            listView.setDividerHeight(0);
            listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
            listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                    view.setSelected(true);
                    listView.setItemChecked(position, true);
                    listView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
                    actionMode = startSupportActionMode(new MessageSectionAction());

                    return true;
                }
            });
        }
    }

    private void updateList() {
        if (!this.requiredInstances()) {
            return;
        }

        try {
            this.ballots = this.ballotService.getBallots(new BallotService.BallotFilter() {
                @Override
                public MessageReceiver getReceiver() {
                    return messageReceiver;
                }

                @Override
                public BallotModel.State[] getStates() {
                    return null;
                }

                @Override
                public boolean filter(BallotModel ballotModel) {
                    return true;
                }
            });

            if (this.ballots != null) {
                this.listAdapter = new BallotOverviewListAdapter(
                    this,
                    this.ballots,
                    this.ballotService,
                    this.contactService,
                    Glide.with(this)
                );

                listView.setAdapter(this.listAdapter);
            }
        } catch (NotAllowedException e) {
            logger.error("Exception", e);
            finish();
            return;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (this.listAdapter == null) {
            return;
        }

        if (actionMode == null) {
            this.deselectItem();

            BallotModel ballotModel = listAdapter.getItem(position);

            if (ballotModel != null) {
                ArrayList<SelectorDialogItem> items = new ArrayList<>(3);
                ArrayList<Integer> values = new ArrayList<>(3);

                if (BallotUtil.canVote(ballotModel, myIdentity)) {
                    items.add(new SelectorDialogItem(getString(R.string.ballot_vote), R.drawable.ic_vote_outline));
                    values.add(SELECTOR_ID_VOTE);
                }
                if (BallotUtil.canViewMatrix(ballotModel, myIdentity)) {
                    items.add(new SelectorDialogItem(getString(ballotModel.getState() == BallotModel.State.CLOSED ? R.string.ballot_result_final : R.string.ballot_result_intermediate), R.drawable.ic_ballot_outline));
                    values.add(SELECTOR_ID_RESULTS);
                }
                if (BallotUtil.canClose(ballotModel, myIdentity)) {
                    items.add(new SelectorDialogItem(getString(R.string.ballot_close), R.drawable.ic_check));
                    values.add(SELECTOR_ID_CLOSE);
                }

                if (items.size() == 1) {
                    BallotUtil.openDefaultActivity(this, this.getSupportFragmentManager(), ballotModel, myIdentity);
                } else {
                    SelectorDialog selectorDialog = SelectorDialog.newInstance(null, items, values, null);
                    selectorDialog.setData(ballotModel);
                    selectorDialog.show(getSupportFragmentManager(), DIALOG_TAG_CHOOSE_ACTION);
                }
            }
        } else {
            // invalidate menu to update display => onPrepareActionMode()
            final int checked = listView.getCheckedItemCount();

            if (checked > 0) {
                actionMode.invalidate();
            } else {
                actionMode.finish();
            }
        }
    }

    @Override
    protected boolean checkInstances() {
        return !TestUtil.isEmptyOrNull(this.myIdentity) && TestUtil.required(
            this.ballotService,
            this.contactService,
            this.groupService);
    }

    @Override
    protected void instantiate() {
        if (serviceManager != null) {
            try {
                this.ballotService = serviceManager.getBallotService();
                this.contactService = serviceManager.getContactService();
                this.groupService = serviceManager.getGroupService();
                this.myIdentity = serviceManager.getUserService().getIdentity();
            } catch (Exception e) {
                logger.error("Exception", e);
            }
        }
    }

    private boolean requireInstancesOrExit() {
        if (!this.requiredInstances()) {
            logger.error("Required instances failed");
            this.finish();
            return false;
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
        }

        return true;
    }

    @Override
    protected boolean enableOnBackPressedCallback() {
        return true;
    }

    @Override
    protected void handleOnBackPressed() {
        setResult(RESULT_OK);
        finish();
    }

    private void deselectItem() {
        if (listView != null) {
            listView.clearChoices();
            listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
            listView.requestLayout();
        }
    }

    private int getFirstCheckedPosition(ListView listView) {
        SparseBooleanArray checked = listView.getCheckedItemPositions();
        for (int i = 0; i < checked.size(); i++) {
            if (checked.valueAt(i)) {
                return checked.keyAt(i);
            }
        }
        return AbsListView.INVALID_POSITION;
    }

    private void removeSelectedBallots() {
        final SparseBooleanArray checkedItems = listView.getCheckedItemPositions();
        final int numCheckedItems = listView.getCheckedItemCount();

        GenericAlertDialog dialog = GenericAlertDialog.newInstance(
            ConfigUtils.getSafeQuantityString(this, R.plurals.ballot_really_delete, numCheckedItems, numCheckedItems),
            ConfigUtils.getSafeQuantityString(this, R.plurals.ballot_really_delete_text, numCheckedItems, numCheckedItems),
            R.string.ok,
            R.string.cancel
        );
        dialog.setData(checkedItems);
        dialog.show(getSupportFragmentManager(), DIALOG_TAG_BALLOT_DELETE);
    }

    private void removeSelectedBallotsDo(SparseBooleanArray checkedItems) {
        if (!this.requiredInstances()) {
            return;
        }
        synchronized (this.ballots) {
            //disable listener
            enableBallotListeners = false;
            for (int i = 0; i < checkedItems.size(); i++) {
                if (checkedItems.valueAt(i)) {

                    final int index = checkedItems.keyAt(i);
                    if (index >= 0 && index < this.ballots.size()) {
                        try {
                            this.ballotService.remove(this.ballots.get(index));
                        } catch (NotAllowedException e) {
                            LogUtil.exception(e, this);
                            return;
                        }
                    }

                }
            }

            enableBallotListeners = true;
        }

        if (actionMode != null) {
            actionMode.finish();
        }

        this.updateList();
    }

    @Override
    public void onYes(String tag, Object data) {
        if (tag.equals(DIALOG_TAG_BALLOT_DELETE)) {
            removeSelectedBallotsDo((SparseBooleanArray) data);
        } else if (tag.equals(AppConstants.CONFIRM_TAG_CLOSE_BALLOT)) {
            BallotUtil.closeBallot(this, (BallotModel) data, ballotService, MessageId.random(), TriggerSource.LOCAL);
        }
    }

    @Override
    public void onClick(String tag, int which, Object data) {
        final BallotModel ballotModel = (BallotModel) data;

        switch (which) {
            case SELECTOR_ID_VOTE:
                BallotUtil.openVoteDialog(this.getSupportFragmentManager(), ballotModel, myIdentity);
                break;
            case SELECTOR_ID_RESULTS:
                BallotUtil.openMatrixActivity(this, ballotModel, myIdentity);
                break;
            case SELECTOR_ID_CLOSE:
                BallotUtil.requestCloseBallot(ballotModel, myIdentity, null, this);
                break;
        }
    }

    public class MessageSectionAction implements ActionMode.Callback {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.action_ballot_overview, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            final int checked = listView.getCheckedItemCount();
            final int firstCheckedItem = getFirstCheckedPosition(listView);

            if (firstCheckedItem == AbsListView.INVALID_POSITION) {
                return false;
            }

            mode.setTitle(String.format(getString(R.string.num_items_sected), Integer.toString(checked)));
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            final int firstCheckedItem = getFirstCheckedPosition(listView);

            if (firstCheckedItem == AbsListView.INVALID_POSITION) {
                return false;
            }

            if (item.getItemId() == R.id.menu_ballot_remove) {
                removeSelectedBallots();
                return true;
            }

            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            actionMode = null;
            deselectItem();
        }
    }

}
