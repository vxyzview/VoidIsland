package com.vxyzview.voidisland;

import java.util.LinkedHashMap;
import java.util.Map;

/** Size-bounded least-recently-used map: the oldest entry goes first, instead of everything being wiped at the cap. Pure Java. */
final class Lru {
    private Lru() {}

    static <K, V> LinkedHashMap<K, V> map(final int max) {
        return new LinkedHashMap<K, V>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<K, V> eldest) { return size() > max; }
        };
    }
}
