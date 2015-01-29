package edu.berkeley.eecs.cfc_tracker.location.actions;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import edu.berkeley.eecs.cfc_tracker.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import edu.berkeley.eecs.cfc_tracker.Constants;
import edu.berkeley.eecs.cfc_tracker.location.LocationChangeIntentService;

/**
 * Created by shankari on 12/30/14.
 */
public class LocationTrackingActions {
    private static final String TAG = "LocationHandler";
    private static final int LOCATION_IN_NUMBERS = 56228466;
    private static final int LOCATION_UPDATE_INTERVAL = Constants.THIRTY_SECONDS;

    private Context mCtxt;
    private GoogleApiClient mGoogleApiClient;

    public LocationTrackingActions(Context ctxt, GoogleApiClient googleApiClient) {
        this.mCtxt = ctxt;
        this.mGoogleApiClient = googleApiClient;
    }

    public PendingResult<Status> start() {
        return LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,
                getLocationRequest(),
                getLocationTrackingPendingIntent(mCtxt));
                // .setResultCallback(startCallback);
    }

    public LocationRequest getLocationRequest() {
        LocationRequest defaultRequest = LocationRequest.create();
        Log.d(TAG, "default request is " + defaultRequest);
        return defaultRequest
                .setInterval(30 * 1000);
    }

    ResultCallback<Status> startCallback = new ResultCallback<Status>() {
        @Override
        public void onResult(Status status) {

        }
    };

    public PendingResult<Status> stop() {
        return LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient,
                getLocationTrackingPendingIntent(mCtxt));
                // .setResultCallback(stopCallback);
    }

    ResultCallback<Status> stopCallback = new ResultCallback<Status>() {
        @Override
        public void onResult(Status status) {

        }
    };

    public static PendingIntent getLocationTrackingPendingIntent(Context ctxt) {
        Intent innerIntent = new Intent(ctxt, LocationChangeIntentService.class);
		/*
		 * Setting FLAG_UPDATE_CURRENT so that sending the PendingIntent again updates the original.
		 * We only want to have one geofence active at one point of time.
		 */
        return PendingIntent.getService(ctxt, 0, innerIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
