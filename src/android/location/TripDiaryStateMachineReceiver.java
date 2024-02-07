package edu.berkeley.eecs.emission.cordova.tracker.location;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import edu.berkeley.eecs.emission.BuildConfig;
import edu.berkeley.eecs.emission.R;

import edu.berkeley.eecs.emission.cordova.tracker.ConfigManager;
import edu.berkeley.eecs.emission.cordova.tracker.ExplicitIntent;
import edu.berkeley.eecs.emission.cordova.tracker.sensors.BatteryUtils;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.Battery;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.SimpleLocation;
import edu.berkeley.eecs.emission.cordova.tracker.verification.SensorControlBackgroundChecker;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.NotificationHelper;
import edu.berkeley.eecs.emission.cordova.usercache.BuiltinUserCache;
import edu.berkeley.eecs.emission.cordova.usercache.UserCacheFactory;

/*
 * The BroadcastReceiver is really lightweight because it is considered to be inactive once it
 * returns from onReceive. Most of our real work happens after the google API client is connected,
 * which is an async call with a callback. It looks like the inactive process can be killed even
 * when processing the callback.
 * http://developer.android.com/reference/android/content/BroadcastReceiver.html#ReceiverLifecycle
 * https://github.com/e-mission/e-mission-data-collection/issues/36
 *
 * So most of the real work is in the associated @class TripDiaryStateMachineIntentService
 */

public class TripDiaryStateMachineReceiver extends BroadcastReceiver {

	public static Set<String> validTransitions = null;
	private static String TAG = "TripDiaryStateMachineRcvr";
    private static final String SETUP_COMPLETE_KEY = "setup_complete";
    private static final int STARTUP_IN_NUMBERS = 7827887;

    public TripDiaryStateMachineReceiver() {
        // The automatically created receiver needs a default constructor
        android.util.Log.i(TAG, "noarg constructor called");
    }

	public TripDiaryStateMachineReceiver(Context context) {
		android.util.Log.i(TAG, "TripDiaryStateMachineReceiver constructor called");

        // Why do we need to put the CURR_STATE_KEY to null every time the receiver is created?
        // Oh wait, is this the call from the activity?
        // Need to investigate and unify
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putString(context.getString(R.string.curr_state_key), null);
        editor.apply();
    }

	@Override
	public void onReceive(Context context, Intent intent) {
        Log.i(context, TAG, "TripDiaryStateMachineReciever onReceive(" + context + ", " + intent + ") called");

        // Check to see if the user came out of airplane mode as the FSM needs to be restarted if they did
        if (intent.getAction() != null && intent.getAction().equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
            boolean isAirplaneModeOn = intent.getBooleanExtra("state", false);
            if (!isAirplaneModeOn) {
                Log.d(context, TAG, "Airplane mode was turned off, restart FSM.");
                SensorControlBackgroundChecker.restartFSMIfStartState(context);
            } else {
                Log.d(context, TAG, "Airplane mode was turned on.");
            }
        }

        // Next two calls copied over from the constructor, figure out if this is the best place to
        // put them
        validTransitions = new HashSet<String>(Arrays.asList(new String[]{
                context.getString(R.string.transition_initialize),
                context.getString(R.string.transition_exited_geofence),
                context.getString(R.string.transition_stopped_moving),
                context.getString(R.string.transition_stop_tracking),
                context.getString(R.string.transition_start_tracking),
                context.getString(R.string.transition_tracking_error)
        }));

        if (!validTransitions.contains(intent.getAction())) {
            Log.e(context, TAG, "Received unknown action "+intent.getAction()+" ignoring");
            return;
        }

        if (intent.getAction().equals(context.getString(R.string.transition_initialize))) {
            String reqConsent = ConfigManager.getReqConsent(context);
            if (ConfigManager.isConsented(context, reqConsent)) {
                Log.i(context, TAG, reqConsent + " is the current consented version, sending msg to service...");
            } else {
            JSONObject introDoneResult = null;
            try {
                introDoneResult = BuiltinUserCache.getDatabase(context).getLocalStorage("intro_done", false);
            } catch(JSONException e) {
                Log.i(context, TAG, "unable to read intro done state, skipping prompt");
                    return;
            }
            if (introDoneResult != null) {
                    Log.i(context, TAG, reqConsent + " is not the current consented version, skipping init...");
                    NotificationHelper.createNotification(context, STARTUP_IN_NUMBERS,
                      null, context.getString(R.string.new_data_collection_terms));
                    return;
                } else {
                Log.i(context, TAG, "onboarding is not complete, skipping prompt");
                    return;
                }
            }
            TripDiaryStateMachineForegroundService.startProperly(context);
        }

        // we should only get here if the user has consented
        Intent serviceStartIntent = getStateMachineServiceIntent(context);
        serviceStartIntent.setAction(intent.getAction());
        context.startService(serviceStartIntent);
    }

