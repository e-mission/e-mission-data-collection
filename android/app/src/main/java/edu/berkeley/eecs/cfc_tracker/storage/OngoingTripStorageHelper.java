package edu.berkeley.eecs.cfc_tracker.storage;

import java.util.HashMap;
import java.util.Map;

import com.google.android.gms.location.DetectedActivity;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import edu.berkeley.eecs.cfc_tracker.Log;

public class OngoingTripStorageHelper extends SQLiteOpenHelper {
	// Database version
	private static final int DATABASE_VERSION = 3;
	
	// Database name
	private static final String DATABASE_NAME = "ONGOING_TRIP";
	
	// Table names
	// private static final String TABLE_NAME_TRIP_SUMMARY = "TRIP_SUMMARY";
	private static final String TABLE_TRACK_POINTS = "TRACK_POINTS";
	private static final String TABLE_MODE_CHANGES = "MODE_CHANGES";
	
	private static final String KEY_TS = "TIMESTAMP";
    private static final String KEY_WALL_CLOCK_TS = "WALL_CLOCK_TIMESTAMP";
	private static final String KEY_LAT = "LAT";
	private static final String KEY_LNG = "LNG";
	private static final String KEY_ACCURACY = "ACCURACY";

	private static final String KEY_TYPE = "TYPE";
	private static final String KEY_CONFIDENCE = "CONFIDENCE";

	private static final String TAG = "OngoingTripStorageHelper";
	
	public OngoingTripStorageHelper(Context ctx) {
		/*
		 *  I would ideally like to include the start time as part of the database name.
		 *  But that would mean that we would need to pass in the start time for every
		 *  call, not just the initial creation. Since we anticipate only one ongoing trip at a time,
		 *  this doesn't seem to be too dangerous. 
		 */
		super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		// Create the first table
	    String CREATE_TRACK_POINTS_TABLE = "CREATE TABLE " + TABLE_TRACK_POINTS +" (" +
	            KEY_TS + " INTEGER, "+ KEY_WALL_CLOCK_TS + " INTEGER, " +
                KEY_LAT +" REAL, " + KEY_LNG + " REAL, " +
	    		KEY_ACCURACY + " REAL)";
	    Log.d(TAG, "CREATE_TRACK_POINTS_TABLE = "+CREATE_TRACK_POINTS_TABLE);
	    db.execSQL(CREATE_TRACK_POINTS_TABLE);
	    // Create the second table
	    String CREATE_MODE_CHANGE_TABLE = "CREATE TABLE " + TABLE_MODE_CHANGES +" (" +
	            KEY_TS + " INTEGER, "+ KEY_TYPE +" INTEGER, " + KEY_CONFIDENCE + " INTEGER)";
	    Log.d(TAG, "CREATE_MODE_CHANGE_TABLE = "+CREATE_MODE_CHANGE_TABLE);
	    db.execSQL(CREATE_MODE_CHANGE_TABLE);
	}
	
	public void addPoint(Location loc) {
		SQLiteDatabase db = this.getWritableDatabase();
		
		ContentValues values = new ContentValues();
		values.put(KEY_TS, loc.getElapsedRealtimeNanos());
        values.put(KEY_WALL_CLOCK_TS, loc.getTime());
		values.put(KEY_LAT, loc.getLatitude());
		values.put(KEY_LNG, loc.getLongitude());
		if (loc.hasAccuracy()) {
			values.put(KEY_ACCURACY, loc.getAccuracy());
		}
		db.insert(TABLE_TRACK_POINTS, null, values);
		db.close();
	}
	
	public void addModeChange(long ts, DetectedActivity mode) {
		SQLiteDatabase db = this.getWritableDatabase();
		
		ContentValues values = new ContentValues();
		values.put(KEY_TS,  ts);
		values.put(KEY_TYPE, mode.getType());
		values.put(KEY_CONFIDENCE, mode.getConfidence());
		db.insert(TABLE_MODE_CHANGES, null, values);
		db.close();
	}
	
	public DataUtils.ModeChange getCurrentMode() {
		SQLiteDatabase db = this.getReadableDatabase();
		
		String selectStmt = "SELECT * FROM "+TABLE_MODE_CHANGES+
				" ORDER BY "+KEY_TS+" DESC LIMIT 1";
		Log.d(TAG, "selectStmt = "+selectStmt);
		Cursor lastCursor = db.rawQuery(selectStmt, null);
		assert(lastCursor.getCount() == 0 || lastCursor.getCount() == 1);
		/*
		 * If we have no data yet, then we return mode = UNKNOWN with 100% certainty.
		 */
		DataUtils.ModeChange currMode = new DataUtils.ModeChange(0,
				new DetectedActivity(DetectedActivity.UNKNOWN, 100));
		if (lastCursor.moveToFirst()) {
			Map<String, Integer> colMap = getColumnMap(lastCursor);
			currMode = getModeChangeFromCursor(colMap, lastCursor);
		}
		db.close();
		return currMode;
	}
	
	public Location[] getLastPoints(int nPoints) {
		String selectStmt = "SELECT * FROM "+TABLE_TRACK_POINTS+
				" ORDER BY "+KEY_TS+" DESC LIMIT "+nPoints;
		Log.d(TAG, "selectStmt = "+selectStmt);
		return getLocationsFromStmt(selectStmt);
	}
	
	public static Map<String, Integer> getColumnMap(Cursor cursor) {
		Map<String, Integer> retMap = new HashMap<String, Integer>();
		int nCols = cursor.getColumnCount();
		for (int i = 0; i < nCols; i++) {
			retMap.put(cursor.getColumnName(i), i);
		}
		return retMap;
	}
	
