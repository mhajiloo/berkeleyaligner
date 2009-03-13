package edu.berkeley.nlp.wordAlignment;

import static edu.berkeley.nlp.wa.basic.LogInfo.logs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.InputMismatchException;
import java.util.List;

import edu.berkeley.nlp.wa.basic.Exceptions;
import edu.berkeley.nlp.wa.basic.Fmt;
import edu.berkeley.nlp.wa.basic.IOUtils;
import edu.berkeley.nlp.wa.basic.LogInfo;
import edu.berkeley.nlp.wa.basic.Option;
import edu.berkeley.nlp.wa.basic.String2DoubleMap;
import edu.berkeley.nlp.wa.exec.Execution;
import edu.berkeley.nlp.wa.mt.SentencePair;
import edu.berkeley.nlp.wa.mt.SentencePairReader;
import edu.berkeley.nlp.wa.mt.SentencePairReader.PairDepot;
import edu.berkeley.nlp.wa.syntax.Tree;
import edu.berkeley.nlp.wa.syntax.Trees;
import edu.berkeley.nlp.wa.util.Filter;
import edu.berkeley.nlp.wa.util.Filters;
import edu.berkeley.nlp.wa.util.Lists;
import edu.berkeley.nlp.wa.util.Maxer;
import edu.berkeley.nlp.wordAlignment.combine.WordAlignerCompetitiveThresholding;
import edu.berkeley.nlp.wordAlignment.combine.WordAlignerHardIntersect;
import edu.berkeley.nlp.wordAlignment.combine.WordAlignerHardUnion;
import edu.berkeley.nlp.wordAlignment.combine.WordAlignerSoftIntersect;
import edu.berkeley.nlp.wordAlignment.combine.WordAlignerSoftUnion;
import edu.berkeley.nlp.wordAlignment.distortion.DistortionModel;
import edu.berkeley.nlp.wordAlignment.distortion.IBMModel1;
import edu.berkeley.nlp.wordAlignment.distortion.IBMModel2;
import edu.berkeley.nlp.wordAlignment.distortion.StateMapper;
import edu.berkeley.nlp.wordAlignment.distortion.StringDistanceModel;
import edu.berkeley.nlp.wordAlignment.distortion.TreeWalkModel;
import edu.berkeley.nlp.wordAlignment.distortion.StateMapper.EndsStateMapper;
import edu.berkeley.nlp.wordAlignment.distortion.StateMapper.SingleStateMapper;

/**
 * Entry point for word alignment.
 */
public class Main implements Runnable {
	public enum ModelT {
		MODEL1, MODEL2, HMM, SYNTACTIC, NONE
	};

	public enum TrainMode {
		FORWARD, REVERSE, BOTH_INDEP, JOINT
	};

	// Input parameters
	@Option(gloss = "Directories or files containing training files.")
	public ArrayList<String> trainSources = Lists.newList("example/train");
	@Option(gloss = "Directory or file containing testing files.")
	public ArrayList<String> testSources = Lists.newList("example/test");
	@Option(name = "sentences", gloss = "Maximum number of the training sentences to use")
	public int maxTrainSentences = Integer.MAX_VALUE;
	@Option(gloss = "Skip this number of the first training sentences")
	public int offsetTrainingSentences = 0;
	@Option(gloss = "Maximum length (in words) of a training sentence")
	public static int maxTrainingLength = 200;
	@Option(gloss = "Maximum number of the test sentences to use")
	public int maxTestSentences = Integer.MAX_VALUE;
	@Option(gloss = "Skip this number of the first test sentences")
	public int offsetTestSentences = 0;
	@Option(gloss = "Foreign language file suffix")
	public static String foreignSuffix = "f";
	@Option(gloss = "English language file suffix")
	public static String englishSuffix = "e";
	@Option(gloss = "Reverse test set alignments (i.e., foreign to english)")
	public boolean reverseAlignments = false;
	@Option(gloss = "Convert all words to lowercase")
	public boolean lowercaseWords = false;
	@Option(gloss = "Don't load and store the training set upfront (slower, but less memory)")
	public boolean leaveTrainingOnDisk = false;
	@Option(gloss = "Save rejected sentence pairs")
	public boolean saveRejects = false;

