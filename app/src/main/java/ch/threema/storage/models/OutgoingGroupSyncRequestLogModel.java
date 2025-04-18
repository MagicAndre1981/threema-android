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

package ch.threema.storage.models;


import java.util.Date;

import androidx.annotation.NonNull;

/**
 * This model is used to track at what time a group sync request has been sent to what group. This
 * is required to prevent that group sync requests are sent too often per group.
 */
public class OutgoingGroupSyncRequestLogModel {

    public static final String TABLE = "m_group_request_sync_log";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_API_GROUP_ID = "apiGroupId";
    public static final String COLUMN_CREATOR_IDENTITY = "creatorIdentity";
    public static final String COLUMN_LAST_REQUEST = "lastRequest";

    private int id;
    private String apiGroupId;
    private String creatorIdentity;
    private Date lastRequest;

    public OutgoingGroupSyncRequestLogModel() {
    }

    public int getId() {
        return id;
    }

    public OutgoingGroupSyncRequestLogModel setId(int id) {
        this.id = id;
        return this;
    }

    @NonNull
    @Override
    public String toString() {
        return "m_group_request_sync_log.id = " + this.getId();
    }

    public OutgoingGroupSyncRequestLogModel setAPIGroupId(String apiGroupId, String creatorIdentity) {
        this.apiGroupId = apiGroupId;
        this.creatorIdentity = creatorIdentity;
        return this;
    }

    public String getCreatorIdentity() {
        return this.creatorIdentity;
    }

    public String getApiGroupId() {
        return this.apiGroupId;
    }

    public Date getLastRequest() {
        return this.lastRequest;
    }

    public OutgoingGroupSyncRequestLogModel setLastRequest(Date lastRequest) {
        this.lastRequest = lastRequest;
        return this;
    }
}
