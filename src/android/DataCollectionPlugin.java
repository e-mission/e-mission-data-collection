package edu.berkeley.eecs.emission.cordova.tracker;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

import edu.berkeley.eecs.emission.*;
import edu.berkeley.eecs.emission.cordova.tracker.location.TripDiaryStateMachineService;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.ConsentConfig;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.LocationTrackingConfig;
import edu.berkeley.eecs.emission.cordova.tracker.location.TripDiaryStateMachineReceiver;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.StatsEvent;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.NotificationHelper;
import edu.berkeley.eecs.emission.cordova.usercache.BuiltinUserCache;

public class DataCollectionPlugin extends CordovaPlugin {
    public static final String TAG = "DataCollectionPlugin";
    public static String LOCATION_PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION;

    public static final int ENABLE_LOCATION_SETTINGS = 362253738;
    public static final int ENABLE_LOCATION_PERMISSION = 362253737;

    public static final String ENABLE_LOCATION_PERMISSION_ACTION = "ENABLE_LOCATION_PERMISSION";

    @Override
    public void pluginInitialize() {
        final Activity myActivity = cordova.getActivity();
        BuiltinUserCache.getDatabase(myActivity).putMessage(R.string.key_usercache_client_nav_event,
                new StatsEvent(myActivity, R.string.app_launched));

        TripDiaryStateMachineReceiver.initOnUpgrade(myActivity);
        TripDiaryStateMachineReceiver.startForegroundIfNeeded(myActivity);
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
            // Note that we don't call initOnUpgrade so that we can handle the case where the
            // user deleted the consent and re-consented, but didn't upgrade the app
            checkAndPromptPermissions();
            // ctxt.sendBroadcast(new ExplicitIntent(ctxt, R.string.transition_initialize));
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
                        ctxt.sendBroadcast(new ExplicitIntent(ctxt, androidTransition));
                        callbackContext.success(androidTransition);
                    } else {
                        callbackContext.error(generalTransition + " not supported, ignoring");
                    }
                }
            });
            return true;
        } else if (action.equals("handleSilentPush")) {
            throw new UnsupportedOperationException("silent push handling not supported for android");
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

    private void checkAndPromptPermissions() {
        if(cordova.hasPermission(LOCATION_PERMISSION)) {
            TripDiaryStateMachineService.restartFSMIfStartState(cordova.getActivity());
        } else {
            cordova.requestPermission(this, ENABLE_LOCATION_PERMISSION, LOCATION_PERMISSION);
        }
    }

    private void displayResolution(PendingIntent resolution) {
        if (resolution != null) {
            try {
                cordova.setActivityResultCallback(this);
                cordova.getActivity().startIntentSenderForResult(resolution.getIntentSender(), ENABLE_LOCATION_SETTINGS, null, 0, 0, 0, null);
            } catch (IntentSender.SendIntentException e) {
                Context mAct = cordova.getActivity();
                NotificationHelper.createNotification(mAct, Constants.TRACKING_ERROR_ID, mAct.getString(R.string.unable_resolve_issue));
            }
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        Context mAct = cordova.getActivity();
        Log.d(mAct, TAG, "onNewIntent(" + intent.getAction() + ")");
        Log.d(mAct, TAG, "Found extras " + intent.getExtras());

        if(ENABLE_LOCATION_PERMISSION_ACTION.equals(intent.getAction())) {
            checkAndPromptPermissions();
            return;
        }
        if (NotificationHelper.DISPLAY_RESOLUTION_ACTION.equals(intent.getAction())) {
            PendingIntent piFromIntent = intent.getParcelableExtra(
                    NotificationHelper.RESOLUTION_PENDING_INTENT_KEY);
            displayResolution(piFromIntent);
            return;
        }
        Log.i(mAct, TAG, "Action "+intent.getAction()+" unknown, ignoring ");
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException
    {
        /*
         Let us figure out if we want to sent a javascript callback with the error.
         This is currently only called from markConsented, and I don't think we listen to failures there
        for(int r:grantResults)
        {
            if(r == PackageManager.PERMISSION_DENIED)
            {
                this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, PERMISSION_DENIED_ERROR));
                return;
            }
        }
         */
        switch(requestCode)
        {
            case ENABLE_LOCATION_PERMISSION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    NotificationHelper.cancelNotification(cordova.getActivity(), ENABLE_LOCATION_PERMISSION);
                    TripDiaryStateMachineService.restartFSMIfStartState(cordova.getActivity());
                } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    TripDiaryStateMachineService.generateLocationEnableNotification(cordova.getActivity());
                }
                break;
            default:
                Log.e(cordova.getActivity(), TAG, "Unknown permission code "+requestCode+" ignoring");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(cordova.getActivity(), TAG, "received onActivityResult("+requestCode+","+
                resultCode+","+data.getDataString()+")");
        switch (requestCode) {
            case ENABLE_LOCATION_SETTINGS:
                Activity mAct = cordova.getActivity();
                Log.d(mAct, TAG, requestCode + " is our code, handling callback");
                cordova.setActivityResultCallback(null);
                final LocationSettingsStates states = LocationSettingsStates.fromIntent(data);
                Log.d(cordova.getActivity(), TAG, "at this point, isLocationUsable = "+states.isLocationUsable());
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        // All required changes were successfully made
                        Log.i(cordova.getActivity(), TAG, "All changes successfully made, reinitializing");
                        NotificationHelper.cancelNotification(mAct, Constants.TRACKING_ERROR_ID);
                        TripDiaryStateMachineService.restartFSMIfStartState(mAct);
                        break;
                    case Activity.RESULT_CANCELED:
                        // The user was asked to change settings, but chose not to
                        Log.e(cordova.getActivity(), TAG, "User chose not to change settings, dunno what to do");
                        break;
                    default:
                        Log.e(cordova.getActivity(), TAG, "Unknown result code while enabling location "+resultCode);
                        break;
                }
                break;
            default:
                Log.d(cordova.getActivity(), TAG, "Got unsupported request code "+requestCode+ " , ignoring...");
        }
    }

}