	// Training regimen
	@Option(gloss = "Which word alignment model to use in the forward direction.")
	public ArrayList<ModelT> forwardModels = Lists.newList(ModelT.MODEL1, ModelT.HMM);
	@Option(gloss = "Which word alignment model to use in the backward direction.")
	public ArrayList<ModelT> reverseModels = Lists.newList(ModelT.MODEL1, ModelT.HMM);
	@Option(name = "iters", gloss = "Number of iterations to run the model.")
	public ArrayList<Integer> numIters = Lists.newList(5, 5);
	@Option(name = "mode", gloss = "Whether to train the two models jointly or independently.")
	public ArrayList<TrainMode> trainingModes = Lists.newList(TrainMode.JOINT,
			TrainMode.JOINT);
	@Option(gloss = "Max sentence length for caching the HMM trellis (efficiency only).")
	public int trainingCacheMaxSize = 100;

	// Model initialization
	@Option(gloss = "Directory to load parameters from.")
	public String loadParamsDir = "";
	@Option(gloss = "When true, the lexical model is loaded, but the distortion model is not.")
	public boolean loadLexicalModelOnly = true;

	// Output
	@Option(gloss = "Whether to save parameters.")
	public boolean saveParams = true;
	@Option(gloss = "Whether to save test alignments produced by the system.")
	public boolean saveAlignOutput = true;
	@Option(gloss = "Produce two GIZA files and a Pharaoh file for translation")
	public boolean alignTraining = false;
	@Option(gloss = "Produce posterior alignment weight file when aligning training (lots of disk space)")
	public static boolean writePosteriors = false;
	@Option(gloss = "Produce two lexical translation tables for lexical weighting (unsupported)")
	public boolean saveLexicalWeights = false;

	// Decoding
	@Option(gloss = "Use competitive thresholding to eliminate distributed many-to-one alignments")
	public boolean competitiveThresholding = false; // Also evaluates without thresholding
	@Option(gloss = "Evaluate directional models alone")
	public boolean evaluateDirectionalModels = false;
	@Option(gloss = "Evaluate hard alignment combinations")
	public boolean evaluateHardCombination = false;
	@Option(gloss = "Evaluate soft alignment combinations")
	public boolean evaluateSoftCombination = false; // soft-union is always included

	// Dictionary support
	@Option(gloss = "Bilingual dictionary file (e.g., en-ch.dict)")
	public String dictionary = "example/en-ch.dict";
	@Option(gloss = "Breaks up multi-word definitions and enters each word into the dictionary map")
	public boolean splitDefinitions = false;

	// Logging
	@Option(gloss = "Output a lot of junk (largely unsupported)")
	public static boolean rantOutput = false;

	public static void main(String[] args) {
		Main main = new Main();
		Execution.init(args, main, EMWordAligner.class, Evaluator.class,
				TreeWalkModel.class);
		main.run();
		Execution.finish();
	}

