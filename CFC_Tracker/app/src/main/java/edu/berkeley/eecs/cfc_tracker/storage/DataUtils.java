package edu.berkeley.eecs.cfc_tracker.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.android.gms.location.DetectedActivity;

import android.content.Context;
import android.location.Location;
import android.text.format.DateFormat;

import edu.berkeley.eecs.cfc_tracker.Constants;
import edu.berkeley.eecs.cfc_tracker.Log;

/** Common process for writing data to phone DB */
public class DataUtils {
	static class CurrentState {
		private ModeChange currentMode;
		private Location[] lastPoints;
		public ModeChange getCurrentMode() {
			return currentMode;
		}
		public void setCurrentMode(ModeChange currentMode) {
			this.currentMode = currentMode;
		}
		public Location[] getLastPoints() {
			return lastPoints;
		}
		public void setLastPoints(Location[] lastPoints) {
			this.lastPoints = lastPoints;
		}
	}

	private static final String TAG = "DataUtils";
    private static final String DATE_FORMAT = "yyyyMMddTHHmmssz";

	/*
	 * Since we will be checking the current state on every update (every 30 secs),
	 * it would be great if we could not always read the data from the database.
	 * We do need to account for the app getting killed and losing in-memory data.
	 * Maybe a cache is a good compromise. We will hold the data in memory and populate
	 * from the database if it is not in memory
	 
	private CurrentState getCurrentState() {
		if 
		return null;
	}
	*/
	
	public static void addPoint(Context ctxt, Location currLoc) {
		Log.d(TAG, "addPoint("+currLoc+") called");
		new OngoingTripStorageHelper(ctxt).addPoint(currLoc);
	}
	
	public static Location[] getLastPoints(Context ctxt, int nPoints) {
		Log.d(TAG, "getLastPoints("+nPoints+") called");
		return new OngoingTripStorageHelper(ctxt).getLastPoints(nPoints);
	}

	public static void addModeChange(Context ctxt, long ts, DetectedActivity newMode) {
		Log.d(TAG, "addModeChange("+ts+"," + newMode +") called");
		new OngoingTripStorageHelper(ctxt).addModeChange(ts, newMode);
	}
	
	public static class ModeChange {
		private long lastChangeTs;
		private DetectedActivity lastActivity;
		    
		public ModeChange(long lastChangeTs, DetectedActivity lastActivity) {
		  this.lastChangeTs = lastChangeTs;
		  this.lastActivity = lastActivity;
		}
		
		public long getLastChangeTs() {
			return lastChangeTs;
		}
		
		public DetectedActivity getLastActivity() {
			return lastActivity;
		}
	};
	  
	public static ModeChange getCurrentMode(Context ctxt) {
		Log.d(TAG, "getCurrentMode() called");
		return new OngoingTripStorageHelper(ctxt).getCurrentMode();
	}
	
	public static void endTrip(Context ctxt) {
		/*
		 * So what should we do if we are converting the ongoing trip to a stored trip, and
		 * we get a JSON exception on the way? First, note that there are multiple operations
		 * happening here, so we really want to wrap everything in a transaction. So if there
		 * is an exception, we definitely want to abort the transaction so that we are not
		 * in an inconsistent state.
		 * After aborting, what do we do?
		 * a) We can ignore it and lose the trip
		 * b) We can retry and try to retrieve the trip.
		 * 
		 * As always, retrying is good, but is not guaranteed to work.
		 * So we should probably retry (maybe once) to deal with transient issues, but ignore long-term.
		 * What's a trip between friends? :)
		 */
		Log.d(TAG, "endTrip called");
		try {
			convertOngoingToStored(ctxt);
		} catch (JSONException e) {
			Log.e(TAG, "Got initial error "+e+" while saving ongoing trips, retrying once to see if it is a transient error");
			try {
				convertOngoingToStored(ctxt);
			} catch (JSONException eFinal) {
				Log.e(TAG, "Got final error "+eFinal+" while saving ongoing trips, deleting DB and abandoning");
			}
		}
		clearOngoingDb(ctxt);
	}
	
	public static void clearOngoingDb(Context ctxt) {
		Log.d(TAG, "clearOngoingDb called");
		OngoingTripStorageHelper db = new OngoingTripStorageHelper(ctxt);
		db.clear();
	}
	
