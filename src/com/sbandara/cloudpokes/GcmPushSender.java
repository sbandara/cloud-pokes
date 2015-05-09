package com.sbandara.cloudpokes;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eclipsesource.json.JsonObject;
import com.sbandara.cloudpokes.util.ServiceConnector;

final class GcmPushSender extends ServiceConnector {
	
	private static final String url = "https://android.googleapis.com/gcm/send";
	private static final String TAG = "GcmPushSender";
	private static final Logger logger = LoggerFactory.getLogger(TAG);
	
	private final URL endpoint;
	private final GcmDelegate delegate;

	GcmPushSender(GcmDelegate delegate) {
		this.delegate = delegate;
		try {
			endpoint = new URL(url);
		}
		catch (IOException e) {
			throw new RuntimeException("GCM endpoint URL is misconfigured.");
		}
	}
	
	private HttpURLConnection getConnection() {
		HttpURLConnection conn;
		try {
			conn = (HttpURLConnection) endpoint.openConnection();
			conn.setRequestMethod("POST");
		}
		catch (IOException never) {
			throw new RuntimeException("Failed to open HTTP POST connection.");
		}
		conn.setRequestProperty("Content-Type", "application/json");
		conn.setRequestProperty("Authorization", "key=" + delegate.getApiKey());
		conn.setDoOutput(true);
		conn.setUseCaches(false);
		return conn;
	}
	
	void sendNotification(Notification notification) {
		HttpURLConnection conn = getConnection();
		OutputStream os = null;
		try {
			os = conn.getOutputStream();
			notification.writeToOutputStream(os);
		}
		catch (IOException e) {
			logger.error("Failed to send GCM request.");
			conn.disconnect();
			return;
		}
		finally {
			closeQuietly(os);
		}
		InputStreamReader reader = null;
		InputStream is = null;
		JsonObject response = null;
		try {
			is = conn.getInputStream();
		    reader = new InputStreamReader(is);
		    response = JsonObject.readFrom(reader);
		}
		catch (IOException e) {
			logger.error("Failed to read response from GCM server.");
			delegate.didFail(notification, null);
			return;
		}
		catch (RuntimeException e) {
			logger.error("Failed to parse response from GCM server.");
			delegate.didFail(notification, null);
			return;
		}
		finally {
			if (reader == null) {
				closeQuietly(is);
			}
			else {
				closeQuietly(reader);
			}
		}
		try {
			JsonObject device = response.get("results").asArray().get(0)
					.asObject();
			String error = device.getString("error", null);
			if (error == null) {
				String reg_id = device.getString("registration_id", null);
				delegate.didSend(notification, reg_id);
			}
			else {
				delegate.didFail(notification, error);
			}
		}
		catch (RuntimeException e) {
			delegate.didFail(notification, null);
		}
	}
}
