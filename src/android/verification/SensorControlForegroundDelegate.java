package edu.berkeley.eecs.emission.cordova.tracker.verification;
// Auto fixed by post-plugin hook
import edu.berkeley.eecs.emission.R;


import edu.berkeley.eecs.emission.cordova.tracker.Constants;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;


/*
 * Deals with settings and resolutions when the app activity is visible.
 * Includes:
 * - Prompting for dynamic permissions
 * - Displaying resolution from the intent for tracking errors
 * - Dealing with user responses
 */

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;


import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import java.util.Arrays;
import java.util.Objects;

import edu.berkeley.eecs.emission.cordova.unifiedlogger.NotificationHelper;

public class SensorControlForegroundDelegate {
    public static final String TAG = "SensorPermissionsAndSettingsForegroundDelegate";

    private CordovaPlugin plugin = null;
    private CordovaInterface cordova = null;
    private CallbackContext cordovaCallback = null;

    public SensorControlForegroundDelegate(CordovaPlugin inPlugin,
                                           CordovaInterface inCordova) {
        plugin = inPlugin;
        cordova = inCordova;
    }

    // Invokes the callback with a boolean indicating whether
    // the location settings are correct or not
    public void checkLocationSettings(CallbackContext cordovaCallback) {
      Activity currActivity = cordova.getActivity();
      SensorControlChecks.checkLocationSettings(currActivity,
        resultTask -> {
          try {
            LocationSettingsResponse response = resultTask.getResult(ApiException.class);
            // All location settings are satisfied. The client can initialize location
            // requests here.
            Log.i(currActivity, TAG, "All settings are valid, checking current state");
            Log.i(currActivity, TAG, "Current location settings are "+response.getLocationSettingsStates());
            cordovaCallback.success(Objects.requireNonNull(response.getLocationSettingsStates()).toString());
          } catch (ApiException exception) {
            Log.i(currActivity, TAG, "Settings are not valid, returning "+exception.getMessage());
            cordovaCallback.error(exception.getLocalizedMessage());
          }
        });
    }

    public void checkAndPromptLocationSettings(CallbackContext callbackContext) {
      Activity currActivity = cordova.getActivity();
      SensorControlChecks.checkLocationSettings(currActivity, resultTask -> {
        try {
          LocationSettingsResponse response = resultTask.getResult(ApiException.class);
          // All location settings are satisfied. The client can initialize location
          // requests here.
          Log.i(currActivity, TAG, "All settings are valid, checking current state");
          JSONObject lssJSON = statesToJSON(response.getLocationSettingsStates());
          Log.i(currActivity, TAG, "Current location settings are "+lssJSON);
          callbackContext.success(lssJSON);
        } catch (ApiException exception) {
          switch (exception.getStatusCode()) {
            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
              Log.i(currActivity, TAG, "location settings are not valid, but could be fixed by showing the user a dialog");
              // Location settings are not satisfied. But could be fixed by showing the
              // user a dialog.
              try {
                // Cast to a resolvable exception.
                ResolvableApiException resolvable = (ResolvableApiException) exception;
                // Show the dialog by calling startResolutionForResult(),
                // and check the result in onActivityResult().
                // Experiment with "send" instead of this resolution code
                this.cordovaCallback = callbackContext;
                cordova.setActivityResultCallback(plugin);
                resolvable.startResolutionForResult(currActivity, SensorControlConstants.ENABLE_LOCATION_SETTINGS);
              } catch (IntentSender.SendIntentException | ClassCastException sie) {
                callbackContext.error(sie.getLocalizedMessage());
              }
              break;
            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
              // Location settings are not satisfied. However, we have no way to fix the
              // settings so we won't show the dialog.
              Log.i(currActivity, TAG, "location settings are not valid, but cannot be fixed by showing a dialog");
              openLocationSettingsPage(callbackContext);
              break;
            default:
              Log.i(currActivity, TAG, "unknown error reading location");
              openLocationSettingsPage(callbackContext);
          }
        } catch (JSONException e) {
          callbackContext.error(e.getLocalizedMessage());
        }
        });
    }

    private static JSONObject statesToJSON(LocationSettingsStates lss) throws JSONException {
      // TODO: Does this need to be internationalized?
      if (lss == null) {
        throw new JSONException("null input");
      }
      JSONObject jo = new JSONObject();
      jo.put("Bluetooth", lss.isBleUsable());
      jo.put("GPS", lss.isGpsUsable());
      jo.put("Network", lss.isNetworkLocationUsable());
      jo.put("location", lss.isLocationUsable());
      return jo;
    }

