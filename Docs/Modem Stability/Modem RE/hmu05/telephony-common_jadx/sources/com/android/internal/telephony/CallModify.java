package com.android.internal.telephony;

/* JADX INFO: loaded from: classes.dex */
public class CallModify {
    public CallDetails call_details;
    public int call_index;
    public int error;
    public static int E_SUCCESS = 0;
    public static int E_CANCELLED = 7;
    public static int E_UNUSED = 16;

    public CallModify() {
        this(new CallDetails(), 0);
    }

    public CallModify(CallDetails callDetails, int callIndex) {
        this(callDetails, callIndex, E_SUCCESS);
    }

    public CallModify(CallDetails callDetails, int callIndex, int err) {
        this.call_details = callDetails;
        this.call_index = callIndex;
        this.error = err;
    }

    public void setCallDetails(CallDetails calldetails) {
        this.call_details = new CallDetails(calldetails);
    }

    public boolean error() {
        return (this.error == E_UNUSED || this.error == E_SUCCESS) ? false : true;
    }

    public String toString() {
        return " " + this.call_index + " " + this.call_details + " " + this.error;
    }
}
