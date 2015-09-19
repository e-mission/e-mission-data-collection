package edu.berkeley.eecs.cfc_tracker.location;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import edu.berkeley.eecs.cfc_tracker.R;
import edu.berkeley.eecs.cfc_tracker.log.Log;

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

    // Can remove this if we can remove the constructor with the Context argument.
    private static String CURR_STATE_KEY = "TripDiaryCurrState";

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
        editor.putString(CURR_STATE_KEY, null);
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
                context.getString(R.string.transition_stop_tracking)
        }));

        if (!validTransitions.contains(intent.getAction())) {
            Log.e(context, TAG, "Received unknown action "+intent.getAction()+" ignoring");
            return;
        }

        Intent serviceStartIntent = new Intent(context, TripDiaryStateMachineIntentService.class);
        serviceStartIntent.setAction(intent.getAction());
        context.startService(serviceStartIntent);
    }
	
}
