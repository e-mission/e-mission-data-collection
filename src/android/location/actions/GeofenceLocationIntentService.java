package edu.berkeley.eecs.emission.cordova.tracker.location.actions;

import android.app.IntentService;
import android.content.Intent;
import android.location.Location;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationResult;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;

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
        return super.onStartCommand(i, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.d(this, TAG, "onDestroy called");
        super.onDestroy();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
		/*
		 * The intent is called when we get a location update.
		 */
        Log.d(this, TAG, "FINALLY! Got location update, intent is "+intent);
        Log.d(this, TAG, "Extras bundle = "+ Objects.requireNonNull(intent.getExtras()).toString());

        Object[] extraKeys = intent.getExtras().keySet().toArray();
        Log.d(this, TAG, "Extras keys are "+ Arrays.toString(extraKeys));
        Object[] extraValues = new Object[extraKeys.length];
        for (int i = 0; i < extraKeys.length; i++) {
            extraValues[i] = intent.getExtras().get((String)extraKeys[i]);
        }
        Log.d(this, TAG, "Extras values are "+ Arrays.toString(extraValues));

        Location loc = null;

        if (LocationResult.hasResult(intent)) {
          List<Location> locList = LocationResult.extractResult(intent).getLocations();
          // flip to be newest to oldest
          Collections.reverse(locList);

          for (Location currLoc : locList) {
            if (GeofenceActions.isValidLocation(this, currLoc)) {
              Log.d(this, TAG, "Found most recent valid location = "+loc);
              loc = currLoc;
              break;
            }
          }
        }

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

        // TODO: Remove this if statement since we have already checked for validity earlier
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
