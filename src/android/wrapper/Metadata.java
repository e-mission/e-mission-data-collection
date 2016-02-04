package edu.berkeley.eecs.emission.cordova.tracker.wrapper;

/**
 * Created by shankari on 7/5/15.
 */

public class Metadata {
    public double getWrite_ts() {
        return write_ts;
    }

    public void setWrite_ts(double write_ts) {
        this.write_ts = write_ts;
    }

    public double getRead_ts() {
        return read_ts;
    }

    public void setRead_ts(double read_ts) {
        this.read_ts = read_ts;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getPlugin() {
        return plugin;
    }

    public void setPlugin(String plugin) {
        this.plugin = plugin;
    }

    public void setTimeZone(String timeZone) { this.time_zone = timeZone; }

    public String getTimeZone() { return time_zone; }


    private double write_ts;
    private double read_ts;
    private String time_zone;
    private String type;
    private String key;
    private String plugin;
    private final String platform = "android";

    public Metadata() {
    }
}
