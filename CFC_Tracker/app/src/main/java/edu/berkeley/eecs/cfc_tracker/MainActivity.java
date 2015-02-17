package edu.berkeley.eecs.cfc_tracker;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import edu.berkeley.eecs.cfc_tracker.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import java.io.IOException;

import edu.berkeley.eecs.cfc_tracker.auth.GoogleAccountManagerAuth;
import edu.berkeley.eecs.cfc_tracker.auth.UserProfile;
import edu.berkeley.eecs.cfc_tracker.location.TripDiaryStateMachineReceiver;
import edu.berkeley.eecs.cfc_tracker.storage.OngoingTripStorageHelper;
import edu.berkeley.eecs.cfc_tracker.storage.StoredTripHelper;

public class MainActivity extends Activity {
    boolean initDone = false;
    // The authority for the sync adapter's content provider
    public static final String AUTHORITY = "edu.berkeley.eecs.cfc_tracker.provider";
    // An account type, in the form of a domain name
    public static final String ACCOUNT_TYPE = "openbms.org";
    // The account name
    public static final String ACCOUNT = "dummy_account";
    
    private static final long SYNC_INTERVAL = 30 * 60L; // 30 mins
    
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
	private static final String TAG = "MainActivity";
	
	private static final int MAIN_IN_NUMBERS = 6249;

    private static final int REQUEST_CODE_PICK_ACCOUNT = 1000;
    private static final String SENT_INIT_BROADCAST_KEY = "init_broadcast_done";
    public static final String LOG_FILE_INIT_KEY = "init_log_file_init" ;

    Account mAccount;
    // Our ContentResolver is actually a dummy - does this matter?
    ContentResolver mResolver;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		System.out.println("MainActivity.onCreate called");
		setContentView(R.layout.activity_main);
		if (!initDone) {
			AlarmHandler.setupAlarms(this);
			initDone = true;
		}

		// TODO: Determine whether this is the right place to create this.  This
		// will work for now because we launch the activity on reboot, but we need
		// to figure out our UI story and see if this will always be true. If not,
		// we need to move it (and the alarm setup) to some other location.
		mAccount = GetOrCreateSyncAccount(this);
		System.out.println("mAccount = "+mAccount);
	    // Get the content resolver for your app
	    mResolver = getContentResolver();
	    // Turn on automatic syncing for the default account and authority
	    ContentResolver.setSyncAutomatically(mAccount, AUTHORITY, true);
	    
	    /*
	     * This is the SECOND time that the online tutorial has been bogus and caused me to waste
	     * hours in getting the framework working. Contrary to the code in:
	     * https://developer.android.com/training/sync-adapters/running-sync-adapter.html
	     * it turns out that the last argument to addPeriodicSync is in SECONDS, not MILLISECONDS.
	     * So when I had 15 * 1000L, it didn't run in any visible time.
	     * Changing it to 15 causes it to run at a frequency that is visible.
	     * Of course, we don't really want to run this at this frequency in the real world.
	     * Need to tweak this. Make it a configurable option?
	     * 
	     * Also, note that sometimes, the networking in the emulator gets disconnected,
	     * and that might be the cause for the sync not happening as well.
	     * 
	     * TODO: Test to see whether the manual sync works even when the network is detected as down
	     * (need to wait for the emulator to get into this bad state again)
	     */
	    ContentResolver.addPeriodicSync(mAccount, AUTHORITY, new Bundle(), SYNC_INTERVAL);
	    System.out.println("Setting the resolver to sync automatically");

        /*
        TripDiaryStateMachineReceiver stateMachineReceiver = new TripDiaryStateMachineReceiver(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(getString(R.string.transition_initialize));
        filter.addAction(getString(R.string.transition_exited_geofence));
        filter.addAction(getString(R.string.transition_stopped_moving));
        filter.addAction(getString(R.string.transition_stop_tracking));

        LocalBroadcastManager.getInstance(this)
                .registerReceiver(stateMachineReceiver, filter);
        */

        // Initialize the state transitions
        // We want to initialize the first time the app is launched, but not on
        // subsequent creates. We keep track of that in the shared preferences,
        // In the real world, we will want to do this as part of an onboarding
        // process.

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (!sp.getBoolean(SENT_INIT_BROADCAST_KEY, false)) {
            sendBroadcast(new Intent(getString(R.string.transition_initialize)));
            SharedPreferences.Editor editor = sp.edit();
            editor.putBoolean(SENT_INIT_BROADCAST_KEY, true);
            editor.apply();
        }

        if (!sp.getBoolean(LOG_FILE_INIT_KEY, false)) {
            try {
                Log.initWithFile(this);
                SharedPreferences.Editor editor = sp.edit();
                editor.putBoolean(LOG_FILE_INIT_KEY, true);
                editor.apply();
            } catch (IOException e) {
                NotificationHelper.createNotification(
                        this, MAIN_IN_NUMBERS, e.getMessage());
            }
        }

    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	public void startTracking(View view) {
		System.out.println("MainActivity sending start service !!");
		// NOTE: new Intent(this, MainActivity.class) here instead FAILS
		Intent intent = new Intent();
		// NOTE: you HAVE to set the action here. Skipping the action here,
		// and skipping the intentFilter in the android manifest FAILS 
		if (servicesConnected()) {
			intent.setAction(getString(R.string.transition_initialize));
			sendBroadcast(intent);
		} else {
			// not connected, just skip for now
			// TODO: Test and figure out better error handling here
		}
	}
	
