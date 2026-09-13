package com.google.common.collect;

import com.google.common.base.Ascii;
import com.google.common.base.Equivalence;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Ticker;
import java.io.Serializable;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/* JADX INFO: loaded from: classes.dex */
public final class MapMaker extends GenericMapMaker<Object, Object> {
    Equivalence<Object> keyEquivalence;
    MapMakerInternalMap.Strength keyStrength;
    RemovalCause nullRemovalCause;
    Ticker ticker;
    boolean useCustomMap;
    Equivalence<Object> valueEquivalence;
    MapMakerInternalMap.Strength valueStrength;
    int initialCapacity = -1;
    int concurrencyLevel = -1;
    int maximumSize = -1;
    long expireAfterWriteNanos = -1;
    long expireAfterAccessNanos = -1;

    enum RemovalCause {
        EXPLICIT { // from class: com.google.common.collect.MapMaker.RemovalCause.1
        },
        REPLACED { // from class: com.google.common.collect.MapMaker.RemovalCause.2
        },
        COLLECTED { // from class: com.google.common.collect.MapMaker.RemovalCause.3
        },
        EXPIRED { // from class: com.google.common.collect.MapMaker.RemovalCause.4
        },
        SIZE { // from class: com.google.common.collect.MapMaker.RemovalCause.5
        }
    }

    interface RemovalListener<K, V> {
        void onRemoval(RemovalNotification<K, V> removalNotification);
    }

    private boolean useNullMap() {
        return this.nullRemovalCause == null;
    }

    MapMaker keyEquivalence(Equivalence<Object> equivalence) {
        Preconditions.checkState(this.keyEquivalence == null, "key equivalence was already set to %s", this.keyEquivalence);
        this.keyEquivalence = (Equivalence) Preconditions.checkNotNull(equivalence);
        this.useCustomMap = true;
        return this;
    }

    Equivalence<Object> getKeyEquivalence() {
        return (Equivalence) Objects.firstNonNull(this.keyEquivalence, getKeyStrength().defaultEquivalence());
    }

    MapMaker valueEquivalence(Equivalence<Object> equivalence) {
        Preconditions.checkState(this.valueEquivalence == null, "value equivalence was already set to %s", this.valueEquivalence);
        this.valueEquivalence = (Equivalence) Preconditions.checkNotNull(equivalence);
        this.useCustomMap = true;
        return this;
    }

    Equivalence<Object> getValueEquivalence() {
        return (Equivalence) Objects.firstNonNull(this.valueEquivalence, getValueStrength().defaultEquivalence());
    }

    public MapMaker initialCapacity(int initialCapacity) {
        Preconditions.checkState(this.initialCapacity == -1, "initial capacity was already set to %s", Integer.valueOf(this.initialCapacity));
        Preconditions.checkArgument(initialCapacity >= 0);
        this.initialCapacity = initialCapacity;
        return this;
    }

    int getInitialCapacity() {
        if (this.initialCapacity == -1) {
            return 16;
        }
        return this.initialCapacity;
    }

    @Deprecated
    MapMaker maximumSize(int size) {
        Preconditions.checkState(this.maximumSize == -1, "maximum size was already set to %s", Integer.valueOf(this.maximumSize));
        Preconditions.checkArgument(size >= 0, "maximum size must not be negative");
        this.maximumSize = size;
        this.useCustomMap = true;
        if (this.maximumSize == 0) {
            this.nullRemovalCause = RemovalCause.SIZE;
        }
        return this;
    }

    public MapMaker concurrencyLevel(int concurrencyLevel) {
        Preconditions.checkState(this.concurrencyLevel == -1, "concurrency level was already set to %s", Integer.valueOf(this.concurrencyLevel));
        Preconditions.checkArgument(concurrencyLevel > 0);
        this.concurrencyLevel = concurrencyLevel;
        return this;
    }

    int getConcurrencyLevel() {
        if (this.concurrencyLevel == -1) {
            return 4;
        }
        return this.concurrencyLevel;
    }

    public MapMaker weakKeys() {
        return setKeyStrength(MapMakerInternalMap.Strength.WEAK);
    }

