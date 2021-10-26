package edu.berkeley.eecs.emission.cordova.tracker.verification;
// Auto fixed by post-plugin hook
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.tasks.Task;

import edu.berkeley.eecs.emission.cordova.tracker.Constants;
import edu.berkeley.eecs.emission.cordova.tracker.ExplicitIntent;
import edu.berkeley.eecs.emission.cordova.tracker.location.TripDiaryStateMachineService;
import edu.berkeley.eecs.emission.cordova.tracker.location.actions.LocationTrackingActions;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.NotificationHelper;
import edu.berkeley.eecs.emission.cordova.MainActivity;
import edu.berkeley.eecs.emission.cordova.R;

/*
 * Deals with settings and resolutions from the background as a service.
 * A bunch of static functions for ensuring that all the settings match what we
 * want, and prompts users to fix them. The actual fix will happen in the
 * foreground as the user clicks on the fixit notification.
 */

public class SensorControlBackgroundChecker {
    public static String TAG = "SensorControlBackgroundChecker";

    private static int STATE_IN_NUMBERS = 78283;

    public static void restartFSMIfStartState(Context ctxt) {
        String START_STATE = ctxt.getString(R.string.state_start);
        String currState = TripDiaryStateMachineService.getState(ctxt);
        Log.i(ctxt, TAG, "in restartFSMIfStartState, currState = "+currState);
        if (START_STATE.equals(currState)) {
            Log.i(ctxt, TAG, "in start state, sending initialize");
            ctxt.sendBroadcast(new ExplicitIntent(ctxt, R.string.transition_initialize));
        }
    }

