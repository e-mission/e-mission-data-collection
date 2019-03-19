package edu.berkeley.eecs.emission.cordova.tracker.location.actions;

import android.app.IntentService;
import android.content.Intent;
import android.location.Location;
import android.support.v4.content.LocalBroadcastManager;

import com.google.android.gms.location.FusedLocationProviderApi;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationResult;

import java.util.Arrays;

import edu.berkeley.eecs.emission.cordova.tracker.ConfigManager;
import edu.berkeley.eecs.emission.cordova.tracker.location.TripDiaryStateMachineForegroundService;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.LocationTrackingConfig;
import edu.berkeley.eecs.emission.cordova.tracker.sensors.PollSensorManager;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.usercache.UserCache;
import edu.berkeley.eecs.emission.cordova.usercache.UserCacheFactory;

/**
 * Specialized intent service used for determining the location at which to create a geofence.
 */

public class GeofenceLocationIntentService extends IntentService {
    public static String TAG = "GeofenceLocationIntentService";
    protected static String INTENT_NAME = "GEOFENCE_LOCATION_RESULT";
    protected static String INTENT_RESULT_KEY = "CURRENT_LOCATION";

    public GeofenceLocationIntentService() {
        super("GeofenceLocationIntentService");
    }

    @Override
    public void onCreate() {
        Log.d(this, TAG, "onCreate called");
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        Log.d(this, TAG, "onStart called with "+i+" startId "+startId);
        TripDiaryStateMachineForegroundService.handleStart(this, "Handling geofence event", i, flags, startId);
        return super.onStartCommand(i, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.d(this, TAG, "onDestroy called");
        TripDiaryStateMachineForegroundService.handleDestroy(this);
        super.onDestroy();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
		/*
		 * The intent is called when we get a location update.
		 */
        Log.d(this, TAG, "FINALLY! Got location update, intent is "+intent);
        Log.d(this, TAG, "Extras bundle = "+intent.getExtras().toString());

        Object[] extraKeys = intent.getExtras().keySet().toArray();
        Log.d(this, TAG, "Extras keys are "+ Arrays.toString(extraKeys));
        Object[] extraValues = new Object[extraKeys.length];
        for (int i = 0; i < extraKeys.length; i++) {
            extraValues[i] = intent.getExtras().get((String)extraKeys[i]);
        }
        Log.d(this, TAG, "Extras values are "+ Arrays.toString(extraValues));


        /*
         * For the sensors that are not managed by the android sensor manager, but instead, require
         * polling us to poll them, let us do so at the time that we get location updates. We will
         * always have location updates, since that is our goal, and piggybacking
         * in this fashion will avoid the overhead of building a scheduler, launching a new process
         * for the polling, and the power drain of waking up the CPU.
         */
        PollSensorManager.getAndSaveAllValues(this);

        Location loc = (Location)intent.getExtras().get(FusedLocationProviderApi.KEY_LOCATION_CHANGED);
        Log.d(this, TAG, "Read location "+loc+" from intent");

		/*
		It seems that newer version of Google Play will send along an intent that does not have the
		KEY_LOCATION_CHANGED extra, but rather an EXTRA_LOCATION_AVAILABILITY. The original code
		assumed KEY_LOCATION_CHANGED would always be there, and didn't check for this other type
		of extra. If this is sent with availability = false, let's broadcast null.

		see http://stackoverflow.com/questions/29960981/why-does-android-fusedlocationproviderapi-requestlocationupdates-send-updates-wi
		 */
        if (loc == null) {
            if (LocationAvailability.hasLocationAvailability(intent)) {
                LocationAvailability locationAvailability = LocationAvailability.extractLocationAvailability(intent);
                Log.d(this, TAG, "availability = "+locationAvailability.isLocationAvailable());
                if (!locationAvailability.isLocationAvailable()) {
                    Log.d(this, TAG, "location is not available, broadcast null result");
                    broadcastLoc(null);
                }
            }  else {
                // loc is null, but no location availability flag, let's continue ignoring the intent
                return;
            }
        }

        if (GeofenceActions.isValidLocation(this, loc)) {
            // notify something
            Log.d(this, TAG, "location is valid, broadcast it "+loc);
            broadcastLoc(loc);
        }
    }

    private void broadcastLoc(Location loc) {
        Intent answerIntent = new Intent(INTENT_NAME);
        answerIntent.putExtra(INTENT_RESULT_KEY, loc);
        Log.i(this, TAG, "broadcasting intent "+answerIntent);
        LocalBroadcastManager.getInstance(this).sendBroadcast(answerIntent);
    }
}
