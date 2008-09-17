package edu.berkeley.nlp.wa.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Lists {
	public static <T> ArrayList<T> newList(T... args) {
		ArrayList<T> l = new ArrayList<T>();
		for (T arg : args) {
			l.add(arg);
		}
		return l;
	}

	public static <T> List<T> newListFromIterable(Iterable<T> iter) {
		if (iter instanceof List) return (List<T>) iter;
		ArrayList<T> list = new ArrayList<T>();
		for (T el : iter) {
			list.add(el);
		}
		return list;
	}

	public static <T> List<T> concat(List<T> l1, List<T> l2) {
		List<T> l = new ArrayList<T>(l1);
		l.addAll(l2);
		return l;
	}

	public static <T> void reverse(List<T> list) {
		List<T> temp = new ArrayList<T>();
		temp.addAll(list);
		int len = list.size();
		for (int i = 0; i < len; i++) {
			list.set(i, temp.get(len - i - 1));
		}
	}

	public static <T> void set(List<T> list, int index, T element) {
		int gap = index - list.size() + 1;
		while (gap-- > 0) {
			list.add(null);
		}
		list.set(index, element);
	}

	public static <T extends Comparable<T>> Comparator<List<T>> comparator(T example) {
		return new Comparator<List<T>>() {
			public int compare(List<T> o1, List<T> o2) {
				for (int i = 0; i < o1.size(); i++) {
					if (i == o2.size()) return 1;
					T c1 = o1.get(i);
					T c2 = o2.get(i);
					int r = c1.compareTo(c2);
					if (r != 0) return r;
				}
				return o2.size() > o1.size() ? -1 : 0;
			}
		};
	}

	public static int min(List<Integer> list) {
		int min = Integer.MAX_VALUE;
		for (int i : list) {
			if (i < min) min = i;
		}
		return min;
	}

	public static int max(List<Integer> list) {
		int max = Integer.MIN_VALUE;
		for (int i : list) {
			if (i > max) max = i;
		}
		return max;
	}

}
