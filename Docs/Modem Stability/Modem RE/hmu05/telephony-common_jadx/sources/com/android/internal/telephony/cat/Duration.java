package com.android.internal.telephony.cat;

import android.os.Parcel;
import android.os.Parcelable;

/* JADX INFO: loaded from: classes.dex */
public class Duration implements Parcelable {
    public static final Parcelable.Creator<Duration> CREATOR = new Parcelable.Creator<Duration>() { // from class: com.android.internal.telephony.cat.Duration.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public Duration createFromParcel(Parcel in) {
            return new Duration(in);
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public Duration[] newArray(int size) {
            return new Duration[size];
        }
    };
    public int timeInterval;
    public TimeUnit timeUnit;

    public enum TimeUnit {
        MINUTE(0),
        SECOND(1),
        TENTH_SECOND(2);

        private int mValue;

        TimeUnit(int value) {
            this.mValue = value;
        }

        public int value() {
            return this.mValue;
        }
    }

    public Duration(int timeInterval, TimeUnit timeUnit) {
        this.timeInterval = timeInterval;
        this.timeUnit = timeUnit;
    }

    private Duration(Parcel in) {
        this.timeInterval = in.readInt();
        this.timeUnit = TimeUnit.values()[in.readInt()];
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.timeInterval);
        dest.writeInt(this.timeUnit.ordinal());
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }
}
