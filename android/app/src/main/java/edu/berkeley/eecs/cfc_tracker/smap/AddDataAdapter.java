/**
 * Creates an adapter to post data to the SMAP server
 */
package edu.berkeley.eecs.cfc_tracker.smap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import edu.berkeley.eecs.cfc_tracker.CommunicationHelper;
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

import edu.berkeley.eecs.cfc_tracker.Log;
import edu.berkeley.eecs.cfc_tracker.usercache.BuiltinUserCache;
import edu.berkeley.eecs.cfc_tracker.usercache.UserCache;

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
		/*
		 * We send almost all pending trips to the server
		 */
		
		/*
		 * We are going to send over information for all the data in a single JSON object, to avoid overhead.
		 * So we take a quick check to see if the number of entries is zero.
		 */
        BuiltinUserCache biuc = new BuiltinUserCache(mContext);

        try {
			JSONArray entriesToPush = biuc.sync_phone_to_server();
			if (entriesToPush.length() == 0) {
				System.out.println("No data to send, returning early!");
				return;
            }

            CommunicationHelper.phone_to_server(mContext, userToken, entriesToPush);
            UserCache.TimeQuery tq = getTimeQuery(entriesToPush);
            biuc.clearMessages(tq);

        } catch (JSONException e) {
			Log.e(mContext, TAG, "Error "+e+" while saving converting trips to JSON, skipping all of them");
		} catch (IOException e) {
			Log.e(mContext, TAG, "IO Error "+e+" while posting converted trips to JSON");
		}

        /*
         * Now, read all the information from the server. This is in a different try/catch block,
         * because we want to try it even if the push fails.
         */
        try {
            JSONArray entriesReceived = CommunicationHelper.server_to_phone(mContext, userToken);
            biuc.sync_server_to_phone(entriesReceived);
        } catch (JSONException e) {
            Log.e(mContext, TAG, "Error "+e+" while saving converting trips to JSON, skipping all of them");
        } catch (IOException e) {
            Log.e(mContext, TAG, "IO Error "+e+" while posting converted trips to JSON");
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

    public static UserCache.TimeQuery getTimeQuery(JSONArray pointList) throws JSONException {
        long start_ts = pointList.getJSONObject(0).getJSONObject("metadata").getLong("write_ts");
        long end_ts = pointList.getJSONObject(pointList.length() - 1).getJSONObject("metadata").getLong("write_ts");
        // This might still have a race in which there are new entries added with the same timestamp as the last
        // entry. Use an id instead? Or manually choose a slightly earlier ts to be on the safe side?
        // TODO: Need to figure out which one to do
        UserCache.TimeQuery tq = new UserCache.TimeQuery(R.string.metadata_usercache_write_ts,
                start_ts, end_ts);
        return tq;
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
		HttpPost msg = new HttpPost(emissionServer+"/tripManager/storeSensedTrips");

		System.out.println("Posting data to "+msg);
		msg.setHeader("Content-Type", "application/json");
		msg.setEntity(new StringEntity(data.toString()));
		AndroidHttpClient connection = AndroidHttpClient.newInstance(projectName);
		HttpResponse response = connection.execute(msg);
		System.out.println("Got response "+response+" with status "+response.getStatusLine());
		connection.close();
        if (response.getStatusLine().getStatusCode() == 200) {
            return true;
        } else {
            return false;
        }
	}

    /*
	public String getPath(String serviceName) {
		return "/"+userName+"/"+serviceName;
	}
	*/
}
