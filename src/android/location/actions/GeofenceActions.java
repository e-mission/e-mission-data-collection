package edu.berkeley.eecs.emission.cordova.tracker.location.actions;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.support.v4.content.LocalBroadcastManager;

import edu.berkeley.eecs.emission.cordova.tracker.ConfigManager;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.NotificationHelper;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.LocationTrackingConfig;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

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

    private Context mCtxt;
    private GoogleApiClient mGoogleApiClient;
    // Used only when the last location from the manager is null, or invalid and so we have
    // to read a new one. This is a private variable for synchronization
    private Location newLastLocation;

    public GeofenceActions(Context ctxt, GoogleApiClient googleApiClient) {
        this.mCtxt = ctxt;
        this.mGoogleApiClient = googleApiClient;
    }

    /*
     * Actually creates the geofence. We want to create the geofence at the last known location, so
     * we retrieve it from the location services. If this is not null, we call createGeofenceRequest to
     * create the geofence request and register it.
     *
     * see @GeofenceActions.createGeofenceRequest
     */
    public PendingResult<Status> create() {
        Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);
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
    }

    private PendingResult<Status> createGeofenceAtLocation(Location currLoc) {
        Log.d(mCtxt, TAG, "creating geofence at location " + currLoc);
            // This is also an asynchronous call. We can either wait for the result,
            // or we can provide a callback. Let's provide a callback to keep the async
            // logic in place
            return LocationServices.GeofencingApi.addGeofences(mGoogleApiClient,
                createGeofenceRequest(currLoc.getLatitude(), currLoc.getLongitude()),
                        getGeofenceExitPendingIntent(mCtxt));
    }

    private Location readAndReturnCurrentLocation() {
        Intent geofenceLocIntent = new Intent(mCtxt, GeofenceLocationIntentService.class);
        final PendingIntent geofenceLocationIntent = PendingIntent.getService(mCtxt, 0,
                geofenceLocIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        LocalBroadcastManager.getInstance(mCtxt).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                synchronized(GeofenceActions.this) {
                    GeofenceActions.this.newLastLocation = intent.getParcelableExtra(GeofenceLocationIntentService.INTENT_RESULT_KEY);
                    GeofenceActions.this.notify();
                }
            }
        }, new IntentFilter(GeofenceLocationIntentService.INTENT_NAME));

        PendingResult<Status> locationReadingResult =
                LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,
                getHighAccuracyHighFrequencyRequest(), geofenceLocationIntent);
        Status result = locationReadingResult.await(1L, TimeUnit.MINUTES);
        if (result.getStatusCode() == CommonStatusCodes.SUCCESS) {
            Log.d(mCtxt, TAG, "Successfully started tracking location, about to start waiting for location update");
        } else {
            Log.w(mCtxt, TAG, "Error "+result+"while getting location, returning null ");
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
                LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, geofenceLocationIntent);
                Log.d(mCtxt, TAG, "After waiting for location reading result, location is " + this.newLastLocation);
                return this.newLastLocation;
            } catch (InterruptedException e) {
                LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, geofenceLocationIntent);
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
            return false; // too inaccurate. Note that a high accuracy number means a larger radius
            // of validity which effectively means a low accuracy
        }
        int fiveMins = 5 * 60 * 1000;
        if ((testLoc.getTime() - System.currentTimeMillis()) > fiveMins * 60) {
            return false; // too old
        }
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
                    "Unable to detect current location even after forcing, will retry at next sync");
        }

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

    public PendingResult<Status> remove() {
        Log.d(mCtxt, TAG, "Removing geofence with ID = "+GEOFENCE_REQUEST_ID);
        return LocationServices.GeofencingApi.removeGeofences(mGoogleApiClient,
                Arrays.asList(GEOFENCE_REQUEST_ID));
    }

    public static PendingIntent getGeofenceExitPendingIntent(Context ctxt) {
        Intent innerIntent = new Intent(ctxt, GeofenceExitIntentService.class);
		/*
		 * Setting FLAG_UPDATE_CURRENT so that sending the PendingIntent again updates the original.
		 * We only want to have one geofence active at one point of time.
		 */
        return PendingIntent.getService(ctxt, 0, innerIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

}
