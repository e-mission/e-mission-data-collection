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
import java.util.Iterator;
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

        // Make sure that we start every test with a clean slate
        File[] existingFiles = testCtxt.getFilesDir().listFiles();
        for (int i = 0; i < existingFiles.length; i++) {
            existingFiles[i].delete();
        }
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testExtractMessage() throws Exception {
        // Should we strip the result to avoid extra whitespace at the beginning?
        assertEquals(Log.extractMessage("[0|ms|WARNING] Message"), " Message");
    }

    public void testExtractIndex() throws Exception {
        assertEquals(Log.extractIndex("[0|ms|WARNING] Message"), "0");
    }

    public void testSimpleFileHandler() throws Exception {
        Logger testLogger = Logger.getLogger("edu.berkeley.eecs.cfc_tracker.test");
        String fileName = testCtxt.getFilesDir() + "/test-long-term.log";
        System.out.println("fileName = " + fileName);
        FileHandler fh = new FileHandler(fileName);
        fh.setFormatter(Log.simpleFormatter);
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
        String pattern = Log.getPattern(testCtxt, Log.logFilePrefix);
        System.out.println("pattern = " + pattern);
        FileHandler fh = new FileHandler(pattern, 1024 * 1024, 10);
        fh.setFormatter(Log.simpleFormatter);
        testLogger.addHandler(fh);
        // So that we don't junk up the console
        testLogger.setLevel(Level.FINE);
        testLogger.setUseParentHandlers(false);

        String msg = "This is a test log message";
        // We log this 20,000 times to ensure that the files rotate
        for (int i = 0; i < 20000; i++) {
            testLogger.warning(msg);
            testLogger.fine(msg);
        }
        File[] potentialFiles = testCtxt.getFilesDir().listFiles();
        System.out.println("potential files are "+ Arrays.toString(potentialFiles));
        // 3 log files and 1 lock file
        assertEquals(potentialFiles.length, 4);
        File[] selectedFiles = testCtxt.getFilesDir().listFiles(Log.simpleFilter);
        System.out.println("selected files are " + Arrays.toString(selectedFiles));
        assertEquals(selectedFiles.length, 3);

        Iterator<String> logLineIterator = Log.getLogLineIterator(testCtxt);
        int expectedIndex = 0;
        /*
            Ensure that the lines in the log are returned correctly.
         */
        while(logLineIterator.hasNext()) {
            String currLogLine = logLineIterator.next();
            assertEquals(Log.extractMessage(currLogLine), msg);
            int currIndex = Integer.parseInt(Log.extractIndex(currLogLine));
            assertEquals(expectedIndex, currIndex);
            expectedIndex = expectedIndex + 1;
        }
        for (int i = 0; i < selectedFiles.length; i++) {
            deleteFileAndLock(selectedFiles[i]);
        }
    }
}
