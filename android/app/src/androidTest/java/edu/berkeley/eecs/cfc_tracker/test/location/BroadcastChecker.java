package edu.berkeley.eecs.cfc_tracker.test.location;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BroadcastChecker extends BroadcastReceiver {
    private static final String TAG = "BroadcastChecker";
    private Intent receivedIntent = null;
	private String broadcastAction = null;
	
	public BroadcastChecker(String broadcastAction) {
		this.broadcastAction = broadcastAction;
	}
	
	public boolean hasReceivedBroadcast() {
		return receivedIntent != null;
	}

    public Intent getReceivedIntent() { return receivedIntent; }

    public boolean isMatchingIntent(Intent intent) {
        if (intent.getAction().equals(this.broadcastAction)) {
            return true;
        }
        Log.i(TAG, "intent.getAction = " + intent.getAction() + " expected action = "
                + broadcastAction + " returning false");
        return false;
    }
	
	@Override
	public void onReceive(Context context, Intent intent) {
		System.out.println("testExitGeofence received intent "+intent);
		// We shouldn't need this check since we only register for this
		// one particular action. But it doesn't hurt to check
		if (isMatchingIntent(intent)) {
			synchronized(this) {
				receivedIntent = intent;
				this.notify();
			}
		} else {
			System.out.println("Recieved message that I did not register for!");
		}
	}
}
