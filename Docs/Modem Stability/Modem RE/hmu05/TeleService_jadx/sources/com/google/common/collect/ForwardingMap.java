package com.google.common.collect;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/* JADX INFO: loaded from: classes.dex */
public abstract class ForwardingMap<K, V> extends ForwardingObject implements Map<K, V> {
    /* JADX INFO: Access modifiers changed from: protected */
    @Override // com.google.common.collect.ForwardingObject
    public abstract Map<K, V> delegate();

    protected ForwardingMap() {
    }

    @Override // java.util.Map
    public int size() {
        return delegate().size();
    }

    @Override // java.util.Map
    public boolean isEmpty() {
        return delegate().isEmpty();
    }

    @Override // java.util.Map
    public V remove(Object object) {
        return delegate().remove(object);
    }

    @Override // java.util.Map
    public void clear() {
        delegate().clear();
    }

    @Override // java.util.Map
    public boolean containsKey(Object key) {
        return delegate().containsKey(key);
    }

    @Override // java.util.Map
    public boolean containsValue(Object value) {
        return delegate().containsValue(value);
    }

    @Override // java.util.Map
    public V get(Object key) {
        return delegate().get(key);
    }

    @Override // java.util.Map
    public V put(K key, V value) {
        return delegate().put(key, value);
    }

    @Override // java.util.Map
    public void putAll(Map<? extends K, ? extends V> map) {
        delegate().putAll(map);
    }

    @Override // java.util.Map
    public Set<K> keySet() {
        return delegate().keySet();
    }

    @Override // java.util.Map
    public Collection<V> values() {
        return delegate().values();
    }

    @Override // java.util.Map
    public Set<Map.Entry<K, V>> entrySet() {
        return delegate().entrySet();
    }

    @Override // java.util.Map
    public boolean equals(Object object) {
        return object == this || delegate().equals(object);
    }

    @Override // java.util.Map
    public int hashCode() {
        return delegate().hashCode();
    }
}
