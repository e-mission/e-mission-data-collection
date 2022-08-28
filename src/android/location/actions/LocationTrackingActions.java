package edu.berkeley.eecs.emission.cordova.tracker.location.actions;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import edu.berkeley.eecs.emission.cordova.tracker.ConfigManager;
import edu.berkeley.eecs.emission.cordova.tracker.location.TripDiaryStateMachineForegroundService;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.LocationTrackingConfig;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;

import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Task;

import edu.berkeley.eecs.emission.cordova.tracker.location.LocationChangeIntentService;

/**
 * Created by shankari on 12/30/14.
 */
public class LocationTrackingActions {
    private static final String TAG = "LocationTrackingAction";
    private static final int LOCATION_IN_NUMBERS = 56228466;

    private Context mCtxt;

  public LocationTrackingActions(Context ctxt) {
        this.mCtxt = ctxt;
    }

    public Task<Void> start() {
        try {
        Log.d(mCtxt, TAG, "requesting location updates" + getLocationRequest());
        return LocationServices.getFusedLocationProviderClient(mCtxt).requestLocationUpdates(
                getLocationRequest(),
                getLocationTrackingPendingIntent(mCtxt));
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

    public Task<Void> stop() {
        return LocationServices.getFusedLocationProviderClient(mCtxt).removeLocationUpdates(
                getLocationTrackingPendingIntent(mCtxt));
        }

    public static PendingIntent getLocationTrackingPendingIntent(Context ctxt) {
        Intent innerIntent = new Intent(ctxt, LocationChangeIntentService.class);
		/*
		 * Setting FLAG_UPDATE_CURRENT so that sending the PendingIntent again updates the original.
		 * We only want to receive one location at one point of time.
		 */
		return TripDiaryStateMachineForegroundService.getProperPendingIntent(ctxt, innerIntent);
    }

}
