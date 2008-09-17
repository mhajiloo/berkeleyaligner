package edu.berkeley.nlp.wordAlignment.distortion;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import edu.berkeley.nlp.wa.basic.LogInfo;
import edu.berkeley.nlp.wa.basic.NumUtils;
import edu.berkeley.nlp.wa.basic.Option;
import edu.berkeley.nlp.wa.mt.SentencePair;
import edu.berkeley.nlp.wa.syntax.Tree;
import edu.berkeley.nlp.wa.util.ArrayUtil;
import edu.berkeley.nlp.wordAlignment.TrainingCache;
import edu.berkeley.nlp.wordAlignment.distortion.SyntacticProfile.Transition;

/**
 * The TreeWalk distortion model is the syntax sensitive model described in 
 * detail in Tailoring Word Alignments to Syntactic MT (ACL07).
 * 
 * This version supports two parameterizations; Moves either share parameters
 * with pushes or they don't.  If they do, all parameters are stored as move
 * parameters and push parameters are ignored.
 */
public class TreeWalkModel extends BucketModel {

	@Option(gloss = "Separate parameters for moving and pushing.")
	public static boolean usePushProbabilities = true;
	@Option(gloss = "Whether to condition distortion on the tag types.")
	public static boolean conditionOnTag = true;
	@Option(gloss = "Whether to cache paths through trees (uses lots of memory; faster).")
	public static boolean cacheTreePaths = false;

	public enum TransType {
		POP, STOP, MOVE, MOVEPUSH
	};

	static final int NUM_POP_POSITIONS = 2;

	private class TreeWalkParameters implements DistortionParameters {

		private static final long serialVersionUID = 1L;
		private SyntacticProfile prof;

		/********************/
		/* Tree Processing  */
		/********************/
		public TreeWalkParameters(SentencePair sp) {
			Tree<String> t = sp.getEnglishTree();
			if (t == null) {
				throw new RuntimeException("Missing tree for a sentence.");
			}
			prof = SyntacticProfile.getSentenceProfile(t);
		}

		/**************
		 * Evaluation *
		 **************/

		public void add(int state, int h, int i, int I, double count) {
			// Find all transitions on the path from h to i and add to those parameters.
			List<Transition> path = prof.pathCache[h + 1][i];
			path = (path == null) ? prof.getTransitionPath(h, i, tagMapper) : path;
			for (Transition t : path) {
				if (t.type == TransType.POP) {
					popProbs[state][t.tagGroup][prof.compressPopPosition(t)][0] += count;
				} else if (t.type == TransType.STOP) {
					popProbs[state][t.tagGroup][prof.compressPopPosition(t)][1] += count;
				} else if (t.type == TransType.MOVEPUSH) {
					int d = t.getChange();
					if (t.isMove() || !usePushProbabilities) {
						// Add mass to the appropriate MOVE bucket
						if (d >= windowSize) {
							moveProbs[state][t.tagGroup][2 * windowSize] += count;
						} else if (d <= -windowSize) {
							moveProbs[state][t.tagGroup][0] += count;
						} else {
							moveProbs[state][t.tagGroup][windowSize + d] += count;
						}
					} else {
						// Add mass to the appropriate PUSH bucket
						if (d >= windowSize) {
							pushProbs[state][t.tagGroup][windowSize] += count;
						} else {
							pushProbs[state][t.tagGroup][d] += count;
						}
					}
				}
			}
		}

		public double get(int state, int h, int i, int I) {
			double cached = prof.transitionCostCache[h + 1][i];
			if (cached > 0) {
				return cached;
			}
			List<Transition> path = prof.getTransitionPath(h, i, tagMapper);
			double prob = 1.0;
			for (Transition t : path) {
				prob *= computeTransitionProbability(state, t);
			}
			if (h == -1 && i == I) {
				prob = 0;
			}
			prof.transitionCostCache[h + 1][i] = prob;
			prof.pathCache[h + 1][i] = path;
			return prob;
			//		return Math.sqrt(prob);
		}

