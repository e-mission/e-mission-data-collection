package edu.berkeley.eecs.emission.cordova.tracker.location;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;

import edu.berkeley.eecs.emission.MainActivity;


import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.NotificationHelper;

/**
 * Created by shankari on 1/30/18
 * as a hopefully short-to-medium term workaround for
 * https://github.com/e-mission/e-mission-data-collection/issues/164
 */

public class TripDiaryStateMachineForegroundService extends Service {
    private static String TAG = "TripDiaryStateMachineForegroundService";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(this, TAG, "onStartCommand called with flags = " + flags +
                " and startId = " + startId);
        handleStart(this, this.getString(R.string.trip_tracking_started), intent, flags, startId);

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    public static void handleStart(Service srv, String msg, Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(srv, TAG, "onStartCommand called on oreo+, starting foreground service");
            // Go to the foreground with a dummy notification
            NotificationManager nMgr = (NotificationManager)srv.getSystemService(Context.NOTIFICATION_SERVICE);
            Notification.Builder builder = NotificationHelper.getNotificationBuilderForApp(srv,
                    nMgr, msg);
            builder.setOngoing(true);

            Intent activityIntent = new Intent(srv, MainActivity.class);
            activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            PendingIntent activityPendingIntent = PendingIntent.getActivity(srv, 0,
                    activityIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            builder.setContentIntent(activityPendingIntent);


            int ONGOING_TRIP_ID = 6646464;
            srv.startForeground(ONGOING_TRIP_ID, builder.build());
        } else {
            Log.d(srv, TAG, "onStartCommand called on pre-oreo, ignoring");
        }
    }

    @Override
    public void onDestroy() {
        handleDestroy(this);
    }

    public static void handleDestroy(Service srv) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(srv, TAG, "onDestroy called, removing notification");
            srv.stopForeground(true);
        } else {
            Log.d(srv, TAG, "onDestroy called on pre-oreo, ignoring");
        }
    }

    /*
     Returns the correct pending intent to be passed to invoke a background service
     when the app is in the background (which is almost 100% of the time in our case.
     We need to use getService for pre-Oreo and getForegroundService for O+.
     There are 4 usages of this pattern, so it is probably worth pulling out into a static function.
     When we ever move the minAPI up to 26, we can move this back inline.
     */

    public static PendingIntent getProperPendingIntent(Context ctxt, Intent innerIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return PendingIntent.getForegroundService(ctxt, 0, innerIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        } else {
            return PendingIntent.getService(ctxt, 0, innerIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
    }
}
