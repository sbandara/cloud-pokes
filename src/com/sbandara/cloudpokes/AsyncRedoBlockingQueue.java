package com.sbandara.cloudpokes;

public final class AsyncRedoBlockingQueue {
	
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
		
		void execute() {
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
	private int tail = 0, head = 0, last_id = -1;
	private final Consumer consumer = new Consumer();
	private Thread worker = null;
	
	private int inc(int k) {
		if (++ k == tape_length) {
			k = 0;
		}
		return k;
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
			if (worker == null) {
				worker = new Thread(consumer);
				worker.start();
			}
		}
		if (was_interrupted) {
			Thread.currentThread().interrupt();
		}
		last_id = id;
	}
	
	private final class Consumer implements Runnable {
		
		public void run() {
			for (;;) {
				synchronized (tape) {
					tail = inc(tail);
					tape.notify();
				}
				tape[tail].execute();
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
	
	synchronized public void rewind(int id) throws EntryNotFoundException {
		boolean was_interrupted = false;
		int idx = -1;
		if (id == last_id) {
			return;
		}
		for (int k = inc(head); k != head; k = inc(k)) {
			if ((tape[k] != null) && (tape[k].id == id) && (tape[k].run > 0)) {
				idx = k;
				break;
			}
		}
		if (idx == -1) {
			throw new EntryNotFoundException(id);
		}
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
			tail = idx;
			for (int k = inc(tail); k != inc(head); k = inc(k)) {
				if (tape[k].run == 0) {
					break;
				}
				tape[k].run = 0;
			}
			worker = new Thread(consumer);
		}
		worker.start();
		if (was_interrupted) {
			Thread.currentThread().interrupt();
		}
	}
	
	public synchronized void purgeQueue() {
		boolean was_interrupted = false;
		synchronized (consumer) {
			while (worker != null) {
				try {
					consumer.wait();
				}
				catch (InterruptedException e) {
					was_interrupted = true;
				}
			}
		}
		if (was_interrupted) {
			Thread.currentThread().interrupt();
		}
	}
}
