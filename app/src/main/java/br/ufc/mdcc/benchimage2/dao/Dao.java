/*******************************************************************************
 * Copyright (C) 2014 Philipp B. Costa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package br.ufc.mdcc.benchimage2.dao;

import br.ufc.mdcc.benchimage2.util.DatabaseManager;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

/**
 * @author Philipp
 */
public abstract class Dao {
	protected SQLiteDatabase database;
	private final DatabaseManager databaseManager;

	public Dao(Context con) {
		databaseManager = new DatabaseManager(con);
	}

	public void openDatabase() {
		database = databaseManager.getWritableDatabase();
	}

	public void closeDatabase() {
		database.close();
	}
}