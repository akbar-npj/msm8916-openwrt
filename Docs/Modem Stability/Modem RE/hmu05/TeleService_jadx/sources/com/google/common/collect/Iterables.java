package com.google.common.collect;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import java.util.Collection;
import java.util.Iterator;

/* JADX INFO: loaded from: classes.dex */
public final class Iterables {
    private Iterables() {
    }

    public static String toString(Iterable<?> iterable) {
        return Iterators.toString(iterable.iterator());
    }

    public static <T> T getOnlyElement(Iterable<T> iterable) {
        return (T) Iterators.getOnlyElement(iterable.iterator());
    }

    static Object[] toArray(Iterable<?> iterable) {
        return toCollection(iterable).toArray();
    }

    private static <E> Collection<E> toCollection(Iterable<E> iterable) {
        return iterable instanceof Collection ? (Collection) iterable : Lists.newArrayList(iterable.iterator());
    }

    public static <F, T> Iterable<T> transform(final Iterable<F> fromIterable, final Function<? super F, ? extends T> function) {
        Preconditions.checkNotNull(fromIterable);
        Preconditions.checkNotNull(function);
        return new IterableWithToString<T>() { // from class: com.google.common.collect.Iterables.8
            @Override // java.lang.Iterable
            public Iterator<T> iterator() {
                return Iterators.transform(fromIterable.iterator(), function);
            }
        };
    }

    static abstract class IterableWithToString<E> implements Iterable<E> {
        IterableWithToString() {
        }

        public String toString() {
            return Iterables.toString(this);
        }
    }
}
