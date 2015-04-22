package com.sbandara.cloudpokes;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.*;

public final class ApnsPushSender extends ApnsGateway {
	
	private final ReentrantLock socket_lock = new ReentrantLock();
	private SSLSocket socket = null;
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
	
	static private class ErrorReceiver extends Thread {
		
		final static int OK = 0, HANGUP = 1024;
		
		private InputStream input_stream = null;
		private int error_code = OK, last_sent_id = 0;
		
		int getLastSentId() { return last_sent_id; }
		
		int getErrorCode() { return error_code; }
		
		void setInputStream(InputStream input_stream) {
			this.input_stream = input_stream;
		}
		
		@Override
		public void run() {
			if (input_stream == null) {
				throw new IllegalStateException("No input stream assigned.");
			}
			try {
				byte[] packet = new byte[6];
				int n_byte = input_stream.read(packet);
				if (n_byte < 6) {
					throw new IOException("Connection dropped.");
				}
				if (packet[0] != 8) {
					System.out.println("Unexpected response from APNS.");
					return;
				}
				error_code = packet[1];
				last_sent_id = 0;
				long mod = 1;
				for (int k = 5; k > 1; k --) {
					last_sent_id += packet[k] * mod;
					mod *= 256;
				}
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
			if (socket == null) {
				socket = secureConnect();
				if (socket == null) {
					System.out.println("Failed to dispatch notification.");
					return;
				}
				try {
					socket.setSoTimeout(0);
				}
				catch (SocketException e) {
					System.out.println("Failed to configure socket timeout.");
					closeSocket();
					return;
				}
				observer = new ErrorReceiver();
				try {
					observer.setInputStream(socket.getInputStream());
				}
				catch (IOException e) {
					System.out.println("Unable to get input stream.");
					closeSocket();
					System.out.println("Failed to dispatch notification.");
					return;
				}
				observer.start();
			}
			try {
				notification.writeToOutputStream(socket.getOutputStream());
			}
			catch (IOException e) {
				closeSocket();
			}
		}
		finally {
			socket_lock.unlock();
		}
	}
}
