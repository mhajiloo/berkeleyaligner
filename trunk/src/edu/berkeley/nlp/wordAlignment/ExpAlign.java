package edu.berkeley.nlp.wordAlignment;

import static edu.berkeley.nlp.wa.basic.LogInfo.logs;

/**
 * Expected links in an alignment (soft counts)
 */
public abstract class ExpAlign {
  public abstract double get(int j, int i); // P(a_j = i)
  public abstract int I(); // Length of English sentence
  public abstract int J(); // Length of French sentence
  public abstract void merge(ExpAlign ea1, ExpAlign ea2);

  public void dump() {
    for(int j = 0; j < J(); j++)
      for(int i = 0; i <= I(); i++)
        logs("expAlign(j=%d/%d, i=%d/%d) = %f", j, J(), i, I(), get(j, i));
  }
}
