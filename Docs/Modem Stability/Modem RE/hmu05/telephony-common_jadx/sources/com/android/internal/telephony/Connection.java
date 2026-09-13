package com.android.internal.telephony;

import android.telephony.Rlog;

/* JADX INFO: loaded from: classes.dex */
public abstract class Connection {
    private static String LOG_TAG = "Connection";
    public String errorInfo;
    protected String mCnapName;
    public long mConnectTimeReal;
    Object mUserData;
    protected int mCnapNamePresentation = PhoneConstants.PRESENTATION_ALLOWED;
    public CallDetails callDetails = new CallDetails();
    public CallModify callModifyRequest = null;

    public enum DisconnectCause {
        NOT_DISCONNECTED,
        INCOMING_MISSED,
        NORMAL,
        LOCAL,
        BUSY,
        CONGESTION,
        MMI,
        INVALID_NUMBER,
        NUMBER_UNREACHABLE,
        SERVER_UNREACHABLE,
        INVALID_CREDENTIALS,
        OUT_OF_NETWORK,
        SERVER_ERROR,
        TIMED_OUT,
        LOST_SIGNAL,
        LIMIT_EXCEEDED,
        INCOMING_REJECTED,
        POWER_OFF,
        OUT_OF_SERVICE,
        ICC_ERROR,
        CALL_BARRED,
        FDN_BLOCKED,
        CS_RESTRICTED,
        CS_RESTRICTED_NORMAL,
        CS_RESTRICTED_EMERGENCY,
        UNOBTAINABLE_NUMBER,
        DIAL_MODIFIED_TO_USSD,
        DIAL_MODIFIED_TO_SS,
        DIAL_MODIFIED_TO_DIAL,
        CDMA_LOCKED_UNTIL_POWER_CYCLE,
        CDMA_DROP,
        CDMA_INTERCEPT,
        CDMA_REORDER,
        CDMA_SO_REJECT,
        CDMA_RETRY_ORDER,
        CDMA_ACCESS_FAILURE,
        CDMA_PREEMPTED,
        CDMA_NOT_EMERGENCY,
        CDMA_ACCESS_BLOCKED,
        EMERGENCY_TEMP_FAILURE,
        EMERGENCY_PERM_FAILURE,
        ERROR_UNSPECIFIED,
        SRVCC_CALL_DROP,
        ANSWERED_ELSEWHERE,
        CALL_FAIL_MISC
    }

    public enum PostDialState {
        NOT_STARTED,
        STARTED,
        WAIT,
        WILD,
        COMPLETE,
        CANCELLED,
        PAUSE
    }

    public abstract void cancelPostDial();

    public abstract String getAddress();

    public abstract Call getCall();

    public abstract long getConnectTime();

    public abstract long getCreateTime();

    public abstract DisconnectCause getDisconnectCause();

    public abstract long getDisconnectTime();

    public abstract long getDurationMillis();

    public abstract long getHoldDurationMillis();

    public abstract int getNumberPresentation();

    public abstract PostDialState getPostDialState();

    public abstract String getRemainingPostDialString();

    public abstract UUSInfo getUUSInfo();

    public abstract void hangup() throws CallStateException;

    public abstract boolean isIncoming();

    public abstract void proceedAfterWaitChar();

    public abstract void proceedAfterWildChar(String str);

    public abstract void separate() throws CallStateException;

    public String getCnapName() {
        return this.mCnapName;
    }

    public String getOrigDialString() {
        return null;
    }

    public int getCnapNamePresentation() {
        return this.mCnapNamePresentation;
    }

    public CallDetails getCallDetails() {
        return this.callDetails;
    }

    public CallModify getCallModify() {
        return this.callModifyRequest;
    }

    public String getErrorInfo() {
        return this.errorInfo;
    }

    public void setConnectionDetails(CallDetails ConnDetails) {
        this.callDetails = ConnDetails;
    }

    public void setModifyConnectionDetails(CallModify modifyConn) {
        this.callModifyRequest = modifyConn;
    }

    public void setErrorInfo(String errorInfo) {
    }

    public void setConnectTime(long timeInMillis) {
        Rlog.e(LOG_TAG, "setConnectTime() not implemented");
    }

    public Call.State getState() {
        Call c = getCall();
        return c == null ? Call.State.IDLE : c.getState();
    }

    public boolean isAlive() {
        return getState().isAlive();
    }

    public boolean isRinging() {
        return getState().isRinging();
    }

    public Object getUserData() {
        return this.mUserData;
    }

    public void setUserData(Object userdata) {
        this.mUserData = userdata;
    }

    public void clearUserData() {
        this.mUserData = null;
    }

    public int getIndex() throws CallStateException {
        throw new CallStateException("Connection index not assigned");
    }

    public String toString() {
        StringBuilder str = new StringBuilder(128);
        if (Rlog.isLoggable(LOG_TAG, 3)) {
            str.append("addr: " + getAddress()).append(" pres.: " + getNumberPresentation()).append(" dial: " + getOrigDialString()).append(" postdial: " + getRemainingPostDialString()).append(" cnap name: " + getCnapName()).append("(" + getCnapNamePresentation() + ")");
        }
        str.append(" incoming: " + isIncoming()).append(" state: " + getState()).append(" post dial state: " + getPostDialState());
        return str.toString();
    }
}
