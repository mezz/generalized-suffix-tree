/*
 * Copyright 2012 Alessandro Bahgat Shehata
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.mezzdev.suffixtree;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import javax.annotation.Nullable;

/**
 * A Generalized Suffix Tree, based on the Ukkonen's paper
 * <a href="http://www.cs.helsinki.fi/u/ukkonen/SuffixT1withFigs.pdf">"On-line construction of suffix trees"</a>
 * <p>
 * Allows for fast storage and fast(er) retrieval by creating a tree-based index out of a set of strings.
 * Unlike common suffix trees, which are generally used to build an index out of one (very) long string,
 * a Generalized Suffix Tree can be used to build an index over many strings.
 * <p>
 * Its main operations are put and search:
 * Put adds the given key to the index, allowing for later retrieval of the given value.
 * Search can be used to retrieve the set of all the values that were put in the index with keys that contain a given input.
 * <p>
 * In particular, after put(K, V), search(H) will return a set containing V for any string H that is substring of K.
 * <p>
 * The overall complexity of the retrieval operation (search) is O(m) where m is the length of the string to search within the index.
 * <p>
 * Although the implementation is based on the original design by Ukkonen, there are a few aspects where it differs significantly.
 * <p>
 * The tree is composed of a set of nodes and labeled edges. The labels on the edges can have any length as long as it's greater than 0.
 * The only constraint is that no two edges going out from the same node will start with the same character.
 * <p>
 * Because of this, a given (startNode, stringSuffix) pair can denote a unique path within the tree, and it is the path (if any) that can be
 * composed by sequentially traversing all the edges (e1, e2, ...) starting from startNode such that (e1.label + e2.label + ...) is equal
 * to the stringSuffix.
 * See the search method for details.
 * <p>
 * The union of all the edge labels from the root to a given leaf node denotes the set of the strings explicitly contained within the GST.
 * In addition to those Strings, there are a set of different strings that are implicitly contained within the GST, and it is composed of
 * the strings built by concatenating e1.label + e2.label + ... + $end, where e1, e2, ... is a proper path and $end is prefix of any of
 * the labels of the edges starting from the last node of the path.
 * <p>
 * This kind of "implicit path" is important in the testAndSplit method.
 */
public class GeneralizedSuffixTree<T> {
	/**
	 * Marker stored in {@link #refNodesByKey} after the first insertion of a key.
	 * <p>
	 * The next insertion of the same key still has to run the normal construction path, because intervening different
	 * keys may have split edges. That replay discovers the exact nodes that need direct updates, and replaces this
	 * marker with a cached node array for later identical keys.
	 */
	private static final Object SEEN_KEY = new Object();
	/**
	 * The root of the suffix tree
	 */
	private final RootNode<T> root = new RootNode<>();
	/**
	 * The last leaf that was added during the update operation
	 */
	private Node<T> activeLeaf = root;
	/**
	 * Reusable result object for the top-level active point returned by {@link #update(Node, SubString, char, SubString, Object, List, ActivePoint)}.
	 * This field exists to avoid allocating one short-lived {@code Pair<Node, SubString>} for every character added to
	 * the tree. The tree is mutable and not thread-safe, so this scratch object must only be used during one synchronous
	 * {@link #put(String, Object)} call and must be read only after {@code update} has written it.
	 */
	private final ActivePoint<T> activePointResult = new ActivePoint<>();
	/**
	 * Reusable scratch result for nested {@link #canonize(Node, SubString, ActivePoint)} calls.
	 * This is intentionally separate from {@link #activePointResult}, because {@code update} and {@code testAndSplit}
	 * need temporary canonized active points without overwriting the caller-visible result.
	 */
	private final ActivePoint<T> canonizeResult = new ActivePoint<>();
	/**
	 * Reusable result for {@link #testAndSplit(Node, SubString, char, SubString, Object, List, SplitResult, ActivePoint)}.
	 * This avoids allocating one short-lived {@code Pair<Boolean, Node>} per split test in the construction hot path.
	 */
	private final SplitResult<T> splitResult = new SplitResult<>();
	/**
	 * Tracks repeated-key replay state.
	 * <p>
	 * A key maps to {@link #SEEN_KEY} after its first insertion, then to a {@code Node<T>[]} that is not mutated after
	 * the first repeated insertion records exactly which nodes received the value. Keeping both states in one map avoids
	 * a separate seen-key set and lets hot repeated keys reach the cached direct-update path with one hash lookup.
	 */
	private final Map<String, Object> refNodesByKey = new HashMap<>();
	/**
	 * Searches for the given word within the GST.
	 * <p>
	 * Gets all the results for which the key contains the {@code word} that was
	 * supplied as input.
	 *
	 * @param word            the key to search for
	 * @param resultsConsumer receives the results of the search
	 */
	public void getSearchResults(String word, Consumer<Collection<T>> resultsConsumer) {
		Objects.requireNonNull(word, "word");
		Objects.requireNonNull(resultsConsumer, "resultsConsumer");

		Node<T> currentNode = root;
		SubString wordSubString = new SubString(word);

		while (!wordSubString.isEmpty()) {
			// follow the edge corresponding to this char
			Node<T> currentEdge = currentNode.getEdge(wordSubString);
			if (currentEdge == null) {
				// there is no edge starting with this char
				return;
			}

			int lenToMatch = Math.min(wordSubString.length(), currentEdge.length());
			if (!currentEdge.startsWith(wordSubString, lenToMatch)) {
				// the label on the edge does not correspond to the one in the string to search
				return;
			}

			currentNode = currentEdge;
			if (lenToMatch == wordSubString.length()) {
				// we found the edge we're looking for
				currentNode.getData(resultsConsumer);
				return;
			}

			// advance to next node
			wordSubString = wordSubString.subSequence(lenToMatch);
		}
	}

