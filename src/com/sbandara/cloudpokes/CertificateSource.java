package com.sbandara.cloudpokes;

import java.io.IOException;
import java.io.InputStream;

public interface CertificateSource {

	public InputStream getCertFile() throws IOException;
	
	public String getCertPhrase();
}
