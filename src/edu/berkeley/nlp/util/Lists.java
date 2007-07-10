package edu.berkeley.nlp.util;

import java.util.ArrayList;
import java.util.List;

public class Lists {
	public static <T> ArrayList<T> newList(T... args) {
		ArrayList<T> l = new ArrayList<T>();
		for (T arg : args) {
			l.add(arg);
		}
		return l;
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

}