    private void openLocationSettingsPage(CallbackContext callbackContext) {
        this.cordovaCallback = callbackContext;
        cordova.setActivityResultCallback(plugin);
        Intent locSettingsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        cordova.getActivity().startActivityForResult(locSettingsIntent,
          SensorControlConstants.ENABLE_LOCATION_SETTINGS_MANUAL);
    }

    public void checkAndPromptPermissions() {
        checkAndPromptLocationPermissions();
        checkAndPromptMotionActivityPermissions();
    }

    private void checkAndPromptLocationPermissions() {
        if(cordova.hasPermission(SensorControlConstants.LOCATION_PERMISSION) &&
          cordova.hasPermission(SensorControlConstants.BACKGROUND_LOC_PERMISSION)) {
            SensorControlBackgroundChecker.restartFSMIfStartState(cordova.getActivity());
            return;
        }
        // If this is android 11 (API 30), we want to launch the app settings instead of prompting for permission
        // because the default permission prompting does not offer "always" as an option
        // https://github.com/e-mission/e-mission-docs/issues/608
        // we don't really care about which level of permission is missing since the prompt doesn't
        // do anything anyway. If either permission is missing, we just open the app settings
        // Note also that we should actually check for VERSION_CODES.R
        // but since we are not targeting API 30 yet, we can't do that
        // so we use Q (29) + 1 instead. I think that is more readable than 30
        if ((Build.VERSION.SDK_INT >= (Build.VERSION_CODES.Q + 1)) &&
          (!cordova.hasPermission(SensorControlConstants.LOCATION_PERMISSION) ||
           !cordova.hasPermission(SensorControlConstants.BACKGROUND_LOC_PERMISSION))) {
          Activity mAct = cordova.getActivity();
          String msgString = " LOC = "+cordova.hasPermission(SensorControlConstants.LOCATION_PERMISSION)+
            " BACKGROUND LOC "+ cordova.hasPermission(SensorControlConstants.BACKGROUND_LOC_PERMISSION)+
            " Android R+, so opening app settings anyway";
          Log.i(cordova.getActivity(), TAG, msgString);
          // These are to hopefully help us get a callback once the settings are changed
          Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
          intent.setData(Uri.fromParts("package", mAct.getPackageName(), null));
          cordova.setActivityResultCallback(plugin);
          mAct.startActivityForResult(intent, SensorControlConstants.ENABLE_BOTH_PERMISSION);
          return;
        }
        if(!cordova.hasPermission(SensorControlConstants.LOCATION_PERMISSION) &&
          (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) &&
          !cordova.hasPermission(SensorControlConstants.BACKGROUND_LOC_PERMISSION)) {
          Log.i(cordova.getActivity(), TAG, "Both permissions missing, requesting both");
          cordova.requestPermissions(plugin, SensorControlConstants.ENABLE_BOTH_PERMISSION,
            new String[]{SensorControlConstants.LOCATION_PERMISSION, SensorControlConstants.BACKGROUND_LOC_PERMISSION});
          return;
        }
        if(!cordova.hasPermission(SensorControlConstants.LOCATION_PERMISSION)) {
            Log.i(cordova.getActivity(), TAG, "Only location permission missing, requesting it");
            cordova.requestPermission(plugin, SensorControlConstants.ENABLE_LOCATION_PERMISSION, SensorControlConstants.LOCATION_PERMISSION);
            return;
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !cordova.hasPermission(SensorControlConstants.BACKGROUND_LOC_PERMISSION)) {
            Log.i(cordova.getActivity(), TAG, "Only background permission missing, requesting it");
            cordova.requestPermission(plugin, SensorControlConstants.ENABLE_BACKGROUND_LOC_PERMISSION, SensorControlConstants.BACKGROUND_LOC_PERMISSION);
            return;
        }
    }

