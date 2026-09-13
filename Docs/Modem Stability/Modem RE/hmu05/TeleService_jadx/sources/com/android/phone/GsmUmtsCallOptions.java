package com.android.phone;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.util.Log;
import com.android.internal.telephony.Phone;

/* JADX INFO: loaded from: classes.dex */
public class GsmUmtsCallOptions extends PreferenceActivity {
    private Phone mPhone;
    private PreferenceScreen subscriptionPrefCFE;
    private final boolean DBG = true;
    private int mSubscription = 0;

    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onCreate(Bundle icicle) {
        Log.d("GsmUmtsCallOptions", "onCreate()");
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.gsm_umts_call_options);
        this.mSubscription = getIntent().getIntExtra("subscription", PhoneGlobals.getInstance().getDefaultSubscription());
        this.subscriptionPrefCFE = (PreferenceScreen) findPreference("button_cf_expand_key");
        this.subscriptionPrefCFE.getIntent().putExtra("subscription", this.mSubscription);
        PreferenceScreen subscriptionPrefAdditionSettings = (PreferenceScreen) findPreference("button_more_expand_key");
        subscriptionPrefAdditionSettings.getIntent().putExtra("subscription", this.mSubscription);
        Log.d("GsmUmtsCallOptions", "Getting GsmUmtsCallOptions subscription =" + this.mSubscription);
        PreferenceScreen subscriptionPrefCBSettings = (PreferenceScreen) findPreference("button_callbarring_expand_key");
        subscriptionPrefCBSettings.getIntent().putExtra("subscription", this.mSubscription);
        this.mPhone = PhoneGlobals.getInstance().getPhone(this.mSubscription);
        if (this.mPhone.getPhoneType() != 1) {
            Log.d("GsmUmtsCallOptions", "Non GSM Phone!");
            getPreferenceScreen().setEnabled(false);
        }
    }
}
