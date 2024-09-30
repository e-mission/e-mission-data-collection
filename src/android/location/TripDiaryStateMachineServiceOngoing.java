package edu.berkeley.eecs.emission.cordova.tracker.location;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.LinkedList;
import java.util.List;

import edu.berkeley.eecs.emission.cordova.tracker.ConfigManager;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.NotificationHelper;
import edu.berkeley.eecs.emission.R;

import edu.berkeley.eecs.emission.cordova.tracker.location.actions.ActivityRecognitionActions;
import edu.berkeley.eecs.emission.cordova.tracker.location.actions.LocationTrackingActions;
import edu.berkeley.eecs.emission.cordova.tracker.sensors.BatteryUtils;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.usercache.UserCacheFactory;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.Transition;

/**
 * Created by shankari on 10/20/15.
 */

public class TripDiaryStateMachineServiceOngoing extends Service {
    public static String TAG = "TripDiaryStateMachineService";

    private static int STATE_IN_NUMBERS = 78283;

    /*
     * Unfortunately, due to the structure of the google API client, with callbacks
     * and everything, we are unable to do handle the intent statelessly. For example,
     * when the client is connected, the onConnected() method is invoked asynchronously
     * with a service specific connection hint. We have to make further calls
     * (to get the current location, to register a geofence) after we are connected,
     * using the client. But there is no way to pass in a reference to the client or
     * to the current state or to the intent action. So all of those need to be stored
     * in instance variables.
    */

    private String mCurrState = null;
    private String mTransition = null;
    private SharedPreferences mPrefs = null;
    private ForegroundServiceComm mComm = null;

    public TripDiaryStateMachineServiceOngoing() {
        super();
    }

    @Override
    public void onCreate() {
        Log.i(this, TAG, "Service created. Initializing one-time variables!");
        mComm = new ForegroundServiceComm(this);
    }

    @Override
    public void onDestroy() {
        Log.i(this, TAG, "Service destroyed. So long, suckers!");
        mComm.unbind();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent,  int flags, int startId) {
        Log.d(this, TAG, "service started with flags = "+flags+" startId = "+startId
                +" action = "+intent.getAction());
        if (flags == Service.START_FLAG_REDELIVERY) {
            Log.d(this, TAG, "service restarted! need to check idempotency!");
        }
        mTransition = intent.getAction();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mCurrState = mPrefs.getString(this.getString(R.string.curr_state_key),
            this.getString(R.string.state_start));
        Log.d(this, TAG, "after reading from the prefs, the current state is "+mCurrState);
        UserCacheFactory.getUserCache(this).putMessage(R.string.key_usercache_transition,
                new Transition(mCurrState, mTransition, ((double)System.currentTimeMillis())/1000));
        handleAction(this, mCurrState, mTransition);
        /*
         We are returning with START_REDELIVER_INTENT, so the process will be restarted with the
         same intent if it is killed. We need to think through the implications of this. If the
         process was killed before any of the actions were performed, then we are fine. If the
         process was killed after the actions were performed, then we are in trouble, because the
         actions are not necessarily idempotent.

         Some of the actions are clearly idempotent. For example, starting the activity recognition
         API. For example, deleting a geofence. It might be easiest to convert the actions to be
         idempotent, but that requires some careful work. TODO: Need to think through this carefully.
         */
        Log.d(this, TAG, "Launched connect to the google API client, returning from onStartCommand");
        return START_REDELIVER_INTENT;
    }

    public static String getState(Context ctxt) {
        SharedPreferences sPrefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
        return sPrefs.getString(ctxt.getString(R.string.curr_state_key), ctxt.getString(R.string.state_start));
    }

    public void setNewState(String newState) {
        Log.d(this, TAG, "newState after handling action is "+newState);
        SharedPreferences.Editor prefsEditor =
                PreferenceManager.getDefaultSharedPreferences(this).edit();
        prefsEditor.putString(this.getString(R.string.curr_state_key), newState);
        prefsEditor.commit();
        Log.d(this, TAG, "newState saved in prefManager is "+
                PreferenceManager.getDefaultSharedPreferences(this).getString(
                        this.getString(R.string.curr_state_key), "not found"));
        mComm.setNewState(newState);
        stopSelf();
    }

