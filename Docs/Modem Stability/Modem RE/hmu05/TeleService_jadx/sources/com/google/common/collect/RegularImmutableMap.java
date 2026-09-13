package com.google.common.collect;

import com.google.common.base.Preconditions;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
final class RegularImmutableMap<K, V> extends ImmutableMap<K, V> {
    private static final long serialVersionUID = 0;
    private final transient LinkedEntry<K, V>[] entries;
    private transient ImmutableSet<Map.Entry<K, V>> entrySet;
    private transient ImmutableSet<K> keySet;
    private final transient int keySetHashCode;
    private final transient int mask;
    private final transient LinkedEntry<K, V>[] table;
    private transient ImmutableCollection<V> values;

    private interface LinkedEntry<K, V> extends Map.Entry<K, V> {
        LinkedEntry<K, V> next();
    }

    RegularImmutableMap(Map.Entry<?, ?>... immutableEntries) {
        int size = immutableEntries.length;
        this.entries = createEntryArray(size);
        int tableSize = chooseTableSize(size);
        this.table = createEntryArray(tableSize);
        this.mask = tableSize - 1;
        int keySetHashCodeMutable = 0;
        for (int entryIndex = 0; entryIndex < size; entryIndex++) {
            Map.Entry<?, ?> entry = immutableEntries[entryIndex];
            Object key = entry.getKey();
            int keyHashCode = key.hashCode();
            keySetHashCodeMutable += keyHashCode;
            int tableIndex = Hashing.smear(keyHashCode) & this.mask;
            LinkedEntry<K, V> existing = this.table[tableIndex];
            LinkedEntry<K, V> linkedEntry = newLinkedEntry(key, entry.getValue(), existing);
            this.table[tableIndex] = linkedEntry;
            this.entries[entryIndex] = linkedEntry;
            while (existing != null) {
                Preconditions.checkArgument(!key.equals(existing.getKey()), "duplicate key: %s", key);
                existing = existing.next();
            }
        }
        this.keySetHashCode = keySetHashCodeMutable;
    }

    private static int chooseTableSize(int size) {
        int tableSize = Integer.highestOneBit(size) << 1;
        Preconditions.checkArgument(tableSize > 0, "table too large: %s", Integer.valueOf(size));
        return tableSize;
    }

    private LinkedEntry<K, V>[] createEntryArray(int size) {
        return new LinkedEntry[size];
    }

    private static <K, V> LinkedEntry<K, V> newLinkedEntry(K key, V value, LinkedEntry<K, V> next) {
        return next == null ? new TerminalEntry(key, value) : new NonTerminalEntry(key, value, next);
    }

    private static final class NonTerminalEntry<K, V> extends ImmutableEntry<K, V> implements LinkedEntry<K, V> {
        final LinkedEntry<K, V> next;

        NonTerminalEntry(K key, V value, LinkedEntry<K, V> next) {
            super(key, value);
            this.next = next;
        }

        @Override // com.google.common.collect.RegularImmutableMap.LinkedEntry
        public LinkedEntry<K, V> next() {
            return this.next;
        }
    }

    private static final class TerminalEntry<K, V> extends ImmutableEntry<K, V> implements LinkedEntry<K, V> {
        TerminalEntry(K key, V value) {
            super(key, value);
        }

        @Override // com.google.common.collect.RegularImmutableMap.LinkedEntry
        public LinkedEntry<K, V> next() {
            return null;
        }
    }

