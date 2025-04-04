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

package ch.threema.app.services;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import ch.threema.app.R;
import ch.threema.app.managers.ListenerManager;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.ConversationTagModel;
import ch.threema.storage.models.TagModel;

public class ConversationTagServiceImpl implements ConversationTagService {
    // Do not change this tag before db entries not changed
    public static final String FIXED_TAG_PIN = "star";
    public static final String FIXED_TAG_UNREAD = "unread"; // chats deliberately marked as unread

    private final DatabaseServiceNew databaseService;
    private final List<TagModel> tagModels = new ArrayList<TagModel>() {{
        add(new TagModel(
            FIXED_TAG_PIN,
            1,
            2,
            R.string.pin));
        add(new TagModel(
            FIXED_TAG_UNREAD,
            0xFFFF0000,
            0xFFFFFFFF,
            R.string.unread));
    }};

    public ConversationTagServiceImpl(DatabaseServiceNew databaseService) {
        this.databaseService = databaseService;
    }

    @Override
    @Nullable
    public TagModel getTagModel(@NonNull final String tagKey) {
        for (TagModel tagModel : this.tagModels) {
            if (tagKey.equals(tagModel.getTag())) {
                return tagModel;
            }
        }
        return null;
    }

    @Override
    public void addTagAndNotify(@Nullable ConversationModel conversation, @Nullable TagModel tagModel) {
        if (conversation != null && tagModel != null) {
            if (!this.isTaggedWith(conversation, tagModel)) {
                this.databaseService.getConversationTagFactory()
                    .create(new ConversationTagModel(conversation.getUid(), tagModel.getTag()));
                this.fireOnModifiedConversation(conversation);
            }
        }
    }

    @Override
    public void removeTagAndNotify(@Nullable ConversationModel conversation, @Nullable TagModel tagModel) {
        if (conversation != null && tagModel != null) {
            if (this.isTaggedWith(conversation, tagModel)) {
                this.removeTag(conversation.getUid(), tagModel);
                this.fireOnModifiedConversation(conversation);
            }
        }
    }

    @Override
    public void removeTag(@NonNull String conversationUid, @NonNull TagModel tagModel) {
        this.databaseService.getConversationTagFactory().deleteByConversationUidAndTag(
            conversationUid, tagModel.getTag()
        );
    }

    @Override
    public boolean toggle(@Nullable ConversationModel conversation, @Nullable TagModel tagModel, boolean silent) {
        if (conversation != null && tagModel != null) {
            if (this.isTaggedWith(conversation, tagModel)) {
                // remove
                this.databaseService.getConversationTagFactory()
                    .deleteByConversationUidAndTag(conversation.getUid(), tagModel.getTag());
                if (!silent) {
                    this.fireOnModifiedConversation(conversation);
                }
            } else {
                // Add
                this.databaseService.getConversationTagFactory()
                    .create(new ConversationTagModel(conversation.getUid(), tagModel.getTag()));
                if (!silent) {
                    this.fireOnModifiedConversation(conversation);
                }
            }
        }
        return false;
    }

    @Override
    public boolean isTaggedWith(@Nullable ConversationModel conversation, @Nullable TagModel tagModel) {
        if (conversation == null || tagModel == null) {
            return false;
        }

        return this.databaseService.getConversationTagFactory()
            .getByConversationUidAndTag(conversation.getUid(), tagModel.getTag()) != null;
    }

    @Override
    public void removeAll(@Nullable ConversationModel conversation) {
        if (conversation != null) {
            removeAll(conversation.getUid());
        }
    }

    @Override
    public void removeAll(@NonNull String conversationUid) {
        this.databaseService.getConversationTagFactory()
            .deleteByConversationUid(conversationUid);
    }

    @Override
    public List<ConversationTagModel> getAll() {
        return this.databaseService.getConversationTagFactory().getAll();
    }

    @Override
    @NonNull
    public List<String> getConversationUidsByTag(@NonNull TagModel tagModel) {
        return this.databaseService.getConversationTagFactory().getAllConversationUidsByTag(
            tagModel.getTag()
        );
    }

    @Override
    public long getCount(@NonNull TagModel tagModel) {
        return this.databaseService.getConversationTagFactory().countByTag(tagModel.getTag());
    }

    private void fireOnModifiedConversation(final ConversationModel conversationModel) {
        ListenerManager.conversationListeners.handle(
            listener -> listener.onModified(conversationModel, conversationModel.getPosition())
        );
    }
}
