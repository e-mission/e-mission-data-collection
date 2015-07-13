package edu.berkeley.eecs.cfc_tracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/*
 * Class that allows us to re-register the alarms when the phone is rebooted.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final int BOOT_RECEIVER_ID = 1111;

    @Override
	public void onReceive(Context ctx, Intent intent) {
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
        	System.out.println("BootReceiver.onReceive called");

            /*
             * End any ongoing trips, because the elapsed time will get reset at this
             * point and invalidate all entries in the database.
             * TODO: Remove this if we decide to switch back to utc time.
             */
            // DataUtils.endTrip(ctx);

            // Re-initialize the state machine
            ctx.sendBroadcast(new Intent(ctx.getString(R.string.transition_initialize)));
        }
	}
}