    @Override // com.google.common.collect.ImmutableMap, java.util.Map
    public V get(Object key) {
        if (key == null) {
            return null;
        }
        int index = Hashing.smear(key.hashCode()) & this.mask;
        for (LinkedEntry<K, V> entry = this.table[index]; entry != null; entry = entry.next()) {
            K candidateKey = entry.getKey();
            if (key.equals(candidateKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Override // java.util.Map
    public int size() {
        return this.entries.length;
    }

    @Override // com.google.common.collect.ImmutableMap, java.util.Map
    public boolean isEmpty() {
        return false;
    }

    @Override // java.util.Map
    public boolean containsValue(Object value) {
        if (value == null) {
            return false;
        }
        for (Map.Entry<K, V> entry : this.entries) {
            if (entry.getValue().equals(value)) {
                return true;
            }
        }
        return false;
    }

    @Override // com.google.common.collect.ImmutableMap, java.util.Map
    public ImmutableSet<Map.Entry<K, V>> entrySet() {
        ImmutableSet<Map.Entry<K, V>> es = this.entrySet;
        if (es != null) {
            return es;
        }
        ImmutableSet<Map.Entry<K, V>> es2 = new EntrySet<>(this);
        this.entrySet = es2;
        return es2;
    }

    private static class EntrySet<K, V> extends ImmutableSet.ArrayImmutableSet<Map.Entry<K, V>> {
        final transient RegularImmutableMap<K, V> map;

        EntrySet(RegularImmutableMap<K, V> map) {
            super(((RegularImmutableMap) map).entries);
            this.map = map;
        }

        @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
        public boolean contains(Object target) {
            if (!(target instanceof Map.Entry)) {
                return false;
            }
            Map.Entry<?, ?> entry = (Map.Entry) target;
            V mappedValue = this.map.get(entry.getKey());
            return mappedValue != null && mappedValue.equals(entry.getValue());
        }
    }

    @Override // com.google.common.collect.ImmutableMap, java.util.Map
    public ImmutableSet<K> keySet() {
        ImmutableSet<K> ks = this.keySet;
        if (ks != null) {
            return ks;
        }
        ImmutableSet<K> ks2 = new KeySet<>(this);
        this.keySet = ks2;
        return ks2;
    }

    private static class KeySet<K, V> extends ImmutableSet.TransformedImmutableSet<Map.Entry<K, V>, K> {
        final RegularImmutableMap<K, V> map;

        KeySet(RegularImmutableMap<K, V> map) {
            super(((RegularImmutableMap) map).entries, ((RegularImmutableMap) map).keySetHashCode);
            this.map = map;
        }

        /* JADX INFO: Access modifiers changed from: package-private */
        @Override // com.google.common.collect.ImmutableSet.TransformedImmutableSet
        public K transform(Map.Entry<K, V> element) {
            return element.getKey();
        }

        @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
        public boolean contains(Object target) {
            return this.map.containsKey(target);
        }

        @Override // com.google.common.collect.ImmutableCollection
        boolean isPartialView() {
            return true;
        }
    }

    @Override // com.google.common.collect.ImmutableMap, java.util.Map
    public ImmutableCollection<V> values() {
        ImmutableCollection<V> v = this.values;
        if (v != null) {
            return v;
        }
        ImmutableCollection<V> v2 = new Values<>(this);
        this.values = v2;
        return v2;
    }

    private static class Values<V> extends ImmutableCollection<V> {
        final RegularImmutableMap<?, V> map;

        Values(RegularImmutableMap<?, V> map) {
            this.map = map;
        }

        @Override // java.util.Collection
        public int size() {
            return ((RegularImmutableMap) this.map).entries.length;
        }

        @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.lang.Iterable, java.util.List
        public UnmodifiableIterator<V> iterator() {
            return new AbstractIndexedListIterator<V>(((RegularImmutableMap) this.map).entries.length) { // from class: com.google.common.collect.RegularImmutableMap.Values.1
                @Override // com.google.common.collect.AbstractIndexedListIterator
                protected V get(int index) {
                    return ((RegularImmutableMap) Values.this.map).entries[index].getValue();
                }
            };
        }

        @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
        public boolean contains(Object target) {
            return this.map.containsValue(target);
        }

        @Override // com.google.common.collect.ImmutableCollection
        boolean isPartialView() {
            return true;
        }
    }

    @Override // com.google.common.collect.ImmutableMap
    public String toString() {
        StringBuilder result = Collections2.newStringBuilderForCollection(size()).append('{');
        Collections2.STANDARD_JOINER.appendTo(result, this.entries);
        return result.append('}').toString();
    }
}
