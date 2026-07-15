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

import it.unimi.dsi.fastutil.chars.Char2ObjectMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import javax.annotation.Nullable;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * Represents a node of the generalized suffix tree graph
 *
 * @see GeneralizedSuffixTree
 */
class Node<T> extends SubString {
	/**
	 * Values associated with this node.
	 * <p>
	 * This is intentionally stored in a compact tagged form:
	 * {@code null} when empty, the value itself when there is exactly one value, and a mutable collection when there are
	 * multiple values. Use {@link #dataSize} to determine which form is active, and use {@link #getDataValues()} or
	 * {@link #getMutableDataValues()} instead of casting directly.
	 */
	@Nullable
	private Object data;
	/**
	 * Number of values stored in {@link #data}. This keeps the compact tagged payload representation explicit and avoids
	 * relying on {@code data == null} checks outside the small payload helper methods.
	 */
	private int dataSize;

	/**
	 * Edges starting from this node.
	 * <p>
	 * This is intentionally stored in a compact tagged form: {@code null} when empty, the child {@link Node} itself
	 * when there is exactly one edge, and a mutable character map when there are multiple edges. Single-edge nodes are
	 * common in suffix trees, so this avoids allocating a map wrapper for them.
	 */
	@Nullable
	private Object edges;

	/**
	 * The suffix link as described in Ukkonen's paper.
	 * if str is the string denoted by the path from the root to this, this.suffix
	 * is the node denoted by the path that corresponds to str without the first char.
	 */
	@Nullable
	private Node<T> suffix;

	/**
	 * Creates a new Node
	 */
	Node(SubString string) {
		super(string);
		edges = null;
		data = null;
		dataSize = 0;
		suffix = null;
	}

	/**
	 * Gets data from the payload of both this node and its children, the string representation
	 * of the path to this node is a substring of the one of the children nodes.
	 *
	 * @param resultsConsumer  a consumer that accepts the resulting data
	 */
	public void getData(Consumer<Collection<T>> resultsConsumer) {
		Collection<T> values = getDataValues();
		if (!values.isEmpty()) {
			resultsConsumer.accept(values);
		}
		forEachEdge(edge -> edge.getData(resultsConsumer));
	}

	/**
	 * Adds the given <tt>index</tt> to the set of indexes associated with <tt>this</tt>
	 * returns false if this node already contains the ref
	 */
	void addRef(T value) {
		addRef(value, null);
	}

	/**
	 * Adds the value to this node and suffix-linked nodes that need it.
	 *
	 * @param value the value to add
	 * @param addedRefNodes receives every node that was actually updated, or {@code null} when the caller does not need
	 *                      to record those nodes
	 */
	void addRef(T value, @Nullable List<Node<T>> addedRefNodes) {
		if (contains(value)) {
			return;
		}

		addValue(value);
		if (addedRefNodes != null) {
			addedRefNodes.add(this);
		}

		// add this reference to all the suffixes as well
		Node<T> iter = this.suffix;
		while (iter != null) {
			if (!iter.contains(value)) {
				iter.addValue(value);
				if (addedRefNodes != null) {
					addedRefNodes.add(iter);
				}
				iter = iter.suffix;
			} else {
				break;
			}
		}
	}

	/**
	 * Adds a value only to this node.
	 * <p>
	 * This is used by repeated-key replay after the full insertion path has already identified the exact nodes that need
	 * the key's values. It must not follow suffix links, because the cached node list already includes the suffix-linked
	 * nodes that were updated during the recorded insertion. The caller must check one representative cached node for
	 * the value before calling this; all nodes in a cached-key list receive the same values, so per-node duplicate checks
	 * would only repeat the same work.
	 *
	 * @param value the value to add
	 */
	void addDirectRefWithoutDuplicateCheck(T value) {
		addValue(value);
	}

	/**
	 * Tests whether a node contains a reference to the given index.
	 *
	 * @param value the value to look for
	 * @return true if this contains the value
	 */
	protected boolean contains(T value) {
		if (dataSize == 0) {
			return false;
		}
		if (dataSize == 1) {
			return Objects.equals(data, value);
		}
		return getMutableDataValues().contains(value);
	}

