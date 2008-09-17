package edu.berkeley.nlp.wa.util;

import java.util.*;

/**
 * @author Dan Klein
 */
public class CollectionUtils {
	public static <E extends Comparable<E>> List<E> sort(Collection<E> c) {
		List<E> list = new ArrayList<E>(c);
		Collections.sort(list);
		return list;
	}

	public static <E> List<E> sort(Collection<E> c, Comparator<E> r) {
		List<E> list = new ArrayList<E>(c);
		Collections.sort(list, r);
		return list;
	}

	public static <K, V> void addToValueList(Map<K, List<V>> map, K key, V value) {
		List<V> valueList = map.get(key);
		if (valueList == null) {
			valueList = new ArrayList<V>();
			map.put(key, valueList);
		}
		valueList.add(value);
	}

	public static <K, V> List<V> getValueList(Map<K, List<V>> map, K key) {
		List<V> valueList = map.get(key);
		if (valueList == null)
			return Collections.emptyList();
		return valueList;
	}

	public static <T> List<T> makeList(T... args) {
		return new ArrayList<T>(Arrays.asList(args));
	}
}
