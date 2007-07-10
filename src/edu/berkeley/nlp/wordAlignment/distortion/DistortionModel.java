package edu.berkeley.nlp.wordAlignment.distortion;

import java.io.PrintWriter;
import java.io.Serializable;

import edu.berkeley.nlp.wordAlignment.TrainingCache;
import edu.berkeley.nlp.wordAlignment.SentencePairState.Factory;

/**
 * Distortion component of a Model.
 */
public interface DistortionModel extends Serializable {

	public void normalize();

	public void initUniform();

	public void initZero();

	public void incrAll(DistortionModel other);

	public DistortionModel copy();

	public void dump(PrintWriter out);

	public StateMapper getStateMapper();

	public Factory getSpsFactory();

	public TrainingCache getTrainingCache();
}