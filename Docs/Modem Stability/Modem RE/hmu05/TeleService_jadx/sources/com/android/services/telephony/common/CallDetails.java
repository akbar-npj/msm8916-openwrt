package com.android.services.telephony.common;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public class CallDetails implements Parcelable {
    private int mCallDomain;
    private int mCallType;
    private Map<String, String[]> mConfDetails;
    private String[] mConfParList;
    private String mErrorInfo;
    private String[] mExtras;
    private boolean mIsMpty;
    private static String LOG_TAG = "CallDetails";
    public static final Parcelable.Creator<CallDetails> CREATOR = new Parcelable.Creator<CallDetails>() { // from class: com.android.services.telephony.common.CallDetails.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public CallDetails createFromParcel(Parcel in) {
            return new CallDetails(in);
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public CallDetails[] newArray(int size) {
            return new CallDetails[size];
        }
    };

    public CallDetails() {
        this.mCallType = 10;
        this.mCallDomain = 4;
        this.mExtras = null;
        this.mConfParList = new String[0];
        this.mErrorInfo = "";
        this.mIsMpty = false;
    }

    public CallDetails(Parcel in) {
        readFromParcel(in);
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    public int getCallDomain() {
        return this.mCallDomain;
    }

    public void setCallDomain(int callDomain) {
        this.mCallDomain = callDomain;
    }

    public int getCallType() {
        return this.mCallType;
    }

    public void setCallType(int callType) {
        this.mCallType = callType;
    }

    public boolean isMpty() {
        return this.mIsMpty;
    }

    public void setMpty(boolean mpty) {
        this.mIsMpty = mpty;
    }

    public String getErrorInfo() {
        return this.mErrorInfo;
    }

    public void setErrorInfo(String errorInfo) {
        this.mErrorInfo = errorInfo;
    }

    public void setExtras(String[] extraparams) {
        if (extraparams != null) {
            int listLength = extraparams.length;
            this.mExtras = new String[listLength];
            for (int i = 0; i < listLength; i++) {
                this.mExtras[i] = extraparams[i];
            }
            return;
        }
        Log.e(LOG_TAG, "list is null in setConfUriList");
    }

    public void setConfUriList(String[] list) {
        if (list != null) {
            int listLength = list.length;
            this.mConfParList = new String[listLength];
            for (int i = 0; i < listLength; i++) {
                this.mConfParList[i] = list[i];
            }
            return;
        }
        Log.e(LOG_TAG, "list is null in setConfUriList");
    }

    public void setConfDetailsFromMap(Map<String, List<String>> confMap) {
        if (this.mConfDetails != null) {
            this.mConfDetails.clear();
        } else {
            this.mConfDetails = new HashMap();
        }
        for (Map.Entry<String, List<String>> userEntry : confMap.entrySet()) {
            String uri = userEntry.getKey();
            List<String> list = userEntry.getValue();
            String[] userDetail = null;
            if (list != null) {
                userDetail = new String[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    userDetail[i] = list.get(i);
                    Log.d(LOG_TAG, "uri=" + uri + ";Detail=" + userDetail[i]);
                }
            } else {
                Log.e(LOG_TAG, "no userdetail for uri: " + uri);
            }
            this.mConfDetails.put(uri, userDetail);
        }
    }

    public String[] getExtras() {
        return this.mExtras;
    }

    public String[] getConfParticipantList() {
        return this.mConfParList;
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel dest, int flag) {
        dest.writeInt(this.mCallType);
        dest.writeInt(this.mCallDomain);
        dest.writeStringArray(this.mExtras);
        dest.writeString(this.mErrorInfo);
        dest.writeStringArray(this.mConfParList);
        dest.writeByte((byte) (this.mIsMpty ? 1 : 0));
        int confDetailLen = this.mConfDetails == null ? 0 : this.mConfDetails.size();
        dest.writeInt(confDetailLen);
        if (confDetailLen > 0) {
            for (Map.Entry<String, String[]> userEntry : this.mConfDetails.entrySet()) {
                dest.writeString(userEntry.getKey());
                dest.writeStringArray(userEntry.getValue());
            }
        }
    }

    public void readFromParcel(Parcel in) {
        this.mCallType = in.readInt();
        this.mCallDomain = in.readInt();
        this.mExtras = in.readStringArray();
        this.mErrorInfo = in.readString();
        this.mConfParList = in.readStringArray();
        this.mIsMpty = in.readByte() != 0;
        int confDetailLen = in.readInt();
        this.mConfDetails = new HashMap();
        for (int i = 0; i < confDetailLen; i++) {
            String userUri = in.readString();
            String[] userDetails = in.readStringArray();
            this.mConfDetails.put(userUri, userDetails);
        }
    }

    public String toString() {
        String extrasResult = "";
        String uri = "";
        if (this.mExtras != null) {
            String[] arr$ = this.mExtras;
            for (String s : arr$) {
                extrasResult = extrasResult + s;
            }
        }
        if (this.mConfParList != null) {
            String[] arr$2 = this.mConfParList;
            for (String s2 : arr$2) {
                uri = uri + s2;
            }
        }
        return " calltype" + this.mCallType + " domain" + this.mCallDomain + " erroinfo" + this.mErrorInfo + " mConfParList" + uri + " multiparty" + this.mIsMpty + " " + extrasResult + "mConfDetails:" + this.mConfDetails;
    }
}