	public void run() {
		checkOptions(); // Run some tests to see if the inputed options make sense

		// Set path to training and test data
		SentencePairReader spReader = new SentencePairReader(lowercaseWords);
		spReader.setEnglishExtension(englishSuffix);
		spReader.setForeignExtension(foreignSuffix);
		spReader.setReverseAlignments(reverseAlignments);

		// Read (or prepare to read) data
		LogInfo.track("Preparing Training Data");
		PairDepot testPairs = spReader.pairDepotFromSources(testSources,
				offsetTestSentences, maxTestSentences, getTestFilter(), false);
		PairDepot trainingPairs = spReader.pairDepotFromSources(trainSources,
				offsetTrainingSentences, maxTrainSentences, getTrainingFilter(),
				leaveTrainingOnDisk);
		LogInfo.end_track();

		String trainSize = "Unknown number of";
		if (!leaveTrainingOnDisk)
			trainSize = ((Integer) trainingPairs.size()).toString();
		logs("%s training sentences, %d test sentences", trainSize, testPairs.size());

		// Create support objects: dictionary and evaluator
		String2DoubleMap dictionary = loadDictionary();
		String2DoubleMap reverseDictionary = null;
		if (dictionary != null) {
			reverseDictionary = new String2DoubleMap();
			dictionary.reverse(reverseDictionary);
		}
		Evaluator evaluator = new Evaluator(testPairs, dictionary);

		// Training regimen
		EMWordAligner wa1 = null, wa2 = null;
		SentencePairState.Factory spsFactory1, spsFactory2;
		StrCondProbTable lastLex1 = null, lastLex2 = null; // previous stage parameters
		int numStages = getNumStagesAndCheckRegimen();

		// We must conduct one training stage to load a model, 
		// even if there are no training iterations.
		assert (numStages > 0);
		LogInfo.track("Training models: %d stages", numStages);

		for (int stage = 0; stage < numStages; stage++) {

			// Create models
			ModelT forwardType = forwardModels.get(stage);
			ModelT reverseType = reverseModels.get(stage);
			DistortionModel distModel1 = createDistortionModel(forwardType);
			DistortionModel distModel2 = createDistortionModel(reverseType);
			spsFactory1 = distModel1.getSpsFactory();
			spsFactory2 = distModel2.getSpsFactory();
			wa1 = new EMWordAligner(spsFactory1, evaluator, false);
			wa2 = new EMWordAligner(spsFactory2, evaluator, true);
			int iters = numIters.get(stage);

			// Dirichlet prior on lexical model
			wa1.setLexicalPrior(reverseDictionary);
			wa2.setLexicalPrior(dictionary);

			// Which models to train (1 normal vs. 2 reversed)
			boolean b1 = false, b2 = false;
			TrainMode trainingMode = trainingModes.get(stage);
			if (trainingMode == TrainMode.JOINT) {
				b1 = b2 = true;
				LogInfo.track("Training stage %d: %s and %s jointly for %d iterations",
						stage + 1, forwardType.toString(), reverseType.toString(), iters);
			} else if (trainingMode == TrainMode.FORWARD) {
				b1 = true;
				LogInfo.track("Training stage %d: %s for %d iterations", stage + 1,
						forwardType.toString(), iters);
			} else if (trainingMode == TrainMode.REVERSE) {
				b2 = true;
				LogInfo.track("Training stage %d: %s for %d iterations", stage + 1,
						reverseType.toString(), iters);
			} else {
				b1 = b2 = true;
				LogInfo.track(
						"Training stage %d: %s and %s independently for %d iterations",
						stage + 1, forwardType.toString(), reverseType.toString(), iters);
			}

			// Initialize models
			distModel1.initUniform();
			distModel2.initUniform();
			if (stage == 0) {
				boolean l = loadLexicalModelOnly;
				if (b1)
					wa1.initializeModel(loadParamsDir, distModel1, l, false,
							trainingPairs);
				if (b2)
					wa2
							.initializeModel(loadParamsDir, distModel2, l, true,
									trainingPairs);
			} else {
				if (b1) {
					wa1.trainingCache = distModel1.getTrainingCache();
					wa1.setModel(new Model<DistortionModel>("Norm", false, distModel1));
					wa1.params.transProbs = lastLex1;
				}
				if (b2) {
					wa2.trainingCache = distModel2.getTrainingCache();
					wa2.setModel(new Model<DistortionModel>("Norm", false, distModel2));
					wa2.params.transProbs = lastLex2;
				}
			}

			// Train
			if (iters > 0) { // Prevents weird logging for zero-iteration training
				if (trainingMode == TrainMode.BOTH_INDEP) {
					EMWordAligner.jointTrain(wa1, wa2, trainingPairs, iters, false);
				} else if (trainingMode == TrainMode.JOINT) {
					EMWordAligner.jointTrain(wa1, wa2, trainingPairs, iters, true);
				} else {
					if (b1) wa1.train(trainingPairs, iters);
					if (b2) wa2.train(trainingPairs, iters);
				}
			}

			// Save models
			if (saveLexicalWeights) {
				if (b1) wa1.saveLexicalWeights();
				if (b2) wa2.saveLexicalWeights();
			}
			if (saveParams) {
				if (b1) wa1.saveParams(stage + 1);
				if (b2) wa2.saveParams(stage + 1);
			}

			if (stage + 1 < numStages) { // Transfer model parameters to next stage
				// TODO refactor to allow transfer of distortion model parameters
				lastLex1 = wa1.params.transProbs;
				lastLex2 = wa2.params.transProbs;
			}

			LogInfo.end_track();
		}
		LogInfo.end_track();

		// Evaluate
		List<WordAligner> aligners = loadAligners(wa1, wa2);
		WordAligner bestwa = aligners.get(0);

		if (testPairs.size() > 0) {
			LogInfo.track("Evaluating %d Aligners", aligners.size());
			Maxer<WordAligner> maxer = new Maxer<WordAligner>();
			for (WordAligner aligner : aligners) {
				Performance perf = evaluator.test(aligner, saveAlignOutput);
				String name = aligner.modelPrefix;
				Execution.putOutput("best-aer-" + name, Fmt.D(perf.bestAER));
				Execution.putOutput("best-threshold-" + name, Fmt.D(perf.bestThreshold));
				Execution.putOutput("aer-" + name, Fmt.D(perf.aer));
				Execution.putOutput("prec-" + name, Fmt.D(perf.precision));
				Execution.putOutput("recall-" + name, Fmt.D(perf.recall));
				maxer.observe(aligner, -1 * perf.bestAER); // Minimize AER
			}
			bestwa = maxer.argMax();

			// Save parameters and info
			Evaluator.writeAlignments(testPairs, bestwa, "testset");

			LogInfo.end_track();
		}

		if (alignTraining) Evaluator.writeAlignments(trainingPairs, bestwa, "training");
	}

