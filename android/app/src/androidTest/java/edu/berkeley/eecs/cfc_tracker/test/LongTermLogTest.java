package edu.berkeley.eecs.cfc_tracker.test;

import android.content.Context;
import android.test.AndroidTestCase;
import android.test.RenamingDelegatingContext;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import edu.berkeley.eecs.cfc_tracker.Log;

import edu.berkeley.eecs.cfc_tracker.storage.DataUtils;
import edu.berkeley.eecs.cfc_tracker.storage.StoredTripHelper;

/**
 * Created by shankari on 6/20/15.
 */
public class LongTermLogTest extends AndroidTestCase {
    private Context testCtxt;

    // We want to store a long-term log for
    public LongTermLogTest() {
        super();
    }

    protected void setUp() throws Exception {
		/*
		 * Don't need to populate with test data here, can do that in the test cases.
		 */
        super.setUp();
        RenamingDelegatingContext context =
                new RenamingDelegatingContext(getContext(), "test_");
        testCtxt = context;

        File[] existingFiles = testCtxt.getFilesDir().listFiles();
        for (int i = 0; i < existingFiles.length; i++) {
            existingFiles[i].delete();
        }

        // Make sure that we start every test with a clean slate
        // DataUtils.clearOngoingDb(testCtxt);
        // new StoredTripHelper(testCtxt).clear();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testSimpleFileHandler() throws Exception {
        Logger testLogger = Logger.getLogger("edu.berkeley.eecs.cfc_tracker.test");
        String fileName = testCtxt.getFilesDir() + "/test-long-term.log";
        System.out.println("fileName = " + fileName);
        FileHandler fh = new FileHandler(fileName);
        fh.setFormatter(new java.util.logging.Formatter() {
            @Override
            public String format(LogRecord logRecord) {
                return "[" + logRecord.getSequenceNumber() + "|" + logRecord.getMillis() + "|" + logRecord.getLevel() +"]" +
                        logRecord.getMessage() + "\n";
            }
        });
        testLogger.addHandler(fh);

        String msg = "This is a test log message";
        testLogger.warning(msg);
        File[] potentialFiles = testCtxt.getFilesDir().listFiles();
        // sensor_uuid.props, userProfile, test-long-term.log
        System.out.println("potential files are "+ Arrays.toString(potentialFiles));
        assertEquals(potentialFiles.length, 2);
        File[] selectedFiles = testCtxt.getFilesDir().listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                if (s.startsWith("test-long-term") && s.endsWith(".log")) {
                    return true;
                } else {
                    return false;
                }
            }
        });
        System.out.println("selected files are "+ Arrays.toString(selectedFiles));
        assertEquals(selectedFiles.length, 1);
        BufferedReader testReader = new BufferedReader(new FileReader(selectedFiles[0]));
        checkMessage(testReader.readLine(), msg);
        deleteFileAndLock(selectedFiles[0]);
    }

    public void deleteFileAndLock(File logFile) {
        logFile.delete();
        new File(logFile.getPath()+".lck").delete();
    }

    public void checkMessage(String line, String msg) {
        System.out.println("Comparing "+line);
        assertEquals(line.split("]")[1], msg);
    }

    public void testRotatingFileHandler() throws Exception {
        Logger testLogger = Logger.getLogger("edu.berkeley.eecs.cfc_tracker.test");
        String pattern = testCtxt.getFilesDir() + "/test-long-term-%g.log";
        System.out.println("pattern = " + pattern);
        FileHandler fh = new FileHandler(pattern, 10 * 1024 * 1024, 10);
        fh.setFormatter(new java.util.logging.Formatter() {
            @Override
            public String format(LogRecord logRecord) {
                return "[" + logRecord.getSequenceNumber() + "|" + logRecord.getMillis() + "|" + logRecord.getLevel() +"]" +
                        logRecord.getMessage() + "\n";
            }
        });
        testLogger.addHandler(fh);
        // So that we don't junk up the console
        testLogger.setLevel(Level.FINE);
        testLogger.setUseParentHandlers(false);

        String msg = "This is a test log message";
        for (int i = 0; i < 200000; i++) {
            testLogger.warning(msg);
            testLogger.fine(msg);
        }
        File[] potentialFiles = testCtxt.getFilesDir().listFiles();
        System.out.println("potential files are "+ Arrays.toString(potentialFiles));
        // Two standard files as usual, then log file has rolled over, so two of those, and two lock files for the log files
        // since the logging infrastructure starts a new log file if the existing file is in use
        assertEquals(potentialFiles.length, 4);
        File[] selectedFiles = testCtxt.getFilesDir().listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                if (s.startsWith("test-long-term") && s.endsWith(".log")) {
                    return true;
                } else {
                    return false;
                }
            }
        });
        System.out.println("selected files are "+ Arrays.toString(selectedFiles));
        assertEquals(selectedFiles.length, 3);
        for (int i = 0; i < selectedFiles.length; i++) {
            BufferedReader in = new BufferedReader(new FileReader(testCtxt.getFilesDir()+"/test-long-term-"+i+".log"));
            System.out.println("Reading logs from file "+selectedFiles[i]);

            String readLine = in.readLine();
            while(readLine != null) {
                // Let's skip the first line
                readLine = in.readLine();
                System.out.println("readLine = " + readLine);
            }
            // assertEquals(in.readLine(), msg);
        }
        for (int i = 0; i < selectedFiles.length; i++) {
            deleteFileAndLock(selectedFiles[i]);
        }
    }
}
