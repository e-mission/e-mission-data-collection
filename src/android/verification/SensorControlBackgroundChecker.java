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

import java.util.Arrays;

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

    public static void checkAppState(final Context ctxt) {
      NotificationHelper.cancelNotification(ctxt,
        SensorControlConstants.OPEN_APP_STATUS_PAGE);
      // check location settings. This is a separate function because it
      // currently has a callback. The others can be inlined here for greater
      // readability.
            checkLocationSettings(ctxt);
      boolean[] allOtherChecks = new boolean[]{
        SensorControlChecks.checkLocationPermissions(ctxt),
        SensorControlChecks.checkMotionActivityPermissions(ctxt),
        SensorControlChecks.checkNotificationsEnabled(ctxt),
        SensorControlChecks.checkUnusedAppsUnrestricted(ctxt)
      };
      boolean allOtherChecksPass = true;
      for (boolean check: allOtherChecks) {
        allOtherChecksPass = allOtherChecksPass && check;
            }
      if (allOtherChecksPass) {
        Log.d(ctxt, TAG, "All permissions (except location settings) valid, nothing to prompt");
        } else {
        Log.i(ctxt, TAG, "Curr status check results = "+
            " loc permission, motion permission, notification, unused apps "+ Arrays.toString(allOtherChecks));
            ctxt.sendBroadcast(new ExplicitIntent(ctxt, R.string.transition_tracking_error));
        generateOpenAppSettingsNotification(ctxt);
        }
      restartFSMIfStartState(ctxt);
    }

    public static void generateOpenAppSettingsNotification(Context ctxt) {
            Intent activityIntent = new Intent(ctxt, MainActivity.class);
      activityIntent.setAction(SensorControlConstants.OPEN_APP_STATUS_PAGE_ACTION);
      PendingIntent pi = PendingIntent.getActivity(ctxt, SensorControlConstants.OPEN_APP_STATUS_PAGE,
                    activityIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            NotificationHelper.createNotification(ctxt, SensorControlConstants.ENABLE_BACKGROUND_LOC_PERMISSION,
        ctxt.getString(R.string.fix_app_status_title),
        ctxt.getString(R.string.fix_app_status_text),
                    pi);
    }

    private static void checkLocationSettings(final Context ctxt) {
      SensorControlChecks.checkLocationSettings(ctxt, resultTask -> {
          try {
            LocationSettingsResponse response = resultTask.getResult(ApiException.class);
                        // All location settings are satisfied. The client can initialize location
                        // requests here.
            Log.i(ctxt, TAG, "All settings are valid, checking current state");
            Log.i(ctxt, TAG, "Current location settings are "+response);
          } catch (ApiException exception) {
            generateOpenAppSettingsNotification(ctxt);
            }
        });
    }
}
