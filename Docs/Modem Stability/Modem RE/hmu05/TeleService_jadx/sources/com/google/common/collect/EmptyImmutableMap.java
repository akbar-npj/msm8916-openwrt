package com.google.common.collect;

import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
final class EmptyImmutableMap extends ImmutableMap<Object, Object> {
    static final EmptyImmutableMap INSTANCE = new EmptyImmutableMap();
    private static final long serialVersionUID = 0;

    private EmptyImmutableMap() {
    }

    @Override // com.google.common.collect.ImmutableMap, java.util.Map
    public Object get(Object key) {
        return null;
    }

    @Override // java.util.Map
    public int size() {
        return 0;
    }

    @Override // com.google.common.collect.ImmutableMap, java.util.Map
    public boolean isEmpty() {
        return true;
    }

    @Override // com.google.common.collect.ImmutableMap, java.util.Map
    public boolean containsKey(Object key) {
        return false;
    }

    @Override // java.util.Map
    public boolean containsValue(Object value) {
        return false;
    }

    @Override // com.google.common.collect.ImmutableMap, java.util.Map
    public ImmutableSet<Map.Entry<Object, Object>> entrySet() {
        return ImmutableSet.of();
    }

    @Override // com.google.common.collect.ImmutableMap, java.util.Map
    public ImmutableSet<Object> keySet() {
        return ImmutableSet.of();
    }

    @Override // com.google.common.collect.ImmutableMap, java.util.Map
    public ImmutableCollection<Object> values() {
        return ImmutableCollection.EMPTY_IMMUTABLE_COLLECTION;
    }

    @Override // com.google.common.collect.ImmutableMap, java.util.Map
    public boolean equals(Object object) {
        if (!(object instanceof Map)) {
            return false;
        }
        Map<?, ?> that = (Map) object;
        return that.isEmpty();
    }

    @Override // com.google.common.collect.ImmutableMap, java.util.Map
    public int hashCode() {
        return 0;
    }

    @Override // com.google.common.collect.ImmutableMap
    public String toString() {
        return "{}";
    }

    Object readResolve() {
        return INSTANCE;
    }
}
