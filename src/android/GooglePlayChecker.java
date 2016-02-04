package edu.berkeley.eecs.emission.cordova.tracker;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Intent;
import android.os.Bundle;

import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.ConnectionResult;

import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;

/*
 * This code is supposed to ensure that google play services is enabled, and to
 * prompt the user to enable it if it is not. However, while testing, we have
 * not encountered a situation in which Google Play services is obsolete, so
 * most of this code is untested and might not work. However, the fused API
 * guide suggests using it, so let's leave the code in here, along with the
 * caveat that it is untested.
 */

public class GooglePlayChecker {
  private static final String TAG = "GooglePlayChecker";
  private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
  private Activity myActivity;

  public boolean servicesConnected(Activity activity) {
      myActivity = activity;
      // Check that Google Play services is available
      int resultCode =
              GooglePlayServicesUtil.
                      isGooglePlayServicesAvailable(activity);
      // If Google Play services is available
      if (ConnectionResult.SUCCESS == resultCode) {
          // In debug mode, log the status
          Log.d(activity,
                  TAG, "Google Play services is available.");
          // Continue
          return true;
      // Google Play services was not available for some reason.
      // resultCode holds the error code.
      } else {
          // Get the error dialog from Google Play services
          Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(
                  resultCode,
                  activity,
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
              errorFragment.show(activity.getFragmentManager(),
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

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(myActivity, "TAG", "requestCode = "+requestCode+" resultCode = "+resultCode);
        if (requestCode == CONNECTION_FAILURE_RESOLUTION_REQUEST) {
          /*
           * If the result code is Activity.RESULT_OK, try
           * to connect again
           */
            if (resultCode == Activity.RESULT_OK) {
                servicesConnected(myActivity);
            }
        }
    }
}
