package edu.berkeley.nlp.wordAlignment;

import static edu.berkeley.nlp.wa.basic.LogInfo.stdout;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.List;

import edu.berkeley.nlp.wa.basic.BigStatFig;
import edu.berkeley.nlp.wa.basic.IOUtils;
import edu.berkeley.nlp.wa.basic.LogInfo;
import edu.berkeley.nlp.wa.mt.SentencePair;
import edu.berkeley.nlp.wordAlignment.distortion.DistortionModel;
import edu.berkeley.nlp.wordAlignment.distortion.IBMModel1;
import edu.berkeley.nlp.wordAlignment.distortion.StateMapper;

/**
 * A directional alignment model with distortion and translation components
 */
public class Model<D extends DistortionModel> implements Serializable {
	static final long serialVersionUID = 42;

	public String name;
	public boolean reverse; // Whether the transProbs are reversed
	public StrCondProbTable transProbs = new StrCondProbTable();
	public D distortionModel;

	public Model(String name, boolean reverse, D dm) {
		this.name = name;
		this.reverse = reverse;
		distortionModel = dm;
	}

	public static Model<DistortionModel> createModel1Params(String name, boolean reverse) {
		return new Model<DistortionModel>(name, reverse, new IBMModel1());
	}

	@SuppressWarnings("unchecked")
	public Model(Model<D> params) {
		this.name = params.name;
		this.reverse = params.reverse;
		this.transProbs = (StrCondProbTable) params.transProbs.copy();
		this.distortionModel = (D) params.getDistortionModel().copy();
	}

	public D getDistortionModel() {
		return distortionModel;
	}

	// Called after the M-step
	public void normalize() {
		transProbs.normalize();
		distortionModel.normalize();
	}

	public void dump(PrintWriter out, WordPairStats wpStats, boolean reverse) {
		out.println("# Name: " + name);
		out.println("# Reverse: " + reverse);
		out.println("# Distortion probabilities");
		distortionModel.dump(out);
		out.println("# Translation probabilities");
		transProbs.dump(out, wpStats, reverse);
	}

	public void save(String file) {
		IOUtils.writeObjFileHard(file, this);
	}

	@SuppressWarnings("unchecked")
	public static <T extends DistortionModel> Model<T> load(String file) {
		return (Model<T>) IOUtils.readObjFileHard(file);
	}

	public Model<D> restrict(List<SentencePair> sentences, boolean reverse) {
		Model<D> subParams = new Model<D>(name, reverse, distortionModel);
		subParams.transProbs = transProbs.restrict(sentences, reverse);
		return subParams;
	}

	public void initZero() {
		transProbs.initZero();
		distortionModel.initZero();
	}

	public void initUniform() {
		transProbs.initUniform();
		distortionModel.initUniform();
	}

	// Measure how much the parameters changed
	public BigStatFig getDiff(Model otherParams) {
		return transProbs.getDiff(otherParams.transProbs);
	}

	public String toString() {
		return name;
	}

	public static void main(String[] args) {
		LogInfo.init();
		Model params = load(args[0]);

		stdout.println("# Name: " + params.name);
		stdout.println("# Reverse: " + params.reverse);
		stdout.println("# Distortion probabilities");
		params.distortionModel.dump(stdout);
		stdout.println("# Translation probabilities");
		params.transProbs.dump(stdout);
	}

	public void incrAll(Model other) {
		transProbs.incrAll(other.transProbs);
		distortionModel.incrAll(other.distortionModel);
	}

	public StateMapper getStateMapper() {
		return distortionModel.getStateMapper();
	}

}