	void addEdge(Node<T> edge) {
		char firstChar = edge.charAt(0);
		Object currentEdges = edges;

		if (currentEdges == null) {
			edges = edge;
			return;
		}

		if (currentEdges instanceof Node<?>) {
			Node<T> currentSingleEdge = singleEdge(currentEdges);
			if (currentSingleEdge.charAt(0) == firstChar) {
				// During an edge split, the replacement edge is the prefix of the old edge.
				// It takes over the same first-character transition from this node; the old
				// edge is then shortened and reattached below the new split node.
				edges = edge;
			} else {
				Char2ObjectMap<Node<T>> newEdges = new Char2ObjectOpenHashMap<>(4);
				newEdges.put(currentSingleEdge.charAt(0), currentSingleEdge);
				newEdges.put(firstChar, edge);
				edges = newEdges;
			}
			return;
		}

		edgeMap(currentEdges).put(firstChar, edge);
	}

	@Nullable
	Node<T> getEdge(char ch) {
		Object currentEdges = edges;
		if (currentEdges == null) {
			return null;
		}
		if (currentEdges instanceof Node<?>) {
			Node<T> currentSingleEdge = singleEdge(currentEdges);
			if (currentSingleEdge.charAt(0) == ch) {
				return currentSingleEdge;
			}
			return null;
		}
		return edgeMap(currentEdges).get(ch);
	}

	@Nullable
	Node<T> getEdge(SubString string) {
		if (string.isEmpty()) {
			return null;
		}
		char ch = string.charAt(0);
		return getEdge(ch);
	}

	@Nullable
	Node<T> getSuffix() {
		return suffix;
	}

	void setSuffix(Node<T> suffix) {
		this.suffix = suffix;
	}

	/**
	 * Add a new value to this {@code Node}'s data.
	 * @param value the value to add
	 */
	protected void addValue(T value) {
		switch (dataSize) {
			case 0 -> data = value;
			case 1 -> {
				List<T> newData = new ArrayList<>(4);
				newData.add(getSingleDataValue());
				newData.add(value);
				data = newData;
			}
			case 16 -> {
				// "upgrade" data to a Set once it's getting bigger,
				// to improve its `contains` performance.
				Collection<T> newData = new ReferenceOpenHashSet<>(17);
				newData.addAll(getMutableDataValues());
				newData.add(value);
				data = newData;
			}
			default -> getMutableDataValues().add(value);
		}
		dataSize++;
	}

	@SuppressWarnings("unchecked")
	private T getSingleDataValue() {
		return (T) data;
	}

	@SuppressWarnings("unchecked")
	private Collection<T> getMutableDataValues() {
		return (Collection<T>) data;
	}

	private Collection<T> getDataValues() {
		return switch (dataSize) {
			case 0 -> List.of();
			case 1 -> Collections.singleton(getSingleDataValue());
			default -> Collections.unmodifiableCollection(getMutableDataValues());
		};
	}

	@Override
	public String toString() {
		return "Node: edge: " + super.toString() + " size:" + dataSize + " Edges: " + edges;
	}

	/**
	 * @return debug statistics for this node's size and the size of all its descendants.
	 */
	public IntSummaryStatistics nodeSizeStats() {
		return nodeSizes().summaryStatistics();
	}

	private IntStream nodeSizes() {
		return IntStream.concat(
				IntStream.of(dataSize),
				edgeValues().stream().flatMapToInt(Node::nodeSizes)
		);
	}

	/**
	 * @return debug statistics for this node's edges and the edges of all its descendants.
	 */
	public String nodeEdgeStats() {
		IntSummaryStatistics edgeCounts = nodeEdgeCounts().summaryStatistics();
		IntSummaryStatistics edgeLengths = nodeEdgeLengths().summaryStatistics();
		return "Edge counts: " + edgeCounts +
				"\nEdge lengths: " + edgeLengths;
	}

	private IntStream nodeEdgeCounts() {
		return IntStream.concat(
				IntStream.of(edgeCount()),
				edgeValues().stream().flatMapToInt(Node::nodeEdgeCounts)
		);
	}

