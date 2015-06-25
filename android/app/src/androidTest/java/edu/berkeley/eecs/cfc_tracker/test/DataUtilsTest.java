package edu.berkeley.eecs.cfc_tracker.test;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.android.gms.location.DetectedActivity;

import edu.berkeley.eecs.cfc_tracker.storage.DataUtils;
import edu.berkeley.eecs.cfc_tracker.storage.StoredTripHelper;

import android.content.Context;
import android.location.Location;
import android.test.AndroidTestCase;
import android.test.RenamingDelegatingContext;
import android.text.format.DateFormat;
import android.util.Log;

import java.util.Date;

public class DataUtilsTest extends AndroidTestCase {
	private static final String TAG = "DataUtilsTest";
	private Context testCtxt = null;
	private long testStartTs;
    private static final String DATE_FORMAT = "yyyyMMddTHHmmssz";
	
	public DataUtilsTest() {
		super();
	}

	protected void setUp() throws Exception {
		/*
		 * Don't need to populate with test data here, can do that in the test cases.
		 */
		super.setUp();
		RenamingDelegatingContext context = 
				new RenamingDelegatingContext(getContext(), "test_");
		testCtxt = context;
		// Make sure that we start every test with a clean slate
		DataUtils.clearOngoingDb(testCtxt);
		new StoredTripHelper(testCtxt).clear();
		testStartTs = System.currentTimeMillis();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}
	
	private Location makeLoc(double lat, double lng, long ts) {
		Location retLoc = new Location("TEST");
		retLoc.setLatitude(lat);
		retLoc.setLongitude(lng);
		retLoc.setTime(testStartTs + ts);
        retLoc.setElapsedRealtimeNanos((testStartTs + ts) * 1000000);
		retLoc.setAccuracy((float) 0.1);
		return retLoc;
	}
	
	private Location[] getFourPoints() {
		Location[] fourPoints = new Location[4];
		fourPoints[0] = makeLoc(34, -122, 1);
		fourPoints[1] = makeLoc(34, -121, 2);
		fourPoints[2] = makeLoc(34, -120, 3);
		fourPoints[3] = makeLoc(33, -120, 4);
		return fourPoints;
	}
	
	public void testAddPoint() throws Exception {
		assertEquals(DataUtils.getLastPoints(testCtxt, 5).length, 0);
		DataUtils.addPoint(testCtxt, makeLoc(34, -122, 1));
		assertEquals(DataUtils.getLastPoints(testCtxt, 5).length, 1);
	}
	
	public void testMultipleAddPoints() throws Exception {
		assertEquals(DataUtils.getLastPoints(testCtxt, 5).length, 0);
		Location[] fourPoints = getFourPoints();
        for (int i = 0; i < 4; i++) {
            DataUtils.addPoint(testCtxt, fourPoints[i]);
        }
        assertEquals(DataUtils.getLastPoints(testCtxt, 5).length, 4);
	}
	
	// We have already tested getLastPoints as part of the testAddPoint() and testMultipleAddPoints() tests above
	public void testAddModeChange() throws Exception {
		DataUtils.ModeChange currMode = DataUtils.getCurrentMode(testCtxt);
		assertEquals(currMode.getLastActivity().getType(), DetectedActivity.UNKNOWN);
		assertEquals(currMode.getLastActivity().getConfidence(), 100);
		
		DataUtils.addModeChange(testCtxt, 1111111, new DetectedActivity(DetectedActivity.ON_FOOT, 75));
		
		currMode = DataUtils.getCurrentMode(testCtxt);
		assertEquals(currMode.getLastActivity().getType(), DetectedActivity.ON_FOOT);
		assertEquals(currMode.getLastActivity().getConfidence(), 75);
		assertEquals(currMode.getLastChangeTs(), 1111111);
	}
	
	public void testClearOngoingDb() {
		assertEquals(DataUtils.getLastPoints(testCtxt, 5).length, 0);
		DataUtils.addPoint(testCtxt, makeLoc(34, -122, 1));
		assertEquals(DataUtils.getLastPoints(testCtxt, 5).length, 1);
		DataUtils.clearOngoingDb(testCtxt);
		assertEquals(DataUtils.getLastPoints(testCtxt, 5).length, 0);		
	}
	
	public void testGetJSONPlace() throws JSONException {
		Location loc = makeLoc(37, -122, 1);
		JSONObject pls = DataUtils.getJSONPlace(testCtxt, loc);
		Log.d(TAG, pls.toString());
		assertTrue(pls.has("place"));
		assertEquals(pls.getString("type"), "place");
        assertEquals(pls.getString("startTime"), DateFormat.format(DATE_FORMAT, new Date(loc.getTime())));
		assertEquals(pls.getLong("startTimeTs"), loc.getTime());
		assertTrue(pls.getJSONObject("place").has("location"));
		assertEquals(pls.getJSONObject("place").getJSONObject("location").getInt("lat"), 37);			
		assertEquals(pls.getJSONObject("place").getJSONObject("location").getInt("lon"), -122);
	}
	
