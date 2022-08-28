package edu.berkeley.eecs.emission.cordova.tracker.location; 

import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.lang.InterruptedException;
import java.util.concurrent.TimeoutException;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
// import com.google.android.gms.location.Priority;
// import com.google.android.gms.location.CurrentLocationRequest;
// import com.google.android.gms.location.CurrentLocationRequest.Builder;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.tasks.CancellationToken;
import com.google.android.gms.tasks.CancellationTokenSource;

import edu.berkeley.eecs.emission.cordova.tracker.ConfigManager;
import edu.berkeley.eecs.emission.cordova.tracker.Constants;
import edu.berkeley.eecs.emission.cordova.tracker.ExplicitIntent;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.MotionActivity;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.SimpleLocation;
import edu.berkeley.eecs.emission.cordova.tracker.location.actions.GeofenceActions;

import edu.berkeley.eecs.emission.cordova.unifiedlogger.NotificationHelper;


import android.app.IntentService;
import android.content.Intent;
import android.location.Location;
import android.content.Context;

import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.usercache.UserCache;
import edu.berkeley.eecs.emission.cordova.usercache.UserCacheFactory;


import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkRequest;
import androidx.work.WorkerParameters;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OneTimeWorkRequest.Builder;

import androidx.annotation.NonNull;

public class OPGeofenceWalkExitWorker extends Worker {
    private Context ctxt;
	private static final int ACTIVITY_ERROR_IN_NUMBERS = 22848490;

    public OPGeofenceWalkExitWorker(
        @NonNull Context ctxt,
        @NonNull WorkerParameters params) {
        super(ctxt, params);
        this.ctxt = ctxt;
    }

	private static final String TAG = "OPGeofenceWalkExitWorker";

    @Override
    public Result doWork() {
        Log.i(ctxt, TAG, "Initiating delayed read for walking transition");
        OPGeofenceExitActivityIntentService.LocationGeofenceStatus isOutsideStatus =
            OPGeofenceExitActivityIntentService.isOutsideGeofence(
                ctxt, LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        if (isOutsideStatus != OPGeofenceExitActivityIntentService.LocationGeofenceStatus.UNKNOWN) {
            return handleKnownResult(isOutsideStatus);
        } else {
            Log.i(ctxt, TAG, "handle walking transition: unknown status with balanced accuracy, retrying with high accuracy");
            OPGeofenceExitActivityIntentService.LocationGeofenceStatus highAccuracyOutsideStatus = OPGeofenceExitActivityIntentService.isOutsideGeofence(ctxt,
                LocationRequest.PRIORITY_HIGH_ACCURACY);
            if (highAccuracyOutsideStatus == OPGeofenceExitActivityIntentService.LocationGeofenceStatus.UNKNOWN) {
                return handleUnknownResult();
            } else {
                return handleKnownResult(highAccuracyOutsideStatus);
            }
        }
    }

    private Result handleKnownResult(OPGeofenceExitActivityIntentService.LocationGeofenceStatus outsideStatus) {
        if (outsideStatus == OPGeofenceExitActivityIntentService.LocationGeofenceStatus.INSIDE) {
            Log.i(ctxt, TAG, "is outside check: stayed inside geofence, not an exit, ignoring");
            scheduleCheckWalkGeofenceExit(ctxt);
        }
        if (outsideStatus == OPGeofenceExitActivityIntentService.LocationGeofenceStatus.OUTSIDE) {
            Log.i(ctxt, TAG, "is outside check: exited geofence, sending broadcast");
            ctxt.sendBroadcast(new ExplicitIntent(ctxt, R.string.transition_exited_geofence));
        }
        return Result.success();
    }

    private Result handleUnknownResult() {
        scheduleCheckWalkGeofenceExit(ctxt);
        NotificationHelper.createNotification(ctxt, ACTIVITY_ERROR_IN_NUMBERS,
            null, new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date())
            +" walking transition but status is unknown, skipping ");
        // Indicate whether the work finished successfully with the Result
        return Result.failure();
    }

    public static void scheduleCheckWalkGeofenceExit(Context ctxt) {
        WorkRequest walkExitGeofenceRequest =
            new OneTimeWorkRequest.Builder(OPGeofenceWalkExitWorker.class)
                .setInitialDelay(1, TimeUnit.MINUTES)
                .addTag(getAppSpecificWorkTag(ctxt))
                .build();

        /*
         * Do we want exponential backoff?
         * I claim "no" since if we have already lost more than a minute, and
         * haven't been cancelled by a non-walk transition yet, we actually
         * want to speed up our checks.
         */

        WorkManager
            .getInstance(ctxt)
            .enqueue(walkExitGeofenceRequest);

    }

    public static void cancelCheckWalkGeofenceExit(Context ctxt) {
        WorkManager.getInstance(ctxt).cancelAllWorkByTag(getAppSpecificWorkTag(ctxt));
    }

    private static String getAppSpecificWorkTag(Context ctxt) {
        // not sure how long tags can be. But let's truncate the PackageName to
        // 80 characters to be on the safe side
        String pkgName = ctxt.getPackageName();
        if (pkgName.length() > 80) {
            pkgName = pkgName.substring(0, 80);
        }
        String retVal = pkgName+"_"+"DELAYED_WALK_EXIT_CHECK";
        Log.d(ctxt, TAG, "Returning app-specific work tag "+retVal);
        return retVal;
    }

}

