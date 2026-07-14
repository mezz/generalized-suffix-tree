/**
 * Copyright 2012 Alessandro Bahgat Shehata
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.mezzdev.suffixtree;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static net.mezzdev.suffixtree.Utils.getSubstrings;

public class SuffixTreeTest extends TestCase {

    public static <E> void assertEmpty(Collection<E> collection) {
        assertTrue("Expected empty collection.", collection.isEmpty());
    }

    public static <T> Set<T> search(GeneralizedSuffixTree<T> suffixTree, String s) {
        Set<T> results = new HashSet<>();
        suffixTree.getSearchResults(s, results::addAll);
        return results;
    }

    public void testBasicTreeGeneration() {
        GeneralizedSuffixTree<Integer> in = new GeneralizedSuffixTree<>();

        String word = "cacao";
        in.put(word, 0);

        /* test that every substring is contained within the tree */
        for (String s : getSubstrings(word)) {
            assertTrue(search(in, s).contains(0));
        }
        assertEmpty(search(in, "caco"));
        assertEmpty(search(in, "cacaoo"));
        assertEmpty(search(in, "ccacao"));

        in = new GeneralizedSuffixTree<>();
        word = "bookkeeper";
        in.put(word, 0);
        for (String s : getSubstrings(word)) {
            assertTrue(search(in, s).contains(0));
        }
        assertEmpty(search(in, "books"));
        assertEmpty(search(in, "boke"));
        assertEmpty(search(in, "ookepr"));
    }

    public void testWeirdWord() {
        GeneralizedSuffixTree<Integer> in = new GeneralizedSuffixTree<>();

        String word = "cacacato";
        in.put(word, 0);

        /* test that every substring is contained within the tree */
        for (String s : getSubstrings(word)) {
            assertTrue(search(in, s).contains(0));
        }
    }

    public void testDouble() {
        // test whether the tree can handle repetitions
        GeneralizedSuffixTree<Integer> in = new GeneralizedSuffixTree<>();
        String word = "cacao";
        in.put(word, 0);
        in.put(word, 1);

        for (String s : getSubstrings(word)) {
            assertTrue(search(in, s).contains(0));
            assertTrue(search(in, s).contains(1));
        }
    }

    public void testBananaAddition() {
        GeneralizedSuffixTree<Integer> in = new GeneralizedSuffixTree<>();
        String[] words = new String[] {"banana", "bano", "ba"};
        for (int i = 0; i < words.length; ++i) {
            in.put(words[i], i);

            for (String s : getSubstrings(words[i])) {
                Collection<Integer> result = search(in, s);
                assertNotNull("result null for string " + s + " after adding " + words[i], result);
                assertTrue("substring " + s + " not found after adding " + words[i], result.contains(i));
            }
        }

        // verify post-addition
        for (int i = 0; i < words.length; ++i) {
            for (String s : getSubstrings(words[i])) {
                assertTrue(search(in, s).contains(i));
            }
        }

        // add again, to see if it's stable
        for (int i = 0; i < words.length; ++i) {
            in.put(words[i], i + words.length);

            for (String s : getSubstrings(words[i])) {
                assertTrue(search(in, s).contains(i + words.length));
            }
        }

    }

    public void testAddition() {
        GeneralizedSuffixTree<Integer> in = new GeneralizedSuffixTree<>();
        String[] words = new String[] {"cacaor" , "caricato", "cacato", "cacata", "caricata", "cacao", "banana"};
        for (int i = 0; i < words.length; ++i) {
            String word = words[i];
            in.put(word, i);

            for (String s : getSubstrings(word)) {
                Collection<Integer> result = search(in, s);
                assertNotNull("result null for string " + s + " after adding " + word, result);
                assertTrue("substring " + s + " not found after adding " + word, result.contains(i));
            }
        }
        // verify post-addition
        for (int i = 0; i < words.length; ++i) {
            for (String s : getSubstrings(words[i])) {
                Collection<Integer> result = search(in, s);
                assertNotNull("result null for string " + s + " after adding " + words[i], result);
                assertTrue("substring " + s + " not found after adding " + words[i], result.contains(i));
            }
        }

        // add again, to see if it's stable
        for (int i = 0; i < words.length; ++i) {
            in.put(words[i], i + words.length);

            for (String s : getSubstrings(words[i])) {
                assertTrue(search(in, s).contains(i + words.length));
            }
        }

        assertEmpty(search(in, "aoca"));
    }

    public void testSampleAddition() {
        GeneralizedSuffixTree<Integer> in = new GeneralizedSuffixTree<>();
        String[] words = new String[] {"libertypike",
            "franklintn",
            "carothersjohnhenryhouse",
            "carothersezealhouse",
            "acrossthetauntonriverfromdightonindightonrockstatepark",
            "dightonma",
            "dightonrock",
            "6mineoflowgaponlowgapfork",
            "lowgapky",
            "lemasterjohnjandellenhouse",
            "lemasterhouse",
            "70wilburblvd",
            "poughkeepsieny",
            "freerhouse",
            "701laurelst",
            "conwaysc",
            "hollidayjwjrhouse",
            "mainandappletonsts",
            "menomoneefallswi",
            "mainstreethistoricdistrict",
            "addressrestricted",
            "brownsmillsnj",
            "hanoverfurnace",
            "hanoverbogironfurnace",
            "sofsavannahatfergusonaveandbethesdard",
            "savannahga",
            "bethesdahomeforboys",
            "bethesda"};
        for (int i = 0; i < words.length; ++i) {
            in.put(words[i], i);

            for (String s : getSubstrings(words[i])) {
                Collection<Integer> result = search(in, s);
                assertNotNull("result null for string " + s + " after adding " + words[i], result);
                assertTrue("substring " + s + " not found after adding " + words[i], result.contains(i));
            }
        }
        // verify post-addition
        for (int i = 0; i < words.length; ++i) {
            for (String s : getSubstrings(words[i])) {
                assertTrue(search(in, s).contains(i));
            }
        }

        // add again, to see if it's stable
        for (int i = 0; i < words.length; ++i) {
            in.put(words[i], i + words.length);

            for (String s : getSubstrings(words[i])) {
                assertTrue(search(in, s).contains(i + words.length));
            }
        }

        assertEmpty(search(in, "aoca"));
    }

    /* testing a test method :) */
    public void testGetSubstrings() {
        Collection<String> exp = new HashSet<>(Arrays.asList("w", "r", "d", "wr", "rd", "wrd"));
        Collection<String> ret = getSubstrings("wrd");
        assertEquals(ret, exp);
    }

    public void testSubSearch() {
        GeneralizedSuffixTree<Integer> tree = new GeneralizedSuffixTree<>();

        tree.put("a", 0);
        assertEquals(Set.of(0), search(tree, "a"));

        tree.put("ab", 1);
        assertEquals(Set.of(1), search(tree, "ab"));
        assertEquals(Set.of(1), search(tree, "b"));
        assertEquals(Set.of(0, 1), search(tree, "a"));

        tree.put("cab", 2);
        assertEquals(Set.of(2), search(tree, "cab"));
        assertEquals(Set.of(2), search(tree, "ca"));
        assertEquals(Set.of(2), search(tree, "c"));
        assertEquals(Set.of(1, 2), search(tree, "ab"));
        assertEquals(Set.of(1, 2), search(tree, "b"));
        assertEquals(Set.of(0, 1, 2), search(tree, "a"));

        tree.put("abcabxabcd", 3);
        assertEquals(Set.of(3), search(tree, "abcabxabcd"));
        assertEquals(Set.of(3), search(tree, "abcabxabc"));
        assertEquals(Set.of(3), search(tree, "abcabxab"));
        assertEquals(Set.of(3), search(tree, "abcabxa"));
        assertEquals(Set.of(3), search(tree, "abcabx"));
        assertEquals(Set.of(3), search(tree, "abcab"));
        assertEquals(Set.of(3), search(tree, "abca"));
        assertEquals(Set.of(3), search(tree, "abc"));

        assertEquals(Set.of(3), search(tree, "bcabxabcd"));
        assertEquals(Set.of(3), search(tree, "bcabxabc"));
        assertEquals(Set.of(3), search(tree, "bcabxab"));
        assertEquals(Set.of(3), search(tree, "bcabxa"));
        assertEquals(Set.of(3), search(tree, "bcabx"));
        assertEquals(Set.of(3), search(tree, "bcab"));
        assertEquals(Set.of(3), search(tree, "bca"));
        assertEquals(Set.of(3), search(tree, "bc"));

        assertEquals(Set.of(3), search(tree, "cabxabcd"));
        assertEquals(Set.of(3), search(tree, "cabxabc"));
        assertEquals(Set.of(3), search(tree, "cabxab"));
        assertEquals(Set.of(3), search(tree, "cabxa"));
        assertEquals(Set.of(3), search(tree, "cabx"));

        assertEquals(Set.of(3), search(tree, "abxabcd"));
        assertEquals(Set.of(3), search(tree, "abxabc"));
        assertEquals(Set.of(3), search(tree, "abxab"));
        assertEquals(Set.of(3), search(tree, "abxa"));
        assertEquals(Set.of(3), search(tree, "abx"));

        assertEquals(Set.of(3), search(tree, "bxabcd"));
        assertEquals(Set.of(3), search(tree, "bxabc"));
        assertEquals(Set.of(3), search(tree, "bxab"));
        assertEquals(Set.of(3), search(tree, "bxa"));
        assertEquals(Set.of(3), search(tree, "bx"));

        assertEquals(Set.of(3), search(tree, "xabcd"));
        assertEquals(Set.of(3), search(tree, "xabc"));
        assertEquals(Set.of(3), search(tree, "xab"));
        assertEquals(Set.of(3), search(tree, "xa"));
        assertEquals(Set.of(3), search(tree, "x"));

        assertEquals(Set.of(3), search(tree, "abcd"));
        assertEquals(Set.of(3), search(tree, "abc"));

        assertEquals(Set.of(3), search(tree, "bcd"));

        assertEquals(Set.of(3), search(tree, "d"));

        assertEquals(Set.of(2, 3), search(tree, "cab"));
        assertEquals(Set.of(2, 3), search(tree, "ca"));
        assertEquals(Set.of(2, 3), search(tree, "c"));

        assertEquals(Set.of(1, 2, 3), search(tree, "ab"));
        assertEquals(Set.of(1, 2, 3), search(tree, "b"));
        assertEquals(Set.of(0, 1, 2, 3), search(tree, "a"));
    }

    public void testPuttingSameString() {
        GeneralizedSuffixTree<Integer> tree = new GeneralizedSuffixTree<>();

        tree.put("ab", 0);
        assertEquals(Set.of(0), search(tree, "a"));
        assertEquals(Set.of(0), search(tree, "b"));
        assertEquals(Set.of(0), search(tree, "ab"));

        tree.put("ab", 1);
        assertEquals(Set.of(0, 1), search(tree, "a"));
        assertEquals(Set.of(0, 1), search(tree, "b"));
        assertEquals(Set.of(0, 1), search(tree, "ab"));
    }

    public void testPuttingSameStringAfterSplit() {
        GeneralizedSuffixTree<Integer> tree = new GeneralizedSuffixTree<>();

        tree.put("abcde", 0);
        tree.put("abcde", 1);
        tree.put("abxde", 2);
        tree.put("abcde", 3);

        for (String s : getSubstrings("abcde")) {
            assertTrue("substring " + s + " not found after replaying cached key", search(tree, s).contains(3));
        }
    }

    public void testPuttingShorterString() {
        GeneralizedSuffixTree<Integer> tree = new GeneralizedSuffixTree<>();

        tree.put("ab", 0);
        assertEquals(Set.of(0), search(tree, "a"));
        assertEquals(Set.of(0), search(tree, "b"));
        assertEquals(Set.of(0), search(tree, "ab"));

        tree.put("a", 1);
        assertEquals(Set.of(0, 1), search(tree, "a"));
        assertEquals(Set.of(0), search(tree, "b"));
        assertEquals(Set.of(0), search(tree, "ab"));
    }

    public void testNonMatchingSearches() {
        GeneralizedSuffixTree<Integer> tree = new GeneralizedSuffixTree<>();

        tree.put("ab", 0);
        assertEquals(Set.of(), search(tree, ""));
        assertEquals(Set.of(), search(tree, "abc"));
        assertEquals(Set.of(), search(tree, "ac"));
        assertEquals(Set.of(), search(tree, "ba"));
        assertEquals(Set.of(), search(tree, "c"));
    }

    public void testIndexWorksOutOfOrder() {
        GeneralizedSuffixTree<Integer> tree = new GeneralizedSuffixTree<>();

        tree.put("ab", 10);
        assertEquals(Set.of(10), search(tree, "a"));
        assertEquals(Set.of(10), search(tree, "b"));
        assertEquals(Set.of(10), search(tree, "ab"));

        tree.put("a", 5);
        assertEquals(Set.of(10, 5), search(tree, "a"));
        assertEquals(Set.of(10), search(tree, "b"));
        assertEquals(Set.of(10), search(tree, "ab"));
    }

    public void testUnicode() {
        GeneralizedSuffixTree<Integer> tree = new GeneralizedSuffixTree<>();
        String word = "こんにちは";
        tree.put(word, 1);

        assertEquals(Set.of(1), search(tree, "んに"));
        assertEquals(Set.of(1), search(tree, "に"));
        assertEquals(Set.of(1), search(tree, "は"));
        assertEmpty(search(tree, "さ"));
    }

    public void testSupplementaryCharacters() {
        GeneralizedSuffixTree<Integer> tree = new GeneralizedSuffixTree<>();
        String word = "😀😁😂";
        tree.put(word, 1);

        assertEquals(Set.of(1), search(tree, "😁"));
        assertEquals(Set.of(1), search(tree, "😂"));
        assertEmpty(search(tree, "🤣"));
    }

    public void testSubstringToTermMatching() {
        GeneralizedSuffixTree<Integer> suffixTree = new GeneralizedSuffixTree<>();
        String[] terms = {
                "tablett",
                "fleischtablett",
                "salz",
                "pfeffer",
                "kämpft",
                "grünen",
        };

        for (int i = 0; i < terms.length; i++) {
            suffixTree.put(terms[i], i);
        }

        assertEquals(2, search(suffixTree, "tablett").size());
        assertEquals(2, search(suffixTree, "blet").size());
        assertEquals(1, search(suffixTree, "feff").size());
        assertEquals(1, search(suffixTree, "ün").size());
        assertEquals(1, search(suffixTree, "äm").size());
    }
}
