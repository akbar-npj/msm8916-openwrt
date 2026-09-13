package com.android.internal.telephony.gsm;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Registrant;
import android.os.SystemClock;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.text.TextUtils;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.DriverCall;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.UiccCardApplication;

/* JADX INFO: loaded from: classes.dex */
public class GsmConnection extends Connection {
    private static final boolean DBG = true;
    static final int EVENT_DTMF_DONE = 1;
    static final int EVENT_NEXT_POST_DIAL = 3;
    static final int EVENT_PAUSE_DONE = 2;
    static final int EVENT_WAKE_LOCK_TIMEOUT = 4;
    private static final String LOG_TAG = "GsmConnection";
    static final int PAUSE_DELAY_MILLIS = 3000;
    static final int WAKE_LOCK_TIMEOUT_MILLIS = 60000;
    String mAddress;
    long mConnectTime;
    long mCreateTime;
    String mDialString;
    long mDisconnectTime;
    boolean mDisconnected;
    long mDuration;
    Handler mHandler;
    long mHoldingStartTime;
    int mIndex;
    boolean mIsIncoming;
    int mNextPostDialChar;
    int mNumberPresentation;
    GsmCallTracker mOwner;
    GsmCall mParent;
    private PowerManager.WakeLock mPartialWakeLock;
    String mPostDialString;
    UUSInfo mUusInfo;
    Connection.DisconnectCause mCause = Connection.DisconnectCause.NOT_DISCONNECTED;
    Connection.PostDialState mPostDialState = Connection.PostDialState.NOT_STARTED;

