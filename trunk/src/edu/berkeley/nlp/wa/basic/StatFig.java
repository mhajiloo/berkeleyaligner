package edu.berkeley.nlp.wa.basic;

import java.util.*;

/**
 * For keeping track of statistics.
 * Just keeps average and sum.
 */
public class StatFig {
  public StatFig() { sum = 0; n = 0; }
  public void add(boolean x) { if(x) sum++; n++; }
  public void add(double x) { sum += x; n++; }
  public double mean() { return sum/n; }
  public int size() { return n; }
  public double total() { return sum; }
  public String toString() {
    return Fmt.D(mean()) + " (" + n + ")";
  }

  double sum;
  int n;
}
