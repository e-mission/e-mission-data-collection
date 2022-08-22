package edu.berkeley.eecs.emission.cordova.tracker.location.actions;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.tasks.Task;

import edu.berkeley.eecs.emission.cordova.tracker.ConfigManager;
import edu.berkeley.eecs.emission.cordova.tracker.location.TripDiaryStateMachineForegroundService;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.tracker.location.ActivityRecognitionChangeIntentService;

/**
 * Created by shankari on 12/31/14.
 */
public class ActivityRecognitionActions {
    private static final int ACTIVITY_IN_NUMBERS = 22848489;
    private int ACTIVITY_DETECTION_INTERVAL;

    private static final String TAG = "ActivityRecognitionActions";

    private Context mCtxt;

    public ActivityRecognitionActions(Context context) {
        this.mCtxt = context;
        ACTIVITY_DETECTION_INTERVAL = ConfigManager.getConfig(context).getFilterTime();
    }

    public Task<Void> start() {
        Log.d(mCtxt, TAG, "Starting activity recognition with interval = "+ACTIVITY_DETECTION_INTERVAL);
        return ActivityRecognition.getClient(mCtxt).requestActivityUpdates(
                ACTIVITY_DETECTION_INTERVAL,
                getActivityRecognitionPendingIntent(mCtxt));
    }

    public static PendingIntent getActivityRecognitionPendingIntent(Context ctxt) {
        Intent innerIntent = new Intent(ctxt, ActivityRecognitionChangeIntentService.class);
		/*
		 * Setting FLAG_UPDATE_CURRENT so that sending the PendingIntent again updates the original.
		 * We only want to have one geofence active at one point of time.
		 */
        return TripDiaryStateMachineForegroundService.getProperPendingIntent(ctxt, innerIntent);
    }

    public Task<Void> stop() {
        Log.d(mCtxt, TAG, "Stopping activity recognition with interval = "+ACTIVITY_DETECTION_INTERVAL);
        return ActivityRecognition.getClient(mCtxt).removeActivityUpdates(
                getActivityRecognitionPendingIntent(mCtxt));
    }
}
