/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

package ch.threema.storage.databaseupdate;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import android.database.SQLException;

/**
 * update constraint for GroupMessagePendingMessageIdModel
 */
public class DatabaseUpdateToVersion58 implements DatabaseUpdate {

    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion58(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        sqLiteDatabase.rawExecSQL("DROP TABLE IF EXISTS `m_group_message_pending_message_id`");
        sqLiteDatabase.rawExecSQL("DROP TABLE IF EXISTS `m_group_message_pending_msg_id`");
        sqLiteDatabase.rawExecSQL(
            "CREATE TABLE `m_group_message_pending_msg_id`"
                + "("
                + "`id` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`groupMessageId` INTEGER,"
                + "`apiMessageId` VARCHAR"
                + ")");
    }

    @Override
    public String getDescription() {
        return "change GroupMessagePendingMessageIdModel primary key";
    }

    @Override
    public int getVersion() {
        return 58;
    }
}
