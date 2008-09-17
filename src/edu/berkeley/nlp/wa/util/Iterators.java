package edu.berkeley.nlp.wa.util;

import java.util.Iterator;

public class Iterators {
	private Iterators() {}

	public static <T> Iterator<T> maxLengthIterator(final Iterator<T> base, final int max) {
		return new Iterator<T>() {
			int count = 0;

			public boolean hasNext() {
				return base.hasNext() && count < max;
			}

			public T next() {
				count++;
				return base.next();
			}

			public void remove() {
				throw new UnsupportedOperationException();
				// TODO Maybe this should behave in a more friendly manner
			}

		};
	}

	public static class IteratorIterator<T> implements Iterator<T> {
		Iterator<T> current = null;
		Iterator keys;
		Factory<Iterator<T>> iterFactory;

		public IteratorIterator(Iterator keys, Factory<Iterator<T>> iterFactory) {
			this.keys = keys;
			this.iterFactory = iterFactory;
			current = getNextIterator();
		}

		private Iterator<T> getNextIterator() {
			Iterator<T> next = null;
			while (next == null) {
				if (!keys.hasNext()) break;
				next = iterFactory.newInstance(keys.next());
				if (!next.hasNext()) next = null;
			}
			return next;
		}

		public boolean hasNext() {
			return current != null;
		}

		public T next() {
			T next = current.next();
			if (!current.hasNext()) current = getNextIterator();
			return next;
		}

		public void remove() {
			throw new UnsupportedOperationException();
		}

	}

}
