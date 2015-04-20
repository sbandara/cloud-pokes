package com.sbandara.cloudpokes;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

import com.eclipsesource.json.JsonObject;

final class ApnsNotification extends Notification {

	private final static byte cmd_send = 2, id_token = 1, id_payload = 2,
			id_identifier = 3, id_expiration = 4, id_priority = 5;

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
		
	private byte[] integerToBytes(int j, int n_bytes) {
		byte buf[] = new byte[n_bytes];
		long mod = 256;
		for (int k = n_bytes - 1; k > 0; k --) {
			buf[k] = (byte) (j % mod);
			j -= buf[k];
			mod *= 256;
		}
		buf[0] = (byte) j;
		return buf;
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
		out.write(cmd_send);
		out.write(integerToBytes(frm_len, 4));
		out.write(new byte[] {id_token, 0, 32});
		out.write(token.getApnsToken());
		out.write(id_payload);
		out.write(integerToBytes(payload.length, 2));
		out.write(payload);		
		out.write(new byte[] {id_identifier, 0, 4});
		out.write(integerToBytes(identifier, 4));
		out.write(new byte[] {id_expiration, 0, 4, 0, 0, 0, 0});
		out.write(new byte[] {id_priority, 0, 1, 10});
	}

	@Override
	public void send() {
		ApnsPushSender.getInstance().sendNotification(this);
	}
}
