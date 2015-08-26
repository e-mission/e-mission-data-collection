package edu.berkeley.eecs.cfc_tracker.location;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import edu.berkeley.eecs.cfc_tracker.log.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.Batch;
import com.google.android.gms.common.api.BatchResult;
import com.google.android.gms.common.api.BatchResultToken;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.LocationServices;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import edu.berkeley.eecs.cfc_tracker.NotificationHelper;
import edu.berkeley.eecs.cfc_tracker.R;
import edu.berkeley.eecs.cfc_tracker.location.actions.ActivityRecognitionActions;
import edu.berkeley.eecs.cfc_tracker.location.actions.GeofenceActions;
import edu.berkeley.eecs.cfc_tracker.location.actions.LocationTrackingActions;
import edu.berkeley.eecs.cfc_tracker.usercache.UserCacheFactory;
import edu.berkeley.eecs.cfc_tracker.wrapper.Transition;

public class TripDiaryStateMachineReceiver extends BroadcastReceiver
	implements ConnectionCallbacks, OnConnectionFailedListener {

	public static Set<String> validTransitions = null;
    private static int STATE_IN_NUMBERS = 78283;

	private static String TAG = "TripDiaryStateMachineReceiver";
	private static String CURR_STATE_KEY = "TripDiaryCurrState";
	
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
	private Context mContext = null;

    public TripDiaryStateMachineReceiver() {
        // The automatically created receiver needs a default constructor
        android.util.Log.i(TAG, "noarg constructor called");
    }

	public TripDiaryStateMachineReceiver(Context context) {
		Log.i(mContext, TAG, "TripDiaryStateMachineReceiver constructor called");
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putString(CURR_STATE_KEY, null);
        editor.apply();
    }

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.i(mContext, TAG, "TripDiaryStateMachineReciever onReceive("+context+", "+intent+") called");
		// Populate all the instance variables to determine the transition
        mContext = context;
		mTransition = intent.getAction();
        // Next two calls copied over from the constructor, figure out if this is the best place to
        // put them
        validTransitions = new HashSet<String>(Arrays.asList(new String[]{
                context.getString(R.string.transition_initialize),
                context.getString(R.string.transition_exited_geofence),
                context.getString(R.string.transition_stopped_moving),
                context.getString(R.string.transition_stop_tracking)
        }));

        // We create this here because for the activity lifecycle, we are supposed to
        // create the client in create, connect in start and disconnect in stop.
        // so the equivalent for us is to create in the constructor,
        mApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .addApi(ActivityRecognition.API)
                .build();

        if (!validTransitions.contains(mTransition)) {
        	Log.e(mContext, TAG, "Received unknown action "+intent.getAction()+" ignoring");
        	return;
        }
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mCurrState = mPrefs.getString(CURR_STATE_KEY, context.getString(R.string.state_start));
        Log.d(mContext, TAG, "after reading from the prefs, the current state is "+mCurrState);
        UserCacheFactory.getUserCache(mContext).putMessage(R.string.key_usercache_transition,
                new Transition(mCurrState, mTransition));
        // And then connect to the client. All subsequent processing will be in the onConnected
        // method
        // TODO: Also figure out whether it is best to create it here or in the constructor.
        // If it in the constructor, where do we get the context from?
		mApiClient.connect();
	}
	
	@Override
	public void onConnected(Bundle connectionHint) {
		Log.d(mContext, TAG, "TripDiaryStateMachineReceiver onConnected("+connectionHint+") called");
		handleAction(mContext, mApiClient, mCurrState, mTransition);

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
        return sPrefs.getString(CURR_STATE_KEY, ctxt.getString(R.string.state_start));
    }

    public void setNewState(String newState) {
        Log.d(mContext, TAG, "newState after handling action is "+newState);
        SharedPreferences.Editor prefsEditor =
                PreferenceManager.getDefaultSharedPreferences(mContext).edit();
        prefsEditor.putString(CURR_STATE_KEY, newState);
        prefsEditor.apply();
        Log.d(mContext, TAG, "newState saved in prefManager is "+
            PreferenceManager.getDefaultSharedPreferences(mContext).getString(
                    CURR_STATE_KEY, "not found"));
    }

	@Override
	public void onConnectionSuspended(int arg0) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onConnectionFailed(ConnectionResult arg0) {
		// TODO Auto-generated method stub
		
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
		Log.d(mContext, TAG, "TripDiaryStateMachineReceiver handleAction("+currState+", "+actionString+") called");
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
        if (actionString.equals(ctxt.getString(R.string.transition_initialize))) {
            handleStart(ctxt, apiClient, actionString);
        } else if (currState.equals(ctxt.getString(R.string.state_start))) {
			handleStart(ctxt, apiClient, actionString);
        } else if (currState.equals(ctxt.getString(R.string.state_waiting_for_trip_start))) {
            handleTripStart(ctxt, apiClient, actionString);
        } else if (currState.equals(ctxt.getString(R.string.state_ongoing_trip))) {
            handleTripEnd(ctxt, apiClient, actionString);
        }
	}
	
	private void handleStart(Context ctxt, GoogleApiClient apiClient, String actionString) {
		Log.d(mContext, TAG, "TripDiaryStateMachineReceiver handleStarted("+actionString+") called");
		// Get current location
		if (actionString.equals(ctxt.getString(R.string.transition_initialize))) {
            final Context fCtxt = ctxt;
            com.google.android.gms.common.api.PendingResult<Status> createGeofenceResult =
            new GeofenceActions(ctxt, apiClient).create();
            if (createGeofenceResult != null) {
                createGeofenceResult.setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        String newState = fCtxt.getString(R.string.state_waiting_for_trip_start);
                        if (status.isSuccess()) {
                            setNewState(newState);
                            NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                    "Success moving to " + newState);
                        } else {
                            NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                    "Failed moving to " + newState);
                        }
                        mApiClient.disconnect();
                    }
                });
            }
        }
    }

    public void handleTripStart(Context ctxt, GoogleApiClient apiClient, String actionString) {
        Log.d(mContext, TAG, "TripDiaryStateMachineReceiver handleTripStart("+actionString+") called");
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
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                "Success moving to "+newState);
                    } else {
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                "Failed moving to "+newState+" failed");
                    }
                    mApiClient.disconnect();
                }
            });
        }
        if(actionString.equals(ctxt.getString(R.string.transition_stop_tracking))) {
            // Delete geofence
            final List<BatchResultToken<Status>> tokenList = new LinkedList<BatchResultToken<Status>>();
            Batch.Builder resultBarrier = new Batch.Builder(apiClient);
            tokenList.add(resultBarrier.add(new GeofenceActions(ctxt, apiClient).remove()));
            final Context fCtxt = ctxt;
            resultBarrier.build().setResultCallback(new ResultCallback<BatchResult>() {
                @Override
                public void onResult(BatchResult batchResult) {
                    String newState = fCtxt.getString(R.string.state_start);
                    if (batchResult.getStatus().isSuccess()) {
                        setNewState(newState);
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                "Success moving to "+newState);
                    } else {
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                "Failed moving to "+newState);
                    }
                    mApiClient.disconnect();
                }
            });
        }
    }

    public void handleTripEnd(Context ctxt, GoogleApiClient apiClient, String actionString) {
        Log.d(mContext, TAG, "TripDiaryStateMachineReceiver handleTripEnd(" + actionString + ") called");
        if (actionString.equals(ctxt.getString(R.string.transition_stopped_moving))) {
            // Stopping location tracking
            final List<BatchResultToken<Status>> tokenList = new LinkedList<BatchResultToken<Status>>();
            Batch.Builder resultBarrier = new Batch.Builder(apiClient);
            tokenList.add(resultBarrier.add(new LocationTrackingActions(ctxt, apiClient).stop()));
            tokenList.add(resultBarrier.add(new ActivityRecognitionActions(ctxt, apiClient).stop()));
            // TODO: This action can return null, and is the only one that can.
            // Should we refactor to simplify the code? Throw an exception instead?
            com.google.android.gms.common.api.PendingResult<Status> createGeofenceResult =
                    new GeofenceActions(ctxt, apiClient).create();
            if (createGeofenceResult != null) {
                tokenList.add(resultBarrier.add(createGeofenceResult));
            }
            final Context fCtxt = ctxt;
            resultBarrier.build().setResultCallback(new ResultCallback<BatchResult>() {
                @Override
                public void onResult(BatchResult batchResult) {
                    String newState = fCtxt.getString(R.string.state_waiting_for_trip_start);
                    if (batchResult.getStatus().isSuccess()) {
                        setNewState(newState);
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                "Success moving to "+newState);
                    } else {
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                "Failed moving to "+newState);
                    }
                    mApiClient.disconnect();
                }
            });
        }
        if (actionString.equals(ctxt.getString(R.string.transition_stop_tracking))) {
            final List<BatchResultToken<Status>> tokenList = new LinkedList<BatchResultToken<Status>>();
            Batch.Builder resultBarrier = new Batch.Builder(apiClient);
            tokenList.add(resultBarrier.add(new LocationTrackingActions(ctxt, apiClient).stop()));
            tokenList.add(resultBarrier.add(new ActivityRecognitionActions(ctxt, apiClient).stop()));
            final Context fCtxt = ctxt;
            resultBarrier.build().setResultCallback(new ResultCallback<BatchResult>() {
                @Override
                public void onResult(BatchResult batchResult) {
                    String newState = fCtxt.getString(R.string.state_start);
                    if (batchResult.getStatus().isSuccess()) {
                        setNewState(newState);
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                "Success moving to "+newState);
                    } else {
                        NotificationHelper.createNotification(fCtxt, STATE_IN_NUMBERS,
                                "Failed moving to "+newState);
                    }
                    mApiClient.disconnect();
                }
            });
        }
    }
}
