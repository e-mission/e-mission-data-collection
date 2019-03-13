package edu.berkeley.eecs.emission.cordova.tracker.location.actions;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import edu.berkeley.eecs.emission.cordova.tracker.ConfigManager;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.LocationTrackingConfig;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import edu.berkeley.eecs.emission.cordova.tracker.location.LocationChangeIntentService;

/**
 * Created by shankari on 12/30/14.
 */
public class LocationTrackingActions {
    private static final String TAG = "LocationHandler";
    private static final int LOCATION_IN_NUMBERS = 56228466;

    private Context mCtxt;
    private GoogleApiClient mGoogleApiClient;

    public LocationTrackingActions(Context ctxt, GoogleApiClient googleApiClient) {
        this.mCtxt = ctxt;
        this.mGoogleApiClient = googleApiClient;
    }

    public PendingResult<Status> start() {
        try {
        return LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,
                getLocationRequest(),
                getLocationTrackingPendingIntent(mCtxt));
                // .setResultCallback(startCallback);
        } catch (SecurityException e) {
            Log.e(mCtxt, TAG, "Found security error "+e.getMessage()+" while creating geofence");
            return null;
        }
    }

    public LocationRequest getLocationRequest() {
        LocationTrackingConfig cfg = ConfigManager.getConfig(this.mCtxt);
        LocationRequest defaultRequest = LocationRequest.create();
        Log.d(mCtxt, TAG, "default request is " + defaultRequest);
        LocationRequest modifiedRequest = defaultRequest
                .setInterval(cfg.getFilterTime())
                .setPriority(cfg.getAccuracy());
        Log.d(mCtxt, TAG, "after applying config, value is "+modifiedRequest);
        return modifiedRequest;
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
