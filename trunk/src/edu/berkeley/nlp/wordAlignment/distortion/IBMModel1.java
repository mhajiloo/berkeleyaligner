package edu.berkeley.nlp.wordAlignment.distortion;

import java.io.PrintWriter;

import edu.berkeley.nlp.wordAlignment.distortion.StateMapper.SingleStateMapper;

/**
 * Uniform distortion distribution over positions.
 */
public class IBMModel1 extends PositionModel {

	private static final long serialVersionUID = 1L;

	public DistortionModel copy() {
		return new IBMModel1();
	}

	public void dump(PrintWriter out) {
		out.println("Uniform models have no content.");
	}

	public void incrAll(DistortionModel other) {}

	public void initUniform() {}

	public void initZero() {}

	public void normalize() {}

	public void add(int state, int i, int I, double count) {}

	public double get(int state, int i, int I) {
		return 1.0 / (I + 1);
	}

	public StateMapper getStateMapper() {
		return new SingleStateMapper();
	}

}
