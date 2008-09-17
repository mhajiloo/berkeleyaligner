package edu.berkeley.nlp.wa.basic;

import java.util.*;

/**
 * Allows measuring precision and recall.
 */
public class EvalResult {
  private double pp, pn, np, nn;
  private int count;

  // Probability of being positive
  public void add(double trueProb, double predProb) {
    pp += trueProb * predProb;
    pn += trueProb * (1-predProb);
    np += (1-trueProb) * predProb;
    nn += (1-trueProb) * (1-predProb);
    count++;
  }
  public void add(boolean trueVal, boolean predVal) {
    add(trueVal ? 1 : 0, predVal ? 1 : 0);
  }
  public void add(EvalResult r) {
    pp += r.pp;
    pn += r.pn;
    np += r.np;
    nn += r.nn;
    count += r.count;
  }

  public double precision() { return pp / (pp + np); }
  public double recall() { return pp / (pp + pn); }
  public double falsePos() { return np / (pp + np); }
  public double trueNeg() { return pn / (pp + pn); }
  public int count() { return count; }

  public double f1() {
    double p = precision(), r = recall();
    return 2 * p * r / (p + r);
  }

  public String toString() {
    return String.format("Precision = %f, recall = %f, F1 = %f (%d)",
        precision(), recall(), f1(), count());
  }
}
