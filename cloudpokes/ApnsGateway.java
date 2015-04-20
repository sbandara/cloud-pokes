package com.sbandara.cloudpokes;

import java.io.IOException;
import java.io.InputStream;
import java.security.*;

import javax.net.ssl.*;

public abstract class ApnsGateway extends ServiceConnector {

	private static final SecureRandom sec_rnd = new SecureRandom();
	public static enum Env { PRODUCTION, SANDBOX }	
	public static enum Service { DISPATCH, FEEDBACK }

	public static abstract class ApnsConfig {
				
		public final Env environment;
		
		public ApnsConfig(Env environment) { this.environment = environment; }

		public final String getHostname(Service service) {
			if (environment == Env.PRODUCTION) {
				if (service == Service.DISPATCH) {
					return "gateway.push.apple.com";
				}
				else {
					assert (service == Service.FEEDBACK);
					return "feedback.push.apple.com";
				}
			}
			else {
				assert (environment == Env.SANDBOX);
				if (service == Service.DISPATCH) {
					return "gateway.sandbox.push.apple.com";
				}
				else {
					assert (service == Service.FEEDBACK);
					return "feedback.sandbox.push.apple.com";
				}
			}
		}
		
		public final int getPort(Service service) {
			if (service == Service.DISPATCH) {
				return 2195;
			}
			else {
				assert (service == Service.FEEDBACK);
				return 2196;
			}
		};
		
		public abstract InputStream getCertFile() throws IOException;
		
		public abstract String getCertPhrase();
	}
		
	private KeyManager[] key_managers = null;
	private SSLSocketFactory socket_factory = null;
	private final ApnsConfig config;
	private final Service service;
	
	protected ApnsGateway(ApnsConfig config, Service service) {
		this.config = config;
		this.service = service;
	}
	
	protected KeyManager[] getKeyManagers() throws IOException {
		if (key_managers == null) {
			KeyStore ks;
			try {
				ks = KeyStore.getInstance("PKCS12");
			}
			catch (KeyStoreException e) {
				throw new RuntimeException("Unable to create key store.");
			}
	    	char certphrase[] = config.getCertPhrase().toCharArray();
	    	InputStream cert = config.getCertFile();
	    	try {
	    		ks.load(cert, certphrase);
	    	}
	    	catch (GeneralSecurityException e) {
				throw new RuntimeException("Bad certificate or unknown type.");
	    	}
	    	finally {
	    		closeQuietly(cert);
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
	
	protected SSLSocket secureConnect() {
		if (socket_factory == null) {
			try {
				SSLContext context = SSLContext.getInstance("TLS");
				context.init(getKeyManagers(), null, sec_rnd);
				socket_factory = context.getSocketFactory();
			}
			catch (GeneralSecurityException security_exception) {
				System.out.println("Failed to create secure socket factory.");
				return null;
			}
			catch (IOException io_exception) {
				System.out.println("Failed to read APNS certificate.");
				return null;
			}
		}
		SSLSocket ssl_socket = null;
		try {
			ssl_socket = (SSLSocket) socket_factory.createSocket(config
					.getHostname(service), config.getPort(service));
		}
		catch (IOException e) {
			System.out.println("Failed to open connection.");
			return null;
		}
		ssl_socket.setUseClientMode(true);
		return ssl_socket;
	}
}
