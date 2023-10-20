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
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings;
import android.content.DialogInterface;
import android.app.AlertDialog;


import androidx.core.app.ActivityCompat;
import androidx.core.content.IntentCompat;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import edu.berkeley.eecs.emission.cordova.unifiedlogger.NotificationHelper;

public class SensorControlForegroundDelegate {
    public static final String TAG = "SensorPermissionsAndSettingsForegroundDelegate";

    private CordovaPlugin plugin = null;
    private CordovaInterface cordova = null;
    private CallbackContext cordovaCallback = null;
    private PermissionPopupChecker permissionChecker = null;
    private Map<Integer, PermissionPopupChecker> permissionCheckerMap = new HashMap<>();

    class PermissionPopupChecker {
      int permissionStatusConstant = -1;
      boolean shouldShowRequestRationaleBefore = false;
      String deniedString;
      String retryString;
      boolean openAppSettings;

      String[] currPermissions;

      public PermissionPopupChecker(int permissionStatusConstant,
                                    String[] permissions,
                                    String retryString,
                                    String deniedString) {
        this.permissionStatusConstant = permissionStatusConstant;
        currPermissions = permissions;
        this.deniedString = deniedString;
        this.retryString = retryString;
      }

      private boolean shouldShowRequestForCurrPermissions() {
        boolean ssrrb = false;
        for (String cp: currPermissions){
          boolean css = ActivityCompat.shouldShowRequestPermissionRationale(cordova.getActivity(), cp);
          Log.d(cordova.getActivity(), TAG, "For permission "+cp+" shouldShowRequest = " + css);
          ssrrb |= css;
        }
        return ssrrb;
      }

      void requestPermission() {
        shouldShowRequestRationaleBefore = shouldShowRequestForCurrPermissions();
        Log.d(cordova.getActivity(), TAG,
          String.format("After iterating over all entries in %s shouldShowRequest = %s", currPermissions, shouldShowRequestRationaleBefore));
        if (openAppSettings) {
          SensorControlForegroundDelegate.this.openAppSettingsPage(cordovaCallback, permissionStatusConstant);
        } else if (permissionStatusConstant == SensorControlConstants.IGNORE_BATTERY_OPTIMIZATIONS) {
          SensorControlForegroundDelegate.this.openRequestBatteryOptimization(cordovaCallback, permissionStatusConstant);
        } else {
          if (currPermissions.length > 1) {
            cordova.requestPermissions(plugin, permissionStatusConstant, currPermissions);
          } else {
            Log.e(cordova.getActivity(), TAG, "currPermissions.length = " + currPermissions.length);
            cordova.requestPermission(plugin, permissionStatusConstant, currPermissions[0]);
          }
        }
      }

      void generateErrorCallback() {
        if (cordovaCallback == null) {
          NotificationHelper.createNotification(cordova.getActivity(), Constants.TRACKING_ERROR_ID, null, "Please upload log and report issue in generateErrorCallback");
          return;
        }
        boolean shouldShowRequestRationaleAfter = shouldShowRequestForCurrPermissions();
        Log.d(cordova.getActivity(), TAG, "In permission prompt, error callback,"+
            " before = "+shouldShowRequestRationaleBefore+" after = "+shouldShowRequestRationaleAfter);
        // see the issue for more details
        // https://github.com/e-mission/e-mission-docs/issues/680#issuecomment-958438153

        if (permissionStatusConstant == SensorControlConstants.LOCATION_INTERMEDIARY) {
          cordovaCallback.error(cordova.getActivity().getString(R.string.location_permission_denied_fine));
          return;
        }
        if (permissionStatusConstant == SensorControlConstants.ENABLE_BACKGROUND_LOC_PERMISSION) {
          String errorMessage = SensorControlForegroundDelegate.this.generateLocationPermissionError();
          cordovaCallback.error(errorMessage);
          return;
        }
        if (!shouldShowRequestRationaleBefore && !shouldShowRequestRationaleAfter) {
          // before = FALSE, after = FALSE => user had denied it earlier
          openAppSettings = true;
          cordovaCallback.error(deniedString);
        }
        if (!shouldShowRequestRationaleBefore && shouldShowRequestRationaleAfter) {
          // before = FALSE, after = TRUE => first time ask
          cordovaCallback.error(retryString);
        }
        if (shouldShowRequestRationaleBefore && !shouldShowRequestRationaleAfter) {
          // before = TRUE, after = FALSE => popup was shown, user hit don't ask
          openAppSettings = true;
          cordovaCallback.error(deniedString);
        }
         if (shouldShowRequestRationaleBefore && shouldShowRequestRationaleAfter) {
           // before = TRUE, after = TRUE => popup was shown, user hit deny ONLY
           cordovaCallback.error(retryString);
         }
      }
    }