	/**
	 * Gets the search results for the given token.
	 *
	 * @param word the search token to search for
	 * @return an identity set of all the search results
	 */
	public Set<T> getSearchResults(String word) {
		Set<T> results = Collections.newSetFromMap(new IdentityHashMap<>());
		getSearchResults(word, results::addAll);
		return results;
	}

	public void getAllElements(Consumer<Collection<T>> resultsConsumer) {
		Objects.requireNonNull(resultsConsumer, "resultsConsumer");
		root.getData(resultsConsumer);
	}

	/**
	 * Gets all values in this tree.
	 *
	 * @return an identity set of all indexed values
	 */
	public Set<T> getAllElements() {
		Set<T> results = Collections.newSetFromMap(new IdentityHashMap<>());
		getAllElements(results::addAll);
		return results;
	}

	/**
	 * Adds the specified {@code value} to the GST under the given {@code key}.
	 *
	 * @param key   the string key that will be added to the index
	 * @param value the value that will be added
	 */
	public void put(String key, T value) {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(value, "value");
		if (key.isEmpty()) {
			return;
		}

		putIntoTree(key, value);
	}

	public boolean remove(String key, T value) {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(value, "value");
		if (key.isEmpty()) {
			return false;
		}

		boolean removed = false;
		SubString keyString = new SubString(key);
		for (int i = 0; i < keyString.length(); i++) {
			removed |= removeData(keyString.subSequence(i), value);
		}
		return removed;
	}

	private void putIntoTree(String key, T value) {
		Object keyState = refNodesByKey.get(key);
		if (keyState instanceof Node<?>[]) {
			Node<T>[] cachedRefNodes = cachedRefNodes(keyState);
			if (cachedRefNodes.length == 0) {
				return;
			}
			if (cachedRefNodes[0].contains(value)) {
				return;
			}
			for (Node<T> cachedRefNode : cachedRefNodes) {
				cachedRefNode.addDirectRefWithoutDuplicateCheck(value);
			}
			return;
		}

		boolean repeatedKey = keyState == SEEN_KEY;
		List<Node<T>> addedRefNodes = repeatedKey ? new ArrayList<>() : null;

		// reset activeLeaf
		activeLeaf = root;

		Node<T> s = root;

		final SubString keyString = new SubString(key);
		// proceed with tree construction (closely related to procedure in Ukkonen's paper)
		SubString text = keyString.shorten(keyString.length());
		SubString rest = new SubString(keyString);
		// iterate over the string, one char at a time
		for (int i = 0; i < keyString.length(); i++) {
			// line 6, line 7: update the tree with the new transitions due to this new char
			rest.setTrustedRange(key, i, keyString.length() - i);
			update(s, text, keyString.charAt(i), rest, value, addedRefNodes, activePointResult);

			s = activePointResult.node;
			text = activePointResult.string;
		}

		// add leaf suffix link, if necessary
		if (null == activeLeaf.getSuffix() && activeLeaf != root && activeLeaf != s) {
			activeLeaf.setSuffix(s);
		}

		if (addedRefNodes != null && !addedRefNodes.isEmpty()) {
			refNodesByKey.put(key, addedRefNodes.toArray(Node[]::new));
		} else {
			refNodesByKey.put(key, SEEN_KEY);
		}
	}

	private boolean removeData(SubString wordSubString, T value) {
		Node<T> currentNode = root;
		while (!wordSubString.isEmpty()) {
			Node<T> currentEdge = currentNode.getEdge(wordSubString);
			if (currentEdge == null) {
				return false;
			}

			int lenToMatch = Math.min(wordSubString.length(), currentEdge.length());
			if (!currentEdge.startsWith(wordSubString, lenToMatch)) {
				return false;
			}

			currentNode = currentEdge;
			if (lenToMatch == wordSubString.length()) {
				return currentNode.removeData(value);
			}

			wordSubString = wordSubString.subSequence(lenToMatch);
		}
		return false;
	}