    MapMaker setKeyStrength(MapMakerInternalMap.Strength strength) {
        Preconditions.checkState(this.keyStrength == null, "Key strength was already set to %s", this.keyStrength);
        this.keyStrength = (MapMakerInternalMap.Strength) Preconditions.checkNotNull(strength);
        if (strength != MapMakerInternalMap.Strength.STRONG) {
            this.useCustomMap = true;
        }
        return this;
    }

    MapMakerInternalMap.Strength getKeyStrength() {
        return (MapMakerInternalMap.Strength) Objects.firstNonNull(this.keyStrength, MapMakerInternalMap.Strength.STRONG);
    }

    MapMaker setValueStrength(MapMakerInternalMap.Strength strength) {
        Preconditions.checkState(this.valueStrength == null, "Value strength was already set to %s", this.valueStrength);
        this.valueStrength = (MapMakerInternalMap.Strength) Preconditions.checkNotNull(strength);
        if (strength != MapMakerInternalMap.Strength.STRONG) {
            this.useCustomMap = true;
        }
        return this;
    }

    MapMakerInternalMap.Strength getValueStrength() {
        return (MapMakerInternalMap.Strength) Objects.firstNonNull(this.valueStrength, MapMakerInternalMap.Strength.STRONG);
    }

    @Deprecated
    MapMaker expireAfterWrite(long duration, TimeUnit unit) {
        checkExpiration(duration, unit);
        this.expireAfterWriteNanos = unit.toNanos(duration);
        if (duration == 0 && this.nullRemovalCause == null) {
            this.nullRemovalCause = RemovalCause.EXPIRED;
        }
        this.useCustomMap = true;
        return this;
    }

    private void checkExpiration(long duration, TimeUnit unit) {
        Preconditions.checkState(this.expireAfterWriteNanos == -1, "expireAfterWrite was already set to %s ns", Long.valueOf(this.expireAfterWriteNanos));
        Preconditions.checkState(this.expireAfterAccessNanos == -1, "expireAfterAccess was already set to %s ns", Long.valueOf(this.expireAfterAccessNanos));
        Preconditions.checkArgument(duration >= 0, "duration cannot be negative: %s %s", Long.valueOf(duration), unit);
    }

    long getExpireAfterWriteNanos() {
        if (this.expireAfterWriteNanos == -1) {
            return 0L;
        }
        return this.expireAfterWriteNanos;
    }

    @Deprecated
    MapMaker expireAfterAccess(long duration, TimeUnit unit) {
        checkExpiration(duration, unit);
        this.expireAfterAccessNanos = unit.toNanos(duration);
        if (duration == 0 && this.nullRemovalCause == null) {
            this.nullRemovalCause = RemovalCause.EXPIRED;
        }
        this.useCustomMap = true;
        return this;
    }

    long getExpireAfterAccessNanos() {
        if (this.expireAfterAccessNanos == -1) {
            return 0L;
        }
        return this.expireAfterAccessNanos;
    }

    Ticker getTicker() {
        return (Ticker) Objects.firstNonNull(this.ticker, Ticker.systemTicker());
    }

    @Deprecated
    <K, V> GenericMapMaker<K, V> removalListener(RemovalListener<K, V> listener) {
        Preconditions.checkState(this.removalListener == null);
        this.removalListener = (RemovalListener) Preconditions.checkNotNull(listener);
        this.useCustomMap = true;
        return this;
    }

    public <K, V> ConcurrentMap<K, V> makeMap() {
        if (this.useCustomMap) {
            return this.nullRemovalCause == null ? new MapMakerInternalMap(this) : new NullConcurrentMap(this);
        }
        return new ConcurrentHashMap(getInitialCapacity(), 0.75f, getConcurrencyLevel());
    }

    @Deprecated
    public <K, V> ConcurrentMap<K, V> makeComputingMap(Function<? super K, ? extends V> computingFunction) {
        return useNullMap() ? new ComputingConcurrentHashMap.ComputingMapAdapter(this, computingFunction) : new NullComputingConcurrentMap(this, computingFunction);
    }

