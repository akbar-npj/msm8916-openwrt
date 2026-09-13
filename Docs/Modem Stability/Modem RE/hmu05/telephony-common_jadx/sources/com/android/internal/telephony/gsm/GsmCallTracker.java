package com.android.internal.telephony.gsm;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.EventLog;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.DriverCall;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.UUSInfo;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public final class GsmCallTracker extends CallTracker {
    private static final boolean DBG_POLL = false;
    static final String LOG_TAG = "GsmCallTracker";
    static final int MAX_CONNECTIONS = 7;
    static final int MAX_CONNECTIONS_PER_CALL = 5;
    private static final boolean REPEAT_POLLING = false;
    boolean mHangupPendingMO;
    GsmConnection mPendingMO;
    GSMPhone mPhone;
    GsmConnection[] mConnections = new GsmConnection[7];
    RegistrantList mVoiceCallEndedRegistrants = new RegistrantList();
    RegistrantList mVoiceCallStartedRegistrants = new RegistrantList();
    ArrayList<GsmConnection> mDroppedDuringPoll = new ArrayList<>(7);
    GsmCall mRingingCall = new GsmCall(this);
    GsmCall mForegroundCall = new GsmCall(this);
    GsmCall mBackgroundCall = new GsmCall(this);
    boolean callSwitchPending = false;
    boolean mDesiredMute = false;
    PhoneConstants.State mState = PhoneConstants.State.IDLE;

    GsmCallTracker(GSMPhone phone) {
        this.mPhone = phone;
        this.mCi = phone.mCi;
        this.mCi.registerForCallStateChanged(this, 2, null);
        this.mCi.registerForOn(this, 9, null);
        this.mCi.registerForNotAvailable(this, 10, null);
    }

    public void dispose() {
        this.mCi.unregisterForCallStateChanged(this);
        this.mCi.unregisterForOn(this);
        this.mCi.unregisterForNotAvailable(this);
        GsmConnection[] arr$ = this.mConnections;
        for (GsmConnection c : arr$) {
            if (c != null) {
                try {
                    hangup(c);
                    Rlog.d(LOG_TAG, "dispose: call connnection onDisconnect, cause LOST_SIGNAL");
                    c.onDisconnect(Connection.DisconnectCause.LOST_SIGNAL);
                } catch (CallStateException ex) {
                    Rlog.e(LOG_TAG, "dispose: unexpected error on hangup", ex);
                }
            }
        }
        try {
            if (this.mPendingMO != null) {
                hangup(this.mPendingMO);
                Rlog.d(LOG_TAG, "dispose: call mPendingMO.onDsiconnect, cause LOST_SIGNAL");
                this.mPendingMO.onDisconnect(Connection.DisconnectCause.LOST_SIGNAL);
            }
        } catch (CallStateException ex2) {
            Rlog.e(LOG_TAG, "dispose: unexpected error on hangup", ex2);
        }
        clearDisconnected();
    }

    protected void finalize() {
        Rlog.d(LOG_TAG, "GsmCallTracker finalized");
    }

    @Override // com.android.internal.telephony.CallTracker
    public void registerForVoiceCallStarted(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mVoiceCallStartedRegistrants.add(r);
    }

    @Override // com.android.internal.telephony.CallTracker
    public void unregisterForVoiceCallStarted(Handler h) {
        this.mVoiceCallStartedRegistrants.remove(h);
    }

    @Override // com.android.internal.telephony.CallTracker
    public void registerForVoiceCallEnded(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mVoiceCallEndedRegistrants.add(r);
    }

    @Override // com.android.internal.telephony.CallTracker
    public void unregisterForVoiceCallEnded(Handler h) {
        this.mVoiceCallEndedRegistrants.remove(h);
    }

    private void fakeHoldForegroundBeforeDial() {
        List<Connection> connCopy = (List) this.mForegroundCall.mConnections.clone();
        int s = connCopy.size();
        for (int i = 0; i < s; i++) {
            GsmConnection conn = (GsmConnection) connCopy.get(i);
            conn.fakeHoldBeforeDial();
        }
    }

    synchronized Connection dial(String dialString, int clirMode, UUSInfo uusInfo) throws CallStateException {
        clearDisconnected();
        boolean isDialRequestPending = false;
        if (!canDial()) {
            throw new CallStateException("cannot dial in current state");
        }
        if (this.mForegroundCall.getState() == Call.State.ACTIVE) {
            switchWaitingOrHoldingAndActive(clirMode);
            fakeHoldForegroundBeforeDial();
            isDialRequestPending = true;
        }
        if (this.mForegroundCall.getState() != Call.State.IDLE) {
            throw new CallStateException("cannot dial in current state");
        }
        this.mPendingMO = new GsmConnection(this.mPhone.getContext(), checkForTestEmergencyNumber(dialString), this, this.mForegroundCall);
        this.mHangupPendingMO = false;
        if (this.mPendingMO.mAddress == null || this.mPendingMO.mAddress.length() == 0 || this.mPendingMO.mAddress.indexOf(78) >= 0) {
            this.mPendingMO.mCause = Connection.DisconnectCause.INVALID_NUMBER;
            pollCallsWhenSafe();
        } else if (!isDialRequestPending) {
            setMute(false);
            this.mCi.dial(this.mPendingMO.mAddress, clirMode, uusInfo, obtainCompleteMessage());
        }
        updatePhoneState();
        this.mPhone.notifyPreciseCallStateChanged();
        return this.mPendingMO;
    }

    Connection dial(String dialString) throws CallStateException {
        return dial(dialString, 0, null);
    }

    Connection dial(String dialString, UUSInfo uusInfo) throws CallStateException {
        return dial(dialString, 0, uusInfo);
    }

    Connection dial(String dialString, int clirMode) throws CallStateException {
        return dial(dialString, clirMode, null);
    }

    void dialPendingCall(int clirMode) {
        if (this.mPendingMO.mAddress == null || this.mPendingMO.mAddress.length() == 0 || this.mPendingMO.mAddress.indexOf(78) >= 0) {
            this.mPendingMO.mCause = Connection.DisconnectCause.INVALID_NUMBER;
            pollCallsWhenSafe();
        } else {
            setMute(false);
            this.mCi.dial(this.mPendingMO.mAddress, clirMode, obtainCompleteMessage());
        }
        updatePhoneState();
        this.mPhone.notifyPreciseCallStateChanged();
    }

    void acceptCall() throws CallStateException {
        if (this.mRingingCall.getState() == Call.State.INCOMING) {
            Rlog.i("phone", "acceptCall: incoming...");
            setMute(false);
            this.mCi.acceptCall(obtainCompleteMessage());
        } else {
            if (this.mRingingCall.getState() == Call.State.WAITING) {
                setMute(false);
                switchWaitingOrHoldingAndActive();
                return;
            }
            throw new CallStateException("phone not ringing");
        }
    }

    void rejectCall() throws CallStateException {
        if (this.mRingingCall.getState().isRinging()) {
            this.mCi.rejectCall(obtainCompleteMessage());
            return;
        }
        throw new CallStateException("phone not ringing");
    }

    void switchWaitingOrHoldingAndActive() throws CallStateException {
        if (this.mRingingCall.getState() == Call.State.INCOMING) {
            throw new CallStateException("cannot be in the incoming state");
        }
        if (!this.callSwitchPending) {
            this.mCi.switchWaitingOrHoldingAndActive(obtainCompleteMessage(8));
            this.callSwitchPending = true;
        } else {
            Rlog.w(LOG_TAG, "Call Switch request ignored due to pending response");
        }
    }

    void conference() {
        this.mCi.conference(obtainCompleteMessage(11));
    }

    void switchWaitingOrHoldingAndActive(int clirMode) throws CallStateException {
        if (this.mRingingCall.getState() == Call.State.INCOMING) {
            throw new CallStateException("cannot be in the incoming state");
        }
        if (!this.callSwitchPending) {
            this.mCi.switchWaitingOrHoldingAndActive(obtainCompleteMessage(8, clirMode));
            this.callSwitchPending = true;
        } else {
            Rlog.w(LOG_TAG, "Call Switch request ignored due to pending response");
        }
    }

    void explicitCallTransfer() {
        this.mCi.explicitCallTransfer(obtainCompleteMessage(13));
    }

    void clearDisconnected() {
        internalClearDisconnected();
        updatePhoneState();
        this.mPhone.notifyPreciseCallStateChanged();
    }

    boolean canConference() {
        return this.mForegroundCall.getState() == Call.State.ACTIVE && this.mBackgroundCall.getState() == Call.State.HOLDING && !this.mBackgroundCall.isFull() && !this.mForegroundCall.isFull();
    }

    boolean canDial() {
        int serviceState = this.mPhone.getServiceState().getState();
        String disableCall = SystemProperties.get("ro.telephony.disable-call", "false");
        return (serviceState == 3 || this.mPendingMO != null || this.mRingingCall.isRinging() || disableCall.equals("true") || (this.mForegroundCall.getState().isAlive() && this.mBackgroundCall.getState().isAlive())) ? false : true;
    }

    boolean canTransfer() {
        return (this.mForegroundCall.getState() == Call.State.ACTIVE || this.mForegroundCall.getState() == Call.State.ALERTING || this.mForegroundCall.getState() == Call.State.DIALING) && this.mBackgroundCall.getState() == Call.State.HOLDING;
    }

    private void internalClearDisconnected() {
        this.mRingingCall.clearDisconnected();
        this.mForegroundCall.clearDisconnected();
        this.mBackgroundCall.clearDisconnected();
    }

    private Message obtainCompleteMessage() {
        return obtainCompleteMessage(4);
    }

    private Message obtainCompleteMessage(int what) {
        this.mPendingOperations++;
        this.mLastRelevantPoll = null;
        this.mNeedsPoll = true;
        return obtainMessage(what);
    }

    private Message obtainCompleteMessage(int what, int clirMode) {
        this.mPendingOperations++;
        this.mLastRelevantPoll = null;
        this.mNeedsPoll = true;
        return obtainMessage(what, Integer.valueOf(clirMode));
    }

    private void operationComplete() {
        this.mPendingOperations--;
        if (this.mPendingOperations == 0 && this.mNeedsPoll) {
            this.mLastRelevantPoll = obtainMessage(1);
            this.mCi.getCurrentCalls(this.mLastRelevantPoll);
        } else if (this.mPendingOperations < 0) {
            Rlog.e(LOG_TAG, "GsmCallTracker.pendingOperations < 0");
            this.mPendingOperations = 0;
        }
    }

    private void updatePhoneState() {
        PhoneConstants.State oldState = this.mState;
        if (this.mRingingCall.isRinging()) {
            this.mState = PhoneConstants.State.RINGING;
        } else if (this.mPendingMO != null || !this.mForegroundCall.isIdle() || !this.mBackgroundCall.isIdle()) {
            this.mState = PhoneConstants.State.OFFHOOK;
        } else {
            this.mState = PhoneConstants.State.IDLE;
        }
        if (this.mState == PhoneConstants.State.IDLE && oldState != this.mState) {
            this.mVoiceCallEndedRegistrants.notifyRegistrants(new AsyncResult((Object) null, (Object) null, (Throwable) null));
        } else if (oldState == PhoneConstants.State.IDLE && oldState != this.mState) {
            this.mVoiceCallStartedRegistrants.notifyRegistrants(new AsyncResult((Object) null, (Object) null, (Throwable) null));
        }
        if (this.mState != oldState) {
            this.mPhone.notifyPhoneStateChanged();
        }
    }

    @Override // com.android.internal.telephony.CallTracker
    protected synchronized void handlePollCalls(AsyncResult ar) {
        List polledCalls;
        Connection.DisconnectCause cause;
        if (ar.exception == null) {
            polledCalls = (List) ar.result;
        } else if (isCommandExceptionRadioNotAvailable(ar.exception)) {
            polledCalls = new ArrayList();
        } else {
            pollCallsAfterDelay();
        }
        Connection newRinging = null;
        boolean hasNonHangupStateChanged = false;
        boolean hasAnyCallDisconnected = false;
        boolean unknownConnectionAppeared = false;
        int curDC = 0;
        int dcSize = polledCalls.size();
        for (int i = 0; i < this.mConnections.length; i++) {
            GsmConnection conn = this.mConnections[i];
            DriverCall dc = null;
            if (curDC < dcSize) {
                dc = (DriverCall) polledCalls.get(curDC);
                if (dc.index == i + 1) {
                    curDC++;
                } else {
                    dc = null;
                }
            }
            if (conn == null && dc != null) {
                if (this.mPendingMO != null && this.mPendingMO.compareTo(dc)) {
                    this.mConnections[i] = this.mPendingMO;
                    this.mPendingMO.mIndex = i;
                    this.mPendingMO.update(dc);
                    this.mPendingMO = null;
                    if (this.mHangupPendingMO) {
                        this.mHangupPendingMO = false;
                        try {
                            log("poll: hangupPendingMO, hangup conn " + i);
                            hangup(this.mConnections[i]);
                        } catch (CallStateException e) {
                            Rlog.e(LOG_TAG, "unexpected error on hangup");
                        }
                    }
                } else {
                    this.mConnections[i] = new GsmConnection(this.mPhone.getContext(), dc, this, i);
                    if (this.mConnections[i].getCall() == this.mRingingCall) {
                        newRinging = this.mConnections[i];
                    } else {
                        Rlog.i(LOG_TAG, "Phantom call appeared " + dc);
                        if (dc.state != DriverCall.State.ALERTING && dc.state != DriverCall.State.DIALING) {
                            this.mConnections[i].onConnectedInOrOut();
                            if (dc.state == DriverCall.State.HOLDING) {
                                this.mConnections[i].onStartedHolding();
                            }
                        }
                        unknownConnectionAppeared = true;
                    }
                }
                hasNonHangupStateChanged = true;
            } else if (conn != null && dc == null) {
                this.mDroppedDuringPoll.add(conn);
                this.mConnections[i] = null;
            } else if (conn != null && dc != null && !conn.compareTo(dc)) {
                this.mDroppedDuringPoll.add(conn);
                this.mConnections[i] = new GsmConnection(this.mPhone.getContext(), dc, this, i);
                if (this.mConnections[i].getCall() == this.mRingingCall) {
                    newRinging = this.mConnections[i];
                }
                hasNonHangupStateChanged = true;
            } else if (conn != null && dc != null) {
                boolean changed = conn.update(dc);
                hasNonHangupStateChanged = hasNonHangupStateChanged || changed;
            }
        }
        if (this.mPendingMO != null) {
            Rlog.d(LOG_TAG, "Pending MO dropped before poll fg state:" + this.mForegroundCall.getState());
            this.mDroppedDuringPoll.add(this.mPendingMO);
            this.mPendingMO = null;
            this.mHangupPendingMO = false;
        }
        if (newRinging != null) {
            this.mPhone.notifyNewRingingConnection(newRinging);
        }
        for (int i2 = this.mDroppedDuringPoll.size() - 1; i2 >= 0; i2--) {
            GsmConnection conn2 = this.mDroppedDuringPoll.get(i2);
            if (conn2.isIncoming() && conn2.getConnectTime() == 0) {
                if (conn2.mCause == Connection.DisconnectCause.LOCAL) {
                    cause = Connection.DisconnectCause.INCOMING_REJECTED;
                } else {
                    cause = Connection.DisconnectCause.INCOMING_MISSED;
                }
                log("missed/rejected call, conn.cause=" + conn2.mCause);
                log("setting cause to " + cause);
                this.mDroppedDuringPoll.remove(i2);
                hasAnyCallDisconnected |= conn2.onDisconnect(cause);
            } else if (conn2.mCause == Connection.DisconnectCause.LOCAL || conn2.mCause == Connection.DisconnectCause.INVALID_NUMBER) {
                this.mDroppedDuringPoll.remove(i2);
                hasAnyCallDisconnected |= conn2.onDisconnect(conn2.mCause);
            }
        }
        if (this.mDroppedDuringPoll.size() > 0) {
            this.mCi.getLastCallFailCause(obtainNoPollCompleteMessage(5));
        }
        if (0 != 0) {
            pollCallsAfterDelay();
        }
        if (newRinging != null || hasNonHangupStateChanged || hasAnyCallDisconnected) {
            internalClearDisconnected();
        }
        updatePhoneState();
        if (unknownConnectionAppeared) {
            this.mPhone.notifyUnknownConnection();
        }
        if (hasNonHangupStateChanged || newRinging != null || hasAnyCallDisconnected) {
            this.mPhone.notifyPreciseCallStateChanged();
        }
    }

    private void handleRadioNotAvailable() {
        pollCallsWhenSafe();
    }

    private void dumpState() {
        Rlog.i(LOG_TAG, "Phone State:" + this.mState);
        Rlog.i(LOG_TAG, "Ringing call: " + this.mRingingCall.toString());
        List<Connection> connections = this.mRingingCall.getConnections();
        int s = connections.size();
        for (int i = 0; i < s; i++) {
            Rlog.i(LOG_TAG, connections.get(i).toString());
        }
        Rlog.i(LOG_TAG, "Foreground call: " + this.mForegroundCall.toString());
        List<Connection> connections2 = this.mForegroundCall.getConnections();
        int s2 = connections2.size();
        for (int i2 = 0; i2 < s2; i2++) {
            Rlog.i(LOG_TAG, connections2.get(i2).toString());
        }
        Rlog.i(LOG_TAG, "Background call: " + this.mBackgroundCall.toString());
        List<Connection> connections3 = this.mBackgroundCall.getConnections();
        int s3 = connections3.size();
        for (int i3 = 0; i3 < s3; i3++) {
            Rlog.i(LOG_TAG, connections3.get(i3).toString());
        }
    }

    void hangup(GsmConnection conn) throws CallStateException {
        if (conn.mOwner != this) {
            throw new CallStateException("GsmConnection " + conn + "does not belong to GsmCallTracker " + this);
        }
        if (conn == this.mPendingMO) {
            log("hangup: set hangupPendingMO to true");
            this.mHangupPendingMO = true;
        } else {
            try {
                this.mCi.hangupConnection(conn.getGSMIndex(), obtainCompleteMessage());
            } catch (CallStateException e) {
                Rlog.w(LOG_TAG, "GsmCallTracker WARN: hangup() on absent connection " + conn);
            }
        }
        conn.onHangupLocal();
    }

    void separate(GsmConnection conn) throws CallStateException {
        if (conn.mOwner != this) {
            throw new CallStateException("GsmConnection " + conn + "does not belong to GsmCallTracker " + this);
        }
        try {
            this.mCi.separateConnection(conn.getGSMIndex(), obtainCompleteMessage(12));
        } catch (CallStateException e) {
            Rlog.w(LOG_TAG, "GsmCallTracker WARN: separate() on absent connection " + conn);
        }
    }

    void setMute(boolean mute) {
        this.mDesiredMute = mute;
        this.mCi.setMute(this.mDesiredMute, null);
    }

    boolean getMute() {
        return this.mDesiredMute;
    }

    void hangup(GsmCall call) throws CallStateException {
        if (call.getConnections().size() == 0) {
            throw new CallStateException("no connections in call");
        }
        if (call == this.mRingingCall) {
            log("(ringing) hangup waiting or background");
            this.mCi.hangupWaitingOrBackground(obtainCompleteMessage());
        } else if (call == this.mForegroundCall) {
            if (call.isDialingOrAlerting()) {
                log("(foregnd) hangup dialing or alerting...");
                hangup((GsmConnection) call.getConnections().get(0));
            } else {
                hangupForegroundResumeBackground();
            }
        } else if (call == this.mBackgroundCall) {
            if (this.mRingingCall.isRinging()) {
                log("hangup all conns in background call");
                hangupAllConnections(call);
            } else {
                hangupWaitingOrBackground();
            }
        } else {
            throw new RuntimeException("GsmCall " + call + "does not belong to GsmCallTracker " + this);
        }
        call.onHangupLocal();
        this.mPhone.notifyPreciseCallStateChanged();
    }

    void hangupWaitingOrBackground() {
        log("hangupWaitingOrBackground");
        this.mCi.hangupWaitingOrBackground(obtainCompleteMessage());
    }

    void hangupForegroundResumeBackground() {
        log("hangupForegroundResumeBackground");
        this.mCi.hangupForegroundResumeBackground(obtainCompleteMessage());
    }

    void hangupConnectionByIndex(GsmCall call, int index) throws CallStateException {
        int count = call.mConnections.size();
        for (int i = 0; i < count; i++) {
            GsmConnection cn = (GsmConnection) call.mConnections.get(i);
            if (cn.getGSMIndex() == index) {
                this.mCi.hangupConnection(index, obtainCompleteMessage());
                return;
            }
        }
        throw new CallStateException("no gsm index found");
    }

    void hangupAllConnections(GsmCall call) {
        try {
            int count = call.mConnections.size();
            for (int i = 0; i < count; i++) {
                GsmConnection cn = (GsmConnection) call.mConnections.get(i);
                this.mCi.hangupConnection(cn.getGSMIndex(), obtainCompleteMessage());
            }
        } catch (CallStateException ex) {
            Rlog.e(LOG_TAG, "hangupConnectionByIndex caught " + ex);
        }
    }

    GsmConnection getConnectionByIndex(GsmCall call, int index) throws CallStateException {
        int count = call.mConnections.size();
        for (int i = 0; i < count; i++) {
            GsmConnection cn = (GsmConnection) call.mConnections.get(i);
            if (cn.getGSMIndex() == index) {
                return cn;
            }
        }
        return null;
    }

    private Phone.SuppService getFailedService(int what) {
        switch (what) {
            case 8:
                return Phone.SuppService.SWITCH;
            case 9:
            case 10:
            default:
                return Phone.SuppService.UNKNOWN;
            case 11:
                return Phone.SuppService.CONFERENCE;
            case 12:
                return Phone.SuppService.SEPARATE;
            case 13:
                return Phone.SuppService.TRANSFER;
        }
    }

    @Override // com.android.internal.telephony.CallTracker, android.os.Handler
    public void handleMessage(Message msg) {
        int causeCode;
        if (!this.mPhone.mIsTheCurrentActivePhone) {
            Rlog.e(LOG_TAG, "Received message " + msg + "[" + msg.what + "] while being destroyed. Ignoring.");
        }
        switch (msg.what) {
            case 1:
                if (msg == this.mLastRelevantPoll) {
                    this.mNeedsPoll = false;
                    this.mLastRelevantPoll = null;
                    handlePollCalls((AsyncResult) msg.obj);
                }
                break;
            case 2:
            case 3:
                pollCallsWhenSafe();
                break;
            case 4:
                operationComplete();
                break;
            case 5:
                AsyncResult ar = (AsyncResult) msg.obj;
                operationComplete();
                if (ar.exception != null) {
                    causeCode = 16;
                    Rlog.i(LOG_TAG, "Exception during getLastCallFailCause, assuming normal disconnect");
                } else {
                    causeCode = ((int[]) ar.result)[0];
                }
                if (causeCode == 34 || causeCode == 41 || causeCode == 42 || causeCode == 44 || causeCode == 49 || causeCode == 58 || causeCode == 65535) {
                    GsmCellLocation loc = (GsmCellLocation) this.mPhone.getCellLocation();
                    Object[] objArr = new Object[3];
                    objArr[0] = Integer.valueOf(causeCode);
                    objArr[1] = Integer.valueOf(loc != null ? loc.getCid() : -1);
                    objArr[2] = Integer.valueOf(TelephonyManager.getDefault().getNetworkType());
                    EventLog.writeEvent(EventLogTags.CALL_DROP, objArr);
                }
                int s = this.mDroppedDuringPoll.size();
                for (int i = 0; i < s; i++) {
                    GsmConnection conn = this.mDroppedDuringPoll.get(i);
                    conn.onRemoteDisconnect(causeCode);
                }
                updatePhoneState();
                this.mPhone.notifyPreciseCallStateChanged();
                this.mDroppedDuringPoll.clear();
                break;
            case 8:
                this.callSwitchPending = false;
                AsyncResult ar2 = (AsyncResult) msg.obj;
                if (ar2.exception != null) {
                    this.mPhone.notifySuppServiceFailed(getFailedService(msg.what));
                } else if (ar2.userObj != null) {
                    dialPendingCall(((Integer) ar2.userObj).intValue());
                }
                operationComplete();
                break;
            case 9:
                handleRadioAvailable();
                break;
            case 10:
                handleRadioNotAvailable();
                break;
            case 11:
            case 12:
            case 13:
                if (((AsyncResult) msg.obj).exception != null) {
                    this.mPhone.notifySuppServiceFailed(getFailedService(msg.what));
                }
                operationComplete();
                break;
        }
    }

    @Override // com.android.internal.telephony.CallTracker
    protected void log(String msg) {
        Rlog.d(LOG_TAG, "[GsmCallTracker] " + msg);
    }

    @Override // com.android.internal.telephony.CallTracker
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("GsmCallTracker extends:");
        super.dump(fd, pw, args);
        pw.println("mConnections: length=" + this.mConnections.length);
        for (int i = 0; i < this.mConnections.length; i++) {
            pw.printf("  mConnections[%d]=%s\n", Integer.valueOf(i), this.mConnections[i]);
        }
        pw.println(" mVoiceCallEndedRegistrants=" + this.mVoiceCallEndedRegistrants);
        pw.println(" mVoiceCallStartedRegistrants=" + this.mVoiceCallStartedRegistrants);
        pw.println(" mDroppedDuringPoll: size=" + this.mDroppedDuringPoll.size());
        for (int i2 = 0; i2 < this.mDroppedDuringPoll.size(); i2++) {
            pw.printf("  mDroppedDuringPoll[%d]=%s\n", Integer.valueOf(i2), this.mDroppedDuringPoll.get(i2));
        }
        pw.println(" mRingingCall=" + this.mRingingCall);
        pw.println(" mForegroundCall=" + this.mForegroundCall);
        pw.println(" mBackgroundCall=" + this.mBackgroundCall);
        pw.println(" mPendingMO=" + this.mPendingMO);
        pw.println(" mHangupPendingMO=" + this.mHangupPendingMO);
        pw.println(" mPhone=" + this.mPhone);
        pw.println(" mDesiredMute=" + this.mDesiredMute);
        pw.println(" mState=" + this.mState);
    }
}
