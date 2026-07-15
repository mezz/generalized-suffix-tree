package net.mezzdev.suffixtree;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SubStringTest {
	@Test
	public void testEmptyString() {
		SubString subString = new SubString("");
		assertTrue(subString.isEmpty());
		assertEquals(0, subString.length());
		assertEquals("", subString.toString());
	}

	@Test
	public void testEmptyByOffset() {
		SubString subString = new SubString("a", 1);
		assertTrue(subString.isEmpty());
		assertEquals("", subString.toString());
	}

	@Test
	public void testEmptyByLength() {
		SubString subString = new SubString("abc", 1, 0);
		assertTrue(subString.isEmpty());
		assertEquals("", subString.toString());
	}

	@Test
	public void testConstructorRejectsInvalidRanges() {
		assertThrows(IllegalArgumentException.class, () -> new SubString("abc", -1));
		assertThrows(IllegalArgumentException.class, () -> new SubString("abc", 0, -1));
		assertThrows(IllegalArgumentException.class, () -> new SubString("abc", 2, 2));
	}

	@Test
	public void testCharAtLengthAndToString() {
		String string = "abcdefg";
		SubString subString = new SubString("12abcdefg34", 2, string.length());

		assertEquals(string.length(), subString.length());
		assertEquals(string, subString.toString());
		for (int i = 0; i < string.length(); i++) {
			assertEquals(string.charAt(i), subString.charAt(i));
		}
	}

	@Test
	public void testCopyConstructor() {
		SubString original = new SubString("12abcdefg34", 2, 7);
		SubString copy = new SubString(original);

		assertEquals(original.length(), copy.length());
		assertEquals(original.toString(), copy.toString());
		assertEquals("abcdefg", copy.toString());
	}

	@Test
	public void testSubSequenceStart() {
		String string = "abcdefg";
		SubString subString = new SubString(string);

		for (int start = 0; start <= string.length(); start++) {
			SubString actual = subString.subSequence(start);
			assertEquals(string.substring(start), actual.toString());
		}
	}

	@Test
	public void testSubSequenceStartAndEnd() {
		String string = "abcdefg";
		SubString subString = new SubString("12abcdefg34", 2, string.length());

		for (int start = 0; start <= string.length(); start++) {
			for (int end = start; end <= string.length(); end++) {
				SubString actual = subString.subSequence(start, end);
				assertEquals(string.substring(start, end), actual.toString());
			}
		}
	}

	@Test
	public void testSubSequenceWholeRangeReturnsSameInstance() {
		SubString subString = new SubString("abcdefg");
		assertSame(subString, subString.subSequence(0, subString.length()));
	}

	@Test
	public void testSubSequenceRejectsInvalidRanges() {
		SubString subString = new SubString("abcdefg");

		assertThrows(IndexOutOfBoundsException.class, () -> subString.subSequence(-1));
		assertThrows(IndexOutOfBoundsException.class, () -> subString.subSequence(0, subString.length() + 1));
		assertThrows(IndexOutOfBoundsException.class, () -> subString.subSequence(3, 2));
	}

	@Test
	public void testShorten() {
		SubString subString = new SubString("abcdefg");

		assertEquals("abcdef", subString.shorten(1).toString());
		assertEquals("abcd", subString.shorten(3).toString());
		assertEquals("", subString.shorten(subString.length()).toString());
		assertEquals("", subString.shorten(subString.length() + 1).toString());
	}

	@Test
	public void testShortenReturnsSameInstanceForNoOp() {
		SubString subString = new SubString("abcdefg");
		SubString emptySubString = new SubString("abcdefg", 0, 0);

		assertSame(subString, subString.shorten(0));
		assertSame(emptySubString, emptySubString.shorten(1));
		assertThrows(IllegalArgumentException.class, () -> subString.shorten(-1));
	}

	@Test
	public void testExtend() {
		SubString subString = new SubString("abcdefg", 0, 0);

		for (int i = 0; i < "abcdefg".length(); i++) {
			subString = subString.extend("abcdefg".charAt(i));
			assertEquals("abcdefg".substring(0, i + 1), subString.toString());
		}
	}

	@Test
	public void testExtendRejectsInvalidCharacter() {
		SubString subString = new SubString("abcdefg", 0, 3);

		assertThrows(IllegalArgumentException.class, () -> subString.extend('x'));
	}

	@Test
	public void testExtendRejectsPastEnd() {
		SubString subString = new SubString("abc");

		assertThrows(IndexOutOfBoundsException.class, () -> subString.extend('d'));
	}

	@Test
	public void testStartsWith() {
		SubString subString = new SubString("12abcdefg34", 2, 7);

		assertTrue(subString.startsWith(new SubString("")));
		assertTrue(subString.startsWith(new SubString("abc")));
		assertTrue(subString.startsWith(new SubString("12abc34", 2, 3)));
		assertTrue(subString.startsWith(new SubString("abcdefg")));

		assertFalse(subString.startsWith(new SubString("abcdefgh")));
		assertFalse(subString.startsWith(new SubString("abd")));
		assertFalse(subString.startsWith(new SubString("12abc34", 1, 3)));
	}

	@Test
	public void testStartsWithLengthLimit() {
		SubString subString = new SubString("abcdefg");
		SubString prefix = new SubString("abcxyz");

		assertTrue(subString.startsWith(prefix, 3));
		assertFalse(subString.startsWith(prefix, 4));
		assertFalse(subString.startsWith(prefix, subString.length() + 1));
	}

	@Test
	public void testSet() {
		SubString subString = new SubString("abcdefg", 0, 3);
		SubString other = new SubString("12xyz34", 2, 3);

		subString.set(other);

		assertEquals("xyz", subString.toString());
		assertEquals(other.length(), subString.length());
	}

	@Test
	public void testSetTrustedRange() {
		SubString subString = new SubString("abcdefg", 0, 3);

		subString.setTrustedRange("12xyz34", 2, 3);

		assertEquals("xyz", subString.toString());
		assertEquals(3, subString.length());
	}

	@Test
	public void testDebugStringShowsBackingString() {
		SubString subString = new SubString("12abcdefg34", 2, 7);

		assertEquals("SubString: \"abcdefg\"\nBacking string: \"12abcdefg34\"", subString.debugString());
	}
}
