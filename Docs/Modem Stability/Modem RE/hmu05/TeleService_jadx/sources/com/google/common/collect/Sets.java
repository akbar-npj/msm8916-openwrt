package com.google.common.collect;

import com.google.common.base.Preconditions;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

/* JADX INFO: loaded from: classes.dex */
public final class Sets {
    private Sets() {
    }

    public static <E> TreeSet<E> newTreeSet(Comparator<? super E> comparator) {
        return new TreeSet<>((Comparator) Preconditions.checkNotNull(comparator));
    }

    static int hashCodeImpl(Set<?> s) {
        int hashCode = 0;
        Iterator<?> it = s.iterator();
        while (it.hasNext()) {
            Object o = it.next();
            hashCode += o != null ? o.hashCode() : 0;
        }
        return hashCode;
    }

    static boolean equalsImpl(Set<?> s, Object object) {
        if (s == object) {
            return true;
        }
        if (!(object instanceof Set)) {
            return false;
        }
        Set<?> o = (Set) object;
        try {
            return s.size() == o.size() && s.containsAll(o);
        } catch (ClassCastException e) {
            return false;
        } catch (NullPointerException e2) {
            return false;
        }
    }
}
