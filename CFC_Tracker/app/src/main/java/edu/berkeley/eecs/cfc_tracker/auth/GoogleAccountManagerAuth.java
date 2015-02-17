package edu.berkeley.eecs.cfc_tracker.auth;

import java.io.IOException;
import java.sql.Connection;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.AccountPicker;

import edu.berkeley.eecs.cfc_tracker.ConnectionSettings;
import edu.berkeley.eecs.cfc_tracker.Log;
import edu.berkeley.eecs.cfc_tracker.R;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

public class GoogleAccountManagerAuth {

    Activity mCtxt;
    int mRequestCode;
    public static String TAG = "GoogleAccountManagerAuth";

	public GoogleAccountManagerAuth(Activity ctxt, int requestCode) {
		mCtxt = ctxt;
		mRequestCode = requestCode;
	}
	
	public void getUserName() {
		try {
			String[] accountTypes = new String[]{"com.google"};

			/*
    		Account[] existingAccounts = accountManager.getAccountsByType("com.google");
    		assert(existingAccounts.length >= 1);
        	Toast.makeText(mCtxt, existingAccounts[0].name, Toast.LENGTH_SHORT).show();
    		return existingAccounts[0].name;
			 */
    	
			Intent intent = AccountPicker.newChooseAccountIntent(null, null,
					accountTypes, true, null, null, null, null);
        
			// Note that because we are starting the activity using mCtxt, the activity callback
			// invoked will not be the one in this class, but the one in the original context.
			// In our current flow, that is the one in the MainActivity
			mCtxt.startActivityForResult(intent, mRequestCode);
		} catch (ActivityNotFoundException e) {
			// If the user does not have a google account, then 
			// this exception is thrown
			AlertDialog.Builder alertDialog = new AlertDialog.Builder(mCtxt);
			alertDialog.setTitle("Account missing");
//			alertDialog.setIcon(R.drawable.ic);
			alertDialog.setMessage("Continue by signing in to google");
			alertDialog.setNeutralButton("OK", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					mCtxt.startActivity(new Intent(Settings.ACTION_ADD_ACCOUNT));
				}
			});
			alertDialog.show();
		}
	}
	
	public static String getServerToken(Context context, String userName) {
		String serverToken = null;
		try {
			String AUTH_SCOPE = "audience:server:client_id:"+ConnectionSettings.getGoogleWebAppClientID(context);
			serverToken = GoogleAuthUtil.getToken(context,
					userName, AUTH_SCOPE);
		} catch (UserRecoverableAuthException e) {
			// TODO Auto-generated catch block
			context.startActivity(e.getIntent());
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (GoogleAuthException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("serverToken = "+serverToken);
		return serverToken;
	}
}