		/**
		 * The cost of a transition depends upon the nature of the transition.
		 * 
		 * @param state
		 * @param t
		 * @return
		 */
		private double computeTransitionProbability(int state, Transition t) {
			if (t.type == TransType.POP) {
				return popProbs[state][t.tagGroup][prof.compressPopPosition(t)][0];
			} else if (t.type == TransType.STOP) {
				return popProbs[state][t.tagGroup][prof.compressPopPosition(t)][1];
			} else if (t.type == TransType.MOVEPUSH) {
				return getMoveOrPushProbability(state, t); // TODO fill in move probability
			} else { // TransitionType.PUSH
				return 0;
			}
		}

		/**
		 * Move probabilities are like HMM distortion probabilities based on
		 * signed distance, but bucketed into a multinomial.
		 * 
		 * @param state
		 * @param t
		 * @return
		 */
		private double getMoveOrPushProbability(int state, Transition t) {
			int tagGroup = t.tagGroup;
			int d = t.getChange();
			int h = t.prevPosition;
			int I = t.numPositions;
			int div;

			if (t.isMove() || !usePushProbabilities) {
				// Treat transition as a move (rather than a push)
				if (d <= -windowSize) {
					d = -windowSize;
					div = (h - windowSize) - 0 + 1;
				} else if (d >= windowSize) {
					d = windowSize;
					div = I - (h + windowSize);
				} else
					div = 1;
				double norm = computeMoveNorm(state, tagGroup, h, I);
				if (norm == 0) return 0;
				assert div > 0 : String.format(
						"getDDiv: state=%d, h=%d, d=%d, I=%d: div=%d, norm=%f/%f", state, h, d, I,
						div, norm, moveSums[state][tagGroup][2 * windowSize + 1]);
				return moveProbs[state][tagGroup][d + windowSize] / div / norm;
			} else {
				// Transition is a push; use push probabilities.
				if (d >= windowSize) {
					d = windowSize;
					div = I - (h + windowSize);
				} else
					div = 1;
				assert (h == -1) : String.format("h is not -1: %d", h);
				double norm = computePushNorm(state, tagGroup, I);
				if (norm == 0) return 0;
				assert div > 0 : String.format(
						"getDDiv: state=%d, h=%d, d=%d, I=%d: div=%d, norm=%f/%f", state, h, d, I,
						div, norm, pushSums[state][tagGroup][windowSize + 1]);
				return pushProbs[state][tagGroup][d] / div / norm;
			}
		}

	}

	private static final long serialVersionUID = 1L;

	// For each string position, the path through the tree to get there. 
	// Each node is a {node, {childIndex, numChildren}} structure.
	TagMapper tagMapper;

	private static TagMapper getRootTagMapper() {
		return new TagMapper() {
			private static final long serialVersionUID = 1L;

			public int getGroup(String tag) {
				return (tag.startsWith("ROOT")) ? 0 : 1;
			}

			public int numGroups() {
				return 2;
			}
		};
	}

	private static TagMapper getClusteredTagMapper() {
		return new TagMapper() {
			private static final long serialVersionUID = 1L;
			Map<String, Integer> tagMap = new HashMap<String, Integer>();
			{
				tagMap.put("S", 1);
				tagMap.put("SBAR", 1);
				tagMap.put("NP", 2);
				tagMap.put("VP", 3);
				tagMap.put("PP", 4);
				tagMap.put("SQ", 5);
				tagMap.put("SBARQ", 5);
				tagMap.put("ADJP", 6);
				tagMap.put("NN", 7);
				tagMap.put("NNS", 7);
				tagMap.put("NNP", 8);
			}
			int numGroups = 0;

			public int numGroups() {
				if (numGroups == 0) {
					numGroups = new HashSet<Integer>(tagMap.values()).size() + 2;
				}
				return numGroups;
			}

			public int getGroup(String tag) {
				if (tag.startsWith("ROOT")) return 0;
				Integer i = tagMap.get(tag);
				return (i == null) ? numGroups() - 1 : i;
			}
		};
	}

	/********************/
	/* Model parameters */
	/********************/

	// Pop probabilities: State x Node_Type x Constituent_Pos x {POP, STOP}
	double[][][][] popProbs;
	// Move probabilities: State x Node_Type x Move_Distance (signed window) 
	double[][][] moveProbs;
	// Move sums: State x Node_Type x Move_Distance (signed window) 
	double[][][] moveSums;
	// Push probabilities (might not be necessary)
	double[][][] pushProbs;
	// Push probabilities (might not be necessary)
	double[][][] pushSums;

