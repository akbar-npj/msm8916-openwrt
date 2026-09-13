package com.android.internal.telephony;

import android.telephony.Rlog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/* JADX INFO: loaded from: classes.dex */
public abstract class Call {
    private ConferenceStateInfo mConfStateInfo;
    private String[] mConfUriList;
    protected final String LOG_TAG = "Call";
    public boolean isMpty = false;
    public State mState = State.IDLE;
    public ArrayList<Connection> mConnections = new ArrayList<>();
    protected boolean mIsGeneric = false;

    public abstract List<Connection> getConnections();

    public abstract Phone getPhone();

    public abstract void hangup() throws CallStateException;

    public abstract boolean isMultiparty();

    public enum State {
        IDLE,
        ACTIVE,
        HOLDING,
        DIALING,
        ALERTING,
        INCOMING,
        WAITING,
        DISCONNECTED,
        DISCONNECTING;

        public boolean isAlive() {
            return (this == IDLE || this == DISCONNECTED || this == DISCONNECTING) ? false : true;
        }

        public boolean isRinging() {
            return this == INCOMING || this == WAITING;
        }

        public boolean isDialing() {
            return this == DIALING || this == ALERTING;
        }
    }

    public static class ConfUser {
        public String displayText;
        public String status;
        public String uri;

        public String toString() {
            return "Uri: " + this.uri + " dispText: " + this.displayText + " status: " + this.status;
        }
    }

    public static class ConferenceStateInfo {
        public HashMap<String, ConfUser> usersMap;
        public int version;

        public String[] getUserUriList() {
            Set<String> setKeySet;
            if (this.usersMap == null || (setKeySet = this.usersMap.keySet()) == null) {
                return null;
            }
            String[] uriList = (String[]) setKeySet.toArray(new String[0]);
            return uriList;
        }
    }

    public boolean hasConnection(Connection c) {
        return c.getCall() == this;
    }

    public boolean hasConnections() {
        List<Connection> connections = getConnections();
        return connections != null && connections.size() > 0;
    }

    public State getState() {
        return this.mState;
    }

    public boolean isIdle() {
        return !getState().isAlive();
    }

    public Connection getEarliestConnection() {
        long time = Long.MAX_VALUE;
        Connection earliest = null;
        List<Connection> l = getConnections();
        if (l.size() == 0) {
            return null;
        }
        int s = l.size();
        for (int i = 0; i < s; i++) {
            Connection c = l.get(i);
            long t = c.getCreateTime();
            if (t < time) {
                earliest = c;
                time = t;
            }
        }
        return earliest;
    }

    public String[] getConfUriList() {
        return this.mConfUriList;
    }

    public void setConfUriList(String[] urilist) {
        this.mConfUriList = urilist;
    }

    public ConferenceStateInfo getConfStateInfo() {
        return this.mConfStateInfo;
    }

    public void setConfStateInfo(ConferenceStateInfo confStateInfo) {
        this.mConfStateInfo = confStateInfo;
    }

    public long getEarliestCreateTime() {
        long time = Long.MAX_VALUE;
        List<Connection> l = getConnections();
        if (l.size() == 0) {
            return 0L;
        }
        int s = l.size();
        for (int i = 0; i < s; i++) {
            Connection c = l.get(i);
            long t = c.getCreateTime();
            if (t < time) {
                time = t;
            }
        }
        return time;
    }

    public long getEarliestConnectTime() {
        long time = Long.MAX_VALUE;
        List<Connection> l = getConnections();
        if (l.size() == 0) {
            return 0L;
        }
        int s = l.size();
        for (int i = 0; i < s; i++) {
            Connection c = l.get(i);
            long t = c.getConnectTime();
            if (t < time) {
                time = t;
            }
        }
        return time;
    }

    public boolean isDialingOrAlerting() {
        return getState().isDialing();
    }

    public boolean isRinging() {
        return getState().isRinging();
    }

    public Connection getLatestConnection() {
        List<Connection> l = getConnections();
        if (l.size() == 0) {
            return null;
        }
        long time = 0;
        Connection latest = null;
        int s = l.size();
        for (int i = 0; i < s; i++) {
            Connection c = l.get(i);
            long t = c.getCreateTime();
            if (t > time) {
                latest = c;
                time = t;
            }
        }
        return latest;
    }

    public boolean isGeneric() {
        return this.mIsGeneric;
    }

    public void setGeneric(boolean generic) {
        this.mIsGeneric = generic;
    }

    public void hangupIfAlive() {
        if (getState().isAlive()) {
            try {
                hangup();
            } catch (CallStateException ex) {
                Rlog.w("Call", " hangupIfActive: caught " + ex);
            }
        }
    }
}
