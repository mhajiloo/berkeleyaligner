package edu.berkeley.nlp.wa.basic;

import java.io.*;
import java.util.*;

/**
 * Maintains a two-way map between a set of objects and contiguous integers from
 * 0 to the number of objects.  Use get(i) to look up object i, and
 * indexOf(object) to look up the index of an object.
 *
 * @author Dan Klein
 */
public class Indexer <E> extends AbstractList<E> implements Serializable {
  private static final long serialVersionUID = -8769544079136550516L;
  List<E> objects;
  Map<E, Integer> indexes;

  /**
   * Return the object with the given index
   *
   * @param index
   */
  @Deprecated
  public E get(int index) {
    return objects.get(index);
  }
  public E getObject(int index) {
    return objects.get(index);
  }

  /**
   * Returns the number of objects indexed.
   */
  public int size() {
    return objects.size();
  }

  /**
   * Returns the index of the given object, or -1 if the object is not present
   * in the indexer.
   *
   * @param o
   * @return
   */
  public int indexOf(Object o) {
    Integer index = indexes.get(o);
    if (index == null)
      return -1;
    return index;
  }

  /**
   * Constant time override for contains.
   */
  public boolean contains(Object o) {
    return indexes.keySet().contains(o);
  }

  // Return the index of the element
  // If doesn't exist, add it.
  public int getIndex(E e) {
    if(e == null) return -1;
    Integer index = indexes.get(e);
    if(index == null) {
      index = size();
      objects.add(e);
      indexes.put(e, index);
    }
    return index;
  }

  public Indexer() {
    objects = new ArrayList<E>();
    indexes = new HashMap<E, Integer>();
  }
  
  public Indexer(Collection<? extends E> c) {
    this();
    for(E a : c) getIndex(a);
  }

  // Not really safe; trust them not to modify it
  public List<E> getObjects() { return objects; }
}
