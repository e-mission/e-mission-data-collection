/**
 * Creates an adapter to post data to the SMAP server
 */
package edu.berkeley.eecs.cfc_tracker.smap;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import edu.berkeley.eecs.cfc_tracker.ConnectionSettings;
import edu.berkeley.eecs.cfc_tracker.Constants;
import edu.berkeley.eecs.cfc_tracker.R;
import edu.berkeley.eecs.cfc_tracker.auth.GoogleAccountManagerAuth;
import edu.berkeley.eecs.cfc_tracker.auth.UserProfile;
import edu.berkeley.eecs.cfc_tracker.storage.DataUtils;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.net.http.AndroidHttpClient;
import android.os.Bundle;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManagerFactory;

import edu.berkeley.eecs.cfc_tracker.Log;

/**
 * @author shankari
 *
 */
public class AddDataAdapter extends AbstractThreadedSyncAdapter {
	private static final String USER = "userName";

	private static final String TAG = "AddDataAdapter";
	
	private String projectName;
	private static String READINGS_NAME = "Readings";
	
	// private String userName;

	File privateFileDir;
	Properties uuidMap;
	// TODO: Delete after we start getting real values
	Random pseudoRandom;
	boolean syncSkip = false;
    private Context mContext = null;

	public AddDataAdapter(Context context, boolean autoInitialize) {
		super(context, autoInitialize);
		privateFileDir = context.getFilesDir();
		System.out.println("AddDataAdapter constructor called with "+privateFileDir);
		// We read the project name here because that's where we have access to a context
		projectName = context.getString(R.string.app_name);
		// It's enough to initialize the uuidMap at startup, since they don't change on a regular basis
		try {
			uuidMap = getUUIDMap(privateFileDir);
		} catch (IOException e) {
			// TODO: Built in some retry logic here
			System.err.println("Unable to save uuid map to file, so no point in publishing data anyway. Skipping sync ");
			syncSkip = true;
		}
		// The username is currently a uuid, which is better for privacy, and is also easier to implement
		// because we don't have to pass data between the main activity and this adapter, AND we don't have
		// to worry about what will happen if the user forgets to sign in, etc
		
		// userName = (String)uuidMap.get(USER);
        /*
		System.out.println("uuidMap.get(USER) = "+uuidMap.get(USER)+
				" userName = "+userName);
				*/

		// Create a random number generator since we don't yet have real values
		// TODO: Remove this once we get real values
		pseudoRandom = new Random();

        mContext = context;
		// Our ContentProvider is a dummy so there is nothing else to do here
	}
	
	/* (non-Javadoc)
	 * @see android.content.AbstractThreadedSyncAdapter#onPerformSync(android.accounts.Account, android.os.Bundle, java.lang.String, android.content.ContentProviderClient, android.content.SyncResult)
	 */
	@Override
	public void onPerformSync(Account account, Bundle extras, String authority,
			ContentProviderClient provider, SyncResult syncResult) {
		if (syncSkip == true) {
			System.err.println("Something is wrong and we have been asked to skip the sync, exiting immediately");
			return;
		}

        String emission_host = ConnectionSettings.getConnectURL(mContext);
        System.out.println("About to connect to host "+emission_host);

        String userName = UserProfile.getInstance(mContext).getUserEmail();
        System.out.println("retrieved user name = "+userName);

        if (userName == null || userName.trim().equals("")) {
            System.err.println("Don't have a username, exiting immediately");
            return;
        }
        // First, get a token so that we can make the authorized calls to the server
        String userToken = GoogleAccountManagerAuth.getServerToken(mContext, userName);
		//userToken = null;
		/*
		 * We send almost all pending trips to the server
		 */
		
		/*
		 * We are going to send over information for all the data in a single JSON object, to avoid overhead.
		 * So we take a quick check to see if the number of entries is zero.
		 */
		try {
			JSONArray tripsToPush = DataUtils.getTripsToPush(getContext());

			if (tripsToPush.length() == 0) {
				System.out.println("No data to send, returning early!");
				return;
			}
		
			JSONObject toSend = new JSONObject();
			toSend.put("sections", tripsToPush);
            toSend.put("user", userToken);
		
			Log.i(TAG, "About to post JSON object "+toSend);
			boolean success = pushTripsToServer(emission_host, toSend);
            if (success) {
                Log.i(TAG, "Push successful, deleting trips");
                DataUtils.deletePushedTrips(getContext(), tripsToPush);
            } else {
                Log.i(TAG, "Push unsuccessful, retaining trips");
            }
		} catch (JSONException e) {
			Log.e(TAG, "Error "+e+" while saving converting trips to JSON, skipping all of them");
		} catch (IOException e) {
			Log.e(TAG, "IO Error "+e+" while posting converted trips to JSON");			
		}
	}

