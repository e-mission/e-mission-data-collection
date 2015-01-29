package edu.berkeley.eecs.cfc_tracker.test.location;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.test.ActivityInstrumentationTestCase2;

import com.google.android.gms.common.api.Batch;
import com.google.android.gms.common.api.BatchResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;

import edu.berkeley.eecs.cfc_tracker.MainActivity;
import edu.berkeley.eecs.cfc_tracker.R;
import edu.berkeley.eecs.cfc_tracker.location.actions.ActivityRecognitionActions;
import edu.berkeley.eecs.cfc_tracker.location.actions.GeofenceActions;
import edu.berkeley.eecs.cfc_tracker.location.actions.LocationTrackingActions;
import edu.berkeley.eecs.cfc_tracker.storage.DataUtils;

public class LocationTests extends ActivityInstrumentationTestCase2<MainActivity> implements GoogleApiClient.ConnectionCallbacks {
    // Intent to send to SendMockLocationService. Contains the type of test to run
    private Intent mRequestIntent;

	private Context appCtxt = null;
    // Let's use a special renaming context so that we don't mess up the main install
	private Context testCtxt = null;

    SendMockLocationService svc;

    /*
     * I tried reusing the GoogleApiClient in the SendMockLocationService, but that makes the
     * interactions with the actual mock location detection too complicated. Let's just make a new
     * right here
     */
    GoogleApiClient mGoogleApiClient;

    private static final String CALLBACK_CLEAR_DONE = "LocationTests.callback_clear_done";
    private static final String CALLBACK_STATUS_KEY = "CALLBACK_STATUS_EXTRA_KEY";


    public LocationTests() {
		super(MainActivity.class);
		System.out.println("location tests constructor");
	}

	protected void setUp() throws Exception {
		super.setUp();
		
		appCtxt = getInstrumentation().getTargetContext();
		System.out.println("appContext = "+appCtxt);
		
		testCtxt = getInstrumentation().getContext();
		System.out.println("Base application info = "+appCtxt.getApplicationInfo().packageName);
		System.out.println("Test application info = "+testCtxt.getApplicationInfo().packageName);		
		System.out.println("textCtxt = "+testCtxt);
		
		// Let's start off with a clean slate
		DataUtils.clearOngoingDb(appCtxt);
		DataUtils.deleteAllStoredTrips(appCtxt);

        final BroadcastChecker removeChecker = new BroadcastChecker(CALLBACK_CLEAR_DONE);
        LocalBroadcastManager.getInstance(appCtxt)
                .registerReceiver(removeChecker, new IntentFilter(CALLBACK_CLEAR_DONE));
        long startTime = System.currentTimeMillis();
        long WAIT_TIME = 120 * 1000;

        mGoogleApiClient = new GoogleApiClient.Builder(appCtxt)
                .addConnectionCallbacks(this)
                .addApi(LocationServices.API)
                .addApi(ActivityRecognition.API)
                .build();
        mGoogleApiClient.connect();

        svc = new SendMockLocationService();
        svc.onCreate(appCtxt);

        synchronized(removeChecker) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            while ((removeChecker.hasReceivedBroadcast() == false) && (elapsedTime < WAIT_TIME)) {
                removeChecker.wait(WAIT_TIME - elapsedTime);
            }
        }

