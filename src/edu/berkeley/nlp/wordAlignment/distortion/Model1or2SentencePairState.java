package edu.berkeley.nlp.wordAlignment.distortion;

import java.util.List;

import edu.berkeley.nlp.wa.basic.LogInfo;
import edu.berkeley.nlp.wa.mt.Alignment;
import edu.berkeley.nlp.wa.mt.SentencePair;
import edu.berkeley.nlp.wordAlignment.EMWordAligner;
import edu.berkeley.nlp.wordAlignment.ExpAlign;
import edu.berkeley.nlp.wordAlignment.Main;
import edu.berkeley.nlp.wordAlignment.Model;
import edu.berkeley.nlp.wordAlignment.SentencePairState;

/**
 * Inference for position models
 */
public class Model1or2SentencePairState extends SentencePairState {
	private static final long serialVersionUID = 1L;

	public static class Factory extends SentencePairState.Factory {
		private static final long serialVersionUID = 1L;

		public SentencePairState create(SentencePair pair, EMWordAligner wa) {
			if (wa.isReversed()) pair = pair.reverse();
			return new Model1or2SentencePairState(pair.getEnglishWords(), pair
					.getForeignWords(), wa);
		}

		public String getName() {
			return "Model1";
		}
	}

	public Model1or2SentencePairState(List<String> enWords, List<String> frWords,
			EMWordAligner wa) {
		super(enWords, frWords, wa);

		nullProb = EMWordAligner.nullProb / (I + 1);
	}

	// Return P(a_j = i | f, e)
	double alignProb(int j, int i) {
		return (i == I ? nullProb : (1 - nullProb) / I);
	}

	// Compute expected alignments for a particular sentence
	public ExpAlign computeExpAlign() {
		double[][] expAlign = new double[J][I + 1];

		likelihood = 1;
		for (int j = 0; j < J; j++) {
			// Compute P(a_j | f, e) \propto P(a_j, f | e) = P(a_j) P(f_j | e_{a_j})
			String v = fr(j);
			double sum = 0;
			for (int i = 0; i <= I; i++) {
				String u = en(i);
				// TODO Emission for an unknown word should not have prob = 1
				if (EMWordAligner.handleUnknownWords) {
					double emit = (u == wa.getNull()) ? 1 : wa.getParams().transProbs.get(u, v, 0);
					expAlign[j][i] = alignProb(j, i) * emit;
				} else {
					double emit = (u == wa.getNull()) ? 1 : wa.getParams().transProbs.getSure(u, v);
					expAlign[j][i] = alignProb(j, i) * emit;
				}
				sum += expAlign[j][i];
			}

			// Normalize
			for (int i = 0; i <= I; i++)
				expAlign[j][i] /= sum;
			likelihood *= sum;

		}

		if (likelihood == 0) {
			if (Main.rantOutput) {
				String warning = "Likelihood = 0 for sentence with length (%d,%d); to prevent underflow, set to 1 (ignores the sentence)";
				LogInfo.warning(warning, enWords.size(), frWords.size());
			}
			likelihood = 1;
		}

		return new Model1ExpAlign(expAlign);
	}

	/**
	 * Update the word aligner's new translation parameters.
	 */
	public void updateNewParams(ExpAlign expAlign, Model model) {
		// Translation parameters
		updateTransProbs(expAlign, model);
	}

	public Alignment getViterbi(boolean reverse) {
		Alignment alignment = new Alignment(enWords, frWords);
		//Random rand = new Random();
		for (int j = 0; j < J; j++) {
			// For each a_j, simply pick the maximum
			int besti = -1;
			double bestp = -1;

			String v = fr(j);
			for (int i = 0; i <= I; i++) {
				String u = en(i);
				double p = alignProb(j, i) * wa.getParams().transProbs.getWithErrorMsg(u, v, 0);
				if (i != I) {
					int realI = reverse ? j : i;
					int realJ = reverse ? i : j;
					alignment.setStrength(realI, realJ, p);
				}
				if (p > bestp) {
					bestp = p;
					besti = i;
				}
			}

			assert besti != -1;
			if (besti == I) continue; // Skip NULL
			if (!reverse)
				alignment.addAlignment(besti, j, true);
			else
				alignment.addAlignment(j, besti, true);
		}
		return alignment;
	}

	public double getLikelihood(int[] pos) {
		double likelihood = 1;
		for (int j = 0; j < J; j++) {
			String v = fr(j);
			// Compute P(a_j | f, e) \propto P(a_j, f | e) = P(a_j) P(f_j | e_{a_j})
			int i = pos[j];
			String u = en(i);
			if (EMWordAligner.handleUnknownWords)
				likelihood *= alignProb(j, i) * wa.getParams().transProbs.get(u, v, 0);
			else
				likelihood *= alignProb(j, i) * wa.getParams().transProbs.getSure(u, v);
		}
		return likelihood;
	}

	double nullProb; // Specific to this sentence
}
