package edu.berkeley.eecs.emission.cordova.tracker.location.actions;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import edu.berkeley.eecs.emission.cordova.tracker.ConfigManager;
import edu.berkeley.eecs.emission.cordova.tracker.location.TripDiaryStateMachineForegroundService;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.NotificationHelper;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.LocationTrackingConfig;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;

import edu.berkeley.eecs.emission.cordova.usercache.UserCache;
import edu.berkeley.eecs.emission.cordova.usercache.UserCacheFactory;


import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import edu.berkeley.eecs.emission.cordova.tracker.location.GeofenceExitIntentService;

/**
 * Created by shankari on 12/30/14.
 */
public class GeofenceActions {
    private static final String GEOFENCE_REQUEST_ID = "DYNAMIC_EXIT_GEOFENCE";
    private static final int GEOFENCE_IN_NUMBERS = 43633623; // GEOFENCE
    // TODO: need to check what the definition of a city block is
    // Apparently city block sizes vary dramatically depending on the city.
    // Per wikipedia, http://en.wikipedia.org/wiki/City_block,
    // this ranges from 79m in Portland to 120m in Sacramento.
    // Let's pick 100 as a nice round number. If we are using this for privacy
    // and not just battery life, it should really be dependent on the density
    // of the location.

    private static final String TAG = "CreateGeofenceAction";
    public static final String GEOFENCE_LOC_KEY = "CURR_GEOFENCE_LOCATION";

    private Context mCtxt;
    private UserCache uc;
    // Used only when the last location from the manager is null, or invalid and so we have
    // to read a new one. This is a private variable for synchronization
    private Location newLastLocation;

    public GeofenceActions(Context ctxt) {
        this.mCtxt = ctxt;
        this.uc = UserCacheFactory.getUserCache(ctxt);
    }

    /*
     * Actually creates the geofence. We want to create the geofence at the last known location, so
     * we retrieve it from the location services. If this is not null, we call createGeofenceRequest to
     * create the geofence request and register it.
     *
     * see @GeofenceActions.createGeofenceRequest
     */
    public Task<Void> create() {
        try {
        Location mLastLocation = Tasks.await(LocationServices.getFusedLocationProviderClient(mCtxt).getLastLocation(), 30, TimeUnit.SECONDS);
        Log.d(mCtxt, TAG, "Last location would have been " + mLastLocation +" if we hadn't reset it");
        if (isValidLocation(mCtxt, mLastLocation)) {
            Log.d(mCtxt, TAG, "Last location is " + mLastLocation + " using it");
            return createGeofenceAtLocation(mLastLocation);
        } else {
            Log.w(mCtxt, TAG, "mLastLocationTime = null, launching callback to read it and then" +
                    "create the geofence");
            Location newLoc = readAndReturnCurrentLocation();
            if (newLoc != null) {
                Log.d(mCtxt, TAG, "New last location is " + newLoc + " using it");
                return createGeofenceAtLocation(newLoc);
            } else {
                Log.d(mCtxt, TAG, "Was not able to read new location, skipping geofence creation");
                return null;
            }
        }
        } catch (SecurityException e) {
            Log.e(mCtxt, TAG, "Found security error "+e.getMessage()+" while creating geofence");
            return null;
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
          Log.e(mCtxt, TAG, "Reading last location failed with error "+e.getMessage()+" while creating geofence");
          return null;
        }
    }

    private Task<Void> createGeofenceAtLocation(Location currLoc)  throws SecurityException {
        Log.d(mCtxt, TAG, "creating geofence at location " + currLoc);
        try {
            JSONObject jo = new JSONObject();
            jo.put("type", "Point");
            JSONArray currCoordinates = new JSONArray();
            currCoordinates.put(0, currLoc.getLongitude());
            currCoordinates.put(1, currLoc.getLatitude());
            jo.put("coordinates", currCoordinates);
            uc.putLocalStorage(GEOFENCE_LOC_KEY, jo);
        } catch (JSONException e) {
            Log.e(mCtxt, TAG, "Error while storing current geofence location, skipping..."+e.getMessage());
        }
            // This is also an asynchronous call. We can either wait for the result,
            // or we can provide a callback. Let's provide a callback to keep the async
            // logic in place
            return LocationServices.getGeofencingClient(mCtxt).addGeofences(
                createGeofenceRequest(currLoc.getLatitude(), currLoc.getLongitude()),
                        getGeofenceExitPendingIntent(mCtxt));
    }

