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
	
	public final void setSound(String sound) { this.sound = sound; }
	
	public abstract void setDefaultSound();
	
	public final String getSound() { return sound; }
	
	public final void setMessage(String message) { this.message = message; }
	
	public final String getMessage() { return message; }
	
	public final void setCustom(String key, JsonValue value) {
		if ("aps".equals(key)) {
			throw new IllegalArgumentException(key + " is a reserved key.");
		}
		json_payload.set(key, value);
	}
	
	protected abstract void sealPayload();
	
	final void sealEnvelope() {
		if (did_seal) {
			throw new IllegalStateException("Notification already sealed.");
		}
		sealPayload();
		did_seal = true;
	}
	
	public abstract void send();
	
	final protected static byte[] jsonToByteArray(JsonValue json) {
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
