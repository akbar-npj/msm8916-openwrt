package com.android.phone;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.ListPreference;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import com.android.internal.telephony.Phone;

/* JADX INFO: loaded from: classes.dex */
public class CdmaSystemSelectListPreference extends ListPreference {
    private MyHandler mHandler;
    private Phone mPhone;

    /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
    public CdmaSystemSelectListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mHandler = new MyHandler();
        this.mPhone = PhoneGlobals.getPhone();
        this.mHandler = new MyHandler();
        this.mPhone.queryCdmaRoamingPreference(this.mHandler.obtainMessage(0));
    }

    public CdmaSystemSelectListPreference(Context context) {
        this(context, null);
    }

    @Override // android.preference.DialogPreference
    protected void showDialog(Bundle state) {
        if (!Boolean.parseBoolean(SystemProperties.get("ril.cdma.inecmmode"))) {
            super.showDialog(state);
        }
    }

    @Override // android.preference.ListPreference, android.preference.DialogPreference
    protected void onDialogClosed(boolean positiveResult) {
        int statusCdmaRoamingMode;
        super.onDialogClosed(positiveResult);
        if (positiveResult && getValue() != null) {
            int buttonCdmaRoamingMode = Integer.valueOf(getValue()).intValue();
            int settingsCdmaRoamingMode = Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), "roaming_settings", 0);
            if (buttonCdmaRoamingMode != settingsCdmaRoamingMode) {
                switch (buttonCdmaRoamingMode) {
                    case 2:
                        statusCdmaRoamingMode = 2;
                        break;
                    default:
                        statusCdmaRoamingMode = 0;
                        break;
                }
                Settings.Global.putInt(this.mPhone.getContext().getContentResolver(), "roaming_settings", buttonCdmaRoamingMode);
                this.mPhone.setCdmaRoamingPreference(statusCdmaRoamingMode, this.mHandler.obtainMessage(1));
                return;
            }
            return;
        }
        Log.d("CdmaRoamingListPreference", String.format("onDialogClosed: positiveResult=%b value=%s -- do nothing", Boolean.valueOf(positiveResult), getValue()));
    }

    private class MyHandler extends Handler {
        private MyHandler() {
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    handleQueryCdmaRoamingPreference(msg);
                    break;
                case 1:
                    handleSetCdmaRoamingPreference(msg);
                    break;
            }
        }

        private void handleQueryCdmaRoamingPreference(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            if (ar.exception == null) {
                int statusCdmaRoamingMode = ((int[]) ar.result)[0];
                int settingsRoamingMode = Settings.Global.getInt(CdmaSystemSelectListPreference.this.mPhone.getContext().getContentResolver(), "roaming_settings", 0);
                if (statusCdmaRoamingMode == 0 || statusCdmaRoamingMode == 2) {
                    if (statusCdmaRoamingMode != settingsRoamingMode) {
                        Settings.Global.putInt(CdmaSystemSelectListPreference.this.mPhone.getContext().getContentResolver(), "roaming_settings", statusCdmaRoamingMode);
                    }
                    CdmaSystemSelectListPreference.this.setValue(Integer.toString(statusCdmaRoamingMode));
                    return;
                }
                resetCdmaRoamingModeToDefault();
            }
        }

        private void handleSetCdmaRoamingPreference(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            if (ar.exception != null || CdmaSystemSelectListPreference.this.getValue() == null) {
                CdmaSystemSelectListPreference.this.mPhone.queryCdmaRoamingPreference(obtainMessage(0));
            } else {
                int cdmaRoamingMode = Integer.valueOf(CdmaSystemSelectListPreference.this.getValue()).intValue();
                Settings.Global.putInt(CdmaSystemSelectListPreference.this.mPhone.getContext().getContentResolver(), "roaming_settings", cdmaRoamingMode);
            }
        }

        private void resetCdmaRoamingModeToDefault() {
            CdmaSystemSelectListPreference.this.setValue(Integer.toString(0));
            Settings.Global.putInt(CdmaSystemSelectListPreference.this.mPhone.getContext().getContentResolver(), "roaming_settings", 0);
            CdmaSystemSelectListPreference.this.mPhone.setCdmaRoamingPreference(0, obtainMessage(1));
        }
    }
}
