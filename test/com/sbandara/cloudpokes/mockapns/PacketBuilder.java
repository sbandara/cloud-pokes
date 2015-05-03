package com.sbandara.cloudpokes.mockapns;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

/**
 * Utility class for building APNS version 2-style request packets. Packets
 * begin with a command byte and a four-byte integer indicating the total length
 * of allvpacket items to follow. Each item is made up of a byte indicating the
 * type of item, a two-byte integer indicating the length of the item content,
 * andvthe item itself. All data is written big endian.
 */
public class PacketBuilder {
	
	private final ByteBuffer buf;
	private byte[] packet = null;
	
	/**
	 * Constructor for building APNS version 2-style request packets.
	 * @param capacity the maximum possible length of the packet in bytes
	 */
	public PacketBuilder(int capacity) {
		buf = ByteBuffer.allocate(capacity);
		buf.put((byte) 2).mark();
		buf.putInt(0);
	}
	
	private void ensureIsBuilding() {
		if (packet != null) {
			throw new IllegalStateException("Packet was already built.");
		}
	}
	
	/**
	 * Appends an item that consists of an array of bytes to the packet.
	 * @param id an identifier for the type of item
	 * @param item the array-of-bytes content of the item
	 * @return this PacketBuilder instance for fluent use
	 */
	public PacketBuilder putArrayItem(byte id, byte[] item) {
		ensureIsBuilding();
		buf.put(id).putShort((short) item.length).put(item);
		return this;
	}

	/**
	 * Appends a String item to the packet.
	 * @param id an identifier for the type of item
	 * @param item the String content of the item
	 * @return this PacketBuilder instance for fluent use
	 */
	public PacketBuilder putStringItem(byte id, String item) {
		try {
			return putArrayItem(id, item.getBytes("UTF-8"));
		}
		catch (UnsupportedEncodingException e) {
			throw new RuntimeException("UTF-8 encoding not supported.");
		}
	}

	/**
	 * Appends an item that consists of a four-byte integer to the packet.
	 * @param id an identifier for the type of item
	 * @param item the 4-byte integer content of the item
	 * @return this PacketBuilder instance for fluent use
	 */
	public PacketBuilder putIntItem(byte id, int item) {
		ensureIsBuilding();
		buf.put(id).putShort((short) 4).putInt(item);
		return this;
	}

	/**
	 * Appends an item that consists of a single byte to the packet.
	 * @param id an identifier for the type of item
	 * @param item the one-byte content of the item
	 * @return this PacketBuilder instance for fluent use
	 */
	public PacketBuilder putByteItem(byte id, byte item) {
		ensureIsBuilding();
		buf.put(id).putShort((short) 1).put(item);
		return this;
	}
	
	/**
	 * Constructs and returns the packet as an array of bytes. Attempts to
	 * modify the packet through this builder instance after build was called
	 * will raise an {@code IllegalStateException}.
	 * @return the fully constructed packet
	 * @throws IllegalStateException
	 */
	public byte[] build() throws IllegalStateException {
		if (packet == null) {
			final int total_len = buf.position();
			buf.reset();
			buf.putInt(total_len - 5);
			buf.rewind();
			packet = new byte[total_len];
			buf.get(packet, 0, total_len);
		}
		return packet;
	}
}
