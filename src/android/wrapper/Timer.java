package edu.berkeley.eecs.emission.cordova.tracker.wrapper;

/**
 * Created by shankari on 10/30/16.
 *
 */

public class Timer {
    private long startMillis;
    public Timer() {
        this.startMillis = System.currentTimeMillis();
    };

    public double elapsedSecs() {
        return ((double)(System.currentTimeMillis() - startMillis))/1000;
    }
}
