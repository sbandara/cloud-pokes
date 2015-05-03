package com.sbandara.cloudpokes;

import java.util.ArrayList;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class AsyncQueueTest {
	
	private final static int SIZE = 12, HISTORY_MS = 200, COUNT = 64;
	private AsyncRedoBlockingQueue queue;
	private ArrayList<Integer> dest = new ArrayList<Integer>();
	
	private class Action implements Runnable {
		public Action(int id) {
			this.id = id;
		}
		public void run() {
			dest.add(id);
		}
		protected final int id;
	}
	
	@Before
	public void init() {
		dest.clear();
		queue = new AsyncRedoBlockingQueue(SIZE, HISTORY_MS);
	}
	
	private static void sleepDeeply(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@Test(timeout=10000)
	public void testBlockingQueue() {
		final int OFF = 7, DELAY = 200;
		for (int id = 0; id < COUNT; id ++) {
			Action action = new Action(id);
			if (id % (SIZE + OFF) == 0) {
				sleepDeeply(DELAY);
			}
			queue.enqueue(action, id);
		}
		queue.purgeQueue();
		for (int k = 0; k < COUNT; k ++) {
			int id = dest.get(k);
			Assert.assertEquals(id, k);
		}
	}
	
	private volatile int rewind = -1, fx = 0;
	
	@Test(timeout=10000)
	public void testRewind() {
		final int[] FAIL_ID = new int[]{ 5, 21, 23, 46, 61 };
		int n_error = 0, n_rewind = 0, n_missed = 0;
		rewind = -1;
		for (int id = 0; id < COUNT; id ++) {
			queue.enqueue(new Action(id) {
				public void run() {
					if ((fx < FAIL_ID.length) && (this.id == FAIL_ID[fx])) {
						rewind = this.id - 1;
						fx ++;
					}
					super.run();
				}
			}, id);
			if (rewind > -1) {
				try {
					n_error ++;
					queue.rewind(rewind);
				}
				catch (EntryNotFoundException e) {
					n_missed ++;
				}
				rewind = -1;
			}
		}
		queue.purgeQueue();
		int prev_id = -1, id;
		for (int k = 0; k < dest.size(); k ++) {
			id = dest.get(k);
			if (id <= prev_id) {
				boolean is_fail_id = false;
				for (int n = 0; n < FAIL_ID.length; n ++) {
					if (FAIL_ID[n] == id) {
						is_fail_id = true;
						break;
					}
				}
				Assert.assertTrue(is_fail_id);
				n_rewind ++;
			}
			prev_id = id;
		}
		Assert.assertEquals(n_error, n_rewind + n_missed);
		Assert.assertTrue(n_rewind > 0);
		int last_entry = dest.get(dest.size() - 1);
		Assert.assertEquals(COUNT - 1, last_entry);
	}
}
