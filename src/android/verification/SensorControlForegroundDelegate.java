package edu.berkeley.eecs.emission.cordova.tracker.verification;
// Auto fixed by post-plugin hook
import edu.berkeley.eecs.emission.cordova.tracker.Constants;
import edu.berkeley.eecs.emission.cordova.tracker.location.TripDiaryStateMachineService;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import gov.colorado.energyoffice.emission.R;

/*
 * Deals with settings and resolutions when the app activity is visible.
 * Includes:
 * - Prompting for dynamic permissions
 * - Displaying resolution from the intent for tracking errors
 * - Dealing with user responses
 */

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONException;

import android.app.Activity;
import android.content.Context;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.Build;

import com.google.android.gms.location.LocationSettingsStates;

import java.util.Arrays;

import edu.berkeley.eecs.emission.cordova.unifiedlogger.NotificationHelper;

import static edu.berkeley.eecs.emission.cordova.tracker.verification.SensorControlConstants.BACKGROUND_LOC_PERMISSION;
import static edu.berkeley.eecs.emission.cordova.tracker.verification.SensorControlConstants.ENABLE_LOCATION_SETTINGS;
import static edu.berkeley.eecs.emission.cordova.tracker.verification.SensorControlConstants.LOCATION_PERMISSION;

public class SensorControlForegroundDelegate {
    public static final String TAG = "SensorPermissionsAndSettingsForegroundDelegate";

    private CordovaPlugin plugin = null;
    private CordovaInterface cordova = null;

    public SensorControlForegroundDelegate(CordovaPlugin iplugin, CordovaInterface icordova) {
        plugin = iplugin;
        cordova = icordova;
    }

    public void checkAndPromptPermissions() {
        if(cordova.hasPermission(LOCATION_PERMISSION) && cordova.hasPermission(BACKGROUND_LOC_PERMISSION)) {
            SensorControlBackgroundChecker.restartFSMIfStartState(cordova.getActivity());
            return;
        }
        if(!cordova.hasPermission(LOCATION_PERMISSION) &&
          (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) &&
          !cordova.hasPermission(BACKGROUND_LOC_PERMISSION)) {
          Log.i(cordova.getActivity(), TAG, "Both permissions missing, requesting both");
          cordova.requestPermissions(plugin, SensorControlConstants.ENABLE_BOTH_PERMISSION,
            new String[]{LOCATION_PERMISSION, BACKGROUND_LOC_PERMISSION});
          return;
        }
        if(!cordova.hasPermission(LOCATION_PERMISSION)) {
            Log.i(cordova.getActivity(), TAG, "Only location permission missing, requesting it");
            cordova.requestPermission(plugin, SensorControlConstants.ENABLE_LOCATION_PERMISSION, LOCATION_PERMISSION);
            return;
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !cordova.hasPermission(BACKGROUND_LOC_PERMISSION)) {
            Log.i(cordova.getActivity(), TAG, "Only background permission missing, requesting it");
            cordova.requestPermission(plugin, SensorControlConstants.ENABLE_BACKGROUND_LOC_PERMISSION, BACKGROUND_LOC_PERMISSION);
            return;
        }
    }

    private void displayResolution(PendingIntent resolution) {
        if (resolution != null) {
            try {
                cordova.setActivityResultCallback(plugin);
                cordova.getActivity().startIntentSenderForResult(resolution.getIntentSender(), ENABLE_LOCATION_SETTINGS, null, 0, 0, 0, null);
            } catch (IntentSender.SendIntentException e) {
                Context mAct = cordova.getActivity();
                NotificationHelper.createNotification(mAct, Constants.TRACKING_ERROR_ID, mAct.getString(R.string.unable_resolve_issue));
            }
        }
    }

