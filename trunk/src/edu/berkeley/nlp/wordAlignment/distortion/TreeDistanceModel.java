package edu.berkeley.nlp.wordAlignment.distortion;

import java.util.ArrayList;
import java.util.List;

import edu.berkeley.nlp.wa.mt.SentencePair;
import edu.berkeley.nlp.wa.syntax.Tree;
import edu.berkeley.nlp.wordAlignment.TrainingCache;

/**
 * The baseline tree-based distortion model assigns a position to each
 * word in the English tree according to its order in a depth-first 
 * left-branching traversal of the tree.  Distances between words are
 * measured according to these positions.  
 * 
 * A standard window-based HMM distortion model is fitted to these positions.
 */
public class TreeDistanceModel extends BucketModel {

	private class TreeDistanceParameters implements DistortionParameters {

		private static final long serialVersionUID = 1L;
		private List<Integer> treePositions;

		public TreeDistanceParameters(SentencePair pair) {
			// Create map of string positions to tree order positions
			Tree<String> t = pair.getEnglishTree();
			if (t == null) {
				throw new RuntimeException("No tree is available for the English sentence.");
			}
			treePositions = new ArrayList<Integer>();
			treePositions.add(-1);
			totalNodes = createTreePositions(t, 0, treePositions);
			treePositions.add(totalNodes);
		}

		private int createTreePositions(Tree<String> tree, int rootpos,
				List<Integer> treePositions) {
			if (tree.isLeaf() || tree.getChildren().get(0).isLeaf()) { // Collapse pre-terminals
				treePositions.add(rootpos);
				return rootpos + 1;
			} else {
				int childpos = rootpos + 1;
				for (Tree<String> child : tree.getChildren()) {
					childpos = createTreePositions(child, childpos, treePositions);
				}
				return childpos;
			}
		}

		//		int pos = treePhositions.get(i + 1);
		//		int mind = Math.max(0 - pos, -windowSize); // lower distortion bound
		//		int maxd = Math.min(totalNodes - pos, windowSize); // upper distortion bound
		//		assert mind <= maxd : String.format("mind=%d,maxd=%d", mind, maxd);
		//		// Return probs[state][mind] + ... + probs[state][maxd]
		//		return sums[state][maxd + windowSize + 1] - sums[state][mind + windowSize];
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
			int div = 0;
			if (d <= -windowSize) {
				d = -windowSize;
				div = getSubWindowCount(h);
			} else if (d >= windowSize) {
				d = windowSize;
				div = getSuperWindowCount(h, I);
			} else {
				div = 1;
			}
			double norm = 1;
			if (norm == 0) {
				return 0;
			}
			assert div > 0 : String.format(
					"getDDiv: state=%d, d=%d, h=%d, i=%d, I=%d: div=%d, norm=%f/%f", state, d, h,
					i, I, div, norm, sums[state][2 * windowSize + 1]);
			double prob = probs[state][d + windowSize] / div / norm;
			return prob;
		}

		private int getSubWindowCount(int h) {
			int max = treePositions.get(h + 1) - windowSize;
			int count = 0;
			while (treePositions.get(count) <= max) {
				count++;
			}
			return count;
		}

		private int getSuperWindowCount(int h, int I) {
			int min = treePositions.get(h + 1) + windowSize;
			int count = 0;
			while (treePositions.get(I - count + 1) >= min) {
				count++;
			}
			return count;
		}

		public void add(int state, int h, int i, int I, double count) {
			// dbg("DistortProbTable.add state=%d, %d -> %d: %f", state, h, i, count);
			int d = getDistance(i, h);
			if (d < -windowSize)
				d = -windowSize;
			else if (d > windowSize) d = windowSize;
			// Even if we are using a scaled version of the parameter probs[state][...]
			// by the number of divisions, the maximum likelihood estimate of that
			// parameter does not scale the count.
			probs[state][d + windowSize] += count; // /d_div.second;
		}

		int getDistance(int i, int h) {
			return treePositions.get(i + 1) - treePositions.get(h + 1);
		}

	}

	public DistortionParameters getDistortionParameters(SentencePair pair) {
		return new TreeDistanceParameters(pair);
	}

	static final long serialVersionUID = 42;

	private int totalNodes;

	// sums[state][k] = probs[state][0] + ... + probs[state][k-1]

	public TreeDistanceModel(StateMapper stateMapper) {
		super(stateMapper);
	}

	public BucketModel copy() {
		BucketModel model = new TreeDistanceModel(stateMapper);
		model.set(this);
		return model;
	}

	public TrainingCache getTrainingCache() {
		return new HMMTrainingCache(-1);
	}

}
