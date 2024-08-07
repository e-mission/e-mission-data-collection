package edu.berkeley.eecs.emission.cordova.tracker.location;

import java.util.Arrays;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import edu.berkeley.eecs.emission.cordova.tracker.ConfigManager;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.MotionActivity;
import edu.berkeley.eecs.emission.cordova.tracker.verification.SensorControlChecks;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.NotificationHelper;
import edu.berkeley.eecs.emission.R;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;


import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.usercache.UserCache;
import edu.berkeley.eecs.emission.cordova.usercache.UserCacheFactory;

import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import android.content.Context;

public class ActivityRecognitionChangeIntentService extends IntentService {
	private static final int ACTIVITY_IN_NUMBERS = 22848489;

	public ActivityRecognitionChangeIntentService() {
		super("ActivityRecognitionChangeIntentService");
	}

	private static final String TAG = "ActivityRecognitionChangeIntentService";

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(this, TAG, "onStartCommand called with intent "+intent+" flags "+flags+" startId "+startId);
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void onDestroy() {
		Log.d(this, TAG, "onDestroy called");
		super.onDestroy();
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		Log.d(this, TAG, "FINALLY! Got activity update, intent is "+intent);
//		Log.d(this, TAG, "Intent extras are "+intent.getExtras().describeContents());
//		Log.d(this, TAG, "Intent extra key list is "+Arrays.toString(intent.getExtras().keySet().toArray()));
		if (ActivityRecognitionResult.hasResult(intent)) {
			ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
			DetectedActivity mostProbableActivity = result.getMostProbableActivity();
			Log.i(this, TAG, "Detected new activity "+mostProbableActivity);
			if (ConfigManager.getConfig(this).isSimulateUserInteraction()) {
			NotificationHelper.createNotification(this, ACTIVITY_IN_NUMBERS, null, this.getString(R.string.detected_new_activity, activityType2Name(mostProbableActivity.getType(), this)));
		}

		// Add in logs to check for trip disappearance
		Log.d(this, TAG, "Beginning checks for trip disappearance...");

		// Check to see if the foreground service is still alive
		final Context ctxt = getApplicationContext();
		TripDiaryStateMachineForegroundService.checkForegroundNotification(ctxt);

		// Check if can retrieve current location
		OnCompleteListener<LocationSettingsResponse> callback = new OnCompleteListener<LocationSettingsResponse>() {
			@Override // no-op
			public void onComplete(Task<LocationSettingsResponse> task) {
				if (task.isSuccessful()) {
					LocationSettingsStates result = task.getResult().getLocationSettingsStates();
					// Docs for what we can check using LocationSettingsStates https://developers.google.com/android/reference/com/google/android/gms/location/LocationSettingsStates
					Log.d(ctxt, TAG, "While checking location status in activity response, got responses... isGpsUsable(): " + result.isGpsUsable() + " isLocationUsable(): " + result.isLocationUsable() + " isNetworkLocationUsable(): " + result.isNetworkLocationUsable());
				} else {
					Exception error = task.getException();
					Log.d(ctxt, TAG, "While checking location status in activity response, got exception... " + error.getMessage());
				}
			}
		};
		SensorControlChecks.checkLocationSettings(this, callback);
		
		// Check for current state
		SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        String mCurrState = mPrefs.getString(this.getString(R.string.curr_state_key),
            this.getString(R.string.state_start));
		Log.d(this, TAG, "Checking current state: " + mCurrState);

		Log.d(this, TAG, "Ending checks for trip disappearance...");

			// TODO: Do we want to compare activity and only store when different?
            // Can easily do that by getting the last activity
            // Let's suck everything up to the server for now and optimize later
			// We used to only send the activities that are above 90% confidence.
			// But it looks like that has a delay in the detection of starts, specially for train trips.
			// Let us just suck everything up for now and see if the smoothing HMM from PerCom works
			// better.
            // if (mostProbableActivity.getConfidence() > 90) {
                UserCache userCache = UserCacheFactory.getUserCache(this);
                MotionActivity mpma = new MotionActivity(mostProbableActivity);
                userCache.putSensorData(R.string.key_usercache_activity, mpma);
            // }
			/*
			DetectedActivity currentActivity = DataUtils.getCurrentMode(this).getLastActivity();
			if (currentActivity.getType() == mostProbableActivity.getType()) {
				Log.d(this, TAG, "currentActivity ("+currentActivity+") == newActivity ("+mostProbableActivity+"), skipping update");
			} else {
                //    At least in the current version of the API, the confidence is given in percent (i.e. 90 instead of 0.9)
				Log.d(this, TAG, "currentActivity ("+currentActivity+") != newActivity ("+mostProbableActivity+"), checking confidence");
				if (mostProbableActivity.getConfidence() > 90) {
                    if (!isFilteredActivity(mostProbableActivity.getType())) {
                        Log.d(this, TAG, "currentActivity (" + currentActivity + ") != newActivity (" + mostProbableActivity + ") with confidence (" +
                                mostProbableActivity.getConfidence() + " > 90, updating current state");
                        DataUtils.addModeChange(this, SystemClock.elapsedRealtimeNanos(), mostProbableActivity);
                    }
				}
			}
			*/
		}
	}

    private boolean isFilteredActivity(int activityType) {
        // We ignore STILL because it is not an activity/mode like walking, biking or transport.
        // We also don't have the resources to deal with it properly on the server side.
        // TODO: We can use it to detect stops, both on the road network and on public transport
        // lines, for better mode detection
        if ((activityType == DetectedActivity.TILTING) ||
                (activityType == DetectedActivity.UNKNOWN) ||
                (activityType == DetectedActivity.STILL)) {
            return true;
        }
        return false;
    }

	/**
	 * Map detected activity types to strings
	 *@param activityType The detected activity type
	 *@return A user-readable name for the type
	 */
	public static String activityType2Name(int activityType, IntentService intentService) {
		switch(activityType) {
			case DetectedActivity.IN_VEHICLE:
				return intentService.getString(R.string.activity_transport);
			case DetectedActivity.ON_BICYCLE:
				return intentService.getString(R.string.activity_cycling);
			case DetectedActivity.ON_FOOT:
				return intentService.getString(R.string.activity_walking);
			case DetectedActivity.STILL:
				return intentService.getString(R.string.activity_still);
			case DetectedActivity.UNKNOWN:
				return intentService.getString(R.string.activity_unknown);
			case DetectedActivity.TILTING:
				return intentService.getString(R.string.activity_tilting);
		}
		return intentService.getString(R.string.activity_unknown);
	}
}
