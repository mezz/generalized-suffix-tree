package net.mezzdev.suffixtree;

import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(3)
public class JMHJeiLikeIndexBenchmark {
    // Aggregate shape from a large JEI debug dump. These are counts only; no dump strings are embedded.
    private static final int REFERENCE_ITEM_COUNT = 92_617;
    private static final int REFERENCE_ITEM_NAME_UNIQUE_COUNT = 74_364;
    private static final int REFERENCE_TOOLTIP_LINE_COUNT = 675_660;
    private static final int REFERENCE_TOOLTIP_UNIQUE_COUNT = 11_810;
    private static final int REFERENCE_MOD_NAME_COUNT = 398;
    private static final int REFERENCE_TAG_COUNT = 6_876;

    // Synthetic short tokens only; avoid copying JEI dump text or dictionary corpora into benchmark fixtures.
    private static final String[] MODIFIERS = {
            "raw", "cut", "polished", "refined", "charged", "reinforced", "tempered", "gilded",
            "woven", "etched", "stable", "dense", "bright", "silent", "rough", "smooth"
    };
    private static final String[] MATERIALS = {
            "copper", "iron", "tin", "silver", "lead", "zinc", "nickel", "brass", "bronze",
            "steel", "glass", "quartz", "slate", "basalt", "ruby", "sapphire", "amber", "jade",
            "opal", "topaz", "willow", "cedar", "maple", "hemp", "flax", "cobalt", "osmium"
    };
    private static final String[] ITEM_TYPES = {
            "ingot", "nugget", "plate", "rod", "gear", "wire", "coil", "dust", "crystal",
            "gem", "block", "brick", "plank", "panel", "hammer", "wrench", "drill", "saw",
            "pickaxe", "shovel", "axe", "helmet", "chestplate", "leggings", "boots", "ring",
            "amulet", "cell", "tank", "pipe", "filter", "module"
    };
    private static final String[] SERIES = {
            "alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta"
    };
    private static final String[] HOT_TOOLTIP_LINES = {
            "shift", "ctrl", "info", "uses", "empty", "full", "on", "off", "hold", "more"
    };
    private static final String[] COMMON_TOOLTIP_LINES = {
            "energy", "armor", "speed", "damage", "stored", "fluid", "tier 1", "tier 2",
            "active", "ready", "locked", "hidden", "stable", "recipe", "input", "output",
            "bonus", "range", "charge", "filter", "slot a", "slot b", "mode x", "mode y"
    };
    private static final String[] TAG_DOMAINS = {
            "ingots", "nuggets", "plates", "rods", "gears", "wires", "dusts", "gems",
            "blocks", "tools", "armor", "fluids", "storage", "filters", "modules", "circuits"
    };
    private static final String[] MOD_NAME_PREFIXES = {
            "Aster", "Beacon", "Cinder", "Delta", "Echo", "Fable", "Glyph", "Harbor",
            "Iris", "Jade", "Keystone", "Lumen", "Mosaic", "Nimbus", "Orbit", "Prism",
            "Quartz", "Relay", "Signal", "Tangent", "Umber", "Vector", "Willow", "Zenith"
    };
    private static final String[] MOD_NAME_SUFFIXES = {
            "Works", "Labs", "Grid", "Tools", "Systems", "Craft", "Foundry", "Logic",
            "Engine", "Fields", "Depot", "Circuit", "Forge", "Archive", "Control", "Core",
            "Link", "Vault", "Tower", "Garden"
    };
    private static volatile Object retainedTree;

    @State(Scope.Benchmark)
    public static class CorpusState {
        @Param({"1000", "10000", "100000"})
        public int itemCount;

        private Corpus itemNames;
        private Corpus tooltips;
        private Corpus modNames;
        private Corpus tags;
        private Corpus allDefaults;

        @Setup(Level.Trial)
        public void setup() {
            ItemRef[] items = createItems(itemCount);
            itemNames = createItemNameCorpus(items);
            tooltips = createTooltipCorpus(items);
            modNames = createModNameCorpus(itemCount);
            tags = createTagCorpus(itemCount);
            allDefaults = concatenateCorpora(itemNames, tooltips, modNames, tags);
        }
    }

    @State(Scope.Benchmark)
    public static class SearchState {
        @Param({"1000", "10000", "100000"})
        public int itemCount;

        private GeneralizedSuffixTree<ItemRef> itemNameTree;
        private GeneralizedSuffixTree<ItemRef> tooltipTree;
        private GeneralizedSuffixTree<ItemRef> modNameTree;
        private GeneralizedSuffixTree<ItemRef> tagTree;
        private GeneralizedSuffixTree<ItemRef> allDefaultsTree;