    class MyHandler extends Handler {
        MyHandler(Looper l) {
            super(l);
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                case 2:
                case 3:
                    GsmConnection.this.processNextPostDialChar();
                    break;
                case 4:
                    GsmConnection.this.releaseWakeLock();
                    break;
            }
        }
    }

    GsmConnection(Context context, DriverCall dc, GsmCallTracker ct, int index) {
        this.mNumberPresentation = PhoneConstants.PRESENTATION_ALLOWED;
        createWakeLock(context);
        acquireWakeLock();
        this.mOwner = ct;
        this.mHandler = new MyHandler(this.mOwner.getLooper());
        this.mAddress = dc.number;
        this.mIsIncoming = dc.isMT;
        this.mCreateTime = System.currentTimeMillis();
        this.mCnapName = dc.name;
        this.mCnapNamePresentation = dc.namePresentation;
        this.mNumberPresentation = dc.numberPresentation;
        this.mUusInfo = dc.uusInfo;
        this.mIndex = index;
        this.mParent = parentFromDCState(dc.state);
        this.mParent.attach(this, dc);
    }

    GsmConnection(Context context, String dialString, GsmCallTracker ct, GsmCall parent) {
        this.mNumberPresentation = PhoneConstants.PRESENTATION_ALLOWED;
        createWakeLock(context);
        acquireWakeLock();
        this.mOwner = ct;
        this.mHandler = new MyHandler(this.mOwner.getLooper());
        this.mDialString = dialString;
        this.mAddress = PhoneNumberUtils.extractNetworkPortionAlt(dialString);
        this.mPostDialString = PhoneNumberUtils.extractPostDialPortion(dialString);
        this.mIndex = -1;
        this.mIsIncoming = false;
        this.mCnapName = null;
        this.mCnapNamePresentation = PhoneConstants.PRESENTATION_ALLOWED;
        this.mNumberPresentation = PhoneConstants.PRESENTATION_ALLOWED;
        this.mCreateTime = System.currentTimeMillis();
        this.mParent = parent;
        parent.attachFake(this, Call.State.DIALING);
    }

    public void dispose() {
    }

    static boolean equalsHandlesNulls(Object a, Object b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }

    boolean compareTo(DriverCall c) {
        if (!this.mIsIncoming && !c.isMT) {
            return true;
        }
        String cAddress = PhoneNumberUtils.stringFromStringAndTOA(c.number, c.TOA);
        return this.mIsIncoming == c.isMT && equalsHandlesNulls(this.mAddress, cAddress);
    }

    @Override // com.android.internal.telephony.Connection
    public String getAddress() {
        return this.mAddress;
    }

    @Override // com.android.internal.telephony.Connection
    public GsmCall getCall() {
        return this.mParent;
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
    public void setConnectTime(long timeInMillis) {
        this.mConnectTime = timeInMillis;
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
        if (this.mDuration == 0) {
            return SystemClock.elapsedRealtime() - this.mConnectTimeReal;
        }
        return this.mDuration;
    }

    @Override // com.android.internal.telephony.Connection
    public long getHoldDurationMillis() {
        if (getState() != Call.State.HOLDING) {
            return 0L;
        }
        return SystemClock.elapsedRealtime() - this.mHoldingStartTime;
    }

    @Override // com.android.internal.telephony.Connection
    public Connection.DisconnectCause getDisconnectCause() {
        return this.mCause;
    }

    @Override // com.android.internal.telephony.Connection
    public boolean isIncoming() {
        return this.mIsIncoming;
    }

    @Override // com.android.internal.telephony.Connection
    public Call.State getState() {
        return this.mDisconnected ? Call.State.DISCONNECTED : super.getState();
    }

    @Override // com.android.internal.telephony.Connection
    public void hangup() throws CallStateException {
        if (!this.mDisconnected) {
            this.mOwner.hangup(this);
            return;
        }
        throw new CallStateException("disconnected");
    }

    @Override // com.android.internal.telephony.Connection
    public void separate() throws CallStateException {
        if (!this.mDisconnected) {
            this.mOwner.separate(this);
            return;
        }
        throw new CallStateException("disconnected");
    }

    @Override // com.android.internal.telephony.Connection
    public Connection.PostDialState getPostDialState() {
        return this.mPostDialState;
    }

    @Override // com.android.internal.telephony.Connection
    public void proceedAfterWaitChar() {
        if (this.mPostDialState != Connection.PostDialState.WAIT) {
            Rlog.w(LOG_TAG, "GsmConnection.proceedAfterWaitChar(): Expected getPostDialState() to be WAIT but was " + this.mPostDialState);
        } else {
            setPostDialState(Connection.PostDialState.STARTED);
            processNextPostDialChar();
        }
    }

    @Override // com.android.internal.telephony.Connection
    public void proceedAfterWildChar(String str) {
        if (this.mPostDialState != Connection.PostDialState.WILD) {
            Rlog.w(LOG_TAG, "GsmConnection.proceedAfterWaitChar(): Expected getPostDialState() to be WILD but was " + this.mPostDialState);
            return;
        }
        setPostDialState(Connection.PostDialState.STARTED);
        this.mPostDialString = str + this.mPostDialString.substring(this.mNextPostDialChar);
        this.mNextPostDialChar = 0;
        log(new StringBuilder().append("proceedAfterWildChar: new postDialString is ").append(this.mPostDialString).toString());
        processNextPostDialChar();
    }

    @Override // com.android.internal.telephony.Connection
    public void cancelPostDial() {
        setPostDialState(Connection.PostDialState.CANCELLED);
    }

    void onHangupLocal() {
        this.mCause = Connection.DisconnectCause.LOCAL;
    }

    Connection.DisconnectCause disconnectCauseFromCode(int causeCode) {
        switch (causeCode) {
            case 1:
                return Connection.DisconnectCause.UNOBTAINABLE_NUMBER;
            case 17:
                return Connection.DisconnectCause.BUSY;
            case 34:
            case CallFailCause.TEMPORARY_FAILURE /* 41 */:
            case 42:
            case CallFailCause.CHANNEL_NOT_AVAIL /* 44 */:
            case CallFailCause.QOS_NOT_AVAIL /* 49 */:
            case CallFailCause.BEARER_NOT_AVAIL /* 58 */:
                return Connection.DisconnectCause.CONGESTION;
            case 68:
                return Connection.DisconnectCause.LIMIT_EXCEEDED;
            case 240:
                return Connection.DisconnectCause.CALL_BARRED;
            case 241:
                return Connection.DisconnectCause.FDN_BLOCKED;
            case 244:
                return Connection.DisconnectCause.DIAL_MODIFIED_TO_USSD;
            case 245:
                return Connection.DisconnectCause.DIAL_MODIFIED_TO_SS;
            case 246:
                return Connection.DisconnectCause.DIAL_MODIFIED_TO_DIAL;
            case 325:
                return Connection.DisconnectCause.EMERGENCY_TEMP_FAILURE;
            case 326:
                return Connection.DisconnectCause.EMERGENCY_PERM_FAILURE;
            default:
                GSMPhone phone = this.mOwner.mPhone;
                int serviceState = phone.getServiceState().getState();
                UiccCardApplication cardApp = phone.getUiccCardApplication();
                IccCardApplicationStatus.AppState uiccAppState = cardApp != null ? cardApp.getState() : IccCardApplicationStatus.AppState.APPSTATE_UNKNOWN;
                if (serviceState == 3) {
                    return Connection.DisconnectCause.POWER_OFF;
                }
                if (serviceState == 1 || phone.getServiceState().isEmergencyOnly()) {
                    if (phone.getServiceState().isEmergencyOnly() && causeCode == 16) {
                        return Connection.DisconnectCause.NORMAL;
                    }
                    return Connection.DisconnectCause.OUT_OF_SERVICE;
                }
                if (causeCode == 65535) {
                    if (phone.mSST.mRestrictedState.isCsRestricted()) {
                        return Connection.DisconnectCause.CS_RESTRICTED;
                    }
                    if (phone.mSST.mRestrictedState.isCsEmergencyRestricted()) {
                        return Connection.DisconnectCause.CS_RESTRICTED_EMERGENCY;
                    }
                    if (phone.mSST.mRestrictedState.isCsNormalRestricted()) {
                        return Connection.DisconnectCause.CS_RESTRICTED_NORMAL;
                    }
                    return Connection.DisconnectCause.ERROR_UNSPECIFIED;
                }
                if (causeCode == 16) {
                    return Connection.DisconnectCause.NORMAL;
                }
                if (uiccAppState != IccCardApplicationStatus.AppState.APPSTATE_READY) {
                    return Connection.DisconnectCause.ICC_ERROR;
                }
                return Connection.DisconnectCause.ERROR_UNSPECIFIED;
        }
    }

    void onRemoteDisconnect(int causeCode) {
        onDisconnect(disconnectCauseFromCode(causeCode));
    }

    boolean onDisconnect(Connection.DisconnectCause cause) {
        boolean changed = false;
        this.mCause = cause;
        if (!this.mDisconnected) {
            this.mIndex = -1;
            this.mDisconnectTime = System.currentTimeMillis();
            this.mDuration = SystemClock.elapsedRealtime() - this.mConnectTimeReal;
            this.mDisconnected = true;
            Rlog.d(LOG_TAG, "onDisconnect: cause=" + cause);
            this.mOwner.mPhone.notifyDisconnect(this);
            if (this.mParent != null) {
                changed = this.mParent.connectionDisconnected(this);
            }
        }
        releaseWakeLock();
        return changed;
    }

    boolean update(DriverCall dc) {
        boolean changed;
        boolean changed2 = false;
        boolean wasConnectingInOrOut = isConnectingInOrOut();
        boolean wasHolding = getState() == Call.State.HOLDING;
        GsmCall newParent = parentFromDCState(dc.state);
        if (!equalsHandlesNulls(this.mAddress, dc.number)) {
            log("update: phone # changed!");
            this.mAddress = dc.number;
            changed2 = true;
        }
        if (TextUtils.isEmpty(dc.name)) {
            if (!TextUtils.isEmpty(this.mCnapName)) {
                changed2 = true;
                this.mCnapName = "";
            }
        } else if (!dc.name.equals(this.mCnapName)) {
            changed2 = true;
            this.mCnapName = dc.name;
        }
        log("--dssds----" + this.mCnapName);
        this.mCnapNamePresentation = dc.namePresentation;
        this.mNumberPresentation = dc.numberPresentation;
        if (newParent != this.mParent) {
            if (this.mParent != null) {
                this.mParent.detach(this);
            }
            newParent.attach(this, dc);
            this.mParent = newParent;
            changed = true;
        } else {
            boolean parentStateChange = this.mParent.update(this, dc);
            changed = changed2 || parentStateChange;
        }
        log("update: parent=" + this.mParent + ", hasNewParent=" + (newParent != this.mParent) + ", wasConnectingInOrOut=" + wasConnectingInOrOut + ", wasHolding=" + wasHolding + ", isConnectingInOrOut=" + isConnectingInOrOut() + ", changed=" + changed);
        if (wasConnectingInOrOut && !isConnectingInOrOut()) {
            onConnectedInOrOut();
        }
        if (changed && !wasHolding && getState() == Call.State.HOLDING) {
            onStartedHolding();
        }
        return changed;
    }

    void fakeHoldBeforeDial() {
        if (this.mParent != null) {
            this.mParent.detach(this);
        }
        this.mParent = this.mOwner.mBackgroundCall;
        this.mParent.attachFake(this, Call.State.HOLDING);
        onStartedHolding();
    }

    int getGSMIndex() throws CallStateException {
        if (this.mIndex >= 0) {
            return this.mIndex + 1;
        }
        throw new CallStateException("GSM index not yet assigned");
    }

    void onConnectedInOrOut() {
        this.mConnectTime = System.currentTimeMillis();
        this.mConnectTimeReal = SystemClock.elapsedRealtime();
        this.mDuration = 0L;
        log("onConnectedInOrOut: connectTime=" + this.mConnectTime);
        if (!this.mIsIncoming) {
            processNextPostDialChar();
        }
        releaseWakeLock();
    }

    void onStartedHolding() {
        this.mHoldingStartTime = SystemClock.elapsedRealtime();
    }

    private boolean processPostDialChar(char c) {
        if (PhoneNumberUtils.is12Key(c)) {
            this.mOwner.mCi.sendDtmf(c, this.mHandler.obtainMessage(1));
            return true;
        }
        if (c == ',') {
            this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(2), 3000L);
            return true;
        }
        if (c == ';') {
            setPostDialState(Connection.PostDialState.WAIT);
            return true;
        }
        if (c == 'N') {
            setPostDialState(Connection.PostDialState.WILD);
            return true;
        }
        return false;
    }

    @Override // com.android.internal.telephony.Connection
    public String getRemainingPostDialString() {
        return (this.mPostDialState == Connection.PostDialState.CANCELLED || this.mPostDialState == Connection.PostDialState.COMPLETE || this.mPostDialString == null || this.mPostDialString.length() <= this.mNextPostDialChar) ? "" : this.mPostDialString.substring(this.mNextPostDialChar);
    }

    protected void finalize() {
        if (this.mPartialWakeLock.isHeld()) {
            Rlog.e(LOG_TAG, "[GSMConn] UNEXPECTED; mPartialWakeLock is held when finalizing.");
        }
        releaseWakeLock();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void processNextPostDialChar() {
        char c;
        Message notifyMessage;
        if (this.mPostDialState != Connection.PostDialState.CANCELLED) {
            if (this.mPostDialString == null || this.mPostDialString.length() <= this.mNextPostDialChar) {
                setPostDialState(Connection.PostDialState.COMPLETE);
                c = 0;
            } else {
                setPostDialState(Connection.PostDialState.STARTED);
                String str = this.mPostDialString;
                int i = this.mNextPostDialChar;
                this.mNextPostDialChar = i + 1;
                c = str.charAt(i);
                boolean isValid = processPostDialChar(c);
                if (!isValid) {
                    this.mHandler.obtainMessage(3).sendToTarget();
                    Rlog.e("GSM", "processNextPostDialChar: c=" + c + " isn't valid!");
                    return;
                }
            }
            Registrant postDialHandler = this.mOwner.mPhone.mPostDialHandler;
            if (postDialHandler != null && (notifyMessage = postDialHandler.messageForRegistrant()) != null) {
                Connection.PostDialState state = this.mPostDialState;
                AsyncResult ar = AsyncResult.forMessage(notifyMessage);
                ar.result = this;
                ar.userObj = state;
                notifyMessage.arg1 = c;
                notifyMessage.sendToTarget();
            }
        }
    }

    private boolean isConnectingInOrOut() {
        return this.mParent == null || this.mParent == this.mOwner.mRingingCall || this.mParent.mState == Call.State.DIALING || this.mParent.mState == Call.State.ALERTING;
    }

    private GsmCall parentFromDCState(DriverCall.State state) {
        switch (state) {
            case ACTIVE:
            case DIALING:
            case ALERTING:
                return this.mOwner.mForegroundCall;
            case HOLDING:
                return this.mOwner.mBackgroundCall;
            case INCOMING:
            case WAITING:
                return this.mOwner.mRingingCall;
            default:
                throw new RuntimeException("illegal call state: " + state);
        }
    }

    private void setPostDialState(Connection.PostDialState s) {
        if (this.mPostDialState != Connection.PostDialState.STARTED && s == Connection.PostDialState.STARTED) {
            acquireWakeLock();
            Message msg = this.mHandler.obtainMessage(4);
            this.mHandler.sendMessageDelayed(msg, 60000L);
        } else if (this.mPostDialState == Connection.PostDialState.STARTED && s != Connection.PostDialState.STARTED) {
            this.mHandler.removeMessages(4);
            releaseWakeLock();
        }
        this.mPostDialState = s;
    }

    private void createWakeLock(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService("power");
        this.mPartialWakeLock = pm.newWakeLock(1, LOG_TAG);
    }

    private void acquireWakeLock() {
        log("acquireWakeLock");
        this.mPartialWakeLock.acquire();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void releaseWakeLock() {
        synchronized (this.mPartialWakeLock) {
            if (this.mPartialWakeLock.isHeld()) {
                log("releaseWakeLock");
                this.mPartialWakeLock.release();
            }
        }
    }

    private void log(String msg) {
        Rlog.d(LOG_TAG, "[GSMConn] " + msg);
    }

    @Override // com.android.internal.telephony.Connection
    public int getNumberPresentation() {
        return this.mNumberPresentation;
    }

    @Override // com.android.internal.telephony.Connection
    public UUSInfo getUUSInfo() {
        return this.mUusInfo;
    }
}