	private IntStream nodeEdgeLengths() {
		return IntStream.concat(
				edgeValues().stream().mapToInt(Node::length),
				edgeValues().stream().flatMapToInt(Node::nodeEdgeLengths)
		);
	}

	/**
	 * Debug function for printing this {@code Node} in a format usable by <a href="https://graphviz.org/">graphviz</a>.
	 *
	 * @param out  the print writer to output to.
	 * @param includeSuffixLinks  whether to include suffix links in the output graph.
	 * @see GeneralizedSuffixTree#printTree
	 */
	public void printTree(PrintWriter out, boolean includeSuffixLinks) {
		out.println("digraph {");
		out.println("\trankdir = LR;");
		out.println("\tordering = out;");
		out.println("\tedge [arrowsize=0.4,fontsize=10]");
		out.println("\t" + nodeId(this) + " [label=\"\",style=filled,fillcolor=lightgrey,shape=circle,width=.1,height=.1];");
		out.println("//------leaves------");
		printLeaves(out);
		out.println("//------internal nodes------");
		printInternalNodes(this, out);
		out.println("//------edges------");
		printEdges(out);
		if (includeSuffixLinks) {
			out.println("//------suffix links------");
			printSLinks(out);
		}
		out.println("}");
	}

	private void printLeaves(PrintWriter out) {
		if (edgeCount() == 0) {
			out.println("\t" + nodeId(this) + " [label=\"" + getDataValues() + "\",shape=point,style=filled,fillcolor=lightgrey,shape=circle,width=.07,height=.07]");
		} else {
			forEachEdge(edge -> edge.printLeaves(out));
		}
	}

	private void printInternalNodes(Node<T> root, PrintWriter out) {
		if (this != root && edgeCount() > 0) {
			out.println("\t" + nodeId(this) + " [label=\"" + data + "\",style=filled,fillcolor=lightgrey,shape=circle,width=.07,height=.07]");
		}

		forEachEdge(edge -> edge.printInternalNodes(root, out));
	}

	private void printEdges(PrintWriter out) {
		for (Node<T> child : edgeValues()) {
			out.println("\t" + nodeId(this) + " -> " + nodeId(child) + " [label=\"" + child + "\",weight=10]");
			child.printEdges(out);
		}
	}

	private void printSLinks(PrintWriter out) {
		if (suffix != null) {
			out.println("\t" + nodeId(this) + " -> " + nodeId(suffix) + " [label=\"\",weight=0,style=dotted]");
		}
		forEachEdge(edge -> edge.printSLinks(out));
	}

	private int edgeCount() {
		Object currentEdges = edges;
		if (currentEdges == null) {
			return 0;
		}
		if (currentEdges instanceof Node<?>) {
			return 1;
		}
		return edgeMap(currentEdges).size();
	}

	private Collection<Node<T>> edgeValues() {
		Object currentEdges = edges;
		if (currentEdges == null) {
			return List.of();
		}
		if (currentEdges instanceof Node<?>) {
			return Collections.singleton(singleEdge(currentEdges));
		}
		return edgeMap(currentEdges).values();
	}

	private void forEachEdge(Consumer<Node<T>> edgeConsumer) {
		Object currentEdges = edges;
		if (currentEdges == null) {
			return;
		}
		if (currentEdges instanceof Node<?>) {
			edgeConsumer.accept(singleEdge(currentEdges));
			return;
		}
		for (Node<T> edge : edgeMap(currentEdges).values()) {
			edgeConsumer.accept(edge);
		}
	}

	@SuppressWarnings("unchecked")
	private Node<T> singleEdge(Object currentEdges) {
		return (Node<T>) currentEdges;
	}

	@SuppressWarnings("unchecked")
	private Char2ObjectMap<Node<T>> edgeMap(Object currentEdges) {
		return (Char2ObjectMap<Node<T>>) currentEdges;
	}

	private static <T> String nodeId(Node<T> node) {
		return "node" + Integer.toHexString(node.hashCode()).toUpperCase();
	}
}
