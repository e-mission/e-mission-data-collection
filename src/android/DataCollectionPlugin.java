package edu.berkeley.eecs.emission.cordova.tracker;

import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Intent;

public class DataCollectionPlugin extends CordovaPlugin {
    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {
        if (action.equals("startupInit")) {
            if (GooglePlayChecker.servicesConnected(cordova.getActivity()) {
                sendBroadcast(new Intent(getString(R.string.transition_initialize)));
                callbackContext.success();
            } else {
                Log.e(cordova.getActivity(), TAG, "unable to connect to google play services");
            }
            return true;
        } else if (action.equals("launchInit")) {
            Log.d(cordova.getActivity(), TAG, "application launched, init is nop on android");
            callbackContext.success();
            return true;
        } else if (action.equals("getConfig")) {
            Context ctxt = cordova.getActivity();
            LocationTrackingConfig cfg = LocationTrackingConfig.getConfig(ctxt);

            JSONObject retObject = JSONObject();
            retObject.put("isDutyCycling", LocationTrackingConfig.getConfig(ctxt).isDutyCycling();
            retObject.put("accuracy", getAccuracyAsString(cfg.getAccuracy()));
            retObject.put("geofenceRadius", cfg.getRadius());
            retObject.put("accuracyThreshold", cfg.getAccuracyThreshold());
            retObject.put("filter", "time");
            retObject.put("filterValue", cfg.getDetectionInterval());
            retObject.put("tripEndStationaryMins", 5 * 60);
            callbackContext.success(retObject);
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(cordova.getActivity(), "TAG, requestCode = "+requestCode+" resultCode = "+resultCode);
        GooglePlayChecker.onActivityResult(requestCode, resultCode, data);
    }
}