	private int numTagGroups;

	public TreeWalkModel(StateMapper stateMapper) {
		super(stateMapper);
		this.tagMapper = (conditionOnTag) ? getClusteredTagMapper() : getRootTagMapper();
		numTagGroups = tagMapper.numGroups();
		alloc();
	}

	private void alloc() {
		if (numStates > 0) {
			popProbs = new double[numStates][numTagGroups][NUM_POP_POSITIONS][2];
			moveProbs = new double[numStates][numTagGroups][2 * windowSize + 1];
			moveSums = new double[numStates][numTagGroups][2 * windowSize + 1 + 1];

			if (usePushProbabilities) {
				pushProbs = new double[numStates][numTagGroups][windowSize + 1];
				pushSums = new double[numStates][numTagGroups][windowSize + 1 + 1];
			}
		}
	}

	protected double computeMoveNorm(int state, int tagGroup, int h, int I) {
		int mind = Math.max(0 - h, -windowSize);
		int maxd = Math.min(I - h - 1, windowSize); // Position < I in this case.
		assert mind <= maxd : String.format("mind=%d,maxd=%d", mind, maxd);
		// Return probs[state][mind] + ... + probs[state][maxd]
		return moveSums[state][tagGroup][maxd + windowSize + 1]
				- moveSums[state][tagGroup][mind + windowSize];
	}

	protected double computePushNorm(int state, int tagGroup, int I) {
		int mind = 1;
		int maxd = Math.min(I, windowSize); // Move <= I in this case b/c start = -1.
		assert mind <= maxd : String.format("mind=%d,maxd=%d", mind, maxd);
		// Return probs[state][mind] + ... + probs[state][maxd]
		return pushSums[state][tagGroup][maxd + 1];
	}

	private void readObject(ObjectInputStream in) throws IOException,
			ClassNotFoundException {
		popProbs = (double[][][][]) in.readObject();
		moveProbs = (double[][][]) in.readObject();
		stateMapper = (StateMapper) in.readObject();
		tagMapper = (TagMapper) in.readObject(); 
		moveSums = null;
		pushSums = null;

		// Check for push probabilities (not terribly safe)
		try {
			pushProbs = (double[][][]) in.readObject();
			usePushProbabilities = true;
		} catch (IOException e) {
			// Assume that the exception arose because no further data was available.
			usePushProbabilities = false;
		}

		// Complete object creation
		numStates = popProbs.length;
		numTagGroups = tagMapper.numGroups();
		computeSums();
	}

	private void writeObject(ObjectOutputStream out) throws IOException {
		out.writeObject(popProbs);
		out.writeObject(moveProbs);
		out.writeObject(stateMapper);
		out.writeObject(tagMapper);
		if (usePushProbabilities) {
			out.writeObject(pushProbs);
		}
	}

	public DistortionModel copy() {
		TreeWalkModel model = new TreeWalkModel(stateMapper);
		if (popProbs != null && moveProbs != null) {
			model.popProbs = ArrayUtil.copy(popProbs);
			model.moveProbs = ArrayUtil.copy(moveProbs);
			model.moveSums = ArrayUtil.copy(moveSums);
			if (usePushProbabilities) {
				model.pushProbs = ArrayUtil.copy(pushProbs);
				model.pushSums = ArrayUtil.copy(pushSums);
			}
		}
		return model;
	}

	public void dump(PrintWriter out) {
		for (int state = 0; state < numStates; state++) {
			for (int tag = 0; tag < numTagGroups; tag++) {
				// POP Probabilities
				for (int pos = 0; pos < NUM_POP_POSITIONS; pos++) {
					out.print("State: " + state + " Tag: " + tag + " Pos: " + pos + " POP: \t");
					out.println(popProbs[state][tag][pos][0]);
					out.print("State: " + state + " Tag: " + tag + " Pos: " + pos + " STOP:\t");
					out.println(popProbs[state][tag][pos][1]);
				}
				// MOVE Probabilities
				for (int i = -windowSize; i <= windowSize; i++) {
					String s;
					if (i == -windowSize)
						s = "<= " + i;
					else if (i == windowSize)
						s = ">= " + i;
					else
						s = "= " + i;
					out.print("State: " + state + " Tag: " + tag + "MOVE: ");
					out.println(s + "\t" + moveProbs[state][tag][i + windowSize]);
				}
				// PUSH Probabilities, if they exist...
				if (usePushProbabilities) {
					for (int i = 0; i <= windowSize; i++) {
						String s;
						if (i == windowSize)
							s = ">= " + i;
						else
							s = "= " + i;
						out.print("State: " + state + " Tag: " + tag + "PUSH: ");
						out.println(s + "\t" + pushProbs[state][tag][i]);
					}
				}
			}
		}
	}