	public Properties getUUIDMap(File privateFileDir) throws FileNotFoundException, IOException {
		File uuidFile = new File(privateFileDir, "sensor-uuid.props");
		Properties uuidMap = new Properties();
		try {
			uuidMap.load(new FileInputStream(uuidFile));
		} catch (IOException e) {
			uuidMap.put(USER, UUID.randomUUID().toString());
			System.out.println("Created UUID for USER");
			for (int i = 0; i < Constants.sensors.length; i++) {
				uuidMap.put(Constants.sensors[i], UUID.randomUUID().toString());
			}
			uuidMap.store(new FileOutputStream(uuidFile), null);
		}
		return uuidMap;
	}
	
	/*
	 * We add the parts to the object for each sensor based on the order in:
	 * http://www.cs.berkeley.edu/~stevedh/smap2/archiver.html
	 * 
	 * The Metadata name recommendations are from Tyler.
	 * TODO: Check with him on whether these are special or not
	 */
	public JSONObject createTopLevelObject() throws JSONException {
		JSONObject retObject = new JSONObject();
		for (int i = 0; i < Constants.sensors.length; i++) {
			JSONObject sensorObj = new JSONObject();
			/*
			 * Note the / before the name here. That's because this name represents a path.
			 * If you remove it, this won't show up in the tree view of the sMAP server.
			 * Note also that none of the other names require this /
			 */
			// retObject.put(getPath(Constants.sensors[i]), sensorObj);
			
			JSONObject metadataObj = new JSONObject();
			// All our data is for the E-Mission project
			metadataObj.put("SourceName", projectName);
			metadataObj.put("PointName", Constants.sensors[i]);
			// At this point, we don't know who the user is. Put that into settings?
			sensorObj.put("Metadata", metadataObj);
			
			JSONObject propertyObject = new JSONObject();
			propertyObject.put("Timezone", java.util.TimeZone.getDefault().getID());
			propertyObject.put("ReadingType", "double");
			sensorObj.put("Properties", propertyObject);
			
			JSONArray readings = new JSONArray();
			sensorObj.put(READINGS_NAME, readings);		
			
			sensorObj.put("uuid", uuidMap.get(Constants.sensors[i]));
		}
		System.out.println("Returning object skeleton "+retObject);
		return retObject;
	}
	
	class NotUUIDFileFilter extends Object implements FilenameFilter {
		public boolean accept(File dir, String fileName) {
			if(fileName.contains("uuid")) {
				return false;
			} else {
				return true;
			}
		}
	}
	
	public void addDataToSmapServer(JSONObject data, String smapHost)
			throws IOException {
		HttpPost msg = new HttpPost(smapHost+"/add/"+ConnectionSettings.getSmapKey(mContext));
		System.out.println("Posting data to "+msg);
		msg.setHeader("Content-Type", "application/json");
		msg.setEntity(new StringEntity(data.toString()));
		AndroidHttpClient connection = AndroidHttpClient.newInstance(projectName);
		HttpResponse response = connection.execute(msg);
		System.out.println("Got response "+response+" with status "+response.getStatusLine());
		connection.close();
	}
	
	public boolean pushTripsToServer(String emissionServer, JSONObject data)
			throws IOException {

		// TODO: Recover from errors better
		// TODO: Provide some better documentation of how this works

		//final String hostIp = mContext.getResources().getString(R.string.remote_host_name);
		final String hostIp = ConnectionSettings.getConnectURL(mContext);
		try {

			HttpsURLConnection urlConnection =
					ConnectionSettings.getConnection("/tripManager/storeSensedTrips", mContext);

			urlConnection.setRequestMethod("POST");
			urlConnection.addRequestProperty("Content-Type", "application/json");
			urlConnection.setDoOutput(true);

			DataOutputStream wr = new DataOutputStream(urlConnection.getOutputStream());
			wr.writeBytes(data.toString());
			wr.flush();
			wr.close();

			int responseCode = urlConnection.getResponseCode();
			Log.d(TAG, "Post parameters : " + data.toString());
			Log.d(TAG, "Response Code : " + responseCode + ", Response Message : " + urlConnection.getResponseMessage());

			BufferedReader in = new BufferedReader(
					new InputStreamReader(urlConnection.getInputStream()));

			StringBuffer response = new StringBuffer();
			String inputLine;
			while ((inputLine = in.readLine()) != null) response.append(inputLine);

			in.close();

			//print result
			Log.d(TAG, "Response string: " + response.toString());
			Log.d(TAG, "Response message: " + urlConnection.getResponseMessage());
			Log.d(TAG, "Response code: " + urlConnection.getResponseCode());

			urlConnection.disconnect();
			if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_OK)
				return true;
			else
				return false;

		} catch (Exception e) {
			e.printStackTrace();
		} finally {

		}

		// There must have been an error, return false
		return false;
	}

    /*
	public String getPath(String serviceName) {
		return "/"+userName+"/"+serviceName;
	}
	*/
}
