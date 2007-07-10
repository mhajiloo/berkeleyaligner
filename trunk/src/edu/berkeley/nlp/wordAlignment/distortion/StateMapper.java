package edu.berkeley.nlp.wordAlignment.distortion;

import java.io.Serializable;


/**
 * A StateMapper compresses states (positions in the French sentence) to allow for
 * parameter sharing.  The states are partitioned into
 * distortion groups, each of which has a vector of distortion parameters. For
 * example, a group might be identified with the compressed j position jz.
 */
public abstract class StateMapper implements Serializable {
	public abstract int numjz();

	public abstract int getjz(int j, int J);

	public boolean isValidTransition(WAState s1, WAState s2) {
		return s1.isValidTransition(s2);
	}

	/**
	 * Maps all positions to a single state: all French positions are homogenous.
	 */
	public static class SingleStateMapper extends StateMapper {

		private static final long serialVersionUID = 1L;

		public int numjz() {
			return 1;
		}

		public int getjz(int j, int J) {
			return 0;
		}

	}

	/**
	 * StateMapper creating special cases for the beginning and end of the French sentence.
	 */
	public static class EndsStateMapper extends StateMapper {
		private static final long serialVersionUID = 1L;
		static final int JZ_INIT = 0; // j = -1
		static final int JZ_MIDDLE = 1; // j >= 0 && j < J-1
		static final int JZ_PENFINAL = 2; // j == J-1
		static final int JZ_FINAL = 3; // j == J

		public int numjz() {
			return 4;
		}

		public int getjz(int j, int J) {
			if (j == -1) return JZ_INIT;
			if (j == J - 1) return JZ_PENFINAL;
			if (j == J) return JZ_FINAL;
			return JZ_MIDDLE;
		}

		public boolean isValidTransition(WAState s1, WAState s2) {
			// Is this state even consistent with jz?
			// Allowable transitions:
			// - init -> {middle, penfinal}
			// - middle -> {middle, penfinal}
			// - penfinal -> {final}
			// - final -> {}
			// Optional?
			/*
			 * if(jz == JZ_INIT) { if(s2.isFinalState()) return false; } else if(jz ==
			 * JZ_MIDDLE) { if(s2.isInitState() || s2.isFinalState()) return false; }
			 * else if(jz == JZ_PENFINAL) { if(!s2.isFinalState()) return false; } else
			 * if(jz == JZ_FINAL) { return false; }
			 */
			return super.isValidTransition(s1, s2);
		}
	}

}
