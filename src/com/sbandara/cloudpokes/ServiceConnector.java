package com.sbandara.cloudpokes;

import java.io.Closeable;
import java.io.IOException;

class ServiceConnector {

	static void closeQuietly(Closeable is) {
		if (is != null) {
			try {
				is.close();
			}
			catch (IOException e) { }
		}
	}
}
