package edu.berkeley.nlp.syntax;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.berkeley.nlp.util.Method;

/**
 * Represent linguistic trees, with each node consisting of a label and a list
 * of children.
 * 
 * @author Dan Klein
 * 
 * Added function to get a map of subtrees to constituents.
 */
public class Tree<L> implements Serializable, Iterable<Tree<L>> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	L label;
	List<Tree<L>> children;

	public void setChildren(List<Tree<L>> c) {
		this.children = c;
	}

	public List<Tree<L>> getChildren() {
		return children;
	}

	public L getLabel() {
		return label;
	}

	public boolean isLeaf() {
		return getChildren().isEmpty();
	}

	public boolean isPreTerminal() {
		return getChildren().size() == 1 && getChildren().get(0).isLeaf();
	}

	public List<L> getYield() {
		List<L> yield = new ArrayList<L>();
		appendYield(this, yield);
		return yield;
	}

	public Map<Tree<L>, Constituent<L>> getConstituents() {
		Map<Tree<L>, Constituent<L>> constituents = new HashMap<Tree<L>, Constituent<L>>();
		appendConstituent(this, constituents, 0);
		return constituents;
	}

	private static <L> int appendConstituent(Tree<L> tree,
			Map<Tree<L>, Constituent<L>> constituents, int index) {
		if (tree.isLeaf()) {
			Constituent<L> c = new Constituent<L>(tree.getLabel(), index, index);
			constituents.put(tree, c);
			return 1; // Length of a leaf constituent
		} else {
			int nextIndex = index;
			for (Tree<L> kid : tree.getChildren()) {
				nextIndex += appendConstituent(kid, constituents, nextIndex);
			}
			Constituent<L> c = new Constituent<L>(tree.getLabel(), index, nextIndex - 1);
			constituents.put(tree, c);
			return nextIndex - index; // Length of a leaf constituent
		}
	}

	public List<Tree<L>> getTerminals() {
		List<Tree<L>> yield = new ArrayList<Tree<L>>();
		appendTerminals(this, yield);
		return yield;
	}

	private static <L> void appendTerminals(Tree<L> tree, List<Tree<L>> yield) {
		if (tree.isLeaf()) {
			yield.add(tree);
			return;
		}
		for (Tree<L> child : tree.getChildren()) {
			appendTerminals(child, yield);
		}
	}

	/**
	 * Clone the structure of the tree. Unfortunately, the new labels are copied
	 * by reference from the current tree.
	 * 
	 * @return
	 */
	public Tree<L> shallowClone() {
		ArrayList<Tree<L>> newChildren = new ArrayList<Tree<L>>(children.size());
		for (Tree<L> child : children) {
			newChildren.add(child.shallowClone());
		}
		return new Tree<L>(label, newChildren);
	}

	private static <L> void appendYield(Tree<L> tree, List<L> yield) {
		if (tree.isLeaf()) {
			yield.add(tree.getLabel());
			return;
		}
		for (Tree<L> child : tree.getChildren()) {
			appendYield(child, yield);
		}
	}

	public List<L> getPreTerminalYield() {
		List<L> yield = new ArrayList<L>();
		appendPreTerminalYield(this, yield);
		return yield;
	}

	private static <L> void appendPreTerminalYield(Tree<L> tree, List<L> yield) {
		if (tree.isPreTerminal()) {
			yield.add(tree.getLabel());
			return;
		}
		for (Tree<L> child : tree.getChildren()) {
			appendPreTerminalYield(child, yield);
		}
	}

	public List<Tree<L>> getPreOrderTraversal() {
		ArrayList<Tree<L>> traversal = new ArrayList<Tree<L>>();
		traversalHelper(this, traversal, true);
		return traversal;
	}

	public List<Tree<L>> getPostOrderTraversal() {
		ArrayList<Tree<L>> traversal = new ArrayList<Tree<L>>();
		traversalHelper(this, traversal, false);
		return traversal;
	}

	private static <L> void traversalHelper(Tree<L> tree, List<Tree<L>> traversal,
			boolean preOrder) {
		if (preOrder)
			traversal.add(tree);
		for (Tree<L> child : tree.getChildren()) {
			traversalHelper(child, traversal, preOrder);
		}
		if (!preOrder)
			traversal.add(tree);
	}

	public int getDepth() {
		int maxDepth = 0;
		for (Tree<L> child : children) {
			int depth = child.getDepth();
			if (depth > maxDepth)
				maxDepth = depth;
		}
		return maxDepth + 1;
	}

	public List<Tree<L>> getAtDepth(int depth) {
		List<Tree<L>> yield = new ArrayList<Tree<L>>();
		appendAtDepth(depth, this, yield);
		return yield;
	}

	private static <L> void appendAtDepth(int depth, Tree<L> tree, List<Tree<L>> yield) {
		if (depth < 0)
			return;
		if (depth == 0) {
			yield.add(tree);
			return;
		}
		for (Tree<L> child : tree.getChildren()) {
			appendAtDepth(depth - 1, child, yield);
		}
	}

	public void setLabel(L label) {
		this.label = label;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		toStringBuilder(sb);
		return sb.toString();
	}

	public void toStringBuilder(StringBuilder sb) {
		if (!isLeaf())
			sb.append('(');
		if (getLabel() != null) {
			sb.append(getLabel());
		}
		if (!isLeaf()) {
			for (Tree<L> child : getChildren()) {
				sb.append(' ');
				child.toStringBuilder(sb);
			}
			sb.append(')');
		}
	}

	public Tree(L label, List<Tree<L>> children) {
		this.label = label;
		this.children = children;
	}

	public Tree(L label) {
		this.label = label;
		this.children = Collections.emptyList();
	}

	/**
	 * Get the set of all subtrees inside the tree by returning a tree rooted at
	 * each node. These are <i>not</i> copies, but all share structure. The tree
	 * is regarded as a subtree of itself.
	 * 
	 * @return the <code>Set</code> of all subtrees in the tree.
	 */
	public Set<Tree<L>> subTrees() {
		return (Set<Tree<L>>) subTrees(new HashSet<Tree<L>>());
	}

	/**
	 * Get the list of all subtrees inside the tree by returning a tree rooted at
	 * each node. These are <i>not</i> copies, but all share structure. The tree
	 * is regarded as a subtree of itself.
	 * 
	 * @return the <code>List</code> of all subtrees in the tree.
	 */
	public List<Tree<L>> subTreeList() {
		return (List<Tree<L>>) subTrees(new ArrayList<Tree<L>>());
	}

	/**
	 * Add the set of all subtrees inside a tree (including the tree itself) to
	 * the given <code>Collection</code>.
	 * 
	 * @param n
	 *          A collection of nodes to which the subtrees will be added
	 * @return The collection parameter with the subtrees added
	 */
	public Collection<Tree<L>> subTrees(Collection<Tree<L>> n) {
		n.add(this);
		List<Tree<L>> kids = getChildren();
		for (Tree<L> kid : kids) {
			kid.subTrees(n);
		}
		return n;
	}

	/**
	 * Returns an iterator over the nodes of the tree. This method implements the
	 * <code>iterator()</code> method required by the <code>Collections</code>
	 * interface. It does a preorder (children after node) traversal of the tree.
	 * (A possible extension to the class at some point would be to allow
	 * different traversal orderings via variant iterators.)
	 * 
	 * @return An iterator over the nodes of the tree
	 */
	public Iterator<Tree<L>> iterator() {
		return new TreeIterator();
	}

	private class TreeIterator implements Iterator<Tree<L>> {

		private List<Tree<L>> treeStack;

		private TreeIterator() {
			treeStack = new ArrayList<Tree<L>>();
			treeStack.add(Tree.this);
		}

		public boolean hasNext() {
			return (!treeStack.isEmpty());
		}

		public Tree<L> next() {
			int lastIndex = treeStack.size() - 1;
			Tree<L> tr = treeStack.remove(lastIndex);
			List<Tree<L>> kids = tr.getChildren();
			// so that we can efficiently use one List, we reverse them
			for (int i = kids.size() - 1; i >= 0; i--) {
				treeStack.add(kids.get(i));
			}
			return tr;
		}

		/**
		 * Not supported
		 */
		public void remove() {
			throw new UnsupportedOperationException();
		}

	}

	/**
	 * Applies a transformation to all labels in the tree and 
	 * returns the resulting tree.
	 * 
	 * @param <O> Output type of the transformation
	 * @param trans The transformation to apply
	 * @return Transformed tree
	 */
	public <O> Tree<O> transformNodes(Method<L, O> trans) {
		ArrayList<Tree<O>> newChildren = new ArrayList<Tree<O>>(children.size());
		for (Tree<L> child : children) {
			newChildren.add(child.transformNodes(trans));
		}
		return new Tree<O>(trans.call(label), newChildren);
	}

}