    private Location readAndReturnCurrentLocation() throws SecurityException {
        Intent geofenceLocIntent = new Intent(mCtxt, GeofenceLocationIntentService.class);
        final PendingIntent geofenceLocationIntent =
                TripDiaryStateMachineForegroundService.getProperPendingIntent(mCtxt, geofenceLocIntent);

        LocalBroadcastManager.getInstance(mCtxt).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(mCtxt, TAG, "recieved broadcast intent "+intent);
                synchronized(GeofenceActions.this) {
                    GeofenceActions.this.newLastLocation = intent.getParcelableExtra(GeofenceLocationIntentService.INTENT_RESULT_KEY);
                    GeofenceActions.this.notify();
                }
            }
        }, new IntentFilter(GeofenceLocationIntentService.INTENT_NAME));

        Task<Void> locationReadingTask =
                LocationServices.getFusedLocationProviderClient(mCtxt).requestLocationUpdates(
                getHighAccuracyHighFrequencyRequest(), geofenceLocationIntent);

        try {
            Tasks.await(locationReadingTask, 1L, TimeUnit.MINUTES);
            // no exception means this call was successful
            Log.d(mCtxt, TAG, "Successfully started tracking location, about to start waiting for location update");
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            Log.w(mCtxt, TAG, "Error "+e.getLocalizedMessage()+"while getting location, returning null ");
            return null;
        }
        synchronized (this) {
            try {
                Log.d(mCtxt, TAG, "About to start waiting for location");
                this.wait(10 * 60 * 1000); // 10 minutes * 60 secs * 1000 milliseconds
                // If we stop the location tracking in the broadcast listener, before the notify, we can run into races
                // in which the notify has happened before we start waiting, which means that we wait forever.
                // Putting the stop in here means that we will continue to notify until the message is received
                // which should be safe.
                LocationServices.getFusedLocationProviderClient(mCtxt).removeLocationUpdates(geofenceLocationIntent);
                Log.d(mCtxt, TAG, "After waiting for location reading result, location is " + this.newLastLocation);
                return this.newLastLocation;
            } catch (InterruptedException e) {
                LocationServices.getFusedLocationProviderClient(mCtxt).removeLocationUpdates(geofenceLocationIntent);
                Log.w(mCtxt, TAG, "Timed out waiting for location result, returning null ");
                return null;
            }
        }
    }

    // Consistent with iOS, we say that the location is invalid if it is null, its accuracy is too low,
    // or it is too old
    static boolean isValidLocation(Context mCtxt, Location testLoc) {
        if (testLoc == null) {
            return false; // Duh!
        }
        LocationTrackingConfig cfg = ConfigManager.getConfig(mCtxt);
        if (testLoc.getAccuracy() > cfg.getAccuracyThreshold()) {
            Log.i(mCtxt, TAG, "testLoc.getAccuracy "+testLoc.getAccuracy()+
                    " > " + cfg.getAccuracyThreshold() + " isValidLocation = false");
            return false; // too inaccurate. Note that a high accuracy number means a larger radius
            // of validity which effectively means a low accuracy
        }

        int fiveMins = 5 * 60 * 1000;
        // testLoc is before now, so now - testLoc will be positive, and we check it against 5 mins
        if ((System.currentTimeMillis() - testLoc.getTime()) > fiveMins) {
            Log.i(mCtxt, TAG, "testLoc.getTime() = "+ new Date(testLoc.getTime()) +
                    " testLoc.oldness "+(testLoc.getTime() - System.currentTimeMillis()) +
                    " > " + fiveMins * 60 + " isValidLocation = false");
            return false; // too old
        }
        Log.i(mCtxt, TAG, "isValidLocation = true. Yay!");
        return true;
    }

    private static LocationRequest getHighAccuracyHighFrequencyRequest() {
        LocationRequest defaultRequest = LocationRequest.create();
        return defaultRequest.setInterval(1)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

        public void notifyFailure() {
            Log.w(mCtxt, TAG,
                    "Unable to detect current location even after forcing, will retry at next sync");
            NotificationHelper.createNotification(mCtxt, GEOFENCE_IN_NUMBERS,
                    null, mCtxt.getString(R.string.unable_detect_current_location));
        }

    /*
     * At least on android 12, geofence IDs are not application specific.
     * So if we have multiple instance of the app, and manually turn geofences
     * off and on, we will end up with only one geofence ID at the end.
     *
     * https://github.com/e-mission/e-mission-docs/issues/774#issuecomment-1221468502
     * https://github.com/e-mission/e-mission-docs/issues/774#issuecomment-1221476351
     *
     * prepending with the PackageName to allow multiple apps to co-exist.
    private getAppSpecificGeofenceID() {
        // geofence IDs are limited to 100 characters, so let's truncate
        // the PackageName to 80 characters if needed
        String pkgName = mCtxt.getPackageName();
        if (pkgName.length() > 80) {
            pkgName = pkgName.substring(0, 80);
        }
        String retVal = pkgName+"_"+GEOFENCE_REQUEST_ID;
        Log.d("Returning app-specific geofence ID "+retVal);
        return retVal;
    }
     */

    /*
     * Returns the geofence request object to be used with the geofencing API.
     * Called from the previous create() call.
     */
    public GeofencingRequest createGeofenceRequest(double lat, double lng) {
        Log.d(mCtxt, TAG, "creating geofence at location "+lat+", "+lng);
        LocationTrackingConfig cfg = ConfigManager.getConfig(this.mCtxt);
        Geofence currGeofence =
                new Geofence.Builder().setRequestId(GEOFENCE_REQUEST_ID)
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .setCircularRegion(lat, lng, cfg.getGeofenceRadius())
                        .setNotificationResponsiveness(cfg.getResponsiveness()) // 5 secs
                        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
                        .build();
        return new GeofencingRequest.Builder()
                .addGeofence(currGeofence)
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_EXIT)
                .build();
    }

    public Task<Void> remove() {
        Log.d(mCtxt, TAG, "Removing geofence with ID = "+GEOFENCE_REQUEST_ID);
        /*
         * remove using pending intent instead of ID to ensure that we delete
         * only the entry for this app
         * https://github.com/e-mission/e-mission-docs/issues/774#issuecomment-1221468502
         * https://github.com/e-mission/e-mission-docs/issues/774#issuecomment-1221476351
         */
        return LocationServices.getGeofencingClient(mCtxt).removeGeofences(
                getGeofenceExitPendingIntent(mCtxt));
    }

    public static PendingIntent getGeofenceExitPendingIntent(Context ctxt) {
        Intent innerIntent = new Intent(ctxt, GeofenceExitIntentService.class);
		/*
		 * Setting FLAG_UPDATE_CURRENT so that sending the PendingIntent again updates the original.
		 * We only want to have one geofence active at one point of time.
		 */
		return TripDiaryStateMachineForegroundService.getProperPendingIntent(ctxt, innerIntent);
    }

}
