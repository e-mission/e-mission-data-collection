package edu.berkeley.eecs.emission.cordova.tracker.verification;

import edu.berkeley.eecs.emission.R;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.location.Location;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;


import java.util.List;

import edu.berkeley.eecs.emission.cordova.tracker.Constants;
import edu.berkeley.eecs.emission.cordova.tracker.ExplicitIntent;
import edu.berkeley.eecs.emission.cordova.tracker.location.actions.LocationTrackingActions;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.NotificationHelper;

public class SensorControlChecks {
    public static final String TAG = "SensorControlChecks";

    public static void checkLocationSettings(final Context ctxt,
                                             OnCompleteListener<LocationSettingsResponse> callback) {
        Log.i(ctxt, TAG, "About to check location settings");
        LocationRequest request = new LocationTrackingActions(ctxt).getLocationRequest();
        Log.d(ctxt, TAG, "Checking location settings for request "+request);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
          .addLocationRequest(request);

        Task<LocationSettingsResponse> task =
          LocationServices.getSettingsClient(ctxt).checkLocationSettings(builder.build());
        Log.d(ctxt, TAG, "Got back result "+task);
        task.addOnCompleteListener(callback);
    }

    // TODO: Figure out how to integrate this with the background code
    // https://github.com/e-mission/e-mission-docs/issues/680#issuecomment-953403832
    public static boolean checkLocationPermissions(final Context ctxt) {
      boolean foregroundPerm = ContextCompat.checkSelfPermission(ctxt, SensorControlConstants.LOCATION_PERMISSION) == PermissionChecker.PERMISSION_GRANTED;
      // the background permission is only valid for Q+
      boolean backgroundPerm = true;
      if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        backgroundPerm = ContextCompat.checkSelfPermission(ctxt, SensorControlConstants.BACKGROUND_LOC_PERMISSION) == PermissionChecker.PERMISSION_GRANTED;
      }
      return foregroundPerm && backgroundPerm;
    }

    // TODO: Figure out how to integrate this with the background code
    // https://github.com/e-mission/e-mission-docs/issues/680#issuecomment-953403832
    public static boolean checkMotionActivityPermissions(final Context ctxt) {
      // apps before version 29 did not need to prompt for dynamic permissions related
      // to motion activity
      boolean version29Check = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q;
      boolean permCheck = ContextCompat.checkSelfPermission(ctxt, SensorControlConstants.MOTION_ACTIVITY_PERMISSION) == PermissionChecker.PERMISSION_GRANTED;
      return version29Check || permCheck;
    }

    public static boolean checkNotificationsEnabled(final Context ctxt) {
      NotificationManagerCompat nMgr = NotificationManagerCompat.from(ctxt);
      boolean appDisabled = nMgr.areNotificationsEnabled();
      boolean channelsDisabled = false;
      // notification channels did not exist before oreo, so they
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        List<NotificationChannel> channels = nMgr.getNotificationChannels();
        for (NotificationChannel c : channels) {
          boolean currChannelDisabled = false;
          currChannelDisabled = (c.getImportance() == NotificationManager.IMPORTANCE_NONE);
          channelsDisabled = channelsDisabled || currChannelDisabled;
        }
      }
      return appDisabled || channelsDisabled;
    }

  public static boolean checkNotificationsUnpaused(final Context ctxt) {
    NotificationManager nMgr = (NotificationManager) ctxt.getSystemService(Context.NOTIFICATION_SERVICE);
    boolean appUnpaused = true;
    // app notification pausing apparently did not exist before API 29, so we return unpaused = true
    // by default
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
      appUnpaused = !(nMgr.areNotificationsPaused());
    }
    return appUnpaused;
  }
}
