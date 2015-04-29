package com.sbandara.cloudpokes;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.*;

import javax.net.ssl.*;

public abstract class ApnsGateway extends ServiceConnector {

	private static final SecureRandom sec_rnd = new SecureRandom();
	public static enum Env { PRODUCTION, SANDBOX }	
	public static enum Service { DISPATCH, FEEDBACK }
	
	public interface ApnsConfig {

		public String getHostname(Service service);
		
		public int getPort(Service service);
		
		public InputStream getCertFile() throws IOException;
		
		public String getCertPhrase();
	}

	public static abstract class ApnsDefaultConfig implements ApnsConfig {
				
		public final Env environment;
		
		public ApnsDefaultConfig(Env environment) {
			this.environment = environment;
		}

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
				context.init(getKeyManagers(config.getCertFile(), config
						.getCertPhrase()), null, sec_rnd);
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
	
	protected Socket socketConnect() throws IOException {
		if (config.getCertPhrase() == null) {
			return new Socket(config.getHostname(service), config.getPort(
					service));
		}
		else {
			return secureConnect();
		}
	}
}
