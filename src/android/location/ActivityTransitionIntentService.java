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

import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.usercache.UserCache;
import edu.berkeley.eecs.emission.cordova.usercache.UserCacheFactory;


public class ActivityTransitionIntentService extends IntentService {
	private static final int ACTIVITY_IN_NUMBERS = 22848489;
	private static final int ACTIVITY_ERROR_IN_NUMBERS = 22848490;
    private UserCache uc;

    enum LocationGeofenceStatus {
        INSIDE,
        OUTSIDE,
        UNKNOWN
    }

	public ActivityTransitionIntentService() {
		super("ActivityTransitionIntentService");
        this.uc = UserCacheFactory.getUserCache(this);
	}

	private static final String TAG = "ActivityTransitionIntentService";

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
                NotificationHelper.createNotification(this, ACTIVITY_IN_NUMBERS,
                    null, new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date())
                        +" "+toActivityString(event.getActivityType())
                        +" "+toTransitionType(event.getTransitionType()));
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
        sendBroadcast(new ExplicitIntent(this, R.string.transition_exited_geofence));
    }

    private void handleWalkingTransition() {
        Log.i(this, TAG, "Found walking transition in custom geofence, starting to read location");
        LocationGeofenceStatus alreadyOutsideStatus = checkAlreadyOutsideGeofence();
        if (alreadyOutsideStatus == LocationGeofenceStatus.INSIDE) {
            Log.i(this, TAG, "already outside check: stayed inside geofence, not an exit, ignoring");
            return;
        }
        if (alreadyOutsideStatus == LocationGeofenceStatus.OUTSIDE) {
            Log.i(this, TAG, "already outside check: exited geofence, sending broadcast");
            sendBroadcast(new ExplicitIntent(this, R.string.transition_exited_geofence));
            return;
        }

        NotificationHelper.createNotification(this, ACTIVITY_ERROR_IN_NUMBERS,
            null, new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date())
                +" walking transition but status is unknown, skipping ");
    }

    private LocationGeofenceStatus checkAlreadyOutsideGeofence() {
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
                LocationServices.getFusedLocationProviderClient(this).getCurrentLocation(
                        LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY,
                        initialReadCancelToken.getToken()),
                    2, TimeUnit.MINUTES);
            if (currLoc == null) {
                Log.d(this, TAG, "isAlreadyOutsideGeofence: currLocation = null "
                +"returning UNKNOWN");
                return LocationGeofenceStatus.UNKNOWN;
            }
            JSONObject currGeofenceLoc = uc.getLocalStorage(GeofenceActions.GEOFENCE_LOC_KEY, false);
            Log.d(this, TAG, "isAlreadyOutsideGeofence: currLocation = "+currLoc
                +"checking with stored loc "+currGeofenceLoc);
            float distanceToCurrGeofence = SimpleLocation.distanceTo(currLoc,
                currGeofenceLoc);
            if (distanceToCurrGeofence > 100) {
                Log.d(this, TAG, "isAlreadyOutsideGeofence: distanceToCurrGeofence = "
                +distanceToCurrGeofence+" returning OUTSIDE");
                return LocationGeofenceStatus.OUTSIDE;
            } else {
                Log.d(this, TAG, "isAlreadyOutsideGeofence: distanceToCurrGeofence = "
                +distanceToCurrGeofence+" returning INSIDE");
                return LocationGeofenceStatus.INSIDE;
            }
        } catch (ExecutionException e) {
            Log.exception(this, TAG, e);
            return LocationGeofenceStatus.UNKNOWN;
        } catch (InterruptedException e) {
            Log.exception(this, TAG, e);
            return LocationGeofenceStatus.UNKNOWN;
        } catch (TimeoutException e) {
            Log.exception(this, TAG, e);
            return LocationGeofenceStatus.UNKNOWN;
        } catch (JSONException e) {
            Log.exception(this, TAG, e);
            return LocationGeofenceStatus.UNKNOWN;
        }
    }

    /*
     * Checking the location every minute can be done in multiple different
     * ways, namely: (1) using a looper, (2) using an executor, (3) using a
     * pending intent, (4) using a worker and checking the current location.
     * Let's use the looper for now since it is the easiest and the best documented,
     * but might want to move to a pending intent to be consistent with geofence
     * creation in the future
     * https://developer.android.com/training/location/request-updates

    private checkGeofenceExit(LocationRequest request) {
        JSONObject currGeofenceLoc = uc.getLocalStorage(GEOFENCE_LOC_KEY);
        final Context fCtxt = this;
        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    // if we have had a sequence of NULL responses
                    // return UNKNOWN
                    return;
                }
                // if last transition was STILL
                    // return INSIDE
                for (Location currLocation : locationResult.getLocations()) {
                    float distanceToCurrGeofence = SimpleLocation.distanceTo(
                        currLocation,
                        currGeofenceLoc);
                    if (distanceToCurrGeofence > 100) {
                        return;
                    }
                    // if duration between first and now is 5 minutes
                        // return INSIDE
                }
            }
            
            private void returnResult(LocationGeofenceStatus result) {
                synchronized(ActivityTransitionIntentService.this) {
                    ActivityTransitionIntentService.this.geofenceStateResult = result;
                    ActivityTransitionIntentService.this.notify();
                    LocationServices.getFusedLocationProviderClient(fCtxt).removeLocationUpdates(this);
                }
            }
        };
    }
     */


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

    /*
    private static LocationRequest getMediumAccuracyOneMinuteRequest() {
        LocationRequest everyMinute = LocationRequest.create();
        return everyMinute
                // get locations every minute
                .setInterval(60 * Constants.MILLISECONDS)
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
    }

    private static LocationRequest getHighAccuracyOneMinuteRequest() {
        LocationRequest everyMinute = LocationRequest.create();
        return everyMinute
                // get locations every minute
                .setInterval(60 * Constants.MILLISECONDS)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }
    */

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
