package com.sbandara.cloudpokes.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

public class ServiceConnector {

	public static void closeQuietly(Closeable is) {
		if (is != null) {
			try {
				is.close();
			}
			catch (IOException e) { }
		}
	}
	
	public static int readStream(InputStream is, byte[] dest)
			throws IOException {
		int n_byte, off = 0;
		while (off < dest.length) {
			n_byte = is.read(dest, off, dest.length - off);
			if (n_byte == -1) {
				return off;
			}
			off += n_byte;
		}
		return off;
	}
}
