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

/**
 * Reduces memory usage by avoiding creating substrings from slicing.
 * Instead, one string is used and multiple SubString classes point to it.
 */
public class SubString implements CharSequence {
	private String string;
	private int offset;
	private int length;

	/**
	 * Create a new {@code SubString} from the given string.
	 *
	 * @param string  the underlying string for this {@code SubString}
	 */
	public SubString(String string) {
		this(string, 0, string.length());
	}

	/**
	 * Create a new {@code SubString} from another {@code SubString}.
	 *
	 * @param other  the other {@code SubString}
	 */
	public SubString(SubString other) {
		this(other.string, other.offset, other.length, true);
	}

	/**
	 * Create a new {@code SubString} from the given string.
	 *
	 * @param string  the underlying string for this {@code SubString}
	 * @param offset  the starting offset to be trimmed from the start of the underlying string.
	 */
	public SubString(String string, int offset) {
		this(string, offset, string.length() - offset);
	}

	/**
	 * Create a new {@code SubString} from the given string.
	 * See {@link CharSequence#subSequence(int, int)} for how to set the {@code offset} and {@code length}.
	 *
	 * @param string  the underlying string for this {@code SubString}
	 * @param offset  the starting offset
	 * @param length  the starting length
	 */
	public SubString(String string, int offset, int length) {
		if (length < 0) {
			throw new IllegalArgumentException("length (" + length + ") must be greater than or equal to 0 ");
		}
		if (offset < 0) {
			throw new IllegalArgumentException("offset (" + offset + ") must be greater than or equal to 0 ");
		}
		if (offset + length > string.length()) {
			throw new IllegalArgumentException(
					"offset (" + offset + ") plus length (" + length +
					") must be less than or equal to the string's length (" + string.length() + ")"
			);
		}

		this.string = string;
		this.offset = offset;
		this.length = length;
	}

	private SubString(String string, int offset, int length, boolean trustedRange) {
		assert trustedRange;
		// Used when the caller has already performed the same range checks as the public constructor.
		this.string = string;
		this.offset = offset;
		this.length = length;
	}

	/**
	 * Returns a {@code SubString} that is a subsequence of this {@code SubString}.
	 * The subsequence starts with the {@code char} value at the specified index and
	 * ends with the same {@code char} as this {@code SubString}.
	 *
	 * @param   start   the start index, inclusive
	 * @return the specified subsequence
	 */
	public SubString subSequence(int start) {
		return subSequence(start, length);
	}

	/**
	 * Returns a {@code SubString} that is a subsequence of this {@code SubString}.
	 * The subsequence starts with the {@code char} value at the specified index and
	 * ends with the {@code char} value at index {@code end - 1}.  The length
	 * (in {@code char}s) of the
	 * returned sequence is {@code end - start}, so if {@code start == end}
	 * then an empty sequence is returned.
	 *
	 * @param   start   the start index, inclusive
	 * @param   end     the end index, exclusive
	 *
	 * @return the specified subsequence
	 * @see CharSequence#subSequence
	 *
	 * @throws  IndexOutOfBoundsException
	 *          if {@code start} or {@code end} are negative,
	 *          if {@code end} is greater than {@code length()},
	 *          or if {@code start} is greater than {@code end}
	 */
	@Override
	public SubString subSequence(int start, int end) {
		if (start < 0 || start > end || end > length) {
			throw new IndexOutOfBoundsException("start " + start + ", end " + end + ", length " + length);
		}
		if (start == 0 && end == length) {
			return this;
		}
		return new SubString(string, offset + start, end - start, true);
	}

	@Override
	public boolean isEmpty() {
		return this.length == 0;
	}

	@Override
	public char charAt(int index) {
		return string.charAt(offset + index);
	}

	@Override
	public int length() {
		return length;
	}

	/**
	 * Sets this {@code SubString} to refer to the same range as another one.
	 *
	 * @param other the other {@code SubString}
	 */
	public void set(SubString other) {
		this.string = other.string;
		this.offset = other.offset;
		this.length = other.length;
	}

	/**
	 * Sets this view to a caller-validated range without repeating public constructor checks.
	 * <p>
	 * This is package-private for construction hot paths that repeatedly retarget a temporary {@code SubString}. Do not
	 * use it when accepting external offsets; call a public constructor or {@link #subSequence(int, int)} instead.
	 */
	void setTrustedRange(String string, int offset, int length) {
		assert length >= 0;
		assert offset >= 0;
		assert offset + length <= string.length();
		this.string = string;
		this.offset = offset;
		this.length = length;
	}

	/**
	 * Returns a {@code SubString} that is a subsequence of this {@code SubString}.
	 * The subsequence starts with the same {@code char} value as this {@code SubString},
	 * and removes {@code amount} from the end.
	 *
	 * @param   amount   the amount of characters to remove from the end of this {@code SubString}.
	 * @return the specified subsequence
	 */
	public SubString shorten(int amount) {
		if (amount < 0) {
			throw new IllegalArgumentException("amount (" + amount + ") must be greater than or equal to 0 ");
		}
		if (length == 0 || amount == 0) {
			return this;
		}
		int newLength = Math.max(length - amount, 0);
		return new SubString(string, offset, newLength, true);
	}

	/**
	 * Extending a substring is the opposite of shortening.
	 * Note, it will only work if the character matches the next character in the underlying string.
	 *
	 * @param  newChar  the next character in this {@code SubString}.
	 * @throws IllegalArgumentException if the character does not match the next character in the underlying string.
	 * @return a new {@code SubString} with the given character appended to the end.
	 */
	public SubString extend(char newChar) {
		if (offset + length >= string.length()) {
			throw new IndexOutOfBoundsException("cannot extend the string past its maximum length " + length);
		}

		char expectedChar = charAt(length);
		if (expectedChar != newChar) {
			throw new IllegalArgumentException(
					"extend must be called with the next char. expected '" + expectedChar +
					"' but was given '" + newChar + "' instead."
			);
		}

		return new SubString(string, this.offset, this.length + 1, true);
	}

	/**
	 * Tests if this {@code SubString} starts with the specified prefix.
	 *
	 * @param   prefix    the prefix.
	 * @return  {@code true} if the character sequence represented by the
	 *          argument is a prefix of the substring of this object;
	 *          {@code false} otherwise.
	 */
	public boolean startsWith(SubString prefix) {
		return startsWith(prefix, prefix.length());
	}

	/**
	 * Tests if this {@code SubString} starts with the specified prefix.
	 *
	 * @param   prefix    the prefix.
	 * @param   lenToMatch   the number of characters to compare.
	 * @return  {@code true} if the character sequence represented by the
	 *          argument is a prefix of the substring of this object;
	 *          {@code false} otherwise.
	 */
	@SuppressWarnings("StringEquality")
	public boolean startsWith(SubString prefix, int lenToMatch) {
		if (lenToMatch > length) {
			return false;
		}
		if (string == prefix.string && offset == prefix.offset) {
			return true;
		}
		return string.regionMatches(offset, prefix.string, prefix.offset, lenToMatch);
	}

	@Override
	public String toString() {
		return string.substring(offset, offset + length);
	}

	/**
	 * @return a string used for debugging, it exposes the underlying string.
	 */
	public String debugString() {
		return this.getClass().getSimpleName() + ": \"" + this + "\"\nBacking string: \"" + string + "\"";
	}
}
