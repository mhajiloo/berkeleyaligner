package edu.berkeley.nlp.wordAlignment.distortion;

import java.io.Serializable;

import edu.berkeley.nlp.wa.mt.SentencePair;

/**
 * A distortion model that conditions upon the last position: P(i|h, I, state)
 */
public interface FirstOrderModel extends DistortionModel {
	public interface DistortionParameters extends Serializable {
		public abstract double get(int state, int h, int i, int I);

		public abstract void add(int state, int h, int i, int I, double count);
	}

	public DistortionParameters getDistortionParameters(SentencePair pair);

}
