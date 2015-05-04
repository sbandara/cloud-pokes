package com.sbandara.cloudpokes;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

import com.eclipsesource.json.JsonObject;
import com.sbandara.cloudpokes.util.PacketBuilder;

final class ApnsNotification extends Notification {

	final static byte CMD_SEND = 2, ID_TOKEN = 1, ID_PAYLOAD = 2,
			ID_IDENTIFIER = 3, ID_EXPIRATION = 4, ID_PRIORITY = 5;

	private final int identifier;
	private final ApnsPushSender sender;

	private final static AtomicInteger id_gen = new AtomicInteger(1);

	ApnsNotification(DeviceToken token, ApnsPushSender sender) {
		super(token);
		this.sender = sender;
		identifier = id_gen.getAndIncrement();
	}
	
	@Override
	public Notification setDefaultSound() {
		setSound("default");
		return this;
	}
	
	@Override
	void sealPayload() {
		JsonObject aps = new JsonObject();
		aps.add("alert", getMessage());
		String sound = getSound();
		if (sound != null) {
			aps.add("sound", sound);
		}
		json_payload.add("aps", aps);		
	}
	
	@Override
	void writeToOutputStream(OutputStream out) throws IOException {
		byte[] payload = jsonToByteArray(json_payload);
		final int packet_len = payload.length + 61;
		PacketBuilder builder = new PacketBuilder(packet_len);
		out.write(builder.putArrayItem(ID_TOKEN, getToken().getApnsToken())
				.putArrayItem(ID_PAYLOAD, payload)
				.putIntItem(ID_IDENTIFIER, identifier)
				.putIntItem(ID_EXPIRATION, 0)
				.putByteItem(ID_PRIORITY, (byte) 10).build());
	}

	@Override
	void dispatch() {
		sender.sendNotification(this);
	}
}