	private void checkOptions() {
		// Check for the output directory
		PrintWriter testfile = IOUtils.openOutEasy(Execution.getFile("test"));
		if (testfile == null) {
			String msg = "Files cannot be saved.  Make sure you have specified an execDir or execPoolDir to save your output files.  Use -help for more options.";
			throw Exceptions.bad(msg);
		}
		testfile.close();
		IOUtils.deleteFile(Execution.getFile("test"));

		// Check Training regimen
		getNumStagesAndCheckRegimen();
		for (ModelT model : reverseModels) {
			if (model == ModelT.SYNTACTIC) {
				throw Exceptions
						.bad("Syntactic reverse models aren't quite supported yet.");
			}
		}
	}

	private List<WordAligner> loadAligners(EMWordAligner wa1, EMWordAligner wa2) {
		ArrayList<WordAligner> aligners = new ArrayList<WordAligner>();
		if (evaluateDirectionalModels) {
			aligners.add(wa1);
			aligners.add(wa2);
		}
		if (evaluateHardCombination) {
			aligners.add(new WordAlignerHardIntersect(wa1, wa2));
			aligners.add(new WordAlignerHardUnion(wa1, wa2));
		}
		if (evaluateSoftCombination) {
			aligners.add(new WordAlignerSoftIntersect(wa1, wa2));
		}
		aligners.add(new WordAlignerSoftUnion(wa1, wa2));

		if (competitiveThresholding) {
			int numAligners = aligners.size();
			for (int i = 0; i < numAligners; i++) {
				aligners.add(new WordAlignerCompetitiveThresholding(aligners.get(i)));
			}
		}
		return aligners;
	}

	private String2DoubleMap loadDictionary() {
		String2DoubleMap dict = new String2DoubleMap();
		BufferedReader dictReader = IOUtils.openInEasy(dictionary);
		if (dictReader == null) {
			return null;
		} else {
			try {
				while (dictReader.ready()) {
					String[] words = dictReader.readLine().split("\\t");
					String[] translations = words[1].split("/");
					for (int i = 1; i < translations.length; i++) {
						String translation = translations[i];
						if (lowercaseWords) {
							translation = translation.toLowerCase();
						}
						if (splitDefinitions) {
							String[] transwords = translation.split(" ");
							int len = transwords.length;
							for (int j = 0; j < len; j++) {
								dict.incr(words[0].intern(), transwords[j].intern(),
										1.0 / len);
							}
						} else {
							dict.incr(words[0].intern(), translation.intern(), 1);
						}
					}
				}
				LogInfo.logss("Dictionary loaded");
				return dict;
			} catch (IOException e) {
				LogInfo.error("Problem loading dictionary file: " + dictionary);
				return null;
			}
		}
	}

