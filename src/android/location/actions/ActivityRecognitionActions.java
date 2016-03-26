package edu.berkeley.eecs.emission.cordova.tracker.location.actions;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;

import edu.berkeley.eecs.emission.cordova.tracker.ConfigManager;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.LocationTrackingConfig;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.tracker.location.ActivityRecognitionChangeIntentService;

/**
 * Created by shankari on 12/31/14.
 */
public class ActivityRecognitionActions {
    private static final int ACTIVITY_IN_NUMBERS = 22848489;
    private int ACTIVITY_DETECTION_INTERVAL;

    private static final String TAG = "ActivityRecognitionHandler";

    private Context mCtxt;
    private GoogleApiClient mGoogleApiClient;

    public ActivityRecognitionActions(Context context, GoogleApiClient apiClient) {
        this.mCtxt = context;
        this.mGoogleApiClient = apiClient;
        ACTIVITY_DETECTION_INTERVAL = ConfigManager.getConfig(context).getFilterTime();
    }

    public PendingResult<Status> start() {
        Log.d(mCtxt, TAG, "Starting activity recognition with interval = "+ACTIVITY_DETECTION_INTERVAL);
        return ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(mGoogleApiClient,
                ACTIVITY_DETECTION_INTERVAL,
                getActivityRecognitionPendingIntent(mCtxt));
    }

    public static PendingIntent getActivityRecognitionPendingIntent(Context ctxt) {
        Intent innerIntent = new Intent(ctxt, ActivityRecognitionChangeIntentService.class);
		/*
		 * Setting FLAG_UPDATE_CURRENT so that sending the PendingIntent again updates the original.
		 * We only want to have one geofence active at one point of time.
		 */
        return PendingIntent.getService(ctxt, 0, innerIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public PendingResult<Status> stop() {
        Log.d(mCtxt, TAG, "Stopping activity recognition with interval = "+ACTIVITY_DETECTION_INTERVAL);
        return ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(mGoogleApiClient,
                getActivityRecognitionPendingIntent(mCtxt));
    }
}