    public void onNewIntent(Intent intent) {
        if(SensorControlConstants.ENABLE_LOCATION_PERMISSION_ACTION.equals(intent.getAction()) ||
           SensorControlConstants.ENABLE_BACKGROUND_LOC_PERMISSION_ACTION.equals(intent.getAction())) {
            checkAndPromptPermissions();
            return;
        }
        if (NotificationHelper.DISPLAY_RESOLUTION_ACTION.equals(intent.getAction())) {
            PendingIntent piFromIntent = intent.getParcelableExtra(
                    NotificationHelper.RESOLUTION_PENDING_INTENT_KEY);
            displayResolution(piFromIntent);
            return;
        }
        Log.i(cordova.getActivity(), TAG, "Action "+intent.getAction()+" unknown, ignoring ");
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException
    {
        Log.i(cordova.getActivity(), TAG, "onRequestPermissionResult called with "+requestCode);
        Log.i(cordova.getActivity(), TAG, "permissions are "+ Arrays.toString(permissions));
        Log.i(cordova.getActivity(), TAG, "grantResults are "+Arrays.toString(grantResults));
        /*
         Let us figure out if we want to sent a javascript callback with the error.
         This is currently only called from markConsented, and I don't think we listen to failures there
        for(int r:grantResults)
        {
            if(r == PackageManager.PERMISSION_DENIED)
            {
                this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, PERMISSION_DENIED_ERROR));
                return;
            }
        }
         */
        switch(requestCode)
        {
          case SensorControlConstants.ENABLE_BOTH_PERMISSION:
            if ((grantResults[0] == PackageManager.PERMISSION_GRANTED) &&
              (grantResults[1] == PackageManager.PERMISSION_GRANTED)) {
              NotificationHelper.cancelNotification(cordova.getActivity(), SensorControlConstants.ENABLE_BOTH_PERMISSION);
              SensorControlBackgroundChecker.restartFSMIfStartState(cordova.getActivity());
            } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
              SensorControlBackgroundChecker.generateLocationEnableNotification(cordova.getActivity());
            } else if (grantResults[1] == PackageManager.PERMISSION_DENIED) {
              SensorControlBackgroundChecker.generateBackgroundLocEnableNotification(cordova.getActivity());
            }
            break;
            case SensorControlConstants.ENABLE_LOCATION_PERMISSION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    NotificationHelper.cancelNotification(cordova.getActivity(), SensorControlConstants.ENABLE_LOCATION_PERMISSION);
                    SensorControlBackgroundChecker.restartFSMIfStartState(cordova.getActivity());
                } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    SensorControlBackgroundChecker.generateLocationEnableNotification(cordova.getActivity());
                }
                break;
            case SensorControlConstants.ENABLE_BACKGROUND_LOC_PERMISSION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    NotificationHelper.cancelNotification(cordova.getActivity(), SensorControlConstants.ENABLE_BACKGROUND_LOC_PERMISSION);
                    SensorControlBackgroundChecker.restartFSMIfStartState(cordova.getActivity());
                } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    SensorControlBackgroundChecker.generateBackgroundLocEnableNotification(cordova.getActivity());
                }
                break;
            default:
                Log.e(cordova.getActivity(), TAG, "Unknown permission code "+requestCode+" ignoring");
        }
    }


  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    switch (requestCode) {
      case ENABLE_LOCATION_SETTINGS:
        Activity mAct = cordova.getActivity();
        Log.d(mAct, TAG, requestCode + " is our code, handling callback");
        cordova.setActivityResultCallback(null);
        final LocationSettingsStates states = LocationSettingsStates.fromIntent(data);
        Log.d(cordova.getActivity(), TAG, "at this point, isLocationUsable = " + states.isLocationUsable());
        switch (resultCode) {
          case Activity.RESULT_OK:
            // All required changes were successfully made
            Log.i(cordova.getActivity(), TAG, "All changes successfully made, reinitializing");
            NotificationHelper.cancelNotification(mAct, Constants.TRACKING_ERROR_ID);
            SensorControlBackgroundChecker.restartFSMIfStartState(mAct);
            break;
          case Activity.RESULT_CANCELED:
            // The user was asked to change settings, but chose not to
            Log.e(cordova.getActivity(), TAG, "User chose not to change settings, dunno what to do");
            break;
          default:
            Log.e(cordova.getActivity(), TAG, "Unknown result code while enabling location " + resultCode);
            break;
        }
      default:
        Log.d(cordova.getActivity(), TAG, "Got unsupported request code " + requestCode + " , ignoring...");
    }
  }
}