	public static void convertOngoingToStored(Context ctxt) throws JSONException {
		Log.d(TAG, "convertOngoingToStored called");
		OngoingTripStorageHelper db = new OngoingTripStorageHelper(ctxt);
		StoredTripHelper storedDb = new StoredTripHelper(ctxt);
		
		// The moves format is a little clunky because a "segment" of type "place" is
		// part of the array. It is not clear whether we need this, but let's do it for
		// now to keep life easier
		Location[] endPoints = db.getEndPoints();
		Location startPoint = endPoints[0];
		Location endPoint = endPoints[1];

		Log.d(TAG, "startPoint = "+startPoint+" endPoint = "+endPoint);
		if (startPoint == null && endPoint == null) {
			// The trip has no points, so we can't store anything
			return;
		}

		JSONObject startPlace = null;
		String lastTripString = storedDb.getLastTrip();
		Log.d(TAG, "lastTripString = "+lastTripString);
		if (lastTripString == null) {
			// This is the first time we have started running, don't have any data so don't have any pending trip to complete.
			// Let's just create an object with the current location and a start time of midnight today
			startPlace = getJSONPlace(startPoint);
			Date now = new Date();
			Calendar nowCal = Calendar.getInstance();
			nowCal.setTime(now);
			nowCal.set(Calendar.DATE, nowCal.get(Calendar.DATE) - 1);
			nowCal.set(Calendar.HOUR, 0);
			long midnight = nowCal.getTimeInMillis();
			startPlace.put("startTime", DateFormat.format(DATE_FORMAT, new Date(midnight)));
            // TODO: Change to millisecond timestamp throughout system
            startPlace.put("startTimeTs", midnight);
		} else {
			startPlace = new JSONObject(storedDb.getLastTrip());
		}
		
		// Our end point in the start place is when this trip starts
		startPlace.put("endTime", DateFormat.format(DATE_FORMAT, new Date(startPoint.getTime())));
		Log.d(TAG, "updated startPlace = "+startPlace.toString());
		storedDb.updateTrip(startPlace.getLong("startTimeTs"), startPlace.toString());

        long startTripUTC = startPoint.getTime();
        long startTripElapsedTime = startPoint.getElapsedRealtimeNanos();

		JSONObject completedTrip = new JSONObject();
		completedTrip.put("type", "move");
		completedTrip.put("startTime", DateFormat.format(DATE_FORMAT, new Date(startPoint.getTime())));
        completedTrip.put("startTimeTs", startPoint.getTime());
        // TODO: If we put the end time of the trip into the DB like this, it may not be consistent
        // with the track point queries, which are based on elapsed time. Figure out whether we need
        // to regenerate actual time from start time, start elapsed time and end elapsed time as well
        // This may not be necessary because we only end the trip when we have been stable for ~ 15 minutes
		completedTrip.put("endTime", DateFormat.format(DATE_FORMAT, new Date(elapsedToUTC(startTripUTC,
                startTripElapsedTime,
                endPoint.getElapsedRealtimeNanos()))));

		JSONArray activityArray = new JSONArray();
		ModeChange[] modeChanges = db.getModeChanges();
        Log.d(TAG, "mode change list is of size "+modeChanges.length);
		
		/*
		 * We request mode changes at the same frequency as location changes (30 secs),
		 * and we wait for at least 5 locations that are under 100m in length to stop
		 * so it is unlikely that we will have location changes without mode changes.
		 * However, we get the first location as the point that we leave the geofence,
		 * and that's when we turn on mode detection, so the first mode detection
		 * will be at the time of the second point. Specially if one is driving, this
		 * might be significantly further away than the actual start point.
		 * 
		 * So we need to do the following:
		 * - Handle the case where there are no mode changes 
		 *       (by creating a single activity with unknown mode)
		 * - Handle the case where there is a single mode change
		 *       (by creating a single activity with that mode)
		 * - Handle the case where there are multiple mode changes
		 * 		 (by adding the first point to the first mode) 
		 */

		if (modeChanges.length == 0) {
            Log.i(TAG, "Found zero mode changes, creating one unknown section");
			JSONObject currSection = createSection(startTripUTC, startTripElapsedTime,
                    startPoint.getElapsedRealtimeNanos(), endPoint.getElapsedRealtimeNanos(),
                    "unknown", db);
			activityArray.put(currSection);
		}
		
		if (modeChanges.length == 1) {
            Log.i(TAG, "Found one mode change, creating one section of type "+
                    activityType2Name(modeChanges[0].getLastActivity().getType()));
			JSONObject currSection = createSection(startTripUTC, startTripElapsedTime,
                    startPoint.getElapsedRealtimeNanos(), endPoint.getElapsedRealtimeNanos(),
					activityType2Name(modeChanges[0].getLastActivity().getType()), db);
			activityArray.put(currSection);
		}
		
		if (modeChanges.length > 1) {
            Log.i(TAG, "Found more than one mode change, iterating through them ");

            for (int i=0; i < modeChanges.length; i++)
			{
                long startTs = modeChanges[i].getLastChangeTs();
                String activityType = activityType2Name(modeChanges[i].getLastActivity().getType());
                long endTs = 0;
				// If this is the first point, we actually count points from
				// the start of the trip
				if (i == 0) {
					startTs = startPoint.getElapsedRealtimeNanos();
				}

                /*
                 * If this is the end point, we count points to the end of the trip
                 * Note that we cannot use the default/override pattern here like we
                 * did for startTs, because if this is the last segment, then the default would be
                 * to end at modeChange[i+1] and that will cause an ArrayOutOfBounds exception.
                 */
                if (i == modeChanges.length - 1) {
                    endTs = endPoint.getElapsedRealtimeNanos();
                } else {
                    endTs = modeChanges[i+1].getLastChangeTs();
                }

                JSONObject currSection = createSection(startTripUTC, startTripElapsedTime,
                        startTs,
						endTs,
						activityType,
						db);
				activityArray.put(currSection);
			}
		}
		if (activityArray.length() > 0) {
			completedTrip.put("activities", activityArray);
		}
		Log.d(TAG, "completedTrip = "+completedTrip.toString());
		storedDb.addTrip(completedTrip.getLong("startTimeTs"), completedTrip.toString());
		
		
		// We are in the end place starting now.
		// Dunno when we will stop.
		// But when we do, this will be the startPlace and we will set the endTime above
		JSONObject endPlace = getJSONPlace(endPoint);
		Log.d(TAG, "endPlace = "+endPlace.toString());
		storedDb.addTrip(endPlace.getLong("startTimeTs"), endPlace.toString());
	}
	
