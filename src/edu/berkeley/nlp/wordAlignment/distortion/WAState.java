package edu.berkeley.nlp.wordAlignment.distortion;

/**
 * Represents a state of the Hidden Markov Model. We support non-homogenous
 * HMMs. The dependence on the time j is summarized into numjz() states, 0, ...,
 * numjz-1. getjz() returns the index summarizing time step j.
 */
public class WAState {
	int i; // Most recent (including current) English position that was not null-aligned
	boolean currAligned; // Whether the current English position is aligned (not null)
	int I; // Length of English sentence (same for all states in the group)

	public static class Factory {
		public WAState createState(int i, boolean currAligned, int I) {
			if (i == I && !currAligned)
				return null; // Impossible state: previous word aligned to end I
			return new WAState(i, currAligned, I);
		}

		public WAState getInitState(int I) {
			return createState(-1, true, I);
		}

		public WAState getFinalState(int I) {
			return createState(I, true, I);
		}
	}

	protected WAState(int i, boolean currAligned, int I) {
		this.i = i;
		this.currAligned = currAligned;
		this.I = I;
	}

	public boolean isValidTransition(WAState s2) {
		if (!s2.currAligned && i != s2.i)
			return false; // Consistency: (i1, b1) -> (i2, b2=0)
		if (s2.isInitState())
			return false; // No transitions into initial state
		if (isFinalState())
			return false; // No transitions out of final state
		return true;
	}

	public boolean isInitState() {
		return i == -1 && currAligned;
	}

	public boolean isFinalState() {
		return i == I && currAligned;
	}

	public boolean equals(Object _other) {
		WAState other = (WAState) _other;
		return i == other.i && currAligned == other.currAligned;
	}

	public int hashCode() {
		return i * 2 + (currAligned ? 1 : 0);
	}

	public String toString() {
		// return String.format("i=%d/%d,b=%d", i, I, currAligned ? 1 : 0);
		return String.format("%d,%d", i, currAligned ? 1 : 0);
	}

}
