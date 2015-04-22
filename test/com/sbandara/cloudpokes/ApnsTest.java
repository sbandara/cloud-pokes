package com.sbandara.cloudpokes;

import static org.junit.Assert.*;
import org.junit.Test;

public class ApnsTest {
	
	@Test
	public void testByteArrayToIntConversion() {
		for (int k = 0; k < 30; k ++) {
			int value = 2 << k;
			System.out.println(value);
			assertEquals(value, ApnsPushSender.bytesToInteger(
					ApnsNotification.integerToBytes(value), 0));
		}
	}
}
