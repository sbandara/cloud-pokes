package com.sbandara.cloudpokes;

public interface ApnsConfig {

	public static enum Env { PRODUCTION, SANDBOX }	
	public static enum Service { DISPATCH, FEEDBACK }
	
	public String getHostname(Service service);
	
	public int getPort(Service service);
	
	public CertificateSource getCertSource();
}
