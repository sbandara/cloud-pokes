package com.sbandara.cloudpokes;

import java.util.ArrayList;
import org.junit.Assert;
import org.junit.Test;

public class AsyncQueueTest {
	
	private final static int SIZE = 12, OFF = 3, HISTORY_MS = 100, COUNT = 64,
			DELAY = 50;
	
	private AsyncRedoBlockingQueue queue =
			new AsyncRedoBlockingQueue(SIZE, HISTORY_MS);
	private ArrayList<Integer> dest = new ArrayList<Integer>();

	private class Action implements Runnable {
		public Action(int id) {
			this.id = id;
		}
		public void run() {
			dest.add(id);
			synchronized (dest) {
				dest.notify();
			}
		}
		private final int id;
	}
	
	@Test(timeout=5000)
	public void test() {
		int id;
		dest.clear();
		for (id = 0; id < COUNT; id ++) {
			Action action = new Action(id);
			if (id % (SIZE + OFF) == 0) {
				try {
					Thread.sleep(DELAY);
				}
				catch (InterruptedException e) {
					return;
				}
			}
			queue.enqueue(action, id);
		}
		synchronized (dest) {
			queue.enqueue(new Action(id), id);
			while(dest.size() <= COUNT) {
				try {
					dest.wait();
				}
				catch (InterruptedException e) { }
			}
		}
		for (int k = 0; k < COUNT; k ++) {
			id = dest.get(k);
			Assert.assertEquals(id, k);
		}
	}
}
