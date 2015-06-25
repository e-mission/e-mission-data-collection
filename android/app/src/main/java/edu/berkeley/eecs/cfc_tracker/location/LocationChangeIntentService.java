package edu.berkeley.eecs.cfc_tracker.location;

import java.util.Arrays;

// import com.google.android.gms.location.LocationClient;

import edu.berkeley.eecs.cfc_tracker.Constants;
import edu.berkeley.eecs.cfc_tracker.R;
import edu.berkeley.eecs.cfc_tracker.storage.DataUtils;
import android.app.IntentService;
import android.content.Intent;
import android.location.Location;

import edu.berkeley.eecs.cfc_tracker.Log;

import com.google.android.gms.location.FusedLocationProviderApi;

public class LocationChangeIntentService extends IntentService {
	private static final String TAG = "LocationChangeIntentService";
	private static final int TRIP_END_RADIUS = Constants.TRIP_EDGE_THRESHOLD;
	
	public LocationChangeIntentService() {
		super("LocationChangeIntentService");
	}
	
	@Override
	public void onCreate() {
		Log.d(this, TAG, "onCreate called");
		super.onCreate();
	}
	
	@Override
	public void onStart(Intent i, int startId) {
		Log.d(this, TAG, "onStart called with "+i+" startId "+startId);
		super.onStart(i, startId);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		/*
		 * The intent is called when we get a location update.
		 */
		Log.d(this, TAG, "FINALLY! Got location update, intent is "+intent);
		Log.d(this, TAG, "Extras keys are "+Arrays.toString(intent.getExtras().keySet().toArray()));
        Log.d(this, TAG, "Intent Action is "+intent);

		Location loc = (Location)intent.getExtras().get(FusedLocationProviderApi.KEY_LOCATION_CHANGED);

		/*
		It seems that newer version of Google Play will send along an intent that does not have the
		KEY_LOCATION_CHANGED extra, but rather an EXTRA_LOCATION_AVAILABILITY. The original code
		assumed KEY_LOCATION_CHANGED would always be there, and didn't check for this other type
		of extra. I think we can safely ignore these intents and just return when loc is null.

		see http://stackoverflow.com/questions/29960981/why-does-android-fusedlocationproviderapi-requestlocationupdates-send-updates-wi
		 */
		if (loc == null) return;

		DataUtils.addPoint(this, loc);
		if (isTripEnded()) {
			// Stop listening to more updates
			Intent stopMonitoringIntent = new Intent();
			stopMonitoringIntent.setAction(getString(R.string.transition_stopped_moving));
			stopMonitoringIntent.putExtra(FusedLocationProviderApi.KEY_LOCATION_CHANGED, loc);
			sendBroadcast(stopMonitoringIntent);
            Log.d(this, TAG, "Finished broadcasting state change to receiver, ending trip now");
            DataUtils.endTrip(this);
		}
	}
	
	public boolean isTripEnded() {
		/* We have requested 10 points, but we might get less than 10 if the trip has just started
		 * We request updates every 30 secs, but we might get updates more frequently if other apps have
		 * requested that. So maybe relying on the last n updates is not such a good idea.
		 *
		 * TODO: Switching to all updates in the past 5 minutes may be a better choice
		 */
		Location[] last10Points = DataUtils.getLastPoints(this, 10);
		Log.d(this, TAG, "last10Points = "+ Arrays.toString(last10Points));
		if (last10Points.length < 10) {
			Log.i(this, TAG, "Only "+last10Points.length+
					" points, not enough to decide, returning false");
			return false;
		}
		double[] last9Distances = getDistances(last10Points);
		Log.d(this, TAG, "last9Distances = "+ Arrays.toString(last9Distances));
		if (stoppedMoving(last9Distances)) {
			Log.i(this, TAG, "stoppedMoving = true");
			return true;
		}
		Log.i(this, TAG, "stoppedMoving = false");
		return false;
	}
	
	public double[] getDistances(Location[] points) {
		if (points.length < 2) {
			return new double[0];
		}
		double[] last9Distances = new double[points.length - 1];
		/*
		 * Since we get the last n points, they are sorted in
		 * reverse order by timestamp. So the most recent point is
		 * really the first point and we need to compare it against
		 * all the others. 
		 */
		Location lastPoint = points[0];
		for (int i = 0; i < points.length - 1; i++) {
			last9Distances[i] = lastPoint.distanceTo(points[i+1]);
		}
		return last9Distances;
	}
	
	/*
	 * The current check to detect that we have stopped moving is that all the last 4 points are within
	 * the TRIP_END_RADIUS from the last point.
	 */
	public boolean stoppedMoving(double[] last4Distances) {
		if (last4Distances.length < 1) {
			// We don't have enough points to decide whether we have finished or not, so let's wait a bit longer
			return false;
		}
		// We don't want to start with maxDistance == 0 because then we will always
		// be within the threshold. We already know that there is at least one element, so we
		// start with that element
		double maxDistance = last4Distances[0];

		for(int i = 1; i < last4Distances.length; i++) {
			double currDistance = last4Distances[i];
			if (currDistance > maxDistance) {
				maxDistance = currDistance;
			}
		}
		Log.d(this, TAG, "maxDistance = "+maxDistance+" TRIP_END_RADIUS = "+TRIP_END_RADIUS);
		// If all the distances are below the trip radius, then we have ended
		if (maxDistance < TRIP_END_RADIUS) {
			Log.d(this, TAG, "stoppedMoving = true");
			return true;
		} else {
			Log.d(this, TAG, "stoppedMoving = false");
			return false;
		}
	}
}
