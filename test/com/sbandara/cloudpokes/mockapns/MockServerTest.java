package com.sbandara.cloudpokes.mockapns;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;

import org.junit.*;

import com.sbandara.cloudpokes.util.PacketBuilder;

public class MockServerTest {
	
	private final static int MOCK_APNS_PORT = 2195, MSG_ID = 42;

	private static MockApnsServer mock = null;
	private Socket socket = null;
	private final ArrayList<ApnsPacket> packets = new ArrayList<ApnsPacket>();
	
	@BeforeClass
	public static void setUp() throws IOException {
		mock = new MockApnsServer();
		mock.start(MOCK_APNS_PORT);
	}
	
	@Before
	public void init() throws IOException {
		packets.clear();
		mock.setEventListener(new ApnsServerEventListener() {
			@Override
			public void didAcceptPacket(ApnsPacket packet) {
				synchronized (packets) {
					packets.add(packet);
					packets.notify();
				}
			}
			@Override
			public void didRejectPacket(ApnsPacket packet, byte error) {
			}
		}).defineBadToken(null);
		socket = new Socket(InetAddress.getLocalHost(), MOCK_APNS_PORT);
	}
	
	@After
	public void close() throws IOException {
		socket.close();
	}
	
	private static byte[] buildValid(ApnsToken token, int identifier) {
		PacketBuilder builder = new PacketBuilder(256);
		builder.putArrayItem((byte) 1, token.getBytes()).putStringItem((byte) 2,
				"{\"aps\":{\"alert\":\"Hello world!\"}}")
				.putIntItem((byte) 3, identifier).putIntItem((byte) 4, 0)
				.putByteItem((byte) 5, (byte) 10);
		return builder.build();
	}
	
	private byte[] readResponse() throws IOException {
		InputStream is = socket.getInputStream();
		byte[] response = new byte[6];
		int off = 0, n;
		while (off < response.length) {
			n = is.read(response, off, response.length - off);
			if (n == -1) {
				fail();
			}
			off += n;
		}
		return response;
	}
	
	@Test(timeout=1000)
	public void testWithValidPackets()
			throws IOException, InterruptedException {
		ApnsToken token[] = ApnsToken.uniqueRandom(3);
		for (int k = 0; k < token.length; k ++) {
			synchronized (packets) {
				socket.getOutputStream().write(buildValid(token[k], 0));
				packets.wait();
			}
		}
		assertEquals(packets.size(), token.length);
	}
	
	@Test(timeout=1000)
	public void testBadToken() throws IOException {
		ApnsToken bad_token = ApnsToken.randomToken();
		mock.defineBadToken(bad_token.getBytes());
		socket.getOutputStream().write(buildValid(bad_token, MSG_ID));
		assertArrayEquals(new byte[] {8, 8, 0, 0, 0, MSG_ID}, readResponse());
		assertEquals(packets.size(), 0);
	}

	@Test(timeout=1000)
	public void testLastAcceptedId() throws IOException {
		OutputStream os = socket.getOutputStream(); 
		os.write(buildValid(ApnsToken.randomToken(), MSG_ID));
		os.write(new PacketBuilder(256).putArrayItem((byte) 1, ApnsToken
				.randomToken().getBytes()).putIntItem((byte) 3, MSG_ID + 1)
				.putIntItem((byte) 4, 0).putByteItem((byte) 5, (byte) 5)
				.build());
		assertArrayEquals(new byte[] {8, MockApnsServer.NO_PAYLOAD, 0, 0, 0,
				MSG_ID}, readResponse());
	}
	
	@Test(timeout=1000)
	public void testShutdown() throws IOException {
		synchronized (packets) {
			socket.getOutputStream().write(buildValid(ApnsToken.randomToken(),
					MSG_ID));
			try {
				packets.wait();
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		mock.disconnectAll();
		assertArrayEquals(new byte[] {8, 10, 0, 0, 0, MSG_ID}, readResponse());
	}

	@AfterClass
	public static void tearDown() {
		mock.stop();
	}
}
