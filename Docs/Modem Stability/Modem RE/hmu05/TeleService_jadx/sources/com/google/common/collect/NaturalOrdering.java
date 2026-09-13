package com.google.common.collect;

import com.google.common.base.Preconditions;
import java.io.Serializable;

/* JADX INFO: loaded from: classes.dex */
final class NaturalOrdering extends Ordering<Comparable> implements Serializable {
    static final NaturalOrdering INSTANCE = new NaturalOrdering();
    private static final long serialVersionUID = 0;

    @Override // java.util.Comparator
    public int compare(Comparable left, Comparable right) {
        Preconditions.checkNotNull(left);
        Preconditions.checkNotNull(right);
        if (left == right) {
            return 0;
        }
        return left.compareTo(right);
    }

    private Object readResolve() {
        return INSTANCE;
    }

    public String toString() {
        return "Ordering.natural()";
    }

    private NaturalOrdering() {
    }
}
