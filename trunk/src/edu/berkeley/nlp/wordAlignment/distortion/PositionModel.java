package edu.berkeley.nlp.wordAlignment.distortion;

import edu.berkeley.nlp.wordAlignment.TrainingCache;
import edu.berkeley.nlp.wordAlignment.SentencePairState.Factory;

/**
 * A distortion model that considers only position: P(i | I, state)
 */
public abstract class PositionModel implements DistortionModel {
	public abstract double get(int state, int i, int I);

	public abstract void add(int state, int i, int I, double count);

	public Factory getSpsFactory() {
		return new Model1or2SentencePairState.Factory();
	}

	public TrainingCache getTrainingCache() {
		return new TrainingCache();
	}
}
