package it.eneiluj.nextcloud.phonetrack.persistence;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//import it.eneiluj.nextcloud.phonetrack.android.appwidget.NoteListWidget;
//import it.eneiluj.nextcloud.phonetrack.android.appwidget.SingleNoteWidget;
import it.eneiluj.nextcloud.phonetrack.model.CloudSession;
import it.eneiluj.nextcloud.phonetrack.model.DBSession;
import it.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import it.eneiluj.nextcloud.phonetrack.service.LoggerService;
import it.eneiluj.nextcloud.phonetrack.util.ICallback;

/**
 * Helps to add, get, update and delete Notes with the option to trigger a Resync with the Server.
 */
public class PhoneTrackSQLiteOpenHelper extends SQLiteOpenHelper {

    private static final String TAG = PhoneTrackSQLiteOpenHelper.class.getSimpleName();

    private static final int database_version = 8;
    private static final String database_name = "NEXTCLOUD_PHONETRACK";

    private static final String table_sessions = "SESSIONS";
    private static final String key_id = "ID";
    private static final String key_token = "TOKEN";
    private static final String key_nextURL = "NEXTURL";
    private static final String key_name = "NAME";

    private static final String table_logjobs = "LOGJOBS";
    private static final String key_title = "TITLE";
    private static final String key_deviceName = "DEVICENAME";
    private static final String key_minTime = "MINTIME";
    private static final String key_minDistance = "MINDISTANCE";
    private static final String key_minAccuracy = "MINACCURACY";
    private static final String key_enabled = "ENABLED";

    private static final String table_locations = "LOCATIONS";
    private static final String key_lat = "LAT";
    private static final String key_lon = "LON";
    private static final String key_time = "TIME";
    private static final String key_bearing = "BEARING";
    private static final String key_altitude = "ALTITUDE";
    private static final String key_speed = "SPEED";
    private static final String key_accuracy = "ACCURACY";
    private static final String key_provider = "PROVIDER";
    private static final String key_battery = "BATTERY";

    private static final String[] columnsSessions = {key_id, key_token, key_name, key_nextURL};
    private static final String[] columnsLogjobs = {key_id, key_title, key_nextURL, key_token, key_deviceName, key_minTime, key_minDistance, key_minAccuracy, key_enabled};
    private static final String[] columnsLocations = {key_id, key_lat, key_lon, key_time, key_bearing, key_altitude, key_speed, key_accuracy, key_provider, key_battery};

    private static final String default_order = key_id + " DESC";

    private static PhoneTrackSQLiteOpenHelper instance;

    private SessionServerSyncHelper serverSyncHelper = null;
    private Context context = null;

    private PhoneTrackSQLiteOpenHelper(Context context) {
        super(context, database_name, null, database_version);
        this.context = context.getApplicationContext();
        serverSyncHelper = SessionServerSyncHelper.getInstance(this);
        //recreateDatabase(getWritableDatabase());
    }

    public static PhoneTrackSQLiteOpenHelper getInstance(Context context) {
        if (instance == null)
            return instance = new PhoneTrackSQLiteOpenHelper(context.getApplicationContext());
        else
            return instance;
    }

    public SessionServerSyncHelper getPhonetrackServerSyncHelper() {
        return serverSyncHelper;
    }

