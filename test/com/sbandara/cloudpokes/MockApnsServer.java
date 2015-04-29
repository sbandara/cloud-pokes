package com.sbandara.cloudpokes;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.EventListener;

import com.eclipsesource.json.JsonObject;

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
				    new Thread(new ServerThread(client));
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
		private JsonObject payload = null;
		
		public int getNotificationId() { return notification_id; }

		public int getExpirationDate() { return expires; }

		public byte[] getToken() { return token; }
		
		public byte getPriority() { return priority; }
		
		public JsonObject getPayload() { return payload; }
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
		
		private void failConnection(byte code) throws IOException {
			ByteBuffer response = ByteBuffer.allocate(6).put(ERROR_HEADER)
					.put(code);
			if (code == INVALID_TOKEN) {
				if ((packet == null) || (packet.token == null) ||
						(packet.notification_id == -1)) {
					throw new IllegalStateException(
							"Token from incomplete packet cannot be invalid.");
				}
				response.putInt(packet.notification_id);
			}
			else {
				if (accepted != null) {
					response.putInt(accepted.notification_id);
				}
			}
			OutputStream os = client.getOutputStream();
			os.write(response.array());
			os.close();
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
		
		private final static int HEADER_CODE = 2, MAX_PAYLOAD_LEN = 2048;
		private final static int ID_DEVICE_TOKEN = 1, ID_PAYLOAD = 2,
				ID_IDENTIFIER = 3, ID_EXPIRATION = 4, ID_PRIORITY = 5;

		private void readPacket() throws IOException {
			int frame_len = readInt();
			while (frame_len > 0) {
				int item_id = is.read();
				if (item_id == -1) {
					failConnection(PROCESSING_ERROR);
				}
				short item_len = readShort();
				frame_len -= 3 + item_len;
				if ((item_len < 0) || (frame_len < 0)) {
					failConnection(PROCESSING_ERROR);
				}
				switch(item_id) {
				case ID_DEVICE_TOKEN:
					if (item_len != 32) {
						failConnection(INVALID_TOKEN_SIZE);
					}
					packet.token = readBytes(32);
					break;
				case ID_PAYLOAD:
					if (item_len > MAX_PAYLOAD_LEN) {
						failConnection(INVALID_PAYLOAD_SIZE);
					}
					String json = new String(readBytes(item_len), "UTF-8");
					packet.payload = JsonObject.readFrom(json);
					break;
				case ID_IDENTIFIER:
					if (item_len != 4) {
						failConnection(PROCESSING_ERROR);
					}
					packet.notification_id = readInt();
					break;
				case ID_EXPIRATION:
					if (item_len != 4) {
						failConnection(PROCESSING_ERROR);
					}
					packet.expires = readInt();
					break;
				case ID_PRIORITY:
					int priority = is.read();
					if ((item_len != 1) || (priority == -1)) {
						failConnection(PROCESSING_ERROR);
					}
					packet.priority = (byte) priority;
					break;
				default:
					failConnection(PROCESSING_ERROR);
				}
			}
		}
		
		public void run() {
			System.out.println("Connected to client.");
			try {
				is = new BufferedInputStream(client.getInputStream());
				for (;;) {
					int header = is.read();
					if (header == -1) {
						return;
					}
					else if (header != HEADER_CODE) {
						failConnection(PROCESSING_ERROR);
					}
					packet = new ApnsPacket();
					readPacket();
					if (packet.token == null) {
						failConnection(MISSING_DEVICE_TOKEN);
					}
					if (packet.payload == null) {
						failConnection(MISSING_PAYLOAD);
					}
					if (bad_token != null) {
						if (bad_token.equalsApnsToken(packet.token )) {
							failConnection(INVALID_TOKEN);
						}
					}
					accepted = packet;
					if (event_listener != null) {
						event_listener.didAcceptPacket(accepted);
					}
				}
			}
			catch (IOException e) {
				e.printStackTrace();
			}
			finally {
				ServiceConnector.closeQuietly(is);
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