	public void stopTracking(View view) {
		System.out.println("MainActivity sending stop service !!");
		Intent intent = new Intent();
		intent.setAction(getString(R.string.transition_stop_tracking));
		sendBroadcast(intent);
	}
	
	public void forceSync(View view) {
		System.out.println("MainActivity forcing sync");
	    Bundle b = new Bundle();
        b.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        b.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
		ContentResolver.requestSync(mAccount, AUTHORITY, new Bundle());
	}
	
	public void clearDb(View view) {
		System.out.println("MainActivity forcing sync");
		new OngoingTripStorageHelper(this).clear();
		new StoredTripHelper(this).clear();
	}

    public void loginToGoogle(View view) {
        new GoogleAccountManagerAuth(this, REQUEST_CODE_PICK_ACCOUNT).getUserName();
    }
	
	@Override
	protected void onStart() {
		super.onStart();
		System.out.println("MainActivity.onStart called");
	}

	@Override
	protected void onStop() {
		super.onStop();
		System.out.println("MainActivity.onStop called");
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		System.out.println("MainActivity.onDestroy called");
	}

  public static Account GetOrCreateSyncAccount(Context context) {
    // Get an instance of the Android account manager
    AccountManager accountManager =
            (AccountManager) context.getSystemService(
                    ACCOUNT_SERVICE);
    Account[] existingAccounts = accountManager.getAccountsByType(ACCOUNT_TYPE);
    assert(existingAccounts.length <= 1);
    if (existingAccounts.length == 1) {
    	return existingAccounts[0];
    }
	  
	// Create the account type and default account
	Account newAccount = new Account(ACCOUNT, ACCOUNT_TYPE);	  
    /*
     * Add the account and account type, no password or user data
     * If successful, return the Account object, otherwise report an error.
     */
    if (accountManager.addAccountExplicitly(newAccount, null, null)) {
      return newAccount;
    } else {
      System.err.println("Unable to create a dummy account to sync with!");
      return null;
    }
  }
  
  private boolean servicesConnected() {
      // Check that Google Play services is available
      int resultCode =
              GooglePlayServicesUtil.
                      isGooglePlayServicesAvailable(this);
      // If Google Play services is available
      if (ConnectionResult.SUCCESS == resultCode) {
          // In debug mode, log the status
          Log.d(TAG,
                  "Google Play services is available.");
          // Continue
          return true;
      // Google Play services was not available for some reason.
      // resultCode holds the error code.
      } else {
          // Get the error dialog from Google Play services
          Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(
                  resultCode,
                  this,
                  CONNECTION_FAILURE_RESOLUTION_REQUEST);

          // If Google Play services can provide an error dialog
          if (errorDialog == null) {
        	  return false;
          } else {
              // Create a new DialogFragment for the error dialog
              ErrorDialogFragment errorFragment =
                      new ErrorDialogFragment();
              // Set the dialog in the DialogFragment
              errorFragment.setDialog(errorDialog);
              // Show the error dialog in the DialogFragment
              errorFragment.show(getFragmentManager(),
                      "Location Updates");
              return false;
          }
      }
  }
  
  // Define a DialogFragment that displays the error dialog
  public static class ErrorDialogFragment extends DialogFragment {
      // Global field to contain the error dialog
      private Dialog mDialog;
      // Default constructor. Sets the dialog field to null
      public ErrorDialogFragment() {
          super();
          mDialog = null;
      }
      // Set the dialog to display
      public void setDialog(Dialog dialog) {
          mDialog = dialog;
      }
      // Return a Dialog to the DialogFragment.
      @Override
      public Dialog onCreateDialog(Bundle savedInstanceState) {
          return mDialog;
      }
  }

  /*
   * Handle results returned to the FragmentActivity
   * by Google Play services
   */
  @Override
  protected void onActivityResult(
          int requestCode, int resultCode, Intent data) {
      // Decide what to do based on the original request code
      switch (requestCode) {
          case CONNECTION_FAILURE_RESOLUTION_REQUEST :
          /*
           * If the result code is Activity.RESULT_OK, try
           * to connect again
           */
           switch (resultCode) {
           		case Activity.RESULT_OK :
                /*
                 * Try the request again
                 */
           		 servicesConnected();
                 break;
            }
          case REQUEST_CODE_PICK_ACCOUNT:
              Log.d(TAG, "Got result of picking account! "+resultCode+", "+data);
              if (resultCode == Activity.RESULT_OK) {
                  String userEmail = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                  Toast.makeText(this, userEmail, Toast.LENGTH_SHORT).show();
                  UserProfile.getInstance(this).setUserEmail(userEmail);
                  Log.d(TAG, "After saving, username is "+
                          UserProfile.getInstance(this).getUserEmail());
              } else if (resultCode == Activity.RESULT_CANCELED) {
                  Toast.makeText(this, "You must pick an account", Toast.LENGTH_SHORT).show();
              }
      }
   }
}
