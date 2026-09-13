package com.android.phone;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.ListPreference;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

/* JADX INFO: loaded from: classes.dex */
public class CdmaSubscriptionListPreference extends ListPreference {
    private CdmaSubscriptionButtonHandler mHandler;
    private Phone mPhone;

    public CdmaSubscriptionListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mPhone = PhoneFactory.getDefaultPhone();
        this.mHandler = new CdmaSubscriptionButtonHandler();
        setCurrentCdmaSubscriptionModeValue();
        if (context.getResources().getBoolean(R.bool.disable_cdma_subscription)) {
            setCurrentCdmaSubscriptionSummary(context);
        }
    }

    private void setCurrentCdmaSubscriptionModeValue() {
        int cdmaSubscriptionMode = Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), "subscription_mode", 1);
        setValue(Integer.toString(cdmaSubscriptionMode));
    }

    public CdmaSubscriptionListPreference(Context context) {
        this(context, null);
    }

    public void setCurrentCdmaSubscriptionSummary(Context context) {
        int cdmaSubscriptionMode = Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), "subscription_mode", 1);
        String[] summary = context.getResources().getStringArray(R.array.cdma_subscription_choices);
        setSummary(summary[cdmaSubscriptionMode]);
    }

    @Override // android.preference.DialogPreference
    protected void showDialog(Bundle state) {
        setCurrentCdmaSubscriptionModeValue();
        super.showDialog(state);
    }

    @Override // android.preference.ListPreference, android.preference.DialogPreference
    protected void onDialogClosed(boolean positiveResult) {
        int statusCdmaSubscriptionMode;
        super.onDialogClosed(positiveResult);
        if (positiveResult) {
            int buttonCdmaSubscriptionMode = Integer.valueOf(getValue()).intValue();
            Log.d("CdmaSubscriptionListPreference", "Setting new value " + buttonCdmaSubscriptionMode);
            switch (buttonCdmaSubscriptionMode) {
                case 0:
                    statusCdmaSubscriptionMode = 0;
                    break;
                case 1:
                    statusCdmaSubscriptionMode = 1;
                    break;
                default:
                    statusCdmaSubscriptionMode = 0;
                    break;
            }
            this.mPhone.setCdmaSubscription(statusCdmaSubscriptionMode, this.mHandler.obtainMessage(0, getValue()));
        }
    }

    private class CdmaSubscriptionButtonHandler extends Handler {
        private CdmaSubscriptionButtonHandler() {
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    handleSetCdmaSubscriptionMode(msg);
                    break;
            }
        }

        private void handleSetCdmaSubscriptionMode(Message msg) {
            CdmaSubscriptionListPreference.this.mPhone = PhoneFactory.getDefaultPhone();
            AsyncResult ar = (AsyncResult) msg.obj;
            if (ar.exception == null) {
                int cdmaSubscriptionMode = Integer.valueOf((String) ar.userObj).intValue();
                Settings.Global.putInt(CdmaSubscriptionListPreference.this.mPhone.getContext().getContentResolver(), "subscription_mode", cdmaSubscriptionMode);
            } else {
                Log.e("CdmaSubscriptionListPreference", "Setting Cdma subscription source failed");
            }
        }
    }
}
