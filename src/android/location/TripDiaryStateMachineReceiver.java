package edu.berkeley.eecs.emission.cordova.tracker.location;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.apache.cordova.ConfigXmlParser;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import edu.berkeley.eecs.emission.BuildConfig;
import edu.berkeley.eecs.emission.R;
import edu.berkeley.eecs.emission.cordova.tracker.ConfigManager;
import edu.berkeley.eecs.emission.cordova.tracker.sensors.BatteryUtils;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.Battery;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.LocationTrackingConfig;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.NotificationHelper;
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
	private static String TAG = "TripDiaryStateMachineReceiver";
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

        // Next two calls copied over from the constructor, figure out if this is the best place to
        // put them
        validTransitions = new HashSet<String>(Arrays.asList(new String[]{
                context.getString(R.string.transition_initialize),
                context.getString(R.string.transition_exited_geofence),
                context.getString(R.string.transition_stopped_moving),
                context.getString(R.string.transition_stop_tracking),
                context.getString(R.string.transition_start_tracking)
        }));

        if (!validTransitions.contains(intent.getAction())) {
            Log.e(context, TAG, "Received unknown action "+intent.getAction()+" ignoring");
            return;
        }

        Intent serviceStartIntent = getStateMachineServiceIntent(context);
        serviceStartIntent.setAction(intent.getAction());
        context.startService(serviceStartIntent);
    }

    /*
 * TODO: Need to find a place to put this.
 */
    public static void validateAndCleanupState(Context ctxt) {
        /*
         * Check for being in geofence if in waiting_for_trip_state.
         */
        if (TripDiaryStateMachineService.getState(ctxt).equals(ctxt.getString(R.string.state_start))) {
            ctxt.sendBroadcast(new Intent(ctxt.getString(R.string.transition_initialize)));
        } else if (TripDiaryStateMachineService.getState(ctxt).equals(
                ctxt.getString(R.string.state_waiting_for_trip_start))) {
            // We cannot check to see whether there is an existing geofence and whether we are in it.
            // In particular, there is no method to get a geofence given an ID, and no method to get the status of a geofence
            // even if we did have it. So this is not a check that we can do.
        }
        initOnUpgrade(ctxt);
    }

    public static void saveBatteryAndSimulateUser(Context ctxt) {
        Battery currInfo = BatteryUtils.getBatteryInfo(ctxt);
        UserCacheFactory.getUserCache(ctxt).putSensorData(R.string.key_usercache_battery, currInfo);
        if (ConfigManager.getConfig(ctxt).isSimulateUserInteraction()) {
            NotificationHelper.createNotification(ctxt, 1234, "Interact with me! Battery level is "+
                    currInfo.getBatteryLevelPct());
        }
    }

    public static void initOnUpgrade(Context ctxt) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctxt);
        System.out.println("All preferences are "+sp.getAll());

        int currentCompleteVersion = sp.getInt(SETUP_COMPLETE_KEY, 0);
        if(currentCompleteVersion != BuildConfig.VERSION_CODE) {
            Log.d(ctxt, TAG, "Setup not complete, checking consent before initialize");
            // this is the code that checks whether the native collection has been upgraded and
            // restarts the data collection in that case. Without this, tracking is turned off
            // until the user restarts the app.
            // https://github.com/e-mission/e-mission-data-collection/commit/5544afd64b0c731e1633d1dd9f51a713fdea85fa
            // Since every consent change is (presumably) tied to a native code change, we can
            // just check for the consent here before reinitializing.
            ConfigXmlParser parser = new ConfigXmlParser();
            parser.parse(ctxt);
            String reqConsent = parser.getPreferences().getString("emSensorDataCollectionProtocolApprovalDate", null);
            if (ConfigManager.isConsented(ctxt, reqConsent)) {
            ctxt.sendBroadcast(new Intent(ctxt.getString(R.string.transition_initialize)));
            SharedPreferences.Editor prefsEditor = sp.edit();
            // TODO: This is supposed to be set from the javascript as part of the onboarding process.
            // However, it looks like it doesn't actually work - it looks like the app preferences plugin
            // saves to local storage by default. Need to debug the app preferences plugin and maybe ask
            // some questions of the maintainer. For now, setting it here for the first time should be fine.
            prefsEditor.putInt(SETUP_COMPLETE_KEY, BuildConfig.VERSION_CODE);
            prefsEditor.commit();
        } else {
                NotificationHelper.createNotification(ctxt, STARTUP_IN_NUMBERS,
                        "New data collection terms - collection paused until consent");
            }
        } else {
            Log.d(ctxt, TAG, "Setup complete, skipping initialize");
        }
    }

    public static void restartCollection(Context ctxt) {
        /*
         Super hacky solution, but works for now. Steps are:
         * Stop tracking
         * Poll for state change
         * Start tracking
         * Ugh! My eyeballs hurt to even read that?!
         */
        ctxt.sendBroadcast(new Intent(ctxt.getString(R.string.transition_stop_tracking)));
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
                fCtxt.sendBroadcast(new Intent(fCtxt.getString(R.string.transition_start_tracking)));
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
