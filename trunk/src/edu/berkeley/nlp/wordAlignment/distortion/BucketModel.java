package edu.berkeley.nlp.wordAlignment.distortion;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.Arrays;

import edu.berkeley.nlp.wa.basic.LogInfo;
import edu.berkeley.nlp.wa.basic.NumUtils;
import edu.berkeley.nlp.wordAlignment.SentencePairState.Factory;

/**
 * The BucketModel is a standard bucket-based HMM implementation
 * that parameterizes distortions according to a bucketed distance
 * within some fixed window, and a uniform distribution outside of
 * that window.
 */
public abstract class BucketModel implements FirstOrderModel {
	static final long serialVersionUID = 42;

	// P(a_j | a_{j-1}) consists of 2*windowSize+1 buckets
	// Anything outside the window gets mapped on to the closest bucket.
	// Also condition on a state.
	public static final int windowSize = 5;
	public int numStates;

	protected double[][] probs = null; // Transition distributions
	protected transient double[][] sums = null; // Partial sums (for normalization)
	// sums[state][k] = probs[state][0] + ... + probs[state][k-1]

	protected StateMapper stateMapper;

	public Factory getSpsFactory() {
		return new HMMSentencePairState.Factory();
	}

	public BucketModel(StateMapper stateMapper) {
		this.stateMapper = stateMapper;
		this.numStates = stateMapper.numjz();
		alloc();
	}

	public StateMapper getStateMapper() {
		return stateMapper;
	}

	private void alloc() {
		if (numStates > 0) {
			probs = new double[numStates][2 * windowSize + 1];
			sums = new double[numStates][2 * windowSize + 1 + 1];
		}
	}

	public void set(BucketModel model) {
		if (probs == null) return;
		probs = NumUtils.copy(model.probs);
		sums = NumUtils.copy(model.sums);
	}

	protected double computeNorm(int state, int i, int I) {
		int mind = Math.max(0 - i, -windowSize);
		int maxd = Math.min(I - i, windowSize);
		assert mind <= maxd : String.format("mind=%d,maxd=%d", mind, maxd);
		// Return probs[state][mind] + ... + probs[state][maxd]
		return sums[state][maxd + windowSize + 1] - sums[state][mind + windowSize];
	}

	public void computeSums() {
		if (sums == null) sums = new double[numStates][2 * windowSize + 1 + 1];
		for (int state = 0; state < numStates; state++) {
			Arrays.fill(sums[state], 0);
			for (int k = 0; k < probs[state].length; k++)
				sums[state][k + 1] = sums[state][k] + probs[state][k];
		}
	}

	public void normalize() {
		if (probs == null) return;
		for (int i = 0; i < probs.length; i++) {
			if (!NumUtils.normalizeForce(probs[i]))
				LogInfo.warning("normalize(): distortProbs(state=%d) has sum 0, using uniform", i);
		}
		computeSums();
	}

	public void initUniform() {
		if (probs == null) return;
		for (double[] p : probs)
			Arrays.fill(p, 1.0 / p.length);
		computeSums();
	}

	public void initZero() {
		if (probs == null) return;
		for (double[] p : probs)
			Arrays.fill(p, 0.0);
		computeSums();
	}

	public void dump(PrintWriter out) {
		for (int state = 0; state < numStates; state++) {
			for (int i = -windowSize; i <= windowSize; i++) {
				String s;
				if (i == -windowSize)
					s = "<= " + i;
				else if (i == windowSize)
					s = ">= " + i;
				else
					s = "= " + i;
				out.println(state + ": " + s + "\t" + probs[state][i + windowSize]);
			}
		}
	}

	private void readObject(ObjectInputStream in) throws IOException,
			ClassNotFoundException {
		probs = (double[][]) in.readObject();
		stateMapper = (StateMapper) in.readObject();
		numStates = probs.length;
		computeSums();
	}

	private void writeObject(ObjectOutputStream out) throws IOException {
		out.writeObject(probs);
		out.writeObject(stateMapper);
	}

	public void incrAll(DistortionModel otherModel) {
		StringDistanceModel other = (StringDistanceModel) otherModel;
		for (int state = 0; state < numStates; state++) {
			for (int window = 0; window < probs[state].length; window++) {
				probs[state][window] += other.probs[state][window];
			}
		}
	}

}
