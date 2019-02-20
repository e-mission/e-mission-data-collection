package edu.berkeley.eecs.emission.cordova.tracker.location;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.Batch;
import com.google.android.gms.common.api.BatchResult;
import com.google.android.gms.common.api.BatchResultToken;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import java.util.LinkedList;
import java.util.List;

import edu.berkeley.eecs.emission.cordova.tracker.ConfigManager;
import edu.berkeley.eecs.emission.cordova.tracker.Constants;
import edu.berkeley.eecs.emission.cordova.tracker.sensors.BatteryUtils;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.NotificationHelper;
import edu.berkeley.eecs.emission.R;

import edu.berkeley.eecs.emission.cordova.tracker.location.actions.ActivityRecognitionActions;
import edu.berkeley.eecs.emission.cordova.tracker.location.actions.GeofenceActions;
import edu.berkeley.eecs.emission.cordova.tracker.location.actions.LocationTrackingActions;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.usercache.UserCacheFactory;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.Transition;

/**
 * Created by shankari on 9/12/15.
 */

public class TripDiaryStateMachineService extends Service implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
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

    private GoogleApiClient mApiClient = null;
    private String mCurrState = null;
    private String mTransition = null;
    private SharedPreferences mPrefs = null;

    public TripDiaryStateMachineService() {
        super();
    }

    @Override
    public void onCreate() {
        Log.i(this, TAG, "Service created. Initializing one-time variables!");
        /*
         * Need to initialize once per create.
         * http://stackoverflow.com/questions/29343922/googleapiclient-is-throwing-googleapiclient-is-not-connected-yet-after-onconne
         * https://github.com/e-mission/e-mission-data-collection/issues/132
         */
        // We create this here because for the activity lifecycle, we are supposed to
        // create the client in create, connect in start and disconnect in stop.
        // so the equivalent for us is to create in the onCreate method,
        mApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .addApi(ActivityRecognition.API)
                .build();

    }

    @Override
    public void onDestroy() {
        Log.d(this, TAG, "About to disconnect the api client");
        mApiClient.disconnect();
        Log.i(this, TAG, "Service destroyed. So long, suckers!");
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

        if (mApiClient.isConnected()) {
            Log.d(this, TAG, "client is already connected, can directly handle the action");
            handleAction(this, mApiClient, mCurrState, mTransition);
        } else {
        // And then connect to the client. All subsequent processing will be in the onConnected
        // method
            Log.d(this, TAG, "Launched connect to the google API client, returning from onStartCommand");
        mApiClient.connect();
        }

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

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.d(this, TAG, "onConnected("+connectionHint+") called");
        ConnectionResult locResult = mApiClient.getConnectionResult(LocationServices.API);
        ConnectionResult activityResult = mApiClient.getConnectionResult(ActivityRecognition.API);
        if (!locResult.isSuccess()) {
            if (locResult.hasResolution()) {
                NotificationHelper.createNotification(this, Constants.TRACKING_ERROR_ID, locResult.getErrorMessage(),
                        locResult.getResolution());
            } else {
                NotificationHelper.createNotification(this, Constants.TRACKING_ERROR_ID, locResult.getErrorMessage());
            }
        }
        if (!activityResult.isSuccess()) {
            if (activityResult.hasResolution()) {
                NotificationHelper.createNotification(this, Constants.TRACKING_ERROR_ID, activityResult.getErrorMessage(),
                        activityResult.getResolution());
            } else {
                NotificationHelper.createNotification(this, Constants.TRACKING_ERROR_ID, activityResult.getErrorMessage());
            }
        }

        handleAction(this, mApiClient, mCurrState, mTransition);

        // Note that it does NOT work to disconnect from here because the actions in the state
        // might happen asynchronously, and we disconnect too early, then the async callbacks
        // are never invoked. In particular, anything called from the "main/UI" thread, such as
        // the geofence creation, will FAIL if not invoked asynchronously (i.e. if await() is called).
        // It is the responsibility of the state handler to disconnect correctly.
        // If the state handler does not disconnect properly, the state machine will fail because
        // onConnect() is not invoked when connect() is invoked on an already connected client
        // TODO: This lead to missed messages if we receive a message while an async operation is ongoing!
        // Should really check isConnected() and handle it!
        // mApiClient.disconnect();
    }

    public static String getState(Context ctxt) {
        SharedPreferences sPrefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
        return sPrefs.getString(ctxt.getString(R.string.curr_state_key), ctxt.getString(R.string.state_start));
    }

    public static void restartFSMIfStartState(Context ctxt) {
        String START_STATE = ctxt.getString(R.string.state_start);
        String currState = getState(ctxt);
        Log.i(ctxt, TAG, "in restartFSMIfStartState, currState = "+currState);
        if (START_STATE.equals(currState)) {
            Log.i(ctxt, TAG, "in start state, sending initialize");
            ctxt.sendBroadcast(new Intent(ctxt.getString(R.string.transition_initialize)));
        }
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
        stopSelf();
    }

    @Override
    public void onConnectionSuspended(int cause) {
        String causeStr = "unknown";
        if (cause == GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST) {
            causeStr = "network lost";
        } else if (cause == GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
            causeStr = "network disconnected";
        }
        NotificationHelper.createNotification(this, STATE_IN_NUMBERS,
                "google API client connection suspended"+causeStr);
    }

    @Override
    public void onConnectionFailed(ConnectionResult cr) {
        NotificationHelper.createNotification(this, STATE_IN_NUMBERS,
                "google API client connection failed"+cr.toString());

    }

    private Intent getForegroundServiceIntent() {
        return new Intent(this, TripDiaryStateMachineForegroundService.class);
    }

    /*
     * Handles the transition based on the current state.
     * Assumes that the API client is already connected.
     * TODO: It looks like the various actions are actually pretty similar - they invoke a
     * method on the API and then wait for a callback. It seems like it should be possible
     * to write a generic action that takes the google API call and the broadcast intent
     * as parameters, makes the call, and issues the broadcast in the callback
     */
    private void handleAction(Context ctxt, GoogleApiClient apiClient, String currState, String actionString) {
        Log.d(this, TAG, "handleAction("+currState+", "+actionString+") calle");
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
            handleStart(ctxt, apiClient, actionString);
        } else if (LocationManager.MODE_CHANGED_ACTION.equals(actionString)) {
            // should we do a handleXXX() wrapper for this too?
            checkLocationSettings(ctxt, apiClient);
        } else if (currState.equals(ctxt.getString(R.string.state_start))) {
            handleStart(ctxt, apiClient, actionString);
        } else if (currState.equals(ctxt.getString(R.string.state_waiting_for_trip_start))) {
            handleTripStart(ctxt, apiClient, actionString);
        } else if (currState.equals(ctxt.getString(R.string.state_ongoing_trip))) {
            handleTripEnd(ctxt, apiClient, actionString);
        } else if (currState.equals(ctxt.getString(R.string.state_tracking_stopped))) {
            handleTrackingStopped(ctxt, apiClient, actionString);
        }
    }

    private void handleStart(final Context ctxt, final GoogleApiClient apiClient, String actionString) {
        Log.d(this, TAG, "TripDiaryStateMachineReceiver handleStarted(" + actionString + ") called");
        // Get current location
        if (actionString.equals(ctxt.getString(R.string.transition_initialize)) &&
                !mCurrState.equals(ctxt.getString(R.string.state_tracking_stopped))) {
            createGeofenceInThread(ctxt, apiClient, actionString);
        }

        // One would think that we don't need to deal with anything other than starting from the start
        // state, but we can be stuck in the start state for a while if it turns out that the geofence is
        // not created correctly. If the user forces us to stop tracking then, we still need to do it.
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

    public void handleTripStart(Context ctxt, final GoogleApiClient apiClient, final String actionString) {
        Log.d(this, TAG, "TripDiaryStateMachineReceiver handleTripStart(" + actionString + ") called");

        if (actionString.equals(ctxt.getString(R.string.transition_exited_geofence))) {
            // Delete geofence
            final List<BatchResultToken<Status>> tokenList = new LinkedList<BatchResultToken<Status>>();
            Batch.Builder resultBarrier = new Batch.Builder(apiClient);
            tokenList.add(resultBarrier.add(new GeofenceActions(ctxt, apiClient).remove()));
            tokenList.add(resultBarrier.add(new LocationTrackingActions(ctxt, apiClient).start()));
            tokenList.add(resultBarrier.add(new ActivityRecognitionActions(ctxt, apiClient).start()));
            // TODO: How to pass in the token list?
            // Also, the callback is currently the same for all of them, but could potentially be
            // different in the future once we add in failure handling because we may want to do
            // different things based on the different failure cases. If we don't do that, we should
            // refactor the callback to a common class. and then we need to pass in the token list to it
            // since it can't use the final variable
            final Context fCtxt = ctxt;
            resultBarrier.build().setResultCallback(new ResultCallback<BatchResult>() {
                @Override
                public void onResult(BatchResult batchResult) {
                    String newState = fCtxt.getString(R.string.state_ongoing_trip);
                    if (batchResult.getStatus().isSuccess()) {
                        setNewState(newState);
                        startService(getForegroundServiceIntent());
                        if (ConfigManager.getConfig(fCtxt).isSimulateUserInteraction()) {
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                "Success moving to "+newState);
                        }
                    } else {
                        if (batchResult.getStatus().hasResolution()) {
                            NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                    "Error " + batchResult.getStatus().getStatusCode()+" while creating geofence",
                                    batchResult.getStatus().getResolution());
                        } else {
                            NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                    "Error " + batchResult.getStatus().getStatusCode()+" while creating geofence");
                            checkLocationSettings(TripDiaryStateMachineService.this, mApiClient);
                        }
                        if (ConfigManager.getConfig(fCtxt).isSimulateUserInteraction()) {
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                    "Failed moving to "+newState);
                    }
                    }
                    Log.d(fCtxt, TAG, "About to disconnect the api client");
                    mApiClient.disconnect();
                }
            });
        }

        if(actionString.equals(ctxt.getString(R.string.transition_stop_tracking))) {
            // Delete geofence
            deleteGeofence(ctxt, apiClient, ctxt.getString(R.string.state_tracking_stopped));
        }

        if (actionString.equals(ctxt.getString(R.string.transition_tracking_error))) {
            /*
            NotificationHelper.createNotification(ctxt, Constants.TRACKING_ERROR_ID,
                    "Location tracking turned off. Please turn on for emission to work properly");
                    */
            Log.i(this, TAG, "Got tracking_error moving to start state");
            deleteGeofence(ctxt, mApiClient, ctxt.getString(R.string.state_start));
            setNewState(getString(R.string.state_start));
        }
    }

    public void handleTripEnd(final Context ctxt, final GoogleApiClient apiClient, final String actionString) {
        Log.d(this, TAG, "TripDiaryStateMachineReceiver handleTripEnd(" + actionString + ") called");
        if (actionString.equals(ctxt.getString(R.string.transition_stopped_moving))) {
            // Stopping location tracking
            new Thread(new Runnable() {
                @Override
                public void run() {
                    System.out.println("Running in new thread!!");
            final List<BatchResultToken<Status>> tokenList = new LinkedList<BatchResultToken<Status>>();
            Batch.Builder resultBarrier = new Batch.Builder(apiClient);
            tokenList.add(resultBarrier.add(new LocationTrackingActions(ctxt, apiClient).stop()));
            tokenList.add(resultBarrier.add(new ActivityRecognitionActions(ctxt, apiClient).stop()));
                    // TODO: change once we move to chained promises
                    PendingResult<Status> createGeofenceResult =
                    new GeofenceActions(ctxt, apiClient).create();
            if (createGeofenceResult != null) {
                tokenList.add(resultBarrier.add(createGeofenceResult));
            }
                    final boolean geofenceCreationPossible = createGeofenceResult != null;
            final Context fCtxt = ctxt;
            resultBarrier.build().setResultCallback(new ResultCallback<BatchResult>() {
                @Override
                public void onResult(BatchResult batchResult) {
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
                    if (batchResult.getStatus().isSuccess()) {
                        setNewState(newState);
                        stopService(getForegroundServiceIntent());
                        if (ConfigManager.getConfig(ctxt).isSimulateUserInteraction()) {
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                "Success moving to "+newState);
                        }
                    } else {
                        if (batchResult.getStatus().hasResolution()) {
                            NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                    "Error " + batchResult.getStatus().getStatusCode()+" while creating geofence",
                                    batchResult.getStatus().getResolution());
                        } else {
                            NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                    "Error " + batchResult.getStatus().getStatusCode()+" while creating geofence");
                            checkLocationSettings(TripDiaryStateMachineService.this, mApiClient);
                        }
                        if (ConfigManager.getConfig(ctxt).isSimulateUserInteraction()) {
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                "Failed moving to "+newState);
                    }
                    }
                    Log.d(fCtxt, TAG, "About to disconnect the api client");
                    mApiClient.disconnect();
                }
            });
        }
            }).start();
        }

        if (actionString.equals(ctxt.getString(R.string.transition_stop_tracking))) {
            stopAll(ctxt, apiClient, ctxt.getString(R.string.state_tracking_stopped));
        }

        if (actionString.equals(ctxt.getString(R.string.transition_tracking_error))) {
            /*
            NotificationHelper.createNotification(ctxt, Constants.TRACKING_ERROR_ID,
                    "Location tracking turned off. Please turn on for emission to work properly");
                    */
            Log.i(this, TAG, "Got tracking_error moving to start state");
            // should I stop everything? maybe to be consistent with the start state
            stopAll(this, mApiClient, ctxt.getString(R.string.state_start));
            setNewState(getString(R.string.state_start));
        }
    }

    private void handleTrackingStopped(final Context ctxt, final GoogleApiClient apiClient, String actionString) {
        Log.d(this, TAG, "TripDiaryStateMachineReceiver handleTrackingStopped(" + actionString + ") called");
        if (actionString.equals(ctxt.getString(R.string.transition_start_tracking))) {
            createGeofenceInThread(ctxt, apiClient, actionString);
        } else {
            stopAll(ctxt, apiClient, ctxt.getString(R.string.state_tracking_stopped));
        }
        if (actionString.equals(ctxt.getString(R.string.transition_tracking_error))) {
            Log.i(this, TAG, "Tracking manually turned off, no need to prompt for location");
        }
    }

    /*
      * This is basically what we used to do as part of the initialize transition, but we now do it from initialize
      * or from start_tracking so let's refactor to avoid sending a broadcast while handling a previous broadcast.
      * This may help with the weird tracking issues we have seen, like
      *
     */

    private void createGeofenceInThread(final Context ctxt, final GoogleApiClient apiClient,
                                        String actionString) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("Running in new thread!!");
                final Context fCtxt = ctxt;
                final GoogleApiClient fApiClient = apiClient;
                // TODO: Ideally, new GeofenceActions would return a chained pending result.
                // Then, we would just wait for the combined result callback and all would be well
                // But it looks like the pending result chaining is not supported in the current
                // version of the google play services API. We could chain callbacks here, but then
                // we won't be able to deal with the common case (last location present) and the
                // uncommon case (last location not present) in a unified fashion. We would need
                // one callback for the first and two for the second.
                // So for now, we punt and simply start the geofence creation in a separate
                // (non-UI thread). Revisit this later once chaining is supported.
                com.google.android.gms.common.api.PendingResult<Status> createGeofenceResult =
                        new GeofenceActions(ctxt, apiClient).create();
                if (createGeofenceResult != null) {
                    createGeofenceResult.setResultCallback(new ResultCallback<Status>() {
                        @Override
                        public void onResult(Status status) {
                            String newState = fCtxt.getString(R.string.state_waiting_for_trip_start);
                            if (status.isSuccess()) {
                                setNewState(newState);
                                if (ConfigManager.getConfig(ctxt).isSimulateUserInteraction()) {
                                    NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                            "Success moving to " + newState);
                                }
                            } else {
                                if (status.hasResolution()) {
                                    NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                            "Error " + status.getStatusCode()+" while creating geofence", status.getResolution());
                                } else {
                                    NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                            "Error " + status.getStatusCode()+" while creating geofence");
                                    checkLocationSettings(TripDiaryStateMachineService.this, mApiClient);
                                }
                                if (ConfigManager.getConfig(fCtxt).isSimulateUserInteraction()) {
                                    NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                            "Failed moving to " + newState);
                                }
                            }
                            Log.d(fCtxt, TAG, "About to disconnect the api client");
                            fApiClient.disconnect();
                        }
                    });
                } else {
                    // Geofence was not created properly. let's generate a tracking error so that the user is notified
                    checkLocationSettings(fCtxt, fApiClient);
                }
            }
        }).start();
    }

    private void deleteGeofence(Context ctxt, GoogleApiClient apiClient, final String targetState) {
            final List<BatchResultToken<Status>> tokenList = new LinkedList<BatchResultToken<Status>>();
            Batch.Builder resultBarrier = new Batch.Builder(apiClient);
        tokenList.add(resultBarrier.add(new GeofenceActions(ctxt, apiClient).remove()));
            final Context fCtxt = ctxt;
            resultBarrier.build().setResultCallback(new ResultCallback<BatchResult>() {
                @Override
                public void onResult(BatchResult batchResult) {
                    String newState = targetState;
                    if (batchResult.getStatus().isSuccess()) {
                        setNewState(newState);
                    if (ConfigManager.getConfig(fCtxt).isSimulateUserInteraction()) {
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                "Success moving to " + newState);
                        }
                    } else {
                    if (batchResult.getStatus().hasResolution()) {
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                "Error " + batchResult.getStatus().getStatusCode() + " while creating geofence",
                                batchResult.getStatus().getResolution());
                    } else {
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                "Error " + batchResult.getStatus().getStatusCode() + " while creating geofence");
                        checkLocationSettings(TripDiaryStateMachineService.this, mApiClient);
                    }
                    if (ConfigManager.getConfig(fCtxt).isSimulateUserInteraction()) {
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                "Failed moving to " + newState);
                    }
                    }
                    Log.d(fCtxt, TAG, "About to disconnect the api client");
                    mApiClient.disconnect();
                }
            });

    }

    private void stopAll(Context ctxt, GoogleApiClient apiClient, final String targetState) {
            // We don't really care about any other transitions, but if we are getting random transitions
            // in this state, may be good to turn everything off
            final List<BatchResultToken<Status>> tokenList = new LinkedList<BatchResultToken<Status>>();
            Batch.Builder resultBarrier = new Batch.Builder(apiClient);
            tokenList.add(resultBarrier.add(new GeofenceActions(ctxt, apiClient).remove()));
            tokenList.add(resultBarrier.add(new LocationTrackingActions(ctxt, apiClient).stop()));
            tokenList.add(resultBarrier.add(new ActivityRecognitionActions(ctxt, apiClient).stop()));
            final Context fCtxt = ctxt;
            resultBarrier.build().setResultCallback(new ResultCallback<BatchResult>() {
                @Override
                public void onResult(BatchResult batchResult) {
                    String newState = targetState;
                    if (batchResult.getStatus().isSuccess()) {
                        setNewState(newState);
                        stopService(getForegroundServiceIntent());
                    if (ConfigManager.getConfig(fCtxt).isSimulateUserInteraction()) {
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                "Success moving to "+newState);
                        }
                    } else {
                    if (ConfigManager.getConfig(fCtxt).isSimulateUserInteraction()) {
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                "Failed moving to "+newState);
                    }
                    }
                    Log.d(fCtxt, TAG, "About to disconnect the api client");
                    mApiClient.disconnect();
                }
            });
        }


    public static void checkLocationSettings(final Context ctxt, GoogleApiClient apiClient) {
        LocationRequest request = new LocationTrackingActions(ctxt, apiClient).getLocationRequest();
        Log.d(ctxt, TAG, "Checking location settings for request "+request);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(request);

        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(apiClient, builder.build());
        Log.d(ctxt, TAG, "Got back result "+result);
        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(LocationSettingsResult result) {
                Log.d(ctxt, TAG, "isLocationUsable() "+result.getLocationSettingsStates().isLocationUsable());
                final Status status = result.getStatus();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        // All location settings are satisfied. The client can initialize location
                        // requests here.
                        Log.i(ctxt, TAG, "All settings are valid, checking current state");
                        NotificationHelper.cancelNotification(ctxt, Constants.TRACKING_ERROR_ID);
                        restartFSMIfStartState(ctxt);
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied. But could be fixed by showing the user
                        // a dialog.
                        ctxt.sendBroadcast(new Intent(ctxt.getString(R.string.transition_tracking_error)));
                        if (status.hasResolution()) {
                            NotificationHelper.createNotification(ctxt, Constants.TRACKING_ERROR_ID,
                                    "Error " + status.getStatusCode() + " in location settings",
                                    status.getResolution());
                        } else {
                            NotificationHelper.createNotification(ctxt, Constants.TRACKING_ERROR_ID,
                                    "Error " + status.getStatusCode() + " in location settings");
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        // Location settings are not satisfied. However, we have no way to fix the
                        // settings so we won't show the dialog.
                        ctxt.sendBroadcast(new Intent(ctxt.getString(R.string.transition_tracking_error)));
                        NotificationHelper.createNotification(ctxt, Constants.TRACKING_ERROR_ID,
                                "Error " + status.getStatusCode() + " in location settings");
                        break;
                    default:
                        ctxt.sendBroadcast(new Intent(ctxt.getString(R.string.transition_tracking_error)));
                        NotificationHelper.createNotification(ctxt, Constants.TRACKING_ERROR_ID,
                                "Unknown error while reading location, please check your settings");
                }
            }
        });
    }
}
