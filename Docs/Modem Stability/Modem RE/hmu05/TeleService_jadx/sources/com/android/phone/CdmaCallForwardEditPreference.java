package com.android.phone;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Toast;

/* JADX INFO: loaded from: classes.dex */
public class CdmaCallForwardEditPreference extends EditPhoneNumberPreference {
    private int mButtonClicked;
    private Context mContext;
    private Activity mForeground;
    private String mPrefixNumber;
    private int mSubscription;
    int reason;

    public CdmaCallForwardEditPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mContext = context;
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CallForwardEditPreference, 0, R.style.EditPhoneNumberPreference);
        this.reason = a.getInt(1, 0);
        a.recycle();
    }

    public CdmaCallForwardEditPreference(Context context) {
        this(context, null);
    }

    public void init(Activity foreground, int subscription, String prefixNum) {
        this.mForeground = foreground;
        this.mSubscription = subscription;
        this.mPrefixNumber = prefixNum;
        setSummary(prefixNum);
    }

    @Override // com.android.phone.EditPhoneNumberPreference, android.preference.DialogPreference, android.content.DialogInterface.OnClickListener
    public void onClick(DialogInterface dialog, int which) {
        this.mButtonClicked = which;
        Log.d("CdmaCallForwardEditPreference", "mButtonClicked= " + this.mButtonClicked);
    }

    @Override // com.android.phone.EditPhoneNumberPreference, android.preference.EditTextPreference, android.preference.DialogPreference
    protected void onDialogClosed(boolean positiveResult) {
        Log.d("CdmaCallForwardEditPreference", "mButtonClicked=" + this.mButtonClicked + ", positiveResult=" + positiveResult);
        if (this.mButtonClicked == -3) {
            String number = getEditText().getText().toString();
            if (number.trim().length() == 0) {
                Toast.makeText(this.mContext, R.string.null_phone_number, 1).show();
                return;
            }
            Intent intent = new Intent("android.intent.action.CALL_PRIVILEGED");
            intent.setData(Uri.fromParts("tel", this.mPrefixNumber + number, null));
            intent.putExtra("Cdma_Supp", true);
            intent.putExtra("subscription", this.mSubscription);
            this.mForeground.startActivityForResult(intent, 200);
            return;
        }
        super.onDialogClosed(positiveResult);
    }
}
