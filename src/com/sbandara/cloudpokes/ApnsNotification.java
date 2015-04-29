package com.sbandara.cloudpokes;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import com.eclipsesource.json.JsonObject;

final class ApnsNotification extends Notification {

	final static byte CMD_SEND = 2, ID_TOKEN = 1, ID_PAYLOAD = 2,
			ID_IDENTIFIER = 3, ID_EXPIRATION = 4, ID_PRIORITY = 5;

	private final int identifier;

	private final static AtomicInteger id_gen = new AtomicInteger(1);

	ApnsNotification(DeviceToken token) {
		super(token);
		identifier = id_gen.incrementAndGet();
	}
	
	@Override
	public Notification setDefaultSound() {
		setSound("default");
		return this;
	}
		
	static byte[] integerToBytes(int value) {
		ByteBuffer buf = ByteBuffer.allocate(4);
		return buf.putInt(value).array();
	}

	static byte[] shortToBytes(short value) {
		ByteBuffer buf = ByteBuffer.allocate(2);
		return buf.putShort(value).array();
	}
	
	@Override
	protected void sealPayload() {
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
		int frm_len = 38 + payload.length + 18;
		out.write(CMD_SEND);
		out.write(integerToBytes(frm_len));
		out.write(new byte[] {ID_TOKEN, 0, 32});
		out.write(token.getApnsToken());
		out.write(ID_PAYLOAD);
		out.write(shortToBytes((short) payload.length));
		out.write(payload);		
		out.write(new byte[] {ID_IDENTIFIER, 0, 4});
		out.write(integerToBytes(identifier));
		out.write(new byte[] {ID_EXPIRATION, 0, 4, 0, 0, 0, 0});
		out.write(new byte[] {ID_PRIORITY, 0, 1, 10});
	}

	@Override
	void dispatch() {
		ApnsPushSender.getInstance().sendNotification(this);
	}
}
