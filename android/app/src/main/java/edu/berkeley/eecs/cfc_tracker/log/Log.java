package edu.berkeley.eecs.cfc_tracker.log;

import android.content.Context;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Formatter;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Created by shankari on 1/19/15.
 * Drop-in replacement for android.Log which logs to a file that we can then
 * upload to the server.
 */

public class Log {
    public static String logFilePrefix = "long-term";
    private static Logger logger;
    private static DatabaseLogHandler dbLogger;
    private static final int MB = 1024 * 1024;

    public static java.util.logging.Formatter simpleFormatter = new java.util.logging.Formatter() {
        @Override
        public String format(LogRecord logRecord) {
            return "[" + logRecord.getSequenceNumber() + "|" +
                        logRecord.getMillis() + "|" +
                        // getInstance ensures that we use a SHORT instance type
                        SimpleDateFormat.getInstance().format(new Date(logRecord.getMillis())) + "|" +
                        logRecord.getLevel() +"]" +
                        logRecord.getMessage() + "\n";
        }
    };

    public static FilenameFilter simpleFilter = new java.io.FilenameFilter() {
        @Override
        public boolean accept(File file, String s) {
            if (s.startsWith(logFilePrefix) && s.endsWith(".log")) {
                return true;
            } else {
                return false;
            }
        }
    };

    public static File getLogBase(Context context) {
        return context.getExternalFilesDir(null);
    }

    /*
     Returns a file in the external storage directory. This is one that is under the public root
     (i.e. at the same level as "DCIM" or "Android". It is intended for data that needs to be shared
     with other applications. This is currently unused, but can be used in getLogBase() if we want to
     make our logs more visible.
     */
    public static File getBaseInExternalStorageDirectory(Context ctxt) {
        String ourDirName = Environment.getExternalStorageDirectory().getAbsolutePath() + "/CFC_Tracker";
        File ourDir = new File(ourDirName);
        System.out.println("Our dir = "+ourDirName);
        if (ourDir.exists() == false) {
            ourDir.mkdirs();
        }
        return ourDir;
    }

    static class LogLinesIterator implements Iterator<String> {
        private File[] sortedList;
        private int currIndex = 0;
        private BufferedReader currReader = null;
        private String nextLine = null;

        protected LogLinesIterator(Context ctxt) throws IOException {
            File[] selectedFiles = getLogBase(ctxt).listFiles(simpleFilter);
            System.out.println("selectedFiles = "+java.util.Arrays.toString(selectedFiles));
            sortedList = new File[selectedFiles.length];
            for (int i = 0; i < selectedFiles.length; i++) {
                // We need the -1 because the max index has to be one less than the length
                // so the 0th original element should be mapped to the 9th new element
                // if there are 10 files (10 - 1 - 0)
                sortedList[selectedFiles.length - 1 - i] = selectedFiles[i];
            }
            System.out.println("sortedFiles = "+java.util.Arrays.toString(sortedList));
            if (sortedList.length > 0) {
                currReader = new BufferedReader(new FileReader(sortedList[currIndex]));
                nextLine = getNextLine();
            }
        }

        private String getNextLine() throws IOException {
            String nextLineInFile = currReader.readLine();
            if (nextLineInFile != null) {
                // There are more lines in this file, we are can just return them
                return nextLineInFile;
            } else {
                // This could either be the end of this file, or the end of all files.
                // Let's try to figure out which
                currIndex = currIndex + 1;
                if (currIndex < sortedList.length) {
                    // There are more files, so we can start reading from the next one
                    currReader = new BufferedReader(new FileReader(sortedList[currIndex]));
                    return currReader.readLine();
                } else {
                    // This was the last file, and we got to the end of it, so we are done
                    return null;
                }
            }
        }

        @Override
        public boolean hasNext() {
            return nextLine != null;
        }

        @Override
        public String next() {
            String toReturn = nextLine;
            try {
                nextLine = getNextLine();
            } catch (IOException e) {
                nextLine = null;
            }
            // System.out.println("Returning "+toReturn);
            return toReturn;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("LogLinesIterator does not support removal");
        }
    }

    public static Iterator<String> getLogLineIterator(Context ctxt) throws IOException {
        return new LogLinesIterator(ctxt);
    }

    public static String extractMessage(String logLine) {
        /*
        System.out.println("While extracting message from "+logLine+", split results are "+
            java.util.Arrays.toString(logLine.split("]")));
            */
        return logLine.split("]")[1];
    }
    public static String extractIndex(String logLine) {
        /*
        System.out.println("While extracting index from "+logLine+", split results are "+
            java.util.Arrays.toString(logLine.split("\\|")));
            */
        return logLine.split("\\|")[0].substring(1);
    }

    public static String getPattern(Context ctxt, String logFilePrefix) {
        String pattern = getLogBase(ctxt)+"/"+logFilePrefix+"-%g.log";
        System.out.println("Returning pattern "+pattern);
        return pattern;
    }

    public static void flush(Context ctxt) {
        System.out.println("Flushing handler "+getLogger(ctxt).getHandlers()[0]);
        getLogger(ctxt).getHandlers()[0].flush();
    }

    public static Logger getLogger(Context ctxt) {
        if (logger == null) {
            System.out.println("logger == null, lazily creating new logger");
            logger = Logger.getLogger("edu.berkeley.eecs.cfc_tracker");
            String pattern = getPattern(ctxt, logFilePrefix);
            System.out.println("Initializing file handler with pattern " + pattern);
            try {
                FileHandler fh = new FileHandler(pattern, 10 * MB, 10, true); // 10 files of 10 MB each
                fh.setFormatter(simpleFormatter);
                logger.addHandler(fh);
                logger.setLevel(Level.FINE);
                logger.setUseParentHandlers(false);
            } catch (IOException e) {
                // TODO: generate notification here instead
                System.out.println("Error "+e+" while creating file handler, logging is only to the short-term adb logcat");
            }
            dbLogger = new DatabaseLogHandler(ctxt);
        }
        // System.out.println("Returning logger with "+logger.getHandlers().length+" handlers "+logger.getHandlers());
        return logger;
    }

    public static void d(Context ctxt, String TAG, String message) {
        getLogger(ctxt).log(Level.FINE,
                String.format("%s : %s", TAG, message));
        dbLogger.log(TAG+" "+message);
        android.util.Log.d(TAG, message);
    }

    public static void i(Context ctxt, String TAG, String message) {
        getLogger(ctxt).log(Level.INFO,
                String.format("%s : %s", TAG, message));
        dbLogger.log(TAG+" "+message);
        android.util.Log.i(TAG, message);
    }

    public static void w(Context ctxt, String TAG, String message) {
        getLogger(ctxt).log(Level.WARNING,
                String.format("%s : %s", TAG, message));
        dbLogger.log(TAG+ " "+message);
        android.util.Log.w(TAG, message);
    }

    public static void e(Context ctxt, String TAG, String message) {
        getLogger(ctxt).log(Level.SEVERE,
                String.format("%s : %s", TAG, message));
        dbLogger.log(TAG +" "+message);
        android.util.Log.e(TAG, message);
    }
}
