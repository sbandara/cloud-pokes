package com.sbandara.cloudpokes;

public class EntryNotFoundException extends Exception {
	
	private final int id;
	
	EntryNotFoundException(int id) {
		super(String.format("Unable to rewind to entry %d.", id));
		this.id = id;
		
	}
	
	public int getEntryId() { return id; }

	private static final long serialVersionUID = 1L;		
}
