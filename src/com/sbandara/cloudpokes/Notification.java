package com.sbandara.cloudpokes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

public abstract class Notification {

	private String sound = null, message = null;
	private boolean did_seal = false;
	protected final JsonObject json_payload = new JsonObject();
	private final DeviceToken token;
	
	protected Notification(DeviceToken token) {
		this.token = token;
	}
	
	public final DeviceToken getToken() { return token; }
	
	public final Notification setSound(String sound) {
		this.sound = sound;
		return this;
	}
	
	public abstract Notification setDefaultSound();
	
	public final String getSound() { return sound; }
	
	public final Notification setMessage(String message) {
		this.message = message;
		return this;
	}
	
	public final String getMessage() { return message; }
	
	public final Notification setCustom(String key, JsonValue value) {
		if ("aps".equals(key)) {
			throw new IllegalArgumentException(key + " is a reserved key.");
		}
		json_payload.set(key, value);
		return this;
	}
		
	public void send() {
		if (! did_seal) {
			sealPayload();
			did_seal = true;		
		}
		dispatch();
	}

	abstract void sealPayload();

	abstract void dispatch();
	
	final static byte[] jsonToByteArray(JsonValue json) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		OutputStreamWriter writer = new OutputStreamWriter(baos);
		try {
			json.writeTo(writer);
			writer.close();
		}
		catch (IOException e) {
			throw new RuntimeException();
		}
		return baos.toByteArray();
	}

	abstract void writeToOutputStream(OutputStream out) throws IOException;
	
	public final static Notification withToken(DeviceToken token) {
		if (token.isApnsToken()) {
			return new ApnsNotification(token);
		}
		else if (token.isGcmToken()) {
			return new GcmNotification(token);
		}
		else {
			throw new UnsupportedOperationException("No sender for token type");
		}
	}
}
