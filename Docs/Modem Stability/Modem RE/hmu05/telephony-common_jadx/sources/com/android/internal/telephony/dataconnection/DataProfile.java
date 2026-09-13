package com.android.internal.telephony.dataconnection;

/* JADX INFO: loaded from: classes.dex */
public abstract class DataProfile {
    protected static final String LOG_TAG = "DataProfile";
    public final String apn;
    public final int authType;
    public final int bearer;
    public String carrier;
    public boolean carrierEnabled;
    public final int id;
    private DataConnection mDc;
    public String mmsPort;
    public String mmsProxy;
    public String mmsc;
    public final String numeric;
    public final String password;
    public String port;
    public final String protocol;
    public String proxy;
    public final String roamingProtocol;
    public String[] types;
    public final String user;

    public abstract boolean canHandleType(String str);

    public abstract DataProfileType getDataProfileType();

    public abstract int getProfileId();

    public abstract String toHash();

    public abstract String toShortString();

    public enum DataProfileType {
        PROFILE_TYPE_APN(0),
        PROFILE_TYPE_CDMA(1),
        PROFILE_TYPE_OMH(2);

        int id;

        DataProfileType(int i) {
            this.id = i;
        }

        public int getid() {
            return this.id;
        }
    }

    public DataProfile(int id, String numeric, String apn, String user, String password, int authType, String[] types, String protocol, String roamingProtocol, int bearer) {
        this(id, numeric, apn, user, password, authType, types, protocol, roamingProtocol, bearer, "", "", "", "", "", "", false);
    }

    public DataProfile(int id, String numeric, String apn, String user, String password, int authType, String[] types, String protocol, String roamingProtocol, int bearer, String carrier, String proxy, String port, String mmsc, String mmsProxy, String mmsPort, boolean carrierEnabled) {
        this.mDc = null;
        this.id = id;
        this.numeric = numeric;
        this.apn = apn;
        this.types = types;
        this.user = user;
        this.password = password;
        this.authType = authType;
        this.protocol = protocol;
        this.roamingProtocol = roamingProtocol;
        this.bearer = bearer;
        this.carrier = carrier;
        this.proxy = proxy;
        this.port = port;
        this.mmsc = mmsc;
        this.mmsProxy = mmsProxy;
        this.mmsPort = mmsPort;
        this.carrierEnabled = carrierEnabled;
    }

    boolean isActive() {
        return this.mDc != null;
    }

    void setAsActive(DataConnection dc) {
        this.mDc = dc;
    }

    void setAsInactive() {
        this.mDc = null;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[DataProfile] ").append(", ").append(this.id).append(", ").append(this.numeric).append(", ").append(this.apn).append(", ").append(this.authType).append(", ");
        for (int i = 0; i < this.types.length; i++) {
            sb.append(this.types[i]);
            if (i < this.types.length - 1) {
                sb.append(" | ");
            }
        }
        sb.append(", ").append(this.protocol);
        sb.append(", ").append(this.roamingProtocol);
        sb.append(", ").append(this.bearer);
        return sb.toString();
    }
}
