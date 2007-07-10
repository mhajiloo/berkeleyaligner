package edu.berkeley.nlp.util;

/**
 * @author Dan Klein
 */
public class Counters {
  public static <E> Counter<E> normalize(Counter<E> counter) {
    Counter<E> normalizedCounter = new Counter<E>();
    double total = counter.totalCount();
    for (E key : counter.keySet()) {
      normalizedCounter.setCount(key, counter.getCount(key) / total);
    }
    return normalizedCounter;
  }

  public static <K,V> CounterMap<K,V> conditionalNormalize(CounterMap<K,V> counterMap) {
    CounterMap<K,V> normalizedCounterMap = new CounterMap<K,V>();
    for (K key : counterMap.keySet()) {
      Counter<V> normalizedSubCounter = normalize(counterMap.getCounter(key));
      for (V value : normalizedSubCounter.keySet()) {
        double count = normalizedSubCounter.getCount(value);
        normalizedCounterMap.setCount(key, value, count);
      }
    }
    return normalizedCounterMap;
  }
}