    /*
     * Handles the transition based on the current state.
     * Assumes that the API client is already connected.
     * TODO: It looks like the various actions are actually pretty similar - they invoke a
     * method on the API and then wait for a callback. It seems like it should be possible
     * to write a generic action that takes the google API call and the broadcast intent
     * as parameters, makes the call, and issues the broadcast in the callback
     */
    private void handleAction(Context ctxt, String currState, String actionString) {
        Log.d(this, TAG, "handleAction("+currState+", "+actionString+") called");
        assert(currState != null);
        // The current state is stored in the shared preferences, so on reboot, for example, we would
        // store that we are in ongoing_trip, but no listeners would be registered. We can have
        // the broadcast receiver generate an initialize transition, but the states other than
        // start don't handle initialize. So if we get an initialize, we manually call the start method.
        // This does not cover the case in which we get an initialize transition without a reboot, when
        // an earlier geofence or tracking might well be present.
        // TODO: Figure out how best to handle this and handle it. Options are:
        // - have a set state which allows the broadcast code and the test code to set the state to start
        // when restarting
        // - have initialize function as a reset, which stops any current stuff and starts the new one
        UserCacheFactory.getUserCache(ctxt).putSensorData(R.string.key_usercache_battery,
                BatteryUtils.getBatteryInfo(ctxt));
        if (actionString.equals(ctxt.getString(R.string.transition_initialize))) {
            handleStart(ctxt, actionString);
        } else if (currState.equals(ctxt.getString(R.string.state_start))) {
            handleStart(ctxt, actionString);
        } else if (currState.equals(ctxt.getString(R.string.state_waiting_for_trip_start))) {
            handleWaitingForTripStart(ctxt, actionString);
        } else if (currState.equals(ctxt.getString(R.string.state_ongoing_trip))) {
            handleOngoing(ctxt, actionString);
        } if (currState.equals(getString(R.string.state_tracking_stopped))) {
            handleTrackingStopped(ctxt, actionString);
        }
    }

    private void handleStart(Context ctxt, String actionString) {
        Log.d(this, TAG, "TripDiaryStateMachineReceiver handleStarted(" + actionString + ") called");
        // Get current location
        if (actionString.equals(ctxt.getString(R.string.transition_initialize)) &&
                !mCurrState.equals(ctxt.getString(R.string.state_tracking_stopped))) {
            startEverything(ctxt, actionString);
                    }
        if (actionString.equals(ctxt.getString(R.string.transition_stop_tracking))) {
            // Haven't started anything yet (that's why we are in the start state).
            // just move to the stop tracking state
            String newState = ctxt.getString(R.string.state_tracking_stopped);
            setNewState(newState);
                }
        if (actionString.equals(ctxt.getString(R.string.transition_tracking_error))) {
            /*
            NotificationHelper.createNotification(ctxt, Constants.TRACKING_ERROR_ID,
                    "Location tracking turned off. Please turn on for emission to work properly");
                    */
            Log.i(this, TAG, "Already in the start state, so going to stay there");
        }
        }

    // Originally, when the location tracking options were fixed at compile time, we didn't really
    // have to worry about this case. If we were not geofencing, then we would directly go to the
    // ongoing state on initialize, and we ALWAYS initialized when we changed the config.
    // But now, we could be in waiting for trip state when the user reconfigures, and now we
    // get into trouble because we don't handle the transition. But we can certainly make this simple.
    // If we start tracking, we start everything
    // If we stop tracking, we stop everything
    // For everything else, go to the ongoing state :)
    private void handleWaitingForTripStart(final Context ctxt, final String actionString) {
        if (actionString.equals(getString(R.string.transition_exited_geofence))) {
            startEverything(ctxt, actionString);
        } else if (actionString.equals(getString(R.string.transition_start_tracking))) {
            startEverything(ctxt, actionString);
        } else if (actionString.equals(ctxt.getString(R.string.transition_stop_tracking))) {
            // Haven't started anything yet (that's why we are in the start state).
            // just move to the stop tracking state
            stopEverything(ctxt, getString(R.string.state_tracking_stopped));
        } else if (actionString.equals(ctxt.getString(R.string.transition_tracking_error))) {
            /*
            NotificationHelper.createNotification(ctxt, Constants.TRACKING_ERROR_ID,
                    "Location tracking turned off. Please turn on for emission to work properly");
                    */
            stopEverything(ctxt, getString(R.string.state_start));
        } else {
            stopEverything(ctxt, getString(R.string.state_tracking_stopped));
        }
    }

