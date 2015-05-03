package com.sbandara.cloudpokes.mockapns;

import java.util.Random;

public class ApnsToken {
	
	private final static int TOKEN_LEN = 32;
	private final static Random rand = new Random();
	
	final byte[] token;
	
	ApnsToken(byte[] token) {
		if (token.length != TOKEN_LEN) {
			throw new IllegalArgumentException("APNS tokens must be 32 bytes.");
		}
		this.token = token;
	}
	
	public byte[] getBytes() { return token; }
	
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof ApnsToken) {
			ApnsToken other = (ApnsToken) obj;
			return equals(other.token);
		}
		if (obj instanceof byte[]) {
			byte[] other = (byte[]) obj;
			for (int k = 0; k < TOKEN_LEN; k ++) {
				if (other[k] != this.token[k]) {
					return false;
				}
			}
			return true;
		}
		return false;
	}
	
	public static ApnsToken randomToken() {
		byte[] rnd_token = new byte[TOKEN_LEN];
		rand.nextBytes(rnd_token);
		return new ApnsToken(rnd_token);
	}
	
	public static ApnsToken[] uniqueRandom(int n) {
		ApnsToken[] tokens = new ApnsToken[n];
		for (int k = 0; k < n; k ++) {
			boolean is_duplicate = false;
			do {
				tokens[k] = ApnsToken.randomToken();
				for (int j = 0; j < k; j ++) {
					if (tokens[j].equals(tokens[k])) {
						is_duplicate = true;
						break;
					}
				}
			}
			while (is_duplicate);
		}
		return tokens;
	}
}
