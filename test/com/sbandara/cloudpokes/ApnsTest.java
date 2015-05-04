package com.sbandara.cloudpokes;

import static org.junit.Assert.*;
import org.junit.Test;

import java.io.IOException;

import com.sbandara.cloudpokes.mockapns.*;

public class ApnsTest {
	
	private final static int MOCK_APNS_PORT = 2195;
	
	private class MockServerListener implements ApnsServerEventListener {
		@Override
		public synchronized void didAcceptPacket(ApnsPacket packet) {
			System.out.println(packet.getPayload().toString());
		}
		@Override
		public void didRejectPacket(ApnsPacket packet, byte error) {
		}
	}
	
	private final MockServerListener mock_logger = new MockServerListener();
	
	@Test
	public void testMockServer() {
		MockApnsServer mock = new MockApnsServer();
		try {
			mock.setEventListener(mock_logger).start(MOCK_APNS_PORT);
		}
		catch (IOException e) {
			fail(e.getMessage());
		}
		Clerk clerk = new Clerk();
		clerk.configureApns(new ApnsConfig() {
			@Override
			public CertificateSource getCertSource() { return null; }
			@Override
			public String getHostname(Service service) { return "localhost"; }
			@Override
			public int getPort(Service service) { return MOCK_APNS_PORT; }
		});
		clerk.draftTo(new DeviceToken(new byte[32])).setMessage(
				"Hello World!").send();
		try {
			Thread.sleep(1000);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		mock.stop();
	}
}
