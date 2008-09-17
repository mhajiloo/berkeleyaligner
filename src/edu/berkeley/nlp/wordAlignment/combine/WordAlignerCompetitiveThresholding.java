package edu.berkeley.nlp.wordAlignment.combine;

import edu.berkeley.nlp.wa.mt.Alignment;
import edu.berkeley.nlp.wa.mt.SentencePair;
import edu.berkeley.nlp.wordAlignment.EMWordAligner;
import edu.berkeley.nlp.wordAlignment.WordAligner;

/**
 * The competitive thresholding heuristic for posterior decoding is described
 * in Tailoring Word Alignments to Syntactic MT (DeNero & Klein, 2007).
 */
public class WordAlignerCompetitiveThresholding extends WordAligner {

	private static final long serialVersionUID = 1L;
	private WordAligner base;
	boolean usePosteriorDecodingFlag;
	double posteriorDecodingThreshold;

	public WordAlignerCompetitiveThresholding(WordAligner base) {
		this.base = base;
		this.modelPrefix = "ct-" + base.getModelPrefix();
		usePosteriorDecodingFlag = EMWordAligner.usePosteriorDecoding;
		posteriorDecodingThreshold = EMWordAligner.posteriorDecodingThreshold;
	}

	public Alignment alignSentencePair(SentencePair sp) {
		Alignment al = base.alignSentencePair(sp);
		if (usePosteriorDecodingFlag)
			return competitiveThresholding(al, posteriorDecodingThreshold);
		else
			return al;
	}

	@Override
	public String getName() {
		return "CompetitiveThreshold(" + base.getName() + ")";
	}

	public static Alignment competitiveThresholding(Alignment al, double threshold) {
		double[][] post = al.getPosteriors();
		//		Alignment alThresholded = al.thresholdAlignmentByStrength(threshold);
		int I = post.length;
		int J = post[0].length;
		Alignment newAlign = al.thresholdAlignmentByStrength(threshold);

		// I then J
		for (int i = 0; i < I; i++) {
			int maxIndex = -1;
			double maxValue = -1;

			// Find the maximum
			for (int j = 0; j < J; j++) {
				if (post[i][j] > maxValue) {
					maxValue = post[i][j];
					maxIndex = j;
				}
			}

			if (maxValue >= threshold) {
				// Fill above
				boolean contiguous = true;
				for (int j = maxIndex; j < J; j++) {
					if (contiguous) {
						if (post[i][j] >= threshold) {
							//							newAlign.addAlignment(j, i);
						} else {
							contiguous = false;
							//							newPost[i][j] = 0;
						}
					} else {
						newAlign.removeAlignment(j, i);
						//						newPost[i][j] = 0;
					}
				}

				// Fill below
				contiguous = true;
				for (int j = maxIndex; j >= 0; j--) {
					if (contiguous) {
						if (post[i][j] >= threshold) {
							//							newAlign.addAlignment(j, i);
							//														newPost[i][j] = post[i][j];
						} else {
							contiguous = false;
							//							newPost[i][j] = 0;
						}
					} else {
						newAlign.removeAlignment(j, i);
						//						newPost[i][j] = 0;
					}
				}
			}
		}

		// J then I
		for (int j = 0; j < J; j++) {
			int maxIndex = -1;
			double maxValue = -1;

			// Find the maximum
			for (int i = 0; i < I; i++) {
				if (post[i][j] > maxValue) {
					maxValue = post[i][j];
					maxIndex = i;
				}
			}

			if (maxValue >= threshold) {
				// Fill below
				boolean contiguous = true;
				for (int i = maxIndex; i < I; i++) {
					if (contiguous) {
						if (post[i][j] >= threshold) {
							//							newAlign.addAlignment(j, i);
							//							newPost[i][j] = post[i][j];
						} else {
							contiguous = false;
							//							newPost[i][j] = 0;
						}
					} else {
						newAlign.removeAlignment(j, i);
						//						newPost[i][j] = 0;
					}
				}

				// Fill above
				contiguous = true;
				for (int i = maxIndex; i >= 0; i--) {
					if (contiguous) {
						if (post[i][j] >= threshold) {
							//							newAlign.addAlignment(j, i);
							//							newPost[i][j] = post[i][j];
						} else {
							contiguous = false;
							//							newPost[i][j] = 0;
						}
					} else {
						newAlign.removeAlignment(j, i);
						//						newPost[i][j] = 0;
					}
				}
			}
		}

		return newAlign;
	}

	public void setThreshold(int threshold) {
		posteriorDecodingThreshold = threshold;
	}

	@Override
	public Alignment thresholdAlignment(Alignment al, double threshold) {
		return competitiveThresholding(al, threshold);
	}

}
