package com.sbandara.cloudpokes;

public class NotificationBuffer {
	
	private final static int TAPE_LENGTH = 128, MIN_RETAIN_MILLIS = 1000;

	private class Entry {
		
		Entry(ApnsNotification notification, long queued) {
			this.notification = notification;
			this.queued = queued;
		}
		
		final ApnsNotification notification;
		final long queued;
	}
	
	private final Entry[] tape = new Entry[TAPE_LENGTH];
	
	private int mem = 0, out = 0, head = 0;
	
	synchronized void enqueue(ApnsNotification notification) {
		final long now = System.currentTimeMillis();
		Entry entry = new Entry(notification, now);
		if (++ head == TAPE_LENGTH) {
			head = 0;
		}
		if (head == mem) {
			long elapsed = now - tape[mem].queued;
			if (elapsed < MIN_RETAIN_MILLIS) {
				
			}
		}
	}
}
