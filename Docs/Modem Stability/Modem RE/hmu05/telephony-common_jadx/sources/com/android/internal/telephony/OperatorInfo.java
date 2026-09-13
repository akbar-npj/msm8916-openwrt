package com.android.internal.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Telephony;

/* JADX INFO: loaded from: classes.dex */
public class OperatorInfo implements Parcelable {
    public static final Parcelable.Creator<OperatorInfo> CREATOR = new Parcelable.Creator<OperatorInfo>() { // from class: com.android.internal.telephony.OperatorInfo.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public OperatorInfo createFromParcel(Parcel in) {
            OperatorInfo opInfo = new OperatorInfo(in.readString(), in.readString(), in.readString(), (State) in.readSerializable());
            return opInfo;
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public OperatorInfo[] newArray(int size) {
            return new OperatorInfo[size];
        }
    };
    private String mOperatorAlphaLong;
    private String mOperatorAlphaShort;
    private String mOperatorNumeric;
    private String mRadioTech;
    private State mState;

    public enum State {
        UNKNOWN,
        AVAILABLE,
        CURRENT,
        FORBIDDEN
    }

    public String getOperatorAlphaLong() {
        return this.mOperatorAlphaLong;
    }

    public String getOperatorAlphaShort() {
        return this.mOperatorAlphaShort;
    }

    public String getOperatorNumeric() {
        return this.mOperatorNumeric;
    }

    public State getState() {
        return this.mState;
    }

    public String getRadioTech() {
        return this.mRadioTech;
    }

    OperatorInfo(String operatorAlphaLong, String operatorAlphaShort, String operatorNumeric, State state) {
        this.mState = State.UNKNOWN;
        this.mRadioTech = "";
        this.mOperatorAlphaLong = operatorAlphaLong;
        this.mOperatorAlphaShort = operatorAlphaShort;
        this.mOperatorNumeric = operatorNumeric;
        this.mRadioTech = "";
        if (operatorNumeric != null) {
            String[] values = operatorNumeric.split("\\+");
            this.mOperatorNumeric = values[0];
            if (values.length > 1) {
                this.mRadioTech = values[1];
            }
        }
        this.mState = state;
    }

    public OperatorInfo(String operatorAlphaLong, String operatorAlphaShort, String operatorNumeric, String stateString) {
        this(operatorAlphaLong, operatorAlphaShort, operatorNumeric, rilStateToState(stateString));
    }

    private static State rilStateToState(String s) {
        if (s.equals("unknown")) {
            return State.UNKNOWN;
        }
        if (s.equals("available")) {
            return State.AVAILABLE;
        }
        if (s.equals(Telephony.Carriers.CURRENT)) {
            return State.CURRENT;
        }
        if (s.equals("forbidden")) {
            return State.FORBIDDEN;
        }
        throw new RuntimeException("RIL impl error: Invalid network state '" + s + "'");
    }

    public String toString() {
        return "OperatorInfo " + this.mOperatorAlphaLong + "/" + this.mOperatorAlphaShort + "/" + this.mOperatorNumeric + "/" + this.mState + "/" + this.mRadioTech;
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.mOperatorAlphaLong);
        dest.writeString(this.mOperatorAlphaShort);
        dest.writeString(this.mOperatorNumeric + "+" + this.mRadioTech);
        dest.writeSerializable(this.mState);
    }
}
