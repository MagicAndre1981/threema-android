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

package ch.threema.app.services.systemupdate;

import androidx.annotation.NonNull;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import java.util.Arrays;

import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.services.UpdateSystemService;

public class SystemUpdateToVersion9 implements UpdateSystemService.SystemUpdate {
    private final SQLiteDatabase sqLiteDatabase;

    public SystemUpdateToVersion9(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public boolean runDirectly() {

        //update db first
        String[] messageTableColumnNames = sqLiteDatabase.rawQuery("SELECT * FROM message LIMIT 0", null).getColumnNames();


        boolean hasField = Functional.select(Arrays.asList(messageTableColumnNames), new IPredicateNonNull<String>() {
            @Override
            public boolean apply(@NonNull String type) {
                return type.equals("isStatusMessage");
            }
        }) != null;


        if (!hasField) {
            //update the message model with the uid and move every file to the new filename rule
            sqLiteDatabase.rawExecSQL("ALTER TABLE message ADD COLUMN isStatusMessage TINYINT(1) DEFAULT 0");
        }

        return true;
    }

    @Override
    public boolean runAsync() {
        return true;
    }

    @Override
    public String getText() {
        return "version 9";
    }
}
