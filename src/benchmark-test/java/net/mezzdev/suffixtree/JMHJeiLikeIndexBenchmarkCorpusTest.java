package net.mezzdev.suffixtree;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JMHJeiLikeIndexBenchmarkCorpusTest {
    private static final int ITEM_COUNT = 10_000;
    private static final int REFERENCE_ITEM_COUNT = 92_617;
    private static final int REFERENCE_ITEM_NAME_UNIQUE_COUNT = 74_364;
    private static final int REFERENCE_TOOLTIP_LINE_COUNT = 675_660;
    private static final int REFERENCE_TOOLTIP_UNIQUE_COUNT = 11_810;
    private static final int REFERENCE_MOD_NAME_COUNT = 398;
    private static final int REFERENCE_TAG_COUNT = 6_876;

    @Test
    public void itemNameCorpusHasJeiLikeDuplicateRateAtTenThousandItems() {
        JMHJeiLikeIndexBenchmark.ItemRef[] items = JMHJeiLikeIndexBenchmark.createItems(ITEM_COUNT);
        JMHJeiLikeIndexBenchmark.Corpus corpus = JMHJeiLikeIndexBenchmark.createItemNameCorpus(items);

        String[] names = corpus.keys();
        Set<String> uniqueNames = new HashSet<>(Arrays.asList(names));

        assertEquals(ITEM_COUNT, names.length);
        assertEquals(ITEM_COUNT, corpus.values().length);
        assertEquals(scaledCount(ITEM_COUNT, REFERENCE_ITEM_NAME_UNIQUE_COUNT), uniqueNames.size());
        assertTrue(uniqueNames.stream().noneMatch(JMHJeiLikeIndexBenchmarkCorpusTest::endsWithNumericSuffix));
    }

    @Test
    public void itemNameCorpusUsesBroadSyntheticVocabularyAtTenThousandItems() {
        JMHJeiLikeIndexBenchmark.ItemRef[] items = JMHJeiLikeIndexBenchmark.createItems(ITEM_COUNT);
        JMHJeiLikeIndexBenchmark.Corpus corpus = JMHJeiLikeIndexBenchmark.createItemNameCorpus(items);

        Map<String, Integer> tokenCounts = new HashMap<>();
        for (String name : corpus.keys()) {
            Set<String> tokensInName = new HashSet<>(Arrays.asList(name.split(" ")));
            for (String token : tokensInName) {
                tokenCounts.merge(token, 1, Integer::sum);
            }
        }

        int maxAllowedAppearances = ITEM_COUNT / 6;
        int maxActualAppearances = tokenCounts.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElseThrow();

        assertTrue(tokenCounts.size() >= 60);
        assertTrue(maxActualAppearances <= maxAllowedAppearances);
    }

    @Test
    public void tooltipCorpusUsesJeiLikeLineCountAndRepetitionAtTenThousandItems() {
        JMHJeiLikeIndexBenchmark.ItemRef[] items = JMHJeiLikeIndexBenchmark.createItems(ITEM_COUNT);
        JMHJeiLikeIndexBenchmark.Corpus corpus = JMHJeiLikeIndexBenchmark.createTooltipCorpus(items);

        String[] tooltips = corpus.keys();
        Set<String> uniqueTooltips = new HashSet<>(Arrays.asList(tooltips));

        assertEquals(scaledCount(ITEM_COUNT, REFERENCE_TOOLTIP_LINE_COUNT), tooltips.length);
        assertEquals(tooltips.length, corpus.values().length);
        assertEquals(scaledCount(tooltips.length, REFERENCE_TOOLTIP_UNIQUE_COUNT, REFERENCE_TOOLTIP_LINE_COUNT), uniqueTooltips.size());
    }

    @Test
    public void defaultCorporaScaleLikeJeiDumpAtTenThousandItems() {
        JMHJeiLikeIndexBenchmark.ItemRef[] items = JMHJeiLikeIndexBenchmark.createItems(ITEM_COUNT);
        JMHJeiLikeIndexBenchmark.Corpus itemNames = JMHJeiLikeIndexBenchmark.createItemNameCorpus(items);
        JMHJeiLikeIndexBenchmark.Corpus tooltips = JMHJeiLikeIndexBenchmark.createTooltipCorpus(items);
        JMHJeiLikeIndexBenchmark.Corpus modNames = JMHJeiLikeIndexBenchmark.createModNameCorpus(ITEM_COUNT);
        JMHJeiLikeIndexBenchmark.Corpus tags = JMHJeiLikeIndexBenchmark.createTagCorpus(ITEM_COUNT);
        JMHJeiLikeIndexBenchmark.Corpus allDefaults = JMHJeiLikeIndexBenchmark.concatenateCorpora(itemNames, tooltips, modNames, tags);

        assertEquals(ITEM_COUNT, itemNames.keys().length);
        assertEquals(scaledCount(ITEM_COUNT, REFERENCE_TOOLTIP_LINE_COUNT), tooltips.keys().length);
        assertEquals(scaledCount(ITEM_COUNT, REFERENCE_MOD_NAME_COUNT), modNames.keys().length);
        assertEquals(scaledCount(ITEM_COUNT, REFERENCE_TAG_COUNT), tags.keys().length);
        assertEquals(
                itemNames.keys().length + tooltips.keys().length + modNames.keys().length + tags.keys().length,
                allDefaults.keys().length
        );
    }

    private static boolean endsWithNumericSuffix(String name) {
        int lastSpace = name.lastIndexOf(' ');
        if (lastSpace < 0) {
            return false;
        }
        String lastToken = name.substring(lastSpace + 1);
        return lastToken.chars().allMatch(Character::isDigit);
    }

    private static int scaledCount(int count, int referenceNumerator) {
        return scaledCount(count, referenceNumerator, REFERENCE_ITEM_COUNT);
    }

    private static int scaledCount(int count, int referenceNumerator, int referenceDenominator) {
        return Math.max(1, (int) Math.round(count * (referenceNumerator / (double) referenceDenominator)));
    }
}