        @Setup(Level.Trial)
        public void setup() {
            ItemRef[] items = createItems(itemCount);
            Corpus itemNames = createItemNameCorpus(items);
            Corpus tooltips = createTooltipCorpus(items);
            Corpus modNames = createModNameCorpus(itemCount);
            Corpus tags = createTagCorpus(itemCount);

            itemNameTree = buildTree(itemNames);
            tooltipTree = buildTree(tooltips);
            modNameTree = buildTree(modNames);
            tagTree = buildTree(tags);
            allDefaultsTree = buildTree(concatenateCorpora(itemNames, tooltips, modNames, tags));
        }
    }

    @State(Scope.Thread)
    @AuxCounters(AuxCounters.Type.EVENTS)
    public static class RetainedMemoryCounters {
        public long retainedBytes;

        @TearDown(Level.Trial)
        public void tearDown() {
            retainedTree = null;
        }
    }

    @Benchmark
    public GeneralizedSuffixTree<ItemRef> buildItemNameIndex(CorpusState state) {
        return buildTree(state.itemNames);
    }

    @Benchmark
    public GeneralizedSuffixTree<ItemRef> buildTooltipIndex(CorpusState state) {
        return buildTree(state.tooltips);
    }

    @Benchmark
    public GeneralizedSuffixTree<ItemRef> buildModNameIndex(CorpusState state) {
        return buildTree(state.modNames);
    }

    @Benchmark
    public GeneralizedSuffixTree<ItemRef> buildTagIndex(CorpusState state) {
        return buildTree(state.tags);
    }

    @Benchmark
    public GeneralizedSuffixTree<ItemRef> buildAllDefaultsIndex(CorpusState state) {
        return buildTree(state.allDefaults);
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = 1, batchSize = 1)
    @Measurement(iterations = 5, batchSize = 1)
    public long retainedItemNameIndexBytes(CorpusState state, RetainedMemoryCounters counters) throws InterruptedException {
        return retainedBytes(state.itemNames, counters);
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = 1, batchSize = 1)
    @Measurement(iterations = 5, batchSize = 1)
    public long retainedTooltipIndexBytes(CorpusState state, RetainedMemoryCounters counters) throws InterruptedException {
        return retainedBytes(state.tooltips, counters);
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = 1, batchSize = 1)
    @Measurement(iterations = 5, batchSize = 1)
    public long retainedModNameIndexBytes(CorpusState state, RetainedMemoryCounters counters) throws InterruptedException {
        return retainedBytes(state.modNames, counters);
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = 1, batchSize = 1)
    @Measurement(iterations = 5, batchSize = 1)
    public long retainedTagIndexBytes(CorpusState state, RetainedMemoryCounters counters) throws InterruptedException {
        return retainedBytes(state.tags, counters);
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = 1, batchSize = 1)
    @Measurement(iterations = 5, batchSize = 1)
    public long retainedAllDefaultsIndexBytes(CorpusState state, RetainedMemoryCounters counters) throws InterruptedException {
        return retainedBytes(state.allDefaults, counters);
    }

    @Benchmark
    public int searchItemNameIndex(SearchState state) {
        return search(state.itemNameTree, "copper", "gear", "reinforced", "drill", "ingot", "not-present");
    }

    @Benchmark
    public int searchTooltipIndex(SearchState state) {
        return search(state.tooltipTree, "shift", "energy", "tier", "slot", "stat", "not-present");
    }

    @Benchmark
    public int searchModNameIndex(SearchState state) {
        return search(state.modNameTree, "Aster", "Works", "Grid", "Core", "not-present");
    }

    @Benchmark
    public int searchTagIndex(SearchState state) {
        return search(state.tagTree, "ingots", "bench", "tools", "storage", "not-present");
    }

    @Benchmark
    public int searchAllDefaultsIndex(SearchState state) {
        return search(state.allDefaultsTree, "copper", "shift", "Aster", "ingots", "not-present");
    }

    static ItemRef[] createItems(int itemCount) {
        ItemRef[] items = new ItemRef[itemCount];
        for (int i = 0; i < itemCount; i++) {
            items[i] = new ItemRef(i);
        }
        return items;
    }