    public static void checkLocationSettingsAndPermissions(final Context ctxt) {
    LocationRequest request = new LocationTrackingActions(ctxt).getLocationRequest();
        Log.d(ctxt, TAG, "Checking location settings and permissions for request "+request);
        // let's do the permission check first since it is synchronous
        if (checkLocationPermissions(ctxt, request)) {
            Log.d(ctxt, TAG, "checkLocationPermissions returned true, checking background permission");
            if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) ||
              checkBackgroundLocPermissions(ctxt, request)) {
              Log.d(ctxt, TAG, "checkBackgroundLocPermissions returned true, checking location settings");
            checkLocationSettings(ctxt, request);
            } else {
              Log.d(ctxt, TAG, "check background permissions returned false, no point checking settings");
              ctxt.sendBroadcast(new ExplicitIntent(ctxt, R.string.transition_tracking_error));
            }
            // final state will be set in this async call
        } else {
            Log.d(ctxt, TAG, "check location permissions returned false, no point checking settings");
            ctxt.sendBroadcast(new ExplicitIntent(ctxt, R.string.transition_tracking_error));
        }

        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) && checkMotionActivityPermissions(ctxt)) {
            Log.d(ctxt, TAG, "checkMotionActivityPermissions returned true, nothing more yet");
        } else {
            Log.d(ctxt, TAG, "checkMotionActivityPermissions returned false, but that's not a tracking error");
        }
    }

    private static boolean checkLocationPermissions(final Context ctxt,
                                                   final LocationRequest request) {
        // Ideally, we would use the request accuracy to figure out the permissions requested
        // but I can't find an authoritative mapping, and I'm running out of time for
        // fancy stuff
        int result = ContextCompat.checkSelfPermission(ctxt, SensorControlConstants.LOCATION_PERMISSION);
        Log.d(ctxt, TAG, "checkSelfPermission returned "+result);
        if (PackageManager.PERMISSION_GRANTED == result) {
            return true;
        } else {
            generateLocationEnableNotification(ctxt);
            return false;
        }
    }

    public static void generateLocationEnableNotification(Context ctxt) {
            Intent activityIntent = new Intent(ctxt, MainActivity.class);
            activityIntent.setAction(SensorControlConstants.ENABLE_LOCATION_PERMISSION_ACTION);
            PendingIntent pi = PendingIntent.getActivity(ctxt, SensorControlConstants.ENABLE_LOCATION_PERMISSION,
                    activityIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            NotificationHelper.createNotification(ctxt, SensorControlConstants.ENABLE_LOCATION_PERMISSION,
                    ctxt.getString(R.string.location_permission_off_enable), 
                    pi);
    }

    private static boolean checkBackgroundLocPermissions(final Context ctxt,
                                                        final LocationRequest request) {
        int result = ContextCompat.checkSelfPermission(ctxt, SensorControlConstants.BACKGROUND_LOC_PERMISSION);
        Log.d(ctxt, TAG, "checkSelfPermission returned "+result);
        if (PackageManager.PERMISSION_GRANTED == result) {
            return true;
        } else {
            generateBackgroundLocEnableNotification(ctxt);
            return false;
        }
    }

    public static void generateBackgroundLocEnableNotification(Context ctxt) {
            Intent activityIntent = new Intent(ctxt, MainActivity.class);
            activityIntent.setAction(SensorControlConstants.ENABLE_BACKGROUND_LOC_PERMISSION_ACTION);
            PendingIntent pi = PendingIntent.getActivity(ctxt, SensorControlConstants.ENABLE_BACKGROUND_LOC_PERMISSION,
                    activityIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            NotificationHelper.createNotification(ctxt, SensorControlConstants.ENABLE_BACKGROUND_LOC_PERMISSION,
                    ctxt.getString(R.string.background_loc_permission_off_enable), 
                    pi);
    }

    private static boolean checkMotionActivityPermissions(final Context ctxt) {
        int result = ContextCompat.checkSelfPermission(ctxt, SensorControlConstants.MOTION_ACTIVITY_PERMISSION);
        Log.d(ctxt, TAG, "checkSelfPermission returned "+result);
        if (PackageManager.PERMISSION_GRANTED == result) {
            return true;
        } else {
            generateMotionActivityEnableNotification(ctxt);
            return false;
        }
    }

    public static void generateMotionActivityEnableNotification(Context ctxt) {
            Intent activityIntent = new Intent(ctxt, MainActivity.class);
            activityIntent.setAction(SensorControlConstants.ENABLE_MOTION_ACTIVITY_PERMISSION_ACTION);
            PendingIntent pi = PendingIntent.getActivity(ctxt, SensorControlConstants.ENABLE_MOTION_ACTIVITY_PERMISSION,
                    activityIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            NotificationHelper.createNotification(ctxt, SensorControlConstants.ENABLE_MOTION_ACTIVITY_PERMISSION,
                    ctxt.getString(R.string.activity_permission_off_enable), 
                    pi);
    }

    private static void checkLocationSettings(final Context ctxt,
                                             final LocationRequest request) {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(request);

        Task<LocationSettingsResponse> task =
                LocationServices.getSettingsClient(ctxt).checkLocationSettings(builder.build());
        Log.d(ctxt, TAG, "Got back result "+task);
        task.addOnCompleteListener(resultTask -> {
          try {
            LocationSettingsResponse response = task.getResult(ApiException.class);
                        // All location settings are satisfied. The client can initialize location
                        // requests here.
            Log.i(ctxt, TAG, "All settings are valid, checking current state");
            Log.i(ctxt, TAG, "Current location settings are "+response);
                        NotificationHelper.cancelNotification(ctxt, Constants.TRACKING_ERROR_ID);
                        restartFSMIfStartState(ctxt);
          } catch (ApiException exception) {
            switch (exception.getStatusCode()) {
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                      Log.i(ctxt, TAG, "location settings are not valid, but could be fixed by showing the user a dialog");
                      // Location settings are not satisfied. But could be fixed by showing the
                      // user a dialog.
                      try {
                          // Cast to a resolvable exception.
                          ResolvableApiException resolvable = (ResolvableApiException) exception;
                          // Show the dialog by calling startResolutionForResult(),
                          // and check the result in onActivityResult().
                                    NotificationHelper.createResolveNotification(ctxt, Constants.TRACKING_ERROR_ID,
                            ctxt.getString(R.string.error_location_settings),
                            resolvable.getResolution());
                            ctxt.sendBroadcast(new ExplicitIntent(ctxt, R.string.transition_tracking_error));
                        } catch (ClassCastException e) {
                          // Ignore, should be an impossible error.
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        // Location settings are not satisfied. However, we have no way to fix the
                        // settings so we won't show the dialog.
                        Log.i(ctxt, TAG, "location settings are not valid, but cannot be fixed by showing a dialog");
                        NotificationHelper.createNotification(ctxt, Constants.TRACKING_ERROR_ID,
                  ctxt.getString(R.string.error_location_settings, exception.getStatusCode()));
                        ctxt.sendBroadcast(new ExplicitIntent(ctxt, R.string.transition_tracking_error));
                        break;
                    default:
                        Log.i(ctxt, TAG, "unkown error reading location");
                        NotificationHelper.createNotification(ctxt, Constants.TRACKING_ERROR_ID,
                                    ctxt.getString(R.string.unknown_error_location_settings));
                        ctxt.sendBroadcast(new ExplicitIntent(ctxt, R.string.transition_tracking_error));

                }
            }
        });
    }
}
