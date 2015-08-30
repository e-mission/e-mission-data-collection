package edu.berkeley.eecs.cfc_tracker.location.actions;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;

import edu.berkeley.eecs.cfc_tracker.NotificationHelper;
import edu.berkeley.eecs.cfc_tracker.log.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

import java.util.Arrays;

import edu.berkeley.eecs.cfc_tracker.Constants;
import edu.berkeley.eecs.cfc_tracker.location.GeofenceExitIntentService;

/**
 * Created by shankari on 12/30/14.
 */
public class GeofenceActions {
    private static final String GEOFENCE_REQUEST_ID = "DYNAMIC_EXIT_GEOFENCE";
    private static final int GEOFENCE_IN_NUMBERS = 43633623; // GEOFENCE
    private static final float DEFAULT_GEOFENCE_RADIUS = Constants.TRIP_EDGE_THRESHOLD; // meters.
    private static final int GEOFENCE_RESPONSIVENESS = 5 * Constants.MILLISECONDS;
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
        if (mLastLocation != null) {
            Log.d(mCtxt, TAG, "mLastLocation has elapsed time = "+mLastLocation.getElapsedRealtimeNanos());
            Log.d(mCtxt, TAG, "Last location is " + mLastLocation + " creating geofence");
            // This is also an asynchronous call. We can either wait for the result,
            // or we can provide a callback. Let's provide a callback to keep the async
            // logic in place
            return LocationServices.GeofencingApi.addGeofences(mGoogleApiClient,
                        createGeofenceRequest(mLastLocation.getLatitude(), mLastLocation.getLongitude()),
                        getGeofenceExitPendingIntent(mCtxt));
        } else {
            Log.w(mCtxt, TAG, "mLastLocationTime = null, launching callback to read it and then" +
                    "create the geofence");
            return null;
        }
    }

    ResultCallback<Status> locationCallback = new ResultCallback<Status>() {
        Context mCtxt = GeofenceActions.this.mCtxt;
        @Override
        public void onResult(Status status) {
            if (status.isSuccess()) {
                Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                        mGoogleApiClient);
                if (mLastLocation != null) {
                    LocationServices.GeofencingApi.addGeofences(mGoogleApiClient,
                            createGeofenceRequest(mLastLocation.getLatitude(), mLastLocation.getLongitude()),
                            getGeofenceExitPendingIntent(mCtxt))
                            .await();
                } else {
                    notifyFailure();
                }
            } else {
                notifyFailure();
            }
        }

        public void notifyFailure() {
            Log.w(mCtxt, TAG,
                    "Unable to detect current location even after forcing, will retry at next sync");
            NotificationHelper.createNotification(mCtxt, GEOFENCE_IN_NUMBERS,
                    "Unable to detect current location even after forcing, will retry at next sync");
        }
    };

    /*
     * Returns the geofence request object to be used with the geofencing API.
     * Called from the previous create() call.
     */
    public GeofencingRequest createGeofenceRequest(double lat, double lng) {
        Log.d(mCtxt, TAG, "creating geofence at location "+lat+", "+lng);
        Geofence currGeofence =
                new Geofence.Builder().setRequestId(GEOFENCE_REQUEST_ID)
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .setCircularRegion(lat, lng, DEFAULT_GEOFENCE_RADIUS)
                        .setNotificationResponsiveness(GEOFENCE_RESPONSIVENESS) // 5 secs
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
