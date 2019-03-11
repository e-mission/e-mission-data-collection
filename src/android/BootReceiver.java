package edu.berkeley.eecs.emission.cordova.tracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import edu.berkeley.eecs.emission.R;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.usercache.UserCacheFactory;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.Transition;
import edu.berkeley.eecs.emission.cordova.tracker.location.TripDiaryStateMachineService;

/*
 * Class that allows us to re-register the alarms when the phone is rebooted.
 */
public class BootReceiver extends BroadcastReceiver {
    private static String TAG = "BootReceiver";
    private static final int BOOT_RECEIVER_ID = 1111;

    @Override
	public void onReceive(Context ctx, Intent intent) {
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
        	System.out.println("BootReceiver.onReceive called");
            Log.i(ctx, TAG, "BootReceiver.onReceive called");
            // TODO: Use a different wrapper? Or a different key?
            UserCacheFactory.getUserCache(ctx).putMessage(R.string.key_usercache_transition,
                    new Transition("unknown", "booted", ((double)System.currentTimeMillis())/1000));

            /*
             * End any ongoing trips, because the elapsed time will get reset at this
             * point and invalidate all entries in the database.
             * TODO: Remove this if we decide to switch back to utc time.
             */
            // DataUtils.endTrip(ctx);

            // Re-initialize the state machine if we haven't stopped tracking
            if (!TripDiaryStateMachineService.getState(ctx).equals(
                    ctx.getString(R.string.state_tracking_stopped))) {
                ctx.sendBroadcast(new ExplicitIntent(ctx, R.string.transition_initialize));
            }
        }
	}
}
