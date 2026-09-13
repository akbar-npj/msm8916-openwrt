package com.google.common.collect;

import com.google.common.base.Joiner;
import java.util.HashMap;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public final class Maps {
    static final Joiner.MapJoiner STANDARD_JOINER = Collections2.STANDARD_JOINER.withKeyValueSeparator("=");

    private Maps() {
    }

    public static <K, V> HashMap<K, V> newHashMap() {
        return new HashMap<>();
    }

    public static <K, V> Map.Entry<K, V> immutableEntry(K key, V value) {
        return new ImmutableEntry(key, value);
    }

    static String toStringImpl(Map<?, ?> map) {
        StringBuilder sb = Collections2.newStringBuilderForCollection(map.size()).append('{');
        STANDARD_JOINER.appendTo(sb, map);
        return sb.append('}').toString();
    }
}