	/**
	 * Tests whether the string stringPart + t is contained in the subtree that has inputs as root.
	 * If that's not the case, and there exists a path of edges e1, e2, ... such that
	 * e1.label + e2.label + ... + $end = stringPart
	 * and there is an edge g such that
	 * g.label = stringPart + rest
	 * <p>
	 * Then g will be split in two different edges, one having $end as label, and the other one
	 * having rest as label.
	 *
	 * @param startNode    the starting node
	 * @param searchString the string to search
	 * @param t            the following character
	 * @param remainder    the remainder of the string to add to the index
	 * @param value        the value to add to the index
	 * @param addedRefNodes receives each node whose payload was updated, or {@code null} when updates do not need to be
	 *                      recorded
	 * @param result            receives whether {@code searchString + t} is already contained, and the last reachable
	 *                          node
	 * @param canonizeResult    reusable result object for the internal canonize step
	 */
	private static <T> void testAndSplit(
			Node<T> startNode,
			SubString searchString,
			final char t,
			final SubString remainder,
			final T value,
			@Nullable List<Node<T>> addedRefNodes,
			SplitResult<T> result,
			ActivePoint<T> canonizeResult
	) {
		assert !remainder.isEmpty();
		assert remainder.charAt(0) == t;

		// descend the tree as far as possible
		canonize(startNode, searchString, canonizeResult);
		startNode = canonizeResult.node;
		searchString = canonizeResult.string;

		if (!searchString.isEmpty()) {
			Node<T> g = startNode.getEdge(searchString);
			assert g != null;
			// must see whether "searchString" is substring of the label of an edge
			if (g.length() > searchString.length() && g.charAt(searchString.length()) == t) {
				result.set(true, startNode);
				return;
			}
			Node<T> newNode = splitNode(startNode, g, searchString);
			result.set(false, newNode);
			return;
		}

		Node<T> e = startNode.getEdge(remainder);
		if (e == null) {
			// if there is no t-transition from s
			result.set(false, startNode);
			return;
		}

		if (e.startsWith(remainder)) {
			if (e.length() == remainder.length()) {
				// update payload of destination node
				e.addRef(value, addedRefNodes);
				result.set(true, startNode);
				return;
			} else {
				Node<T> newNode = splitNode(startNode, e, remainder);
				newNode.addRef(value, addedRefNodes);
				result.set(false, startNode);
				return;
			}
		} else {
			result.set(true, startNode);
		}
	}

	private static <T> Node<T> splitNode(Node<T> s, Node<T> e, SubString splitFirstPart) {
		assert e == s.getEdge(splitFirstPart);
		assert e.startsWith(splitFirstPart);
		assert e.length() > splitFirstPart.length();

		// need to split the edge
		SubString splitSecondPart = e.subSequence(splitFirstPart.length());

		// build a new node r in between s and e
		Node<T> r = new Node<>(splitFirstPart);
		// replace e with new first part pointing to r
		s.addEdge(r);
		// r is the new node sitting in between s and the original child
		e.set(splitSecondPart);
		r.addEdge(e);

		return r;
	}

	/**
	 * Writes a (Node, String) (n, remainder) pair such that n is the farthest descendant of
	 * s (the input node) that can be reached by following a path of edges denoting
	 * a prefix of input and remainder will be string that must be
	 * appended to the concatenation of labels from s to n to get input.
	 *
	 * @param s      the starting node
	 * @param input  the string to canonize
	 * @param result receives the farthest reachable node and remaining unmatched input
	 */
	private static <T> void canonize(final Node<T> s, final SubString input, ActivePoint<T> result) {
		Node<T> currentNode = s;

		// descend the tree as long as a proper label is found
		SubString remainder = input;

		while (!remainder.isEmpty()) {
			Node<T> nextEdge = currentNode.getEdge(remainder);
			if (nextEdge == null || !remainder.startsWith(nextEdge)) {
				break;
			}
			currentNode = nextEdge;
			remainder = remainder.subSequence(nextEdge.length());
		}

		result.set(currentNode, remainder);
	}

