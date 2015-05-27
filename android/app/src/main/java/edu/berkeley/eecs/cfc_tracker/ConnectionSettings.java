package edu.berkeley.eecs.cfc_tracker;

import android.content.Context;

import java.io.InputStream;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManagerFactory;

/*
 * Single class that returns all the connection level settings that need to be customized
 * when 
 */

public class ConnectionSettings {
	public static final String TAG = "ConnectionSettings";
	private static SSLContext sslContext = null;

	public static String getConnectURL(Context ctxt) {
		return ctxt.getString(R.string.connect_url);
	}

	public static String getGoogleWebAppClientID(Context ctxt) {
		return ctxt.getString(R.string.google_webapp_client_id);
	}
	
	public static String getSmapKey(Context ctxt) {
		return "unused";
	}

	/**
	 * Return an encrypted URL connection potentially using a self-signed certificate. The
	 * serverPath should start with a "/" and is appended to the host name
	 * (Example serverPath: "/tripManager/storeSensedTrips")
	 *
	 * See https://developer.android.com/training/articles/security-ssl.html#CommonProblems for
	 * the details on how this works. We're using a self-signed certificate, and over-riding the
	 * hostname verifier, which is OK because we fully control the remote server.
	 *
	 * @param serverPath
	 * @param ctxt
	 * @return
	 */
	public static HttpsURLConnection getConnection(String serverPath, Context ctxt) {
		HttpsURLConnection urlConnection = null;
		boolean useSelfSignedCert = ctxt.getResources().getBoolean(R.bool.use_self_signed_cert);
		// If this is the first time connecting to the server, load the SSL context
		if (sslContext == null && useSelfSignedCert)
			sslContext = loadSelfSignedCertificate(ctxt);

		try {
			final String hostIp = ConnectionSettings.getConnectURL(ctxt);
			URL url = new URL(hostIp + serverPath);

			urlConnection = (HttpsURLConnection) url.openConnection();

			// If we're using a self-signed certificate, then attach it to the URLConnection.
			// Warning, the returned HttpsConnection will *only* talk to the server specified
			// in the self-signed cert. It won't talk to https://www.google.com for instance.
			if (useSelfSignedCert) {
				// Override the default hostname verifier because it does not like to use I.P. addresses
				// Just check that the host that we're talking to is the same as the host we specify
				// in the connection URL.
				HostnameVerifier hostnameVerifier = new HostnameVerifier() {
					@Override
					public boolean verify(String hostname, SSLSession session) {
						return hostIp.contains(session.getPeerHost());
					}
				};

				urlConnection.setHostnameVerifier(hostnameVerifier);

				// Tell the URLConnection to use a SocketFactory from our SSLContext
				urlConnection.setSSLSocketFactory(sslContext.getSocketFactory());
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return urlConnection;

	}

	/**
	 * Load the self signed certificate into an SSL context and use that for encrypted
	 * communication with the server. The self signed certificate should be the public
	 * key of the server you want to communicate with.
	 *
	 * This only need to happens once.
	 * @param ctxt
	 */
	private static SSLContext loadSelfSignedCertificate(Context ctxt) {
		SSLContext context = null;
		try {

			CertificateFactory cf = CertificateFactory.getInstance("X.509");

			InputStream caInput = ctxt.getResources().openRawResource(R.raw.cert);
			Certificate ca = cf.generateCertificate(caInput);
			Log.d(TAG, "CERT DETAILS= " + ((X509Certificate) ca).getSubjectDN());
			caInput.close();

			// Create a KeyStore containing our trusted certificate
			String keyStoreType = KeyStore.getDefaultType();
			KeyStore keyStore = KeyStore.getInstance(keyStoreType);
			keyStore.load(null, null);
			keyStore.setCertificateEntry("hues", ca);

			// Create a TrustManager that trusts the certificates in our KeyStore
			String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
			tmf.init(keyStore);

			// Create an SSLContext that uses our TrustManager
			context = SSLContext.getInstance("TLS");
			context.init(null, tmf.getTrustManagers(), null);

		} catch (Exception e) {
			e.printStackTrace();
		}

		return context;
	}
}
// getTripsToPush