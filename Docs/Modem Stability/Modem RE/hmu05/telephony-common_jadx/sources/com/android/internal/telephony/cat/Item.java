package com.android.internal.telephony.cat;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

/* JADX INFO: loaded from: classes.dex */
public class Item implements Parcelable {
    public static final Parcelable.Creator<Item> CREATOR = new Parcelable.Creator<Item>() { // from class: com.android.internal.telephony.cat.Item.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public Item createFromParcel(Parcel in) {
            return new Item(in);
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public Item[] newArray(int size) {
            return new Item[size];
        }
    };
    public Bitmap icon;
    public int id;
    public String text;

    public Item(int id, String text) {
        this.id = id;
        this.text = text;
        this.icon = null;
    }

    public Item(Parcel in) {
        this.id = in.readInt();
        this.text = in.readString();
        this.icon = (Bitmap) in.readParcelable(null);
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.id);
        dest.writeString(this.text);
        dest.writeParcelable(this.icon, flags);
    }

    public String toString() {
        return this.text;
    }
}
