package com.android.internal.telephony.cdma;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.DriverCall;
import com.android.internal.telephony.Phone;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public final class CdmaCall extends Call {
    CdmaCallTracker mOwner;

    static Call.State stateFromDCState(DriverCall.State dcState) {
        switch (dcState) {
            case ACTIVE:
                return Call.State.ACTIVE;
            case HOLDING:
                return Call.State.HOLDING;
            case DIALING:
                return Call.State.DIALING;
            case ALERTING:
                return Call.State.ALERTING;
            case INCOMING:
                return Call.State.INCOMING;
            case WAITING:
                return Call.State.WAITING;
            default:
                throw new RuntimeException("illegal call state:" + dcState);
        }
    }

    CdmaCall(CdmaCallTracker owner) {
        this.mOwner = owner;
    }

    public void dispose() {
    }

    @Override // com.android.internal.telephony.Call
    public List<Connection> getConnections() {
        return this.mConnections;
    }

    @Override // com.android.internal.telephony.Call
    public Phone getPhone() {
        return this.mOwner.mPhone;
    }

    @Override // com.android.internal.telephony.Call
    public boolean isMultiparty() {
        return this.mConnections.size() > 1;
    }

    @Override // com.android.internal.telephony.Call
    public void hangup() throws CallStateException {
        this.mOwner.hangup(this);
    }

    public String toString() {
        return this.mState.toString();
    }

    void attach(Connection conn, DriverCall dc) {
        this.mConnections.add(conn);
        this.mState = stateFromDCState(dc.state);
    }

    void attachFake(Connection conn, Call.State state) {
        this.mConnections.add(conn);
        this.mState = state;
    }

    boolean connectionDisconnected(CdmaConnection conn) {
        if (this.mState != Call.State.DISCONNECTED) {
            boolean hasOnlyDisconnectedConnections = true;
            int s = this.mConnections.size();
            for (int i = 0; i < s; i++) {
                if (this.mConnections.get(i).getState() != Call.State.DISCONNECTED) {
                    hasOnlyDisconnectedConnections = false;
                    break;
                }
            }
            if (hasOnlyDisconnectedConnections) {
                this.mState = Call.State.DISCONNECTED;
                return true;
            }
        }
        return false;
    }

    void detach(CdmaConnection conn) {
        this.mConnections.remove(conn);
        if (this.mConnections.size() == 0) {
            this.mState = Call.State.IDLE;
        }
    }

    boolean update(CdmaConnection conn, DriverCall dc) {
        Call.State newState = stateFromDCState(dc.state);
        if (newState == this.mState) {
            return false;
        }
        this.mState = newState;
        return true;
    }

    boolean isFull() {
        return this.mConnections.size() == 1;
    }

    void onHangupLocal() {
        int s = this.mConnections.size();
        for (int i = 0; i < s; i++) {
            CdmaConnection cn = (CdmaConnection) this.mConnections.get(i);
            cn.onHangupLocal();
        }
        this.mState = Call.State.DISCONNECTING;
    }

    void clearDisconnected() {
        for (int i = this.mConnections.size() - 1; i >= 0; i--) {
            CdmaConnection cn = (CdmaConnection) this.mConnections.get(i);
            if (cn.getState() == Call.State.DISCONNECTED) {
                this.mConnections.remove(i);
            }
        }
        if (this.mConnections.size() == 0) {
            this.mState = Call.State.IDLE;
        }
    }
}
