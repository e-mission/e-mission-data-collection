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

import org.json.JSONException;
import org.json.JSONObject;

import edu.berkeley.eecs.emission.cordova.serversync.ServerSyncUtil;
import edu.berkeley.eecs.emission.cordova.tracker.ConfigManager;
import edu.berkeley.eecs.emission.cordova.tracker.verification.SensorControlBackgroundChecker;
import edu.berkeley.eecs.emission.cordova.tracker.ExplicitIntent;
import edu.berkeley.eecs.emission.cordova.tracker.sensors.BatteryUtils;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.NotificationHelper;
import edu.berkeley.eecs.emission.R;


import edu.berkeley.eecs.emission.cordova.tracker.location.actions.ActivityRecognitionActions;
import edu.berkeley.eecs.emission.cordova.tracker.location.actions.OPGeofenceExitActivityActions;
import edu.berkeley.eecs.emission.cordova.tracker.location.actions.GeofenceActions;
import edu.berkeley.eecs.emission.cordova.tracker.location.actions.LocationTrackingActions;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.usercache.UserCacheFactory;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.Transition;

/**
 * Created by shankari on 9/12/15.
 */

public class TripDiaryStateMachineService extends Service {
    public static String TAG = "TripDiaryStateMachineService";
    private static String OP_GEOFENCE_CFG = "OP_GEOFENCE_CFG";

    private static int STATE_IN_NUMBERS = 78283;

    private String mCurrState = null;
    private String mTransition = null;
    private SharedPreferences mPrefs = null;
    private ForegroundServiceComm mComm = null;

    public TripDiaryStateMachineService() {
        super();
    }

    @Override
    public void onCreate() {
        Log.i(this, TAG, "Service created. Initializing one-time variables!");
        mComm = new ForegroundServiceComm(this);
        /*
         * Need to initialize once per create.
         */
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
        return START_REDELIVER_INTENT;
    }

    public static String getState(Context ctxt) {
        SharedPreferences sPrefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
        return sPrefs.getString(ctxt.getString(R.string.curr_state_key), ctxt.getString(R.string.state_start));
    }

    public void setNewState(String newState, boolean doChecks) {
        Log.d(this, TAG, "newState after handling action is "+newState);
        SharedPreferences.Editor prefsEditor =
                PreferenceManager.getDefaultSharedPreferences(this).edit();
        prefsEditor.putString(this.getString(R.string.curr_state_key), newState);
        prefsEditor.commit();
        Log.d(this, TAG, "newState saved in prefManager is "+
                PreferenceManager.getDefaultSharedPreferences(this).getString(
                        this.getString(R.string.curr_state_key), "not found"));
        mComm.setNewState(newState);
        // Let's check the location settings every time we change the state instead of only on failure
        // This makes the rest of the code much simpler, allows us to catch issues as quickly as possible,
        // and
        if (doChecks) {
            SensorControlBackgroundChecker.checkAppState(TripDiaryStateMachineService.this);
        }
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
        // This try/catch block can be removed in the next release, once all the
        // geofence config objects have been removed
        try {
            JSONObject opGeofenceCfg = UserCacheFactory.getUserCache(this).getLocalStorage(OP_GEOFENCE_CFG, false);
            if (opGeofenceCfg != null) {
                Log.i(this, TAG, "opGeofenceCfg != null, opGeofence enabled, "+
                    " deleting entry to cleanup");
                UserCacheFactory.getUserCache(this).removeLocalStorage(OP_GEOFENCE_CFG);
            };
        } catch (JSONException e) {
            Log.i(this, TAG, "JSONException while accessing geofence cfg "+
                " skipping delete");
        }
        if (actionString.equals(ctxt.getString(R.string.transition_initialize))) {
            handleStart(ctxt, actionString);
        } else if (currState.equals(ctxt.getString(R.string.state_start))) {
            handleStart(ctxt, actionString);
        } else if (currState.equals(ctxt.getString(R.string.state_waiting_for_trip_start))) {
            handleTripStart(ctxt, actionString);
        } else if (currState.equals(ctxt.getString(R.string.state_ongoing_trip))) {
            handleTripEnd(ctxt, actionString);
        } else if (currState.equals(ctxt.getString(R.string.state_tracking_stopped))) {
            handleTrackingStopped(ctxt, actionString);
        }
        Log.d(this, TAG, "handleAction("+currState+", "+actionString+") completed, waiting for async operations to complete");
    }

