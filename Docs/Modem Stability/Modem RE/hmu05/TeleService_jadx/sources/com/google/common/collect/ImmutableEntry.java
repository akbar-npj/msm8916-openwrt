package com.google.common.collect;

import java.io.Serializable;

/* JADX INFO: loaded from: classes.dex */
class ImmutableEntry<K, V> extends AbstractMapEntry<K, V> implements Serializable {
    private static final long serialVersionUID = 0;
    private final K key;
    private final V value;

    ImmutableEntry(K key, V value) {
        this.key = key;
        this.value = value;
    }

    @Override // com.google.common.collect.AbstractMapEntry, java.util.Map.Entry
    public K getKey() {
        return this.key;
    }

    @Override // com.google.common.collect.AbstractMapEntry, java.util.Map.Entry
    public V getValue() {
        return this.value;
    }

    @Override // com.google.common.collect.AbstractMapEntry, java.util.Map.Entry
    public final V setValue(V value) {
        throw new UnsupportedOperationException();
    }
}
