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
        cleanAllFiles();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        // Let's ensure that we cleanup even if there are exceptions during the tests
        cleanAllFiles();
    }

    private void cleanAllFiles() {
        File[] existingFiles = testCtxt.getExternalFilesDir(null).listFiles();
        for (int i = 0; i < existingFiles.length; i++) {
            existingFiles[i].delete();
        }
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
        String fileName = testCtxt.getExternalFilesDir(null) + "/test-long-term.log";
        System.out.println("fileName = " + fileName);
        FileHandler fh = new FileHandler(fileName);
        fh.setFormatter(Log.simpleFormatter);
        testLogger.addHandler(fh);

        String msg = "This is a test log message";
        testLogger.warning(msg);
        File[] potentialFiles = testCtxt.getExternalFilesDir(null).listFiles();
        // sensor_uuid.props, userProfile, test-long-term.log
        System.out.println("potential files are " + Arrays.toString(potentialFiles));
        assertEquals(potentialFiles.length, 2);
        File[] selectedFiles = testCtxt.getExternalFilesDir(null).listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                if (s.startsWith("test-long-term") && s.endsWith(".log")) {
                    return true;
                } else {
                    return false;
                }
            }
        });
        System.out.println("selected files are " + Arrays.toString(selectedFiles));
        assertEquals(selectedFiles.length, 1);
        BufferedReader testReader = new BufferedReader(new FileReader(selectedFiles[0]));
        checkMessage(testReader.readLine(), msg);
        deleteFileAndLock(selectedFiles[0]);
    }

    public void deleteFileAndLock(File logFile) {
        logFile.delete();
        new File(logFile.getPath() + ".lck").delete();
    }

    public void checkMessage(String line, String msg) {
        System.out.println("Comparing " + line);
        assertEquals(line.split("]")[1], msg);
    }

    public void testRotatingFileHandler() throws Exception {
        Logger testLogger = Logger.getLogger("edu.berkeley.eecs.cfc_tracker.test");
        /*
            This uses a separate prefix for the following reasons:
            a) if we don't do so, then the file names become different (long-term-1.log.1,
            long-term-0.log.1, long-term-2.log.1,....
            this is almost certainly because the previous handler is still around, however,
            attempts to "fix" that didn't work:
                i) removing the handler from the logger
                ii) moving the other test after this one by renaming it, and
                    setting both the logger and the handler to null here
            b) using the iterator retests the same code twice. This tests the same thing in two
                different ways, which should give us a quicker indication if something goes wrong.
         */
        final String PREFIX = "test-rotating-file";
        String pattern = Log.getPattern(testCtxt, PREFIX);
        System.out.println("pattern = " + pattern);
        FileHandler fh = new FileHandler(pattern, 1024 * 1024, 10);
        fh.setFormatter(Log.simpleFormatter);
        testLogger.addHandler(fh);
        // So that we don't junk up the console
        testLogger.setLevel(Level.FINE);
        testLogger.setUseParentHandlers(false);

        String msg = "This is a test log message";
        // We log this 20,000 times to ensure that the files rotate
        int NENTRIES = 20000;
        for (int i = 0; i < NENTRIES; i++) {
            testLogger.warning(msg);
            testLogger.fine(msg);
        }
        File[] potentialFiles = testCtxt.getExternalFilesDir(null).listFiles();
        System.out.println("potential files are " + Arrays.toString(potentialFiles));
        // 3 log files and 1 lock file
        assertEquals(potentialFiles.length, 4);
        File[] selectedFiles = testCtxt.getExternalFilesDir(null).listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                if (s.startsWith(PREFIX) && s.endsWith(".log")) {
                    return true;
                } else {
                    return false;
                }
            }
        });
        System.out.println("selected files are " + Arrays.toString(selectedFiles));
        assertEquals(selectedFiles.length, 3);

        String currLogLine;
        // Since this test starts after the previous one, the index is already at 40,000
        // I am still not quite sure why the entries don't get reset although we are:
        // a) using a new logger
        // b) with a new logger name
        // But resolving that mystery is not important - moving right along...
        int expectedIndex = NENTRIES * 2;

        for (int i=0; i < selectedFiles.length; i++) {
            File currFile = selectedFiles[selectedFiles.length - 1 - i];
            System.out.println("Reading file "+currFile);
            BufferedReader currReader = new BufferedReader(new FileReader(currFile));
            while ((currLogLine = currReader.readLine()) != null) {
                assertEquals(Log.extractMessage(currLogLine), msg);
                int currIndex = Integer.parseInt(Log.extractIndex(currLogLine));
                assertEquals(expectedIndex, currIndex);
                expectedIndex = expectedIndex + 1;
            }
        }
        assertEquals(expectedIndex, NENTRIES * 4);

        for (int i = 0; i < selectedFiles.length; i++) {
            deleteFileAndLock(selectedFiles[i]);
        }
    }

    public void testLogLibrary() throws Exception {
        String msg = "This is a test log message";
        // We log this 20,000 times to ensure that the files rotate
        int NENTRIES = 20000;
        for (int i = 0; i < NENTRIES; i++) {
            Log.d(testCtxt, "TEST", msg);
            Log.i(testCtxt, "TEST", msg);
        }

        Iterator<String> logLineIterator = Log.getLogLineIterator(testCtxt);
        int expectedIndex = 0;
        /*
            Ensure that the lines in the log are returned correctly.
         */
        while (logLineIterator.hasNext()) {
            String currLogLine = logLineIterator.next();
            assertEquals(Log.extractMessage(currLogLine), "TEST : " + msg);
            int currIndex = Integer.parseInt(Log.extractIndex(currLogLine));
            assertEquals(expectedIndex, currIndex);
            expectedIndex = expectedIndex + 1;
        }
        assertEquals(expectedIndex, NENTRIES * 2);
        cleanAllFiles();
    }
}
