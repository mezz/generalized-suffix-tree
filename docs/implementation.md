# Implementation Notes

This page shows the example `GeneralizedSuffixTree` build from the README in more detail.

The example inserts three key/value pairs:

```java
GeneralizedSuffixTree<String> tree = new GeneralizedSuffixTree<>();
tree.put("banana", "fruit-0");
tree.put("bandana", "cloth-1");
tree.put("cabana", "hut-2");
```

The diagrams were translated from `tree.printTree(out, true)` output for the same insertions.

The regular view shows compressed search edges. The suffix-link view shows construction links used by Ukkonen's algorithm. Suffix links point from a path to the node representing the same path with its first character removed.

Node labels use compact text such as `banana fruit-0`. The path name comes first; any directly stored values follow it. Direct node values are not always the full search result, because search also collects descendant values below the matched node.

## After `banana`

### Regular View

```mermaid
flowchart TD
    r1((root))

    r1 -->|"banana"| banana1["banana fruit-0"]
    r1 -->|"a"| a1["a fruit-0"]
    a1 -->|"na"| ana1["ana fruit-0"]
    ana1 -->|"na"| anana1["anana fruit-0"]
    r1 -->|"na"| na1["na fruit-0"]
    na1 -->|"na"| nana1["nana fruit-0"]
```

### Suffix-Link View

```mermaid
flowchart LR
    banana1s["banana"] -.-> anana1s["anana"]
    anana1s -.-> nana1s["nana"]
    nana1s -.-> ana1s["ana"]
    ana1s -.-> na1s["na"]
    na1s -.-> a1s["a"]
    a1s -.-> root1s((root))
```

## After `bandana`

### Regular View

```mermaid
flowchart TD
    r2((root))

    r2 -->|"ban"| ban2["ban"]
    ban2 -->|"ana"| banana2["banana fruit-0"]
    ban2 -->|"dana"| bandana2["bandana cloth-1"]

    r2 -->|"a"| a2["a fruit-0 cloth-1"]
    a2 -->|"n"| an2["an"]
    an2 -->|"a"| ana2["ana fruit-0 cloth-1"]
    ana2 -->|"na"| anana2["anana fruit-0"]
    an2 -->|"dana"| andana2["andana cloth-1"]

    r2 -->|"n"| n2["n"]
    n2 -->|"a"| na2["na fruit-0 cloth-1"]
    na2 -->|"na"| nana2["nana fruit-0"]
    n2 -->|"dana"| ndana2["ndana cloth-1"]

    r2 -->|"dana"| dana2["dana cloth-1"]
```

### Suffix-Link View

```mermaid
flowchart LR
    ban2s["ban"] -.-> an2s["an"]
    an2s -.-> n2s["n"]
    n2s -.-> root2s((root))

    banana2s["banana"] -.-> anana2s["anana"]
    anana2s -.-> nana2s["nana"]
    nana2s -.-> ana2s["ana"]
    ana2s -.-> na2s["na"]
    na2s -.-> a2s["a"]
    a2s -.-> root2s

    bandana2s["bandana"] -.-> andana2s["andana"]
    andana2s -.-> ndana2s["ndana"]
    ndana2s -.-> dana2s["dana"]
    dana2s -.-> ana2s
```

## After `cabana`

### Regular View

```mermaid
flowchart TD
    r3((root))

    r3 -->|"ban"| ban3["ban"]
    ban3 -->|"a"| bana3["bana hut-2"]
    bana3 -->|"na"| banana3["banana fruit-0"]
    ban3 -->|"dana"| bandana3["bandana cloth-1"]

    r3 -->|"a"| a3["a fruit-0 cloth-1 hut-2"]
    a3 -->|"n"| an3["an"]
    an3 -->|"a"| ana3["ana fruit-0 cloth-1 hut-2"]
    ana3 -->|"na"| anana3["anana fruit-0"]
    an3 -->|"dana"| andana3["andana cloth-1"]
    a3 -->|"bana"| abana3["abana hut-2"]

    r3 -->|"n"| n3["n"]
    n3 -->|"a"| na3["na fruit-0 cloth-1 hut-2"]
    na3 -->|"na"| nana3["nana fruit-0"]
    n3 -->|"dana"| ndana3["ndana cloth-1"]

    r3 -->|"dana"| dana3["dana cloth-1"]
    r3 -->|"cabana"| cabana3["cabana hut-2"]
```

### Suffix-Link View

```mermaid
flowchart LR
    ban3s["ban"] -.-> an3s["an"]
    an3s -.-> n3s["n"]
    n3s -.-> root3s((root))

    cabana3s["cabana"] -.-> abana3s["abana"]
    abana3s -.-> bana3s["bana"]
    bana3s -.-> ana3s["ana"]
    ana3s -.-> na3s["na"]
    na3s -.-> a3s["a"]
    a3s -.-> root3s

    banana3s["banana"] -.-> anana3s["anana"]
    anana3s -.-> nana3s["nana"]
    nana3s -.-> ana3s

    bandana3s["bandana"] -.-> andana3s["andana"]
    andana3s -.-> ndana3s["ndana"]
    ndana3s -.-> dana3s["dana"]
    dana3s -.-> ana3s
```

## Reading The Diagrams

Search only follows the regular compressed edges from the root. For example, searching `bana` starts from the root, follows the `ban` edge, then matches the first character of the `a` edge below it. The query can finish in the middle of a compressed edge; the implementation still collects values below the matched implicit path.

Suffix links are construction shortcuts. They are used while adding text so the algorithm can move from one active suffix state to the next without restarting from the root each time. They are not followed by `getSearchResults`.
