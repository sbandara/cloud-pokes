package com.sbandara.cloudpokes;

import java.util.concurrent.atomic.AtomicInteger;

public class AsyncRedoBlockingQueue {
	
	public AsyncRedoBlockingQueue(int size, int history_millis) {
		tape_length = size;
		min_retain_millis = history_millis;
		tape = new Entry[tape_length];
	}
	
	private final static class Entry {
		
		Entry(Runnable action, int id) {
			this.action = action;
			this.id = id;
		}
		
		final Runnable action;
		final int id;
		volatile long run = 0;
		
		void send() {
			try {
				action.run();
			}
			catch (RuntimeException e) {
				System.out.println("Unhandled Exception.");
			}
			run = System.currentTimeMillis();
		}
	}
	
	private final int tape_length, min_retain_millis;
	private final Entry[] tape;
	private int tail = 0, head = 0;
	private final Consumer consumer = new Consumer();
	private Thread worker = null;
	private boolean is_rewinding = false;
	
	private int inc(int k) {
		if (++ k == tape_length) {
			k = 0;
		}
		return k;
	}
	
	private final LogEntry[] log_entry = new LogEntry[1024];
	private AtomicInteger idx_log = new AtomicInteger(-1);

	private void log(String src, int value) {
		int idx = idx_log.incrementAndGet();
		LogEntry entry = new LogEntry(src, value);
		log_entry[idx] = entry;
	}
	
	private static class LogEntry {
		private LogEntry(String src, int value) {
			this.src = src;
			this.value = value;
		}
		final String src;
		final int value;
	}
	
	public void showLog() {
		int n_log = idx_log.get();
		for (int k = 0; k < n_log; k ++) {
			System.out.println(log_entry[k].src + ": " + log_entry[k].value);
		}
	}
	
	synchronized public void enqueue(Runnable action, int id) {
		boolean was_interrupted = false;
		synchronized (tape) {
			head = inc(head);
			while (head == tail) {
				try {
					tape.wait();
				}
				catch (InterruptedException e) {
					was_interrupted = true;
				}
			}
		}
		if ((tape[head] != null) && (tape[head].run != 0)) {
			for (;;) {
				long elapsed = System.currentTimeMillis() - tape[head].run;
				if (elapsed > min_retain_millis) {
					break;
				}
				try {
					Thread.sleep(min_retain_millis - elapsed + 1);
				}
				catch (InterruptedException e) {
					was_interrupted = true;
				}
			}
		}
		synchronized (consumer) {
			tape[head] = new Entry(action, id);
			if ((worker == null) && (! is_rewinding)) {
				worker = new Thread(consumer);
				worker.start();
			}
		}
		if (was_interrupted) {
			Thread.currentThread().interrupt();
		}
	}
	
	private final class Consumer implements Runnable {
		
		public void run() {
			for (;;) {
				synchronized (tape) {
					tail = inc(tail);
					tape.notify();
				}
				tape[tail].send();
				synchronized (consumer) {
					if ((Thread.interrupted()) || (! hasJobs())) {
						worker = null;
						consumer.notify();
						return;
					}
				}
			}
		}

		private boolean hasJobs() {
			int next = inc(tail);
			return (tape[next] != null) && (tape[next].run == 0);
		}
	}
	
	private final int findEntry(int id) {
		for (int k = 0; k < tape_length; k ++) {
			if (tape[k].id == id) {
				return k;
			}
		}
		return -1;
	}
	
	public final void rewind(int id) throws EntryNotFoundException {
		boolean was_interrupted = false;
		synchronized (consumer) {
			if (worker != null) {
				worker.interrupt();
				while (worker != null) {
					try {
						consumer.wait();
					}
					catch (InterruptedException e) {
						was_interrupted = true;
					}
				}
			}
			is_rewinding = true;
		}
		final int idx = findEntry(id);
		tail = idx;
		for (int k = tail; k != head; k ++) {
			if (k == tape_length) {
				k = 0;
			}
			if (tape[k] == null) {
				continue;
			}
			if (tape[k].run == 0) {
				break;
			}
			tape[k].run = 0;
		}
		if (consumer.hasJobs()) {
			worker = new Thread(consumer);
			worker.start();
		}
		is_rewinding = false;
		if (was_interrupted) {
			Thread.currentThread().interrupt();
		}
		if (idx == -1) {
			throw new EntryNotFoundException(id);
		}
	}
}