    /**
     * Creates initial the Database
     *
     * @param db Database
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        createTableSessions(db, table_sessions);
        createTableLogjobs(db, table_logjobs);
        createIndexes(db);
    }

    private void createTableSessions(SQLiteDatabase db, String tableName) {
        db.execSQL("CREATE TABLE " + tableName + " ( " +
                key_id + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                key_name + " TEXT, " +
                key_nextURL + " TEXT, " +
                key_token + " TEXT)");

    }

    private void createTableLogjobs(SQLiteDatabase db, String tableName) {
        db.execSQL("CREATE TABLE " + tableName + " ( " +
                key_id + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                key_title + " TEXT, " +
                key_nextURL + " TEXT, " +
                key_deviceName + " TEXT, " +
                key_minTime + " INTEGER, " +
                key_minDistance + " INTEGER, " +
                key_minAccuracy + " INTEGER, " +
                key_enabled + " INTEGER DEFAULT 0, " +
                key_token + " TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    /*@Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            recreateDatabase(db);
        }
        if (oldVersion < 4) {
            clearDatabase(db);
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE " + table_notes + " ADD COLUMN " + key_remote_id + " INTEGER");
            db.execSQL("UPDATE " + table_notes + " SET " + key_remote_id + "=" + key_id + " WHERE (" + key_remote_id + " IS NULL OR " + key_remote_id + "=0) AND " + key_status + "!=?", new String[]{DBStatus.LOCAL_CREATED.getTitle()});
            db.execSQL("UPDATE " + table_notes + " SET " + key_remote_id + "=0, " + key_status + "=? WHERE " + key_status + "=?", new String[]{DBStatus.LOCAL_EDITED.getTitle(), DBStatus.LOCAL_CREATED.getTitle()});
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE " + table_notes + " ADD COLUMN " + key_favorite + " INTEGER DEFAULT 0");
        }
        if (oldVersion < 7) {
            dropIndexes(db);
            db.execSQL("ALTER TABLE " + table_notes + " ADD COLUMN " + key_category + " TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE " + table_notes + " ADD COLUMN " + key_etag + " TEXT");
            createIndexes(db);
        }
        if (oldVersion < 8) {
            final String table_temp = "NOTES_TEMP";
            createTable(db, table_temp);
            db.execSQL(String.format("INSERT INTO %s(%s,%s,%s,%s,%s,%s,%s,%s,%s) ", table_temp, key_id, key_remote_id, key_status, key_title, key_modified, key_content, key_favorite, key_category, key_etag)
                    + String.format("SELECT %s,%s,%s,%s,strftime('%%s',%s),%s,%s,%s,%s FROM %s", key_id, key_remote_id, key_status, key_title, key_modified, key_content, key_favorite, key_category, key_etag, table_notes));
            db.execSQL(String.format("DROP TABLE %s", table_notes));
            db.execSQL(String.format("ALTER TABLE %s RENAME TO %s", table_temp, table_notes));
        }
    }*/

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        recreateDatabase(db);
    }

    private void clearDatabase(SQLiteDatabase db) {
        db.delete(table_sessions, null, null);
        db.delete(table_logjobs, null, null);
    }

    private void recreateDatabase(SQLiteDatabase db) {
        dropIndexes(db);
        db.execSQL("DROP TABLE " + table_sessions);
        db.execSQL("DROP TABLE " + table_logjobs);
        onCreate(db);
    }

    private void dropIndexes(SQLiteDatabase db) {
        Cursor c = db.query("sqlite_master", new String[]{"name"}, "type=?", new String[]{"index"}, null, null, null);
        while (c.moveToNext()) {
            db.execSQL("DROP INDEX " + c.getString(0));
        }
        c.close();
    }

    private void createIndexes(SQLiteDatabase db) {
        createIndex(db, table_sessions, key_token);
        createIndex(db, table_logjobs, key_token);
    }

    private void createIndex(SQLiteDatabase db, String table, String column) {
        String indexName = table + "_" + column + "_idx";
        db.execSQL("CREATE INDEX IF NOT EXISTS " + indexName + " ON " + table + "(" + column + ")");
    }

    public Context getContext() {
        return context;
    }

    /**
     * Creates a new session in the Database and adds a Synchronization Flag.
     *
     * @param content String
     */
    @SuppressWarnings("UnusedReturnValue")
    public long addSessionAndSync(String name, String token, String nextURL) {
        CloudSession session = new CloudSession(name, token, nextURL);
        return addSessionAndSync(session);
    }

    public long addSessionAndSync(CloudSession session) {
        DBSession dbs = new DBSession(0, session.getName(), session.getToken(), session.getNextURL());
        long id = addSession(dbs);
        notifyLogjobsChanged();
        getPhonetrackServerSyncHelper().scheduleSync(true);
        return id;
    }

    /**
     * Creates a new session in the Database and adds a Synchronization Flag.
     *
     * @param session Note
     */
    @SuppressWarnings("UnusedReturnValue")
    public long addLogjobAndSync(String title, String nextURL, String token, String deviceName, int minTime, int minDistance, int minAccuracy) {
        // TODO there is an 'enabled' field
        DBLogjob dblj = new DBLogjob(0, title, nextURL, token, deviceName, minTime, minDistance, minAccuracy, false);
        long id = addLogjob(dblj);
        notifyLogjobsChanged();
        getPhonetrackServerSyncHelper().scheduleSync(true);
        return id;
    }

    /**
     * Inserts a logjob directly into the Database.
     *
     * @param logjob logjob to be added.
     */
    public long addLogjob(DBLogjob logjob) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        if (logjob.getId() > 0) {
            values.put(key_id, logjob.getId());
        }
        values.put(key_title, logjob.getTitle());
        values.put(key_token, logjob.getToken());
        values.put(key_deviceName, logjob.getDeviceName());
        values.put(key_minTime, logjob.getMinTime());
        values.put(key_minDistance, logjob.getMinDistance());
        values.put(key_minAccuracy, logjob.getMinAccuracy());
        values.put(key_enabled, logjob.isEnabled() ? "1" : "0");
        values.put(key_nextURL, logjob.getNextURL());
        return db.insert(table_logjobs, null, values);
    }

    long addSession(CloudSession session) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(key_name, session.getName());
        values.put(key_token, session.getToken());
        values.put(key_nextURL, session.getNextURL());
        return db.insert(table_sessions, null, values);
    }

    /**
     * Get a single logjob by ID
     *
     * @param id int - ID of the requested Note
     * @return requested Note
     */
    public DBLogjob getLogjob(long id) {
        List<DBLogjob> logjobs = getLogjobsCustom(key_id + " = ?", new String[]{String.valueOf(id)}, null);
        return logjobs.isEmpty() ? null : logjobs.get(0);
    }

    /**
     * Query the database with a custom raw query.
     *
     * @param selection     A filter declaring which rows to return, formatted as an SQL WHERE clause (excluding the WHERE itself).
     * @param selectionArgs You may include ?s in selection, which will be replaced by the values from selectionArgs, in order that they appear in the selection. The values will be bound as Strings.
     * @param orderBy       How to order the rows, formatted as an SQL ORDER BY clause (excluding the ORDER BY itself). Passing null will use the default sort order, which may be unordered.
     * @return List of Notes
     */
    @NonNull
    @WorkerThread
    private List<DBLogjob> getLogjobsCustom(@NonNull String selection, @NonNull String[] selectionArgs, @Nullable String orderBy) {
        SQLiteDatabase db = getReadableDatabase();
        if (selectionArgs.length > 2) {
            Log.v("Logjob", selection + "   ----   " + selectionArgs[0] + " " + selectionArgs[1] + " " + selectionArgs[2]);
        }
        Cursor cursor = db.query(table_logjobs, columnsLogjobs, selection, selectionArgs, null, null, orderBy);
        List<DBLogjob> logjobs = new ArrayList<>();
        while (cursor.moveToNext()) {
            logjobs.add(getLogjobFromCursor(cursor));
        }
        cursor.close();
        return logjobs;
    }

    /**
     * Creates a DBLogjob object from the current row of a Cursor.
     *
     * @param cursor database cursor
     * @return DBLogjob
     */
    @NonNull
    private DBLogjob getLogjobFromCursor(@NonNull Cursor cursor) {
        return new DBLogjob(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getInt(5), cursor.getInt(6), cursor.getInt(7), cursor.getInt(8) == 1);
    }

    /**
     * Get a single session by ID
     *
     * @param id int - ID of the requested Note
     * @return requested Note
     */
    public DBSession getSession(long id) {
        List<DBSession> sessions = getSessionsCustom(key_id + " = ?", new String[]{String.valueOf(id)}, null);
        return sessions.isEmpty() ? null : sessions.get(0);
    }

    /**
     * Query the database with a custom raw query.
     *
     * @param selection     A filter declaring which rows to return, formatted as an SQL WHERE clause (excluding the WHERE itself).
     * @param selectionArgs You may include ?s in selection, which will be replaced by the values from selectionArgs, in order that they appear in the selection. The values will be bound as Strings.
     * @param orderBy       How to order the rows, formatted as an SQL ORDER BY clause (excluding the ORDER BY itself). Passing null will use the default sort order, which may be unordered.
     * @return List of Notes
     */
    @NonNull
    @WorkerThread
    private List<DBSession> getSessionsCustom(@NonNull String selection, @NonNull String[] selectionArgs, @Nullable String orderBy) {
        SQLiteDatabase db = getReadableDatabase();
        if (selectionArgs.length > 2) {
            Log.v("Session", selection + "   ----   " + selectionArgs[0] + " " + selectionArgs[1] + " " + selectionArgs[2]);
        }
        Cursor cursor = db.query(table_sessions, columnsSessions, selection, selectionArgs, null, null, orderBy);
        List<DBSession> sessions = new ArrayList<>();
        while (cursor.moveToNext()) {
            sessions.add(getSessionFromCursor(cursor));
        }
        cursor.close();
        return sessions;
    }

    /**
     * Creates a DBLogjob object from the current row of a Cursor.
     *
     * @param cursor database cursor
     * @return DBLogjob
     */
    @NonNull
    private DBSession getSessionFromCursor(@NonNull Cursor cursor) {
        return new DBSession(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3));
    }

    public void debugPrintFullDB() {
        List<DBSession> sessions = getSessionsCustom("", new String[]{}, default_order);
        Log.v(getClass().getSimpleName(), "Full Database (" + sessions.size() + " sessions):");
        for (DBSession session : sessions) {
            Log.v(getClass().getSimpleName(), "     " + session);
        }

        List<DBLogjob> logjobs = getLogjobsCustom("", new String[]{}, default_order);
        Log.v(getClass().getSimpleName(), "Full Database (" + logjobs.size() + " logjobs):");
        for (DBLogjob logjob : logjobs) {
            Log.v(getClass().getSimpleName(), "     " + logjob);
        }
    }

    @NonNull
    @WorkerThread
    public Map<String, Long> getTokenMap() {
        Map<String, Long> result = new HashMap<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(table_sessions, new String[]{key_token, key_id}, "", new String[]{}, null, null, null);
        while (cursor.moveToNext()) {
            result.put(cursor.getString(0), cursor.getLong(1));
        }
        cursor.close();
        return result;
    }

    /**
     * Returns a list of all sessions in the Database
     *
     * @return List&lt;DBSession&gt;
     */
    @NonNull
    @WorkerThread
    public List<DBSession> getSessions() {
        return getSessionsCustom("", new String[]{}, default_order);
    }

    @NonNull
    @WorkerThread
    public List<DBLogjob> getLogjobs() {
        return getLogjobsCustom("", new String[]{}, default_order);
    }

    /**
     * Returns a list of all logjobs in the Database
     *
     * @return List&lt;DBLogjob&gt;
     */
    @NonNull
    @WorkerThread
    public List<DBLogjob> searchLogjobs(@Nullable CharSequence query, @Nullable Boolean enabled) {
        List<String> where = new ArrayList<>();
        List<String> args = new ArrayList<>();

        if (query != null) {
            // TODO search with nextURL
            where.add("(" + key_title + " LIKE ? OR " + key_nextURL + " LIKE ? OR " + key_deviceName + " LIKE ?)");
            args.add("%" + query + "%");
            args.add("%" + query + "%");
            args.add("%" + query + "%");
        }

        if (enabled != null) {
            // TODO search with enabled
            //where.add(key_enabled + "=?");
            //args.add(enabled ? "1" : "0");
        }

        String order = key_title;
        return getLogjobsCustom(TextUtils.join(" AND ", where), args.toArray(new String[]{}), order);
    }


    @NonNull
    @WorkerThread
    public Map<String, Integer> getEnabledCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(
                table_logjobs,
                new String[]{key_enabled, "COUNT(*)"},
                "",
                new String[]{},
                key_enabled,
                null,
                key_enabled);
        Map<String, Integer> enabled = new HashMap<>(cursor.getCount());
        while (cursor.moveToNext()) {
            enabled.put(cursor.getString(0), cursor.getInt(1));
        }
        cursor.close();
        return enabled;
    }

    /*@NonNull
    @WorkerThread
    public List<NavigationAdapter.NavigationItem> getCategories() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(
                table_notes,
                new String[]{key_category, "COUNT(*)"},
                key_status + " != ?",
                new String[]{DBStatus.LOCAL_DELETED.getTitle()},
                key_category,
                null,
                key_category);
        List<NavigationAdapter.NavigationItem> categories = new ArrayList<>(cursor.getCount());
        while (cursor.moveToNext()) {
            categories.add(new NavigationAdapter.NavigationItem("category:" + cursor.getString(0), cursor.getString(0), cursor.getInt(1), NavigationAdapter.ICON_FOLDER));
        }
        cursor.close();
        return categories;
    }*/

    public void toggleEnabled(@NonNull DBLogjob logjob, @Nullable ICallback callback) {
        logjob.setEnabled(!logjob.isEnabled());
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(key_enabled, logjob.isEnabled() ? "1" : "0");
        db.update(table_logjobs, values, key_id + " = ?", new String[]{String.valueOf(logjob.getId())});
        if (callback != null) {
            serverSyncHelper.addCallbackPush(callback);
        }
        serverSyncHelper.scheduleSync(true);
    }

    /*public void setCategory(@NonNull DBLogjob logjob, @NonNull String category, @Nullable ICallback callback) {
        logjob.setCategory(category);
        logjob.setStatus(DBStatus.LOCAL_EDITED);
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(key_status, logjob.getStatus().getTitle());
        values.put(key_category, logjob.getCategory());
        db.update(table_notes, values, key_id + " = ?", new String[]{String.valueOf(logjob.getId())});
        if (callback != null) {
            serverSyncHelper.addCallbackPush(callback);
        }
        serverSyncHelper.scheduleSync(true);
    }*/

    /**
     * Updates a single Note with a new content.
     * The title is derived from the new content automatically, and modified date as well as DBStatus are updated, too -- if the content differs to the state in the database.
     *
     * @param oldLogjob    Note to be changed
     * @param newContent New content. If this is <code>null</code>, then <code>oldNote</code> is saved again (useful for undoing changes).
     * @param callback   When the synchronization is finished, this callback will be invoked (optional).
     * @return changed logjob if differs from database, otherwise the old logjob.
     */
    public DBLogjob updateLogjobAndSync(@NonNull DBLogjob oldLogjob, @Nullable String newTitle, @Nullable String newToken, @Nullable String newNextURL, @Nullable String newDevicename, int newMinTime, int newMinDistance, int newMinAccuracy, @Nullable ICallback callback) {
        //debugPrintFullDB();
        DBLogjob newLogjob;
        if (newTitle == null) {
            newLogjob = new DBLogjob(oldLogjob.getId(), oldLogjob.getTitle(), oldLogjob.getNextURL(), oldLogjob.getToken(), oldLogjob.getDeviceName(), oldLogjob.getMinTime(), oldLogjob.getMinDistance(), oldLogjob.getMinAccuracy(), oldLogjob.isEnabled());
        }
        else {
            newLogjob = new DBLogjob(oldLogjob.getId(), newTitle, newNextURL, newToken, newDevicename, newMinTime, newMinDistance, newMinAccuracy, oldLogjob.isEnabled());
        }
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(key_title, newLogjob.getTitle());
        values.put(key_nextURL, newLogjob.getNextURL());
        values.put(key_token, newLogjob.getToken());
        values.put(key_minTime, newLogjob.getMinTime());
        values.put(key_minDistance, newLogjob.getMinDistance());
        values.put(key_minAccuracy, newLogjob.getMinAccuracy());
        values.put(key_deviceName, newLogjob.getDeviceName());
        int rows = db.update(table_logjobs, values, key_id + " = ?", new String[]{String.valueOf(newLogjob.getId())});
        // if data was changed, set new status and schedule sync (with callback); otherwise invoke callback directly.
        if (rows > 0) {
            notifyLogjobsChanged();
            if (callback != null) {
                serverSyncHelper.addCallbackPush(callback);
            }
            serverSyncHelper.scheduleSync(true);
            return newLogjob;
        } else {
            if (callback != null) {
                callback.onFinish();
            }
            return oldLogjob;
        }
    }

    /**
     * Updates a single Note with data from the server, (if it was not modified locally).
     * Thereby, an optimistic concurrency control is realized in order to prevent conflicts arising due to parallel changes from the UI and synchronization.
     * This is used by the synchronization task, hence no Synchronization will be triggered. Use updateNoteAndSync() instead!
     *
     * @param id                        local ID of Note
     * @param remoteSession                Note from the server.
     * @param forceUnchangedDBNoteState is not null, then the local logjob is updated only if it was not modified meanwhile
     * @return The number of the Rows affected.
     */
    int updateSession(long id, @NonNull CloudSession remoteSession) {
        SQLiteDatabase db = this.getWritableDatabase();

        // First, update the remote ID, since this field cannot be changed in parallel, but have to be updated always.
        ContentValues values = new ContentValues();
        //values.put(key_remote_id, remoteSession.getRemoteId());
        //db.update(table_notes, values, key_id + " = ?", new String[]{String.valueOf(id)});

        // The other columns have to be updated in dependency of forceUnchangedDBNoteState,
        // since the Synchronization-Task must not overwrite locales changes!
        //values.clear();
        values.put(key_name, remoteSession.getName());
        values.put(key_token, remoteSession.getToken());
        values.put(key_nextURL, remoteSession.getNextURL());
        String whereClause;
        String[] whereArgs;

        // used by: SessionServerSyncHelper.SyncTask.pullRemoteChanges()
        // update only, if not modified locally (i.e. STATUS="") and if modified remotely (i.e. any (!) column has changed)
        whereClause = key_id + " = ?";
        whereArgs = new String[]{String.valueOf(id)};

        int i = db.update(table_sessions, values, whereClause, whereArgs);
        Log.d(getClass().getSimpleName(), "updateSession: " + remoteSession + " => " + i + " rows updated");
        return i;
    }

    /**
     * Marks a Note in the Database as Deleted. In the next Synchronization it will be deleted
     * from the Server.
     *
     * @param id long - ID of the Note that should be deleted
     * @return Affected rows
     */
    /*@SuppressWarnings("UnusedReturnValue")
    public int deleteNoteAndSync(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(key_status, DBStatus.LOCAL_DELETED.getTitle());
        int i = db.update(table_notes,
                values,
                key_id + " = ?",
                new String[]{String.valueOf(id)});
        notifyLogjobsChanged();
        getPhonetrackServerSyncHelper().scheduleSync(true);
        return i;
    }*/

    /**
     * Delete a single Logjob from the Database
     *
     * @param id            long - ID of the Logjob that should be deleted.
     */
    public void deleteLogjobAndSync(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(table_logjobs,
                key_id + " = ?",
                new String[]{String.valueOf(id)});
    }

    void deleteSession(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(table_sessions,
                key_id + " = ?",
                new String[]{String.valueOf(id)});
    }

    /*void addLocation(String ljId, Location loc) {
        if (LoggerService.DEBUG) { Log.d(TAG, "[writeLocation]"); }
        ContentValues values = new ContentValues();
        values.put(DbContract.Positions.COLUMN_TIME, loc.getTime() / 1000);
        values.put(DbContract.Positions.COLUMN_LATITUDE, loc.getLatitude());
        values.put(DbContract.Positions.COLUMN_LONGITUDE, loc.getLongitude());
        if (loc.hasBearing()) {
            values.put(DbContract.Positions.COLUMN_BEARING, loc.getBearing());
        }
        if (loc.hasAltitude()) {
            values.put(DbContract.Positions.COLUMN_ALTITUDE, loc.getAltitude());
        }
        if (loc.hasSpeed()) {
            values.put(DbContract.Positions.COLUMN_SPEED, loc.getSpeed());
        }
        if (loc.hasAccuracy()) {
            values.put(DbContract.Positions.COLUMN_ACCURACY, loc.getAccuracy());
        }
        values.put(DbContract.Positions.COLUMN_PROVIDER, loc.getProvider());

        db.insert(DbContract.Positions.TABLE_NAME, null, values);
    }*/

    /**
     * Notify about changed logjob.
     */
    void notifyLogjobsChanged() {
        //updateSingleNoteWidgets();
        //updateNoteListWidgets();
    }

    /**
     * Update single logjob widget, if the logjob data was changed.
     */
    /*private void updateSingleNoteWidgets() {
        Intent intent = new Intent(getContext(), SingleNoteWidget.class);
        intent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
        getContext().sendBroadcast(intent);
    }*/

    /**
     * Update logjob list widgets, if the logjob data was changed.
     */
    /*private void updateNoteListWidgets() {
        Intent intent = new Intent(getContext(), NoteListWidget.class);
        intent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
        getContext().sendBroadcast(intent);
    }*/
}
