package edu.berkeley.eecs.cfc_tracker.location.actions;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;

import edu.berkeley.eecs.cfc_tracker.R;
import edu.berkeley.eecs.cfc_tracker.log.Log;
import edu.berkeley.eecs.cfc_tracker.obd.FuelEconomyContainer;
import edu.berkeley.eecs.cfc_tracker.usercache.UserCache;
import edu.berkeley.eecs.cfc_tracker.usercache.UserCacheFactory;


public class OBDChangeIntentService extends IntentService {
    public OBDChangeIntentService() {
        super("OBDChangeIntentService");
    }
    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(this, "OBD Intent", "OBD intent service data update");
        UserCache uc = UserCacheFactory.getUserCache(this);
        Bundle b=intent.getBundleExtra("VehicleData");
        FuelEconomyContainer container= new FuelEconomyContainer(b.getFloat("FuelFlow"),b.getFloat("Speed"),b.getFloat("KM"),b.getInt("RPM"), b.getFloat("Liters"));
        uc.putMessage(R.string.key_usercache_OBD,container);
    }
}
