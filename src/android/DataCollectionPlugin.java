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
import com.google.gson.Gson;

import edu.berkeley.eecs.emission.*;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.LocationTrackingConfig;
import edu.berkeley.eecs.emission.cordova.tracker.location.TripDiaryStateMachineReceiver;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;

public class DataCollectionPlugin extends CordovaPlugin {
    public static String TAG = "DataCollectionPlugin";

    @Override
    public void pluginInitialize() {
        final Activity myActivity = cordova.getActivity();
        int connectionResult = GooglePlayServicesUtil.isGooglePlayServicesAvailable(myActivity);
        if (connectionResult == ConnectionResult.SUCCESS) {
            Log.d(myActivity, TAG, "google play services available, initializing state machine");
            // we want to run this in a separate thread, since it may take some time to get the
            // current location and create a geofence
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    TripDiaryStateMachineReceiver.initOnUpgrade(myActivity);
            }
            });
        } else {
            Log.e(myActivity, TAG, "unable to connect to google play services");
        }
    }


    @Override
    public boolean execute(String action, JSONArray data, final CallbackContext callbackContext) throws JSONException {
        if (action.equals("launchInit")) {
            Log.d(cordova.getActivity(), TAG, "application launched, init is nop on android");
            callbackContext.success();
            return true;
        } else if (action.equals("getConfig")) {
            Context ctxt = cordova.getActivity();
            LocationTrackingConfig cfg = ConfigManager.getConfig(ctxt);
            callbackContext.success(new Gson().toJson(cfg));
            return true;
        } else if (action.equals("setConfig")) {
            Context ctxt = cordova.getActivity();
            JSONObject newConfig = data.getJSONObject(0);
            LocationTrackingConfig cfg = new Gson().fromJson(newConfig.toString(), LocationTrackingConfig.class);
            ConfigManager.updateConfig(ctxt, cfg);
            callbackContext.success();
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
            // we want to run this in a background thread because it may wait to get the current location
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
            Context ctxt = cordova.getActivity();
            ctxt.sendBroadcast(new Intent(ctxt.getString(R.string.transition_stopped_moving)));
            callbackContext.success(ctxt.getString(R.string.transition_stopped_moving));
                }
            });
            return true;
        } else if (action.equals("forceRemotePush")) {
            Log.i(cordova.getActivity(), TAG, "on android, we don't handle remote pushes");
            callbackContext.success("NOP");
            return true;
        } else if (action.equals("getAccuracyOptions")) {
            JSONObject retVal = new JSONObject();
            retVal.put("PRIORITY_HIGH_ACCURACY", LocationRequest.PRIORITY_HIGH_ACCURACY);
            retVal.put("PRIORITY_BALANCED_POWER_ACCURACY", LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
            retVal.put("PRIORITY_LOW_POWER", LocationRequest.PRIORITY_LOW_POWER);
            retVal.put("PRIORITY_NO_POWER", LocationRequest.PRIORITY_NO_POWER);
            callbackContext.success(retVal);
            return true;
        } else {
            return false;
        }
    }
}
