package edu.berkeley.eecs.cfc_tracker;

import android.content.Context;
import android.net.http.AndroidHttpClient;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import edu.berkeley.eecs.cfc_tracker.auth.GoogleAccountManagerAuth;
import edu.berkeley.eecs.cfc_tracker.auth.UserProfile;

public class CommunicationHelper {
    public static final String TAG = "CommunicationHelper";

    public static String readResults(Context ctxt, String cacheControlProperty)
            throws MalformedURLException, IOException {
        final String result_url = ConnectionSettings.getConnectURL(ctxt)+"/compare";
        final String userName = UserProfile.getInstance(ctxt).getUserEmail();
        final String userToken = GoogleAccountManagerAuth.getServerToken(ctxt, userName);

        final URL url = new URL(result_url);
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setUseCaches(true);
        connection.setDoOutput(false);
        connection.setDoInput(true);
        connection.setReadTimeout(10000 /*milliseconds*/);
        connection.setConnectTimeout(15000 /* milliseconds */);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User", "" + userToken);

        /* Force the invalidation of the results summary cache entry */
        connection.addRequestProperty("Cache-Control", cacheControlProperty);
        connection.setRequestProperty("Content-Type", "application/json;charset=utf-8");
        connection.connect();

        final InputStream inputStream = connection.getInputStream();
        final int code = connection.getResponseCode();
        Log.d(TAG, "Update Connection response status " + connection.getResponseCode());
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("Failed : HTTP error code : " + connection.getResponseCode());
        }
        final BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
        final StringBuilder builder = new StringBuilder();
        String currLine = null;
        while ((currLine = in.readLine()) != null) {
            builder.append(currLine + "\n");
        }
        final String rawHTML = builder.toString();
        in.close();
        connection.disconnect();
        return rawHTML;
    }

    public static JSONArray getUnclassifiedSections(Context ctxt, String userToken)
            throws JSONException, IOException {
        String commuteTrackerHost = ConnectionSettings.getConnectURL(ctxt);
        String fullURL = commuteTrackerHost + "/tripManager/getUnclassifiedSections";
        String rawJSON = getUserPersonalData(ctxt, fullURL, userToken);
        JSONObject parentObj = new JSONObject(rawJSON);
        return parentObj.getJSONArray("sections");
    }

    /*
     * Pushes the classifications to the host.
     */
    public static void pushClassifications(Context cachedContext, String userToken,
                                           JSONArray userClassification)
            throws IOException, JSONException {
        String commuteTrackerHost = ConnectionSettings.getConnectURL(cachedContext);
        pushJSON(cachedContext, commuteTrackerHost + "/tripManager/setSectionClassification",
                userToken, "updates", userClassification);
    }

    /*
     * Pushes the classifications to the host.
     */
    public static void pushStats(Context cachedContext, String userToken,
                                 JSONObject appStats) throws IOException, JSONException {
        String commuteTrackerHost = ConnectionSettings.getConnectURL(cachedContext);
        pushJSON(cachedContext, commuteTrackerHost + "/stats/set", userToken, "stats", appStats);
    }

    /*
     * Pushes user cache to the server
     */
    public static void phone_to_server(Context cachedContext, String userToken, JSONArray entryArr)
            throws IOException, JSONException {
        String commuteTrackerHost = ConnectionSettings.getConnectURL(cachedContext);
        pushJSON(cachedContext, commuteTrackerHost + "/usercache/put",
                userToken, "phone_to_server", entryArr);
    }

    /*
     * Gets user cache information from server
     */

    public static JSONArray server_to_phone(Context cachedContext, String userToken)
            throws IOException, JSONException {
        String commuteTrackerHost = ConnectionSettings.getConnectURL(cachedContext);
        String fullURL = commuteTrackerHost + "/usercache/get";
        String rawJSON = getUserPersonalData(cachedContext, fullURL, userToken);
        if (rawJSON.trim().length() == 0) {
            // We didn't get anything from the server, so let's return an empty array for now
            // TODO: Figure out whether we need to return a blank array from the server instead
            return new JSONArray();
        }
        JSONObject parentObj = new JSONObject(rawJSON);
        return parentObj.getJSONArray("server_to_phone");
    }


    public static void saveEulaVer(Context ctxt, String eulaVer) throws IOException, JSONException {
        String commuteTrackerHost = ConnectionSettings.getConnectURL(ctxt);
        final String userName = UserProfile.getInstance(ctxt).getUserEmail();
        final String userToken = GoogleAccountManagerAuth.getServerToken(ctxt, userName);
        pushJSON(ctxt, commuteTrackerHost + "/profile/consent", userToken, "version", eulaVer);
    }

    public static void saveMovesAuth(Context ctxt, JSONObject movesAuth)
        throws IOException, JSONException {
        String commuteTrackerHost = ConnectionSettings.getConnectURL(ctxt);
        final String userName = UserProfile.getInstance(ctxt).getUserEmail();
        final String userToken = GoogleAccountManagerAuth.getServerToken(ctxt, userName);
        pushJSON(ctxt, commuteTrackerHost + "/movesCallbackNew", userToken, "movesAuth", movesAuth);
    }

    public static void pushJSON(Context ctxt, String fullURL, String userToken,
                                String objectLabel, Object jsonObjectOrArray)
            throws IOException, JSONException {
        HttpPost msg = new HttpPost(fullURL);
        System.out.println("Posting data to " + msg.getURI());
        msg.setHeader("Content-Type", "application/json");
        JSONObject toPush = new JSONObject();

        toPush.put("user", userToken);
        toPush.put(objectLabel, jsonObjectOrArray);
        msg.setEntity(new StringEntity(toPush.toString()));
        AndroidHttpClient connection = AndroidHttpClient.newInstance(ctxt.getString(R.string.app_name));
        HttpResponse response = connection.execute(msg);
        System.out.println("Got response " + response + " with status " + response.getStatusLine());
        connection.close();
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new IOException();
        }
        // TODO: Decide whether we want to return the server response here as a string instead of returning void
    }

    public static String getUserSettings(Context ctxt) throws
            JSONException, IOException {
        final String userName = UserProfile.getInstance(ctxt).getUserEmail();
        final String userToken = GoogleAccountManagerAuth.getServerToken(ctxt, userName);
        return getUserPersonalData(ctxt, ConnectionSettings.getConnectURL(ctxt)+
                "/profile/settings", userToken);
    }

    public static String registerUser(Context ctxt) throws JSONException, IOException {
        final String userName = UserProfile.getInstance(ctxt).getUserEmail();
        final String userToken = GoogleAccountManagerAuth.getServerToken(ctxt, userName);
        return getUserPersonalData(ctxt, ConnectionSettings.getConnectURL(ctxt)+
                "/profile/create", userToken);
    }

    public static String getUserPersonalData(Context ctxt, String fullURL, String userToken) throws
            JSONException, IOException {
        String result = "";
        HttpPost msg = new HttpPost(fullURL);
        msg.setHeader("Content-Type", "application/json");

        //String result;
        JSONObject toPush = new JSONObject();
        toPush.put("user", userToken);
        msg.setEntity(new StringEntity(toPush.toString()));

        System.out.println("Posting data to "+msg.getURI());

        //create connection
        AndroidHttpClient connection = AndroidHttpClient.newInstance(R.class.toString());
        HttpResponse response = connection.execute(msg);
        StatusLine statusLine = response.getStatusLine();
        System.out.println("Got response "+response+" with status "+statusLine);
        int statusCode = statusLine.getStatusCode();

        if(statusCode == 200){
            BufferedReader in = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            StringBuilder builder = new StringBuilder();
            String currLine = null;
            while ((currLine = in.readLine()) != null) {
                builder.append(currLine+"\n");
            }
            result = builder.toString();
            System.out.println("Result Summary JSON = "+result);
            in.close();
        } else {
            Log.e(R.class.toString(),"Failed to get JSON object");
        }
        connection.close();
        return result;
    }
}
