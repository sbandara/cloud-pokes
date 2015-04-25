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
	
	public final boolean equalsApnsToken(byte[] token) {
		if ((this.apns_token == null) || (token == null)) {
			return false;
		}
		for (int k = 0; k < this.apns_token.length; k ++) {
			if (this.apns_token[k] != token[k]) {
				return false;
			}
		}
		return true;
	}
	
	public final boolean equalsApnsToken(String base64) {
		if (base64 == null) {
			return false;
		}
		return equalsApnsToken(DatatypeConverter.parseBase64Binary(base64));
	}
	
	public final boolean equalsGcmToken(String token) {
		if (token == null) {
			return false;
		}
		return token.equals(this.gcm_token);
	}
	
	@Override
	public final boolean equals(Object anObject) {
		if (! (anObject instanceof DeviceToken)) {
			return false;
		}
		if (this == anObject) {
			return true;
		}
		DeviceToken other = (DeviceToken) anObject;
		if (other.gcm_token != null) {
			return other.gcm_token.equals(this.gcm_token);
		}
		else return equalsApnsToken(other.apns_token);
	}
	
	@Override
	public final String toString() {
		if (gcm_token != null) {
			return "GCM:" + gcm_token;
		}
		else {
			return "APNS:" + DatatypeConverter.printBase64Binary(apns_token);
		}
	}
	
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
	
	public final String getBase64ApnsToken() {
		if (apns_token == null) {
			throw new UnsupportedOperationException("Not an APNS token.");
		}
		return DatatypeConverter.printBase64Binary(apns_token);
	}
}
