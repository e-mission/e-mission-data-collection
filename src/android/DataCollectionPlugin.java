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

import java.util.HashMap;
import java.util.Map;

import edu.berkeley.eecs.emission.*;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.ConsentConfig;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.LocationTrackingConfig;
import edu.berkeley.eecs.emission.cordova.tracker.location.TripDiaryStateMachineReceiver;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.StatsEvent;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.NotificationHelper;
import edu.berkeley.eecs.emission.cordova.usercache.BuiltinUserCache;

public class DataCollectionPlugin extends CordovaPlugin {
    public static String TAG = "DataCollectionPlugin";

    @Override
    public void pluginInitialize() {
        final Activity myActivity = cordova.getActivity();
        int connectionResult = GooglePlayServicesUtil.isGooglePlayServicesAvailable(myActivity);
        if (connectionResult == ConnectionResult.SUCCESS) {
            Log.d(myActivity, TAG, "google play services available, initializing state machine");
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    TripDiaryStateMachineReceiver.initOnUpgrade(myActivity);
            }
            });
        } else {
            Log.e(myActivity, TAG, "unable to connect to google play services");
            NotificationHelper.createNotification(myActivity, Constants.TRACKING_ERROR_ID,
                    "Unable to connect to google play services, tracking turned off");
        }
        BuiltinUserCache.getDatabase(myActivity).putMessage(R.string.key_usercache_client_nav_event,
                new StatsEvent(myActivity, R.string.app_launched));
    }

    @Override
    public boolean execute(String action, JSONArray data, final CallbackContext callbackContext) throws JSONException {
        if (action.equals("launchInit")) {
            Log.d(cordova.getActivity(), TAG, "application launched, init is nop on android");
            callbackContext.success();
            return true;
        } else if (action.equals("markConsented")) {
            Log.d(cordova.getActivity(), TAG, "marking consent as done");
            Context ctxt = cordova.getActivity();
            JSONObject newConsent = data.getJSONObject(0);
            ConsentConfig cfg = new Gson().fromJson(newConsent.toString(), ConsentConfig.class);
            ConfigManager.setConsented(ctxt, cfg);
            // Now, really initialize the state machine
            TripDiaryStateMachineReceiver.initOnUpgrade(ctxt);
            // TripDiaryStateMachineReceiver.restartCollection(ctxt);
            callbackContext.success();
            return true;
        } else if (action.equals("getConfig")) {
            Context ctxt = cordova.getActivity();
            LocationTrackingConfig cfg = ConfigManager.getConfig(ctxt);
            // Gson.toJson() represents a string and we are expecting an object in the interface
            callbackContext.success(new JSONObject(new Gson().toJson(cfg)));
            return true;
        } else if (action.equals("setConfig")) {
            Context ctxt = cordova.getActivity();
            JSONObject newConfig = data.getJSONObject(0);
            LocationTrackingConfig cfg = new Gson().fromJson(newConfig.toString(), LocationTrackingConfig.class);
            ConfigManager.updateConfig(ctxt, cfg);
            TripDiaryStateMachineReceiver.restartCollection(ctxt);
            callbackContext.success();
            return true;
        } else if (action.equals("getState")) {
            Context ctxt = cordova.getActivity();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
            String state = prefs.getString(ctxt.getString(R.string.curr_state_key), ctxt.getString(R.string.state_start));
            callbackContext.success(state);
            return true;
        } else if (action.equals("forceTransition")) {
            // we want to run this in a background thread because it might sometimes wait to get
            // the current location
            final String generalTransition = data.getString(0);
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
            Context ctxt = cordova.getActivity();
                    Map<String, String> transitionMap = getTransitionMap(ctxt);
                    if (transitionMap.containsKey(generalTransition)) {
                        String androidTransition = transitionMap.get(generalTransition);
                        ctxt.sendBroadcast(new Intent(androidTransition));
                        callbackContext.success(androidTransition);
                    } else {
                        callbackContext.error(generalTransition + " not supported, ignoring");
                    }
                }
            });
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

    private static Map<String, String> getTransitionMap(Context ctxt) {
        Map<String, String> retVal = new HashMap<String, String>();
        retVal.put("INITIALIZE", ctxt.getString(R.string.transition_initialize));
        retVal.put("EXITED_GEOFENCE", ctxt.getString(R.string.transition_exited_geofence));
        retVal.put("STOPPED_MOVING", ctxt.getString(R.string.transition_stopped_moving));
        retVal.put("STOP_TRACKING", ctxt.getString(R.string.transition_stop_tracking));
        retVal.put("START_TRACKING", ctxt.getString(R.string.transition_start_tracking));
        return retVal;
    }
}
