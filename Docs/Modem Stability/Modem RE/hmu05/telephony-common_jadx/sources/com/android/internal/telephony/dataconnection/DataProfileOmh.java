package com.android.internal.telephony.dataconnection;

import android.text.TextUtils;
import java.util.ArrayList;

/* JADX INFO: loaded from: classes.dex */
public class DataProfileOmh extends DataProfile {
    private static String PROFILE_TYPE = "DataProfileOmh";
    private int DATA_PROFILE_OMH_PRIORITY_HIGHEST;
    private int DATA_PROFILE_OMH_PRIORITY_LOWEST;
    private DataProfileTypeModem mDataProfileModem;
    private int mPriority;
    private int mProfileId;
    private int serviceTypeMasks;

    enum DataProfileTypeModem {
        PROFILE_TYPE_UNSPECIFIED(1, "default"),
        PROFILE_TYPE_MMS(2, "mms"),
        PROFILE_TYPE_LBS(32, "supl"),
        PROFILE_TYPE_TETHERED(64, "dun");

        int id;
        String serviceType;

        DataProfileTypeModem(int i, String serviceType) {
            this.id = i;
            this.serviceType = serviceType;
        }

        public int getid() {
            return this.id;
        }

        public String getDataServiceType() {
            return this.serviceType;
        }

        public static DataProfileTypeModem getDataProfileTypeModem(String serviceType) {
            if (TextUtils.equals(serviceType, "default")) {
                return PROFILE_TYPE_UNSPECIFIED;
            }
            if (TextUtils.equals(serviceType, "mms")) {
                return PROFILE_TYPE_MMS;
            }
            if (TextUtils.equals(serviceType, "supl")) {
                return PROFILE_TYPE_LBS;
            }
            if (TextUtils.equals(serviceType, "dun")) {
                return PROFILE_TYPE_TETHERED;
            }
            return PROFILE_TYPE_UNSPECIFIED;
        }
    }

    public DataProfileOmh(int profileId, int priority) {
        super(0, "", PROFILE_TYPE, null, null, 3, null, "IP", "IP", 0);
        this.DATA_PROFILE_OMH_PRIORITY_LOWEST = 255;
        this.DATA_PROFILE_OMH_PRIORITY_HIGHEST = 0;
        this.serviceTypeMasks = 0;
        this.mPriority = 0;
        this.mProfileId = 0;
        this.mProfileId = profileId;
        this.mPriority = priority;
        this.types = new String[0];
    }

    @Override // com.android.internal.telephony.dataconnection.DataProfile
    public boolean canHandleType(String serviceType) {
        return (this.serviceTypeMasks & DataProfileTypeModem.getDataProfileTypeModem(serviceType).getid()) != 0;
    }

    @Override // com.android.internal.telephony.dataconnection.DataProfile
    public DataProfile.DataProfileType getDataProfileType() {
        return DataProfile.DataProfileType.PROFILE_TYPE_OMH;
    }

    @Override // com.android.internal.telephony.dataconnection.DataProfile
    public String toShortString() {
        return "DataProfile OMH";
    }

    @Override // com.android.internal.telephony.dataconnection.DataProfile
    public String toHash() {
        return toString();
    }

    @Override // com.android.internal.telephony.dataconnection.DataProfile
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString()).append(this.mProfileId).append(", ").append(this.mPriority);
        sb.append("]");
        return sb.toString();
    }

    public void setDataProfileTypeModem(DataProfileTypeModem modemProfile) {
        this.mDataProfileModem = modemProfile;
    }

    public DataProfileTypeModem getDataProfileTypeModem() {
        return this.mDataProfileModem;
    }

    public void setPriority(int priority) {
        this.mPriority = priority;
    }

    public boolean isPriorityHigher(int priority) {
        return isValidPriority(priority) && this.mPriority < priority;
    }

    public boolean isPriorityLower(int priority) {
        return isValidPriority(priority) && this.mPriority > priority;
    }

    public boolean isValidPriority() {
        return isValidPriority(this.mPriority);
    }

    private boolean isValidPriority(int priority) {
        return priority >= this.DATA_PROFILE_OMH_PRIORITY_HIGHEST && priority <= this.DATA_PROFILE_OMH_PRIORITY_LOWEST;
    }

    @Override // com.android.internal.telephony.dataconnection.DataProfile
    public int getProfileId() {
        return this.mProfileId;
    }

    public int getPriority() {
        return this.mPriority;
    }

    public void addServiceType(DataProfileTypeModem modemProfile) {
        this.serviceTypeMasks |= modemProfile.getid();
        ArrayList<String> serviceTypes = new ArrayList<>();
        DataProfileTypeModem[] arr$ = DataProfileTypeModem.values();
        for (DataProfileTypeModem dpt : arr$) {
            if ((this.serviceTypeMasks & dpt.getid()) != 0) {
                serviceTypes.add(dpt.getDataServiceType());
            }
        }
        this.types = (String[]) serviceTypes.toArray(new String[0]);
    }
}
