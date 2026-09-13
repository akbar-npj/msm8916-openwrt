package com.google.common.collect;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/* JADX INFO: loaded from: classes.dex */
public abstract class ImmutableBiMap<K, V> extends ImmutableMap<K, V> implements BiMap<K, V> {
    private static final ImmutableBiMap<Object, Object> EMPTY_IMMUTABLE_BIMAP = new EmptyBiMap();

    abstract ImmutableMap<K, V> delegate();

    public abstract ImmutableBiMap<V, K> inverse();

    public static <K, V> ImmutableBiMap<K, V> of() {
        return (ImmutableBiMap<K, V>) EMPTY_IMMUTABLE_BIMAP;
    }

    public static final class Builder<K, V> extends ImmutableMap.Builder<K, V> {
        @Override // com.google.common.collect.ImmutableMap.Builder
        public Builder<K, V> put(K key, V value) {
            super.put((Object) key, (Object) value);
            return this;
        }

        @Override // com.google.common.collect.ImmutableMap.Builder
        public ImmutableBiMap<K, V> build() {
            ImmutableMap<K, V> map = super.build();
            return map.isEmpty() ? ImmutableBiMap.of() : new RegularImmutableBiMap(map);
        }
    }

    ImmutableBiMap() {
    }

    @Override // com.google.common.collect.ImmutableMap, java.util.Map
    public boolean containsKey(Object key) {
        return delegate().containsKey(key);
    }

    @Override // java.util.Map
    public boolean containsValue(Object value) {
        return inverse().containsKey(value);
    }

    @Override // com.google.common.collect.ImmutableMap, java.util.Map
    public ImmutableSet<Map.Entry<K, V>> entrySet() {
        return delegate().entrySet();
    }

    @Override // com.google.common.collect.ImmutableMap, java.util.Map
    public V get(Object key) {
        return delegate().get(key);
    }

    @Override // com.google.common.collect.ImmutableMap, java.util.Map
    public ImmutableSet<K> keySet() {
        return delegate().keySet();
    }

    @Override // com.google.common.collect.ImmutableMap, java.util.Map
    public ImmutableSet<V> values() {
        return inverse().keySet();
    }

    @Override // com.google.common.collect.ImmutableMap, java.util.Map
    public boolean isEmpty() {
        return delegate().isEmpty();
    }

    @Override // java.util.Map
    public int size() {
        return delegate().size();
    }

    @Override // com.google.common.collect.ImmutableMap, java.util.Map
    public boolean equals(Object object) {
        return object == this || delegate().equals(object);
    }

    @Override // com.google.common.collect.ImmutableMap, java.util.Map
    public int hashCode() {
        return delegate().hashCode();
    }

    @Override // com.google.common.collect.ImmutableMap
    public String toString() {
        return delegate().toString();
    }

    static class EmptyBiMap extends ImmutableBiMap<Object, Object> {
        @Override // com.google.common.collect.ImmutableBiMap, com.google.common.collect.ImmutableMap, java.util.Map
        public /* bridge */ /* synthetic */ Set entrySet() {
            return super.entrySet();
        }

        @Override // com.google.common.collect.ImmutableBiMap, com.google.common.collect.ImmutableMap, java.util.Map
        public /* bridge */ /* synthetic */ Set keySet() {
            return super.keySet();
        }

        @Override // com.google.common.collect.ImmutableBiMap, com.google.common.collect.ImmutableMap, java.util.Map
        public /* bridge */ /* synthetic */ ImmutableCollection values() {
            return super.values();
        }

        @Override // com.google.common.collect.ImmutableBiMap, com.google.common.collect.ImmutableMap, java.util.Map
        public /* bridge */ /* synthetic */ Collection values() {
            return super.values();
        }

        @Override // com.google.common.collect.ImmutableBiMap, com.google.common.collect.ImmutableMap, java.util.Map
        public /* bridge */ /* synthetic */ Set values() {
            return super.values();
        }

        EmptyBiMap() {
        }

        @Override // com.google.common.collect.ImmutableBiMap
        ImmutableMap<Object, Object> delegate() {
            return ImmutableMap.of();
        }

        @Override // com.google.common.collect.ImmutableBiMap
        public ImmutableBiMap<Object, Object> inverse() {
            return this;
        }

        Object readResolve() {
            return ImmutableBiMap.EMPTY_IMMUTABLE_BIMAP;
        }
    }

    private static class SerializedForm extends ImmutableMap.SerializedForm {
        private static final long serialVersionUID = 0;

        SerializedForm(ImmutableBiMap<?, ?> bimap) {
            super(bimap);
        }

        @Override // com.google.common.collect.ImmutableMap.SerializedForm
        Object readResolve() {
            Builder<Object, Object> builder = new Builder<>();
            return createMap(builder);
        }
    }

    @Override // com.google.common.collect.ImmutableMap
    Object writeReplace() {
        return new SerializedForm(this);
    }
}
