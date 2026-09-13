package com.google.common.base;

import java.io.Serializable;

/* JADX INFO: loaded from: classes.dex */
public final class Equivalences {
    private Equivalences() {
    }

    public static Equivalence<Object> equals() {
        return Equals.INSTANCE;
    }

    public static Equivalence<Object> identity() {
        return Identity.INSTANCE;
    }

    private static final class Equals extends Equivalence<Object> implements Serializable {
        static final Equals INSTANCE = new Equals();
        private static final long serialVersionUID = 1;

        private Equals() {
        }

        @Override // com.google.common.base.Equivalence
        protected boolean doEquivalent(Object a, Object b) {
            return a.equals(b);
        }

        @Override // com.google.common.base.Equivalence
        public int doHash(Object o) {
            return o.hashCode();
        }

        private Object readResolve() {
            return INSTANCE;
        }
    }

    private static final class Identity extends Equivalence<Object> implements Serializable {
        static final Identity INSTANCE = new Identity();
        private static final long serialVersionUID = 1;

        private Identity() {
        }

        @Override // com.google.common.base.Equivalence
        protected boolean doEquivalent(Object a, Object b) {
            return false;
        }

        @Override // com.google.common.base.Equivalence
        protected int doHash(Object o) {
            return System.identityHashCode(o);
        }

        private Object readResolve() {
            return INSTANCE;
        }
    }
}
