package edu.berkeley.eecs.emission.cordova.tracker.location;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.RequiresApi;

import edu.berkeley.eecs.emission.cordova.tracker.Constants;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.NotificationHelper;
import edu.berkeley.eecs.emission.R;

public class ForegroundServiceComm {
  private static String TAG = "ForegroundServiceComm";
  private Context mCtxt;
  private TripDiaryStateMachineForegroundService mService;
  private boolean mBound = false;
  private int nRecursion = 0;
  private String pendingMsgState = null;

  public ForegroundServiceComm(Context context) {
    mCtxt = context;
    mCtxt.bindService(getForegroundServiceIntent(), connection, 0);
  }

  public void setNewState(String newState) {
    nRecursion++;
    if (mBound) {
      Log.d(mCtxt, TAG, "Service successfully bound, setting state");
      mService.setStateMessage(newState);
    } else {
          Intent fsi = getForegroundServiceIntent();
        if (nRecursion < 5) {
          Log.d(mCtxt, TAG, "nRecursion = "+nRecursion+" rebinding ");
          pendingMsgState = newState;
          mCtxt.bindService(fsi, connection, 0);
        } else if (nRecursion < 10) {
          Log.d(mCtxt, TAG, "nRecursion = "+nRecursion+" restarting before rebind ");
          pendingMsgState = newState;
          TripDiaryStateMachineForegroundService.startProperly(mCtxt);
          mCtxt.bindService(fsi, connection, 0);
        } else {
          NotificationHelper.createNotification(mCtxt, Constants.TRACKING_ERROR_ID,
                  null, mCtxt.getString(R.string.unable_resolve_issue));
          Log.e(mCtxt, TAG, "Too many recursions, generating notification");
        }
    }
  }

  public void unbind() {
    Log.d(mCtxt, TAG, "Destroying FSM service, unbinding foreground service");
    mCtxt.unbindService(connection);
  }

  private ServiceConnection connection = new ServiceConnection() {
    private int nRetries = 0;

    @Override
    public void onServiceConnected(ComponentName className,
                                   IBinder service) {
      // We've bound to LocalService, cast the IBinder and get LocalService instance
      TripDiaryStateMachineForegroundService.LocalBinder binder = (TripDiaryStateMachineForegroundService.LocalBinder) service;
      mService = binder.getService();
      Log.e(mCtxt, TAG, "Successfully bound to service "+ mService);
      mBound = true;
      if (pendingMsgState != null) {
        setNewState(pendingMsgState);
      }
    }

    @Override
    public void onServiceDisconnected(ComponentName arg0) {
      mService = null;
      mBound = false;
      Log.d(mCtxt, TAG, "service disconnected, nRetries = "+nRetries);
      TripDiaryStateMachineForegroundService.startProperly(mCtxt);
      if (nRetries < 10) {
        Log.d(mCtxt, TAG, "starting service and retrying ");
        mCtxt.bindService(getForegroundServiceIntent(), connection, 0);
        try {
          Thread.sleep(30 * 1000);
        } catch (InterruptedException e) { e.printStackTrace(); };
      }
    }
  };

  private Intent getForegroundServiceIntent() {
    Intent intent = new Intent(mCtxt, TripDiaryStateMachineForegroundService.class);
    return intent;
  }
}
