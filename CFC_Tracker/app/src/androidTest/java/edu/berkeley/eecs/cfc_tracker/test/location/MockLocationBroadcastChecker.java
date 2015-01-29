package edu.berkeley.eecs.cfc_tracker.test.location;

import android.content.Intent;
import android.util.Log;

/**
 * Created by shankari on 1/18/15.
 */
public class MockLocationBroadcastChecker extends BroadcastChecker {
    private static final String TAG = "MockLocationBroadcastChecker";
    private int code1;
    private int code2;

    public MockLocationBroadcastChecker(String broadcastAction, int code1, int code2) {
        super(broadcastAction);
        this.code1 = code1;
        this.code2 = code2;
        Log.d(TAG, "Creation done with "+broadcastAction+", "+code1+", "+code2);
    }

    public boolean isMatchingIntent(Intent intent) {
        if (super.isMatchingIntent(intent)) {
            int iCode1 = intent.getIntExtra(LocationUtils.KEY_EXTRA_CODE1, 0);
            int iCode2 = intent.getIntExtra(LocationUtils.KEY_EXTRA_CODE2, 0);
            Log.d(TAG, "intent string matches, checking extra codes from intent "+
                iCode1+", "+iCode2+" against stored "+code1+", "+code2);

            if (iCode1 == code1 &&
                    iCode2 == code2) {
                return true;
            }
        }
        return false;
    }
}
