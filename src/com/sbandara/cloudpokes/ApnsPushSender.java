package com.sbandara.cloudpokes;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sbandara.cloudpokes.ApnsConfig.Service;

final class ApnsPushSender extends ApnsGateway {
	
	private final AsyncRedoBlockingQueue queue;
	private Socket socket = null;
	private ErrorReceiver observer = null;
	
	private final static String TAG = "ApnsPushSender";
	private static final Logger logger = LoggerFactory.getLogger(TAG);	
				
	ApnsPushSender(ApnsConfig config) {
		super(config, Service.DISPATCH);
		queue = new AsyncRedoBlockingQueue(128, 2000);
	}

	static int bytesToInteger(byte[] buf, int off) {
		return ByteBuffer.wrap(buf, off, 4).getInt();
	}

	static private class ErrorReceiver extends Thread {
		
		final static int OK = 0, HANGUP = 1024;
		private final static String TAG = "ApnsPushSender.ErrorReceiver";
		private static final Logger logger = LoggerFactory.getLogger(TAG);
		
		private final InputStream input_stream;
		private int error_code = OK, last_sent_id = 0;
		
		int getLastSentId() { return last_sent_id; }
		
		int getErrorCode() { return error_code; }
		
		ErrorReceiver(InputStream input_stream) {
			this.input_stream = input_stream;
		}
		
		private final static int ERROR_BUF_SIZE = 6, ERROR_HEADER = 8;
				
		@Override
		public void run() {
			byte[] pack = new byte[ERROR_BUF_SIZE];
			int n_byte, off = 0;
			try {
				while (off < pack.length) {
					n_byte = input_stream.read(pack, off, pack.length - off);
					if (n_byte == -1) {
						throw new IOException("Incomplete error response.");
					}
					off += n_byte;
				}
			}
			catch (IOException e) {
				logger.error(e.getMessage());
				error_code = HANGUP;
			}
			if (pack[0] != ERROR_HEADER) {
				logger.error("Unexpected response from APNS.");
				return;
			}
			if (off > 1) {
				error_code = pack[1];
				if (off == pack.length) {
					last_sent_id = bytesToInteger(pack, 2);
				}
			}
		}
	}
	
	int getLastSentId() { return observer.getLastSentId(); }
	
	int getErrorCode() { return observer.getErrorCode(); }
	
	private void closeSocket() {
		closeQuietly(socket);
		socket = null;		
	}
	
	void enqueueNotification(final ApnsNotification notification) {
		queue.enqueue(new Runnable() {
			@Override
			public void run() {
				sendNotification(notification);
			}
		}, notification.getId());
	}

	private void sendNotification(ApnsNotification notification) {
		if (observer != null) {
			int last_error = observer.getErrorCode();
			if (last_error != 0) {
				closeSocket();
			}
		}
		try {
			if (socket == null) {
				socket = socketConnect();
				socket.setSoTimeout(0);
				observer = new ErrorReceiver(socket.getInputStream());
				observer.start();
			}
			notification.writeToOutputStream(socket.getOutputStream());
		}
		catch (IOException e) {
			closeSocket();
			// TODO: store id of notification in case error stream does
			// not indicate last accepted notification.
			logger.warn("Failed attempt to dispatch notification.");
		}
	}	
}
