package edu.berkeley.eecs.cfc_tracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class WakeupReceiver extends BroadcastReceiver {
    private static String TAG = "WakeupReceiver";

	public static int STARTED_MONITORING = 42;
	public static int STOPPED_MONITORING = 43;

	@Override
	public void onReceive(Context ctxt, Intent intent) {
		Log.d(ctxt, TAG, "Received broadcast message " + intent);
		if ((intent.getAction().equals(ctxt.getString(R.string.startAtDropoff)) ||
				intent.getAction().equals(ctxt.getString(R.string.startAtPickup)))) {
			NotificationHelper.createNotification(ctxt, STARTED_MONITORING, ctxt.getString(R.string.startedMonitoring));
//			startMonitoring(ctxt);
	        // Create a new intent here for the CommuteTrackerService so that it will be delivered correctly
			/*
	        Intent serviceStartIntent = new Intent(ctxt, CommuteTrackerService.class);
	        serviceStartIntent.setAction(intent.getAction());
	        ctxt.startService(serviceStartIntent);
	        */
		} else {
			NotificationHelper.createNotification(ctxt, STOPPED_MONITORING, ctxt.getString(R.string.stoppedMonitoring));
			assert(intent.getAction() == ctxt.getString(R.string.stopAtDropoff) ||
					intent.getAction() == ctxt.getString(R.string.stopAtPickup));
			System.out.println("About to stop service");
			stopMonitoring(ctxt);
			
            //Stop CommuteTrackerService intent
			/*
	        Intent serviceStopIntent = new Intent(ctxt, CommuteTrackerService.class);
	        serviceStopIntent.setAction(intent.getAction());
	        ctxt.stopService(serviceStopIntent);
	        */
			
			/*
			 * TODO: This is really a sucky place to put this since we are trying to ensure that
			 * all the triggering code is in CommuteService. However, there does not appear to 
			 * be a way for the CommuteService to get access to the intent that is used to stop it.
			 * Overriding stopService is not useful since the service is not the context that stopService
			 * is invoked on. onDestroy() works, but it does not have the service passed in.
			 * 
			 * So we make the calls here. At this point, it is unclear whether we even need the CommuteService
			 * - it isn't doing much. We could consider moving the business logic in here instead.
			 */	    
			/*
	        Location loc = (Location)intent.getExtras().get(LocationClient.KEY_LOCATION_CHANGED);
			if (loc != null) {
				new GeofenceHandler(ctxt).registerGeofence(
						loc.getLatitude(), loc.getLongitude());
			} else {
				System.out.println("location not specified while stopping, geofence not set");
				NotificationHelper.createNotification(ctxt, STOPPED_MONITORING,
						"location not specified while stopping, geofence not set");
			}
			*/
		}
	}
	
	/*
	public void startMonitoring(Context ctxt) {
        locationHandler = new LocationHandler(ctxt);
        locationHandler.startTracking(null);
        
        activityHandler = new ActivityRecognitionHandler(ctxt);
        activityHandler.startMonitoring();   
	}
	*/
	
	public void stopMonitoring(Context ctxt) {
		/* TODO: This is completely bizarre. Even if we just create a new location handler,
		 * even if we don't use it for anything, the geofence is not created correctly.
		 * I suspect that this is because of some stupid issue with caching the context.
		 * I've spent a day working on this already, so I'm now moving to the new API which
		 * should hopefully work better. Or at least, have a better chance of getting support
		 * when requested.
		 * 
		 * Bizarro world #2: With the new google play API (version 21), even the first geofence
		 * doesn't work if the handler is created here. That is totally bizarre because
		 * stopMonitoring isn't even called at that point. For that matter, startMonitoring
		 * is not called at that point. But yet, it is 100% reproducible. Makes me wonder
		 * whether this is tickling some emulator bug, I should try on a physical device instead. 
		 */
		// locationHandler = new LocationHandler(ctxt);
		// locationHandler.stopTracking(null);
		/*
		activityHandler = new ActivityRecognitionHandler(ctxt);
		activityHandler.stopMonitoring();
		// Generate the final trip
		DataUtils.endTrip(ctxt);
		*/
	}
}
