package com.sbandara.cloudpokes;

public interface GcmDelegate {
	
	public String getApiKey();
	
	public void didSend(Notification notification, String reg_id);
	
	public void didFail(Notification notification, String error);
}