	private DistortionModel createDistortionModel(ModelT model) {
		DistortionModel distModel = null;
		if (model == ModelT.MODEL1) {
			distModel = new IBMModel1();
		} else if (model == ModelT.MODEL2) {
			distModel = new IBMModel2();
		} else if (model == ModelT.HMM) {
			StateMapper mapper = new EndsStateMapper();
			distModel = new StringDistanceModel(mapper);
		} else if (model == ModelT.SYNTACTIC) {
			StateMapper mapper = new SingleStateMapper();
			distModel = new TreeWalkModel(mapper);
		}
		return distModel;
	}

	private static Tree<String> flattenUnaries(Tree<String> nextTree) {
		return transformer.transformTree(nextTree);
	}

	/**
	 * A tree transformer to remove unary productions.
	 */
	private static Trees.TreeTransformer<String> transformer = new Trees.TreeTransformer<String>() {
		public Tree<String> transformTree(Tree<String> tree) {
			String label = tree.getLabel();
			if (tree.isLeaf()) {
				return new Tree<String>(label);
			}
			while (!tree.isPreTerminal() && tree.getChildren().size() == 1) {
				tree = tree.getChildren().get(0);
			}
			List<Tree<String>> children = tree.getChildren();
			List<Tree<String>> transformedChildren = new ArrayList<Tree<String>>();
			for (Tree<String> child : children) {
				Tree<String> transformedChild = transformTree(child);
				transformedChildren.add(transformedChild);
			}
			return new Tree<String>(tree.getLabel(), transformedChildren);
		}
	};

	/**
	 * A verification function for training pairs.
	 */
	private static boolean acceptPair(SentencePair pair) {
		if (pair.getEnglishTree() == null) return false; // TODO Make this more selective.

		Tree<String> newTree = flattenUnaries(pair.getEnglishTree());
		pair.setEnglishTree(newTree);
		List<String> yield = new ArrayList<String>();
		for (String w : pair.getEnglishTree().getYield()) {
			yield.add(w.toLowerCase().intern());
		}

		if (!yield.equals(pair.getEnglishWords())) {
			return false;
		}
		if(yield.size() > maxTrainingLength) return false;
		return true;
	}

	/**
	 * A verification function for the training regimen specified.
	 */
	private int getNumStagesAndCheckRegimen() {
		// Check input length consistency
		int k = forwardModels.size();
		sameCheck(k, reverseModels.size(),
				"ForwardModels and ReverseModels lengths differ");
		sameCheck(k, numIters.size(), "ForwardModels and NumIters lengths differ");
		sameCheck(k, trainingModes.size(),
				"ForwardModels and TrainingModes lengths differ");

		// Check training mode sequence consistency
		TrainMode prev = TrainMode.JOINT;
		for (int i = 0; i < k; i++) {
			TrainMode curr = trainingModes.get(i);
			boolean monoDirectional = false;
			if (prev == TrainMode.FORWARD) {
				monoDirectional = true;
				if (curr == TrainMode.REVERSE) {
					throw new InputMismatchException(
							"Reverse training follows forward training");
				}
			}
			if (prev == TrainMode.REVERSE) {
				monoDirectional = true;
				if (curr == TrainMode.FORWARD) {
					throw new InputMismatchException(
							"Forward training follows Reverse training");
				}
			}
			boolean biDirectional = curr == TrainMode.BOTH_INDEP
					|| curr == TrainMode.JOINT;
			if (monoDirectional && biDirectional) {
				throw new InputMismatchException(
						"bidirectional (INDEP/JOINT) training follows monodirectional training");
			}
		}

		return k;
	}

	private void sameCheck(int l1, int l2, String error) {
		if (l1 != l2)
			throw new InputMismatchException(error + ": (" + l1 + ", " + l2 + ")");
	}

	private Filter<SentencePair> getTestFilter() {
		return Filters.andFilter(getTrainingFilter(), new Filter<SentencePair>() {
			public boolean accept(SentencePair t) {
				return t.getAlignment() != null;
			}
		});
	}

	private Filter<SentencePair> getTrainingFilter() {
		if (englishTreesAreRequired()) {
			return new Filter<SentencePair>() {
				public boolean accept(SentencePair pair) {
					return Main.acceptPair(pair);
				}
			};
		} else {
			return Filters.acceptFilter(SentencePair.class);
		}
	}

	private boolean englishTreesAreRequired() {
		boolean required = false;
		for (ModelT model : forwardModels) {
			required = required || model == ModelT.SYNTACTIC;
		}
		return required;
	}

}
