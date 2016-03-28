package edu.berkeley.eecs.emission.cordova.tracker.sensors;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import edu.berkeley.eecs.emission.cordova.tracker.wrapper.Battery;

public class BatteryUtils {
	public static Battery getBatteryInfo(Context applicationContext) {
	    Intent batteryIntent = applicationContext.registerReceiver(null,
				new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		return new Battery(batteryIntent);
	}
}