	public static JSONObject createSection(Long startTripUTC, Long startTripElapsedTime,
                                                    Long startSectionElapsedTime,
                                                    Long endSectionElapsedTime,
			String activityType, OngoingTripStorageHelper db) throws JSONException {
		JSONObject currSection = new JSONObject();
		currSection.put("startTime", DateFormat.format(DATE_FORMAT, new Date(elapsedToUTC(
                startTripUTC, startTripElapsedTime, startSectionElapsedTime))));
        // TODO: Switch to timestamps throughout system
        currSection.put("startTimeTs", elapsedToUTC(
                startTripUTC, startTripElapsedTime, startSectionElapsedTime));
		currSection.put("endTime", DateFormat.format(DATE_FORMAT, new Date(elapsedToUTC(
                startTripUTC, startTripElapsedTime, endSectionElapsedTime))));
		currSection.put("activity", activityType);
		currSection.put("group", activityType);
		currSection.put("duration", (endSectionElapsedTime - startSectionElapsedTime)/Constants.NANO2MS);
		
		Location[] pointsForSection = db.getPoints(
				startSectionElapsedTime, endSectionElapsedTime);
		JSONArray trackPoints = new JSONArray();
		double distance = 0;
		for (int j = 0; j < pointsForSection.length; j++) {
			JSONObject currPoint = getTrackPoint(pointsForSection[j], startTripUTC, startTripElapsedTime);
			trackPoints.put(currPoint);
			if (j < pointsForSection.length - 1) {
				distance = distance + pointsForSection[j].distanceTo(pointsForSection[j+1]);
			}
		}

		currSection.put("distance", distance);
		currSection.put("trackPoints", trackPoints);
		return currSection;
	}
	
	public static JSONObject getJSONPlace(Location loc) throws JSONException {
		Log.d(TAG, "getJSONPlace("+loc+") called");

		JSONObject retObj = new JSONObject();
		retObj.put("type", "place");
		retObj.put("startTime", DateFormat.format(DATE_FORMAT, new Date(loc.getTime())));
        // The server code currently expects string formatted dates, which are then sent to the
        // app which also expects string formatted dates.
        // TODO: Change everything to millisecond timestamps to avoid confusion
        retObj.put("startTimeTs", loc.getTime());
		
		JSONObject placeObj = new JSONObject();
		// retObj.put("type",  "unknown");

		JSONObject locationObj = new JSONObject();
		locationObj.put("lat", loc.getLatitude());
		locationObj.put("lon", loc.getLongitude());
		placeObj.put("location", locationObj);
        placeObj.put("id", "unknown");
        placeObj.put("type", "unknown");
		
		retObj.put("place", placeObj);
		return retObj;
	}
	
