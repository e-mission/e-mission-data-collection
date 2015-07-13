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

import edu.berkeley.eecs.cfc_tracker.BootReceiver;
import edu.berkeley.eecs.cfc_tracker.MainActivity;
import edu.berkeley.eecs.cfc_tracker.R;
import edu.berkeley.eecs.cfc_tracker.location.TripDiaryStateMachineReceiver;
import edu.berkeley.eecs.cfc_tracker.location.actions.ActivityRecognitionActions;
import edu.berkeley.eecs.cfc_tracker.location.actions.GeofenceActions;
import edu.berkeley.eecs.cfc_tracker.location.actions.LocationTrackingActions;
import edu.berkeley.eecs.cfc_tracker.smap.AddDataAdapter;
import edu.berkeley.eecs.cfc_tracker.storage.DataUtils;
import edu.berkeley.eecs.cfc_tracker.usercache.BuiltinUserCache;
import edu.berkeley.eecs.cfc_tracker.usercache.UserCache;
import edu.berkeley.eecs.cfc_tracker.usercache.UserCacheFactory;

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
        UserCache uc = UserCacheFactory.getUserCache(appCtxt);
        BuiltinUserCache biuc = new BuiltinUserCache(appCtxt);

        long startTime = System.currentTimeMillis();
		long WAIT_TIME = 120 * 1000; // 120 secs = 2 mins
		
		final String startString = appCtxt.getString(R.string.transition_exited_geofence);
		final String stopString = appCtxt.getString(R.string.transition_stopped_moving);

        biuc.clear();
        assertEquals(uc.getLastMessages(R.string.key_usercache_location, 10, Location.class).length, 0);

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

        System.out.println("ASSERT: Got "+uc.getLastMessages(R.string.key_usercache_location, 2,
                Location.class).length+" points");
		assertEquals(uc.getLastMessages(R.string.key_usercache_location, 2,
                Location.class).length, 1);

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

		Location[] allPoints = uc.getLastMessages(R.string.key_usercache_location, 20,
                Location.class);
		System.out.println("last points " + Arrays.toString(allPoints));
		assertEquals(allPoints.length, 15);

        JSONArray entriesToPush = biuc.sync_phone_to_server();
        System.out.println("entriesToPush = "+entriesToPush);
        // assertEquals(entriesToPush.length(), 20);

        UserCache.TimeQuery tq = AddDataAdapter.getTimeQuery(entriesToPush);
		uc.clearMessages(tq);
        System.out.println("timequery = "+tq);
        assertEquals(uc.getLastMessages(R.string.key_usercache_location, 20,
                Location.class).length, 0);

        /*
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
		*/

		assertEquals(allPoints.length, 15);
		assertEquals(allPoints[14].getLatitude(), 37.380866);
		assertEquals(allPoints[14].getLongitude(), -122.086945);
		assertEquals(allPoints[0].getLatitude(), 37.385461);
		assertEquals(allPoints[0].getLongitude(), -122.078265);

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
        System.out.println("Test COMPLETE!!");
	}

    public void verifyTDSMState(int expectedStateId) {
        // Before we get the broadcast, the state should be "START"
        String currState = TripDiaryStateMachineReceiver.getState(appCtxt);
        String expectedState = null;
        if (expectedStateId != -1) {
            expectedState = appCtxt.getString(expectedStateId);
        }
        System.out.println("In testReboot, current state = " + currState);
        assertTrue("Found state "+currState+" expecting something else",
                currState.equals(expectedState));
    }

    public void testReboot() throws Exception {
        long startTime = System.currentTimeMillis();
        long WAIT_TIME = 120 * 1000; // 120 secs = 2 mins

        final String initString = appCtxt.getString(R.string.transition_exited_geofence);

        BuiltinUserCache biuc = new BuiltinUserCache(appCtxt);
        biuc.clear();
        assertEquals(biuc.getLastMessages(R.string.key_usercache_location, 10, Location.class).length, 0);

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

        // First register the receiver so that we will get the transition correctly
        // Let's force the state to be ongoing trip at the beginning to that we can ensure
        // that it actually changes as part of the test
        final BroadcastChecker exitChecker = new BroadcastChecker(initString);
        appCtxt.registerReceiver(exitChecker, new IntentFilter(initString));

        TripDiaryStateMachineReceiver tdsm = new TripDiaryStateMachineReceiver(appCtxt);
        verifyTDSMState(R.string.state_start);

        BootReceiver startupReceiver = new BootReceiver();
        Intent startupIntent = new Intent(Intent.ACTION_BOOT_COMPLETED);
        startupReceiver.onReceive(appCtxt, startupIntent);

        startTime = System.currentTimeMillis();

        synchronized(exitChecker) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            while ((exitChecker.hasReceivedBroadcast() == false) && (elapsedTime < WAIT_TIME)) {
                exitChecker.wait(WAIT_TIME - elapsedTime);
                elapsedTime = System.currentTimeMillis() - startTime;
            }
        }
        // Received the "initialize" transition
        assertTrue(exitChecker.hasReceivedBroadcast());

        verifyTDSMState(R.string.state_waiting_for_trip_start);
    }

    public void testGeofenceWithActivityChanges() throws Exception {
        UserCache uc = UserCacheFactory.getUserCache(appCtxt);
        BuiltinUserCache biuc = new BuiltinUserCache(appCtxt);

        long startTime = System.currentTimeMillis();
        long WAIT_TIME = 120 * 1000; // 120 secs = 2 mins

        final String startString = appCtxt.getString(R.string.transition_exited_geofence);
        final String stopString = appCtxt.getString(R.string.transition_stopped_moving);

        biuc.clear();
        assertEquals(biuc.getLastMessages(R.string.key_usercache_location, 10, Location.class).length, 0);

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
        System.out.println("ASSERT: Got "+
                uc.getLastMessages(R.string.key_usercache_location, 2, Location.class).length+" points");
        assertEquals(uc.getLastMessages(R.string.key_usercache_location, 2, Location.class).length, 1);

        // There is no standard way to mock activity detection for android.
        // So, we manually populate activity detection for now

        uc.putMessage(R.string.key_usercache_activity, new DetectedActivity(DetectedActivity.ON_FOOT, 95));

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
        uc.putMessage(R.string.key_usercache_activity, new DetectedActivity(DetectedActivity.STILL, 95));

        assertTrue(enterChecker.hasReceivedBroadcast());
        Thread.sleep(2 * 1000);

        Location[] allPoints = uc.getLastMessages(R.string.key_usercache_location, 17, Location.class);
        System.out.println("last points " + Arrays.toString(allPoints));
        assertTrue("allPoints.length = "+allPoints.length+" expecting 13 to 17",
                12 < allPoints.length && allPoints.length < 18);

        JSONArray entriesToPush = biuc.sync_phone_to_server();
        System.out.println("trips to push " + entriesToPush);
        System.out.println("trips to push length " + entriesToPush.length());
        // We are actually getting activity entries from the system now, since this is on a physical phone
        // We can't control how many of them we get, so we check for a range instead of an exact value
        assertTrue(20 < entriesToPush.length() && entriesToPush.length() < 30);

        Location startPlaceLocation = allPoints[14];
        assertEquals(startPlaceLocation.getLatitude(), 37.380866);
        assertEquals(startPlaceLocation.getLongitude(), -122.086945);

        DetectedActivity[] allActivities = uc.getLastMessages(R.string.key_usercache_activity, 10,
                DetectedActivity.class);
        assertTrue("allActivities.length = " + allActivities.length + " expecting 2 to 10",
                2 <= allActivities.length && allActivities.length <= 10);

        for (int i = 0; i < entriesToPush.length(); i++) {
            if (entriesToPush.getJSONObject(i).getJSONObject("metadata").getString("key").equals(
                    appCtxt.getString(R.string.key_usercache_location))) {
                // This is the first location
                assertEquals(entriesToPush.getJSONObject(i).getJSONObject("data").getDouble("mLatitude"), 37.380866);
                assertEquals(entriesToPush.getJSONObject(i).getJSONObject("data").getDouble("mLongitude"), -122.086945);
                break;
            }
        }
        for (int i = entriesToPush.length() - 1; i > 0; i--) {
            if (entriesToPush.getJSONObject(i).getJSONObject("metadata").getString("key").equals(
                    appCtxt.getString(R.string.key_usercache_location))) {
                // This is the last location
                assertEquals(entriesToPush.getJSONObject(i).getJSONObject("data").getDouble("mLatitude"), 37.385461);
                assertEquals(entriesToPush.getJSONObject(i).getJSONObject("data").getDouble("mLongitude"), -122.078265);
                break;
            }
        }
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