    static Corpus createItemNameCorpus(ItemRef[] items) {
        int uniqueNameCount = scaledCount(items.length, REFERENCE_ITEM_NAME_UNIQUE_COUNT);
        String[] uniqueNames = new String[uniqueNameCount];
        for (int i = 0; i < uniqueNames.length; i++) {
            uniqueNames[i] = itemName(i);
        }

        String[] keys = new String[items.length];
        ItemRef[] values = new ItemRef[items.length];
        for (int i = 0; i < items.length; i++) {
            String key;
            if (i < uniqueNameCount) {
                key = uniqueNames[i];
            } else {
                key = uniqueNames[duplicateItemNameIndex(i - uniqueNameCount, uniqueNameCount)];
            }
            keys[i] = copyKey(key);
            values[i] = new ItemRef(i);
        }
        return new Corpus(keys, values);
    }

    static Corpus createTooltipCorpus(ItemRef[] items) {
        int lineCount = scaledCount(items.length, REFERENCE_TOOLTIP_LINE_COUNT);
        int targetUniqueLineCount = scaledCount(lineCount, REFERENCE_TOOLTIP_UNIQUE_COUNT, REFERENCE_TOOLTIP_LINE_COUNT);
        int fixedTooltipLineCount = HOT_TOOLTIP_LINES.length + COMMON_TOOLTIP_LINES.length;
        int uniqueTailLineCount = Math.max(1, targetUniqueLineCount - fixedTooltipLineCount);
        String[] tailLines = createTooltipTailLines(uniqueTailLineCount);
        String[] keys = new String[lineCount];
        ItemRef[] values = new ItemRef[keys.length];

        int index = 0;
        for (int i = 0; i < items.length; i++) {
            int linesForItem = lineCount / items.length;
            if (i < lineCount % items.length) {
                linesForItem++;
            }
            for (int line = 0; line < linesForItem; line++) {
                index = putLine(keys, values, index, tooltipLine(i, line, tailLines));
            }
        }

        return new Corpus(keys, values);
    }

    static Corpus createModNameCorpus(int itemCount) {
        int lineCount = scaledCount(itemCount, REFERENCE_MOD_NAME_COUNT);
        String[] keys = new String[lineCount];
        ItemRef[] values = new ItemRef[lineCount];
        for (int i = 0; i < lineCount; i++) {
            putLine(keys, values, i, modName(i));
        }
        return new Corpus(keys, values);
    }

    static Corpus createTagCorpus(int itemCount) {
        int lineCount = scaledCount(itemCount, REFERENCE_TAG_COUNT);
        String[] keys = new String[lineCount];
        ItemRef[] values = new ItemRef[lineCount];
        for (int i = 0; i < lineCount; i++) {
            putLine(keys, values, i, tagName(i));
        }
        return new Corpus(keys, values);
    }

    static Corpus concatenateCorpora(Corpus... corpora) {
        int lineCount = 0;
        for (Corpus corpus : corpora) {
            lineCount += corpus.keys().length;
        }

        String[] keys = new String[lineCount];
        ItemRef[] values = new ItemRef[lineCount];
        int index = 0;
        for (Corpus corpus : corpora) {
            for (String key : corpus.keys()) {
                keys[index] = key;
                values[index] = new ItemRef(index);
                index++;
            }
        }
        return new Corpus(keys, values);
    }

    private static int putLine(String[] keys, ItemRef[] values, int index, String key) {
        keys[index] = copyKey(key);
        values[index] = new ItemRef(index);
        return index + 1;
    }

    private static int scaledCount(int itemCount, int referenceLineCount) {
        return scaledCount(itemCount, referenceLineCount, REFERENCE_ITEM_COUNT);
    }

    private static int scaledCount(int count, int referenceNumerator, int referenceDenominator) {
        return Math.max(1, (int) Math.round(count * (referenceNumerator / (double) referenceDenominator)));
    }

    private static int duplicateItemNameIndex(int duplicateIndex, int uniqueNameCount) {
        int hotNameCount = Math.min(64, uniqueNameCount);
        if (duplicateIndex % 4 != 3) {
            return Math.floorMod((duplicateIndex % hotNameCount) * 1_301 + 17, uniqueNameCount);
        }
        return Math.floorMod(duplicateIndex * 1_103_515_245 + 12_345, uniqueNameCount);
    }

