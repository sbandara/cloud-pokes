package com.sbandara.cloudpokes;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

import com.sbandara.cloudpokes.ApnsConfig.Service;

public class FeedbackClient extends ApnsGateway {
	
	private static final int TOKEN_LEN = 32;
	
	public interface Listener {
		public void receiveInactiveToken(byte[] token);
	}
	
	public FeedbackClient(ApnsConfig config) {
		super(config, Service.FEEDBACK);
	}
	
	@SuppressWarnings("resource")
	public void fetchInactiveTokens(Listener listener) throws IOException {
		InputStream in = null;
		Socket feedback_socket = null;
		try {
			feedback_socket = socketConnect();
			if (feedback_socket == null) {
				throw new IOException("Failed to contact feedback service.");
			}
			in = feedback_socket.getInputStream();
			byte[] header = new byte[6];
			int n = 0;
			for (;;) {
				n = in.read(header);
				if (n < 0) {
					break;
				}
				else if ((n != header.length) || (header[5] != TOKEN_LEN)) {
					throw new IOException("Bad response from APNS.");
				}
				byte[] token = new byte[TOKEN_LEN];
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