    private void handleStart(final Context ctxt, String actionString) {
        Log.d(this, TAG, "TripDiaryStateMachineReceiver handleStarted(" + actionString + ") called");

        // Get current location
        if (actionString.equals(ctxt.getString(R.string.transition_initialize)) &&
                !mCurrState.equals(ctxt.getString(R.string.state_tracking_stopped))) {
            // create the significant motion callback first since it is
            // synchronous and the geofence is not
            createGeofenceInThread(ctxt, actionString);
            return;
            // we will wait for async geofence creation to complete
        }

        // One would think that we don't need to deal with anything other than starting from the start
        // state, but we can be stuck in the start state for a while if it turns out that the geofence is
        // not created correctly. If the user forces us to stop tracking then, we still need to do it.
        if (actionString.equals(ctxt.getString(R.string.transition_stop_tracking))) {
            // Haven't started anything yet (that's why we are in the start state).
            // just move to the stop tracking state
            String newState = ctxt.getString(R.string.state_tracking_stopped);
            setNewState(newState, true);
            return;
        }

        if (actionString.equals(ctxt.getString(R.string.transition_tracking_error))) {
            /*
            NotificationHelper.createNotification(ctxt, Constants.TRACKING_ERROR_ID,
                    "Location tracking turned off. Please turn on for emission to work properly");
                    */
            Log.i(this, TAG, "Already in the start state, so going to stay there");
            setNewState(mCurrState, false);
            return;
        }

        // if we got here, this must be a transition that we don't handle
        Log.i(this, TAG, "Found unhandled transition "+actionString+" staying in current state ");
        boolean checkSettings = !mCurrState.equals(ctxt.getString(R.string.state_tracking_stopped));
        Log.i(this, TAG, "curr state = "+mCurrState+" checkSettings = "+checkSettings);
        setNewState(mCurrState, checkSettings);
    }

