package edu.berkeley.nlp.wordAlignment;

import static edu.berkeley.nlp.wa.basic.LogInfo.end_track;
import static edu.berkeley.nlp.wa.basic.LogInfo.logs;
import static edu.berkeley.nlp.wa.basic.LogInfo.logss;
import static edu.berkeley.nlp.wa.basic.LogInfo.stdout;
import static edu.berkeley.nlp.wa.basic.LogInfo.track;

import java.util.Map;
import java.util.concurrent.Semaphore;

import edu.berkeley.nlp.wa.basic.Fmt;
import edu.berkeley.nlp.wa.basic.LogInfo;
import edu.berkeley.nlp.wa.basic.Option;
import edu.berkeley.nlp.wa.basic.OutputOrderedMap;
import edu.berkeley.nlp.wa.basic.StopWatch;
import edu.berkeley.nlp.wa.basic.String2DoubleMap;
import edu.berkeley.nlp.wa.basic.StringDoubleMap;
import edu.berkeley.nlp.wa.concurrent.WorkQueue;
import edu.berkeley.nlp.wa.exec.Execution;
import edu.berkeley.nlp.wa.mt.Alignment;
import edu.berkeley.nlp.wa.mt.SentencePair;
import edu.berkeley.nlp.wa.mt.SentencePairReader.PairDepot;
import edu.berkeley.nlp.wordAlignment.combine.WordAlignerSoftUnion;
import edu.berkeley.nlp.wordAlignment.distortion.DistortionModel;

/**
 * Generic template for the EM-based IBM models.
 */
public class EMWordAligner extends IterWordAligner<DistortionModel> {
	private static final long serialVersionUID = 1L;
	// Alignments always go from English to French: P(F|E)
	SentencePairState.Factory spsFactory; // Used to create objects of a particular model
	final String nullWord = "(NULL)";
	private String2DoubleMap lexicalPrior;

	// Options
	@Option(gloss = "How to assign null-word probabilities (=1 means 1/n)")
	public static double nullProb = .000001;
	@Option(gloss = "Use posterior decoding (recommended for best performance).")
	public static boolean usePosteriorDecoding = true;
	@Option(gloss = "Threshold in [0,1] for deciding whether an alignment should exist.")
	public static double posteriorDecodingThreshold = 0.5;
	@Option(gloss = "When merging expected sufficient statistics, take into account the NULL (fix).")
	public static boolean mergeConsiderNull = false;
	@Option(gloss = "Don't crash with unknown words (better to train on test set).")
	public static boolean handleUnknownWords = false;
	@Option(gloss = "Fraction of a count to add for links in dictionary prior (1 works well).")
	public static double priorFraction = 0.0;
	@Option(gloss = "Number of concurrent threads to use during E-step (set to number of processors).")
	public static int numThreads = 1;
	@Option(gloss = "Safe concurrency (gets rid of concurrency warnings at the expense of speed)")
	public static boolean safeConcurrency = false;
	@Option(gloss = "Whether to evaluate the model after each training iteration (slower, more memory).")
	public static boolean evaluateDuringTraining = false;

	public EMWordAligner(SentencePairState.Factory spsFactory, Evaluator evaluator,
			boolean reverse) {
		this.spsFactory = spsFactory;
		this.evaluator = evaluator;
		this.reverse = reverse;
		this.modelPrefix = !reverse ? "1" : "2";
	}

	public String getName() {
		return spsFactory.getName() + (reverse ? ":reversed" : ":normal");
	}

