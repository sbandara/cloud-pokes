package com.sbandara.cloudpokes;

import javax.xml.bind.DatatypeConverter;

public class DeviceToken {
	
	private final byte[] apns_token;
	private final String gcm_token;

	public static DeviceToken apnsToken(byte[] apns_token) {
		return new DeviceToken(apns_token);
	}
	
	public static DeviceToken apnsTokenFromBase64(String apns_token) {
		byte[] token = DatatypeConverter.parseBase64Binary(apns_token);
		if (token.length != 32) {
			throw new IllegalArgumentException("Not an APNS token.");
		}
		return new DeviceToken(token);
	}

	DeviceToken(byte[] apns_token) {
		this.apns_token = apns_token;
		gcm_token = null;
	}

	public final static DeviceToken gcmToken(String gcm_token) {
		return new DeviceToken(gcm_token);
	}

	DeviceToken(String gcm_token) {
		apns_token = null;
		this.gcm_token = gcm_token;
	}
	
	public final boolean isGcmToken() { return gcm_token != null; }
	
	public final boolean isApnsToken() { return apns_token != null; }
	
	public final String getGcmToken() {
		if (gcm_token == null) {
			throw new UnsupportedOperationException("Not a GCM token.");
		}
		return gcm_token;
	}

	public final byte[] getApnsToken() {
		if (apns_token == null) {
			throw new UnsupportedOperationException("Not an APNS token.");
		}
		return apns_token;
	}
}
