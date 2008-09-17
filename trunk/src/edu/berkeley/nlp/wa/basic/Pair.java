package edu.berkeley.nlp.wa.basic;

import java.io.*;
import java.util.*;

/**
 * A generic-typed pair of objects.
 * @author Dan Klein
 */
public class Pair<F, S> implements Serializable {
	static final long serialVersionUID = 42;

	F first;
	S second;

	public F getFirst() {
		return first;
	}

	public S getSecond() {
		return second;
	}

	public void setFirst(F pFirst) {
		first = pFirst;
	}

	public void setSecond(S pSecond) {
		second = pSecond;
	}

	public Pair<S, F> reverse() {
		return new Pair<S, F>(second, first);
	}

	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof Pair))
			return false;

		final Pair pair = (Pair) o;

		if (first != null ? !first.equals(pair.first) : pair.first != null)
			return false;
		if (second != null ? !second.equals(pair.second) : pair.second != null)
			return false;

		return true;
	}

	public int hashCode() {
		int result;
		result = (first != null ? first.hashCode() : 0);
		result = 29 * result + (second != null ? second.hashCode() : 0);
		return result;
	}

	public String toString() {
		return "(" + getFirst() + ", " + getSecond() + ")";
	}

	public Pair(F first, S second) {
		this.first = first;
		this.second = second;
	}

	// Compares only first values
	public static class FirstComparator<S extends Comparable<? super S>, T> implements
			Comparator<Pair<S, T>> {
		public int compare(Pair<S, T> p1, Pair<S, T> p2) {
			return p1.getFirst().compareTo(p2.getFirst());
		}
	}

	public static class ReverseFirstComparator<S extends Comparable<? super S>, T>
			implements Comparator<Pair<S, T>> {
		public int compare(Pair<S, T> p1, Pair<S, T> p2) {
			return p2.getFirst().compareTo(p1.getFirst());
		}
	}

	public static <S, T> Pair<S, T> newPair(S first, T second) {
		return new Pair<S, T>(first, second);
	}
}
