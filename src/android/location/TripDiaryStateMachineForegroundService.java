package edu.berkeley.eecs.emission.cordova.tracker.location;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;

import de.appplant.cordova.plugin.notification.ClickActivity;
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(this, TAG, "onStartCommand called on oreo+, starting foreground service");
            // Go to the foreground with a dummy notification
            Notification.Builder builder = NotificationHelper.getNotificationBuilderForApp(this,
                    "background trip tracking started");
            builder.setOngoing(true);

            Intent activityIntent = new Intent(this, MainActivity.class);
            activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            PendingIntent activityPendingIntent = PendingIntent.getActivity(this, 0,
                    activityIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            builder.setContentIntent(activityPendingIntent);


            int ONGOING_TRIP_ID = 6646464;
            startForeground(ONGOING_TRIP_ID, builder.build());
        } else {
            Log.d(this, TAG, "onStartCommand called on pre-oreo, ignoring");
        }

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    public void onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(this, TAG, "onDestroy called, removing notification");
            stopForeground(true);
        } else {
            Log.d(this, TAG, "onDestroy called on pre-oreo, ignoring");
        }
    }
}
