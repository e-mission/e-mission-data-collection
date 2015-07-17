package edu.berkeley.eecs.cfc_tracker.location.actions;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionApi;
import com.google.android.gms.location.LocationServices;

import edu.berkeley.eecs.cfc_tracker.Constants;
import edu.berkeley.eecs.cfc_tracker.Log;
import edu.berkeley.eecs.cfc_tracker.location.ActivityRecognitionChangeIntentService;

/**
 * Created by shankari on 12/31/14.
 */
public class ActivityRecognitionActions {
    private static final int ACTIVITY_IN_NUMBERS = 22848489;
    private static final int ACTIVITY_DETECTION_INTERVAL = Constants.THIRTY_SECONDS;
    // ~ 2.5 minutes - the same change that we used to use to detect the end of a trip

    private static final String TAG = "ActivityRecognitionHandler";

    private Context mCtxt;
    private GoogleApiClient mGoogleApiClient;

    public ActivityRecognitionActions(Context context, GoogleApiClient apiClient) {
        this.mCtxt = context;
        this.mGoogleApiClient = apiClient;
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
        return ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(mGoogleApiClient,
                getActivityRecognitionPendingIntent(mCtxt));
    }
}
