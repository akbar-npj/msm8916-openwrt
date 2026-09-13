package com.google.common.collect;

import com.google.common.base.Preconditions;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

/* JADX INFO: loaded from: classes.dex */
final class SortedIterables {
    private SortedIterables() {
    }

    public static boolean hasSameComparator(Comparator<?> comparator, Iterable<?> elements) {
        Comparator<?> comparator2;
        Preconditions.checkNotNull(comparator);
        Preconditions.checkNotNull(elements);
        if (elements instanceof SortedSet) {
            SortedSet<?> sortedSet = (SortedSet) elements;
            comparator2 = sortedSet.comparator();
            if (comparator2 == null) {
                comparator2 = Ordering.natural();
            }
        } else if (elements instanceof SortedIterable) {
            comparator2 = ((SortedIterable) elements).comparator();
        } else {
            comparator2 = null;
        }
        return comparator.equals(comparator2);
    }

    public static <E> Collection<E> sortedUnique(Comparator<? super E> comparator, Iterator<E> elements) {
        SortedSet<E> sortedSet = Sets.newTreeSet(comparator);
        Iterators.addAll(sortedSet, elements);
        return sortedSet;
    }

    public static <E> Collection<E> sortedUnique(Comparator<? super E> comparator, Iterable<E> elements) {
        if (elements instanceof Multiset) {
            elements = ((Multiset) elements).elementSet();
        }
        if (elements instanceof Set) {
            if (hasSameComparator(comparator, elements)) {
                return (Set) elements;
            }
            List<E> list = Lists.newArrayList(elements);
            Collections.sort(list, comparator);
            return list;
        }
        Object[] array = Iterables.toArray(elements);
        if (!hasSameComparator(comparator, elements)) {
            Arrays.sort(array, comparator);
        }
        return uniquifySortedArray(comparator, array);
    }

    private static <E> Collection<E> uniquifySortedArray(Comparator<? super E> comparator, E[] eArr) {
        if (eArr.length == 0) {
            return Collections.emptySet();
        }
        int i = 1;
        for (int i2 = 1; i2 < eArr.length; i2++) {
            if (comparator.compare((Object) eArr[i2], (Object) eArr[i - 1]) != 0) {
                eArr[i] = eArr[i2];
                i++;
            }
        }
        if (i < eArr.length) {
            eArr = (E[]) ObjectArrays.arraysCopyOf(eArr, i);
        }
        return Arrays.asList(eArr);
    }
}