    public SensorControlForegroundDelegate(CordovaPlugin inPlugin,
                                           CordovaInterface inCordova) {
        plugin = inPlugin;
        cordova = inCordova;
    }

    private PermissionPopupChecker getPermissionChecker(int permissionStatusConstant,
                                                      String permission,
                                                      String retryString,
                                                      String deniedString) {
      return getPermissionChecker(permissionStatusConstant, new String[]{permission}, retryString, deniedString);
    }
    private PermissionPopupChecker getPermissionChecker(int permissionStatusConstant,
                                 String[] permissions,
                                 String retryString,
                                 String deniedString) {
      PermissionPopupChecker pc = permissionCheckerMap.get(Integer.valueOf(permissionStatusConstant));
      if (pc == null) {
         pc = new PermissionPopupChecker(permissionStatusConstant,
          permissions, retryString, deniedString);
        permissionCheckerMap.put(Integer.valueOf(permissionStatusConstant), pc);
      }
      return pc;
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
          SensorControlBackgroundChecker.restartFSMIfStartState(cordova.getActivity());
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

    private void openAppSettingsPage(CallbackContext callbackContext, int requestCode) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", cordova.getActivity().getPackageName(), null));
        this.cordovaCallback = callbackContext;
        cordova.setActivityResultCallback(plugin);
        cordova.getActivity().startActivityForResult(intent, requestCode);
    }