    public static void performPeriodicActivity(Context ctxt) {
        /*
         * Now, do some validation of the current state and clean it up if necessary. This should
         * help with issues we have seen in the field where location updates pause mysteriously, or
         * geofences are never exited.
         */
        Log.i(ctxt, TAG, "START PERIODIC ACTIVITY");
        checkForegroundNotification(ctxt);
        checkLocationStillAvailable(ctxt);
        validateAndCleanupState(ctxt);
        initOnUpgrade(ctxt);
        saveBatteryAndSimulateUser(ctxt);
        Log.i(ctxt, TAG, "END PERIODIC ACTIVITY");
    }

    public static void checkLocationStillAvailable(Context ctxt) {
        SensorControlBackgroundChecker.checkAppState(ctxt);
    }

    public static void validateAndCleanupState(Context ctxt) {
        /*
         * Check for being in geofence if in waiting_for_trip_state.
         */
        if (TripDiaryStateMachineService.getState(ctxt).equals(ctxt.getString(R.string.state_start))) {
            Log.d(ctxt, TAG, "Still in start state, sending initialize...");
            ctxt.sendBroadcast(new ExplicitIntent(ctxt, R.string.transition_initialize));
        } else if (TripDiaryStateMachineService.getState(ctxt).equals(
                ctxt.getString(R.string.state_waiting_for_trip_start))) {
            // We cannot check to see whether there is an existing geofence and whether we are in it.
            // In particular, there is no method to get a geofence given an ID, and no method to get the status of a geofence
            // even if we did have it. So this is not a check that we can do.
        } else if (TripDiaryStateMachineService.getState(ctxt).equals(ctxt.getString(R.string.state_ongoing_trip))) {
            Log.d(ctxt, TAG, "In ongoing trip, checking for ongoing data collection");
            // Get the last recorded point
            SimpleLocation[] lastPoints = UserCacheFactory.getUserCache(ctxt).getLastSensorData(R.string.key_usercache_location, 1, SimpleLocation.class);
            if (lastPoints.length == 0) {
                Log.d(ctxt, TAG, "Found zero points while in 'ongoing_trip' state, re-initializing");
                ctxt.sendBroadcast(new ExplicitIntent(ctxt, R.string.transition_initialize));
            } else {
                SimpleLocation lastPoint = lastPoints[0];
                double nowSecs = ((double)System.currentTimeMillis())/1000;
                double filterTimeSecs = ((double)ConfigManager.getConfig(ctxt).getFilterTime())/1000;
                double threshold = filterTimeSecs * 100;
                // 100 * time filter
                // https://github.com/e-mission/e-mission-docs/issues/580#issuecomment-700791309
                double lastPointAgo = nowSecs - lastPoint.getTs();
                if (lastPointAgo > threshold) {
                    Log.d(ctxt, TAG, "Last point read was "+lastPoint+", "+lastPointAgo+" secs ago, beyond threshold, "+threshold+" re-initializing");
                    ctxt.sendBroadcast(new ExplicitIntent(ctxt, R.string.transition_initialize));
                } else {
                    Log.d(ctxt, TAG, "Last point read was "+lastPoint+", "+lastPointAgo+" secs ago, within threshold, "+threshold+" all is well");
                }
            }
        }
    }

