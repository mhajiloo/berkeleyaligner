package edu.berkeley.nlp.util;

public interface Factory<T> {
	T newInstance(Object...args);
}
