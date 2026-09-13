package com.google.common.collect;

/* JADX INFO: loaded from: classes.dex */
final class RegularImmutableSet<E> extends ImmutableSet.ArrayImmutableSet<E> {
    private final transient int hashCode;
    private final transient int mask;
    final transient Object[] table;

    RegularImmutableSet(Object[] elements, int hashCode, Object[] table, int mask) {
        super(elements);
        this.table = table;
        this.mask = mask;
        this.hashCode = hashCode;
    }

    @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
    public boolean contains(Object target) {
        if (target == null) {
            return false;
        }
        int i = Hashing.smear(target.hashCode());
        while (true) {
            Object candidate = this.table[this.mask & i];
            if (candidate == null) {
                return false;
            }
            if (!candidate.equals(target)) {
                i++;
            } else {
                return true;
            }
        }
    }

    @Override // com.google.common.collect.ImmutableSet, java.util.Collection, java.util.Set
    public int hashCode() {
        return this.hashCode;
    }

    @Override // com.google.common.collect.ImmutableSet
    boolean isHashCodeFast() {
        return true;
    }
}