	public Location[] getLocationsFromStmt(String selectStmt) {
		SQLiteDatabase db = this.getReadableDatabase();
		
		Cursor lastCursor = db.rawQuery(selectStmt, null);
		int resultCount = lastCursor.getCount();
		Log.d(TAG, "Found "+resultCount+" locations that matched the statement");
//		assert(resultCount < nPoints);
		/*
		 * If we have no data yet, then we return mode = UNKNOWN with 100% certainty.
		 */
		Location[] retLoc = new Location[resultCount];
		Map<String, Integer> colMap = getColumnMap(lastCursor);
		if (lastCursor.moveToFirst()) {
			for (int i = 0; i < resultCount; i++) {
				Log.d(TAG, "Reading result at location "+i);
				retLoc[i] = getLocationFromCursor(colMap, lastCursor);
				Log.d(TAG, "Result is "+retLoc[i]);
				lastCursor.moveToNext();
			}
		}
		db.close();
		return retLoc;		
	}
	
	public Location[] getEndPoints() {
		String startSelectStmt = "SELECT * FROM "+TABLE_TRACK_POINTS+
				" ORDER BY "+KEY_TS+" LIMIT 1";
		String endSelectStmt = "SELECT * FROM "+TABLE_TRACK_POINTS+
				" ORDER BY "+KEY_TS+" DESC LIMIT 1";
		Log.d(TAG, "startSelectStmt = "+startSelectStmt);
		Log.d(TAG, "endSelectStmt = "+endSelectStmt);

		Location[] retArray = new Location[2];
		retArray[0] = getLocationFromStmt(startSelectStmt);
		retArray[1] = getLocationFromStmt(endSelectStmt);

		return retArray;
	}
	
	public Location getLocationFromStmt(String selectStmt) {
		SQLiteDatabase db = this.getReadableDatabase();
	
		Cursor lastCursor = db.rawQuery(selectStmt, null);
		int resultCount = lastCursor.getCount();
		assert(resultCount < 1);
		/*
		 * If we have no data yet, then we return mode = UNKNOWN with 100% certainty.
		 */
		Map<String, Integer> colMap = getColumnMap(lastCursor);
		if (lastCursor.moveToFirst()) {
			return getLocationFromCursor(colMap, lastCursor);
		}
		db.close();
		return null;
	}
	
	public static Location getLocationFromCursor(Map<String, Integer> colMap, Cursor lastCursor) {
		Location currLocation = new Location("DATABASE");
        currLocation.setElapsedRealtimeNanos(lastCursor.getLong(colMap.get(KEY_TS)));
        currLocation.setTime(lastCursor.getLong(colMap.get(KEY_WALL_CLOCK_TS)));
        currLocation.setLatitude(lastCursor.getDouble(colMap.get(KEY_LAT)));
		currLocation.setLongitude(lastCursor.getDouble(colMap.get(KEY_LNG)));
		currLocation.setAccuracy(lastCursor.getFloat(colMap.get(KEY_ACCURACY)));
		return currLocation;
	}
	
	public static DataUtils.ModeChange getModeChangeFromCursor(Map<String, Integer> colMap, Cursor lastCursor) {
		return new DataUtils.ModeChange(lastCursor.getLong(colMap.get(KEY_TS)),
				new DetectedActivity(lastCursor.getInt(colMap.get(KEY_TYPE)),
						lastCursor.getInt(colMap.get(KEY_CONFIDENCE))));
	}

	public DataUtils.ModeChange[] getModeChanges() {
		SQLiteDatabase db = this.getReadableDatabase();
		
		String selectStmt = "SELECT * FROM "+TABLE_MODE_CHANGES;
		Log.d(TAG, "selectStmt = "+selectStmt);
		Cursor lastCursor = db.rawQuery(selectStmt, null);
		int resultCount = lastCursor.getCount();

		DataUtils.ModeChange[] retArray = new DataUtils.ModeChange[resultCount];
		// TODO: Remove the i indexing and do a for loop based on the cursor alone?
		if (lastCursor.moveToFirst()) {
			Map<String, Integer> colMap = getColumnMap(lastCursor);
			for (int i = 0; i < resultCount; i++) {
				retArray[i] = getModeChangeFromCursor(colMap, lastCursor);
				lastCursor.moveToNext();
			}
		}
		db.close();
		return retArray;
	}

	public Location[] getPoints(long startTs, long endTs) {
        // TODO: If elapsed time works better, need to switch this to elapsed time as well
		String selectStmt = "SELECT * FROM "+TABLE_TRACK_POINTS+
				" WHERE "+KEY_TS+" >= "+startTs+" AND "+KEY_TS+" < "+endTs;
		Log.d(TAG, "selectStmt = "+selectStmt);
		return getLocationsFromStmt(selectStmt);
	}
	
	public void clear() {
	    SQLiteDatabase db = this.getWritableDatabase();
	    db.delete(TABLE_TRACK_POINTS, null, null);
	    db.delete(TABLE_MODE_CHANGES, null, null);
	    db.close();
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// DROP all exisiting data and re-create.
		// This may drop an ongoing trip if the update happens while we are in motion but that is no big loss
		// We could consider more sophisticated approaches if we know what the upgrade entails.
		db.execSQL("DROP TABLE IF EXISTS "+TABLE_MODE_CHANGES);
		db.execSQL("DROP TABLE IF EXISTS "+TABLE_TRACK_POINTS);
		onCreate(db);
	}
}
