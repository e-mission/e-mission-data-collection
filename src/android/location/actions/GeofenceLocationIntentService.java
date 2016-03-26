package edu.berkeley.eecs.emission.cordova.tracker.location.actions;

import android.app.IntentService;
import android.content.Intent;
import android.location.Location;
import android.support.v4.content.LocalBroadcastManager;

import com.google.android.gms.location.FusedLocationProviderApi;

import java.util.Arrays;

import edu.berkeley.eecs.emission.cordova.tracker.ConfigManager;
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
    public void onStart(Intent i, int startId) {
        Log.d(this, TAG, "onStart called with "+i+" startId "+startId);
        super.onStart(i, startId);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
		/*
		 * The intent is called when we get a location update.
		 */
        Log.d(this, TAG, "FINALLY! Got location update, intent is "+intent);
        Log.d(this, TAG, "Extras keys are "+ Arrays.toString(intent.getExtras().keySet().toArray()));
        int ACCURACY_THRESHOLD = ConfigManager.getConfig(this).getAccuracyThreshold();

        UserCache uc = UserCacheFactory.getUserCache(this);

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
		of extra. I think we can safely ignore these intents and just return when loc is null.

		see http://stackoverflow.com/questions/29960981/why-does-android-fusedlocationproviderapi-requestlocationupdates-send-updates-wi
		 */
        if (loc == null) return;

        if (GeofenceActions.isValidLocation(this, loc)) {
            // notify something
            Intent answerIntent = new Intent(INTENT_NAME);
            answerIntent.putExtra(INTENT_RESULT_KEY, loc);
            LocalBroadcastManager.getInstance(this).sendBroadcast(answerIntent);
        }
    }
}