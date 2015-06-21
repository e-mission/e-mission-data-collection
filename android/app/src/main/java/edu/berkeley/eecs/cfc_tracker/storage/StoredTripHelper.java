package edu.berkeley.eecs.cfc_tracker.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import edu.berkeley.eecs.cfc_tracker.Log;

public class StoredTripHelper extends SQLiteOpenHelper {
	// Database version
	private static final int DATABASE_VERSION = 1;
	
	// Database name
	private static final String DATABASE_NAME = "STORED_TRIP";
	
	// Table names
	// private static final String TABLE_NAME_TRIP_SUMMARY = "TRIP_SUMMARY";
	private static final String TABLE_STORED_TRIPS = "STORED_TRIPS";
	
	private static final String KEY_TS = "START_TIME";
	private static final String KEY_TRIP_BLOB = "TRIP_BLOB";

	private static final String TAG = "StoredTripHelper";
	private Context mCtxt;
		
	public StoredTripHelper(Context ctx) {
		/*
		 *  I would ideally like to include the start time as part of the database name.
		 *  But that would mean that we would need to pass in the start time for every
		 *  call, not just the initial creation. Since we anticipate only one ongoing trip at a time,
		 *  this doesn't seem to be too dangerous. 
		 */
		super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
		mCtxt = ctx;
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		// Create the first table
	    String CREATE_STORED_TRIPS_TABLE = "CREATE TABLE " + TABLE_STORED_TRIPS +" (" +
	            KEY_TS + " INTEGER, "+ KEY_TRIP_BLOB +" BLOB)";
	    Log.d(mCtxt, TAG, "CREATE_STORED_TRIPS_TABLE = "+CREATE_STORED_TRIPS_TABLE);
	    db.execSQL(CREATE_STORED_TRIPS_TABLE);
	}
	
	public void addTrip(long startTs, String tripBlob) {
		SQLiteDatabase db = this.getWritableDatabase();
		Log.d(mCtxt, TAG, "Adding trip "+tripBlob+" with timestamp "+startTs);
		ContentValues values = new ContentValues();
		values.put(KEY_TS, startTs);		
		values.put(KEY_TRIP_BLOB, tripBlob);
		long retVal = db.insert(TABLE_STORED_TRIPS, null, values);
		Log.d(mCtxt, TAG, "retVal = "+retVal);
		db.close();
	}
	
	public void updateTrip(long startTs, String tripBlob) {
		SQLiteDatabase db = this.getWritableDatabase();
		Log.d(mCtxt, TAG, "Updating trip "+tripBlob+" with timestamp "+startTs);
		
		ContentValues values = new ContentValues();
		values.put(KEY_TS, startTs);
		values.put(KEY_TRIP_BLOB, tripBlob);
		
//		String[] whereArgs = new String[]{String.valueOf(startTs)};
		long retVal = db.replace(TABLE_STORED_TRIPS, null, values);
		Log.d(mCtxt, TAG, "retVal = "+retVal);
		db.close();
	}
	
	public String getLastTrip() {
		SQLiteDatabase db = this.getReadableDatabase();
		
		String selectStmt = "SELECT "+KEY_TRIP_BLOB+" FROM "+TABLE_STORED_TRIPS+
				" ORDER BY "+KEY_TS+" DESC LIMIT 1";
		Log.d(mCtxt, TAG, "selectStmt = "+selectStmt);
		Cursor lastCursor = db.rawQuery(selectStmt, null);
		assert(lastCursor.getCount() == 0 || lastCursor.getCount() == 1);
		
		String retVal = null;
		/*
		 * If we have no data yet, then we return null.
		 */
		if (lastCursor.moveToFirst()) {
			retVal = lastCursor.getString(0);
		}
		db.close();
		return retVal;
	}
	
	public String[] getAllStoredTrips() {
		SQLiteDatabase db = this.getReadableDatabase();
		
		String selectStmt = "SELECT ROWID, "+KEY_TRIP_BLOB+" FROM "+TABLE_STORED_TRIPS+
				" ORDER BY "+KEY_TS;
		Log.d(mCtxt, TAG, "selectStmt = "+selectStmt);
		Cursor lastCursor = db.rawQuery(selectStmt, null);
		int resultCount = lastCursor.getCount();
		String[] retVal = new String[resultCount];

		/*
		 * If we have no data yet, then we return mode = UNKNOWN with 100% certainty.
		 */
		if (lastCursor.moveToFirst()) {
            for (int i = 0; i < resultCount; i++) {
            	long id = lastCursor.getLong(0);
            	retVal[i] = lastCursor.getString(1);
            	Log.d(mCtxt, TAG, "JSON string for id = "+id+" at index "+i+" is "+retVal[i]);
            	lastCursor.moveToNext();
            }			
		}
		db.close();
		return retVal;
	}

	public void deleteTrips(long[] tsArray) {
		SQLiteDatabase db = this.getReadableDatabase();
		String[] whereArgs = new String[tsArray.length];
		StringBuffer questionMarks = new StringBuffer("(");
		for (int i = 0; i < tsArray.length; i++) {
			if (i != 0) {
				questionMarks.append(",");
			}
			questionMarks.append("?");
			whereArgs[i] = String.valueOf(tsArray[i]);
		}
		questionMarks.append(")");
		Log.d(mCtxt, TAG, "questionMarks = "+questionMarks.toString());
		db.delete(TABLE_STORED_TRIPS, KEY_TS + " IN "+questionMarks.toString(), whereArgs);
		db.close();
	}
	
	public void clear() {
	    SQLiteDatabase db = this.getWritableDatabase();
	    db.delete(TABLE_STORED_TRIPS, null, null);
	    db.close();
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// DROP all existing data and re-create.
		// This may drop an ongoing trip if the update happens while we are in motion but that is no big loss
		// We could consider more sophisticated approaches if we know what the upgrade entails.
		db.execSQL("DROP TABLE IF EXISTS "+TABLE_STORED_TRIPS);
		onCreate(db);
	}
}