    public void handleTripStart(Context ctxt, final String actionString) {
        Log.d(this, TAG, "TripDiaryStateMachineReceiver handleTripStart(" + actionString + ") called");

        if (actionString.equals(ctxt.getString(R.string.transition_exited_geofence))) {
            // Delete geofence
            // we cannot add null elements to the token list.
            // the LocationTracking start action can now return null
            // so we need to handle it similar to the createGeofence in handleTripEnd
            final List<Task<Void>> tokenList = new LinkedList<Task<Void>>();
            tokenList.add(new GeofenceActions(ctxt).remove());
                tokenList.add(new OPGeofenceExitActivityActions(ctxt).stop());
            tokenList.add(new ActivityRecognitionActions(ctxt).start());
            Task<Void> locationTrackingResult = new LocationTrackingActions(ctxt).start();
            if (locationTrackingResult != null) {
              tokenList.add(locationTrackingResult);
            } else {
                // if we can't turn on the location tracking, we may as well not start the activity
                // recognition
                tokenList.remove(1);
            }
            final boolean locationTrackingPossible = locationTrackingResult != null;

            // TODO: How to pass in the token list?
            // Also, the callback is currently the same for all of them, but could potentially be
            // different in the future once we add in failure handling because we may want to do
            // different things based on the different failure cases. If we don't do that, we should
            // refactor the callback to a common class. and then we need to pass in the token list to it
            // since it can't use the final variable
            final Context fCtxt = ctxt;
            Tasks.whenAllComplete(tokenList).addOnCompleteListener(task -> {
              List<Task<?>> resultList = task.getResult();
                    String newState;
                    if (locationTrackingPossible) {
                        newState = fCtxt.getString(R.string.state_ongoing_trip);
                    } else {
                        // If we are not going to be able to start location tracking, then we don't
                        // want to go to ongoing_trip, because then we will never exit
                        // from it. Instead, we go to state_start so that we will try to get
                        // out of it at every sync.
                        newState = fCtxt.getString(R.string.state_start);
                    }
              if (TripDiaryStateMachineService.isAllSuccessful(resultList)) {
                        if (ConfigManager.getConfig(fCtxt).isSimulateUserInteraction()) {
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                null, fCtxt.getString(R.string.success_moving_new_state, newState));
                        }
                        setNewState(newState, true);
                    } else {
                if (locationTrackingPossible && resultList.get(2).isSuccessful()) {
                                // the location tracking started successfully
                                setNewState(fCtxt.getString(R.string.state_ongoing_trip), true);
                            } else {
                                setNewState(fCtxt.getString(R.string.state_start), true);
                            }
                            // NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                            //         "Error " + batchResult.getStatus().getStatusCode()+" while creating geofence");
                            // this will perform some additional checks which we should wait for
                        }
                        if (ConfigManager.getConfig(fCtxt).isSimulateUserInteraction()) {
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                null, fCtxt.getString(R.string.failed_moving_new_state,newState));
              } // both branches have called setState or are waiting for sth else
            }); // listener end
            return; // handled the transition, returning
        }

        if(actionString.equals(ctxt.getString(R.string.transition_stop_tracking))) {
            // Delete geofence
            deleteGeofence(ctxt, ctxt.getString(R.string.state_tracking_stopped));
            return;
            // when this completes, it should generate transitions and move to the final state
        }

        if (actionString.equals(ctxt.getString(R.string.transition_tracking_error))) {
            /*
            NotificationHelper.createNotification(ctxt, Constants.TRACKING_ERROR_ID,
                    "Location tracking turned off. Please turn on for emission to work properly");
                    */
            Log.i(this, TAG, "Got tracking_error moving to start state");
            deleteGeofence(ctxt, ctxt.getString(R.string.state_start));
            return;
            // when this completes, it should generate transitions and move to the final state
        }

        // if we got here, this must be a transition that we don't handle
        Log.i(this, TAG, "Found unhandled transition "+actionString+" staying in current state ");
        setNewState(mCurrState, true);
    }

    public void handleTripEnd(final Context ctxt, final String actionString) {
        Log.d(this, TAG, "TripDiaryStateMachineReceiver handleTripEnd(" + actionString + ") called");

        if (actionString.equals(ctxt.getString(R.string.transition_stopped_moving))) {
            // Stopping location tracking
            new Thread(new Runnable() {
                @Override
                public void run() {
                    System.out.println("Running in new thread!!");
            final List<Task<Void>> tokenList = new LinkedList<Task<Void>>();
            tokenList.add(new LocationTrackingActions(ctxt).stop());
            tokenList.add(new ActivityRecognitionActions(ctxt).stop());
                    // TODO: change once we move to chained promises

            Task<Void> createGeofenceResult = new GeofenceActions(ctxt).create();
            if (createGeofenceResult != null) {
                tokenList.add(createGeofenceResult);
            }
                tokenList.add(new OPGeofenceExitActivityActions(ctxt).start());
            final boolean geofenceCreationPossible = createGeofenceResult != null;
            final Context fCtxt = ctxt;
            Tasks.whenAllComplete(tokenList).addOnCompleteListener(task -> {
                List<Task<?>> resultList = task.getResult();
                            String newState;
                            if (geofenceCreationPossible) {
                                newState = fCtxt.getString(R.string.state_waiting_for_trip_start);
                            } else {
                                // If we are not going to be able to create a geofence, then we don't
                                // want to go to waiting_for_trip_state, because then we will never exit
                                // from it. Instead, we go to state_start so that we will try to get
                                // out of it at every sync.
                                newState = fCtxt.getString(R.string.state_start);
                            }
                if (TripDiaryStateMachineService.isAllSuccessful(resultList)) {
                        if (ConfigManager.getConfig(ctxt).isSimulateUserInteraction()) {
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                null, fCtxt.getString(R.string.success_moving_new_state, newState));
                        }
                        setNewState(newState, true);
                    } else {
                        if (!resultList.get(0).isSuccessful()) {
                                // the location tracking stop failed
                                setNewState(fCtxt.getString(R.string.state_ongoing_trip), true);
                            } else if (geofenceCreationPossible &&
                            resultList.get(2).isSuccessful()) {
                                setNewState(fCtxt.getString(R.string.state_waiting_for_trip_start), true);
                            } else {
                                // geofence creation is not possible or it failed but location tracking
                                // did successfully stop. Let's go to the start state
                                setNewState(fCtxt.getString(R.string.state_start), true);
                            }
                            // NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                            //        "Error " + batchResult.getStatus().getStatusCode()+" while creating geofence");
                            // let's mark this operation as done since the other one is static
                        // markOngoingOperationFinished();
                            SensorControlBackgroundChecker.checkAppState(TripDiaryStateMachineService.this);
                            // will wait for async call to complete
                        }
                        if (ConfigManager.getConfig(ctxt).isSimulateUserInteraction()) {
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                null, fCtxt.getString(R.string.failed_moving_new_state, newState));
                    }
            });
            
            // Sync data after trip end
            ServerSyncUtil.syncData(ctxt);
        }
            }).start();
            return;
        }

        if (actionString.equals(ctxt.getString(R.string.transition_stop_tracking))) {
            stopAll(ctxt, ctxt.getString(R.string.state_tracking_stopped));
            return;
            // will wait for stopAll to set the state
        }

        if (actionString.equals(ctxt.getString(R.string.transition_tracking_error))) {
            /*
            NotificationHelper.createNotification(ctxt, Constants.TRACKING_ERROR_ID,
                    "Location tracking turned off. Please turn on for emission to work properly");
                    */
            Log.i(this, TAG, "Got tracking_error moving to start state");
            // should I stop everything? maybe to be consistent with the start state
            stopAll(this, ctxt.getString(R.string.state_start));
            return;
            // ditto
        }

        // if we got here, this must be a transition that we don't handle
        Log.i(this, TAG, "Found unhandled transition "+actionString+" staying in current state ");
        setNewState(mCurrState, true);
    }

    private void handleTrackingStopped(final Context ctxt, String actionString) {
        Log.d(this, TAG, "TripDiaryStateMachineReceiver handleTrackingStopped(" + actionString + ") called");
        if (actionString.equals(ctxt.getString(R.string.transition_start_tracking))) {
            ctxt.sendBroadcast(new ExplicitIntent(ctxt, R.string.transition_initialize));
            setNewState(ctxt.getString(R.string.state_start), true);
            return;
            // createGeofenceInThread(ctxt, apiClient, actionString);
        } else {
            // we should have stopped everything when we got to this state,
            // but let's just stop them all again anyway to make sure that
            // they are really stopped and to provide a backstop for any
            // error conditions
            stopAll(ctxt, ctxt.getString(R.string.state_tracking_stopped));
            if (actionString.equals(ctxt.getString(R.string.transition_tracking_error))) {
                Log.i(this, TAG, "Tracking manually turned off, no need to prompt for location");
            }
            return;
        }
    }

    /*
      * This is basically what we used to do as part of the initialize transition, but we now do it from initialize
      * or from start_tracking so let's refactor to avoid sending a broadcast while handling a previous broadcast.
      * This may help with the weird tracking issues we have seen, like
      *
     */

    private void createGeofenceInThread(final Context ctxt,
                                        String actionString) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("Running in new thread!!");
                final Context fCtxt = ctxt;
                // TODO: Ideally, new GeofenceActions would return a chained pending result.
                // Then, we would just wait for the combined result callback and all would be well
                // But it looks like the pending result chaining is not supported in the current
                // version of the google play services API. We could chain callbacks here, but then
                // we won't be able to deal with the common case (last location present) and the
                // uncommon case (last location not present) in a unified fashion. We would need
                // one callback for the first and two for the second.
                // So for now, we punt and simply start the geofence creation in a separate
                // (non-UI thread). Revisit this later once chaining is supported.
                final List<Task<Void>> tokenList = new LinkedList<Task<Void>>();
                Task<Void> createGeofenceResult =
                        new GeofenceActions(ctxt).create();
                if (createGeofenceResult != null) {
                    tokenList.add(createGeofenceResult);
                } else {
                    // Geofence was not created properly. let's make an async call that will generate its
                    // own state change
                    // let's mark this operation as done since the other one is static
                    // markOngoingOperationFinished();
                    SensorControlBackgroundChecker.checkAppState(fCtxt);
                }
                    tokenList.add(new OPGeofenceExitActivityActions(ctxt).start());
                Tasks.whenAllComplete(tokenList).addOnCompleteListener(task -> {
                            String newState = fCtxt.getString(R.string.state_waiting_for_trip_start);
                            if (task.isSuccessful()) {
                                setNewState(newState, true);
                                if (ConfigManager.getConfig(ctxt).isSimulateUserInteraction()) {
                                    NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                            null, fCtxt.getString(R.string.success_moving_new_state, newState));
                                }
                            } else {
                                    Log.e(fCtxt, TAG, "error while creating geofence, staying in the current state");
                                    Log.exception(fCtxt, TAG, task.getException());
                                    setNewState(mCurrState, true);

                                    // NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                    //        "Error " + status.getStatusCode()+" while creating geofence");
                                    // let's mark this operation as done since the other one is static
                                    // markOngoingOperationFinished();
                                }
                                if (ConfigManager.getConfig(fCtxt).isSimulateUserInteraction()) {
                                    NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                            null, fCtxt.getString(R.string.failed_moving_new_state, newState));
                                }
                    });
            }
        }).start();
    }

    private void deleteGeofence(Context ctxt, final String targetState) {
        final List<Task<Void>> tokenList = new LinkedList<Task<Void>>();
        tokenList.add(new GeofenceActions(ctxt).remove());
            tokenList.add(new OPGeofenceExitActivityActions(ctxt).stop());
            final Context fCtxt = ctxt;
            Tasks.whenAllComplete(tokenList).addOnCompleteListener(task -> {
              List<Task<?>> resultList = task.getResult();
                    String newState = targetState;
                    if (TripDiaryStateMachineService.isAllSuccessful(resultList)) {
                        setNewState(newState, true);
                    if (ConfigManager.getConfig(fCtxt).isSimulateUserInteraction()) {
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                null, fCtxt.getString(R.string.success_moving_new_state, newState));
                        }
                    } else {
                            setNewState(mCurrState, true);
                        // markOngoingOperationFinished();
                        SensorControlBackgroundChecker.checkAppState(TripDiaryStateMachineService.this);
                    }
                    if (ConfigManager.getConfig(fCtxt).isSimulateUserInteraction()) {
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                null, fCtxt.getString(R.string.failed_moving_new_state, newState));
                    }
            });
    }

    private void stopAll(Context ctxt, final String targetState) {
            // We don't really care about any other transitions, but if we are getting random transitions
            // in this state, may be good to turn everything off
            final List<Task<Void>> tokenList = new LinkedList<Task<Void>>();
            tokenList.add(new GeofenceActions(ctxt).remove());
                tokenList.add(new OPGeofenceExitActivityActions(ctxt).stop());
            tokenList.add(new LocationTrackingActions(ctxt).stop());
            tokenList.add(new ActivityRecognitionActions(ctxt).stop());
            final Context fCtxt = ctxt;
            Tasks.whenAllComplete(tokenList).addOnCompleteListener(task -> {
              List<Task<?>> resultList = task.getResult();
                    String newState = targetState;
                    if (TripDiaryStateMachineService.isAllSuccessful(resultList)) {
                    if (ConfigManager.getConfig(fCtxt).isSimulateUserInteraction()) {
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                null, fCtxt.getString(R.string.success_moving_new_state, newState));
                        }
                        setNewState(newState, false);
                    } else {
                    if (ConfigManager.getConfig(fCtxt).isSimulateUserInteraction()) {
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                null, fCtxt.getString(R.string.failed_moving_new_state, newState));
                    }

                        if (!resultList.get(1).isSuccessful()) {
                            // the location tracking stop failed
                            setNewState(fCtxt.getString(R.string.state_ongoing_trip), false);
                        } else {
                            setNewState(newState, false);
                        }
                    }
            });
        }

    protected static boolean isAllSuccessful(List<Task<?>> taskList) {
      boolean result = true;
      for (Task t:taskList) {
        result = result & t.isSuccessful();
      }
      return result;
    }
}
