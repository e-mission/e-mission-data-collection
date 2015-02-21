package edu.berkeley.eecs.cfc_tracker.location;

import java.util.Arrays;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import edu.berkeley.eecs.cfc_tracker.NotificationHelper;
import edu.berkeley.eecs.cfc_tracker.storage.DataUtils;

import android.app.IntentService;
import android.content.Intent;
import android.os.SystemClock;

import edu.berkeley.eecs.cfc_tracker.Log;


public class ActivityRecognitionChangeIntentService extends IntentService {
	private static final int ACTIVITY_IN_NUMBERS = 22848489;

	public ActivityRecognitionChangeIntentService() {
		super("ActivityRecognitionChangeIntentService");
	}

	private static final String TAG = "ActivityRecognitionChangeIntentService";

	@Override
	protected void onHandleIntent(Intent intent) {
		Log.d(TAG, "FINALLY! Got activity update, intent is "+intent);
		Log.d(TAG, "Intent extras are "+intent.getExtras().describeContents());
		Log.d(TAG, "Intent extra key list is "+Arrays.toString(intent.getExtras().keySet().toArray()));
		if (ActivityRecognitionResult.hasResult(intent)) {
			ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
			DetectedActivity mostProbableActivity = result.getMostProbableActivity();
			Log.i(TAG, "Detected new activity "+mostProbableActivity);
			NotificationHelper.createNotification(this, ACTIVITY_IN_NUMBERS,
					"Detected new activity "+DataUtils.activityType2Name(mostProbableActivity.getType()));
			DetectedActivity currentActivity = DataUtils.getCurrentMode(this).getLastActivity();
			if (currentActivity.getType() == mostProbableActivity.getType()) {
				Log.d(TAG, "currentActivity ("+currentActivity+") == newActivity ("+mostProbableActivity+"), skipping update");
			} else {
                /*
                    At least in the current version of the API, the confidence is given in percent (i.e. 90 instead of 0.9)
                 */
				Log.d(TAG, "currentActivity ("+currentActivity+") != newActivity ("+mostProbableActivity+"), checking confidence");
				if (mostProbableActivity.getConfidence() > 90) {
                    if (!isFilteredActivity(mostProbableActivity.getType())) {
                        Log.d(TAG, "currentActivity (" + currentActivity + ") != newActivity (" + mostProbableActivity + ") with confidence (" +
                                mostProbableActivity.getConfidence() + " > 90, updating current state");
                        DataUtils.addModeChange(this, SystemClock.elapsedRealtimeNanos(), mostProbableActivity);
                    }
				}
			}
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
}
