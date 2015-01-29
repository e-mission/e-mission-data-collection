package edu.berkeley.eecs.cfc_tracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.io.IOException;

/*
 * Class that allows us to re-register the alarms when the phone is rebooted.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final int BOOT_RECEIVER_ID = 1111;

    @Override
	public void onReceive(Context ctx, Intent intent) {
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
        	System.out.println("BootReceiver.onReceive called");
            Intent i = new Intent(ctx, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            System.out.println("Starting activity in boot receiver");
            ctx.startActivity(i);
            // Re-initialize the state machine
            ctx.sendBroadcast(new Intent(ctx.getString(R.string.transition_initialize)));

            // Re-initialize the logging
            try {
                Log.initWithFile(ctx);
            } catch(IOException e) {
                // If we weren't able to init correctly, generate a notification
                // so that the user can fix it
                NotificationHelper.createNotification(
                        ctx, BOOT_RECEIVER_ID, e.getMessage());

                // Unset the previous successful value so that the user has some clue on what to do
                SharedPreferences.Editor editor =
                        PreferenceManager.getDefaultSharedPreferences(ctx).edit();
                editor.putBoolean(MainActivity.LOG_FILE_INIT_KEY, false);
                editor.apply();
            }
        }
	}
}
