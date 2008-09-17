package edu.berkeley.nlp.wa.util;

public interface Factory<T> {
	T newInstance(Object...args);
}
