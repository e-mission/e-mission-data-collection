package edu.berkeley.eecs.emission.cordova.tracker.verification;
// Auto fixed by post-plugin hook
import edu.berkeley.eecs.emission.R;
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
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import edu.berkeley.eecs.emission.cordova.tracker.Constants;
import edu.berkeley.eecs.emission.cordova.tracker.ExplicitIntent;
import edu.berkeley.eecs.emission.cordova.tracker.location.TripDiaryStateMachineService;
import edu.berkeley.eecs.emission.cordova.tracker.location.actions.LocationTrackingActions;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.NotificationHelper;



/*
 * Deals with settings and resolutions from the background as a service.
 * A bunch of static functions for ensuring that all the settings match what we
 * want, and prompts users to fix them. The actual fix will happen in the
 * foreground as the user clicks on the fixit notification.
 */

public class SensorControlBackgroundChecker {
    public static String TAG = "SensorControlBackgroundChecker";

    private static int STATE_IN_NUMBERS = 78283;

    private static JSONObject OPEN_APP_STATUS_PAGE(Context ctxt) {
      try {
        JSONObject config = new JSONObject();
        config.put("id", SensorControlConstants.OPEN_APP_STATUS_PAGE);
        config.put("title", ctxt.getString(R.string.fix_app_status_title));
        config.put("text", ctxt.getString(R.string.fix_app_status_text));
        JSONObject redirectData = new JSONObject();
        redirectData.put("redirectTo", "root.main.control");
        JSONObject redirectParams = new JSONObject();
        redirectParams.put("launchAppStatusModal", true);
        redirectData.put("redirectParams", redirectParams);
        config.put("data", redirectData);
        return config;
      } catch (JSONException e) {
        e.printStackTrace();
      }
      return null;
    }

    public static void restartFSMIfStartState(Context ctxt) {
        String START_STATE = ctxt.getString(R.string.state_start);
        String currState = TripDiaryStateMachineService.getState(ctxt);
        Log.i(ctxt, TAG, "in restartFSMIfStartState, currState = "+currState);
        if (START_STATE.equals(currState)) {
            Log.i(ctxt, TAG, "in start state, sending initialize");
            ctxt.sendBroadcast(new ExplicitIntent(ctxt, R.string.transition_initialize));
        }
    }

    public static void checkAppState(final Context ctxt) {
      NotificationHelper.cancelNotification(ctxt,
        SensorControlConstants.OPEN_APP_STATUS_PAGE);
      SensorControlChecks.checkLocationSettings(ctxt, resultTask -> {
        try {
          LocationSettingsResponse response = resultTask.getResult(ApiException.class);
          // All location settings are satisfied. The client can initialize location
          // requests here.
          Log.i(ctxt, TAG, "All settings are valid, checking current state");
          Log.i(ctxt, TAG, "Current location settings are "+response);

          // Now that we know that the location settings are correct, we start the permission checks
      boolean[] allOtherChecks = new boolean[]{
        SensorControlChecks.checkLocationPermissions(ctxt),
        SensorControlChecks.checkIgnoreBatteryOptimizations(ctxt),
        SensorControlChecks.checkMotionActivityPermissions(ctxt),
        SensorControlChecks.checkNotificationsEnabled(ctxt),
      };
      boolean allOtherChecksPass = true;
      for (boolean check: allOtherChecks) {
        allOtherChecksPass = allOtherChecksPass && check;
            }

      /*
       Using index-based iteration since we need to start from index 1 instead of 0 and array slices
       are hard in Java
       */
      boolean nonLocChecksPass = true;
      for (int i = 2; i < allOtherChecks.length; i++) {
        nonLocChecksPass = nonLocChecksPass && allOtherChecks[i];
      }

      if (allOtherChecksPass) {
            Log.d(ctxt, TAG, "All settings valid, nothing to prompt");
        restartFSMIfStartState(ctxt);
      }
      else if (allOtherChecks[0] && allOtherChecks[1]) {
            Log.i(ctxt, TAG, "all checks = "+allOtherChecksPass+" but location permission status  "+allOtherChecks[0]+" should be true "+
          " so one of the non-location checks must be false: loc permission, ignore optimization, motion permission, notification" + Arrays.toString(allOtherChecks));
        Log.i(ctxt, TAG, "a non-local check failed, generating only user visible notification");
        generateOpenAppSettingsNotification(ctxt);
      }
      else {
            Log.i(ctxt, TAG, "location settings are valid, but location permission is not, generating tracking error and visible notification");
            Log.i(ctxt, TAG, "curr status check results = " +
            " loc permission, ignore optimization, motion permission, notification"+ Arrays.toString(allOtherChecks));
            ctxt.sendBroadcast(new ExplicitIntent(ctxt, R.string.transition_tracking_error));
        generateOpenAppSettingsNotification(ctxt);
        }
          } catch (ApiException exception) {
          Log.i(ctxt, TAG, "location settings are invalid, generating tracking error and visible notification");
          ctxt.sendBroadcast(new ExplicitIntent(ctxt, R.string.transition_tracking_error));
            generateOpenAppSettingsNotification(ctxt);
            }
        });

          /*
           * AsyncTask is deprecated, so let's try to use the standard concurrent utilities here.
           * Haven't used raw java concurrent utilities for 20 years (new Thread(...)) anyone?
           * So this code snippet is based on
           * https://stackoverflow.com/a/64969640/4040267
           *
           * Do we need to do anything in the UI thread. The `checkUnusedAppsUnrestricted`
           * doesn't say anything about which thread to call it from. What about
           * generateOpenAppSettingsNotification? Nothing in the NotificationManager indicates that
           * it needs to run on the UI thread either.
           */
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        executor.schedule(() -> {
          if (!SensorControlChecks.checkUnusedAppsUnrestricted(ctxt)) {
            Log.i(ctxt, TAG, "all current settings and permissions are probably valid, but could be reset later");
            Log.i(ctxt, TAG, "don't generate a tracking error right now, but let's ask the user to avoid the reset ");
            generateOpenAppSettingsNotification(ctxt);
          }
        }, 1, TimeUnit.MINUTES);
    }

    public static void generateOpenAppSettingsNotification(Context ctxt) {
      NotificationHelper.schedulePluginCompatibleNotification(ctxt, OPEN_APP_STATUS_PAGE(ctxt), null);
    }
}
