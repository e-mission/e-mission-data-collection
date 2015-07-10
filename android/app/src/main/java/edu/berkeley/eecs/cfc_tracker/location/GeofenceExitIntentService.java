package edu.berkeley.eecs.cfc_tracker.location;

import edu.berkeley.eecs.cfc_tracker.R;
import edu.berkeley.eecs.cfc_tracker.storage.DataUtils;

import android.app.IntentService;
import android.content.Intent;

import edu.berkeley.eecs.cfc_tracker.Log;
import edu.berkeley.eecs.cfc_tracker.usercache.UserCacheFactory;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

public class GeofenceExitIntentService extends IntentService {
	private static final String TAG = "GeofenceExitIntentService";
	
	public GeofenceExitIntentService() {
		super("GeofenceExitIntentService");
	}
	
	@Override
	public void onCreate() {
		Log.d(this, TAG, "onCreate called");
		super.onCreate();
	}
	
	@Override
	public void onStart(Intent i, int startId) {
		Log.d(this, TAG, "onStart called with startId "+startId);
		super.onStart(i, startId);
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(this, TAG, "onStartCommand called with intent "+intent+" flags "+flags+" startId "+startId);
		return super.onStartCommand(intent, flags, startId);		
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		/*
		 * The intent is called when we leave a geofence. 
		 */
        Log.d(this, TAG, "geofence exit intent action = "+intent.getAction());
        GeofencingEvent parsedEvent = GeofencingEvent.fromIntent(intent);
        Log.d(this, TAG, "got geofence intent callback with type "+parsedEvent.getGeofenceTransition()+
            " and location "+parsedEvent.getTriggeringLocation());

        // This is the only transition we are listening to
        assert(parsedEvent.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_EXIT);
        if (parsedEvent.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_EXIT) {
    		Log.d(this, TAG, "Geofence exited! Intent = "+ intent+" Starting ongoing monitoring...");
            // Add the exit location to the tracking database
			UserCacheFactory.getUserCache(this).putMessage(R.string.key_usercache_location,
                    parsedEvent.getTriggeringLocation());
            // DataUtils.addPoint(this, parsedEvent.getTriggeringLocation());
            // Let's just re-use the same event for the broadcast, since it has the location information
            // in case we need it on the other side.
            // intent.setAction(getString(R.string.transition_exited_geofence));
            // sendBroadcast(intent);
            sendBroadcast(new Intent(getString(R.string.transition_exited_geofence)));
        }
	}
}
