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
package br.ufc.mdcc.mpos.persistence;

import java.util.ArrayList;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * This class manage sqlite android database.
 * 
 * @author Philipp B. Costa
 */
final class DatabaseManager extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 7;
    private Context context;

	private final ArrayList<String> tabelas = new ArrayList<String>(2);

	public DatabaseManager(Context con) {
		super(con, "mpos.data", null, DATABASE_VERSION);
		this.context = con;

		loadingTables();
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		for (String tabela : tabelas){
			db.execSQL(tabela);
		}
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if (oldVersion != newVersion) {
			db.execSQL("DROP TABLE IF EXISTS netprofile");
			db.execSQL("DROP TABLE IF EXISTS user");
			onCreate(db);
		}
	}

	private void loadingTables() {
		tabelas.add("CREATE TABLE netprofile (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, jitter INTEGER, loss INTEGER, udp TEXT, tcp TEXT, down TEXT, up TEXT, network_type TEXT, endpoint_type TEXT, date DATETIME NOT NULL) ");
		tabelas.add("CREATE TABLE user (id TEXT NOT NULL UNIQUE )");
	}
}