	/**
	 * Trains a single directional model (not used in joint training)
	 * 
	 * @param sentences training sentences
	 * @param numIters training iterations
	 */
	public void train(PairDepot sentences, int numIters) {
		track("EMWordAligner.train(): " + sentences.size() + " sentences");

		initTrain(numIters); // logging initialization
		while (!trainDone()) {
			track("Iteration " + iter + "/" + numIters);
			Execution.putOutput("iters", numIters);

			initNewParams();
			double logLikelihood = 0;
			int sentenceNumber = 0;
			for (SentencePair sp : sentences) {
				logs("Sentence " + ++sentenceNumber + "/" + sentences.size());

				SentencePairState sps = newSentencePairState(sp);

				// E-step
				StopWatch.start("E-step");
				ExpAlign expAlign = sps.computeExpAlign();
				logLikelihood += sps.logLikelihood();
				StopWatch.accumStop("E-step");

				if (Main.rantOutput) expAlign.dump();

				// M-step (partial)
				StopWatch.start("M-step");
				sps.updateNewParams(expAlign, newParams);
				StopWatch.accumStop("M-step");
			}
			StopWatch.start("M-step");
			includePriorCounts();
			newParams.normalize(); // M-step (finish)
			switchToNewParams();
			StopWatch.accumStop("M-step");

			logss("Log-likelihood = " + Fmt.D(logLikelihood));
			if (Main.rantOutput) params.dump(stdout, null, reverse);

			end_track();
		}

		end_track();
	}

	/**
	 * Train the two models jointly.
	 * Use modified EM algorithm (key step: merging of expectations).
	 */
	public static void jointTrain(EMWordAligner wa1, EMWordAligner wa2,
			PairDepot trainingPairs, int numIters, boolean merge) {
		String mergeString = (merge) ? "jointly" : "independently";
		track("Joint Train: " + trainingPairs.size() + " sentences, %s", mergeString);

		WordAligner intwa = new WordAlignerSoftUnion(wa1, wa2);

		OutputOrderedMap<Integer, String> aerMap = null;
		if (evaluateDuringTraining) {
			String mapFile = Execution.getFile(intwa.modelPrefix + ".alignErrorRate");
			aerMap = new OutputOrderedMap<Integer, String>(mapFile);
		}

		wa1.initTrain(numIters);
		wa2.initTrain(numIters);
		while (!wa1.trainDone() && !wa2.trainDone()) {
			track("Iteration " + wa1.iter + "/" + numIters);

			wa1.initNewParams();
			wa2.initNewParams();

			if (numThreads > 1) {
				runParallelEStep(trainingPairs, wa1, wa2, merge);
			} else {
				runSerialEStep(trainingPairs, wa1, wa2, merge);
			}

			// M-step (finish)
			wa1.includePriorCounts();
			wa2.includePriorCounts();
			wa1.newParams.normalize();
			wa2.newParams.normalize();
			wa1.switchToNewParams();
			wa2.switchToNewParams();

			// Evaluate joint model
			if (evaluateDuringTraining) {
				double aer = wa1.evaluator.test(intwa, false).aer;
				logss("AER 1+2 = " + Fmt.D(aer));
				aerMap.put(wa1.iter, Fmt.D(wa1.aer) + " " + Fmt.D(wa2.aer) + " "
						+ Fmt.D(aer));
				Execution.putOutput("AER", Fmt.D(aer));
			}
			end_track();
		}

		end_track();
	}

	private static void runSerialEStep(PairDepot trainingPairs, EMWordAligner wa1,
			EMWordAligner wa2, boolean merge) {
		int sentenceNumber = 0;
		final int numPairs = trainingPairs.size();

		double logLikelihood1 = 0.0, logLikelihood2 = 0.0;

		for (final SentencePair sp : trainingPairs) {
			final int sentNumber = ++sentenceNumber;
			logs("Sentence " + sentNumber + "/" + numPairs);

			SentencePairState sps1, sps2;

			ExpAlign expAlign1, expAlign2;

			// E-step
			sps1 = wa1.newSentencePairState(sp);
			expAlign1 = sps1.computeExpAlign();
			sps2 = wa2.newSentencePairState(sp);
			expAlign2 = sps2.computeExpAlign();
			logLikelihood1 += sps1.logLikelihood();
			logLikelihood2 += sps2.logLikelihood();
			if (logLikelihood1 == Double.NEGATIVE_INFINITY)
				throw new RuntimeException(sp.toString());

			if (merge) expAlign1.merge(expAlign1, expAlign2);

			// M-step (partial)
			//				wa1.newParams.distortionModel.registerEnglishTree(sp.getEnglishTree());
			sps1.updateNewParams(expAlign1, wa1.newParams);
			sps2.updateNewParams(expAlign2, wa2.newParams);
		}
		logss("Log-likelihood 1 = " + Fmt.D(logLikelihood1));
		logss("Log-likelihood 2 = " + Fmt.D(logLikelihood2));
	}

