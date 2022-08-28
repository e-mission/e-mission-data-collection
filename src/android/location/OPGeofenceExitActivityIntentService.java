package edu.berkeley.eecs.emission.cordova.tracker.location; 
// Auto fixed by post-plugin hook 
import edu.berkeley.eecs.emission.R;

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
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
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
import android.os.Looper;

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

public class OPGeofenceExitActivityIntentService extends IntentService {
	private static final int ACTIVITY_IN_NUMBERS = 22848489;
	private static final int ACTIVITY_ERROR_IN_NUMBERS = 22848490;
    private UserCache uc;
    private WalkExitGeofenceLocationCallback walkExitCallback;

    enum LocationGeofenceStatus {
        INSIDE,
        OUTSIDE,
        UNKNOWN
    }

	public OPGeofenceExitActivityIntentService() {
		super("OPGeofenceExitActivityIntentService");
		Log.d(this, TAG, "initializer called");
        this.uc = UserCacheFactory.getUserCache(this);
	}

	private static final String TAG = "OPGeofenceExitActivityIntentService";

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
		Log.d(this, TAG, "FINALLY! Got activity transition, intent is "+intent);
//		Log.d(this, TAG, "Intent extras are "+intent.getExtras().describeContents());
//		Log.d(this, TAG, "Intent extra key list is "+Arrays.toString(intent.getExtras().keySet().toArray()));
		if (ActivityTransitionResult.hasResult(intent)) {
			ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);
            for (ActivityTransitionEvent event : result.getTransitionEvents()) {
                String info = "Transition: " + toActivityString(event.getActivityType()) +
                    " (" + toTransitionType(event.getTransitionType()) + ")" + "   " +
                    new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
                Log.d(this, TAG, info);
                /*
                NotificationHelper.createNotification(this, ACTIVITY_IN_NUMBERS,
                    null, new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date())
                        +" "+toActivityString(event.getActivityType())
                        +" "+toTransitionType(event.getTransitionType()));
                */
                if (event.getTransitionType() != ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                    NotificationHelper.createNotification(this, ACTIVITY_ERROR_IN_NUMBERS,
                        null, new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date())
                            +" Expected enter transition, found "+toActivityString(event.getActivityType())
                            +" "+toTransitionType(event.getTransitionType()));
                    continue;
                }
                // TODO: Only handle this when we are looking for geofence exit
                // Or should we just disable the listener if in `ongoing_trip` state
                // latter is more consistent with current geofence implementation
                // former lets us get a new source of sensor data
                switch(event.getActivityType()) {
                    case DetectedActivity.STILL:
                        cancelPendingDelayedCheck();
                        break;
                    case DetectedActivity.WALKING:
                    case DetectedActivity.RUNNING:
                        handleWalkingTransition();
                        break;
                    case DetectedActivity.ON_BICYCLE:
                    case DetectedActivity.IN_VEHICLE:
                        handleNonWalkingTransition();
                        break;
                    default:
                        NotificationHelper.createNotification(this, ACTIVITY_ERROR_IN_NUMBERS,
                            null, new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date())
                                +" Expected enter transition, found "+toActivityString(event.getActivityType())
                                +" "+toTransitionType(event.getTransitionType()));
                }
            }
		}
	}

    private void handleNonWalkingTransition() {
        Log.i(this, TAG, "Found non-walking transition in custom geofence, sending exited_geofence message");
        cancelPendingDelayedCheck();
        sendBroadcast(new ExplicitIntent(this, R.string.transition_exited_geofence));
    }

    private void handleWalkingTransition() {
        Log.i(this, TAG, "Found walking transition in custom geofence, starting to read location");
        LocationGeofenceStatus isOutsideStatus =
            OPGeofenceExitActivityIntentService.isOutsideGeofence(this, LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        if (isOutsideStatus != LocationGeofenceStatus.UNKNOWN) {
            handleKnownResult(isOutsideStatus);
        } else {
            Log.i(this, TAG, "handle walking transition: unknown status with balanced accuracy, retrying with high accuracy");
            LocationGeofenceStatus highAccuracyOutsideStatus = OPGeofenceExitActivityIntentService.isOutsideGeofence(this,
                LocationRequest.PRIORITY_HIGH_ACCURACY);
            if (highAccuracyOutsideStatus == LocationGeofenceStatus.UNKNOWN) {
                handleUnknownResult();
            } else {
                handleKnownResult(highAccuracyOutsideStatus);
            }
        }
    }

    private void handleKnownResult(LocationGeofenceStatus outsideStatus) {
        if (outsideStatus == LocationGeofenceStatus.INSIDE) {
            Log.i(this, TAG, "handle walking transition: stayed inside geofence, not an exit, ignoring");
            scheduleDelayedCheck();
            return;
        }
        if (outsideStatus == LocationGeofenceStatus.OUTSIDE) {
            Log.i(this, TAG, "handle walking transition: exited geofence, sending broadcast");
            sendBroadcast(new ExplicitIntent(this, R.string.transition_exited_geofence));
            return;
        }
    }

    private void handleUnknownResult() {
        scheduleDelayedCheck();
        NotificationHelper.createNotification(this, ACTIVITY_ERROR_IN_NUMBERS,
            null, new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date())
                +" walking transition but status is unknown, skipping ");
    }

    public static LocationGeofenceStatus isOutsideGeofence(Context ctxt, int priority) {
        CancellationTokenSource initialReadCancelToken = new CancellationTokenSource();
        try {
            /*
             * This is a bit tricky because of all the asynchronous behavior.
             * Get current location will return the location if it is recent enough
             * and will request a location if it is not.
             * 
             * If the location was within the last minute:
             *   and was outside the geofence, we will exit
             *   and was inside the geofence, we will check back in a minute
             *
             * If we re-read, we will get the new location. Note that this
             * will be the location potentially after a minute:
             *   if already outside, we will exit
             *   if inside, we will check back in a minute
             *
             * So in a way, this spans both cases (2)(a) and (2)(c)(workaround)
             */
            Location currLoc = Tasks.await(
                LocationServices.getFusedLocationProviderClient(ctxt).getCurrentLocation(
                        priority,
                        initialReadCancelToken.getToken()),
                    2, TimeUnit.MINUTES);
            if (currLoc == null) {
                Log.d(ctxt, TAG, "isOutsideGeofence: currLocation = null,"
                    +"returning UNKNOWN");
                return LocationGeofenceStatus.UNKNOWN;
            }
            JSONObject currGeofenceLoc = UserCacheFactory.getUserCache(ctxt).getLocalStorage(GeofenceActions.GEOFENCE_LOC_KEY, false);
            Log.d(ctxt, TAG, "isOutsideGeofence: currLocation = "+currLoc
                +"checking with stored loc "+currGeofenceLoc);
            if (currGeofenceLoc == null) {
                throw new JSONException("Unable to retrieve local storage at key "+
                    GeofenceActions.GEOFENCE_LOC_KEY);
            }
            float distanceToCurrGeofence = SimpleLocation.distanceTo(currLoc,
                currGeofenceLoc);
            if (distanceToCurrGeofence > 100) {
                Log.d(ctxt, TAG, "isOutsideGeofence: distanceToCurrGeofence = "
                +distanceToCurrGeofence+" returning OUTSIDE");
                // Add the exit location to the tracking database, just like we do
                // for the geofence exit intent service
                UserCacheFactory.getUserCache(ctxt).putSensorData(R.string.key_usercache_location,
                    new SimpleLocation(currLoc));
                return LocationGeofenceStatus.OUTSIDE;
            } else {
                Log.d(ctxt, TAG, "isOutsideGeofence: distanceToCurrGeofence = "
                    +distanceToCurrGeofence+" returning INSIDE");
                // TODO: Also figure out whether we should store the location
                // even when we are inside.
                // on the one hand, we have read it, so why not store it?
                // on the other hand, we don't really need it and it is is not
                // consistent with anything else
                // optimizing for consistency here...
                return LocationGeofenceStatus.INSIDE;
            }
        } catch (ExecutionException e) {
            Log.exception(ctxt, TAG, e);
            return LocationGeofenceStatus.UNKNOWN;
        } catch (InterruptedException e) {
            Log.exception(ctxt, TAG, e);
            return LocationGeofenceStatus.UNKNOWN;
        } catch (TimeoutException e) {
            Log.exception(ctxt, TAG, e);
            return LocationGeofenceStatus.UNKNOWN;
        } catch (JSONException e) {
            Log.exception(ctxt, TAG, e);
            return LocationGeofenceStatus.UNKNOWN;
        }
    }

    private void scheduleDelayedCheck() {
        Log.i(this, TAG, "scheduleDelayedCheck, creating work request");
        OPGeofenceWalkExitWorker.scheduleCheckWalkGeofenceExit(this);
        /*
        Log.i(this, TAG, "scheduleDelayedCheck, creating location looper");
        try {
            JSONObject currGeofenceLoc = uc.getLocalStorage(
                GeofenceActions.GEOFENCE_LOC_KEY, false);
            walkExitCallback = new WalkExitGeofenceLocationCallback(currGeofenceLoc);
            LocationServices.getFusedLocationProviderClient(this).requestLocationUpdates(
                getMediumAccuracyOneMinuteRequest(),
                walkExitCallback,
                Looper.getMainLooper());
        } catch (JSONException e) {
            Log.exception(this, TAG, e);
        }
        */
    }

    private void cancelPendingDelayedCheck() {
        Log.i(this, TAG, "cancelPendingDelayedCheck, cancelling workers");
        OPGeofenceWalkExitWorker.cancelCheckWalkGeofenceExit(this);

        /*
        Log.i(this, TAG, "cancelPendingDelayedCheck, cancelling callback "+walkExitCallback);
        if (walkExitCallback != null) {
            try {
                Tasks.await(LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(walkExitCallback));
                Log.i(this, TAG, "cancelPendingDelayedCheck, finished cancelling, setting callback to null ");
                walkExitCallback = null;
            } catch (ExecutionException e) {
                Log.exception(this, TAG, e);
            } catch (InterruptedException e) {
                Log.exception(this, TAG, e);
            }
        }
        */
    }

    /*
    private static CurrentLocationRequest getMediumAccuracyOneMinuteRequest() {
        return CurrentLocationRequest.Builder()
                // location no more than a minute old
                .setMaxUpdateAgeMillis(60 * Constants.MILLISECONDS) 
                // retrieve within a minute if too old
                .setDurationMillis(60 * Constants.MILLISECONDS)
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
    }
    */

    private static LocationRequest getMediumAccuracyOneMinuteRequest() {
        LocationRequest everyMinute = LocationRequest.create();
        return everyMinute
                // get locations every minute
                .setInterval(60 * Constants.MILLISECONDS)
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
    }

    /*
    private static LocationRequest getHighAccuracyOneMinuteRequest() {
        LocationRequest everyMinute = LocationRequest.create();
        return everyMinute
                // get locations every minute
                .setInterval(60 * Constants.MILLISECONDS)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }
    */

    class WalkExitGeofenceLocationCallback extends LocationCallback {
        private JSONObject currGeofenceLoc;

        public WalkExitGeofenceLocationCallback(JSONObject geofenceLoc) {
            this.currGeofenceLoc = geofenceLoc;
        }

        @Override
        public void onLocationResult(LocationResult locationResult) {
            if (locationResult == null) {
                Log.d(OPGeofenceExitActivityIntentService.this, TAG, "onLocationResult: currLocation = null "
                +"returning UNKNOWN");
                return;
            }
            for (Location currLoc : locationResult.getLocations()) {
                try {
                    float distanceToCurrGeofence = SimpleLocation.distanceTo(
                        currLoc,
                        currGeofenceLoc);
                    Log.d(OPGeofenceExitActivityIntentService.this, TAG,
                        "onLocationResult: currLocation = "+currLoc
                        +"checking with stored loc "+currGeofenceLoc);
                    if (distanceToCurrGeofence > 100) {
                        Log.d(OPGeofenceExitActivityIntentService.this, TAG,
                            "onLocationResult: distanceToCurrGeofence = "
                            +distanceToCurrGeofence+" sending geofence_exit message");
                        OPGeofenceExitActivityIntentService.this.uc.putSensorData(
                            R.string.key_usercache_location,
                            new SimpleLocation(currLoc));
                        OPGeofenceExitActivityIntentService.this.sendBroadcast(
                            new ExplicitIntent(OPGeofenceExitActivityIntentService.this,
                            R.string.transition_exited_geofence));
                        return;
                    } else {
                        Log.d(OPGeofenceExitActivityIntentService.this,
                            TAG, "onLocationResult: distanceToCurrGeofence = "
                            +distanceToCurrGeofence+" skipping exit");
                        return;
                    }
                } catch (JSONException e) {
                    Log.exception(OPGeofenceExitActivityIntentService.this, TAG, e);
                    return;
                }
            }
        }
    }


    private static String toActivityString(int activity) {
        switch (activity) {
            case DetectedActivity.STILL:
                return "STILL";
            case DetectedActivity.WALKING:
                return "WALKING";
            case DetectedActivity.IN_VEHICLE:
                return "IN_VEHICLE";
            case DetectedActivity.ON_BICYCLE:
                return "ON_BICYCLE";
            case DetectedActivity.RUNNING:
                return "RUNNING";
            default:
                return "UNKNOWN";
        }
    }

    private static String toTransitionType(int transitionType) {
        switch (transitionType) {
            case ActivityTransition.ACTIVITY_TRANSITION_ENTER:
                return "ENTER";
            case ActivityTransition.ACTIVITY_TRANSITION_EXIT:
                return "EXIT";
            default:
                return "UNKNOWN";
        }
    }
}
