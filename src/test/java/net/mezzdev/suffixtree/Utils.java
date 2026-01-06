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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Utils {
    /**
     * Computes the set of all the substrings contained within the <tt>str</tt>.
     * <p>
     * It is fairly expensive as it computes all the possible substrings.
     *
     * @param str the string to compute substrings of
     * @return the set of all possible substrings of str
     */
    public static Set<String> getSubstrings(String str) {
        if (str.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> ret = new HashSet<>();
        int[] codePoints = str.codePoints().toArray();

        // compute all substrings
        for (int len = 1; len <= codePoints.length; ++len) {
            for (int start = 0; start + len <= codePoints.length; ++start) {
                String itstr = new String(codePoints, start, len);
                ret.add(itstr);
            }
        }

        return ret;
    }
}
