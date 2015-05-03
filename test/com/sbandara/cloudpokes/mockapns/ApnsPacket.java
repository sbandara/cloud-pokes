package com.sbandara.cloudpokes.mockapns;

/**
 * ApnsPacket represents accepted packets as received by the mock server.
 * Getters provide access to various request properties and can be used to
 * validate the correct transmission of push notification requests.
 */
public final class ApnsPacket {
	
	ApnsPacket() { };
	
	int notification_id = -1;
	int expires = -1;
	byte token[] = null;
	byte priority = -1;
	String payload = null;
	
	/**
	 * @return the arbitrary notification ID that was received as frame
	 * item 3, or <code>-1</code> if no notification ID was detected 
	 */
	public int getNotificationId() { return notification_id; }

	/**
	 * @return the UNIX epoch expiration date in seconds, or zero, as
	 * received as frame item 4, or <code>-1</code> if no expiration date
	 * was detected
	 */
	public int getExpirationDate() { return expires; }

	/**
	 * @return the device token that was received as frame item 1, or
	 * <code>null</code> if no token was detected
	 */
	public byte[] getToken() { return token; }
	
	/**
	 * @return the priority code, either 10 or 5, that was received as
	 * frame item 5, or <code>-1</code> if no priority code was detected
	 */
	public byte getPriority() { return priority; }
	
	/**
	 * @return the JSON payload that was received as frame item 2, or
	 * null if no payload was detected
	 */
	public String getPayload() { return payload; }
}