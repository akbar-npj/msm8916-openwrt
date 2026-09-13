package com.android.internal.telephony.dataconnection;

/* JADX INFO: loaded from: classes.dex */
public class ApnSetting extends DataProfile {
    static final String V2_FORMAT_REGEX = "^\\[ApnSettingV2\\]\\s*";

    public ApnSetting(int id, String numeric, String carrier, String apn, String proxy, String port, String mmsc, String mmsProxy, String mmsPort, String user, String password, int authType, String[] types, String protocol, String roamingProtocol, boolean carrierEnabled, int bearer) {
        super(id, numeric, apn, user, password, authType, types, protocol, roamingProtocol, bearer, carrier, proxy, port, mmsc, mmsProxy, mmsPort, carrierEnabled);
    }

    public static ApnSetting fromString(String data) {
        int version;
        int authType;
        String[] typeArray;
        String protocol;
        String roamingProtocol;
        boolean carrierEnabled;
        int bearer;
        if (data == null) {
            return null;
        }
        if (data.matches("^\\[ApnSettingV2\\]\\s*.*")) {
            version = 2;
            data = data.replaceFirst(V2_FORMAT_REGEX, "");
        } else {
            version = 1;
        }
        String[] a = data.split("\\s*,\\s*");
        if (a.length < 14) {
            return null;
        }
        try {
            authType = Integer.parseInt(a[12]);
        } catch (Exception e) {
            authType = 0;
        }
        if (version == 1) {
            typeArray = new String[a.length - 13];
            System.arraycopy(a, 13, typeArray, 0, a.length - 13);
            protocol = "IP";
            roamingProtocol = "IP";
            carrierEnabled = true;
            bearer = 0;
        } else {
            if (a.length < 18) {
                return null;
            }
            typeArray = a[13].split("\\s*\\|\\s*");
            protocol = a[14];
            roamingProtocol = a[15];
            try {
                carrierEnabled = Boolean.parseBoolean(a[16]);
            } catch (Exception e2) {
                carrierEnabled = true;
            }
            bearer = Integer.parseInt(a[17]);
        }
        return new ApnSetting(-1, a[10] + a[11], a[0], a[1], a[2], a[3], a[7], a[8], a[9], a[4], a[5], authType, typeArray, protocol, roamingProtocol, carrierEnabled, bearer);
    }

    @Override // com.android.internal.telephony.dataconnection.DataProfile
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[ApnSettingV2] ").append(this.carrier).append(", ").append(this.id).append(", ").append(this.numeric).append(", ").append(this.apn).append(", ").append(this.proxy).append(", ").append(this.mmsc).append(", ").append(this.mmsProxy).append(", ").append(this.mmsPort).append(", ").append(this.port).append(", ").append(this.authType).append(", ");
        for (int i = 0; i < this.types.length; i++) {
            sb.append(this.types[i]);
            if (i < this.types.length - 1) {
                sb.append(" | ");
            }
        }
        sb.append(", ").append(this.protocol);
        sb.append(", ").append(this.roamingProtocol);
        sb.append(", ").append(this.carrierEnabled);
        sb.append(", ").append(this.bearer);
        return sb.toString();
    }

    @Override // com.android.internal.telephony.dataconnection.DataProfile
    public DataProfile.DataProfileType getDataProfileType() {
        return DataProfile.DataProfileType.PROFILE_TYPE_APN;
    }

    @Override // com.android.internal.telephony.dataconnection.DataProfile
    public int getProfileId() {
        return this.id;
    }

    @Override // com.android.internal.telephony.dataconnection.DataProfile
    public boolean canHandleType(String type) {
        String[] arr$ = this.types;
        for (String t : arr$) {
            if (t.equalsIgnoreCase(type) || t.equalsIgnoreCase("*") || (t.equalsIgnoreCase("default") && type.equalsIgnoreCase("hipri"))) {
                return true;
            }
        }
        return false;
    }

    public boolean equals(Object o) {
        if (o instanceof ApnSetting) {
            return toString().equals(o.toString());
        }
        return false;
    }

    @Override // com.android.internal.telephony.dataconnection.DataProfile
    public String toShortString() {
        return "ApnSetting";
    }

    @Override // com.android.internal.telephony.dataconnection.DataProfile
    public String toHash() {
        return toString();
    }
}
