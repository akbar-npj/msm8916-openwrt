package org.codeaurora.ims.csvt;

import android.os.Parcel;
import android.os.Parcelable;

/* JADX INFO: loaded from: classes.dex */
public class CallForwardInfoP implements Parcelable {
    public static final Parcelable.Creator<CallForwardInfoP> CREATOR = new Parcelable.Creator<CallForwardInfoP>() { // from class: org.codeaurora.ims.csvt.CallForwardInfoP.1
        @Override // android.os.Parcelable.Creator
        public CallForwardInfoP createFromParcel(Parcel in) {
            return new CallForwardInfoP(in);
        }

        @Override // android.os.Parcelable.Creator
        public CallForwardInfoP[] newArray(int size) {
            return new CallForwardInfoP[size];
        }
    };
    public String number;
    public int reason;
    public int serviceClass;
    public int status;
    public int timeSeconds;
    public int toa;

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    public CallForwardInfoP() {
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(this.status);
        out.writeInt(this.reason);
        out.writeInt(this.toa);
        out.writeString(this.number);
        out.writeInt(this.timeSeconds);
        out.writeInt(this.serviceClass);
    }

    public CallForwardInfoP(Parcel in) {
        this.status = in.readInt();
        this.reason = in.readInt();
        this.toa = in.readInt();
        this.number = in.readString();
        this.timeSeconds = in.readInt();
        this.serviceClass = in.readInt();
    }
}
