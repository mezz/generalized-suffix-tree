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
import it.unimi.dsi.fastutil.chars.Char2ObjectMaps;
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
	 * The set of edges starting from this node
	 */
	private Char2ObjectMap<Node<T>> edges;

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
		edges = Char2ObjectMaps.emptyMap();
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
		for (Node<T> dest : edges.values()) {
			dest.getData(resultsConsumer);
		}
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
	 * @param addedNodeConsumer receives every node that was actually updated, or {@code null} when the caller does not
	 *                          need to record those nodes
	 */
	void addRef(T value, @Nullable Consumer<Node<T>> addedNodeConsumer) {
		if (contains(value)) {
			return;
		}

		addValue(value);
		if (addedNodeConsumer != null) {
			addedNodeConsumer.accept(this);
		}

		// add this reference to all the suffixes as well
		Node<T> iter = this.suffix;
		while (iter != null) {
			if (!iter.contains(value)) {
				iter.addValue(value);
				if (addedNodeConsumer != null) {
					addedNodeConsumer.accept(iter);
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
	 * nodes that were updated during the recorded insertion.
	 *
	 * @param value the value to add
	 */
	void addDirectRef(T value) {
		if (!contains(value)) {
			addValue(value);
		}
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

		switch (edges.size()) {
			case 0 -> edges = Char2ObjectMaps.singleton(firstChar, edge);
			case 1 -> {
				if (edges.containsKey(firstChar)) {
					// During an edge split, the replacement edge is the prefix of the old edge.
					// It takes over the same first-character transition from this node; the old
					// edge is then shortened and reattached below the new split node.
					edges = Char2ObjectMaps.singleton(firstChar, edge);
				} else {
					Char2ObjectMap<Node<T>> newEdges = new Char2ObjectOpenHashMap<>(4);
					newEdges.putAll(edges);
					newEdges.put(firstChar, edge);
					edges = newEdges;
				}
			}
			default -> edges.put(firstChar, edge);
		}
	}

	@Nullable
	Node<T> getEdge(char ch) {
		return edges.get(ch);
	}

	@Nullable
	Node<T> getEdge(SubString string) {
		if (string.isEmpty()) {
			return null;
		}
		char ch = string.charAt(0);
		return edges.get(ch);
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
				edges.values().stream().flatMapToInt(Node::nodeSizes)
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
				IntStream.of(edges.size()),
				edges.values().stream().flatMapToInt(Node::nodeEdgeCounts)
		);
	}

	private IntStream nodeEdgeLengths() {
		return IntStream.concat(
				edges.values().stream().mapToInt(Node::length),
				edges.values().stream().flatMapToInt(Node::nodeEdgeLengths)
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
		if (edges.size() == 0) {
			out.println("\t" + nodeId(this) + " [label=\"" + getDataValues() + "\",shape=point,style=filled,fillcolor=lightgrey,shape=circle,width=.07,height=.07]");
		} else {
			for (Node<T> edge : edges.values()) {
				edge.printLeaves(out);
			}
		}
	}

	private void printInternalNodes(Node<T> root, PrintWriter out) {
		if (this != root && edges.size() > 0) {
			out.println("\t" + nodeId(this) + " [label=\"" + data + "\",style=filled,fillcolor=lightgrey,shape=circle,width=.07,height=.07]");
		}

		for (Node<T> edge : edges.values()) {
			edge.printInternalNodes(root, out);
		}
	}

	private void printEdges(PrintWriter out) {
		for (Node<T> child : edges.values()) {
			out.println("\t" + nodeId(this) + " -> " + nodeId(child) + " [label=\"" + child + "\",weight=10]");
			child.printEdges(out);
		}
	}

	private void printSLinks(PrintWriter out) {
		if (suffix != null) {
			out.println("\t" + nodeId(this) + " -> " + nodeId(suffix) + " [label=\"\",weight=0,style=dotted]");
		}
		for (Node<T> edge : edges.values()) {
			edge.printSLinks(out);
		}
	}

	private static <T> String nodeId(Node<T> node) {
		return "node" + Integer.toHexString(node.hashCode()).toUpperCase();
	}
}