    private void checkAndPromptMotionActivityPermissions() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !cordova.hasPermission(SensorControlConstants.MOTION_ACTIVITY_PERMISSION)) {
            Log.i(cordova.getActivity(), TAG, "Only motion activity permission missing, requesting it");
            cordova.requestPermission(plugin, SensorControlConstants.ENABLE_MOTION_ACTIVITY_PERMISSION, SensorControlConstants.MOTION_ACTIVITY_PERMISSION);
            return;
        }
    }

    private void displayResolution(PendingIntent resolution) {
        if (resolution != null) {
            try {
                cordova.setActivityResultCallback(plugin);
                cordova.getActivity().startIntentSenderForResult(resolution.getIntentSender(), SensorControlConstants.ENABLE_LOCATION_SETTINGS, null, 0, 0, 0, null);
            } catch (IntentSender.SendIntentException e) {
                Context mAct = cordova.getActivity();
                NotificationHelper.createNotification(mAct, Constants.TRACKING_ERROR_ID, mAct.getString(R.string.unable_resolve_issue));
            }
        }
    }

    public void onNewIntent(Intent intent) {
        if(SensorControlConstants.ENABLE_LOCATION_PERMISSION_ACTION.equals(intent.getAction()) ||
           SensorControlConstants.ENABLE_BACKGROUND_LOC_PERMISSION_ACTION.equals(intent.getAction())) {
            checkAndPromptLocationPermissions();
            return;
        }

        if(SensorControlConstants.ENABLE_MOTION_ACTIVITY_PERMISSION_ACTION.equals(intent.getAction())) {
            checkAndPromptMotionActivityPermissions();
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
            case SensorControlConstants.ENABLE_MOTION_ACTIVITY_PERMISSION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    NotificationHelper.cancelNotification(cordova.getActivity(), SensorControlConstants.ENABLE_MOTION_ACTIVITY_PERMISSION);
                    // motion activity does not affect the FSM
                } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    SensorControlBackgroundChecker.generateMotionActivityEnableNotification(cordova.getActivity());
                }
                break;
            default:
                Log.e(cordova.getActivity(), TAG, "Unknown permission code "+requestCode+" ignoring");
        }
    }


  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    Activity mAct = cordova.getActivity();
    switch (requestCode) {
      case SensorControlConstants.ENABLE_LOCATION_SETTINGS:
        Log.d(mAct, TAG, requestCode + " is our code, handling callback");
        cordova.setActivityResultCallback(null);
        final LocationSettingsStates states = LocationSettingsStates.fromIntent(data);
        Log.d(cordova.getActivity(), TAG, "at this point, isLocationUsable = " + (states != null && states.isLocationUsable()));
        switch (resultCode) {
          case Activity.RESULT_OK:
            // All required changes were successfully made
            Log.i(cordova.getActivity(), TAG, "All changes successfully made, reinitializing");
            try {
              cordovaCallback.success(statesToJSON(states));
            } catch (JSONException e) {
              cordovaCallback.error(mAct.getString(R.string.unknown_error_location_settings));
            }
            break;
          case Activity.RESULT_CANCELED:
            // The user was asked to change settings, but chose not to
            Log.e(cordova.getActivity(), TAG, "User chose not to change settings, dunno what to do");
            cordovaCallback.error(mAct.getString(R.string.user_rejected_setting));
            break;
          default:
            cordovaCallback.error(mAct.getString(R.string.unable_resolve_issue));
            Log.e(cordova.getActivity(), TAG, "Unknown result code while enabling location " + resultCode);
            break;
        }
      case SensorControlConstants.ENABLE_LOCATION_SETTINGS_MANUAL:
        Log.d(mAct, TAG, requestCode + " is our code, handling callback");
        cordova.setActivityResultCallback(null);
        // this will call the callback with success or error
        checkLocationSettings(cordovaCallback);
        break;
      case SensorControlConstants.ENABLE_BOTH_PERMISSION:
        Log.d(mAct, TAG, requestCode + " is our code, handling callback");
        cordova.setActivityResultCallback(null);
        Log.d(mAct, TAG, "Got permission callback from launching app settings");
        if (cordova.hasPermission(SensorControlConstants.LOCATION_PERMISSION)) {
          // location permission enabled, cancelling notification
          NotificationHelper.cancelNotification(cordova.getActivity(), SensorControlConstants.ENABLE_LOCATION_PERMISSION);
        }
        if (cordova.hasPermission(SensorControlConstants.BACKGROUND_LOC_PERMISSION)) {
          // background location permission enabled, cancelling notification
          NotificationHelper.cancelNotification(cordova.getActivity(), SensorControlConstants.ENABLE_BACKGROUND_LOC_PERMISSION);
        }
        SensorControlBackgroundChecker.restartFSMIfStartState(cordova.getActivity());
        break;
      default:
        Log.d(cordova.getActivity(), TAG, "Got unsupported request code " + requestCode + " , ignoring...");
    }
  }
}
