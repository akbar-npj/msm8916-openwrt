package com.android.internal.telephony.cat;

/* JADX INFO: compiled from: CatService.java */
/* JADX INFO: loaded from: classes.dex */
class RilMessage {
    Object mData;
    int mId;
    ResultCode mResCode;

    RilMessage(int msgId, String rawData) {
        this.mId = msgId;
        this.mData = rawData;
    }

    RilMessage(RilMessage other) {
        this.mId = other.mId;
        this.mData = other.mData;
        this.mResCode = other.mResCode;
    }
}
