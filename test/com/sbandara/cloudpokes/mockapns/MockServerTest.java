package com.sbandara.cloudpokes.mockapns;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;

import org.junit.*;

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
	
	private ApnsToken[] createTokens(int n) {
		ApnsToken[] tokens = new ApnsToken[n];
		for (int k = 0; k < n; k ++) {
			boolean is_duplicate = false;
			do {
				tokens[k] = ApnsToken.randomToken();
				for (int j = 0; j < k; j ++) {
					if (tokens[j].equals(tokens[k])) {
						is_duplicate = true;
					}
				}
			}
			while (is_duplicate == true);
		}
		return tokens;
	}
	
	@After
	public void close() throws IOException {
		socket.close();
	}
	
	private static byte[] buildValid(byte[] token, int identifier) {
		PacketBuilder builder = new PacketBuilder(256);
		try {
			builder.putArrayItem((byte) 1, token).putArrayItem((byte) 2,
					"{\"aps\":{\"alert\":\"Hello world!\"}}".getBytes("UTF-8"))
					.putIntItem((byte) 3, identifier).putIntItem((byte) 4, 0)
					.putByteItem((byte) 5, (byte) 10);
		}
		catch (UnsupportedEncodingException e) {
			throw new RuntimeException("UTF-8 encoding not supprted.", e);
		}
		return  builder.build();
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
		final int N_MSG = 3;
		ApnsToken token[] = createTokens(N_MSG);
		for (int k = 0; k < N_MSG; k ++) {
			byte[] packet = buildValid(token[k].getBytes(), 0);
			synchronized (packets) {
				try {
					socket.getOutputStream().write(packet);
					packets.wait();
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		}
		assertEquals(packets.size(), N_MSG);
	}
	
	@Test(timeout=1000)
	public void testWithInvalidToken() throws IOException {
		ApnsToken token[] = createTokens(1);
		mock.defineBadToken(token[0].getBytes());
		byte[] bad_packet = buildValid(token[0].getBytes(), MSG_ID);
		socket.getOutputStream().write(bad_packet);
		assertArrayEquals(new byte[] {8, 8, 0, 0, 0, MSG_ID}, readResponse());
		assertEquals(packets.size(), 0);
	}

	@Test(timeout=1000)
	public void testShutdown() throws IOException {
		ApnsToken token[] = createTokens(1);
		byte[] packet = buildValid(token[0].getBytes(), MSG_ID);
		synchronized (packets) {
			socket.getOutputStream().write(packet);
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
