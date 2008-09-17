package edu.berkeley.nlp.wordAlignment;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.Semaphore;

import edu.berkeley.nlp.wa.basic.LogInfo;
import edu.berkeley.nlp.wa.basic.NumUtils;
import edu.berkeley.nlp.wa.mt.Alignment;
import edu.berkeley.nlp.wa.mt.SentencePair;
import edu.berkeley.nlp.wordAlignment.distortion.DistortionModel;

/**
 * Performs operations with respect to a particular pair of aligned sentences.
 */
public abstract class SentencePairState implements Serializable {
	public abstract static class Factory implements Serializable {
		public abstract SentencePairState create(SentencePair sp, EMWordAligner wa);

		public abstract String getName();
	}

	public SentencePairState(List<String> enWords, List<String> frWords, EMWordAligner wa) {
		this.enWords = enWords;
		this.frWords = frWords;
		this.wa = wa;
		I = enWords.size();
		J = frWords.size();
		likelihood = Double.NaN;
	}

	public String en(int i) {
		return i == I ? wa.nullWord : enWords.get(i);
	}

	public String fr(int j) {
		return frWords.get(j);
	}

	public abstract ExpAlign computeExpAlign();

	public abstract void updateNewParams(ExpAlign expAlign, Model<DistortionModel> params);

	// Two types of decoding: posterior and viterbi
	public abstract Alignment getViterbi(boolean reverse);

	// If !reverse, return a JxI matrix which is the posterior probability of an alignment (i,j)
	// If reverse, return an IxJ matrix ...
	public double[][] getPosteriors(boolean reverse) {
		ExpAlign expAlign = computeExpAlign();
		// Throw away null
		double[][] posteriors = new double[J][I];
		for (int j = 0; j < J; j++)
			for (int i = 0; i < I; i++)
				posteriors[j][i] = expAlign.get(j, i);
		if (reverse) return NumUtils.transpose(posteriors);
		return posteriors;
	}

	// pos[j] = position i
	public double getLikelihood(int[] pos) {
		throw new UnsupportedOperationException();
	}

	public double logLikelihood() {
		return Math.log(likelihood);
	}

	public void updateTransProbs(ExpAlign expAlign, Model params) {
//		try {
//			sem.acquire();
//		} catch (InterruptedException e1) {
//			throw new RuntimeException("Translation model update concurrency error");
//		}
		for (int j = 0; j < J; j++) {
			String v = fr(j);
			for (int i = 0; i <= I; i++) {
				String u = en(i);
				double p = expAlign.get(j, i);
				try {
					params.transProbs.incr(u, v, p);
				} catch (ArrayIndexOutOfBoundsException e) {
					LogInfo.warning("Translation model update concurrency error");
				}
			}
		}
//		sem.release();
	}

	//	private static Semaphore sem = new Semaphore(1);
	protected List<String> enWords, frWords;
	protected transient EMWordAligner wa;
	protected int I; // Length of English and French words
	protected int J;
	protected double likelihood; // Computed when computeExpAlign() is called
}
