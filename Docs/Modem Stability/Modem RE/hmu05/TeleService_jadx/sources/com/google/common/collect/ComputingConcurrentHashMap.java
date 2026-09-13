package com.google.common.collect;

import com.google.common.base.Equivalence;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReferenceArray;

/* JADX INFO: loaded from: classes.dex */
class ComputingConcurrentHashMap<K, V> extends MapMakerInternalMap<K, V> {
    private static final long serialVersionUID = 4;
    final Function<? super K, ? extends V> computingFunction;

    ComputingConcurrentHashMap(MapMaker builder, Function<? super K, ? extends V> computingFunction) {
        super(builder);
        this.computingFunction = (Function) Preconditions.checkNotNull(computingFunction);
    }

    @Override // com.google.common.collect.MapMakerInternalMap
    MapMakerInternalMap.Segment<K, V> createSegment(int initialCapacity, int maxSegmentSize) {
        return new ComputingSegment(this, initialCapacity, maxSegmentSize);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @Override // com.google.common.collect.MapMakerInternalMap
    public ComputingSegment<K, V> segmentFor(int hash) {
        return (ComputingSegment) super.segmentFor(hash);
    }

    V getOrCompute(K key) throws ExecutionException {
        int hash = hash(Preconditions.checkNotNull(key));
        return segmentFor(hash).getOrCompute(key, hash, this.computingFunction);
    }

    static final class ComputingSegment<K, V> extends MapMakerInternalMap.Segment<K, V> {
        ComputingSegment(MapMakerInternalMap<K, V> map, int initialCapacity, int maxSegmentSize) {
            super(map, initialCapacity, maxSegmentSize);
        }

        /* JADX WARN: Code duplicated, block: B:55:0x00e7 A[Catch: all -> 0x00b1, PHI: r4
          0x00e7: PHI (r4v6 com.google.common.collect.MapMakerInternalMap$ReferenceEntry<K, V>) = 
          (r4v3 com.google.common.collect.MapMakerInternalMap$ReferenceEntry<K, V>)
          (r4v0 com.google.common.collect.MapMakerInternalMap$ReferenceEntry<K, V>)
         binds: [B:29:0x007f, B:11:0x001d] A[DONT_GENERATE, DONT_INLINE], TRY_ENTER, TryCatch #0 {all -> 0x00b1, blocks: (B:2:0x0000, B:4:0x0006, B:6:0x000c, B:10:0x0015, B:55:0x00e7, B:58:0x00ee, B:60:0x00fd, B:12:0x001f, B:49:0x00d1, B:38:0x00aa, B:39:0x00b0, B:28:0x0079, B:30:0x0081), top: B:65:0x0000 }] */
        /* JADX WARN: Code duplicated, block: B:57:0x00ed  */
        /* JADX WARN: Code duplicated, block: B:62:0x0105  */
        V getOrCompute(K k, int i, Function<? super K, ? extends V> function) throws ExecutionException {
            MapMakerInternalMap.ReferenceEntry<K, V> entry;
            V vCompute;
            boolean z;
            do {
                try {
                    entry = getEntry(k, i);
                    if (entry != null && (vCompute = getLiveValue(entry)) != null) {
                        recordRead(entry);
                        postReadCleanup();
                    } else if (entry == null || !entry.getValueReference().isComputingReference()) {
                        boolean z2 = true;
                        ComputingValueReference<K, V> computingValueReference = null;
                        lock();
                        try {
                            preWriteCleanup();
                            int i2 = this.count - 1;
                            AtomicReferenceArray<MapMakerInternalMap.ReferenceEntry<K, V>> atomicReferenceArray = this.table;
                            int length = i & (atomicReferenceArray.length() - 1);
                            MapMakerInternalMap.ReferenceEntry<K, V> referenceEntry = atomicReferenceArray.get(length);
                            entry = referenceEntry;
                            while (entry != null) {
                                K key = entry.getKey();
                                if (entry.getHash() == i && key != null && this.map.keyEquivalence.equivalent(k, key)) {
                                    if (!entry.getValueReference().isComputingReference()) {
                                        vCompute = entry.getValueReference().get();
                                        if (vCompute == null) {
                                            enqueueNotification(key, i, vCompute, MapMaker.RemovalCause.COLLECTED);
                                        } else if (this.map.expires() && this.map.isExpired(entry)) {
                                            enqueueNotification(key, i, vCompute, MapMaker.RemovalCause.EXPIRED);
                                        } else {
                                            recordLockedRead(entry);
                                            unlock();
                                            postWriteCleanup();
                                            postReadCleanup();
                                        }
                                        this.evictionQueue.remove(entry);
                                        this.expirationQueue.remove(entry);
                                        this.count = i2;
                                        break;
                                    }
                                    z2 = false;
                                    break;
                                }
                                entry = entry.getNext();
                                unlock();
                                postWriteCleanup();
                                throw th;
                            }
                            if (z2) {
                                ComputingValueReference<K, V> computingValueReference2 = new ComputingValueReference<>(function);
                                if (entry == null) {
                                    try {
                                        entry = newEntry(k, i, referenceEntry);
                                        entry.setValueReference(computingValueReference2);
                                        atomicReferenceArray.set(length, entry);
                                        computingValueReference = computingValueReference2;
                                    } catch (Throwable th) {
                                        th = th;
                                    }
                                } else {
                                    entry.setValueReference(computingValueReference2);
                                    computingValueReference = computingValueReference2;
                                }
                            }
                            unlock();
                            postWriteCleanup();
                            if (z2) {
                                vCompute = compute(k, i, entry, computingValueReference);
                                postReadCleanup();
                            } else {
                                if (Thread.holdsLock(entry)) {
                                    z = false;
                                } else {
                                    z = true;
                                }
                                Preconditions.checkState(z, "Recursive computation");
                                vCompute = entry.getValueReference().waitForValue();
                            }
                        } catch (Throwable th2) {
                            th = th2;
                        }
                    } else {
                        if (Thread.holdsLock(entry)) {
                            z = true;
                        } else {
                            z = false;
                        }
                        Preconditions.checkState(z, "Recursive computation");
                        vCompute = entry.getValueReference().waitForValue();
                    }
                    return vCompute;
                } catch (Throwable th3) {
                    postReadCleanup();
                    throw th3;
                }
            } while (vCompute == null);
            recordRead(entry);
            postReadCleanup();
            return vCompute;
        }

        V compute(K key, int hash, MapMakerInternalMap.ReferenceEntry<K, V> e, ComputingValueReference<K, V> computingValueReference) throws ExecutionException {
            V value = null;
            System.nanoTime();
            long end = 0;
            try {
                synchronized (e) {
                    value = computingValueReference.compute(key, hash);
                    end = System.nanoTime();
                }
                if (value != null) {
                    V oldValue = put(key, hash, value, true);
                    if (oldValue != null) {
                        enqueueNotification(key, hash, value, MapMaker.RemovalCause.REPLACED);
                    }
                }
                if (end == 0) {
                    System.nanoTime();
                }
                if (value == null) {
                    clearValue(key, hash, computingValueReference);
                }
                return value;
            } catch (Throwable th) {
                if (end == 0) {
                    System.nanoTime();
                }
                if (value == null) {
                    clearValue(key, hash, computingValueReference);
                }
                throw th;
            }
        }
    }

    private static final class ComputationExceptionReference<K, V> implements MapMakerInternalMap.ValueReference<K, V> {
        final Throwable t;

        ComputationExceptionReference(Throwable t) {
            this.t = t;
        }

        @Override // com.google.common.collect.MapMakerInternalMap.ValueReference
        public V get() {
            return null;
        }

        @Override // com.google.common.collect.MapMakerInternalMap.ValueReference
        public MapMakerInternalMap.ReferenceEntry<K, V> getEntry() {
            return null;
        }

        @Override // com.google.common.collect.MapMakerInternalMap.ValueReference
        public MapMakerInternalMap.ValueReference<K, V> copyFor(ReferenceQueue<V> queue, MapMakerInternalMap.ReferenceEntry<K, V> entry) {
            return this;
        }

        @Override // com.google.common.collect.MapMakerInternalMap.ValueReference
        public boolean isComputingReference() {
            return false;
        }

        @Override // com.google.common.collect.MapMakerInternalMap.ValueReference
        public V waitForValue() throws ExecutionException {
            throw new ExecutionException(this.t);
        }

        @Override // com.google.common.collect.MapMakerInternalMap.ValueReference
        public void clear(MapMakerInternalMap.ValueReference<K, V> newValue) {
        }
    }

    private static final class ComputedReference<K, V> implements MapMakerInternalMap.ValueReference<K, V> {
        final V value;

        ComputedReference(V value) {
            this.value = value;
        }

        @Override // com.google.common.collect.MapMakerInternalMap.ValueReference
        public V get() {
            return this.value;
        }

        @Override // com.google.common.collect.MapMakerInternalMap.ValueReference
        public MapMakerInternalMap.ReferenceEntry<K, V> getEntry() {
            return null;
        }

        @Override // com.google.common.collect.MapMakerInternalMap.ValueReference
        public MapMakerInternalMap.ValueReference<K, V> copyFor(ReferenceQueue<V> queue, MapMakerInternalMap.ReferenceEntry<K, V> entry) {
            return this;
        }

        @Override // com.google.common.collect.MapMakerInternalMap.ValueReference
        public boolean isComputingReference() {
            return false;
        }

        @Override // com.google.common.collect.MapMakerInternalMap.ValueReference
        public V waitForValue() {
            return get();
        }

        @Override // com.google.common.collect.MapMakerInternalMap.ValueReference
        public void clear(MapMakerInternalMap.ValueReference<K, V> newValue) {
        }
    }

    private static final class ComputingValueReference<K, V> implements MapMakerInternalMap.ValueReference<K, V> {
        volatile MapMakerInternalMap.ValueReference<K, V> computedReference = MapMakerInternalMap.unset();
        final Function<? super K, ? extends V> computingFunction;

        public ComputingValueReference(Function<? super K, ? extends V> computingFunction) {
            this.computingFunction = computingFunction;
        }

        @Override // com.google.common.collect.MapMakerInternalMap.ValueReference
        public V get() {
            return null;
        }

        @Override // com.google.common.collect.MapMakerInternalMap.ValueReference
        public MapMakerInternalMap.ReferenceEntry<K, V> getEntry() {
            return null;
        }

        @Override // com.google.common.collect.MapMakerInternalMap.ValueReference
        public MapMakerInternalMap.ValueReference<K, V> copyFor(ReferenceQueue<V> queue, MapMakerInternalMap.ReferenceEntry<K, V> entry) {
            return this;
        }

        @Override // com.google.common.collect.MapMakerInternalMap.ValueReference
        public boolean isComputingReference() {
            return true;
        }

        @Override // com.google.common.collect.MapMakerInternalMap.ValueReference
        public V waitForValue() throws ExecutionException {
            if (this.computedReference == MapMakerInternalMap.UNSET) {
                boolean interrupted = false;
                try {
                    synchronized (this) {
                        while (this.computedReference == MapMakerInternalMap.UNSET) {
                            try {
                                wait();
                            } catch (InterruptedException e) {
                                interrupted = true;
                            }
                        }
                    }
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                } catch (Throwable th) {
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    throw th;
                }
            }
            return this.computedReference.waitForValue();
        }

        @Override // com.google.common.collect.MapMakerInternalMap.ValueReference
        public void clear(MapMakerInternalMap.ValueReference<K, V> newValue) {
            setValueReference(newValue);
        }

        V compute(K key, int hash) throws ExecutionException {
            try {
                V value = this.computingFunction.apply(key);
                setValueReference(new ComputedReference(value));
                return value;
            } catch (Throwable t) {
                setValueReference(new ComputationExceptionReference(t));
                throw new ExecutionException(t);
            }
        }

        void setValueReference(MapMakerInternalMap.ValueReference<K, V> valueReference) {
            synchronized (this) {
                if (this.computedReference == MapMakerInternalMap.UNSET) {
                    this.computedReference = valueReference;
                    notifyAll();
                }
            }
        }
    }

    static final class ComputingMapAdapter<K, V> extends ComputingConcurrentHashMap<K, V> implements Serializable {
        private static final long serialVersionUID = 0;

        @Override // com.google.common.collect.ComputingConcurrentHashMap, com.google.common.collect.MapMakerInternalMap
        /* bridge */ /* synthetic */ MapMakerInternalMap.Segment segmentFor(int x0) {
            return super.segmentFor(x0);
        }

        ComputingMapAdapter(MapMaker mapMaker, Function<? super K, ? extends V> computingFunction) {
            super(mapMaker, computingFunction);
        }

        /* JADX WARN: Multi-variable type inference failed */
        @Override // com.google.common.collect.MapMakerInternalMap, java.util.AbstractMap, java.util.Map
        public V get(Object obj) throws Throwable {
            try {
                V orCompute = getOrCompute(obj);
                if (orCompute == null) {
                    throw new NullPointerException(this.computingFunction + " returned null for key " + obj + ".");
                }
                return orCompute;
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                Throwables.propagateIfInstanceOf(cause, ComputationException.class);
                throw new ComputationException(cause);
            }
        }
    }

    @Override // com.google.common.collect.MapMakerInternalMap
    Object writeReplace() {
        return new ComputingSerializationProxy(this.keyStrength, this.valueStrength, this.keyEquivalence, this.valueEquivalence, this.expireAfterWriteNanos, this.expireAfterAccessNanos, this.maximumSize, this.concurrencyLevel, this.removalListener, this, this.computingFunction);
    }

    static final class ComputingSerializationProxy<K, V> extends MapMakerInternalMap.AbstractSerializationProxy<K, V> {
        private static final long serialVersionUID = 4;
        final Function<? super K, ? extends V> computingFunction;

        ComputingSerializationProxy(MapMakerInternalMap.Strength keyStrength, MapMakerInternalMap.Strength valueStrength, Equivalence<Object> keyEquivalence, Equivalence<Object> valueEquivalence, long expireAfterWriteNanos, long expireAfterAccessNanos, int maximumSize, int concurrencyLevel, MapMaker.RemovalListener<? super K, ? super V> removalListener, ConcurrentMap<K, V> delegate, Function<? super K, ? extends V> computingFunction) {
            super(keyStrength, valueStrength, keyEquivalence, valueEquivalence, expireAfterWriteNanos, expireAfterAccessNanos, maximumSize, concurrencyLevel, removalListener, delegate);
            this.computingFunction = computingFunction;
        }

        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            writeMapTo(out);
        }

        private void readObject(ObjectInputStream in) throws ClassNotFoundException, IOException {
            in.defaultReadObject();
            MapMaker mapMaker = readMapMaker(in);
            this.delegate = mapMaker.makeComputingMap(this.computingFunction);
            readEntries(in);
        }

        Object readResolve() {
            return this.delegate;
        }
    }
}
