/**
 * Copyright 2018 Ricoh Company, Ltd.
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
 */

package com.theta360.cloudupload.httpserver;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Database helper class
 */
public class Theta360SQLiteOpenHelper extends SQLiteOpenHelper {

    private static final String DB = "theta360_setting.db";
    private static final int DB_VERSION = 1;
    private static final String CREATE_THETA360_SETTING_SQL = "create table theta360_setting (no_operation_timeout_minute INTEGER, status TEXT, is_upload_movie INTEGER);";
    private static final String DROP_THETA360_SETTING_SQL = "drop table theta360_setting;";

    private static final String CREATE_AUTH_INFORMATION_TABLE_SQL = "create table auth_information(refresh_token TEXT, user_id TEXT, api_type TEXT);";
    private static final String DROP_AUTH_INFORMATION_TABLE_SQL = "drop table auth_information;";

    private static final String CREATE_UPLOADED_PHOTO_TABLE_SQL = "create table uploaded_photo(path TEXT, datetime TEXT, user_id TEXT, api_type TEXT);";
    private static final String DROP_UPLOADED_PHOTO_TABLE_SQL = "drop table uploaded_photo;";

    public Theta360SQLiteOpenHelper(Context c) {
        super(c, DB, null, DB_VERSION);
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_THETA360_SETTING_SQL);
        db.execSQL(CREATE_AUTH_INFORMATION_TABLE_SQL);
        db.execSQL(CREATE_UPLOADED_PHOTO_TABLE_SQL);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(DROP_THETA360_SETTING_SQL);
        db.execSQL(DROP_AUTH_INFORMATION_TABLE_SQL);
        db.execSQL(DROP_UPLOADED_PHOTO_TABLE_SQL);
        onCreate(db);
    }
}
