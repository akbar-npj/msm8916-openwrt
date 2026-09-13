package com.android.phone;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import com.android.internal.telephony.Phone;

/* JADX INFO: loaded from: classes.dex */
public class CdmaVoicePrivacyCheckBoxPreference extends CheckBoxPreference {
    private final boolean DBG;
    private MyHandler mHandler;
    Phone phone;

    public CdmaVoicePrivacyCheckBoxPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.DBG = true;
        this.mHandler = new MyHandler();
        this.phone = PhoneGlobals.getPhone();
        this.phone.getEnhancedVoicePrivacy(this.mHandler.obtainMessage(0));
    }

    public CdmaVoicePrivacyCheckBoxPreference(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.checkBoxPreferenceStyle);
    }

    public CdmaVoicePrivacyCheckBoxPreference(Context context) {
        this(context, null);
    }

    @Override // android.preference.TwoStatePreference, android.preference.Preference
    protected void onClick() {
        super.onClick();
        this.phone.enableEnhancedVoicePrivacy(isChecked(), this.mHandler.obtainMessage(1));
    }

    private class MyHandler extends Handler {
        private MyHandler() {
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    handleGetVPResponse(msg);
                    break;
                case 1:
                    handleSetVPResponse(msg);
                    break;
            }
        }

        private void handleGetVPResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            if (ar.exception != null) {
                Log.d("CdmaVoicePrivacyCheckBoxPreference", "handleGetVPResponse: ar.exception=" + ar.exception);
                CdmaVoicePrivacyCheckBoxPreference.this.setEnabled(false);
            } else {
                Log.d("CdmaVoicePrivacyCheckBoxPreference", "handleGetVPResponse: VP state successfully queried.");
                int enable = ((int[]) ar.result)[0];
                CdmaVoicePrivacyCheckBoxPreference.this.setChecked(enable != 0);
                Settings.Secure.putInt(CdmaVoicePrivacyCheckBoxPreference.this.getContext().getContentResolver(), "enhanced_voice_privacy_enabled", enable);
            }
        }

        private void handleSetVPResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            if (ar.exception != null) {
                Log.d("CdmaVoicePrivacyCheckBoxPreference", "handleSetVPResponse: ar.exception=" + ar.exception);
            }
            Log.d("CdmaVoicePrivacyCheckBoxPreference", "handleSetVPResponse: re get");
            CdmaVoicePrivacyCheckBoxPreference.this.phone.getEnhancedVoicePrivacy(obtainMessage(0));
        }
    }
}
