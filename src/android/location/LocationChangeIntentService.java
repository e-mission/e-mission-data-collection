package edu.berkeley.eecs.emission.cordova.tracker.location;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

// import com.google.android.gms.location.LocationClient;

import edu.berkeley.eecs.emission.cordova.tracker.ConfigManager;
import edu.berkeley.eecs.emission.cordova.tracker.Constants;
import edu.berkeley.eecs.emission.R;


import edu.berkeley.eecs.emission.cordova.tracker.ExplicitIntent;
import edu.berkeley.eecs.emission.cordova.tracker.sensors.PollSensorManager;
import android.app.IntentService;
import android.content.Intent;
import android.location.Location;

import edu.berkeley.eecs.emission.cordova.tracker.wrapper.LocationTrackingConfig;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.NotificationHelper;
import edu.berkeley.eecs.emission.cordova.usercache.BuiltinUserCache;
import edu.berkeley.eecs.emission.cordova.usercache.UserCache;
import edu.berkeley.eecs.emission.cordova.usercache.UserCacheFactory;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.SimpleLocation;

import com.google.android.gms.location.FusedLocationProviderApi;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationResult;

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
	public int onStartCommand(Intent i, int flags, int startId) {
		Log.d(this, TAG, "onStart called with "+i+" startId "+startId);
		return super.onStartCommand(i, flags, startId);
	}

	@Override
	public void onDestroy() {
		Log.d(this, TAG, "onDestroy called");
		super.onDestroy();
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		/*
		 * The intent is called when we get a location update.
		 */
		Log.d(this, TAG, "FINALLY! Got location update, intent is "+intent);
		Log.d(this, TAG, "Extras keys are "+Arrays.toString(intent.getExtras().keySet().toArray()));
		int ACCURACY_THRESHOLD = ConfigManager.getConfig(this).getAccuracyThreshold();

        UserCache uc = UserCacheFactory.getUserCache(this);

        /*
         * For the sensors that are not managed by the android sensor manager, but instead, require
         * polling us to poll them, let us do so at the time that we get location updates. We will
         * always have location updates, since that is our goal, and piggybacking
         * in this fashion will avoid the overhead of building a scheduler, launching a new process
         * for the polling, and the power drain of waking up the CPU.
         */
        PollSensorManager.getAndSaveAllValues(this);

		List<Location> locList = LocationResult.hasResult(intent)? LocationResult.extractResult(intent).getLocations() : null;
		LocationAvailability locationAvailability = LocationAvailability.extractLocationAvailability(intent);
		Log.d(this, TAG, "Read locations "+locList+" from intent");
		if (locationAvailability != null) {
			Log.d(this, TAG, "availability = "+locationAvailability.isLocationAvailable());
			if (!locationAvailability.isLocationAvailable()) {
				Log.d(this, TAG, "location is not available, move to start state?");
			}
		}

		/*
		It seems that newer version of Google Play will send along an intent that does not have the
		KEY_LOCATION_CHANGED extra, but rather an EXTRA_LOCATION_AVAILABILITY. The original code
		assumed KEY_LOCATION_CHANGED would always be there, and didn't check for this other type
		of extra. I think we can safely ignore these intents and just return when loc is null.

		see http://stackoverflow.com/questions/29960981/why-does-android-fusedlocationproviderapi-requestlocationupdates-send-updates-wi
		 */

		if (locList == null) return;

		for (Location loc: locList) {
        SimpleLocation simpleLoc = new SimpleLocation(loc);
        uc.putSensorData(R.string.key_usercache_location, simpleLoc);
    }

		/*
		 * If we are going to read data continuously and never stop, then we don't need to read any
		 * points. We can deal with everything exclusively on the server side.
		 */

		if (!ConfigManager.getConfig(this).isDutyCycling()) {
			// Server-side currently expects filtered location, but if we just put everything
			// into filtered location, it won't be filtered any more.
			// Let's just assume that we will have to generate filtered_location on the server side
			// in that case.
			// So in that case, we won't do anything here.
			// We could filter, but that seems like extra work, so it is not a fair comparison with
			// just dumping the data to the server.
			return;
		}

        /*
		 * So far, our analysis for detecting the end of a trip starts off with ignoring points with
		 * low accuracy and that are exactly a duplicate of the prior point. However, it is not
		 * clear if this is always correct, and we want to collect the raw data as well until we are
		 * sure that the algorithm is correct. So we store both the raw location and the filtered
		 * location and use the filtered location for our calculations.
		 */
		int tripEndSecs = ConfigManager.getConfig(this).getTripEndStationaryMins() * 60;
        int pointsToQuery = tripEndSecs * 1000 /
				ConfigManager.getConfig(this).getFilterTime();
        Log.d(this, TAG, "Finding the last "+pointsToQuery+" points");

        SimpleLocation[] last10Points = uc.getLastSensorData(R.string.key_usercache_filtered_location, pointsToQuery , SimpleLocation.class);

        double nowSecs = ((double)System.currentTimeMillis())/1000;
        UserCache.TimeQuery tq = new UserCache.TimeQuery(getString(R.string.metadata_usercache_write_ts),
                nowSecs - tripEndSecs - 10, nowSecs);

        Log.d(this, TAG, "Finding points in the range "+tq);
        SimpleLocation[] points5MinsAgo = uc.getSensorDataForInterval(R.string.key_usercache_filtered_location,
                tq, SimpleLocation.class);

        List<Location> validLocList = new LinkedList<Location>();
        List<SimpleLocation> validSimpleLocList = new ArrayList<SimpleLocation>();

        for (Location loc: locList) {
          boolean validPoint = false;
          SimpleLocation simpleLoc = new SimpleLocation(loc);
        if (loc.getAccuracy() < ACCURACY_THRESHOLD) {
            if (last10Points.length == 0) {
                // Insert at least one entry before we can start comparing for duplicates
                validPoint = true;
            } else {
                assert(last10Points.length > 0);
                if (simpleLoc.distanceTo(last10Points[last10Points.length - 1]) != 0) {
                    validPoint = true;
                } else {
                    Log.i(this, TAG, "Duplicate point," + loc + " skipping ");
                }
            }
        } else {
            Log.d(this, TAG, "Found bad quality point "+loc+" skipping");
        }

        Log.d(this, TAG, "Current point status = "+validPoint);

        if (validPoint) {
            uc.putSensorData(R.string.key_usercache_filtered_location, simpleLoc);
            validLocList.add(loc);
            validSimpleLocList.add(simpleLoc);
          }
        }

		double lastTransitionTs = ((BuiltinUserCache)uc).getTsOfLastTransition();
		last10Points = filterAfterTransition(last10Points, lastTransitionTs);
		points5MinsAgo = filterAfterTransition(points5MinsAgo, lastTransitionTs);

        // We will check whether the trip ended only when the point is valid.
        // Otherwise, we might end up with the duplicates triggering trip ends.
		if (validLocList.size() > 0 && isTripEnded(last10Points, points5MinsAgo, tripEndSecs)) {
			// Stop listening to more updates
			Intent stopMonitoringIntent = new Intent();
			stopMonitoringIntent.setAction(getString(R.string.transition_stopped_moving));
			// stopMonitoringIntent.putExtra(FusedLocationProviderApi.KEY_LOCATION_CHANGED, validLocList.get(0));
			sendBroadcast(new ExplicitIntent(this, stopMonitoringIntent));
            Log.d(this, TAG, "Finished broadcasting state change to receiver, ending trip now");
            // DataUtils.endTrip(this);
		}
	}

	public SimpleLocation[] filterAfterTransition(SimpleLocation[] orig, double lastTransitionTs) {
		ArrayList<SimpleLocation> tempArray = new ArrayList<SimpleLocation>();
		for (SimpleLocation loc: orig) {
			if (loc.getTs() > lastTransitionTs) {
				tempArray.add(loc);
			}
		}
		Log.d(this, TAG, "After filtering at "+lastTransitionTs+" number of points goes from "+
				orig.length+" -> "+tempArray.size());
		SimpleLocation[] retArray = new SimpleLocation[tempArray.size()];
		return tempArray.toArray(retArray);
	}
	
	public boolean isTripEnded(SimpleLocation[] last10Points, SimpleLocation[] points5MinsAgo, int tripEndSecs) {
		/* We have requested 10 points, but we might get less than 10 if the trip has just started
		 * We request updates every 30 secs, but we might get updates more frequently if other apps have
		 * requested that. So maybe relying on the last n updates is not such a good idea.
		 *
		 * TODO: Switching to all updates in the past 5 minutes may be a better choice
		 */
		Log.d(this, TAG, "last10Points.length = "+ last10Points.length);
        Log.d(this, TAG, "points5MinsAgo.length = "+ points5MinsAgo.length);

		if (last10Points.length < 10 || points5MinsAgo.length == 0) {
			Log.i(this, TAG, "last10Points.length = "+last10Points.length+
                    " points5MinsAgo.length = " + points5MinsAgo.length +
					" points, not enough to decide, returning false");
			return false;
		}
		double[] last9Distances = getDistances(last10Points);
        double[] last5MinsDistances = getDistances(points5MinsAgo);

		Log.d(this, TAG, "last9Distances = "+ Arrays.toString(last9Distances));
        Log.d(this, TAG, "last5MinsDistances = "+ Arrays.toString(last5MinsDistances));

		double last5MinsSpan = points5MinsAgo[0].getTs()
									- points5MinsAgo[points5MinsAgo.length - 1].getTs();

		Log.d(this, TAG, "last5MinsSpan = "+last5MinsSpan+" secs, threshold + fuzz = "+(tripEndSecs - 30));
		// -30 fuzz factor because we only look for the range in now - tripEndSecs - 10 anyway
		if (stoppedMoving(last9Distances) && last5MinsSpan > (tripEndSecs - 60) &&
				stoppedMoving(last5MinsDistances)) {
			Log.i(this, TAG, "isTripEnded: stoppedMoving = true");
			return true;
		}
		Log.i(this, TAG, "isTripEnded: stoppedMoving = false");
		return false;
	}
	
	public double[] getDistances(SimpleLocation[] points) {
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
		SimpleLocation lastPoint = points[0];
		for (int i = 0; i < points.length - 1; i++) {
			last9Distances[i] = lastPoint.distanceTo(points[i+1]);
		}
		return last9Distances;
	}
	
	/*
	 * The current check to detect that we have stopped moving is that all the last 4 points are within
	 * the TRIP_END_RADIUS from the last point.
	 */
	public boolean stoppedMoving(double[] last9Distances) {
		if (last9Distances.length < 1) {
			// We don't have enough points to decide whether we have finished or not, so let's wait a bit longer
			return false;
		}
		// We don't want to start with maxDistance == 0 because then we will always
		// be within the threshold. We already know that there is at least one element, so we
		// start with that element
		double maxDistance = last9Distances[0];

		for(int i = 1; i < last9Distances.length; i++) {
			double currDistance = last9Distances[i];
			if (currDistance > maxDistance) {
				maxDistance = currDistance;
			}
		}
		Log.d(this, TAG, "maxDistance = "+maxDistance+" TRIP_END_RADIUS = "+TRIP_END_RADIUS);
		// If all the distances are below the trip radius, then we have ended
		if (maxDistance < TRIP_END_RADIUS) {
			Log.d(this, TAG, "stoppedMoving: stoppedMoving = true");
			return true;
		} else {
			Log.d(this, TAG, "stoppedMoving: stoppedMoving = false");
			return false;
		}
	}
}
