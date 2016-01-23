package edu.berkeley.eecs.cfc_tracker.location;

import java.util.Arrays;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import edu.berkeley.eecs.cfc_tracker.NotificationHelper;
import edu.berkeley.eecs.cfc_tracker.R;

import android.app.IntentService;
import android.content.Intent;

import edu.berkeley.eecs.cfc_tracker.log.Log;
import edu.berkeley.eecs.cfc_tracker.usercache.UserCache;
import edu.berkeley.eecs.cfc_tracker.usercache.UserCacheFactory;


public class ActivityRecognitionChangeIntentService extends IntentService {
	private static final int ACTIVITY_IN_NUMBERS = 22848489;

	public ActivityRecognitionChangeIntentService() {
		super("ActivityRecognitionChangeIntentService");
	}

	private static final String TAG = "ActivityRecognitionChangeIntentService";

	@Override
	protected void onHandleIntent(Intent intent) {
		Log.d(this, TAG, "FINALLY! Got activity update, intent is "+intent);
		Log.d(this, TAG, "Intent extras are "+intent.getExtras().describeContents());
		Log.d(this, TAG, "Intent extra key list is "+Arrays.toString(intent.getExtras().keySet().toArray()));
		if (ActivityRecognitionResult.hasResult(intent)) {
			ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
			DetectedActivity mostProbableActivity = result.getMostProbableActivity();
			Log.i(this, TAG, "Detected new activity "+mostProbableActivity);
			NotificationHelper.createNotification(this, ACTIVITY_IN_NUMBERS,
					"Detected new activity "+activityType2Name(mostProbableActivity.getType()));
			// TODO: Do we want to compare activity and only store when different?
            // Can easily do that by getting the last activity
            // Let's suck everything up to the server for now and optimize later
			// We used to only send the activities that are above 90% confidence.
			// But it looks like that has a delay in the detection of starts, specially for train trips.
			// Let us just suck everything up for now and see if the smoothing HMM from PerCom works
			// better.
            // if (mostProbableActivity.getConfidence() > 90) {
                UserCache userCache = UserCacheFactory.getUserCache(this);
                userCache.putSensorData(R.string.key_usercache_activity, mostProbableActivity);
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
	public static String activityType2Name(int activityType) {
		switch(activityType) {
			case DetectedActivity.IN_VEHICLE:
				return "transport";
			case DetectedActivity.ON_BICYCLE:
				return "cycling";
			case DetectedActivity.ON_FOOT:
				return "walking";
			case DetectedActivity.STILL:
				return "still";
			case DetectedActivity.UNKNOWN:
				return "unknown";
			case DetectedActivity.TILTING:
				return "tilting";
		}
		return "unknown";
	}
}
