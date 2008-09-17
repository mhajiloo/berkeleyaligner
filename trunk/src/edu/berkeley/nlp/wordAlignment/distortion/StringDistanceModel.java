package edu.berkeley.nlp.wordAlignment.distortion;

import edu.berkeley.nlp.wa.mt.SentencePair;
import edu.berkeley.nlp.wordAlignment.TrainingCache;

/**
 * Models distortion.
 */
public class StringDistanceModel extends BucketModel {

	private static final long serialVersionUID = 1L;

	DistortionParameters distParameters = new DistortionParameters() {

		private static final long serialVersionUID = 1L;

		// P(a_j = i | a_{j-1} = h) (Assume nulls are taken care of elsewhere)
		// Current position in English is i
		// Previous position in English is h
		// Independent of current position in French j
		// I = length of English sentence
		// If we're in the fringe buckets, split the probability uniformly
		public double get(int state, int h, int i, int I) {
			int d = getDistance(i, h);
			// div = Number of positions i out of [0, I]
			// that share this bucket d for this given h
			// (so we need to split the probability among these h)
			int div;
			if (d <= -windowSize) {
				d = -windowSize;
				div = (h - windowSize) - 0 + 1;
			} else if (d >= windowSize) {
				d = windowSize;
				div = I - (h + windowSize) + 1;
			} else
				div = 1;
			double norm = computeNorm(state, h, I);
			if (norm == 0) return 0;
			assert div > 0 : String.format(
					"getDDiv: state=%d, h=%d, i=%d, I=%d: div=%d, norm=%f/%f", state, h, i, I, div,
					norm, sums[state][2 * windowSize + 1]);
			return probs[state][d + windowSize] / div / norm;
		}

		int getDistance(int i, int h) {
			return i - h;
		}

		public void add(int state, int h, int i, int I, double count) {
			// dbg("DistortProbTable.add state=%d, %d -> %d: %f", state, h, i, count);
			int d = i - h;
			if (d < -windowSize)
				d = -windowSize;
			else if (d > windowSize) d = windowSize;
			// Even if we are using a scaled version of the parameter probs[state][...]
			// by the number of divisions, the maximum likelihood estimate of that
			// parameter does not scale the count.
			probs[state][d + windowSize] += count; // /d_div.second;
		}
	};

	public StringDistanceModel(StateMapper stateMapper) {
		super(stateMapper);
	}

	public DistortionParameters getDistortionParameters(SentencePair pair) {
		return distParameters;
	}

	public BucketModel copy() {
		BucketModel model = new StringDistanceModel(stateMapper);
		model.set(this);
		return model;
	}

	public TrainingCache getTrainingCache() {
		return new HMMTrainingCache(100);
		//	 Sentences of length 100 or below have cached trellises
	}

}
