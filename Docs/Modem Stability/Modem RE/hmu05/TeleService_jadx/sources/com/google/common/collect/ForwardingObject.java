package com.google.common.collect;

/* JADX INFO: loaded from: classes.dex */
public abstract class ForwardingObject {
    protected abstract Object delegate();

    protected ForwardingObject() {
    }

    public String toString() {
        return delegate().toString();
    }
}
