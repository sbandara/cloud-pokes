package com.sbandara.cloudpokes;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

import com.sbandara.cloudpokes.MockApnsServer.*;
import com.sbandara.cloudpokes.ApnsGateway.*;

public class ApnsTest {
	
	private final static int MOCK_APNS_PORT = 2195;
	
	@Test
	public void testByteArrayToIntConversion() {
		for (int k = 0; k < 30; k ++) {
			int value = 2 << k;
			assertEquals(value, ApnsPushSender.bytesToInteger(
					ApnsNotification.integerToBytes(value), 0));
		}
	}
	
	private class MockServerListener implements ApnsServerEventListener {
		
		@Override
		public synchronized void didAcceptPacket(ApnsPacket packet) {
			System.out.println(packet.getPayload().toString());
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
			fail();
		}
		ApnsPushSender.configure(new ApnsConfig() {
			@Override
			public String getCertPhrase() { return null; }
			@Override
			public InputStream getCertFile() throws IOException { return null; }
			@Override
			public String getHostname(Service service) { return "localhost"; }
			@Override
			public int getPort(Service service) { return MOCK_APNS_PORT; }
		});
		Notification.withToken(new DeviceToken(new byte[32])).setMessage(
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
