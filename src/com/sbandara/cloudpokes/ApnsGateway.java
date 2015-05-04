package com.sbandara.cloudpokes;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.*;

import javax.net.ssl.*;

import com.sbandara.cloudpokes.ApnsConfig.Service;

abstract class ApnsGateway extends ServiceConnector {

	private static final SecureRandom sec_rnd = new SecureRandom();
	
	private KeyManager[] key_managers = null;
	private SSLSocketFactory socket_factory = null;
	private final ApnsConfig config;
	private final Service service;
	
	ApnsGateway(ApnsConfig config, Service service) {
		this.config = config;
		this.service = service;
	}
	
	private KeyManager[] getKeyManagers(InputStream certificate,
			String passphrase) throws IOException {
		if (key_managers == null) {
			KeyStore ks;
			try {
				ks = KeyStore.getInstance("PKCS12");
			}
			catch (KeyStoreException e) {
				throw new RuntimeException("Unable to create key store.");
			}
	    	char certphrase[] = passphrase.toCharArray();
	    	try {
	    		ks.load(certificate, certphrase);
	    	}
	    	catch (GeneralSecurityException e) {
				throw new RuntimeException("Bad certificate or unknown type.");
	    	}
	    	finally {
	    		closeQuietly(certificate);
	    	}
	    	KeyManagerFactory kmf;
	    	try {
	    		kmf = KeyManagerFactory.getInstance(KeyManagerFactory
	    				.getDefaultAlgorithm());
	    		kmf.init(ks, certphrase);
	    	}
	    	catch (GeneralSecurityException e) {
	    		throw new RuntimeException(e.getMessage());
	    	}
	    	key_managers = kmf.getKeyManagers();
		}
    	return key_managers;
	}
	
	private Socket secureConnect() throws IOException {
		if (socket_factory == null) {
			try {
				SSLContext context = SSLContext.getInstance("TLS");
				CertificateSource src = config.getCertSource();
				context.init(getKeyManagers(src.getCertFile(),
						src.getCertPhrase()), null, sec_rnd);
				socket_factory = context.getSocketFactory();
			}
			catch (GeneralSecurityException security_exception) {
				throw new IOException("Failed to create SSL socket factory.");
			}
			catch (IOException io_exception) {
				throw new IOException("Failed to read APNS certificate.");
			}
		}
		SSLSocket ssl_socket = null;
		ssl_socket = (SSLSocket) socket_factory.createSocket(config
				.getHostname(service), config.getPort(service));
		ssl_socket.setUseClientMode(true);
		return ssl_socket;
	}
	
	Socket socketConnect() throws IOException {
		if (config.getCertSource() == null) {
			return new Socket(config.getHostname(service), config.getPort(
					service));
		}
		else {
			return secureConnect();
		}
	}
}
