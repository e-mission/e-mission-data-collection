package edu.berkeley.eecs.cfc_tracker;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Created by shankari on 1/19/15.
 * Drop-in replacement for android.Log which logs to a file that we can then
 * upload to the server.
 */

public class Log {
    private static final Logger logger =
            Logger.getLogger("edu.berkeley.eecs.cfc_tracker");

    public static void initWithFile(Context ctxt) throws IOException {
        FileHandler fh = new FileHandler(ctxt.getFilesDir()+"long-term.log");
        logger.addHandler(fh);
    }

    public static Logger getLogger() {
        return logger;
    }

    public static void d(String TAG, String message) {
        getLogger().log(Level.FINE,
                String.format("%s : %s", TAG, message));
        android.util.Log.d(TAG, message);
    }

    public static void i(String TAG, String message) {
        getLogger().log(Level.INFO,
                String.format("%s : %s", TAG, message));
        android.util.Log.i(TAG, message);
    }

    public static void w(String TAG, String message) {
        getLogger().log(Level.WARNING,
                String.format("%s : %s", TAG, message));
        android.util.Log.w(TAG, message);
    }

    public static void e(String TAG, String message) {
        getLogger().log(Level.SEVERE,
                String.format("%s : %s", TAG, message));
        android.util.Log.e(TAG, message);
    }

}
