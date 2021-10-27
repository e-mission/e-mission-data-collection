package edu.berkeley.eecs.emission.cordova.tracker.verification;
// Auto fixed by post-plugin hook
import edu.berkeley.eecs.emission.R;

import android.app.PendingIntent;
import android.content.Context;
import android.location.Location;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;


import edu.berkeley.eecs.emission.cordova.tracker.Constants;
import edu.berkeley.eecs.emission.cordova.tracker.ExplicitIntent;
import edu.berkeley.eecs.emission.cordova.tracker.location.actions.LocationTrackingActions;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.NotificationHelper;

public class SensorControlChecks {
    public static final String TAG = "SensorControlChecks";

    public static void checkLocationSettings(final Context ctxt,
                                             OnCompleteListener<LocationSettingsResponse> callback) {
        Log.i(ctxt, TAG, "About to check location settings");
        LocationRequest request = new LocationTrackingActions(ctxt).getLocationRequest();
        Log.d(ctxt, TAG, "Checking location settings for request "+request);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
          .addLocationRequest(request);

        Task<LocationSettingsResponse> task =
          LocationServices.getSettingsClient(ctxt).checkLocationSettings(builder.build());
        Log.d(ctxt, TAG, "Got back result "+task);
        task.addOnCompleteListener(callback);
    }

    public static void fixLocationSettings(final Context ctxt) {
        Log.i(ctxt, TAG, "About to fix location settings, checking first...");
    }
}
