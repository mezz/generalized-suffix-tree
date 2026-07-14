package net.mezzdev.suffixtree;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NodeTest extends TestCase {
	public void testPayloadValuesRemainObservableAcrossGrowth() {
		Node<Integer> node = new Node<>(new SubString("node"));

		assertEquals(Set.of(), data(node));

		node.addRef(0);
		assertEquals(Set.of(0), data(node));

		node.addRef(0);
		assertEquals(Set.of(0), data(node));

		Set<Integer> expectedValues = new HashSet<>();
		expectedValues.add(0);
		for (int i = 1; i <= 20; i++) {
			node.addRef(i);
			expectedValues.add(i);
		}

		assertEquals(expectedValues, data(node));
		assertTrue(node.contains(20));
		assertFalse(node.contains(21));
	}

	public void testPayloadCollectionIsNotMutableByConsumers() {
		Node<Integer> node = new Node<>(new SubString("node"));
		node.addRef(1);
		node.addRef(2);

		Collection<Integer> values = dataCollections(node).get(0);
		try {
			values.add(3);
			fail("Expected node data collection to be immutable");
		} catch (UnsupportedOperationException ignored) {
		}

		assertEquals(Set.of(1, 2), data(node));
	}

	public void testAddRefPropagatesValuesThroughSuffixLinks() {
		Node<Integer> node = new Node<>(new SubString("abc"));
		Node<Integer> suffix = new Node<>(new SubString("bc"));
		Node<Integer> suffixSuffix = new Node<>(new SubString("c"));
		node.setSuffix(suffix);
		suffix.setSuffix(suffixSuffix);

		node.addRef(42);
		node.addRef(42);

		assertEquals(Set.of(42), data(node));
		assertEquals(Set.of(42), data(suffix));
		assertEquals(Set.of(42), data(suffixSuffix));

		suffix.addRef(7);

		assertEquals(Set.of(42), data(node));
		assertEquals(Set.of(42, 7), data(suffix));
		assertEquals(Set.of(42, 7), data(suffixSuffix));
	}

	public void testEdgesCanBeAddedFoundAndReplaced() {
		Node<Integer> node = new Node<>(new SubString("root"));
		Node<Integer> alpha = new Node<>(new SubString("alpha"));
		Node<Integer> beta = new Node<>(new SubString("beta"));
		Node<Integer> gamma = new Node<>(new SubString("gamma"));
		Node<Integer> singletonReplacement = new Node<>(new SubString("axis"));

		node.addEdge(alpha);
		assertSame(alpha, node.getEdge('a'));
		assertSame(alpha, node.getEdge(new SubString("anything")));
		assertNull(node.getEdge('b'));

		node.addEdge(singletonReplacement);
		assertSame(singletonReplacement, node.getEdge('a'));

		node.addEdge(beta);
		node.addEdge(gamma);
		assertSame(singletonReplacement, node.getEdge('a'));
		assertSame(beta, node.getEdge(new SubString("blue")));
		assertSame(gamma, node.getEdge(new SubString("green")));

		Node<Integer> alphaReplacement = new Node<>(new SubString("axis"));
		node.addEdge(alphaReplacement);

		assertSame(alphaReplacement, node.getEdge('a'));
		assertSame(beta, node.getEdge('b'));
		assertSame(gamma, node.getEdge('g'));
	}

	private static <T> Set<T> data(Node<T> node) {
		Set<T> results = new HashSet<>();
		node.getData(results::addAll);
		return results;
	}

	private static <T> List<Collection<T>> dataCollections(Node<T> node) {
		List<Collection<T>> results = new ArrayList<>();
		node.getData(results::add);
		return results;
	}
}
