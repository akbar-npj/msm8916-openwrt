package com.google.common.collect;

import com.google.common.base.Preconditions;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public abstract class ImmutableMap<K, V> implements Map<K, V>, Serializable {
    @Override // java.util.Map
    public abstract ImmutableSet<Map.Entry<K, V>> entrySet();

    @Override // java.util.Map
    public abstract V get(Object obj);

    @Override // java.util.Map
    public abstract ImmutableSet<K> keySet();

    @Override // java.util.Map
    public abstract ImmutableCollection<V> values();

    public static <K, V> ImmutableMap<K, V> of() {
        return EmptyImmutableMap.INSTANCE;
    }

    public static <K, V> Builder<K, V> builder() {
        return new Builder<>();
    }

    static <K, V> Map.Entry<K, V> entryOf(K key, V value) {
        return Maps.immutableEntry(Preconditions.checkNotNull(key, "null key"), Preconditions.checkNotNull(value, "null value"));
    }

    public static class Builder<K, V> {
        final ArrayList<Map.Entry<K, V>> entries = Lists.newArrayList();

        public Builder<K, V> put(K key, V value) {
            this.entries.add(ImmutableMap.entryOf(key, value));
            return this;
        }

        public ImmutableMap<K, V> build() {
            return fromEntryList(this.entries);
        }

        private static <K, V> ImmutableMap<K, V> fromEntryList(List<Map.Entry<K, V>> entries) {
            int size = entries.size();
            switch (size) {
                case 0:
                    return ImmutableMap.of();
                case 1:
                    return new SingletonImmutableMap((Map.Entry) Iterables.getOnlyElement(entries));
                default:
                    Map.Entry<?, ?>[] entryArray = (Map.Entry[]) entries.toArray(new Map.Entry[entries.size()]);
                    return new RegularImmutableMap(entryArray);
            }
        }
    }

    ImmutableMap() {
    }

    @Override // java.util.Map
    public final V put(K k, V v) {
        throw new UnsupportedOperationException();
    }

    @Override // java.util.Map
    public final V remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override // java.util.Map
    public final void putAll(Map<? extends K, ? extends V> map) {
        throw new UnsupportedOperationException();
    }

    @Override // java.util.Map
    public final void clear() {
        throw new UnsupportedOperationException();
    }

    @Override // java.util.Map
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override // java.util.Map
    public boolean containsKey(Object key) {
        return get(key) != null;
    }

    @Override // java.util.Map
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }
        if (object instanceof Map) {
            Map<?, ?> that = (Map) object;
            return entrySet().equals(that.entrySet());
        }
        return false;
    }

    @Override // java.util.Map
    public int hashCode() {
        return entrySet().hashCode();
    }

    public String toString() {
        return Maps.toStringImpl(this);
    }

    static class SerializedForm implements Serializable {
        private static final long serialVersionUID = 0;
        private final Object[] keys;
        private final Object[] values;

        SerializedForm(ImmutableMap<?, ?> map) {
            this.keys = new Object[map.size()];
            this.values = new Object[map.size()];
            int i = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                this.keys[i] = entry.getKey();
                this.values[i] = entry.getValue();
                i++;
            }
        }

        Object readResolve() {
            Builder<Object, Object> builder = new Builder<>();
            return createMap(builder);
        }

        Object createMap(Builder<Object, Object> builder) {
            for (int i = 0; i < this.keys.length; i++) {
                builder.put(this.keys[i], this.values[i]);
            }
            return builder.build();
        }
    }

    Object writeReplace() {
        return new SerializedForm(this);
    }
}
