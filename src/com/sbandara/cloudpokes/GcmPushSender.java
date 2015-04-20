package com.sbandara.cloudpokes;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.eclipsesource.json.JsonObject;

public final class GcmPushSender extends ServiceConnector {
	
	private static final String url = "https://android.googleapis.com/gcm/send";
	
	private final URL endpoint;
	private final Delegate delegate;

	private static GcmPushSender the_instance = null;
	
	public static void configure(Delegate delegate) {
		the_instance = new GcmPushSender(delegate);
	}
	
	public static GcmPushSender getInstance() {
		if (the_instance == null) {
			throw new IllegalStateException("GcmPushSender not configured.");
		}
		return the_instance;
	}

	public GcmPushSender(Delegate delegate) {
		this.delegate = delegate;
		try {
			endpoint = new URL(url);
		}
		catch (IOException e) {
			throw new RuntimeException("GCM endpoint URL is misconfigured.");
		}
	}
	
	public interface Delegate {
		public String getApiKey();
		public void didSend(Notification notification, String reg_id);
		public void didFail(Notification notification, String error);
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
		notification.sealEnvelope();
		HttpURLConnection conn = getConnection();
		OutputStream os = null;
		try {
			os = conn.getOutputStream();
			notification.writeToOutputStream(os);
		}
		catch (IOException e) {
			System.out.println("Failed to send GCM request.");
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
			System.out.println("Failed to read response from GCM server.");
			delegate.didFail(notification, null);
			return;
		}
		catch (RuntimeException e) {
			System.out.println("Failed to parse response from GCM server.");
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
