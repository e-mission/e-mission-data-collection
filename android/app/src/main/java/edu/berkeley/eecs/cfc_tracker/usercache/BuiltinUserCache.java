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
import java.util.TimeZone;

import edu.berkeley.eecs.cfc_tracker.NotificationHelper;
import edu.berkeley.eecs.cfc_tracker.R;
import edu.berkeley.eecs.cfc_tracker.location.LocationTrackingConfig;
import edu.berkeley.eecs.cfc_tracker.log.Log;
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
    private static final String KEY_TIMEZONE = "timezone";
    private static final String KEY_TYPE = "type";
    private static final String KEY_KEY = "key";
    private static final String KEY_PLUGIN = "plugin";
    private static final String KEY_DATA = "data";

    private static final String TAG = "BuiltinUserCache";

    private static final String METADATA_TAG = "metadata";
    private static final String DATA_TAG = "data";

    private static final String SENSOR_DATA_TYPE = "sensor-data";
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
                KEY_WRITE_TS + " REAL, "+ KEY_READ_TS +" REAL, " +
                KEY_TIMEZONE + " TEXT, " +
                KEY_TYPE + " TEXT, " + KEY_KEY + " TEXT, "+
                KEY_PLUGIN + " TEXT, " + KEY_DATA + " TEXT)";
        System.out.println("CREATE_USER_CACHE_TABLE = " + CREATE_USER_CACHE_TABLE);
        sqLiteDatabase.execSQL(CREATE_USER_CACHE_TABLE);
    }

    private String getKey(int keyRes) {
        return cachedCtx.getString(keyRes);
    }

    @Override
    public void putSensorData(int keyRes, Object value) {
        putValue(keyRes, value, SENSOR_DATA_TYPE);
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
        newValues.put(KEY_WRITE_TS, ((double)System.currentTimeMillis()/1000));
        newValues.put(KEY_TIMEZONE, TimeZone.getDefault().getID());
        newValues.put(KEY_TYPE, type);
        newValues.put(KEY_KEY, getKey(keyRes));
        newValues.put(KEY_DATA, new Gson().toJson(value));
        db.insert(TABLE_USER_CACHE, null, newValues);
        System.out.println("Added value for key "+ cachedCtx.getString(keyRes) +
                " at time "+newValues.getAsDouble(KEY_WRITE_TS));
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
        return getValuesForInterval(keyRes, MESSAGE_TYPE, tq, classOfT);
    }

    @Override
    public <T> T[] getSensorDataForInterval(int keyRes, TimeQuery tq, Class<T> classOfT) {
        return getValuesForInterval(keyRes, SENSOR_DATA_TYPE, tq, classOfT);
    }

    public <T> T[] getValuesForInterval(int keyRes, String type, TimeQuery tq, Class<T> classOfT) {
        /*
         * Note: the first getKey(keyRes) is the key of the message (e.g. 'background/location').
         * The second getKey(tq.keyRes) is the key of the time query (e.g. 'write_ts')
         */
        String queryString = "SELECT "+KEY_DATA+" FROM "+TABLE_USER_CACHE+
                " WHERE "+KEY_KEY+" = '"+getKey(keyRes)+ "'"+
                " AND "+getKey(tq.keyRes)+" >= "+tq.startTs+
                " AND "+getKey(tq.keyRes)+" <= "+tq.endTs+
                " ORDER BY write_ts DESC";
        System.out.println("About to execute query "+queryString);
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor resultCursor = db.rawQuery(queryString, null);

        T[] result = getValuesFromCursor(resultCursor, classOfT);
        db.close();
        return result;
    }

    @Override
    public <T> T[] getLastMessages(int keyRes, int nEntries, Class<T> classOfT) {
        return getLastValues(keyRes, MESSAGE_TYPE, nEntries, classOfT);
    }

    @Override
    public <T> T[] getLastSensorData(int keyRes, int nEntries, Class<T> classOfT) {
        return getLastValues(keyRes, SENSOR_DATA_TYPE, nEntries, classOfT);
    }

    public <T> T[] getLastValues(int keyRes, String type, int nEntries, Class<T> classOfT) {
        String queryString = "SELECT "+KEY_DATA+" FROM "+TABLE_USER_CACHE+
                " WHERE "+KEY_KEY+" = '"+getKey(keyRes)+ "'"+
                " ORDER BY write_ts DESC  LIMIT "+nEntries;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor resultCursor = db.rawQuery(queryString, null);

        T[] result = getValuesFromCursor(resultCursor, classOfT);
        db.close();
        return result;
    }

    private <T> T[] getValuesFromCursor(Cursor resultCursor, Class<T> classOfT) {
        int resultCount = resultCursor.getCount();
        T[] resultArray = (T[]) Array.newInstance(classOfT, resultCount);
        // System.out.println("resultArray is " + resultArray);
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
        updateValues.put(KEY_READ_TS, ((double)System.currentTimeMillis())/1000);
        updateValues.put(KEY_KEY, getKey(keyRes));
        writeDb.update(TABLE_USER_CACHE, updateValues, null, null);
        writeDb.close();
    }

    @Override
    public void clearEntries(TimeQuery tq) {
        Log.d(cachedCtx, TAG, "Clearing entries for timequery " + tq);
        String whereString = getKey(tq.keyRes) + " > ? AND " + getKey(tq.keyRes) + " < ?";
        String[] whereArgs = {String.valueOf(tq.startTs), String.valueOf(tq.endTs)};
        Log.d(cachedCtx, TAG, "Args =  " + whereString + " : " + Arrays.toString(whereArgs));
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
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + TABLE_USER_CACHE);
        onCreate(sqLiteDatabase);
    }

    /* BEGIN: Methods that are invoked to get the data for syncing to the host
     * Note that these are not defined in the interface, since other methods for syncing,
     * such as couchdb and azure, have their own syncing mechanism that don't depend on our
     * REST API.
     */

    /**
     * Ensure that we don't delete points that we are using for trip end
     * detection. Note that we could just refactor the check for trip end and
     * use the same logic to determine which points to delete - i.e. sync and
     * delete everything older than 30 mins, but I want to keep our options
     * open in case it turns out that we want to do some preprocessing of
     * sensitive trips on the phone before uploading them to the server.
     */
    public long getTsOfLastTransition() {
        /*
         * Find the last transition that was "stopped moving" using a direct SQL query.
         * Note that we cannot use the @see getLastMessage call here because that returns the messages
         * (the transition strings in this case) but not the metadata.
         */

        String selectQuery = "SELECT * FROM "+TABLE_USER_CACHE+
                " WHERE "+KEY_KEY+" = '"+getKey(R.string.key_usercache_transition)+ "'"+
                " AND "+KEY_DATA+" LIKE '%_transition_:_" + cachedCtx.getString(R.string.transition_stopped_moving) +"_%'"+
                " ORDER BY write_ts DESC  LIMIT 1";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor resultCursor = db.rawQuery(selectQuery, null);
        Log.d(cachedCtx, TAG, "While searching for regex, got "+resultCursor.getCount()+" results");
        if (resultCursor.moveToFirst()) {
            Log.d(cachedCtx, TAG, resultCursor.getLong(resultCursor.getColumnIndex(KEY_WRITE_TS)) + ": "+
                    resultCursor.getString(resultCursor.getColumnIndex(KEY_DATA)));
            return resultCursor.getLong(resultCursor.getColumnIndex(KEY_WRITE_TS));
        } else {
            // There was one instance when it looked like the regex search did not work.
            // However, it turns out that it was just a logging issue.
            // Let's have a more robust fallback and see how often we need to use it.
            // But this should almost certainly be removed before deployment.
            String selectQueryAllTrans = "SELECT * FROM "+TABLE_USER_CACHE+
                    " WHERE "+KEY_KEY+" = '"+getKey(R.string.key_usercache_transition)+ "'"+
                    " ORDER BY write_ts DESC";
            Cursor allCursor = db.rawQuery(selectQueryAllTrans, null);

            int resultCount = allCursor.getCount();
            Log.d(cachedCtx, TAG, "While searching for all, got "+resultCount+" results");
            if (allCursor.moveToFirst() && resultCount > 0) {
                for (int i = 0; i < resultCount; i++) {
                    Log.d(cachedCtx, TAG, "Considering transition "+
                            allCursor.getLong(allCursor.getColumnIndex(KEY_WRITE_TS)) + ": "+
                            allCursor.getString(allCursor.getColumnIndex(KEY_DATA)));
                    if(allCursor.getString(allCursor.getColumnIndex(KEY_DATA))
                            .contains("\"transition\":\"local.transition.stopped_moving\"")) {
                        // when we find stopped moving, we return, so this must be the first
                        // time we have found it
                        NotificationHelper.createNotification(cachedCtx, 5, "Had to look in all!");
                        Log.w(cachedCtx, TAG, "regex did not find entry, had to search all");
                        return allCursor.getLong(allCursor.getColumnIndex(KEY_WRITE_TS));
                    }
                    allCursor.moveToNext();
                }
            } else {
                Log.d(cachedCtx, TAG, "There are no entries in the usercache." +
                        "A sync must have just completed!");
            }
        }
        // Did not find a stopped_moving transition.
        // This may mean that we have pushed all completed trips.
        // Since this is supposed to return the millisecond timestamp,
        // we just return a negative number (-1)
        return -1;
    }

    /*
     * If we never duty cycle, we don't have any transitions. So we can push to the server without
     * any issues. So we just find the last entry in the cache.
     */
    private long getTsOfLastEntry() {
        String selectQuery = "SELECT * FROM " + TABLE_USER_CACHE +
                " ORDER BY write_ts DESC LIMIT 1";
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor resultCursor = db.rawQuery(selectQuery, null);
        Log.d(cachedCtx, TAG, "While searching for regex, got " + resultCursor.getCount() + " results");
        if (resultCursor.moveToFirst()) {
            Log.d(cachedCtx, TAG, resultCursor.getLong(resultCursor.getColumnIndex(KEY_WRITE_TS)) + ": " +
                    resultCursor.getString(resultCursor.getColumnIndex(KEY_DATA)));
            return resultCursor.getLong(resultCursor.getColumnIndex(KEY_WRITE_TS));
        } else {
            Log.d(cachedCtx, TAG, "There are no entries in the usercache." +
                    "A sync must have just completed!");
        }
        return -1;
    }

    private long getLastTs() {
        if (LocationTrackingConfig.getConfig(cachedCtx).isDutyCycling()) {
            return getTsOfLastTransition();
        } else {
            return getTsOfLastEntry();
        }
    }

    /*
     * Return a string version of the messages and rw documents that need to be sent to the server.
     */

    public JSONArray sync_phone_to_server() throws JSONException {
        long lastTripEndTs = getLastTs();
        Log.d(cachedCtx, TAG, "Last trip end was at "+lastTripEndTs);

        if (lastTripEndTs < 0) {
            // We don't have a completed trip, so we don't want to push anything yet.
            return new JSONArray();
        }

        String selectQuery = "SELECT * from " + TABLE_USER_CACHE +
                " WHERE " + KEY_TYPE + " = '"+ MESSAGE_TYPE +
                "' OR " + KEY_TYPE + " = '" + RW_DOCUMENT_TYPE +
                "' OR " + KEY_TYPE + " = '" + SENSOR_DATA_TYPE + "'" +
                " AND " + KEY_WRITE_TS + " < " + lastTripEndTs +
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
                md.setTimeZone(queryVal.getString(2));
                md.setType(queryVal.getString(3));
                md.setKey(queryVal.getString(4));
                md.setPlugin(queryVal.getString(5));
                String dataStr = queryVal.getString(6);
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
