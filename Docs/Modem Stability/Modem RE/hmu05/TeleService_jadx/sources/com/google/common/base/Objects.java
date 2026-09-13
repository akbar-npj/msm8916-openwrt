package com.google.common.base;

/* JADX INFO: loaded from: classes.dex */
public final class Objects {
    private Objects() {
    }

    public static boolean equal(Object a, Object b) {
        return a == b || (a != null && a.equals(b));
    }

    public static ToStringHelper toStringHelper(Object self) {
        return new ToStringHelper(simpleName(self.getClass()));
    }

    private static String simpleName(Class<?> clazz) {
        String name = clazz.getName().replaceAll("\\$[0-9]+", "\\$");
        int start = name.lastIndexOf(36);
        if (start == -1) {
            start = name.lastIndexOf(46);
        }
        return name.substring(start + 1);
    }

    public static <T> T firstNonNull(T t, T t2) {
        return t != null ? t : (T) Preconditions.checkNotNull(t2);
    }

    public static final class ToStringHelper {
        private final StringBuilder builder;
        private boolean needsSeparator;

        private ToStringHelper(String className) {
            this.needsSeparator = false;
            Preconditions.checkNotNull(className);
            this.builder = new StringBuilder(32).append(className).append('{');
        }

        public ToStringHelper add(String name, Object value) {
            checkNameAndAppend(name).append(value);
            return this;
        }

        public ToStringHelper add(String name, int value) {
            checkNameAndAppend(name).append(value);
            return this;
        }

        public ToStringHelper add(String name, long value) {
            checkNameAndAppend(name).append(value);
            return this;
        }

        private StringBuilder checkNameAndAppend(String name) {
            Preconditions.checkNotNull(name);
            return maybeAppendSeparator().append(name).append('=');
        }

        public ToStringHelper addValue(Object value) {
            maybeAppendSeparator().append(value);
            return this;
        }

        public String toString() {
            try {
                return this.builder.append('}').toString();
            } finally {
                this.builder.setLength(this.builder.length() - 1);
            }
        }

        private StringBuilder maybeAppendSeparator() {
            if (this.needsSeparator) {
                return this.builder.append(", ");
            }
            this.needsSeparator = true;
            return this.builder;
        }
    }
}
