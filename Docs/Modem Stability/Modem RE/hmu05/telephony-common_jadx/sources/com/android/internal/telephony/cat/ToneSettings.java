package com.android.internal.telephony.cat;

import android.os.Parcel;
import android.os.Parcelable;

/* JADX INFO: loaded from: classes.dex */
public class ToneSettings implements Parcelable {
    public static final Parcelable.Creator<ToneSettings> CREATOR = new Parcelable.Creator<ToneSettings>() { // from class: com.android.internal.telephony.cat.ToneSettings.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public ToneSettings createFromParcel(Parcel in) {
            return new ToneSettings(in);
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public ToneSettings[] newArray(int size) {
            return new ToneSettings[size];
        }
    };
    public Duration duration;
    public Tone tone;
    public boolean vibrate;

    public ToneSettings(Duration duration, Tone tone, boolean vibrate) {
        this.duration = duration;
        this.tone = tone;
        this.vibrate = vibrate;
    }

    private ToneSettings(Parcel in) {
        this.duration = (Duration) in.readParcelable(null);
        this.tone = (Tone) in.readParcelable(null);
        this.vibrate = in.readInt() == 1;
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(this.duration, 0);
        dest.writeParcelable(this.tone, 0);
        dest.writeInt(this.vibrate ? 1 : 0);
    }
}
