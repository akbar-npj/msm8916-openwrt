package com.android.services.telephony.common;

import android.os.Parcel;
import android.os.Parcelable;
import com.google.common.base.Objects;

/* JADX INFO: loaded from: classes.dex */
public final class CallIdentification implements Parcelable {
    public static final Parcelable.Creator<CallIdentification> CREATOR = new Parcelable.Creator<CallIdentification>() { // from class: com.android.services.telephony.common.CallIdentification.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public CallIdentification createFromParcel(Parcel in) {
            return new CallIdentification(in);
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public CallIdentification[] newArray(int size) {
            return new CallIdentification[size];
        }
    };
    private int mCallId;
    private String mCnapName;
    private int mCnapNamePresentation;
    private String mNumber;
    private int mNumberPresentation;

    public CallIdentification(int callId) {
        this.mNumber = "";
        this.mNumberPresentation = Call.PRESENTATION_ALLOWED;
        this.mCnapNamePresentation = Call.PRESENTATION_ALLOWED;
        this.mCnapName = "";
        this.mCallId = callId;
    }

    public CallIdentification(CallIdentification identification) {
        this.mNumber = "";
        this.mNumberPresentation = Call.PRESENTATION_ALLOWED;
        this.mCnapNamePresentation = Call.PRESENTATION_ALLOWED;
        this.mCnapName = "";
        this.mCallId = identification.mCallId;
        this.mNumber = identification.mNumber;
        this.mNumberPresentation = identification.mNumberPresentation;
        this.mCnapNamePresentation = identification.mCnapNamePresentation;
        this.mCnapName = identification.mCnapName;
    }

    public String getNumber() {
        return this.mNumber;
    }

    public void setNumber(String number) {
        this.mNumber = number;
    }

    public int getNumberPresentation() {
        return this.mNumberPresentation;
    }

    public void setNumberPresentation(int presentation) {
        this.mNumberPresentation = presentation;
    }

    public int getCnapNamePresentation() {
        return this.mCnapNamePresentation;
    }

    public void setCnapNamePresentation(int presentation) {
        this.mCnapNamePresentation = presentation;
    }

    public String getCnapName() {
        return this.mCnapName;
    }

    public void setCnapName(String cnapName) {
        this.mCnapName = cnapName;
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.mCallId);
        dest.writeString(this.mNumber);
        dest.writeInt(this.mNumberPresentation);
        dest.writeInt(this.mCnapNamePresentation);
        dest.writeString(this.mCnapName);
    }

    private CallIdentification(Parcel in) {
        this.mNumber = "";
        this.mNumberPresentation = Call.PRESENTATION_ALLOWED;
        this.mCnapNamePresentation = Call.PRESENTATION_ALLOWED;
        this.mCnapName = "";
        this.mCallId = in.readInt();
        this.mNumber = in.readString();
        this.mNumberPresentation = in.readInt();
        this.mCnapNamePresentation = in.readInt();
        this.mCnapName = in.readString();
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    public String toString() {
        return Objects.toStringHelper(this).add("mCallId", this.mCallId).add("mNumber", MoreStrings.toSafeString(this.mNumber)).add("mNumberPresentation", this.mNumberPresentation).add("mCnapName", MoreStrings.toSafeString(this.mCnapName)).add("mCnapNamePresentation", this.mCnapNamePresentation).toString();
    }
}
