package edu.berkeley.eecs.cfc_tracker.usercache;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.LinkedList;

import edu.berkeley.eecs.cfc_tracker.Log;
import edu.berkeley.eecs.cfc_tracker.wrapper.Metadata;

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

    private static final String TAG = "BuiltinUserCache";

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

    private String getKey(int keyRes) {
        return cachedCtx.getString(keyRes);
    }

    @Override
    public void putMessage(int keyRes, Object value) {
        putValue(keyRes, value, MESSAGE_TYPE);
    }

    @Override
    public void putReadWriteDocument(int keyRes, Object value) {
        putValue(keyRes, value, RW_DOCUMENT_TYPE);
    }

    private void putValue(int keyRes, Object value, String type) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues newValues = new ContentValues();
        newValues.put(KEY_WRITE_TS, System.currentTimeMillis());
        newValues.put(KEY_TYPE, type);
        newValues.put(KEY_KEY, getKey(keyRes));
        newValues.put(KEY_DATA, new Gson().toJson(value));
        db.insert(TABLE_USER_CACHE, null, newValues);
        db.close();
    }

    @Override
    public <T> T getDocument(int keyRes, Class<T> classOfT) {
        SQLiteDatabase db = this.getReadableDatabase();
        String selectQuery = "SELECT "+KEY_DATA+" from " + TABLE_USER_CACHE +
                " WHERE " + KEY_KEY + " = '" + getKey(keyRes) + "'" +
                " AND ("+ KEY_TYPE + " = '"+ DOCUMENT_TYPE + "' OR " + KEY_TYPE + " = '" + RW_DOCUMENT_TYPE + "')";
        Cursor queryVal = db.rawQuery(selectQuery, null);
        if (queryVal.moveToFirst()) {
            T retVal = new Gson().fromJson(queryVal.getString(0), classOfT);
            db.close();
            updateReadTimestamp(keyRes);
            return retVal;
        } else {
            // If there was no matching entry, return an empty list instead of null
            db.close();
            return null;
        }
    }

    @Override
    public <T> T getUpdatedDocument(int keyRes, Class<T> classOfT) {
        SQLiteDatabase db = this.getReadableDatabase();
        String selectQuery = "SELECT "+KEY_WRITE_TS+", "+KEY_READ_TS+", "+KEY_DATA+" from " + TABLE_USER_CACHE +
                "WHERE " + KEY_KEY + " = " + getKey(keyRes) +
                " AND ("+ KEY_TYPE + " = "+ DOCUMENT_TYPE + " OR " + KEY_TYPE + " = " + RW_DOCUMENT_TYPE + ")";
        Cursor queryVal = db.rawQuery(selectQuery, null);
        if (queryVal.moveToFirst()) {
            long writeTs = queryVal.getLong(0);
            long readTs = queryVal.getLong(1);
            if (writeTs < readTs) {
                // This has been not been updated since it was last read
                db.close();
                return null;
            }
            T retVal = new Gson().fromJson(queryVal.getString(0), classOfT);
            db.close();
            updateReadTimestamp(keyRes);
            return retVal;
        } else {
            // There is no matching entry
            db.close();
            return null;
        }
    }

    @Override
    public <T> T[] getMessagesForInterval(int keyRes, TimeQuery tq, Class<T> classOfT) {
        String whereString = KEY_KEY + " = ? AND "+ getKey(tq.keyRes) + " < ? AND " + getKey(tq.keyRes) + " > ?";
        String[] whereArgs = {getKey(keyRes), String.valueOf(tq.startTs), String.valueOf(tq.endTs)};
        SQLiteDatabase db = this.getReadableDatabase();
        String[] retCol = {KEY_DATA};
        Cursor resultCursor = db.query(TABLE_USER_CACHE, retCol, whereString, whereArgs, null, null, null, null);

        T[] result = getMessagesFromCursor(resultCursor, classOfT);
        db.close();
        return result;
    }

    @Override
    public <T> T[] getLastMessages(int keyRes, int nEntries, Class<T> classOfT) {
        String queryString = "SELECT "+KEY_DATA+" FROM "+TABLE_USER_CACHE+
                " WHERE "+KEY_KEY+" = '"+getKey(keyRes)+ "'"+
                " ORDER BY write_ts DESC  LIMIT "+nEntries;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor resultCursor = db.rawQuery(queryString, null);
        T[] result = getMessagesFromCursor(resultCursor, classOfT);
        db.close();
        return result;
    }

    private <T> T[] getMessagesFromCursor(Cursor resultCursor, Class<T> classOfT) {
        int resultCount = resultCursor.getCount();
        T[] resultArray = (T[]) Array.newInstance(classOfT, resultCount);
        System.out.println("resultArray is "+resultArray);
        if (resultCursor.moveToFirst()) {
            for (int i = 0; i < resultCount; i++) {
                String data = resultCursor.getString(0);
                // System.out.println("data = "+data);
                resultArray[i] = new Gson().fromJson(data, classOfT);
                resultCursor.moveToNext();
            }
        }
        return resultArray;
    }

    private void updateReadTimestamp(int keyRes) {
        SQLiteDatabase writeDb = this.getWritableDatabase();
        ContentValues updateValues = new ContentValues();
        updateValues.put(KEY_READ_TS, System.currentTimeMillis());
        updateValues.put(KEY_KEY, getKey(keyRes));
        writeDb.update(TABLE_USER_CACHE, updateValues, null, null);
        writeDb.close();
    }

    @Override
    public void clearMessages(TimeQuery tq) {
        Log.d(cachedCtx, TAG, "Clearing message for timequery "+tq);
        String whereString = getKey(tq.keyRes) + " > ? AND " + getKey(tq.keyRes) + " < ?";
        String[] whereArgs = {String.valueOf(tq.startTs), String.valueOf(tq.endTs)};
        Log.d(cachedCtx, TAG, "Args =  "+whereString + " : " + Arrays.toString(whereArgs));
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_USER_CACHE, whereString, whereArgs);
        db.close();
    }

    /*
     * Nuclear option that just deletes everything. Useful for debugging.
     */
    public void clear() {
        Log.d(cachedCtx, TAG, "Clearing all messages ");
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_USER_CACHE, null, null);
        db.close();
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS "+TABLE_USER_CACHE);
        onCreate(sqLiteDatabase);
    }

    /* BEGIN: Methods that are invoked to get the data for syncing to the host
     * Note that these are not defined in the interface, since other methods for syncing,
     * such as couchdb and azure, have their own syncing mechanism that don't depend on our
     * REST API.
     */

    /*
     * Return a string version of the messages and rw documents that need to be sent to the server.
     */

    public JSONArray sync_phone_to_server() throws JSONException {
        String selectQuery = "SELECT * from " + TABLE_USER_CACHE +
                " WHERE " + KEY_TYPE + " = '"+ MESSAGE_TYPE + "' OR " + KEY_TYPE + " = '" + RW_DOCUMENT_TYPE + "'" +
                " ORDER BY "+KEY_WRITE_TS;

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor queryVal = db.rawQuery(selectQuery, null);

        int resultCount = queryVal.getCount();
        JSONArray entryArray = new JSONArray();

        // Returns fals if the cursor is empty
        // in which case we return the empty JSONArray, to be consistent.
        if (queryVal.moveToFirst()) {
            for (int i = 0; i < resultCount; i++) {
                Metadata md = new Metadata();
                md.setWrite_ts(queryVal.getLong(0));
                md.setRead_ts(queryVal.getLong(1));
                md.setType(queryVal.getString(2));
                md.setKey(queryVal.getString(3));
                md.setPlugin(queryVal.getString(4));
                String dataStr = queryVal.getString(5);
                /*
                 * I used to have a GSON wrapper here called "Entry" which encapsulated the metadata
                 * and the data. However, that didn't really work because it was unclear what type
                 * the data was.
                 *
                 * If we assumed that the data was a string, then GSON would escape and encode it
                 * during serialization (e.g. {"data":"{\"mProvider\":\"TEST\",\"mResults\":[0.0,0.0],\"mAccuracy\":5.5,
                 * or {"data":"[\u0027accelerometer\u0027, \u0027gyrometer\u0027, \u0027linear_accelerometer\u0027]
                 * , and expect an encoded string during deserialization.
                 *
                 * This is not consistent with the server, which returns actual JSON in the data, not a string.
                 *
                 * We could attempt to overcome this by assuming that the data is an object, not a string. But in that case,
                 * it is not clear how it would be deserialized, since we wouldn't know what class it was.
                 *
                 * So we are going to return a raw JSON object here instead of a GSONed object. That will also allow us to
                 * put it into the right wrapper object (phone_to_server or server_to_phone).
                 */
                JSONObject entry = new JSONObject();
                entry.put(METADATA_TAG, new JSONObject(new Gson().toJson(md)));
                entry.put(DATA_TAG, new JSONObject(dataStr));
                Log.d(cachedCtx, TAG, "For row " + i + ", about to send string " + entry.toString());
                entryArray.put(entry);
                queryVal.moveToNext();
            }
        }
        db.close();
        return entryArray;
    }

    public void sync_server_to_phone(JSONArray entryArray) throws JSONException {
        SQLiteDatabase db = this.getReadableDatabase();
        for (int i = 0; i < entryArray.length(); i++) {
            /*
             * I used to use a GSON entry class here but switched to JSON instead.
             * Look at the comment in sync_phone_to_server for details.
             */
            JSONObject entry = entryArray.getJSONObject(i);
            Metadata md = new Gson().fromJson(entry.getJSONObject(METADATA_TAG).toString(), Metadata.class);
            ContentValues newValues = new ContentValues();
            newValues.put(KEY_WRITE_TS, md.getWrite_ts());
            newValues.put(KEY_READ_TS, md.getRead_ts());
            newValues.put(KEY_TYPE, md.getType());
            newValues.put(KEY_KEY, md.getKey());
            newValues.put(KEY_PLUGIN, md.getPlugin());
            // We use get() here instead of getJSONObject() because we can get either an object or
            // an array
            newValues.put(KEY_DATA, entry.get(DATA_TAG).toString());
            db.insert(TABLE_USER_CACHE, null, newValues);
        }
        db.close();
    }

    // END: Methods invoked for syncing the data to the host. Not part of the interface.
}