    public String toString() {
        Objects.ToStringHelper s = Objects.toStringHelper(this);
        if (this.initialCapacity != -1) {
            s.add("initialCapacity", this.initialCapacity);
        }
        if (this.concurrencyLevel != -1) {
            s.add("concurrencyLevel", this.concurrencyLevel);
        }
        if (this.maximumSize != -1) {
            s.add("maximumSize", this.maximumSize);
        }
        if (this.expireAfterWriteNanos != -1) {
            s.add("expireAfterWrite", this.expireAfterWriteNanos + "ns");
        }
        if (this.expireAfterAccessNanos != -1) {
            s.add("expireAfterAccess", this.expireAfterAccessNanos + "ns");
        }
        if (this.keyStrength != null) {
            s.add("keyStrength", Ascii.toLowerCase(this.keyStrength.toString()));
        }
        if (this.valueStrength != null) {
            s.add("valueStrength", Ascii.toLowerCase(this.valueStrength.toString()));
        }
        if (this.keyEquivalence != null) {
            s.addValue("keyEquivalence");
        }
        if (this.valueEquivalence != null) {
            s.addValue("valueEquivalence");
        }
        if (this.removalListener != null) {
            s.addValue("removalListener");
        }
        return s.toString();
    }

    static final class RemovalNotification<K, V> extends ImmutableEntry<K, V> {
        private static final long serialVersionUID = 0;
        private final RemovalCause cause;

        RemovalNotification(K key, V value, RemovalCause cause) {
            super(key, value);
            this.cause = cause;
        }
    }

    static class NullConcurrentMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V>, Serializable {
        private static final long serialVersionUID = 0;
        private final RemovalCause removalCause;
        private final RemovalListener<K, V> removalListener;

        NullConcurrentMap(MapMaker mapMaker) {
            this.removalListener = mapMaker.getRemovalListener();
            this.removalCause = mapMaker.nullRemovalCause;
        }

        @Override // java.util.AbstractMap, java.util.Map
        public boolean containsKey(Object key) {
            return false;
        }

        @Override // java.util.AbstractMap, java.util.Map
        public boolean containsValue(Object value) {
            return false;
        }

        @Override // java.util.AbstractMap, java.util.Map
        public V get(Object key) {
            return null;
        }

        void notifyRemoval(K key, V value) {
            RemovalNotification<K, V> notification = new RemovalNotification<>(key, value, this.removalCause);
            this.removalListener.onRemoval(notification);
        }

        @Override // java.util.AbstractMap, java.util.Map
        public V put(K key, V value) {
            Preconditions.checkNotNull(key);
            Preconditions.checkNotNull(value);
            notifyRemoval(key, value);
            return null;
        }

        @Override // java.util.Map, java.util.concurrent.ConcurrentMap
        public V putIfAbsent(K key, V value) {
            return put(key, value);
        }

        @Override // java.util.AbstractMap, java.util.Map
        public V remove(Object key) {
            return null;
        }

        @Override // java.util.Map, java.util.concurrent.ConcurrentMap
        public boolean remove(Object key, Object value) {
            return false;
        }

        @Override // java.util.Map, java.util.concurrent.ConcurrentMap
        public V replace(K key, V value) {
            Preconditions.checkNotNull(key);
            Preconditions.checkNotNull(value);
            return null;
        }

        @Override // java.util.Map, java.util.concurrent.ConcurrentMap
        public boolean replace(K key, V oldValue, V newValue) {
            Preconditions.checkNotNull(key);
            Preconditions.checkNotNull(newValue);
            return false;
        }

        @Override // java.util.AbstractMap, java.util.Map
        public Set<Map.Entry<K, V>> entrySet() {
            return Collections.emptySet();
        }
    }

    static final class NullComputingConcurrentMap<K, V> extends NullConcurrentMap<K, V> {
        private static final long serialVersionUID = 0;
        final Function<? super K, ? extends V> computingFunction;

        NullComputingConcurrentMap(MapMaker mapMaker, Function<? super K, ? extends V> computingFunction) {
            super(mapMaker);
            this.computingFunction = (Function) Preconditions.checkNotNull(computingFunction);
        }

        /* JADX WARN: Multi-variable type inference failed */
        @Override // com.google.common.collect.MapMaker.NullConcurrentMap, java.util.AbstractMap, java.util.Map
        public V get(Object obj) {
            V value = compute(obj);
            Preconditions.checkNotNull(value, this.computingFunction + " returned null for key " + obj + ".");
            notifyRemoval(obj, value);
            return value;
        }

        private V compute(K key) {
            Preconditions.checkNotNull(key);
            try {
                return this.computingFunction.apply(key);
            } catch (ComputationException e) {
                throw e;
            } catch (Throwable t) {
                throw new ComputationException(t);
            }
        }
    }
}
