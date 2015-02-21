package edu.berkeley.eecs.cfc_tracker.auth;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;

import edu.berkeley.eecs.cfc_tracker.auth.GoogleAccountManagerAuth;
import edu.berkeley.eecs.cfc_tracker.ConnectionSettings;

/*
 * Singleton class that is used to store profile information about the user.
 * The current information is:
 * - user email address
 * - whether they are authenticated or not
 * - whether they have clicked the "link to moves" button or not
 */

public class UserProfile {
	private String userEmail;
	private boolean googleAuthDone;
	private boolean linkWithMovesDone;
	private Context savedCtxt;
	File privateFile;
	
	/*
	 * We can't use the classic singleton here because the classic java singleton
	 * creates one object per classloader. While testing this, we found that android
	 * uses separate classloaders for the user visible activities and for the
	 * background calls, so a username set by the main activity would not be available
	 * in the background sync. So we use the filesystem as a shared datastore
	 * to synchronize between the two contexts.
	 */
	private static UserProfile theInstance;
	
	private UserProfile(Context context) {
		System.out.println("Creating a new instance of the user profile");
		savedCtxt = context;
		File privateFileDir = context.getFilesDir();
		privateFile = new File(privateFileDir, "userProfile");
		// Prevent clients from creating the profile directly
	}
	
	private void loadFromFile() {
		try {
			BufferedReader in = new BufferedReader(new FileReader(privateFile));
			String profileJSONStr = in.readLine();
			JSONObject profileObj = new JSONObject(profileJSONStr);
			userEmail = profileObj.getString("userEmail");
			googleAuthDone = profileObj.getBoolean("googleAuthDone");
			linkWithMovesDone = profileObj.getBoolean("linkWithMovesDone");
			in.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void saveToFile() {		
		try {
			JSONObject profileObj = new JSONObject();
			profileObj.put("userEmail", userEmail);
			profileObj.put("googleAuthDone", googleAuthDone);
			profileObj.put("linkWithMovesDone", linkWithMovesDone);
			String profileJSONStr = profileObj.toString();
			PrintWriter out = new PrintWriter(privateFile);
			out.println(profileJSONStr);
			out.flush();
			out.close();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/*
	 * For all the get methods below here, we only re-read the file
	 * if the userEmail == null. This is for performance reasons,
	 * since reading the file every time we want to check the user email seems
	 * pretty expensive. We still save on every set since we don't plan to set
	 * that often. This can cause R after W errors if we have multiple
	 * contexts. Luckily, I think that we get a new context for each background invocation.
	 * 
	 * The main problem if not would occur if the user selected a different user using the settings
	 * activity. If the background invocations reused the context, then we would reuse the old
	 * account in spite of that. Write a unit case to test this?
	 * 
	 * We will also unset the values when we get authentication errors, which should avoid
	 * most of the real errors.
	 * 
	 * Will I feel better about this if we switch to a database?
	 */
	public String getUserEmail() {
		if (userEmail == null) {
			loadFromFile();
		}
		return userEmail;
	}
	public void setUserEmail(String userEmail) {		
		System.out.println("Setting userEmail from "+this.userEmail+" -> "+userEmail);
		this.userEmail = userEmail;
		saveToFile();
	}
	public boolean isGoogleAuthDone() {
		if (userEmail == null) {
			loadFromFile();
		}
		return googleAuthDone;
	}
	public void setGoogleAuthDone(boolean googleAuthDone) {
		this.googleAuthDone = googleAuthDone;
		saveToFile();
	}
	public boolean isLinkWithMovesDone() {
		if (userEmail == null) {
			loadFromFile();
		}
		return linkWithMovesDone;
	}
	public void setLinkWithMovesDone(boolean linkWithMovesDone) {		
		this.linkWithMovesDone = linkWithMovesDone;
		saveToFile();
	}
	
	public synchronized static UserProfile getInstance(Context context) {
		if (theInstance == null) {
			theInstance = new UserProfile(context);
		}
		return theInstance;
	}
	
	public interface RegisterUserResult {
		abstract void registerComplete(String result);
	}
	
	/*
	 * TODO: Pull out the AsyncTask code, now used here, in the DisplayResultSummary, and in the AppSettings
	 * into a separate class.
	 */
	public void registerUser(RegisterUserResult callbackObj) {
//		final long startMs = System.currentTimeMillis();
		final Context thisContext = savedCtxt;
		final String userName = getUserEmail();
		final RegisterUserResult thisCallback = callbackObj;
		
		AsyncTask<Void, Void, String> task = new AsyncTask<Void, Void, String>() {
			@Override
			protected String doInBackground(Void... params) {				
				try {
					String userToken = GoogleAccountManagerAuth.getServerToken(thisContext, userName);
					// TODO: Restructure this later to combine with the data sync class
					HttpPost msg = new HttpPost(ConnectionSettings.getConnectURL(thisContext)+
							"/profile/create");
					msg.setHeader("Content-Type", "application/json");
					
					JSONObject toPush = new JSONObject();
					toPush.put("user", userToken);
					msg.setEntity(new StringEntity(toPush.toString()));
					
				    AndroidHttpClient connection = AndroidHttpClient.newInstance("E-Mission");
				    HttpResponse response = connection.execute(msg);
				    System.out.println("Got response "+response+" with status "+response.getStatusLine());
				    BufferedReader in = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
				    StringBuilder builder = new StringBuilder();
				    String currLine = null;   
				    while ((currLine = in.readLine()) != null) {
				    	builder.append(currLine+"\n");
				    }
				    String rawHTML = builder.toString();
				    // System.out.println("Raw HTML = "+rawHTML);
				    in.close();
				    connection.close();
				    return rawHTML;
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return "<html><body>"+e.getLocalizedMessage()+"</body></html>";
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return "<html><body>"+e.getLocalizedMessage()+"</body></html>";
				}
			}

			@Override
			protected void onPostExecute(String taskResult) {
				if (taskResult != null) {
					thisCallback.registerComplete(taskResult);
				} else {
				}
				/*
				long endMs = System.currentTimeMillis();
				statsHelper.storeMeasurement(thisContext.getString(R.string.result_display_duration),
						String.valueOf(endMs - startMs), String.valueOf(endMs));
						*/
			}

		};
		task.execute((Void)null);
	}
}
