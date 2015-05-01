package com.sbandara.cloudpokes.mockapns;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class MockApnsServer {
	
	private final class PortListener implements Runnable {
		
		private ServerSocket server_socket;
		
		PortListener(int port) throws IOException {
		    server_socket = new ServerSocket(port);
			Thread listener_thread = new Thread(this);
			listener_thread.start();
		}
		
		private void spawnServerThread(Socket client) {
			int k = 0;
		    while ((k < MAX_CONN) && (conns[k ++] != null));
		    if (k == MAX_CONN) {
		    	closeQuietly(client);
		    }
		    else {
		    	conns[k] = new ServerThread(client, k).start();
		    }
		}
		
		public void run() {
			try {
				System.out.println("Waiting for clients...");
				for (;;) {
				    Socket client = server_socket.accept();
				    spawnServerThread(client);
				}
			}
			catch (IOException e) {
				System.out.println("Mock APNS server shut down.");
			}
		}
		
		void stop() {
			closeQuietly(server_socket);
			server_socket = null;
		}
	}
	
	private final static byte CMD_SEND = 2, CMD_ERROR = 8, ID_PAYLOAD = 2,
			ID_IDENTIFIER = 3, ID_EXPIRATION = 4, ID_PRIORITY = 5, ID_TOKEN = 1;
	
	public final static byte OTHER_ERROR = 1, NO_TOKEN = 2, BAD_TOKEN_SIZE = 5,
			NO_PAYLOAD = 4, BAD_PAYLOAD_SIZE = 7, BAD_TOKEN = 8, SHUTDOWN = 10;
	
	public static final byte APNS_MSK = 15, OUT_OF_FRAME = 17, BAD_ITEM_ID = 33,
			BAD_ITEM_SIZE = 49;
	
	public static final byte DELIVER_NOW = 10, SAVE_POWER = 5;
	
	/**
	 * Returns the APNS error code expected in the socket response, given the
	 * error code reported in invocations of {@code didRejectPacket}. Debug
	 * codes sent to the {@code ApnsServerEventListener} are finer-grained than
	 * in the APNS protocol. This method generalizes debug codes to APNS codes.
	 * @param error an error code received via {@code didRejectPacket}
	 * @return the more coarse-grained APNS error code
	 */
	public static byte apnsCodeForError(byte error) {
		return (byte) (error & APNS_MSK);
	}
	
	private final class ServerThread implements Runnable {

		private final Socket client;
		private BufferedInputStream is = null;
		private ApnsPacket packet = null, accepted = null;
		private final int conn_idx;
		private Thread thread = null;
		

		ServerThread(Socket client, int conn_idx) {
			this.client = client;
			this.conn_idx = conn_idx;
		}
		
		private ServerThread start() {
			if (thread == null) {
				thread = new Thread(this);
				thread.start();
			}
			return this;
		}
		
		private void sendErrorPacket(byte code) {
			code = apnsCodeForError(code);
			ByteBuffer response = ByteBuffer.allocate(6).put(CMD_ERROR)
					.put(code);
			if (code == BAD_TOKEN) {
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
					return OUT_OF_FRAME;
				}
				short item_len = readShort();
				frame_len -= 3 + item_len;
				if (frame_len < 0) {
					return OUT_OF_FRAME;
				}
				switch(item_id) {
				case ID_TOKEN:
					if (item_len != 32) {
						return BAD_TOKEN_SIZE;
					}
					packet.token = readBytes(32);
					break;
				case ID_PAYLOAD:
					if ((item_len > MAX_PAYLOAD_LEN) || (item_len < 0)) {
						return BAD_PAYLOAD_SIZE;
					}
					packet.payload = new String(readBytes(item_len), "UTF-8");
					break;
				case ID_IDENTIFIER:
					if (item_len != 4) {
						return BAD_ITEM_SIZE;
					}
					packet.notification_id = readInt();
					break;
				case ID_EXPIRATION:
					if (item_len != 4) {
						return BAD_ITEM_SIZE;
					}
					packet.expires = readInt();
					break;
				case ID_PRIORITY:
					int priority = is.read();
					if (item_len != 1) {
						return BAD_ITEM_SIZE;
					}
					if (priority == -1) {
						return OUT_OF_FRAME;
					}
					packet.priority = (byte) priority;
					break;
				default:
					return BAD_ITEM_ID;
				}
			}
			return 0;
		}
		
		public void run() {
			thread = Thread.currentThread();
			System.out.println("Connected to client.");
			byte status = 0;
			try {
				is = new BufferedInputStream(client.getInputStream());
				for (;;) {
					packet = new ApnsPacket();
					int header = is.read();
					if (header == -1) {
						break;
					}
					else if (header != CMD_SEND) {
						status = OUT_OF_FRAME;
						break;
					}
					status = readPacket();
					if (packet.token == null) {
						status = NO_TOKEN;
						break;
					}
					if (packet.payload == null) {
						status = NO_PAYLOAD;
						break;
					}
					if (Arrays.equals(bad_token, packet.token)) {
						status = BAD_TOKEN;
						break;
					}
					accepted = packet;
					if (event_listener != null) {
						event_listener.didAcceptPacket(accepted);
					}
				}
			}
			catch (IOException e) {
				status = OTHER_ERROR;
			}
			if (Thread.interrupted()) {
				System.out.println("Connection interrupted.");
				status = SHUTDOWN;
			}
			if (status != 0) {
				sendErrorPacket(status);
				if (event_listener != null) {
					event_listener.didRejectPacket(packet, status);
				}
			}
			closeQuietly(client);
			synchronized (conns) {
				conns[conn_idx] = null;
				conns.notify();
			}
			System.out.println("Connection closed.");
		}
		
		private void disconnect() {
			try {
				thread.interrupt();
				client.shutdownInput();
			}
			catch (IOException e) { }
			boolean was_interrupted = false;
			while (conns[conn_idx] != null) {
				try {
					conns.wait();
				}
				catch (InterruptedException e) {
					was_interrupted = true;
				}
			}
			if (was_interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private PortListener port_listener = null;
	private ApnsServerEventListener event_listener = null;
	private byte[] bad_token = null;
	public static final int MAX_CONN = 16;
	private final ServerThread[] conns = new ServerThread[MAX_CONN];
	
	/**
	 * Starts the mock server to accept client connections. Invocations must
	 * specify the port number at which to listen for clients. Repeated calls
	 * cause the mock server to restart.
	 * @param port the port at which to wait for clients to connect
	 * @throws IOException if a port listener could not be established
	 */
	public void start(int port) throws IOException {
		if (port_listener != null) {
			stop();
		}
		port_listener = new PortListener(port);
	}
	
	/**
	 * Closes all current connections with error code 10 but leaves the mock
	 * server running to accept subsequent connection requests.
	 */
	public void disconnectAll() {
		for (int k = 0; k < MAX_CONN; k ++) {
			synchronized (conns) {
				if (conns[k] != null) {
					conns[k].disconnect();
				}
			}
		}
	}
	
	/**
	 * Closes all client connections with error code 10 and shuts down the mock
	 * server.
	 */
	public void stop() {
		disconnectAll();
		port_listener.stop();
		port_listener = null;
	}
	
	/**
	 * Defines a device token that when received from a client, will trigger an
	 * "invalid token" response with status code 8.
	 * @param bad_token the device token to be recognized as invalid
	 * @return this mock server object for fluent configuration
	 */
	public MockApnsServer defineBadToken(byte[] bad_token) {
		this.bad_token = bad_token;
		return this;
	}

	/**
	 * Registers the event listener to observe accepted packets. Only the last
	 * listener to be registered will receive callbacks.
	 * @param listener the listener to observe accepted packets, or null to
	 * unregister
	 * @return this mock server object for fluent configuration
	 */
	public MockApnsServer setEventListener(ApnsServerEventListener listener) {
		this.event_listener = listener;
		return this;
	}
	
	private static void closeQuietly(Closeable is) {
		if (is != null) {
			try {
				is.close();
			}
			catch (IOException e) { }
		}
	}
}
