package edu.berkeley.nlp.wordAlignment;

import static edu.berkeley.nlp.wa.basic.LogInfo.logss;
import edu.berkeley.nlp.wa.basic.BigStatFig;

public class Performance {
  public Performance(double precision, double recall, double aer) {
    this.precision = precision;
    this.recall = recall;
    this.aer = aer;
  }

  public Performance() {
  }

  public void addPoint(boolean proposed, boolean sure, boolean possible, double strength) {
    // Update counts
    if(proposed && sure) proposedSureCount += 1;
    if(proposed && possible) proposedPossibleCount += 1;
    if(proposed) proposedCount += 1;
    if(sure) sureCount++;

    // Add up strengths
    if(sure) sureStrength.add(strength);
    else if(possible) possStrength.add(strength);
    else noneStrength.add(strength);
  }

  // Compute precision, recall, AER from counts.
  public void computeFromCounts() {
    precision = proposedPossibleCount/(double)proposedCount;
    recall = proposedSureCount/(double)sureCount;
    aer = 1.0-(proposedSureCount+proposedPossibleCount)/(double)(sureCount+proposedCount);
  }

  public void dump() {
    //logss("Sure strength: " + sureStrength);
    //logss("Poss strength: " + possStrength);
    //logss("None strength: " + noneStrength);
    logss("Unaligned: %d, %d", numNull1, numNull2);
    logss("A = %d, S = %d, A&S = %d, A&P = %d",
        proposedCount, sureCount, proposedSureCount, proposedPossibleCount);
    logss(toString());
  }

  public String simpleString() {
    return String.format("%f %f %f", precision, recall, aer);
  }

  public String toString() {
    return String.format("Precision = %f, Recall = %f, AER = %f, best AER = %f",
        precision, recall, aer, bestAER);
  }

  public boolean hasBestAER() { return bestAER <= 1; }

  BigStatFig sureStrength = new BigStatFig();
  BigStatFig possStrength = new BigStatFig();
  BigStatFig noneStrength = new BigStatFig();

  int proposedSureCount = 0;
  int proposedPossibleCount = 0;
  int sureCount = 0;
  int proposedCount = 0;
  int numNull1 = 0, numNull2 = 0;
  double precision, recall, aer;
  double bestAER, bestThreshold;
}