    public static void saveBatteryAndSimulateUser(Context ctxt) {
        Battery currInfo = BatteryUtils.getBatteryInfo(ctxt);
        UserCacheFactory.getUserCache(ctxt).putSensorData(R.string.key_usercache_battery, currInfo);
        if (ConfigManager.getConfig(ctxt).isSimulateUserInteraction()) {
            NotificationHelper.createNotification(ctxt, 1234, null, ctxt.getString(R.string.battery_level,
                    currInfo.getBatteryLevelPct()));
        }
    }

    public static void initOnUpgrade(Context ctxt) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctxt);
        System.out.println("All preferences are "+sp.getAll());

        int currentCompleteVersion = sp.getInt(SETUP_COMPLETE_KEY, 0);
        Log.d(ctxt, TAG, "Comparing installed version "+currentCompleteVersion
            + " with new version " + BuildConfig.VERSION_CODE);
        if(currentCompleteVersion != BuildConfig.VERSION_CODE) {
            Log.d(ctxt, TAG, "Setup not complete, sending initialize");
            // this is the code that checks whether the native collection has been upgraded and
            // restarts the data collection in that case. Without this, tracking is turned off
            // until the user restarts the app.
            // https://github.com/e-mission/e-mission-data-collection/commit/5544afd64b0c731e1633d1dd9f51a713fdea85fa
            // Since every consent change is (presumably) tied to a native code change, we can
            // just check for the consent here before reinitializing.
            ctxt.sendBroadcast(new ExplicitIntent(ctxt, R.string.transition_initialize));
            SharedPreferences.Editor prefsEditor = sp.edit();
            // TODO: This is supposed to be set from the javascript as part of the onboarding process.
            // However, it looks like it doesn't actually work - it looks like the app preferences plugin
            // saves to local storage by default. Need to debug the app preferences plugin and maybe ask
            // some questions of the maintainer. For now, setting it here for the first time should be fine.
            prefsEditor.putInt(SETUP_COMPLETE_KEY, BuildConfig.VERSION_CODE);
            prefsEditor.commit();
        } else {
            Log.d(ctxt, TAG, "Setup complete, skipping initialize");
        }
    }

    public static void checkForegroundNotification(Context ctxt) {
      TripDiaryStateMachineForegroundService.checkForegroundNotification(ctxt);
    }

    public static void restartCollection(Context ctxt) {
        /*
         Super hacky solution, but works for now. Steps are:
         * Stop tracking
         * Poll for state change
         * Start tracking
         * Ugh! My eyeballs hurt to even read that?!
         */
        if (TripDiaryStateMachineService.getState(ctxt).equals(ctxt.getString(R.string.state_tracking_stopped))) {
            Log.i(ctxt, TAG, "in restartCollection, tracking is already stopped "
                + " new config will be picked up when it starts"
                + " early return");
            return;
        }
        ctxt.sendBroadcast(new ExplicitIntent(ctxt, R.string.transition_stop_tracking));
        final Context fCtxt = ctxt;
        new Thread(new Runnable() {
            @Override
            public void run() {
        boolean stateChanged = false;
        while(!stateChanged) {
            try {
                Thread.sleep(1000); // Wait for one second in the loop
                        stateChanged = (TripDiaryStateMachineService.getState(fCtxt).equals(
                                fCtxt.getString(R.string.state_tracking_stopped)));
            } catch (InterruptedException e) {
                e.printStackTrace();
                // Sleep has been interrupted, might as well exit the while loop
                stateChanged = true;
            }
        }
                fCtxt.sendBroadcast(new ExplicitIntent(fCtxt, R.string.transition_start_tracking));
            }
        }).start();
    }

    private Intent getStateMachineServiceIntent(Context context) {
        if (ConfigManager.getConfig(context).isDutyCycling()) {
            return new Intent(context, TripDiaryStateMachineService.class);
        } else {
            return new Intent(context, TripDiaryStateMachineServiceOngoing.class);
        }
    }
}