	public static JSONObject getTrackPoint(Location loc, long startUTC,
                                           long startElapsedRealTimeNanos) throws JSONException {
		Log.d(TAG, "getTrackPoint("+loc+") called");
		
		JSONObject retObject = new JSONObject();
        // Convert Nanos to Millis before adding to the startUTC, since
        long currUTC = elapsedToUTC(startUTC, startElapsedRealTimeNanos, loc.getElapsedRealtimeNanos());
		retObject.put("time", DateFormat.format(DATE_FORMAT, new Date(currUTC)));
		retObject.put("lat", loc.getLatitude());
		retObject.put("lon", loc.getLongitude());
		retObject.put("accuracy", loc.getAccuracy());
        retObject.put("loc_utc_ts", loc.getTime());
        retObject.put("loc_elapsed_ts", loc.getElapsedRealtimeNanos());
		return retObject;
	}
	
	public static JSONArray getTripsToPush(Context ctxt) throws JSONException {
		Log.d(TAG, "getTripsToPush() called");
		
		StoredTripHelper storedDb = new StoredTripHelper(ctxt);
		// TODO: Decide how to deal with staying in a place overnight (say from 8pm to 8am)
		// Do we have a place that ends at midnight and another that starts the next morning
		// Or do we have a single place that extends from 8pm to 8am?
		// Right now, return a single place that extends from 8pm to 8am to make the code easier
		
		String[] allTrips = storedDb.getAllStoredTrips();
		JSONArray retVal = new JSONArray();
		for (int i = 0; i < allTrips.length - 1; i++) {
			retVal.put(new JSONObject(allTrips[i]));
		}
		return retVal;
	}
	
	public static void deletePushedTrips(Context ctxt, JSONArray tripsToPush) throws JSONException {
		Log.d(TAG, "deletePushedTrips("+tripsToPush.length()+") called");

		StoredTripHelper storedDb = new StoredTripHelper(ctxt);
		long[] tsArray = new long[tripsToPush.length()];
		for (int i = 0; i < tripsToPush.length(); i++) {
			tsArray[i] = tripsToPush.getJSONObject(i).getLong("startTimeTs");
		}
		storedDb.deleteTrips(tsArray);
	}
	
	public static void deleteAllStoredTrips(Context ctxt) {
		StoredTripHelper storedDb = new StoredTripHelper(ctxt);
		storedDb.clear();
	}
	
	public static void saveData(Properties data, File privateFileDir) {
		try {
			String currTimestamp = String.valueOf(System.currentTimeMillis());
			File currFile = new File(privateFileDir, currTimestamp);
			FileOutputStream outStream = new FileOutputStream(currFile);
			data.store(new FileOutputStream(currFile), "Data for "+currTimestamp);
			outStream.close();
		} catch (IOException e) {
			// TODO: Revisit error handling
			System.err.println("Caught IO Exception "+e+" while writing sensor values, dropping them");
		}
	}
	
    /**
     * Map detected activity types to strings
     *@param activityType The detected activity type
     *@return A user-readable name for the type
     */
    public static String activityType2Name(int activityType) {
        switch(activityType) {
            case DetectedActivity.IN_VEHICLE:
                return "transport";
            case DetectedActivity.ON_BICYCLE:
                return "cycling";
            case DetectedActivity.ON_FOOT:
                return "walking";
            case DetectedActivity.STILL:
                return "still";
            case DetectedActivity.UNKNOWN:
                return "unknown";
            case DetectedActivity.TILTING:
                return "tilting";
        }
        return "unknown";
    }

    /*
        The elapsed time is in nanoseconds, so we need to divide by 10^6 in order to get a millisecond
        value compatible with UTC. Here's an example of how this would work for the location:
        Location[fused 37.391018,-122.086251 acc=39 et=+4d15h48m36s938ms]

        The elapsed time is: 402516938865699
        402,516,938,865,699 ns / 10^6 = 402,516,938 ms
                                      = 402,516 secs
                      (/60)           = 6708 mins
                      (/60)           = 111 hours
                      (/24)           = 4 days

        which is consistent with the value in the location.
     */
    public static long elapsedToUTC(long baseUTC, long baseElapsed, long currElapsed) {
        return baseUTC + (currElapsed/Constants.NANO2MS - baseElapsed/ Constants.NANO2MS);
    }
    
	public static Properties readData(File dataFile) throws IOException {
		Properties props = new Properties();
		FileInputStream inStream = new FileInputStream(dataFile);
		props.load(inStream);
		return props;
	}


}
