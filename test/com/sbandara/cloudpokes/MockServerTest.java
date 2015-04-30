package com.sbandara.cloudpokes;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import javax.xml.bind.DatatypeConverter;

import org.junit.*;

import com.sbandara.cloudpokes.MockApnsServer.*;

public class MockServerTest {
	
	private final static int MOCK_APNS_PORT = 2195, TOKEN_LEN = 32;
	private final static byte[] HEAD = DatatypeConverter.parseBase64Binary(
			"AgAAAFABACA="), TAIL = DatatypeConverter.parseBase64Binary
			("AgAYeyJhcHMiOnsiYWxlcnQiOiJ0ZXN0In19AwAEAAAAAAQABAAAAAAFAAEK");

	private static MockApnsServer mock = null;
	private static byte[][] tokens = null;
	private Socket socket = null;
	private final ArrayList<ApnsPacket> packets = new ArrayList<ApnsPacket>();
	
	@BeforeClass
	public static void setUp() throws IOException {
		tokens = new byte[4][];
		for (int n = 0; n < 4; n ++) {
			byte[] token = new byte[TOKEN_LEN];
			for (int k = 0; k < TOKEN_LEN; k ++) {
				token[k] = (byte) (n + k);
			}
			tokens[n] = token;
		}
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
					System.out.format("Packet %d was accepted.\n",
							packet.getNotificationId());
					packets.notify();
				}
			}
		}).setBadToken(null);
		socket = new Socket(InetAddress.getLocalHost(), MOCK_APNS_PORT);
	}
	
	@After
	public void close() throws IOException {
		socket.close();
	}
	
	private static byte[] binaryPacket(byte[] token) {
		Assert.assertEquals(token.length, TOKEN_LEN);
		ByteBuffer buf = ByteBuffer.allocate(HEAD.length + token.length
				+ TAIL.length);
		return buf.put(HEAD).put(token).put(TAIL).array();
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
	public void testWithValidPacket() throws IOException {
		synchronized (packets) {
			try {
				socket.getOutputStream().write(binaryPacket(tokens[0]));
				packets.wait();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		assertEquals(packets.size(), 1);
	}
	
	@Test(timeout=1000)
	public void testWithInvalidToken() throws IOException {
		mock.setBadToken(DeviceToken.apnsToken(tokens[0]));
		byte[] bad_packet = binaryPacket(tokens[0]);
		bad_packet[bad_packet.length - 12] = 42;
		socket.getOutputStream().write(bad_packet);
		assertArrayEquals(new byte[] {8, 8, 0, 0, 0, 42}, readResponse());
		assertEquals(packets.size(), 0);
	}
	
	@AfterClass
	public static void tearDown() {
		mock.stop();
	}
}