    /**
     * Generates dialog that explains to the user what needs to be turned on in the settings page.
     * This is for the secondary flow where the user denied the initial request and now needs to go 
     * through the old flow of just opening up the app settings page.
     * 
     * @param callbackContext
     * @param requestCode
     */
    public void beforeAppSettingsDialog(CallbackContext callbackContext, int requestCode){
      new AlertDialog.Builder(cordova.getActivity())
      .setTitle(R.string.location_permission_intermediary_title)
      .setMessage(R.string.location_permission_settings_message)
      .setPositiveButton("OK", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            openAppSettingsPage(callbackContext, requestCode);
          }
      })
      .setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            callbackContext.error(cordova.getActivity().getString(R.string.location_feedback_denied_popup));
          }
      })
      .create().show();
    }

    /**
     * Generates dialog to ask user for additional location permissions, before redirecting them to the
     * location permission settings page. This is used in the first flow which sends the user straight 
     * to the location permissions page.
     */
    public void intermediaryLocationDialog(PermissionPopupChecker permissionChecker){
      new AlertDialog.Builder(cordova.getActivity())
      .setTitle(R.string.location_permission_intermediary_title)
      .setMessage(R.string.location_permission_intermediary_message)
      .setPositiveButton("OK", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            permissionChecker.requestPermission();
          }
      })
      .setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
              permissionChecker.generateErrorCallback();
          }
      })
      .create().show();
    }

    /**
     * Generates location permission error based off of what permissions the user currently has given.
     * 3 different states: NO fine && NO background, NO fine, NO background 
     * 
     * @return Error String
     */
    public String generateLocationPermissionError() {
      if (!cordova.hasPermission(SensorControlConstants.LOCATION_PERMISSION) && !cordova.hasPermission(SensorControlConstants.BACKGROUND_LOC_PERMISSION)){
        return cordova.getActivity().getString(R.string.location_feedback_both_off);
      } else if (!cordova.hasPermission(SensorControlConstants.LOCATION_PERMISSION)) {
        // No fine loc
        return cordova.getActivity().getString(R.string.location_feedback_fine_off);
      } else {
        // No background loc
        return cordova.getActivity().getString(R.string.location_feedback_background_off);
      }
      // cordova.hasPermission(SensorControlConstants.BACKGROUND_LOC_PERMISSION)
    }

    public void checkLocationPermissions(CallbackContext cordovaCallback) {
      boolean validPerms = SensorControlChecks.checkLocationPermissions(cordova.getActivity());
      if(validPerms) {
        cordovaCallback.success();
      } else {
        cordovaCallback.error(cordova.getActivity().getString(R.string.location_permission_off));
      }
    }

    public void checkAndPromptLocationPermissions(CallbackContext cordovaCallback) {
        if(cordova.hasPermission(SensorControlConstants.LOCATION_PERMISSION) &&
          cordova.hasPermission(SensorControlConstants.BACKGROUND_LOC_PERMISSION)) {
            SensorControlBackgroundChecker.restartFSMIfStartState(cordova.getActivity());
            cordovaCallback.success();
        }
        // On Android 11 and above, you are no longer allowed to directly request location permissions
        // with "allow all the time" as an option. We are now prompting the user for "FINE" location
        // permissions first, and then sending them directly to the location permissions settings. If
        // the user backs out or denies at any stage, our privileges to send the user directly to the location
        // permissions page are revoked, so we just send them to the generic app settings page like before.
        if (Build.VERSION.SDK_INT >= (Build.VERSION_CODES.R)) { 
          String msgString = " FINE LOC = "+cordova.hasPermission(SensorControlConstants.LOCATION_PERMISSION)+
            " BACKGROUND LOC "+ cordova.hasPermission(SensorControlConstants.BACKGROUND_LOC_PERMISSION);
          Log.i(cordova.getActivity(), TAG, msgString);
          
          if (ActivityCompat.shouldShowRequestPermissionRationale(cordova.getActivity(), SensorControlConstants.BACKGROUND_LOC_PERMISSION) && 
              !ActivityCompat.shouldShowRequestPermissionRationale(cordova.getActivity(), SensorControlConstants.LOCATION_PERMISSION)) {
              Log.i(cordova.getActivity(), TAG, "Has neither, request both!");
              this.cordovaCallback = cordovaCallback;
              this.permissionChecker = getPermissionChecker(
                SensorControlConstants.LOCATION_INTERMEDIARY,
                SensorControlConstants.LOCATION_PERMISSION,
                cordova.getActivity().getString(R.string.location_permission_off),
                cordova.getActivity().getString(R.string.location_permission_off_app_open));
              this.permissionChecker.requestPermission();
              return;
            } else {
              Log.i(cordova.getActivity(), TAG, "User has denied previous requests, just show app settings!");
              // Go to the dialog first, which then sends them to the app settings.
              beforeAppSettingsDialog(cordovaCallback, SensorControlConstants.ENABLE_BOTH_PERMISSION);
              return;
            }
        }
        if(!cordova.hasPermission(SensorControlConstants.LOCATION_PERMISSION) &&
          (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) &&
          !cordova.hasPermission(SensorControlConstants.BACKGROUND_LOC_PERMISSION)) {
          Log.i(cordova.getActivity(), TAG, "Both permissions missing, requesting both");
          this.cordovaCallback = cordovaCallback;
          this.permissionChecker = getPermissionChecker(
            SensorControlConstants.ENABLE_BOTH_PERMISSION,
            new String[]{SensorControlConstants.BACKGROUND_LOC_PERMISSION, SensorControlConstants.LOCATION_PERMISSION},
            cordova.getActivity().getString(R.string.location_permission_off),
            cordova.getActivity().getString(R.string.location_permission_off_app_open));
          this.permissionChecker.requestPermission();
          return;
        }
        if(!cordova.hasPermission(SensorControlConstants.LOCATION_PERMISSION)) {
            Log.i(cordova.getActivity(), TAG, "before call shouldShowRequestPermissionRationale = "+ ActivityCompat.shouldShowRequestPermissionRationale(cordova.getActivity(), SensorControlConstants.LOCATION_PERMISSION));
            Log.i(cordova.getActivity(), TAG, "Only location permission missing, requesting it");
            this.cordovaCallback = cordovaCallback;
            this.permissionChecker = getPermissionChecker(
              SensorControlConstants.ENABLE_LOCATION_PERMISSION,
              SensorControlConstants.LOCATION_PERMISSION,
              cordova.getActivity().getString(R.string.location_permission_off),
              cordova.getActivity().getString(R.string.location_permission_off_app_open));
            this.permissionChecker.requestPermission();
            return;
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !cordova.hasPermission(SensorControlConstants.BACKGROUND_LOC_PERMISSION)) {
            Log.i(cordova.getActivity(), TAG, "Only background permission missing, requesting it");
            this.cordovaCallback = cordovaCallback;
            this.permissionChecker = getPermissionChecker(
              SensorControlConstants.ENABLE_BACKGROUND_LOC_PERMISSION,
              SensorControlConstants.BACKGROUND_LOC_PERMISSION,
              cordova.getActivity().getString(R.string.location_permission_off),
              cordova.getActivity().getString(R.string.location_permission_off_app_open));
            this.permissionChecker.requestPermission();
            return;
        }
    }

    public void checkMotionActivityPermissions(CallbackContext cordovaCallback) {
      boolean validPerms = SensorControlChecks.checkMotionActivityPermissions(cordova.getActivity());
      if(validPerms) {
        cordovaCallback.success();
      } else {
        cordovaCallback.error(cordova.getActivity().getString(R.string.activity_permission_off));
      }
    }

    public void checkAndPromptMotionActivityPermissions(CallbackContext cordovaCallback) {
      boolean validPerms = SensorControlChecks.checkMotionActivityPermissions(cordova.getActivity());
      if(validPerms) {
        SensorControlBackgroundChecker.restartFSMIfStartState(cordova.getActivity());
        cordovaCallback.success();
      } else {
        Log.i(cordova.getActivity(), TAG, "before call shouldShowRequestPermissionRationale = "+ ActivityCompat.shouldShowRequestPermissionRationale(cordova.getActivity(), SensorControlConstants.MOTION_ACTIVITY_PERMISSION));
        Log.i(cordova.getActivity(), TAG, "Motion activity permission missing, requesting it");
        this.cordovaCallback = cordovaCallback;
        this.permissionChecker = getPermissionChecker(
          SensorControlConstants.ENABLE_MOTION_ACTIVITY_PERMISSION,
          SensorControlConstants.MOTION_ACTIVITY_PERMISSION,
          cordova.getActivity().getString(R.string.activity_permission_off),
          cordova.getActivity().getString(R.string.activity_permission_off_app_open));
        this.permissionChecker.requestPermission();
        }
    }

  public void checkShowNotificationsEnabled(CallbackContext cordovaCallback) {
    boolean validPerms = SensorControlChecks.checkNotificationsEnabled(cordova.getActivity());
    if(validPerms) {
      cordovaCallback.success();
    } else {
      cordovaCallback.error(cordova.getActivity().getString(R.string.activity_permission_off));
    }
  }

  public void checkAndPromptShowNotificationsEnabled(CallbackContext cordovaCallback) {
    boolean validPerms = SensorControlChecks.checkNotificationsEnabled(cordova.getActivity());
    if(validPerms) {
      SensorControlBackgroundChecker.restartFSMIfStartState(cordova.getActivity());
      cordovaCallback.success();
    } else {
      Log.i(cordova.getActivity(), TAG, "Notifications not enabled, opening app page");
      // TODO: switch to Settings.ACTION_APP_NOTIFICATION_SETTINGS instead of the app page
      // once our min SDK goes up to oreo
      openAppSettingsPage(cordovaCallback, SensorControlConstants.ENABLE_NOTIFICATIONS);
    }
  }

  public void checkPausedNotifications(CallbackContext cordovaCallback) {
      boolean unpaused = SensorControlChecks.checkNotificationsUnpaused(cordova.getActivity());
      if(unpaused) {
        cordovaCallback.success();
      } else {
        Log.i(cordova.getActivity(), TAG, "Notifications paused, asking user to report");
        cordovaCallback.error(cordova.getActivity().getString(R.string.notifications_paused));
      }
  }

  public void checkUnusedAppsUnrestricted(CallbackContext cordovaCallback) {
    boolean unrestricted = SensorControlChecks.checkUnusedAppsUnrestricted(cordova.getActivity());
    if (unrestricted) {
      cordovaCallback.success();
    } else {
      Log.i(cordova.getActivity(), TAG, "Unused apps restricted, asking user to unrestrict");
      cordovaCallback.error(cordova.getActivity().getString(R.string.unused_apps_restricted));
    }
  }


  public void checkAndPromptUnusedAppsUnrestricted(CallbackContext cordovaCallback) {
    boolean unrestricted = SensorControlChecks.checkUnusedAppsUnrestricted(cordova.getActivity());
    if (unrestricted) {
      SensorControlBackgroundChecker.restartFSMIfStartState(cordova.getActivity());
      cordovaCallback.success();
    } else {
      Log.i(cordova.getActivity(), TAG, "Unused apps restricted, asking user to unrestrict");
      this.cordovaCallback = cordovaCallback;
      cordova.setActivityResultCallback(plugin);
      Intent intent = IntentCompat.createManageUnusedAppRestrictionsIntent(cordova.getActivity(), cordova.getActivity().getPackageName());
      cordova.getActivity().startActivityForResult(intent, SensorControlConstants.REMOVE_UNUSED_APP_RESTRICTIONS);
    }
  }

  public void checkIgnoreBatteryOptimizations(CallbackContext cordovaCallback) {
    boolean unoptimized = SensorControlChecks.checkIgnoreBatteryOptimizations(cordova.getActivity());
    if (unoptimized) {
      cordovaCallback.success();
    } else {
      Log.i(cordova.getActivity(), TAG, "Battery optimizations enforced, asking user to ignore");
      cordovaCallback.error(cordova.getActivity().getString(R.string.unused_apps_restricted));
    }
  }

    /**
     * @param cordovaCallback
     * 
     * Driver function that gets called to ignore battery optimizations. Uses PermissionPopupChecker to generate the
     * intent and handle the user's input. 
     */
    public void checkAndPromptIgnoreBatteryOptimizations(CallbackContext cordovaCallback) {
      boolean unrestricted = SensorControlChecks.checkIgnoreBatteryOptimizations(cordova.getActivity());
      if (unrestricted) {
        SensorControlBackgroundChecker.restartFSMIfStartState(cordova.getActivity());
        cordovaCallback.success();
        return;
      } else {
        this.cordovaCallback = cordovaCallback;
        this.permissionChecker = getPermissionChecker(
            SensorControlConstants.IGNORE_BATTERY_OPTIMIZATIONS,
            SensorControlConstants.REQUEST_BATTERY_PERMISSION,
            "Insufficient battery permissions, please fix!",
            "Insufficient battery permissions, please fix!"
        );
        this.permissionChecker.requestPermission();
        return;
      }
    }

    /**
     * @param cordovaCallback
     * @param requestCode
     * 
     * Creates and specifies an intent to open ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATION. Used as a helper function inside
     * of PermissionPopupChecker.requestPermissions() 
     */
    public void openRequestBatteryOptimization(CallbackContext cordovaCallback, int requestCode) {
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        String packageName = cordova.getActivity().getPackageName();
        intent.setData(Uri.parse("package:" + packageName));
        this.cordovaCallback = cordovaCallback;
        cordova.setActivityResultCallback(plugin);
        // cordova.getActivity().startActivityForResult(intent, SensorControlConstants.OPEN_BATTERY_OPTIMIZATION_PAGE);
        cordova.getActivity().startActivityForResult(intent, requestCode);
    } 

    private void displayResolution(PendingIntent resolution) {
        if (resolution != null) {
            try {
                cordova.setActivityResultCallback(plugin);
                cordova.getActivity().startIntentSenderForResult(resolution.getIntentSender(), SensorControlConstants.ENABLE_LOCATION_SETTINGS, null, 0, 0, 0, null);
            } catch (IntentSender.SendIntentException e) {
                Context mAct = cordova.getActivity();
                NotificationHelper.createNotification(mAct, Constants.TRACKING_ERROR_ID, null, mAct.getString(R.string.unable_resolve_issue));
            }
        }
    }

    public void onNewIntent(Intent intent) {
      Log.i(cordova.getActivity(), TAG, "onNewIntent("+intent+") received, ignoring");
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
      if (this.permissionChecker == null) {
        NotificationHelper.createNotification(cordova.getActivity(), Constants.TRACKING_ERROR_ID, null, "Please upload log and report issue in requestPermissionResult");
        return;
      }
        switch(requestCode)
        {
          case SensorControlConstants.ENABLE_BOTH_PERMISSION:
            Log.i(cordova.getActivity(), TAG, "in callback shouldShowRequestPermissionRationale = "+ ActivityCompat.shouldShowRequestPermissionRationale(cordova.getActivity(), SensorControlConstants.LOCATION_PERMISSION));
            if ((grantResults[0] == PackageManager.PERMISSION_GRANTED) &&
              (grantResults[1] == PackageManager.PERMISSION_GRANTED)) {
              SensorControlBackgroundChecker.restartFSMIfStartState(cordova.getActivity());
              cordovaCallback.success();
            } else if (grantResults[0] == PackageManager.PERMISSION_DENIED || grantResults[1] == PackageManager.PERMISSION_DENIED) {
              this.permissionChecker.generateErrorCallback();
            }
            this.permissionChecker = null;
            break;
            case SensorControlConstants.ENABLE_LOCATION_PERMISSION:
            case SensorControlConstants.ENABLE_BACKGROUND_LOC_PERMISSION:
            case SensorControlConstants.ENABLE_MOTION_ACTIVITY_PERMISSION:
              // Code for all these is the same. We ask for a single permission
              // and if it is denied, we generate the error callback
              // the exact message is stored in the permission checker object
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    SensorControlBackgroundChecker.restartFSMIfStartState(cordova.getActivity());
                    cordovaCallback.success();
                } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                  this.permissionChecker.generateErrorCallback();
                }
                this.permissionChecker = null;
                break;
            case SensorControlConstants.LOCATION_INTERMEDIARY:
                // This case allows us to do the 3 step user location permissions setup. ACCESS_FINE_LOCATION gets called first,  
                // and this is the callback case for it. If it is allowed, prompt user with a dialog, then send them to the location
                // permissions settings if they accept. 
                if (grantResults.length == 0) {
                  // Covers weird error where SensorControlConstants.ENABLE_BACKGROUND_LOC_PERMISSION will get called again after an
                  // initial intended call. The second erroneous call creates an error because grantResults = [], causing an array 
                  // out of bounds error, so just return.
                  Log.i(cordova.getActivity(), TAG, "/d/as/das/dsadas/d/as/das/d/asd/as/d/asd/as/d/as Weird error happened! /d/as/das/dsadas/d/as/das/d/asd/as/d/asd/as/d/as");
                  return;
                } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                  // Set up for potential background location permissions request
                  Log.i(cordova.getActivity(), TAG, "");
                  this.cordovaCallback = cordovaCallback;
                  this.permissionChecker = getPermissionChecker(
                    SensorControlConstants.ENABLE_BACKGROUND_LOC_PERMISSION,
                    SensorControlConstants.BACKGROUND_LOC_PERMISSION,
                    cordova.getActivity().getString(R.string.location_permission_off),
                    cordova.getActivity().getString(R.string.location_permission_off_app_open));
                  // Send out alert dialog
                  intermediaryLocationDialog(this.permissionChecker);
                } else if (grantResults[0] == PackageManager.PERMISSION_DENIED || grantResults[1] == PackageManager.PERMISSION_DENIED) {
                  this.permissionChecker.generateErrorCallback();
                }
                break;
            default:
                Log.e(cordova.getActivity(), TAG, "Unknown permission code "+requestCode+" ignoring");
        }
    }

  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    Activity mAct = cordova.getActivity();
    cordova.setActivityResultCallback(null);
    switch (requestCode) {
      case SensorControlConstants.ENABLE_LOCATION_SETTINGS:
        Log.d(mAct, TAG, requestCode + " is our code, handling callback");
        final LocationSettingsStates states = LocationSettingsStates.fromIntent(data);
        Log.d(cordova.getActivity(), TAG, "at this point, isLocationUsable = " + (states != null && states.isLocationUsable()));
        switch (resultCode) {
          case Activity.RESULT_OK:
            // All required changes were successfully made
            Log.i(cordova.getActivity(), TAG, "All changes successfully made, reinitializing");
            try {
              SensorControlBackgroundChecker.restartFSMIfStartState(cordova.getActivity());
              cordovaCallback.success(statesToJSON(states));
            } catch (JSONException e) {
              cordovaCallback.error(mAct.getString(R.string.unknown_error_location_settings));
            }
            break;
          case Activity.RESULT_CANCELED:
            Log.i(cordova.getActivity(), TAG, "request " + requestCode + " cancelled, failing");
            cordova.setActivityResultCallback(null);
            cordovaCallback.error(cordova.getActivity().getString(R.string.user_rejected_setting));
            break;
          default:
            cordovaCallback.error(mAct.getString(R.string.unable_resolve_issue));
            Log.e(cordova.getActivity(), TAG, "Unknown result code while enabling location " + resultCode);
            break;
        }
        break;
      case SensorControlConstants.ENABLE_LOCATION_SETTINGS_MANUAL:
        Log.d(mAct, TAG, requestCode + " is our code, handling callback");
        // this will call the callback with success or error
        checkLocationSettings(cordovaCallback);
        break;
      case SensorControlConstants.ENABLE_BOTH_PERMISSION:
      case SensorControlConstants.ENABLE_LOCATION_PERMISSION:
      case SensorControlConstants.ENABLE_BACKGROUND_LOC_PERMISSION:
        Log.d(mAct, TAG, requestCode + " is our code, handling callback");
        Log.d(mAct, TAG, "Got permission callback from launching app settings when prompt failed");
        if (SensorControlChecks.checkLocationPermissions(cordova.getActivity())) {
          SensorControlBackgroundChecker.restartFSMIfStartState(cordova.getActivity());
          cordovaCallback.success();
        } else {
          // this is the activity result callback, so only launched when the app settings are used
          // so we don't need to use the permission checker, we know that the only option
          // is to launch the settings
          if (cordovaCallback != null) {
            String errorMessage = generateLocationPermissionError();
            cordovaCallback.error(errorMessage);
          }
        }
        break;
      case SensorControlConstants.ENABLE_MOTION_ACTIVITY_PERMISSION:
        Log.d(mAct, TAG, requestCode + " is our code, handling callback");
        Log.d(mAct, TAG, "Got permission callback from launching app settings");
        if (SensorControlChecks.checkMotionActivityPermissions(cordova.getActivity())) {
          SensorControlBackgroundChecker.restartFSMIfStartState(cordova.getActivity());
          cordovaCallback.success();
        } else {
          permissionChecker.generateErrorCallback();
        }
        permissionChecker = null;
        break;
      case SensorControlConstants.ENABLE_NOTIFICATIONS:
        Log.d(mAct, TAG, requestCode + " is our code, handling callback");
        Log.d(mAct, TAG, "Got notification callback from launching app settings");
        if (SensorControlChecks.checkNotificationsEnabled(cordova.getActivity())) {
          SensorControlBackgroundChecker.restartFSMIfStartState(cordova.getActivity());
          cordovaCallback.success();
        } else {
          cordovaCallback.error(cordova.getActivity().getString(R.string.notifications_blocked));
        }
        break;
      case SensorControlConstants.REMOVE_UNUSED_APP_RESTRICTIONS:
        Log.d(mAct, TAG, requestCode + " is our code, handling callback");
        Log.d(mAct, TAG, "Got unused app restrictions callback from launching app settings");
        AsyncTask.execute(new Runnable() {
          @Override
          public void run() {
        if (SensorControlChecks.checkUnusedAppsUnrestricted(cordova.getActivity())) {
              // SensorControlBackgroundChecker.restartFSMIfStartState(cordova.getActivity());
          cordovaCallback.success();
        } else {
          cordovaCallback.error(cordova.getActivity().getString(R.string.unused_apps_restricted));
        }
          }
        });
        break;
      case SensorControlConstants.IGNORE_BATTERY_OPTIMIZATIONS:
        Log.d(mAct, TAG, requestCode + " is our code, handling callback");
        Log.d(mAct, TAG, "Got ignore battery optimization callback from launching optimization page");
        AsyncTask.execute(new Runnable() {
          @Override
          public void run() {
        if (SensorControlChecks.checkIgnoreBatteryOptimizations(cordova.getActivity())) {
          SensorControlBackgroundChecker.restartFSMIfStartState(cordova.getActivity());
          cordovaCallback.success();
        } else {
          cordovaCallback.error(cordova.getActivity().getString(R.string.unused_apps_restricted));
        }
          }
        });
      default:
        Log.d(cordova.getActivity(), TAG, "Got unsupported request code " + requestCode + " , ignoring...");
    }
  }
}