	public void testGetTrackPoint() throws JSONException {
		Location loc = makeLoc(37, -122, 1);
		JSONObject tp = DataUtils.getTrackPoint(testCtxt, loc, 0, 0);
		assertEquals(tp.getString("time"), DateFormat.format(DATE_FORMAT, new Date(loc.getTime())));
		assertEquals(tp.getDouble("lat"), 37.0);
		assertEquals(tp.getDouble("lon"), -122.0);
		assertEquals("Accuracy check", tp.getDouble("accuracy"), 0.1, 0.0001);
	}
	
	public void testConvertOngoingToStoredNoEntries() throws JSONException {
		// First try with no entries in the ongoing trip DB
		assertEquals(DataUtils.getLastPoints(testCtxt, 5).length, 0);
		DataUtils.convertOngoingToStored(testCtxt);
		assertEquals(DataUtils.getLastPoints(testCtxt, 5).length, 0);
		assertEquals(DataUtils.getTripsToPush(testCtxt).length(), 0);
	}
	
	/*
	 * Also tests getTripsToPush and deletePushedTrips
	 */
	public void testConvertOngoingToStored() throws JSONException {
		StoredTripHelper storedDb = new StoredTripHelper(testCtxt);
		
		// First try with no entries in the ongoing trip DB
		assertEquals(DataUtils.getLastPoints(testCtxt, 5).length, 0);

		Location[] fourPoints = getFourPoints();
        for (int i = 0; i < 4; i++) {
            DataUtils.addPoint(testCtxt, fourPoints[i]);
        }

        assertEquals(DataUtils.getLastPoints(testCtxt, 5).length, 4);
		
		// Add in mode changes so that we get activities
		// Do we need to deal with this case that there are no mode changes but GPS points?
		// should never happen in the real world.
		// TODO: Add it for robustness later
		DataUtils.addModeChange(testCtxt, fourPoints[0].getElapsedRealtimeNanos(),
				new DetectedActivity(DetectedActivity.ON_FOOT, 70));
		DataUtils.addModeChange(testCtxt, fourPoints[1].getElapsedRealtimeNanos(),
				new DetectedActivity(DetectedActivity.IN_VEHICLE, 70));
		DataUtils.addModeChange(testCtxt, fourPoints[2].getElapsedRealtimeNanos(),
				new DetectedActivity(DetectedActivity.ON_FOOT, 70));
		DataUtils.addModeChange(testCtxt, fourPoints[3].getElapsedRealtimeNanos(),
				new DetectedActivity(DetectedActivity.STILL, 70));

		DataUtils.convertOngoingToStored(testCtxt);
		
		assertEquals(storedDb.getAllStoredTrips().length, 3);
		JSONArray toPush = DataUtils.getTripsToPush(testCtxt);
		assertEquals(toPush.length(), 2);
		Log.d(TAG, "toPush[0] = "+toPush.getJSONObject(0).toString());
		Log.d(TAG, "toPush[1] = "+toPush.getJSONObject(1).toString());
		assertTrue(toPush.getJSONObject(0).has("place"));
		assertEquals(toPush.getJSONObject(0).getString("endTime"), DateFormat.format(DATE_FORMAT, new Date(fourPoints[0].getTime())));
		
		assertEquals(toPush.getJSONObject(1).getString("type"), "move");
		
		JSONArray sectionArray = toPush.getJSONObject(1).getJSONArray("activities");
		assertEquals(sectionArray.length(), 4);
		
		for (int i = 0; i < sectionArray.length(); i++) {
			assertTrue(sectionArray.getJSONObject(i).has("duration"));
			assertTrue(sectionArray.getJSONObject(i).has("distance"));
		}
		
		for (int i = 0; i < sectionArray.length(); i++) {
			JSONArray pointsArray = sectionArray.getJSONObject(i).getJSONArray("trackPoints");
			Log.d(TAG, "pointsArray("+i+") = "+pointsArray);
            if (i != (sectionArray.length() - 1)) {
                assertEquals(pointsArray.length(), 1);
            } else {
                /*
                 * Everything except the final segment has one point. The final segment is from the
                 * last mode change to the end of the trip, which has no points in this test because
                 * the last mode change is at the end of the trip.
                 */
                assertEquals(pointsArray.length(), 0);
            }
		}
		
		DataUtils.deletePushedTrips(testCtxt, toPush);
		assertEquals(storedDb.getAllStoredTrips().length, 1);
	}
	
	public void testEndTrip() throws JSONException {
		assertEquals(DataUtils.getLastPoints(testCtxt, 5).length, 0);
		
		Location[] fourPoints = getFourPoints();
        for (int i = 0; i < 4; i++) {
            DataUtils.addPoint(testCtxt, fourPoints[i]);
        }

        assertEquals(DataUtils.getLastPoints(testCtxt, 5).length, 4);

		DataUtils.endTrip(testCtxt);
		assertEquals(DataUtils.getLastPoints(testCtxt, 5).length, 0);	
	}

    public void testElapsedToUTC() throws JSONException {
        assertEquals(DataUtils.elapsedToUTC(1422129326L, 402516938865699L, 402516938865750L), 1422129326L);
        assertEquals(DataUtils.elapsedToUTC(1422129326L, 402516938865699L, 402516948865750L), 1422129336L);
    }
}
