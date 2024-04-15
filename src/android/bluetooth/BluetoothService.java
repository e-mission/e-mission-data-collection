package edu.berkeley.eecs.emission.cordova.tracker.bluetooth;

// Android imports
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import java.util.Collection;
import java.util.Set;
import java.util.HashSet;

// Altbeacon imports
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Beacon;

// Other plugin imports
import edu.berkeley.eecs.emission.R;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.tracker.ExplicitIntent;
import edu.berkeley.eecs.emission.cordova.tracker.verification.SensorControlChecks;

// Saving data
import edu.berkeley.eecs.emission.cordova.usercache.UserCacheFactory;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.BluetoothBLE;

public class BluetoothService extends Service {
    private static String TAG = "BluetoothService";
    private BeaconManager beaconManager;
    private Set<Beacon> scanned;
    private String uuid = "bf3df3b1-6e46-35fa-86e5-927c95dd096c";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(this, TAG, "onStartCommand called!!!!");

        // Instantiate variables
        beaconManager = BeaconManager.getInstanceForApplication(this);
        scanned = new HashSet<>();

        // Check to see if we even have permission to scan at all
        boolean bluetoothPermissions = SensorControlChecks.checkBluetoothScanningPermissions(this);

        if (!bluetoothPermissions) {
            return 1;
        }

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
            this.sendBroadcast(new ExplicitIntent(this, R.string.transition_ble_beacon_found));
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
                Log.d(BluetoothService.this, TAG, "Range notifier called...");

                if (beacons.size() > 0) {
                    Log.d(BluetoothService.this, TAG, "Found beacons " + beacons.toString());

                    for (Beacon beacon : beacons) {
                        // Even though we are scanning for beacons in a certain region, beacons with different UUID's still come up.
                        // Until we figure out why that is the case, put this check in place so we only save the ones with the right UUID.
                        if (beacon.getId1().toString().equals(uuid)) {
                            scanned.add(beacon);
                            BluetoothBLE currWrapper = BluetoothBLE.initRangeUpdate(
                                beacon.getId1().toString(),
                                System.currentTimeMillis() / 1000, // timestamp in always in secs for us
                                beacon.getId2().toInt(),
                                beacon.getId3().toInt(),
// TODO: Figure out what to do with the distance calculations
                                "ProximityNear",
// accuracy = rough distance estimate limited to two decimal places (in metres)
// NO NOT ASSUME THIS IS ACCURATE - it is effected by radio interference and obstacles
// from https://github.com/petermetz/cordova-plugin-ibeacon
                                Math.round((beacon.getDistance() * 100)/100),
                                beacon.getRssi());
                            UserCacheFactory.getUserCache(BluetoothService.this)
                                .putSensorData(R.string.key_usercache_bluetooth_ble, currWrapper);
                            // End scanning early
                            numScans = 5;
                        }
                    }
                }

                numScans++;

                if (numScans >= 5) {
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
