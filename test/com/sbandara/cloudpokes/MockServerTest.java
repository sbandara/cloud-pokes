package com.sbandara.cloudpokes;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;

import org.junit.*;

import com.sbandara.cloudpokes.MockApnsServer.*;

public class MockServerTest {
	
	private final static int MOCK_APNS_PORT = 2195;

	private MockApnsServer mock = null;
	private Socket socket = null;
	private final ArrayList<ApnsPacket> packets = new ArrayList<ApnsPacket>();
	
	@BeforeClass
	public void setUp() {
		mock = new MockApnsServer();
		try {
			mock.setEventListener(new ApnsServerEventListener() {
				@Override
				synchronized public void didAcceptPacket(ApnsPacket packet) {
					packets.add(packet);
				}
			}).start(MOCK_APNS_PORT);
		}
		catch (IOException e) { fail(); }
	}
	
	@Before
	public void init() {
		packets.clear();
		try {
			socket = new Socket(InetAddress.getLocalHost(), MOCK_APNS_PORT);
		}
		catch (IOException e) { fail(); }
	}
	
	@After
	public void close() {
		try {
			socket.close();
		}
		catch (IOException e) { }
	}
	
	@Test
	public void testWithValidPacket() {
	}
	
	@AfterClass
	public void tearDown() {
		mock.stop();
	}
}
