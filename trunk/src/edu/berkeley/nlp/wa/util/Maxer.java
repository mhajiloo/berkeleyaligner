package edu.berkeley.nlp.wa.util;

public class Maxer<T> {
	private double max = Double.NEGATIVE_INFINITY;
	private T argMax = null;

	public void observe(T t, double val) {
		if (val > max) {
			max = val;
			argMax = t;
		}
	}

	public double getMax() {
		return max;
	}

	public T argMax() {
		return argMax;
	}
}
