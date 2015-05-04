package com.sbandara.cloudpokes;

public class ApnsDefaultConfig implements ApnsConfig {
			
	public final Env environment;
	private final CertificateSource cert_source;
	
	public ApnsDefaultConfig(Env environment, CertificateSource cert_source) {
		this.environment = environment;
		this.cert_source = cert_source;
	}

	@Override
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
	
	@Override
	public final int getPort(Service service) {
		if (service == Service.DISPATCH) {
			return 2195;
		}
		else {
			assert (service == Service.FEEDBACK);
			return 2196;
		}
	}

	@Override
	public CertificateSource getCertSource() { return cert_source; }
}
