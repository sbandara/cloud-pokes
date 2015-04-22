package com.sbandara.cloudpokes;

import java.io.IOException;
import java.io.OutputStream;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

final class GcmNotification extends Notification {
	
	GcmNotification(DeviceToken token) {
		super(token);
	}

	@Override
	public Notification setDefaultSound() {
		setSound(null);
		return this;
	}

	@Override
	protected void sealPayload() {
		json_payload.add("message", getMessage());
		String sound = getSound();
		if (sound != null) {
			json_payload.add("sound", sound);
		}
	}

	@Override
	void writeToOutputStream(OutputStream out) throws IOException {
		JsonObject pack = new JsonObject().add("data", json_payload).add(
				"registration_ids", new JsonArray().add(token.getGcmToken()));
		byte[] payload = jsonToByteArray(pack);
		out.write(payload);
	}

	@Override
	void dispatch() {
		GcmPushSender.getInstance().sendNotification(this);
	}
}
