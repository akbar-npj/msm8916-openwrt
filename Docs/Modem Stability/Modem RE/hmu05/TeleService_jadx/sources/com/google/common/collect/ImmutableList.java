package com.google.common.collect;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.RandomAccess;

/* JADX INFO: loaded from: classes.dex */
public abstract class ImmutableList<E> extends ImmutableCollection<E> implements List<E>, RandomAccess {
    @Override // java.util.List
    public abstract UnmodifiableListIterator<E> listIterator(int i);

    @Override // java.util.List
    public abstract ImmutableList<E> subList(int i, int i2);

    public static <E> ImmutableList<E> of() {
        return EmptyImmutableList.INSTANCE;
    }

    public static <E> ImmutableList<E> of(E element) {
        return new SingletonImmutableList(element);
    }

    public static <E> ImmutableList<E> copyOf(Collection<? extends E> elements) {
        if (!(elements instanceof ImmutableCollection)) {
            return copyFromCollection(elements);
        }
        ImmutableList<E> list = ((ImmutableCollection) elements).asList();
        return list.isPartialView() ? copyFromCollection(list) : list;
    }

    public static <E> ImmutableList<E> copyOf(E[] elements) {
        switch (elements.length) {
            case 0:
                return of();
            case 1:
                return new SingletonImmutableList(elements[0]);
            default:
                return construct((Object[]) elements.clone());
        }
    }

    private static <E> ImmutableList<E> copyFromCollection(Collection<? extends E> collection) {
        Object[] elements = collection.toArray();
        switch (elements.length) {
            case 0:
                return of();
            case 1:
                return new SingletonImmutableList(elements[0]);
            default:
                return construct(elements);
        }
    }

    private static <E> ImmutableList<E> construct(Object... elements) {
        for (int i = 0; i < elements.length; i++) {
            checkElementNotNull(elements[i], i);
        }
        return new RegularImmutableList(elements);
    }

    private static Object checkElementNotNull(Object element, int index) {
        if (element == null) {
            throw new NullPointerException("at index " + index);
        }
        return element;
    }

    ImmutableList() {
    }

    @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.lang.Iterable, java.util.List
    public UnmodifiableIterator<E> iterator() {
        return listIterator();
    }

    @Override // java.util.List
    public UnmodifiableListIterator<E> listIterator() {
        return listIterator(0);
    }

    @Override // java.util.List
    public final boolean addAll(int index, Collection<? extends E> newElements) {
        throw new UnsupportedOperationException();
    }

    @Override // java.util.List
    public final E set(int index, E element) {
        throw new UnsupportedOperationException();
    }

    @Override // java.util.List
    public final void add(int index, E element) {
        throw new UnsupportedOperationException();
    }

    @Override // java.util.List
    public final E remove(int index) {
        throw new UnsupportedOperationException();
    }

    @Override // com.google.common.collect.ImmutableCollection
    public ImmutableList<E> asList() {
        return this;
    }

    @Override // java.util.Collection, java.util.List
    public boolean equals(Object obj) {
        return Lists.equalsImpl(this, obj);
    }

    @Override // java.util.Collection, java.util.List
    public int hashCode() {
        return Lists.hashCodeImpl(this);
    }

    private static class SerializedForm implements Serializable {
        private static final long serialVersionUID = 0;
        final Object[] elements;

        SerializedForm(Object[] elements) {
            this.elements = elements;
        }

        Object readResolve() {
            return ImmutableList.copyOf(this.elements);
        }
    }

    private void readObject(ObjectInputStream stream) throws InvalidObjectException {
        throw new InvalidObjectException("Use SerializedForm");
    }

    @Override // com.google.common.collect.ImmutableCollection
    Object writeReplace() {
        return new SerializedForm(toArray());
    }
}
