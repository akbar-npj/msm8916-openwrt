package com.google.common.collect;

import com.google.common.base.Function;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/* JADX INFO: loaded from: classes.dex */
public abstract class Ordering<T> implements Comparator<T> {

    static class IncomparableValueException extends ClassCastException {
        private static final long serialVersionUID = 0;
        final Object value;
    }

    public static <C extends Comparable> Ordering<C> natural() {
        return NaturalOrdering.INSTANCE;
    }

    static class ArbitraryOrdering extends Ordering<Object> {
        private Map<Object, Integer> uids = Platform.tryWeakKeys(new MapMaker()).makeComputingMap(new Function<Object, Integer>() { // from class: com.google.common.collect.Ordering.ArbitraryOrdering.1
            final AtomicInteger counter = new AtomicInteger(0);

            /* JADX WARN: Can't rename method to resolve collision */
            @Override // com.google.common.base.Function
            public Integer apply(Object from) {
                return Integer.valueOf(this.counter.getAndIncrement());
            }
        });

        ArbitraryOrdering() {
        }

        @Override // java.util.Comparator
        public int compare(Object left, Object right) {
            if (left == right) {
                return 0;
            }
            int leftCode = identityHashCode(left);
            int rightCode = identityHashCode(right);
            if (leftCode != rightCode) {
                return leftCode < rightCode ? -1 : 1;
            }
            int result = this.uids.get(left).compareTo(this.uids.get(right));
            if (result == 0) {
                throw new AssertionError();
            }
            return result;
        }

        public String toString() {
            return "Ordering.arbitrary()";
        }

        int identityHashCode(Object object) {
            return System.identityHashCode(object);
        }
    }

    protected Ordering() {
    }
}
