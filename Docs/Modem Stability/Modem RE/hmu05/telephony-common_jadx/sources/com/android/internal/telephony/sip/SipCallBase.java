package com.android.internal.telephony.sip;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.Connection;
import java.util.Iterator;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
abstract class SipCallBase extends Call {
    protected abstract void setState(Call.State state);

    SipCallBase() {
    }

    @Override // com.android.internal.telephony.Call
    public List<Connection> getConnections() {
        return this.mConnections;
    }

    @Override // com.android.internal.telephony.Call
    public boolean isMultiparty() {
        return this.mConnections.size() > 1;
    }

    public String toString() {
        return this.mState.toString() + ":" + super.toString();
    }

    void clearDisconnected() {
        Iterator<Connection> it = this.mConnections.iterator();
        while (it.hasNext()) {
            Connection c = it.next();
            if (c.getState() == Call.State.DISCONNECTED) {
                it.remove();
            }
        }
        if (this.mConnections.isEmpty()) {
            setState(Call.State.IDLE);
        }
    }
}
