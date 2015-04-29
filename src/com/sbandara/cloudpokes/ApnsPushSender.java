package com.sbandara.cloudpokes;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;

public final class ApnsPushSender extends ApnsGateway {
	
	private final ReentrantLock socket_lock = new ReentrantLock();
	private Socket socket = null;
	private ErrorReceiver observer = new ErrorReceiver();
	
	public static boolean is_debug = false;
	private static ApnsPushSender the_instance = null;
	
	public static void configure(ApnsConfig config) {
		the_instance = new ApnsPushSender(config);
	}
	
	public static ApnsPushSender getInstance() {
		if (the_instance == null) {
			throw new IllegalStateException("ApnsPushSender not configured.");
		}
		return the_instance;
	}
			
	private ApnsPushSender(ApnsConfig config) {
		super(config, Service.DISPATCH);
	}

	static int bytesToInteger(byte[] buf, int off) {
		return ByteBuffer.wrap(buf, off, 4).getInt();
	}

	static private class ErrorReceiver extends Thread {
		
		final static int OK = 0, HANGUP = 1024;
		
		private InputStream input_stream = null;
		private int error_code = OK, last_sent_id = 0;
		
		int getLastSentId() { return last_sent_id; }
		
		int getErrorCode() { return error_code; }
		
		void setInputStream(InputStream input_stream) {
			this.input_stream = input_stream;
		}
		
		private final static int ERROR_BUF_SIZE = 6, ERROR_HEADER = 8;
				
		@Override
		public void run() {
			if (input_stream == null) {
				throw new IllegalStateException("No input stream assigned.");
			}
			try {
				byte[] pack = new byte[ERROR_BUF_SIZE];
				int n_byte, off = 0;
				while (off < pack.length) {
					n_byte = input_stream.read(pack, off, pack.length - off);
					if (n_byte == -1) {
						throw new IOException("Connection dropped.");
					}
					off += n_byte;
				}
				if (pack[0] != ERROR_HEADER) {
					System.out.println("Unexpected response from APNS.");
					return;
				}
				error_code = pack[1];
				last_sent_id = bytesToInteger(pack, 2);
			}
			catch (IOException e) {
				System.out.println(e.getMessage());
				if (error_code == 0) {
					error_code = HANGUP;
				}
				return;
			}
		}
	}
		
	public int getLastSentId() { return observer.getLastSentId(); }
	
	public int getErrorCode() { return observer.getErrorCode(); }
	
	private void closeSocket() {
		closeQuietly(socket);
		socket = null;		
	}
	
	void sendNotification(Notification notification) {
		if (is_debug) {
			try {
				notification.writeToOutputStream(System.out);
				return;
			}
			catch (IOException e) { }
		}
		socket_lock.lock();
		try {
			int last_error = observer.getErrorCode();
			if (last_error != 0) {
				closeSocket();
			}
			try {
				if (socket == null) {
					socket = socketConnect();
					socket.setSoTimeout(0);
					observer = new ErrorReceiver();
					observer.setInputStream(socket.getInputStream());
					observer.start();
				}
				notification.writeToOutputStream(socket.getOutputStream());
			}
			catch (IOException e) {
				closeSocket();
				System.out.println("Failed to dispatch notification.");
			}
		}
		finally {
			socket_lock.unlock();
		}
	}
}
