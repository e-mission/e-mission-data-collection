package edu.berkeley.eecs.emission.cordova.tracker.location.actions;

import java.util.ArrayList;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.tasks.Task;

import edu.berkeley.eecs.emission.cordova.tracker.ConfigManager;
import edu.berkeley.eecs.emission.cordova.tracker.location.TripDiaryStateMachineForegroundService;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.tracker.location.ActivityTransitionIntentService;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by shankari on 12/31/14.
 */
public class ActivityTransitionActions {
    private static final int ACTIVITY_IN_NUMBERS = 22848489;
    private int ACTIVITY_DETECTION_INTERVAL;

    private static final String TAG = "ActivityTransitionActions";

    private Context mCtxt;

    public ActivityTransitionActions(Context context) {
        this.mCtxt = context;
        ACTIVITY_DETECTION_INTERVAL = ConfigManager.getConfig(context).getFilterTime();
    }

    public Task<Void> start() {
        List<ActivityTransition> activityTransitionList = new ArrayList<ActivityTransition>();
        activityTransitionList.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.WALKING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build());
        activityTransitionList.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.ON_BICYCLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build());
        activityTransitionList.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.RUNNING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build());
        activityTransitionList.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build());
        activityTransitionList.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build());
        
        Log.d(mCtxt, TAG, "Starting activity transitions for list = "+activityTransitionList);
        ActivityTransitionRequest request = new ActivityTransitionRequest(activityTransitionList);
        return ActivityRecognition.getClient(mCtxt).requestActivityTransitionUpdates(request,
                getActivityTransitionPendingIntent(mCtxt));
    }

    public static PendingIntent getActivityTransitionPendingIntent(Context ctxt) {
        Intent innerIntent = new Intent(ctxt, ActivityTransitionIntentService.class);
		/*
		 * Setting FLAG_UPDATE_CURRENT so that sending the PendingIntent again updates the original.
		 * We only want to have one geofence active at one point of time.
		 */
        return TripDiaryStateMachineForegroundService.getProperPendingIntent(ctxt, innerIntent);
    }

    public Task<Void> stop() {
        Log.d(mCtxt, TAG, "Stopping activity recognition with interval = "+ACTIVITY_DETECTION_INTERVAL);
        return ActivityRecognition.getClient(mCtxt).removeActivityTransitionUpdates(
                getActivityTransitionPendingIntent(mCtxt));
    }
}
