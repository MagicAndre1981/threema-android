/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.storage.databaseupdate

import ch.threema.storage.fieldExists
import net.zetetic.database.sqlcipher.SQLiteDatabase

internal class DatabaseUpdateToVersion97(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        for (table in arrayOf(
            "message",
            "m_group_message",
            "distribution_list_message",
        )) {
            if (!sqLiteDatabase.fieldExists(table, "deletedAtUtc")) {
                sqLiteDatabase.rawExecSQL("ALTER TABLE `$table` ADD COLUMN `deletedAtUtc` DATETIME")
            }
        }
    }

    override fun getDescription() = "add deletedAtUtc field for messages"

    override fun getVersion() = VERSION

    companion object {
        const val VERSION = 97
    }
}
