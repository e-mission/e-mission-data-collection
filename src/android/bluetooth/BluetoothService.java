package edu.berkeley.eecs.emission.cordova.tracker.bluetooth;

// Android imports
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.Set;
import java.util.HashSet;

// Altbeacon imports
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Beacon;

// Other plugin imports
import edu.berkeley.eecs.emission.R;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.tracker.ExplicitIntent;


public class BluetoothService extends Service {
    private static String TAG = "BluetoothService";
    private BeaconManager beaconManager;
    private Set<Beacon> scanned = new HashSet<>();
    private String uuid = "426c7565-4368-6172-6d42-6561636f6e73";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(this, TAG, "onStartCommand called!!!!");

        // Define the beacon manager
        beaconManager = BeaconManager.getInstanceForApplication(this);

        // Add in check to see if we can scan at all, otherwise we will be stuck here
        
        // Start scanning for BLE beacons
        startBeaconScan();

        // Start sticky
        return 1;
    }

    // @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void isInRange() {
        Log.d(this, TAG, "Is in range has been called!");

        stopBeaconScan();

        Log.d(this, TAG, "Done waiting, results are... " + scanned.size());

        if (scanned.size() > 0) {
            Log.d(this, TAG, "Found something!");
            Log.d(this, TAG, scanned.toString());
            this.sendBroadcast(new ExplicitIntent(this, R.string.transition_beacon_found));
        } else {
            Log.d(this, TAG, "Did not find anything!");
            this.sendBroadcast(new ExplicitIntent(this, R.string.transition_beacon_not_found));
        }

        onDestroy();
    }

    private void startBeaconScan() {
        // Code to start scanning for BLE beacons using AltBeacon library
        Log.d(this, TAG, "startBeaconScan called!!!!");

        // Keep track of how many times we have scanned

        beaconManager.addRangeNotifier(new RangeNotifier() {
            int numScans = 0;

            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                if (beacons.size() > 0) {
                    Log.d(BluetoothService.this, TAG, "The first beacon I see is about "+beacons.iterator().next().getDistance()+" meters away.");

                    for (Beacon beacon : beacons) {
                        Log.d(BluetoothService.this, TAG, beacon.toString());
                        // Even though we are scanning for beacons in a certain region, beacons with different UUID's still come up.
                        // Until we figure out why that is the case, put this check in place so we only save the ones with the right UUID.
                        if (beacon.getId1().toString().equals(uuid)) {
                            scanned.add(beacon);
                        }
                    }
                }

                numScans++;

                if (numScans >= 4) {
                    // Once we have hit certain number of scans, stop and determine if any beacons are in range
                    isInRange();
                }
            }
        });
    
        beaconManager.startRangingBeacons(new Region(uuid, null, null, null));
    }

    @Override
    public void onDestroy() {
        // stopBeaconScan();
        super.onDestroy();
    }

    private void stopBeaconScan() {
        Log.d(this, TAG, "Stopping monitoring for the beacon.");
        beaconManager.stopRangingBeacons(new Region(uuid, null, null, null));
        beaconManager.removeAllRangeNotifiers();
    }
}