	public StateMapper getStateMapper() {
		return stateMapper;
	}

	public void computeSums() {
		if (moveSums == null)
			moveSums = new double[numStates][numTagGroups][2 * windowSize + 1 + 1];
		for (int state = 0; state < numStates; state++) {
			for (int g = 0; g < numTagGroups; g++) {
				Arrays.fill(moveSums[state][g], 0);
				for (int k = 0; k < moveProbs[state][g].length; k++)
					moveSums[state][g][k + 1] = moveSums[state][g][k] + moveProbs[state][g][k];
			}
		}
		if (pushProbs != null) {
			if (pushSums == null) {
				pushSums = new double[numStates][numTagGroups][windowSize + 1 + 1];
			}
			for (int state = 0; state < numStates; state++) {
				for (int g = 0; g < numTagGroups; g++) {
					Arrays.fill(pushSums[state][g], 0);
					for (int k = 0; k < pushProbs[state][g].length; k++)
						pushSums[state][g][k + 1] = pushSums[state][g][k] + pushProbs[state][g][k];
				}
			}
		}
	}

	public void incrAll(DistortionModel other) {
	// TODO Auto-generated method stub

	}

	public void initUniform() {
		if (popProbs == null || moveProbs == null) {
			return;
		}
		for (int i = 0; i < numStates; i++) {
			for (int j = 0; j < numTagGroups; j++) {
				for (int k = 0; k < NUM_POP_POSITIONS; k++) {
					Arrays.fill(popProbs[i][j][k], 0.5);
				}
				popProbs[i][0][1][0] = 0.999;
				popProbs[i][0][1][1] = 0.001;
				Arrays.fill(moveProbs[i][j], 1.0 / (moveProbs[i][j].length - 1));
				moveProbs[i][j][windowSize] = 0;
				if (usePushProbabilities) {
					Arrays.fill(pushProbs[i][j], 1.0 / (pushProbs[i][j].length - 1));
					pushProbs[i][j][0] = 0;
				}
			}
		}
		computeSums();
	}

	public void initZero() {
		if (popProbs == null || moveProbs == null) {
			return;
		}
		for (int i = 0; i < numStates; i++) {
			for (int j = 0; j < numTagGroups; j++) {
				for (int k = 0; k < NUM_POP_POSITIONS; k++) {
					Arrays.fill(popProbs[i][j][k], 0.0);
				}
				Arrays.fill(moveProbs[i][j], 0.0);
				if (usePushProbabilities) {
					Arrays.fill(pushProbs[i][j], 0.0);
				}
			}
		}
		computeSums();
	}

	public void normalize() {
		if (popProbs == null || moveProbs == null) {
			return;
		}
		for (int i = 0; i < numStates; i++) {
			for (int j = 0; j < numTagGroups; j++) {
				for (int k = 0; k < NUM_POP_POSITIONS; k++) {
					if (!NumUtils.normalizeForce(popProbs[i][j][k]))
						LogInfo
								.warning("normalize(): popProbs(state=%d) has sum 0, using uniform", i);
				}
				if (!NumUtils.normalizeForce(moveProbs[i][j]))
					LogInfo.warning("normalize(): moveProbs(state=%d) has sum 0, using uniform", i);
				if (usePushProbabilities) {
					if (!NumUtils.normalizeForce(pushProbs[i][j]))
						LogInfo.warning("normalize(): pushProbs(state=%d) has sum 0, using uniform",
								i);
				}
			}
		}
		computeSums();
	}

	public TrainingCache getTrainingCache() {
		return new HMMTrainingCache(-1);
		// No caching because every distortion is different
	}

	public DistortionParameters getDistortionParameters(SentencePair pair) {
		return new TreeWalkParameters(pair);
	}

}