        Status removeStatus = removeChecker.getReceivedIntent().getParcelableExtra(CALLBACK_STATUS_KEY);
        assertTrue(removeStatus.isSuccess());
        mGoogleApiClient.disconnect();
    }

	protected void tearDown() throws Exception {
        svc.stopSelf();
		super.tearDown();
	}
	
	private void startService(String startType) throws Exception {
		mRequestIntent = new Intent(testCtxt, SendMockLocationService.class);
		// Notify SendMockLocationService to loop once through the mock locations
        mRequestIntent.setAction(startType);
        mRequestIntent.putExtra(LocationUtils.EXTRA_SEND_INTERVAL, 20); // wait for 20 secs before notifying location 

        System.out.println("testCtxt = "+testCtxt);
		System.out.println("mRequestIntent = "+mRequestIntent+", starting service");
		// testCtxt.startService(mRequestIntent);
        // Start SendMockLocationService
		svc.onStartCommand(appCtxt, mRequestIntent, 0, 0);
    }

	public void testGeofence() throws Exception {
		long startTime = System.currentTimeMillis();
		long WAIT_TIME = 120 * 1000; // 120 secs = 2 mins
		
		final String startString = appCtxt.getString(R.string.transition_exited_geofence);
		final String stopString = appCtxt.getString(R.string.transition_stopped_moving);

        assertEquals(DataUtils.getLastPoints(appCtxt, 2).length, 0);

		// TODO: Move the way points out of LocationUtils (generic code) into
		// something that the test controls, so that we can reuse the same mechanism
		// for multiple location based tests

        // We need to have the locations be continuous instead of once because
        // we want to check that we exit the geofence the second time as well.
        // Two back to back "once" invocations don't seem to work because the points
        // have the same elapsed time. Instead of trying to fiddle with the elapsed
        // time calculation, we just use continuous, which works.
        startService(LocationUtils.ACTION_START_CONTINUOUS);

        final BroadcastChecker firstPointChecker = new MockLocationBroadcastChecker(
                LocationUtils.ACTION_SERVICE_MESSAGE,
                LocationUtils.FIRST_POINT_SET, 0);
        LocalBroadcastManager.getInstance(appCtxt).registerReceiver(firstPointChecker,
                new IntentFilter(LocationUtils.ACTION_SERVICE_MESSAGE));

        synchronized(firstPointChecker) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            while ((firstPointChecker.hasReceivedBroadcast() == false) && (elapsedTime < WAIT_TIME)) {
                firstPointChecker.wait(WAIT_TIME - elapsedTime);
            }
        }
        assertTrue(firstPointChecker.hasReceivedBroadcast());
        LocalBroadcastManager.getInstance(appCtxt).unregisterReceiver(firstPointChecker);


        // Now that we know that the first point has been sent, there will be a last location,
        // for the geofence, and we can initialize the FSM.
        appCtxt.sendBroadcast(new Intent(appCtxt.getString(R.string.transition_initialize)));

        startTime = System.currentTimeMillis();
        final BroadcastChecker exitChecker = new BroadcastChecker(startString);
		appCtxt.registerReceiver(exitChecker, new IntentFilter(startString));
		
		synchronized(exitChecker) {
			long elapsedTime = System.currentTimeMillis() - startTime;
			while ((exitChecker.hasReceivedBroadcast() == false) && (elapsedTime < WAIT_TIME)) { 
				exitChecker.wait(WAIT_TIME - elapsedTime);
			}
		}
		assertTrue(exitChecker.hasReceivedBroadcast());
        appCtxt.unregisterReceiver(exitChecker);

        // The location at which we exited the geofence
        System.out.println("ASSERT: Got "+DataUtils.getLastPoints(appCtxt, 2).length+" points");
		assertEquals(DataUtils.getLastPoints(appCtxt, 2).length, 1);

        startTime = System.currentTimeMillis();
		final BroadcastChecker enterChecker = new BroadcastChecker(stopString);
		appCtxt.registerReceiver(enterChecker, new IntentFilter(stopString));
		
		synchronized(enterChecker) {
			long elapsedTime = System.currentTimeMillis() - startTime;
			while ((enterChecker.hasReceivedBroadcast() == false) && (elapsedTime < WAIT_TIME)) { 
				enterChecker.wait(WAIT_TIME - elapsedTime);
			}
		}

		assertTrue(enterChecker.hasReceivedBroadcast());
        appCtxt.unregisterReceiver(enterChecker);

		Thread.sleep(2 * 1000);
		
		Location[] last2Points = DataUtils.getLastPoints(appCtxt, 2);
		System.out.println("last points "+Arrays.toString(last2Points));
		assertEquals(DataUtils.getLastPoints(appCtxt, 2).length, 0);
		
		JSONArray tripsToPush = DataUtils.getTripsToPush(appCtxt);
		System.out.println("trips to push "+tripsToPush);
		assertEquals(tripsToPush.length(), 2);
		
		assertEquals(tripsToPush.getJSONObject(0).getString("type"), "place");
		
		JSONObject startPlaceLocation = tripsToPush.getJSONObject(0).getJSONObject("place").getJSONObject("location"); 
		assertEquals(startPlaceLocation.getDouble("lat"), 37.380866);
		assertEquals(startPlaceLocation.getDouble("lon"), -122.086945);

		assertEquals(tripsToPush.getJSONObject(1).getString("type"), "move");
		
		JSONObject moveTrip = tripsToPush.getJSONObject(1);
		assertEquals(moveTrip.getJSONArray("activities").length(), 1);
		JSONObject moveActivity = moveTrip.getJSONArray("activities").getJSONObject(0);
		JSONArray trackPointArray = moveActivity.getJSONArray("trackPoints");
		assertEquals(trackPointArray.length(), 14);
		assertEquals(trackPointArray.getJSONObject(0).getDouble("lat"), 37.380866);
		assertEquals(trackPointArray.getJSONObject(0).getDouble("lon"), -122.086945);
		assertEquals(trackPointArray.getJSONObject(8).getDouble("lat"), 37.385461);
		assertEquals(trackPointArray.getJSONObject(8).getDouble("lon"), -122.078265);

        startTime = System.currentTimeMillis();
        final BroadcastChecker exitChecker2 = new BroadcastChecker(startString);
        appCtxt.registerReceiver(exitChecker2, new IntentFilter(startString));

        synchronized(exitChecker2) {
			long elapsedTime = System.currentTimeMillis() - startTime;
			while ((exitChecker2.hasReceivedBroadcast() == false) && (elapsedTime < WAIT_TIME)) {
				exitChecker2.wait(WAIT_TIME - elapsedTime);
			}
		}
        assertTrue(exitChecker.hasReceivedBroadcast());
        appCtxt.unregisterReceiver(exitChecker2);
	}

    public void testGeofenceWithActivityChanges() throws Exception {
        long startTime = System.currentTimeMillis();
        long WAIT_TIME = 120 * 1000; // 120 secs = 2 mins

        final String startString = appCtxt.getString(R.string.transition_exited_geofence);
        final String stopString = appCtxt.getString(R.string.transition_stopped_moving);

        assertEquals(DataUtils.getLastPoints(appCtxt, 2).length, 0);

        // TODO: Move the way points out of LocationUtils (generic code) into
        // something that the test controls, so that we can reuse the same mechanism
        // for multiple location based tests

        startService(LocationUtils.ACTION_START_ONCE);

        final BroadcastChecker firstPointChecker = new BroadcastChecker(LocationUtils.ACTION_SERVICE_MESSAGE);
        LocalBroadcastManager.getInstance(appCtxt).registerReceiver(firstPointChecker,
                new IntentFilter(LocationUtils.ACTION_SERVICE_MESSAGE));

        synchronized(firstPointChecker) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            while ((firstPointChecker.hasReceivedBroadcast() == false) && (elapsedTime < WAIT_TIME)) {
                firstPointChecker.wait(WAIT_TIME - elapsedTime);
            }
        }

        // Now that we know that the first point has been sent, there will be a last location,
        // for the geofence, and we can initialize the FSM.
        appCtxt.sendBroadcast(new Intent(appCtxt.getString(R.string.transition_initialize)));

        final BroadcastChecker exitChecker = new BroadcastChecker(startString);

        appCtxt.registerReceiver(exitChecker, new IntentFilter(startString));

        synchronized(exitChecker) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            while ((exitChecker.hasReceivedBroadcast() == false) && (elapsedTime < WAIT_TIME)) {
                exitChecker.wait(WAIT_TIME - elapsedTime);
            }
        }
        assertTrue(exitChecker.hasReceivedBroadcast());
        // The location at which we exited the geofence
        System.out.println("ASSERT: Got "+DataUtils.getLastPoints(appCtxt, 2).length+" points");
        assertEquals(DataUtils.getLastPoints(appCtxt, 2).length, 1);

        // There is no standard way to mock activity detection for android.
        // So, we manually populate activity detection for now

		DataUtils.addModeChange(appCtxt, System.currentTimeMillis(),
				new DetectedActivity(DetectedActivity.ON_FOOT, 95));

        final BroadcastChecker enterChecker = new BroadcastChecker(stopString);
        appCtxt.registerReceiver(enterChecker, new IntentFilter(stopString));

        synchronized(enterChecker) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            while ((enterChecker.hasReceivedBroadcast() == false) && (elapsedTime < WAIT_TIME)) {
                enterChecker.wait(WAIT_TIME - elapsedTime);
            }
        }
        // There is no standard way to mock activity detection for android.
        // So, we manually populate activity detection for now
		DataUtils.addModeChange(appCtxt, System.currentTimeMillis(),
				new DetectedActivity(DetectedActivity.STILL, 95));

        assertTrue(enterChecker.hasReceivedBroadcast());
        Thread.sleep(2 * 1000);

        Location[] last2Points = DataUtils.getLastPoints(appCtxt, 2);
        System.out.println("last points "+Arrays.toString(last2Points));
        assertEquals(DataUtils.getLastPoints(appCtxt, 2).length, 0);

        JSONArray tripsToPush = DataUtils.getTripsToPush(appCtxt);
        System.out.println("trips to push "+tripsToPush);
        assertEquals(tripsToPush.length(), 2);

        assertEquals(tripsToPush.getJSONObject(0).getString("type"), "place");

        JSONObject startPlaceLocation = tripsToPush.getJSONObject(0).getJSONObject("place").getJSONObject("location");
        assertEquals(startPlaceLocation.getDouble("lat"), 37.380866);
        assertEquals(startPlaceLocation.getDouble("lon"), -122.086945);

        assertEquals(tripsToPush.getJSONObject(1).getString("type"), "move");

        JSONObject moveTrip = tripsToPush.getJSONObject(1);
        assertEquals(moveTrip.getJSONArray("activities").length(), 2);

        JSONObject onFootMoveActivity = moveTrip.getJSONArray("activities").getJSONObject(0);
        JSONArray onFootTrackPointArray = onFootMoveActivity.getJSONArray("trackPoints");
        assertEquals(onFootTrackPointArray.length(), 16);
        assertEquals(onFootTrackPointArray.getJSONObject(0).getDouble("lat"), 37.380866);
        assertEquals(onFootTrackPointArray.getJSONObject(0).getDouble("lon"), -122.086945);
        assertEquals(onFootTrackPointArray.getJSONObject(8).getDouble("lat"), 37.385461);
        assertEquals(onFootTrackPointArray.getJSONObject(8).getDouble("lon"), -122.078265);

        JSONObject stillMoveActivity = moveTrip.getJSONArray("activities").getJSONObject(1);
        JSONArray stillTrackPointArray = stillMoveActivity.getJSONArray("trackPoints");
        assertEquals(stillTrackPointArray.length(), 0);
    }

    @Override
    public void onConnected(Bundle bundle) {
        System.out.println("GoogleApiClient connected in test code");
        Batch.Builder resultBarrier = new Batch.Builder(mGoogleApiClient);
        resultBarrier.add(new LocationTrackingActions(appCtxt, mGoogleApiClient).stop());
        resultBarrier.add(new ActivityRecognitionActions(appCtxt, mGoogleApiClient).stop());
        resultBarrier.add(new GeofenceActions(appCtxt, mGoogleApiClient).remove());
        resultBarrier.build().setResultCallback(new ResultCallback<BatchResult>() {
            @Override
            public void onResult(BatchResult batchResult) {
                System.out.println("clear callback is now DONE");
                Intent broadcastIntent = new Intent(CALLBACK_CLEAR_DONE);
                broadcastIntent.putExtra(CALLBACK_STATUS_KEY, batchResult.getStatus());
                LocalBroadcastManager.getInstance(appCtxt).sendBroadcast(
                        broadcastIntent);
            }
        });
    }

    @Override
    public void onConnectionSuspended(int i) {

    }
}
