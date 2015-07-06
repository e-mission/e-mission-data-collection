package edu.berkeley.eecs.cfc_tracker.usercache;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.JsonReader;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedList;

/**
 * Concrete implementation of the user cache that stores the entries
 * in an SQLite database.
 *
 * Big design question: should we store the data in separate tables which are put
 * in here
 */
public class BuiltinUserCache extends SQLiteOpenHelper implements UserCache {

    // All Static variables
    // Database Version
    private static final int DATABASE_VERSION = 1;

    // Database Name
    private static final String DATABASE_NAME = "userCacheDB";

    // Table names
    private static final String TABLE_USER_CACHE = "userCache";

    // USER_CACHE Table Columns names
    // We expand the metadata and store the data as a JSON blob
    private static final String KEY_WRITE_TS = "write_ts";
    private static final String KEY_READ_TS = "read_ts";
    private static final String KEY_TYPE = "type";
    private static final String KEY_KEY = "key";
    private static final String KEY_PLUGIN = "plugin";
    private static final String KEY_DATA = "data";

    private static final String TAG = "ClientStatsHelper";

    private static final String METADATA_TAG = "metadata";
    private static final String DATA_TAG = "data";

    private static final String MESSAGE_TYPE = "message";
    private static final String DOCUMENT_TYPE = "document";
    private static final String RW_DOCUMENT_TYPE = "rw-document";

    private Context cachedCtx;

    public BuiltinUserCache(Context ctx) {
        super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
        cachedCtx = ctx;
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        String CREATE_USER_CACHE_TABLE = "CREATE TABLE " + TABLE_USER_CACHE +" (" +
                KEY_WRITE_TS + " INTEGER, "+ KEY_READ_TS +" INTEGER, " +
                KEY_TYPE + " TEXT, " + KEY_KEY + " TEXT, "+
                KEY_PLUGIN + " TEXT, " + KEY_DATA + " TEXT)";
        System.out.println("CREATE_CLIENT_STATS_TABLE = " + CREATE_USER_CACHE_TABLE);
        sqLiteDatabase.execSQL(CREATE_USER_CACHE_TABLE);
    }

    @Override
    public void putMessage(String key, JSONObject value) {
        putValue(key, value, MESSAGE_TYPE);
    }

    @Override
    public void putReadWriteDocument(String key, JSONObject value) {
        putValue(key, value, RW_DOCUMENT_TYPE);
    }

    private void putValue(String key, JSONObject value, String type) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues newValues = new ContentValues();
        newValues.put(KEY_WRITE_TS, System.currentTimeMillis());
        newValues.put(KEY_TYPE, type);
        newValues.put(KEY_KEY, key);
        newValues.put(KEY_DATA, value.toString());
        db.insert(TABLE_USER_CACHE, null, newValues);
    }

    @Override
    public JSONObject getDocument(String key) {
        SQLiteDatabase db = this.getReadableDatabase();
        String selectQuery = "SELECT "+KEY_DATA+" from " + TABLE_USER_CACHE +
                "WHERE " + KEY_KEY + " = " + key +
                " AND ("+ KEY_TYPE + " = "+ DOCUMENT_TYPE + " OR " + KEY_TYPE + " = " + RW_DOCUMENT_TYPE + ")";
        Cursor queryVal = db.rawQuery(selectQuery, null);
        JSONObject dataObj = null;
        try {
            dataObj = new JSONObject(queryVal.getString(0));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        db.close();
        updateReadTimestamp(key);
        return dataObj;
    }

    public JSONObject getUpdatedDocument(String key) {
        SQLiteDatabase db = this.getReadableDatabase();
        String selectQuery = "SELECT "+KEY_WRITE_TS+", "+KEY_READ_TS+", "+KEY_DATA+" from " + TABLE_USER_CACHE +
                "WHERE " + KEY_KEY + " = " + key +
                " AND ("+ KEY_TYPE + " = "+ DOCUMENT_TYPE + " OR " + KEY_TYPE + " = " + RW_DOCUMENT_TYPE + ")";
        Cursor queryVal = db.rawQuery(selectQuery, null);
        long writeTs = queryVal.getLong(0);
        long readTs = queryVal.getLong(1);
        if (writeTs < readTs) {
            // This has been not been updated since it was last read
            return null;
        }
        JSONObject dataObj = null;
        try {
            dataObj = new JSONObject(queryVal.getString(0));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        db.close();
        updateReadTimestamp(key);
        return dataObj;
    }

    private void updateReadTimestamp(String key) {
        SQLiteDatabase writeDb = this.getWritableDatabase();
        ContentValues updateValues = new ContentValues();
        updateValues.put(KEY_READ_TS, System.currentTimeMillis());
        updateValues.put(KEY_KEY, key);
        writeDb.update(TABLE_USER_CACHE, updateValues, null, null);
        writeDb.close();
    }

    @Override
    public void clearMessages(TimeQuery tq) {
        String whereString = tq.key + " < ? AND " + tq.key + " > ?";
        String[] whereArgs = {String.valueOf(tq.startTs), String.valueOf(tq.endTs)};
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_USER_CACHE, whereString, whereArgs);
        db.close();
        /*
        metadataObj.put(KEY_WRITE_TS, queryVal.getString(0));
        metadataObj.put(KEY_READ_TS, queryVal.getString(1));
        metadataObj.put(KEY_TYPE, queryVal.getString(2));
        metadataObj.put(KEY_KEY, queryVal.getString(3));
        metadataObj.put(KEY_PLUGIN, queryVal.getString(4));
        */
    }

    /*
     * Nuclear option that just deletes everything. Useful for debugging.
     */
    public void clear() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_USER_CACHE, null, null);
        db.close();
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {

    }
}
