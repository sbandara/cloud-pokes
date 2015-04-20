package com.sbandara.cloudpokes;

import java.io.IOException;
import java.io.InputStream;

import javax.net.ssl.SSLSocket;

public class FeedbackClient extends ApnsGateway {
	
	public interface Listener {
		public void receiveInactiveToken(byte[] token);
	}
	
	public FeedbackClient(ApnsConfig config) {
		super(config, Service.FEEDBACK);
	}

	@SuppressWarnings("resource")
	public void fetchInactiveTokens(Listener listener) throws IOException {
		InputStream in = null;
		SSLSocket feedback_socket = null;
		try {
			feedback_socket = secureConnect();
			if (feedback_socket == null) {
				System.out.println("Failed to contact feedback service.");
				return;
			}
			in = feedback_socket.getInputStream();
			byte[] header = new byte[6];
			int n = 0;
			for (;;) {
				n = in.read(header);
				if (n < 0) {
					break;
				}
				else if ((n != header.length) || (header[5] != 32)) {
					throw new IOException("Bad response from APNS.");
				}
				byte[] token = new byte[32];
				n = in.read(token);
				if (n != token.length) {
					throw new IOException("Bad response from APNS.");
				}
				listener.receiveInactiveToken(token);
			}
		}
		finally {
			if (in == null) {
				closeQuietly(feedback_socket);
			}
			else {
				closeQuietly(in);
			}
		}
	}
}
