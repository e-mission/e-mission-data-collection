package edu.berkeley.eecs.emission.cordova.tracker;

import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationRequest;

import edu.berkeley.eecs.emission.R;
import edu.berkeley.eecs.emission.cordova.tracker.location.LocationTrackingConfig;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;

public class DataCollectionPlugin extends CordovaPlugin {
    public static String TAG = "DataCollectionPlugin";
    private static final String SETUP_COMPLETE_KEY = "setup_complete";

    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {
        if (action.equals("startupInit")) {
            Activity myActivity = cordova.getActivity();
            int connectionResult = GooglePlayServicesUtil.isGooglePlayServicesAvailable(myActivity);
            if (connectionResult == ConnectionResult.SUCCESS) {
                Log.d(myActivity, TAG, "google play services available, initializing state machine");
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(myActivity);
                if(!sp.getBoolean(SETUP_COMPLETE_KEY, false)) {
                    myActivity.sendBroadcast(new Intent(myActivity.getString(R.string.transition_initialize)));
                }
                callbackContext.success();
            } else {
                Log.e(myActivity, TAG, "unable to connect to google play services");
                callbackContext.error(connectionResult);
            }
            return true;
        } else if (action.equals("launchInit")) {
            Log.d(cordova.getActivity(), TAG, "application launched, init is nop on android");
            callbackContext.success();
            return true;
        } else if (action.equals("getConfig")) {
            Context ctxt = cordova.getActivity();
            LocationTrackingConfig cfg = LocationTrackingConfig.getConfig(ctxt);

            JSONObject retObject = new JSONObject();
            retObject.put("isDutyCycling", LocationTrackingConfig.getConfig(ctxt).isDutyCycling());
            retObject.put("accuracy", getAccuracyAsString(cfg.getAccuracy()));
            retObject.put("geofenceRadius", cfg.getRadius());
            retObject.put("accuracyThreshold", cfg.getAccuracyThreshold());
            retObject.put("filter", "time");
            retObject.put("filterValue", cfg.getDetectionInterval());
            retObject.put("tripEndStationaryMins", 5 * 60);
            callbackContext.success(retObject);
            return true;
        } else if (action.equals("getState")) {
            Context ctxt = cordova.getActivity();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
            String state = prefs.getString(ctxt.getString(R.string.curr_state_key), ctxt.getString(R.string.state_start));
            callbackContext.success(state);
            return true;
        } else if (action.equals("forceTripStart")) {
            Context ctxt = cordova.getActivity();
            ctxt.sendBroadcast(new Intent(ctxt.getString(R.string.transition_exited_geofence)));
            callbackContext.success(ctxt.getString(R.string.transition_exited_geofence));
            return true;
        } else if (action.equals("forceTripEnd")) {
            Context ctxt = cordova.getActivity();
            ctxt.sendBroadcast(new Intent(ctxt.getString(R.string.transition_stopped_moving)));
            callbackContext.success(ctxt.getString(R.string.transition_stopped_moving));
            return true;
        } else if (action.equals("forceRemotePush")) {
            Log.i(cordova.getActivity(), TAG, "on android, we don't handle remote pushes");
            callbackContext.success("NOP");
            return true;
        } else {
            return false;
        }
    }

    public String getAccuracyAsString(int accuracyLevel) {
        if (accuracyLevel == LocationRequest.PRIORITY_HIGH_ACCURACY) {
            return "HIGH";
        } else if (accuracyLevel == LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY) {
            return "BALANCED";
        } else {
            return "UNKNOWN";
        }
    }
}
