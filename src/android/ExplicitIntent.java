package edu.berkeley.eecs.emission.cordova.tracker;

import android.content.Context;
import android.content.Intent;

/*
 * After API 26, android does not support delivery of implicit intents.
 * We use broadcast intents extensively in the FSM, so instead of changing every location,
 * we create an explicit intent that sets the package name correctly and converts it to an implicit intent.
 * And while we are here, we can also simplify the arguments passed in to the interface
 */

public class ExplicitIntent extends Intent {
    public ExplicitIntent(Context context, int actionId) {
        super(context.getString(actionId));
        setPackage(context.getPackageName());
    }

    public ExplicitIntent(Context context, String actionString) {
        super(actionString);
        setPackage(context.getPackageName());
    }

    public ExplicitIntent(Context context, Intent intent) {
        super(intent);
        setPackage(context.getPackageName());
    }
}
