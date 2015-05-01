package com.sbandara.cloudpokes.mockapns;

import java.util.EventListener;

/**
 * The listener interface for observing APNS packets as they are being
 * accepted by the mock server. Tests should implement this interface to
 * confirm packet receipt. 
 */
public interface ApnsServerEventListener extends EventListener {
	
	/**
	 * Invoked when a packet was accepted by the mock server. Because
	 * multiple server threads report to this method, some implementations
	 * will require synchronization.
	 * @param packet the APNS packet that was accepted by the server.
	 */
	public void didAcceptPacket(ApnsPacket packet);
	
	/**
	 * Invoked when an error was detected during transmission of a packet.
	 * The rejected, incompletely parsed packet and an error code is sent
	 * along. To provide insight for debugging, the error codes are more
	 * fine-grained than APNS response codes.
	 * @param packet the partially constructed packet object, or null if
	 * entirely nothing was received
	 * @param error an error code describing the reason for rejection
	 */
	public void didRejectPacket(ApnsPacket packet, byte error);
}
