package edu.berkeley.nlp.wordAlignment.combine;

import edu.berkeley.nlp.wa.mt.Alignment;
import edu.berkeley.nlp.wa.mt.SentencePair;
import edu.berkeley.nlp.wordAlignment.WordAligner;

/**
 * Hard union corresponds to thresholding the max of posteriors for each link.
 */
public class WordAlignerHardUnion extends WordAlignerCombined {
	private static final long serialVersionUID = 1L;

	public WordAlignerHardUnion(WordAligner wa1, WordAligner wa2) {
		super(wa1, wa2);
		this.modelPrefix = "union-hard-" + wa1.getModelPrefix() + "+" + wa2.getModelPrefix();
	}

	public String getName() {
		return "UnionHard(" + wa1.getName() + ", " + wa2.getName() + ")";
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
					posteriors[j][i] = Math.max(a1.getStrength(i, j), a2.getStrength(i, j));
				}
			}
			a3 = a1.thresholdPosteriors(posteriors, posteriorDecodingThreshold);
		}
		return a3;
	}
}
