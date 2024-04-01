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
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;


public class BluetoothService extends Service {
    private static String TAG = "BluetoothService";
    private BeaconManager beaconManager;
    private Set<Beacon> scanned = new HashSet<>();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(this, TAG, "onStartCommand called!!!!");

        // Define the beacon manager
        beaconManager = BeaconManager.getInstanceForApplication(this);
        
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
        } else {
            Log.d(this, TAG, "Did not find anything!");
        }
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
                        scanned.add(beacon);
                    }
                }

                numScans++;

                if (numScans >= 4) {
                    // Once we have hit certain number of scans, stop and determine if any beacons are in range
                    isInRange();
                }
            }
        });
    
        beaconManager.startRangingBeacons(new Region("426C7565-4368-6172-6D42-6561636F6E73", null, null, null));
    }

    @Override
    public void onDestroy() {
        stopBeaconScan();
        super.onDestroy();
    }

    private void stopBeaconScan() {
        Log.d(this, TAG, "Stopping monitoring for the beacon.");
        beaconManager.stopRangingBeacons(new Region("426C7565-4368-6172-6D42-6561636F6E73", null, null, null));
        beaconManager.removeAllRangeNotifiers();
    }
}
