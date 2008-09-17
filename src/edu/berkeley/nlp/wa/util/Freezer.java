package edu.berkeley.nlp.wa.util;

public class Freezer {
	private boolean frozen = false;
	private Object owner;
	
	public Freezer(Object owner) {
		this.owner = owner;
	}
	
	public void freeze() {
		frozen = true;
	}
	
	public boolean checkEasy() {
		return frozen;
	}
	
	public void checkHard() {
		if (frozen) {
			throw new RuntimeException("Attempt to edit "+owner+" while it was frozen.");
		}
	}
	
	public void check() {
		checkHard();
	}
}