	/**
	 * Updates the tree starting from inputNode and by adding stringPart.
	 * <p>
	 * Writes a reference (Node, String) pair for the string that has been added so far to {@code result}.
	 * This means:
	 * - the Node will be the Node that can be reached by the longest path string (S1)
	 * that can be obtained by concatenating consecutive edges in the tree and
	 * that is a substring of the string added so far to the tree.
	 * - the String will be the remainder that must be added to S1 to get the string
	 * added so far.
	 *
	 * @param s          the node to start from
	 * @param stringPart the string to add to the tree
	 * @param newChar    the character being added in this update step
	 * @param rest       the rest of the string
	 * @param value      the value to add
	 * @param addedRefNodes receives each node whose payload was updated, or {@code null} when updates do not need to be
	 *                      recorded
	 * @param result     receives the active point after this update
	 */
	private void update(
			Node<T> s,
			final SubString stringPart,
			final char newChar,
			final SubString rest,
			final T value,
			@Nullable List<Node<T>> addedRefNodes,
			ActivePoint<T> result
	) {
		assert !rest.isEmpty();
		assert rest.charAt(0) == newChar;

		SubString k = stringPart.extend(newChar);

		// line 1
		Node<T> oldRoot = root;

		// line 1b
		testAndSplit(s, stringPart, newChar, rest, value, addedRefNodes, splitResult, canonizeResult);
		Node<T> r = splitResult.node;
		boolean endpoint = splitResult.endpoint;

		Node<T> leaf;
		// line 2
		while (!endpoint) {
			// line 3
			Node<T> tempEdge = r.getEdge(newChar);
			if (tempEdge != null) {
				// such a node is already present. This is one of the main differences from Ukkonen's case:
				// the tree can contain deeper nodes at this stage because different strings were added by previous iterations.
				leaf = tempEdge;
			} else {
				// must build a new leaf
				leaf = new Node<>(rest);
				leaf.addRef(value, addedRefNodes);
				r.addEdge(leaf);
			}

			// update suffix link for newly created leaf
			if (activeLeaf != root) {
				activeLeaf.setSuffix(leaf);
			}
			activeLeaf = leaf;

			// line 4
			if (oldRoot != root) {
				oldRoot.setSuffix(r);
			}

			// line 5
			oldRoot = r;

			// line 6
			if (null == s.getSuffix()) { // root node
				assert (root == s);
				// this is a special case to handle what is referred to as node _|_ on the paper
				k = k.subSequence(1);
			} else {
				char nextChar = k.charAt(k.length() - 1);
				canonize(s.getSuffix(), k.shorten(1), canonizeResult);
				s = canonizeResult.node;
				k = canonizeResult.string.extend(nextChar);
			}

			// line 7
			testAndSplit(s, k.shorten(1), newChar, rest, value, addedRefNodes, splitResult, canonizeResult);
			endpoint = splitResult.endpoint;
			r = splitResult.node;
		}

		// line 8
		if (oldRoot != root) {
			oldRoot.setSuffix(r);
		}

		// make sure the active pair is canonical
		canonize(s, k, result);
	}

	@SuppressWarnings("unchecked")
	private static <T> Node<T>[] cachedRefNodes(Object keyState) {
		return (Node<T>[]) keyState;
	}

	public String statistics() {
		return "GeneralizedSuffixTree:" +
				"\nNode size stats: \n" + this.root.nodeSizeStats() +
				"\nNode edge stats: \n" + this.root.nodeEdgeStats();
	}

	/**
	 * Print the tree for use by <a href="https://graphviz.org/">graphviz</a>.
	 * To view, run the command:
	 * {@code dot -Tpng -O <filename>.dot}
	 *
	 * @param out  the print writer to output to.
	 * @param includeSuffixLinks  whether to include suffix links in the output graph.
	 */
	@SuppressWarnings("unused") // used for debugging
	public void printTree(PrintWriter out, boolean includeSuffixLinks) {
		root.printTree(out, includeSuffixLinks);
	}

	/**
	 * Mutable holder for an active-point result.
	 * <p>
	 * The fields deliberately start as {@code null} so one holder can be allocated once and overwritten many times in
	 * the construction hot path. Callers must treat this like an out-parameter: pass it to a method that documents it as
	 * a result, then read it only after that method returns. It must not be read before the first {@link #set(Node,
	 * SubString)} call, and the same holder must not be shared between an outer result and an inner scratch call.
	 */
	@SuppressWarnings("NotNullFieldNotInitialized")
	private static final class ActivePoint<T> {
		private Node<T> node;
		private SubString string;

		private void set(Node<T> node, SubString string) {
			this.node = node;
			this.string = string;
		}
	}

	/**
	 * Mutable holder for a split-test result.
	 * <p>
	 * Like {@link ActivePoint}, this intentionally uses unset fields between calls so the construction loop can avoid
	 * allocating short-lived result objects. The {@code node} field is valid only after {@link #set(boolean, Node)} has
	 * been called by {@code testAndSplit}; callers must not keep the holder or read stale values across independent
	 * algorithm steps.
	 */
	@SuppressWarnings("NotNullFieldNotInitialized")
	private static final class SplitResult<T> {
		private boolean endpoint;
		private Node<T> node;

		private void set(boolean endpoint, Node<T> node) {
			this.endpoint = endpoint;
			this.node = node;
		}
	}
}