    private static String itemName(int index) {
        int modifierIndex = index % MODIFIERS.length;
        int materialIndex = (index / MODIFIERS.length) % MATERIALS.length;
        int typeIndex = (index / (MODIFIERS.length * MATERIALS.length)) % ITEM_TYPES.length;
        int seriesIndex = (index / (MODIFIERS.length * MATERIALS.length * ITEM_TYPES.length)) % SERIES.length;
        int generation = index / (MODIFIERS.length * MATERIALS.length * ITEM_TYPES.length * SERIES.length);

        String name = MODIFIERS[modifierIndex] + " " + MATERIALS[materialIndex] + " " + ITEM_TYPES[typeIndex];
        if (seriesIndex != 0 || generation != 0) {
            name += " " + SERIES[seriesIndex];
        }
        if (generation != 0) {
            name += " " + generation;
        }
        return name;
    }

    private static String[] createTooltipTailLines(int lineCount) {
        String[] lines = new String[lineCount];
        for (int i = 0; i < lines.length; i++) {
            String code = Integer.toString(i, Character.MAX_RADIX);
            if (i % 97 == 0) {
                lines[i] = "stat " + code + " detail";
            } else {
                lines[i] = "s " + code;
            }
        }
        return lines;
    }

    private static String tooltipLine(int itemIndex, int lineIndex, String[] tailLines) {
        int hash = mix(itemIndex * 31 + lineIndex);
        int slot = Math.floorMod(lineIndex, 8);
        if (slot < 2) {
            return HOT_TOOLTIP_LINES[Math.floorMod(hash, HOT_TOOLTIP_LINES.length)];
        }
        if (slot < 5) {
            return COMMON_TOOLTIP_LINES[Math.floorMod(hash, COMMON_TOOLTIP_LINES.length)];
        }
        return tailLines[Math.floorMod(hash, tailLines.length)];
    }

    private static String modName(int index) {
        String name = MOD_NAME_PREFIXES[index % MOD_NAME_PREFIXES.length] + " " +
                MOD_NAME_SUFFIXES[(index / MOD_NAME_PREFIXES.length) % MOD_NAME_SUFFIXES.length];
        int generation = index / (MOD_NAME_PREFIXES.length * MOD_NAME_SUFFIXES.length);
        if (generation != 0) {
            name += " " + SERIES[generation % SERIES.length];
        }
        return name;
    }

    private static String tagName(int index) {
        String domain = TAG_DOMAINS[index % TAG_DOMAINS.length];
        String material = MATERIALS[(index / TAG_DOMAINS.length) % MATERIALS.length];
        String type = ITEM_TYPES[(index / (TAG_DOMAINS.length * MATERIALS.length)) % ITEM_TYPES.length];
        int generation = index / (TAG_DOMAINS.length * MATERIALS.length * ITEM_TYPES.length);
        String tag = "bench:" + domain + "/" + material + "_" + type;
        if (generation != 0) {
            tag += "/" + SERIES[generation % SERIES.length];
        }
        return tag;
    }

    private static int mix(int value) {
        int h = value * 0x45d9f3b;
        h ^= h >>> 16;
        return h;
    }

    private static String copyKey(String key) {
        return new String(key.toCharArray());
    }

    private static GeneralizedSuffixTree<ItemRef> buildTree(Corpus corpus) {
        GeneralizedSuffixTree<ItemRef> tree = new GeneralizedSuffixTree<>();
        String[] keys = corpus.keys();
        ItemRef[] values = corpus.values();
        for (int i = 0; i < keys.length; i++) {
            tree.put(keys[i], values[i]);
        }
        return tree;
    }

    private static long retainedBytes(Corpus corpus, RetainedMemoryCounters counters) throws InterruptedException {
        retainedTree = null;
        forceGc();
        long beforeBytes = usedHeapBytes();

        retainedTree = buildTree(corpus);

        forceGc();
        long retainedBytes = Math.max(0, usedHeapBytes() - beforeBytes);
        counters.retainedBytes = retainedBytes;
        return retainedBytes;
    }

    private static void forceGc() throws InterruptedException {
        for (int i = 0; i < 6; i++) {
            System.gc();
            Thread.sleep(100);
        }
    }

    private static long usedHeapBytes() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    private static int search(GeneralizedSuffixTree<ItemRef> tree, String... queries) {
        SearchResultCounter counter = new SearchResultCounter();
        int total = 0;
        for (String query : queries) {
            counter.reset();
            tree.getSearchResults(query, counter);
            total += counter.count();
        }
        return total;
    }

    record Corpus(String[] keys, ItemRef[] values) {
    }

    public record ItemRef(int id) {
    }

    private static final class SearchResultCounter implements Consumer<Collection<ItemRef>> {
        private int count;

        @Override
        public void accept(Collection<ItemRef> results) {
            count += results.size();
        }

        public void reset() {
            count = 0;
        }

        public int count() {
            return count;
        }
    }
}
