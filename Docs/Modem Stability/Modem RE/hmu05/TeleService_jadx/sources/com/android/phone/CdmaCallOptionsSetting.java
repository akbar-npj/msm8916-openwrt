package com.android.phone;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.MSimTelephonyManager;
import android.text.TextUtils;
import android.util.Log;

/* JADX INFO: loaded from: classes.dex */
public class CdmaCallOptionsSetting {
    private static final String[] NUM_PROJECTION = {"number", "state"};
    private String mActNumber;
    private int mCategory;
    private String mDeActNumber;
    private int mSubscription;
    private int mType;

    public CdmaCallOptionsSetting(Context context, int type, int category, int subscription) {
        this.mActNumber = "";
        this.mDeActNumber = "";
        this.mType = type;
        this.mCategory = category;
        this.mSubscription = subscription;
        StringBuilder selection = new StringBuilder();
        selection.append("numeric = " + getOperatorNumeric());
        if (TextUtils.isEmpty(getOperatorNumeric())) {
            Log.e("CdmaCallOptionsSetting", "numeric is not found!");
            return;
        }
        if (this.mCategory != -1) {
            selection.append(" and category = " + this.mCategory);
        }
        Cursor cursor = SqliteWrapper.query(context, context.getContentResolver(), Uri.withAppendedPath(Telephony.CdmaCallOptions.CONTENT_URI, getCallOptionType(type)), NUM_PROJECTION, selection.toString(), (String[]) null, (String) null);
        if (cursor == null) {
            Log.e("CdmaCallOptionsSetting", "call option is not found in Database!");
            return;
        }
        while (cursor.moveToNext()) {
            try {
                int state = Integer.valueOf(cursor.getString(1)).intValue();
                if (state == 1) {
                    this.mActNumber = cursor.getString(0);
                    Log.d("CdmaCallOptionsSetting", "act number for type " + this.mType + " is " + this.mActNumber);
                } else {
                    this.mDeActNumber = cursor.getString(0);
                    Log.d("CdmaCallOptionsSetting", "deact number for type " + this.mType + " is " + this.mDeActNumber);
                }
            } catch (Throwable th) {
                cursor.close();
                throw th;
            }
        }
        cursor.close();
    }

    public CdmaCallOptionsSetting(Context context, int type, int subscription) {
        this(context, type, -1, subscription);
    }

    public String getActivateNumber() {
        return this.mActNumber;
    }

    public String getDeactivateNumber() {
        return this.mDeActNumber;
    }

    private String getOperatorNumeric() {
        String numeric = MSimTelephonyManager.getDefault().getSimOperator(this.mSubscription);
        Log.d("CdmaCallOptionsSetting", "numeric is " + numeric + " sub " + this.mSubscription);
        return numeric;
    }

    private String getCallOptionType(int type) {
        switch (type) {
            case 0:
                return "cfu";
            case 1:
                return "cfb";
            case 2:
                return "cfnry";
            case 3:
                return "cfnrc";
            case 4:
                return "cfda";
            case 5:
            case 6:
            default:
                return "cfu";
            case 7:
                return "cw";
        }
    }
}
