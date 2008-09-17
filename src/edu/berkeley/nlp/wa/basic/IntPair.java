package edu.berkeley.nlp.wa.basic;

import java.io.Serializable;

public class IntPair implements Serializable {
  private static final long serialVersionUID = 42;

  public IntPair() { }
  public IntPair(int first, int second) {
    this.first = first;
    this.second = second;
  }

  public String toString() {
    return first + "," + second;
  }

  public int first, second;
}
