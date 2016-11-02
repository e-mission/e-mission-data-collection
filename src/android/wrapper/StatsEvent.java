package edu.berkeley.eecs.emission.cordova.tracker.wrapper;

import android.content.Context;
import android.content.pm.PackageManager;

import java.util.jar.Attributes;

public class StatsEvent {
    public StatsEvent(Context ctxt, int name_id) {
        this(ctxt, name_id, -1);
    }

    public StatsEvent(Context ctxt, int name_id, double reading) {
        // we're passing all info in seconds, so we divide by 1000
        this(ctxt, name_id, ((double)System.currentTimeMillis())/1000, reading);
    }

    public StatsEvent(Context ctxt, int name_id, double ts_secs, double reading) {
        this.name = ctxt.getString(name_id);
        this.ts = ts_secs;
        this.reading = reading;
        try {
            this.client_app_version = ctxt.getPackageManager().getPackageInfo(ctxt.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            this.client_app_version = "UNKNOWN";
        }
        this.client_os_version = android.os.Build.VERSION.RELEASE;
    }
    private String name;
    private double reading;
    private double ts;
    private String client_app_version;
    private String client_os_version;
}
