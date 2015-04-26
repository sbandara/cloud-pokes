package com.sbandara.cloudpokes;

public class AsyncRedoBlockingQueue {
	
	private final static int TAPE_LENGTH = 128, MIN_RETAIN_MILLIS = 1000;
	
	public interface Action {
		public void send();
	}
	
	private final static class Entry {
		
		Entry(Action notification, int id) {
			this.notification = notification;
			this.id = id;
		}
		
		final Action notification;
		final int id;
		long sent = 0;
		
		void send() {
			try {
				notification.send();
			}
			catch (RuntimeException e) {
				System.out.println("Unhandled Exception.");
			}
			sent = System.currentTimeMillis();
		}
	}
	
	private final Entry[] tape = new Entry[TAPE_LENGTH];
	private int tail = 0, head = 0;
	private final Consumer consumer = new Consumer();
	private volatile Thread worker = null;
	private volatile boolean is_rewinding = false;
	
	synchronized public void enqueue(Action notification, int id) {
		boolean was_interrupted = false;
		if (++ head == TAPE_LENGTH) {
			head = 0;
		}
		if (tape[head] != null) {
			synchronized (tape) {
				while (head == tail) {
					try {
						tape.wait();
					}
					catch (InterruptedException e) {
						was_interrupted = true;
					}
				}
			}
			for (;;) {
				long elapsed = System.currentTimeMillis() - tape[head].sent;
				if (elapsed < MIN_RETAIN_MILLIS) {
					break;
				}
				try {
					Thread.sleep(MIN_RETAIN_MILLIS - elapsed + 1);
				}
				catch (InterruptedException e) {
					was_interrupted = true;
				}
			}
		}
		synchronized (consumer) {
			tape[head] = new Entry(notification, id);
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
			if (! hasJobs()) {
				return;
			}
			for (;;) {						
				tape[tail].send();
				synchronized (tape) {
					if (++ tail == TAPE_LENGTH) {
						tail = 0;
					}
					tape.notify();
				}
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
			return (tape[tail] != null) && (tape[tail].sent == 0);
		}
	}
	
	private final int findEntry(int id) {
		for (int k = 0; k < TAPE_LENGTH; k ++) {
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
			if (k == TAPE_LENGTH) {
				k = 0;
			}
			if (tape[k] == null) {
				continue;
			}
			if (tape[k].sent == 0) {
				break;
			}
			tape[k].sent = 0;
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
