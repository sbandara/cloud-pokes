package com.sbandara.cloudpokes;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.EventListener;

import com.eclipsesource.json.JsonObject;

public class MockApnsServer implements Runnable {
	
	private static final class PortListener implements Runnable {
		
		private ServerSocket server_socket = null;
		private final int port;
		private final ApnsServerEventListener event_listener;
		
		public PortListener(int port, ApnsServerEventListener listener) {
			this.port = port;
			this.event_listener = listener;
			Thread listener_thread = new Thread(this);
			listener_thread.start();
		}
		
		public void run() {
			try {
			    server_socket = new ServerSocket(port);
				for (;;) {
				    Socket client = server_socket.accept();
				    new Thread(new MockApnsServer(client, event_listener));
				}
			}
			catch (IOException e) { }
		}
		
		public void stop() {
			// TODO: send shutdown code 10 to all connected clients.
			ServiceConnector.closeQuietly(server_socket);
			server_socket = null;
		}
	}
	
	public interface ApnsServerEventListener extends EventListener {
		public void didReceivePacket(ApnsPacket packet);
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
	
	private final Socket client;
	private final ApnsServerEventListener listener;
	private BufferedInputStream is = null;
	private ApnsPacket packet = null, accepted = null;
	
	private MockApnsServer(Socket client, ApnsServerEventListener listener) {
		this.client = client;
		this.listener = listener;
	}
	
	private final static byte ERROR_HEADER = 8, PROCESSING_ERROR = 1,
			MISSING_DEVICE_TOKEN = 2, MISSING_PAYLOAD = 4,
			INVALID_TOKEN_SIZE = 5, INVALID_PAYLOAD_SIZE = 7, INVALID_TOKEN = 8;
	
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
				accepted = packet;
				listener.didReceivePacket(accepted);
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		finally {
			ServiceConnector.closeQuietly(is);
			ServiceConnector.closeQuietly(client);
		}
	}
	
	private static PortListener port_listener;
	
	public static void start(int port, ApnsServerEventListener listener) {
		if (port_listener != null) {
			port_listener = new PortListener(port, listener);
		}
		else {
			throw new IllegalStateException("Server is already running.");
		}
	}
	
	public static void stop() {
		port_listener.stop();
		port_listener = null;
	}
}
