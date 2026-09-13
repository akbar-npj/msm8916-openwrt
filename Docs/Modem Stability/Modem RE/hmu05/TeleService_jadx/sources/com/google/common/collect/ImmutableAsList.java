package com.google.common.collect;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;

/* JADX INFO: loaded from: classes.dex */
final class ImmutableAsList<E> extends RegularImmutableList<E> {
    private final transient ImmutableCollection<E> collection;

    ImmutableAsList(Object[] array, ImmutableCollection<E> collection) {
        super(array, 0, array.length);
        this.collection = collection;
    }

    @Override // com.google.common.collect.RegularImmutableList, com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
    public boolean contains(Object target) {
        return this.collection.contains(target);
    }

    static class SerializedForm implements Serializable {
        private static final long serialVersionUID = 0;
        final ImmutableCollection<?> collection;

        SerializedForm(ImmutableCollection<?> collection) {
            this.collection = collection;
        }

        Object readResolve() {
            return this.collection.asList();
        }
    }

    private void readObject(ObjectInputStream stream) throws InvalidObjectException {
        throw new InvalidObjectException("Use SerializedForm");
    }

    @Override // com.google.common.collect.ImmutableList, com.google.common.collect.ImmutableCollection
    Object writeReplace() {
        return new SerializedForm(this.collection);
    }
}
