package com.android.internal.telephony.sip;

import android.os.SystemClock;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.UUSInfo;

/* JADX INFO: loaded from: classes.dex */
abstract class SipConnectionBase extends Connection {
    private static final boolean DBG = true;
    private static final String LOG_TAG = "SipConnBase";
    private static final boolean VDBG = false;
    private long mConnectTime;
    private long mConnectTimeReal;
    private long mCreateTime;
    private long mDisconnectTime;
    private long mHoldingStartTime;
    private int mNextPostDialChar;
    private String mPostDialString;
    private long mDuration = -1;
    private Connection.DisconnectCause mCause = Connection.DisconnectCause.NOT_DISCONNECTED;
    private Connection.PostDialState mPostDialState = Connection.PostDialState.NOT_STARTED;

    protected abstract Phone getPhone();

    SipConnectionBase(String dialString) {
        log("SipConnectionBase: ctor dialString=" + dialString);
        this.mPostDialString = PhoneNumberUtils.extractPostDialPortion(dialString);
        this.mCreateTime = System.currentTimeMillis();
    }

    protected void setState(Call.State state) {
        log("setState: state=" + state);
        switch (state) {
            case ACTIVE:
                if (this.mConnectTime == 0) {
                    this.mConnectTimeReal = SystemClock.elapsedRealtime();
                    this.mConnectTime = System.currentTimeMillis();
                }
                break;
            case DISCONNECTED:
                this.mDuration = getDurationMillis();
                this.mDisconnectTime = System.currentTimeMillis();
                break;
            case HOLDING:
                this.mHoldingStartTime = SystemClock.elapsedRealtime();
                break;
        }
    }

    @Override // com.android.internal.telephony.Connection
    public long getCreateTime() {
        return this.mCreateTime;
    }

    @Override // com.android.internal.telephony.Connection
    public long getConnectTime() {
        return this.mConnectTime;
    }

    @Override // com.android.internal.telephony.Connection
    public long getDisconnectTime() {
        return this.mDisconnectTime;
    }

    @Override // com.android.internal.telephony.Connection
    public long getDurationMillis() {
        if (this.mConnectTimeReal == 0) {
            return 0L;
        }
        if (this.mDuration < 0) {
            long dur = SystemClock.elapsedRealtime() - this.mConnectTimeReal;
            return dur;
        }
        long dur2 = this.mDuration;
        return dur2;
    }

    @Override // com.android.internal.telephony.Connection
    public long getHoldDurationMillis() {
        if (getState() != Call.State.HOLDING) {
            return 0L;
        }
        long dur = SystemClock.elapsedRealtime() - this.mHoldingStartTime;
        return dur;
    }

    @Override // com.android.internal.telephony.Connection
    public Connection.DisconnectCause getDisconnectCause() {
        return this.mCause;
    }

    void setDisconnectCause(Connection.DisconnectCause cause) {
        log("setDisconnectCause: prev=" + this.mCause + " new=" + cause);
        this.mCause = cause;
    }

    @Override // com.android.internal.telephony.Connection
    public Connection.PostDialState getPostDialState() {
        return this.mPostDialState;
    }

    @Override // com.android.internal.telephony.Connection
    public void proceedAfterWaitChar() {
        log("proceedAfterWaitChar: ignore");
    }

    @Override // com.android.internal.telephony.Connection
    public void proceedAfterWildChar(String str) {
        log("proceedAfterWildChar: ignore");
    }

    @Override // com.android.internal.telephony.Connection
    public void cancelPostDial() {
        log("cancelPostDial: ignore");
    }

    @Override // com.android.internal.telephony.Connection
    public String getRemainingPostDialString() {
        if (this.mPostDialState != Connection.PostDialState.CANCELLED && this.mPostDialState != Connection.PostDialState.COMPLETE && this.mPostDialString != null && this.mPostDialString.length() > this.mNextPostDialChar) {
            return this.mPostDialString.substring(this.mNextPostDialChar);
        }
        log("getRemaingPostDialString: ret empty string");
        return "";
    }

    private void log(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    @Override // com.android.internal.telephony.Connection
    public int getNumberPresentation() {
        return PhoneConstants.PRESENTATION_ALLOWED;
    }

    @Override // com.android.internal.telephony.Connection
    public UUSInfo getUUSInfo() {
        return null;
    }
}