	private static void runParallelEStep(PairDepot trainingPairs,
			final EMWordAligner wa1, final EMWordAligner wa2, final boolean merge) {
		int sentenceNumber = 0;
		final int numPairs = trainingPairs.size();

		WorkQueue workQueue = new WorkQueue(numThreads);
		final Semaphore updateSem = new Semaphore(1);
		for (final SentencePair sp : trainingPairs) {
			final int sentNumber = ++sentenceNumber;
			workQueue.execute(new Runnable() {

				public void run() {
					logs("Sentence " + sentNumber + "/" + numPairs);

					SentencePairState sps1, sps2;

					ExpAlign expAlign1, expAlign2;

					// E-step
					sps1 = wa1.newSentencePairState(sp);
					expAlign1 = sps1.computeExpAlign();
					sps2 = wa2.newSentencePairState(sp);
					expAlign2 = sps2.computeExpAlign();
					//					logLikelihood1 += sps1.logLikelihood();
					//					logLikelihood2 += sps2.logLikelihood();

					if (merge) expAlign1.merge(expAlign1, expAlign2);

					if (safeConcurrency) {
						try {
							updateSem.acquire();
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					sps1.updateNewParams(expAlign1, wa1.newParams);
					sps2.updateNewParams(expAlign2, wa2.newParams);
					if (safeConcurrency) updateSem.release();
				}

			});
		}
		workQueue.finishWork();
	}

	private void includePriorCounts() {
		// Pass through the prior map and add soft counts to observed pairs.
		if (lexicalPrior != null) {
			for (Map.Entry<String, StringDoubleMap> first : lexicalPrior) {
				for (StringDoubleMap.Entry second : first.getValue()) {
					String s1 = first.getKey();
					String s2 = second.getKey();
					if (newParams.transProbs.containsKey(s1, s2)) {
						newParams.transProbs.incr(s1, s2, second.getValue()
								* priorFraction);
					}
				}
			}
		}
	}

	SentencePairState newSentencePairState(SentencePair sp) {
		return spsFactory.create(sp, this);
	}

	public Alignment alignSentencePair(SentencePair sp) {
		SentencePairState sps = newSentencePairState(sp);
		if (usePosteriorDecoding) {
			return (new Alignment(sp, false)).thresholdPosteriors(sps
					.getPosteriors(reverse), posteriorDecodingThreshold);
		} else {
			return sps.getViterbi(reverse);
		}
	}

	public void initializeModel(String loadParamsDir, DistortionModel distModel,
			boolean loadLexicalModelOnly, boolean reverse, PairDepot trainingPairs) {
		LogInfo.track("Initializing %s model", (reverse) ? "reverse" : "forward");
		if (!loadParamsDir.equals("")) {
			loadParams(loadParamsDir);
			if (loadLexicalModelOnly) {
				params.distortionModel = distModel;
				distModel.initUniform();
			}
			this.trainingCache = distModel.getTrainingCache();
		} else {
			Model<DistortionModel> statModel;
			statModel = new Model<DistortionModel>("Norm", reverse, distModel);
			this.trainingCache = distModel.getTrainingCache();
			setModel(statModel);

			// Initialize translation probabilities to uniform
			WordPairStats wpStats = new WordPairStats(trainingPairs);
			statModel.transProbs = new StrCondProbTable();
			wpStats.allocateForSentencePairs(statModel.transProbs, reverse);
			statModel.transProbs.switchToSortedList();
			statModel.initUniform();
		}
		LogInfo.end_track();
	}

	public void setLexicalPrior(String2DoubleMap lexicalPrior) {
		this.lexicalPrior = lexicalPrior;
	}

	public String toString() {
		return getName();
	}

	public TrainingCache getTrainingCache() {
		return trainingCache;
	}

	public Model getParams() {
		return params;
	}

	public boolean isReversed() {
		return reverse;
	}

	public String getNull() {
		// TODO Auto-generated method stub
		return nullWord;
	}

}