    private void handleOngoing(Context ctxt, String actionString) {
        if (actionString.equals(ctxt.getString(R.string.transition_stop_tracking))) {
            stopEverything(ctxt, getString(R.string.state_tracking_stopped));
        }
        if (actionString.equals(ctxt.getString(R.string.transition_tracking_error))) {
            /*
            NotificationHelper.createNotification(ctxt, Constants.TRACKING_ERROR_ID,
                    "Location tracking turned off. Please turn on for emission to work properly");
                    */
            stopEverything(ctxt, getString(R.string.state_start));
        }
    }

    private void handleTrackingStopped(final Context ctxt, String actionString) {
        Log.d(this, TAG, "TripDiaryStateMachineReceiver handleTrackingStopped(" + actionString + ") called");
        if (actionString.equals(ctxt.getString(R.string.transition_start_tracking))) {
            startEverything(ctxt, actionString);
        }
        if (actionString.equals(ctxt.getString(R.string.transition_tracking_error))) {
            Log.i(this, TAG, "Tracking manually turned off, no need to prompt for location");
        }
    }

    private void startEverything(final Context ctxt, String actionString) {
        final List<Task<Void>> tokenList = new LinkedList<Task<Void>>();
        tokenList.add(new LocationTrackingActions(ctxt).start());
        tokenList.add(new ActivityRecognitionActions(ctxt).start());
        // TODO: How to pass in the token list?
        // Also, the callback is currently the same for all of them, but could potentially be
        // different in the future once we add in failure handling because we may want to do
        // different things based on the different failure cases. If we don't do that, we should
        // refactor the callback to a common class. and then we need to pass in the token list to it
        // since it can't use the final variable
        final Context fCtxt = ctxt;
        Tasks.whenAllSuccess(tokenList).addOnCompleteListener(task -> {
                String newState = fCtxt.getString(R.string.state_ongoing_trip);
          if (task.isSuccessful()) {
                    setNewState(newState);
                    if (ConfigManager.getConfig(ctxt).isSimulateUserInteraction()) {
                    NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                            null, fCtxt.getString(R.string.success_moving_new_state, newState));
                    }
                } else {
                    if (ConfigManager.getConfig(ctxt).isSimulateUserInteraction()) {
                    NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                            null, fCtxt.getString(R.string.failed_moving_new_state,newState));
                }
                }
        });
    }

    private void stopEverything(final Context ctxt, final String targetState) {
        final List<Task<Void>> tokenList = new LinkedList<Task<Void>>();
        tokenList.add(new LocationTrackingActions(ctxt).stop());
        tokenList.add(new ActivityRecognitionActions(ctxt).stop());
        final Context fCtxt = ctxt;
        Tasks.whenAllSuccess(tokenList).addOnCompleteListener(task -> {
                String newState = targetState;
                if (task.isSuccessful()) {
                    setNewState(newState);
                    if (ConfigManager.getConfig(fCtxt).isSimulateUserInteraction()) {
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                null, fCtxt.getString(R.string.success_moving_new_state, newState));
                    }
                } else {
                    if (ConfigManager.getConfig(fCtxt).isSimulateUserInteraction()) {
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                null, fCtxt.getString(R.string.failed_moving_new_state,newState));
                    }
                }
        });
    }
}
