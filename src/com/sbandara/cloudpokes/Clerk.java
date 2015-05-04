package com.sbandara.cloudpokes;

public final class Clerk {
	
	private ApnsPushSender apns_sender = null;
	private GcmPushSender gcm_sender = null;
	
	public Notification draftTo(DeviceToken token) {
		if ((token.isApnsToken()) && (apns_sender != null)) { 
			return new ApnsNotification(token, apns_sender);
		}
		else if ((token.isGcmToken()) && (gcm_sender != null)) {
			return new GcmNotification(token, gcm_sender);
		}
		throw new IllegalStateException("Route not configured for " + token);
	}

	public void configureApns(ApnsConfig config) {
		if (apns_sender == null) {
			apns_sender = new ApnsPushSender(config);
		}
		else {
			throw new IllegalStateException("GCM route already configured.");
		}
	}
	
	public void configureGcm(GcmDelegate delegate) {
		if (gcm_sender == null) {
			gcm_sender = new GcmPushSender(delegate);
		}
		else {
			throw new IllegalStateException("GCM route already configured.");
		}
	}
}
