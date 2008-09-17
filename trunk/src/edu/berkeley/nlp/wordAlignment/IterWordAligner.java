package edu.berkeley.nlp.wordAlignment;

import static edu.berkeley.nlp.wa.basic.LogInfo.end_track;
import static edu.berkeley.nlp.wa.basic.LogInfo.logs;
import static edu.berkeley.nlp.wa.basic.LogInfo.printProgStatus;
import static edu.berkeley.nlp.wa.basic.LogInfo.track;
import edu.berkeley.nlp.wa.basic.IOUtils;
import edu.berkeley.nlp.wa.basic.OutputOrderedMap;
import edu.berkeley.nlp.wa.exec.Execution;
import edu.berkeley.nlp.wordAlignment.distortion.DistortionModel;

/**
 * WordAligner with parameters which are trained via some number of iterations.
 */
public abstract class IterWordAligner<D extends DistortionModel> extends WordAligner {

	// Used to evaluate performance during training time
	transient Evaluator evaluator;
	boolean reverse; // Whether to reverse roles of French and English
	transient TrainingCache trainingCache;

	// Record performance over iterations
	transient OutputOrderedMap<String, String> aerMap, changeMap;

	// Parameters
	Model<D> params, newParams;
	int iter, numIters;
	double aer;

	public abstract String getName();

	protected void initNewParams() {
		if (newParams == null) newParams = new Model<D>(params);
		newParams.initZero();
	}

	/**
	 * Write the model parameters to disk.
	 * Output files: <filePrefix><modelPrefix>.params.{bin,txt}
	 */
	private void saveParams(String filePrefix) {
		String relativeFilename = filePrefix + modelPrefix + ".params";
		String file = Execution.getFile(relativeFilename);
		String link = Execution.getFile(modelPrefix + ".params");
		if (file == null) return;
		track("saveParams(" + file + ")");
		track("Text");
		{
			// TODO Accessing the word pair stats should be trivial, but requires some refactoring
			// .... Currently, the WP states cannot be dumped as part of saveParams()
			params.dump(IOUtils.openOutHard(file + ".txt"), new WordPairStats(), reverse);
			IOUtils.createSymLink(relativeFilename + ".txt", link + ".txt");
		}
		end_track();
		track("Binary");
		{
			params.save(file + ".bin");
			IOUtils.createSymLink(relativeFilename + ".bin", link + ".bin");
			//			params.restrict(evaluator.testSentencePairs, reverse).save(file + "-test.bin");
		}
		end_track();
		end_track();
	}

	public void saveParams(int stage) {
		saveParams("stage" + stage + ".");
	}

	public void saveParams() {
		saveParams("");
	}

	public void saveLexicalWeights() {
		String file = Execution.getFile(modelPrefix + ".lexweights");
		if (file == null) return;
		track("Saving Parameters: " + file);
		params.transProbs.dump(IOUtils.openOutHard(file));
	}

	void loadParams(String dir) {
		if (dir == null || dir.equals("")) return;
		String file = dir + "/" + modelPrefix + ".params.bin";
		track("loadParams(" + file + ")");
		params = Model.load(file);
		params.transProbs.lock();
		logs("Loaded " + params);
		Execution.linkFileToExec(file, modelPrefix + "-loaded.params.bin");
		end_track();
	}

	// Keep only the top translation parameters (u, v).
	// Right now, only use the top with respect to u.
	/*void pruneNumParams() {
	 if(numTransParamsPerWord == -1) return;
	 track("pruneNumParams(" + numTransParamsPerWord + ")");
	 //params.transProbs.truncateParamsByNumber(numTransParamsPerWord);
	 end_track();
	 }*/

	void setModel(Model<D> model) {
		params = model;
		trainingCache.clear();
	}

	void initTrain(int numIters) {
		params.name = getName();

		//		aerMap = new OutputOrderedMap<String, String>(Execution.getFile(modelPrefix
		//				+ ".alignErrorRate"));
		//		changeMap = new OutputOrderedMap<String, String>(Execution.getFile(modelPrefix
		//				+ ".changeInParams"));
		Execution.putOutput("Iterations", "0");
		this.numIters = numIters;
		this.iter = 1;
	}

	boolean trainDone() {
		return iter > numIters;
	}

	void switchToNewParams() {
		// Change in parameters
		//		StatFig changeFig = params.getDiff(newParams);
		//		logss("Change in parameters: " + changeFig);
		//		changeMap.put("" + iter, "" + changeFig);
		//		Execution.putOutput("Change", changeFig.toString());

		// Switch the two
		Model<D> tmpParams = params;
		params = newParams;
		newParams = tmpParams;
		trainingCache.clear();
		//pruneParams();
		//pruneNumParams();

		// Alignment error rate
		//		aer = evaluator.test(this, false).aer;
		//		logss("AER = %f", aer);
		//		aerMap.put("" + iter, "" + aer);
		//		Execution.putOutput("AER", Fmt.D(aer));

		// Misc. input/output
		Execution.putOutput("Iterations", "" + iter);
		if (Execution.getBooleanInput("save")) saveParams();
		//		if (Execution.getBooleanInput("eval")) evaluator.test(this, true);
		if (Execution.getBooleanInput("kill")) kill();
		printProgStatus();

		iter++;
	}

	public void kill() {
		iter = numIters;
		Execution.setExecStatus("killed", true);
	}
}
