/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2024 Threema GmbH
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

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import ch.threema.app.services.UpdateSystemService;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.factories.ModelFactory;

public class SystemUpdateToVersion65 implements UpdateSystemService.SystemUpdate {
	public static final int VERSION = 65;
	public static final String VERSION_STRING = "version " + VERSION;

	private final DatabaseServiceNew databaseService;
	private final SQLiteDatabase sqLiteDatabase;

	public SystemUpdateToVersion65(DatabaseServiceNew databaseService, SQLiteDatabase sqLiteDatabase) {
		this.databaseService = databaseService;
		this.sqLiteDatabase = sqLiteDatabase;
	}

	@Override
	public boolean runASync() {
		return true;
	}

	@Override
	public boolean runDirectly() {
		final ModelFactory[] modelFactories = new ModelFactory[] {
			this.databaseService.getGroupInviteModelFactory(),
			this.databaseService.getOutgoingGroupJoinRequestModelFactory(),
			this.databaseService.getIncomingGroupJoinRequestModelFactory()
		};

		for(ModelFactory factory: modelFactories) {
			for(String statement: factory.getStatements()) {
				this.sqLiteDatabase.execSQL(statement);
			}
		}

		return true;
	}

	@Override
	public String getText() {
		return VERSION_STRING;
	}
}
