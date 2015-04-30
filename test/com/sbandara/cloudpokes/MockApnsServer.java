package com.sbandara.cloudpokes;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.EventListener;

public class MockApnsServer {
	
	private final class PortListener implements Runnable {
		
		private ServerSocket server_socket;
		
		PortListener(int port)
				throws IOException {
		    server_socket = new ServerSocket(port);
			Thread listener_thread = new Thread(this);
			listener_thread.start();
		}
		
		public void run() {
			try {
				System.out.println("Waiting for client...");
				for (;;) {
				    Socket client = server_socket.accept();
				    Thread thread = new Thread(new ServerThread(client));
				    thread.start();
				}
			}
			catch (IOException e) {
				System.out.println("Mock APNS server shut down.");
			}
		}
		
		void stop() {
			ServiceConnector.closeQuietly(server_socket);
			server_socket = null;
		}
	}
	
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
	}
	
	public static final class ApnsPacket {
		
		private ApnsPacket() { };
		
		private int notification_id, expires = -1;
		private byte token[] = null, priority = -1;
		private String payload = null;
		
		public int getNotificationId() { return notification_id; }

		public int getExpirationDate() { return expires; }

		public byte[] getToken() { return token; }
		
		public byte getPriority() { return priority; }
		
		public String getPayload() { return payload; }
	}
	
	private final static byte ERROR_HEADER = 8, PROCESSING_ERROR = 1,
			MISSING_DEVICE_TOKEN = 2, MISSING_PAYLOAD = 4,
			INVALID_TOKEN_SIZE = 5, INVALID_PAYLOAD_SIZE = 7, INVALID_TOKEN = 8;
	
	private final class ServerThread implements Runnable {

		private final Socket client;
		private BufferedInputStream is = null;
		private ApnsPacket packet = null, accepted = null;

		ServerThread(Socket client) {
			this.client = client;
		}
		
		private void failConnection(byte code) {
			ByteBuffer response = ByteBuffer.allocate(6).put(ERROR_HEADER)
					.put(code);
			if (code == INVALID_TOKEN) {
				if ((packet != null) && (packet.notification_id != -1)) {
					response.putInt(packet.notification_id);
				}
			}
			else {
				if (accepted != null) {
					response.putInt(accepted.notification_id);
				}
			}
			try {
				client.getOutputStream().write(response.array());
			}
			catch (IOException e) { }
		}
		
		private byte[] readBytes(int len) throws IOException {
			byte[] pack = new byte[len];
			int n_byte, off = 0;
			while (off < pack.length) {
				n_byte = is.read(pack, off, pack.length - off);
				if (n_byte == -1) {
					throw new IOException("Connection dropped.");
				}
				off += n_byte;
			}
			return pack;
		}
		
		private int readInt() throws IOException {
			return ByteBuffer.wrap(readBytes(4)).getInt();
		}

		private short readShort() throws IOException {
			return ByteBuffer.wrap(readBytes(2)).getShort();
		}
		
		private final static int MAX_PAYLOAD_LEN = 2048;

		private byte readPacket() throws IOException {
			int frame_len = readInt();
			while (frame_len > 0) {
				int item_id = is.read();
				if (item_id == -1) {
					return PROCESSING_ERROR;
				}
				short item_len = readShort();
				frame_len -= 3 + item_len;
				if ((item_len < 0) || (frame_len < 0)) {
					return PROCESSING_ERROR;
				}
				switch(item_id) {
				case ApnsNotification.ID_TOKEN:
					if (item_len != 32) {
						return INVALID_TOKEN_SIZE;
					}
					packet.token = readBytes(32);
					break;
				case ApnsNotification.ID_PAYLOAD:
					if (item_len > MAX_PAYLOAD_LEN) {
						return INVALID_PAYLOAD_SIZE;
					}
					packet.payload = new String(readBytes(item_len), "UTF-8");
					break;
				case ApnsNotification.ID_IDENTIFIER:
					if (item_len != 4) {
						return PROCESSING_ERROR;
					}
					packet.notification_id = readInt();
					break;
				case ApnsNotification.ID_EXPIRATION:
					if (item_len != 4) {
						return PROCESSING_ERROR;
					}
					packet.expires = readInt();
					break;
				case ApnsNotification.ID_PRIORITY:
					int priority = is.read();
					if ((item_len != 1) || (priority == -1)) {
						return PROCESSING_ERROR;
					}
					packet.priority = (byte) priority;
					break;
				default:
					return PROCESSING_ERROR;
				}
			}
			return frame_len < 0 ? PROCESSING_ERROR : 0;
		}
		
		public void run() {
			System.out.println("Connected to client.");
			byte status = 0;
			try {
				is = new BufferedInputStream(client.getInputStream());
				for (;;) {
					int header = is.read();
					if (header == -1) {
						return;
					}
					else if (header != ApnsNotification.CMD_SEND) {
						status = PROCESSING_ERROR;
						break;
					}
					packet = new ApnsPacket();
					status = readPacket();
					if (packet.token == null) {
						status = MISSING_DEVICE_TOKEN;
						return;
					}
					if (packet.payload == null) {
						status = MISSING_PAYLOAD;
						return;
					}
					if ((bad_token != null) && (bad_token.equalsApnsToken(
							packet.token))) {
						status = INVALID_TOKEN;
						System.out.println("Bad token.");
						return;
					}
					accepted = packet;
					if (event_listener != null) {
						event_listener.didAcceptPacket(accepted);
					}
				}
			}
			catch (IOException e) {
				status = PROCESSING_ERROR;
			}
			finally {
				if (status != 0) {
					failConnection(status);
				}
				ServiceConnector.closeQuietly(client);
				System.out.println("Connection closed.");
			}
		}			
	}

	private PortListener port_listener = null;
	private ApnsServerEventListener event_listener = null;
	private DeviceToken bad_token = null;
	
	/**
	 * Starts the mock server to accept client connections. Invocations must
	 * specify the port number at which to listen for clients. Repeated calls
	 * cause the mock server to restart.
	 * @param port the port at which to wait for clients to connect
	 */
	public void start(int port)
			throws IOException {
		if (port_listener != null) {
			stop();
		}
		port_listener = new PortListener(port);
	}
	
	/**
	 * Stops the mock server from accepting any further client connections.
	 */
	public void stop() {
		port_listener.stop();
		port_listener = null;
	}
	
	/**
	 * Defines a device token that when received from a client, will trigger an
	 * "invalid token" response with status code 8.
	 * @param bad_token the device token to be recognized as invalid
	 * @return this mock server object for fluent configuration
	 */
	public MockApnsServer setBadToken(DeviceToken bad_token) {
		this.bad_token = bad_token;
		return this;
	}

	/**
	 * Registers the event listener to observe accepted packets. Only the last
	 * listener to be registered will receive callbacks.
	 * @param event_listener the listener to observe accepted packets, or null
	 * to unregister
	 * @return this mock server object for fluent configuration
	 */
	public MockApnsServer setEventListener(ApnsServerEventListener listener) {
		this.event_listener = listener;
		return this;
	}
}
