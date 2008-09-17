/**
 * 
 */
package edu.berkeley.nlp.wordAlignment.distortion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import edu.berkeley.nlp.wa.basic.Pair;
import edu.berkeley.nlp.wa.syntax.Tree;
import edu.berkeley.nlp.wordAlignment.distortion.TreeWalkModel.TransType;

/**
 * Computes paths through the syntax tree from each pair of positions in a sentence.
 */
class SyntacticProfile {

	public static class Transition {

		TransType type;
		int position;
		int numPositions;
		int tagGroup;
		int prevPosition;

		private Transition(TransType type, int tagClass, int position, int numPositions) {
			this.type = type;
			this.tagGroup = tagClass;
			this.position = position;
			this.numPositions = numPositions;
		}

		private Transition(TransType type, int tagClass, int[] positionOfMany) {
			this(type, tagClass, positionOfMany[0], positionOfMany[1]);
		}

		public int getChange() {
			return position - prevPosition;
		}

		public String toString() {
			return type.toString() + "(" + tagGroup + "): " + getChange() + "/" + numPositions;
		}

		/**
		 * Whether we're moving from an established position or pushing down from the left.
		 * 
		 * @return
		 */
		public boolean isMove() {
			return prevPosition > -1;
		}

	}

	private static Map<Tree<String>, SyntacticProfile> cache = new IdentityHashMap<Tree<String>, SyntacticProfile>();

	Map<Integer, List<Pair<Tree<String>, int[]>>> pathsToLeaf;
	Map<Tree<String>, Integer> leafPositions;
	int[] tagClasses;
	double[][] transitionCostCache;
	Tree<String> tree; // For debugging
	List<Transition>[][] pathCache;
	private double[] transitionSums;

	@SuppressWarnings("unchecked")
	private SyntacticProfile(Tree<String> t) {
		tree = t;
		if (!tree.getLabel().startsWith("ROOT")) {
			tree = new Tree<String>("ROOT-" + t.getLabel(), t.getChildren());
		}

		// Map leaves to positions
		leafPositions = new IdentityHashMap<Tree<String>, Integer>();
		int pos = 0;
		for (Tree<String> node : t.getPreOrderTraversal()) {
			if (node.isPreTerminal()) {
				leafPositions.put(node, pos++);
			}
		}
		// Store paths to each leaf
		pathsToLeaf = new HashMap<Integer, List<Pair<Tree<String>, int[]>>>();
		createPathsToLeaf(t, new ArrayList<Pair<Tree<String>, int[]>>());
		int len = t.getYield().size();
		transitionCostCache = new double[len + 1][len + 1];
		pathCache = new List[len + 1][len + 1];
	}

	public static SyntacticProfile getSentenceProfile(Tree<String> t) {
		if(!TreeWalkModel.cacheTreePaths) return new SyntacticProfile(t);
		
		SyntacticProfile cached = cache.get(t);
		if (cached == null) {
			cached = new SyntacticProfile(t);
			cache.put(t, cached);
		}
		return cached;
	}

	/**
	 * Each step in a path is the root of a branch choice and the child selected,
	 * (0-indexed), along with the number of options.
	 * 
	 * Example: in the tree (ROOT (NP (DT The) (NN dog)) (VP (VBZ likes) (NP ...)))
	 * the path to the leaf dog would be:
	 * [(ROOT, 0, 1), (NP,1,2)]
	 * 
	 * @param root
	 * @param path
	 */
	private void createPathsToLeaf(Tree<String> root, List<Pair<Tree<String>, int[]>> path) {
		if (root.isPreTerminal()) {
			int[] nodeProfile = { 0, 1 };
			path.add(Pair.newPair(root, nodeProfile));
			pathsToLeaf.put(leafPositions.get(root), path);
		}
		int numChildren = root.getChildren().size();
		for (int i = 0; i < numChildren; i++) {
			List<Pair<Tree<String>, int[]>> childpath = new ArrayList<Pair<Tree<String>, int[]>>(
					path);
			Tree<String> child = root.getChildren().get(i);
			int[] nodeProfile = { i, numChildren };
			childpath.add(Pair.newPair(root, nodeProfile));
			createPathsToLeaf(child, childpath);
		}
	}

	List<Transition> getTransitionPath(int start, int end, TagMapper tagMapper) {
		List<Transition> transitions = new ArrayList<Transition>();

		List<Pair<Tree<String>, int[]>> startPath = pathsToLeaf.get(start);
		List<Pair<Tree<String>, int[]>> endPath = pathsToLeaf.get(end);

		if (start == end) {
			Pair<Tree<String>, int[]> step = startPath.get(startPath.size() - 1);
			int tag = tagMapper.getGroup(step.getFirst().getLabel());
			transitions.add(new Transition(TransType.STOP, tag, step.getSecond()));
			return transitions;
		}

		int root, prevPosition;

		if (startPath != null) { // startPath is null if start == -1
			root = 0;
			if (endPath != null) { // endPath is null if end == len(sent)
				for (Pair<Tree<String>, int[]> step : startPath) {
					if (step.getFirst() != endPath.get(root).getFirst()) {
						break;
					}
					root++;
				}
				root--; // Roll back one step to find the common root.
			}

			// Transitions to root
			for (int i = startPath.size() - 1; i > root; i--) {
				Pair<Tree<String>, int[]> step = startPath.get(i);
				int tag = tagMapper.getGroup(step.getFirst().getLabel());
				transitions.add(new Transition(TransType.POP, tag, step.getSecond()));
			}

			// Stop popping, conditioned on the root.
			Pair<Tree<String>, int[]> step = startPath.get(root);
			int tag = tagMapper.getGroup(step.getFirst().getLabel());
			prevPosition = step.getSecond()[0];
			if (endPath == null) { // Instead of stopping, the end of the sentence pops all the way.
				transitions.add(new Transition(TransType.POP, tag, step.getSecond()));
				return transitions;
			} else {
				transitions.add(new Transition(TransType.STOP, tag, step.getSecond()));
			}
		} else {
			prevPosition = -1;
			root = 0;
		}
		// Transitions to leaf
		if (endPath != null) {
			for (int i = root; i < endPath.size() - 1; i++) {
				Pair<Tree<String>, int[]> step = endPath.get(i);
				int tag = tagMapper.getGroup(step.getFirst().getLabel());
				Transition trans = new Transition(TransType.MOVEPUSH, tag, step.getSecond());
				trans.prevPosition = prevPosition;
				transitions.add(trans);
				prevPosition = -1;
			}
		}
		return transitions;
	}

	/**
	 * We condition only on whether we are popping from the ends of the constituent.
	 * 
	 * 0: Beginning
	 * 1: Middle
	 * 2: End
	 * 
	 * TODO Perhaps we should have "middle" contribute to the beginning and end as well.
	 * 
	 * @param t the transition
	 * @return
	 */
	int compressPopPosition(Transition t) {
		assert (TreeWalkModel.NUM_POP_POSITIONS == 2) : "Update pop positions to match this function.";
		if (t.position == t.numPositions - 1) {
			return 1;
		} else {
			return 0;
			//			return t.position == 0 ? 0 : 1;
		}
	}

	public void computeProfile() {
		if (transitionCostCache == null) return;

		int len = transitionCostCache.length;
		transitionSums = new double[len];
		for (int i = 0; i < len; i++) {
			for (int j = 0; j < transitionCostCache[i].length; j++) {
				transitionSums[i] += transitionCostCache[i][j];
			}
		}
	}
}