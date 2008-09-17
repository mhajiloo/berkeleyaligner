package edu.berkeley.nlp.wordAlignment.combine;

import edu.berkeley.nlp.wa.mt.Alignment;
import edu.berkeley.nlp.wa.mt.SentencePair;
import edu.berkeley.nlp.wordAlignment.WordAligner;

/**
 * Soft union corresponds to thresholding the average of posteriors for each link.
 */
public class WordAlignerSoftUnion extends WordAlignerCombined {
	private static final long serialVersionUID = 1L;

	public WordAlignerSoftUnion(WordAligner wa1, WordAligner wa2) {
		super(wa1, wa2);
		this.modelPrefix = "union-soft-" + wa1.getModelPrefix() + "+" + wa2.getModelPrefix();
	}

	public String getName() {
		return "UnionSoft(" + wa1.getName() + ", " + wa2.getName() + ")";
	}

	Alignment combineAlignments(Alignment a1, Alignment a2, SentencePair sentencePair) {
		Alignment a3;
		if (!usePosteriorDecodingFlag) {
			a3 = a1.union(a2);
		} else {
			int I = sentencePair.getEnglishWords().size();
			int J = sentencePair.getForeignWords().size();
			double[][] posteriors = new double[J][I];

			for (int j = 0; j < J; j++) {
				for (int i = 0; i < I; i++) {
					posteriors[j][i] = (a1.getStrength(i, j) + a2.getStrength(i, j)) / 2.0;
				}
			}
			a3 = a1.thresholdPosteriors(posteriors, posteriorDecodingThreshold);
		}
		return a3;
	}

}
