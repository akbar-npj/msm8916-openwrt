package com.android.internal.telephony;

/* JADX INFO: loaded from: classes.dex */
public class SmsResponse {
    String mAckPdu;
    int mErrorCode;
    int mMessageRef;

    public SmsResponse(int messageRef, String ackPdu, int errorCode) {
        this.mMessageRef = messageRef;
        this.mAckPdu = ackPdu;
        this.mErrorCode = errorCode;
    }

    public String toString() {
        String ret = "{ mMessageRef = " + this.mMessageRef + ", mErrorCode = " + this.mErrorCode + ", mAckPdu = " + this.mAckPdu + "}";
        return ret;
    }
}
