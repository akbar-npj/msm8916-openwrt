package com.android.phone;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.util.Log;
import com.android.internal.telephony.Phone;

/* JADX INFO: loaded from: classes.dex */
public class CdmaCallOptions extends PreferenceActivity {
    private final boolean DBG = true;
    private CheckBoxPreference mButtonVoicePrivacy;

    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.cdma_call_privacy);
        int subscription = getIntent().getIntExtra("subscription", PhoneGlobals.getInstance().getDefaultSubscription());
        Log.d("CdmaCallOptions", "Getting CDMACallOptions subscription =" + subscription);
        Phone phone = PhoneGlobals.getInstance().getPhone(subscription);
        PhoneGlobals.initCallWaitingPref(this, subscription);
        this.mButtonVoicePrivacy = (CheckBoxPreference) findPreference("button_voice_privacy_key");
        if (phone.getPhoneType() != 2 || getResources().getBoolean(R.bool.config_voice_privacy_disable)) {
            getPreferenceScreen().setEnabled(false);
        }
    }

    @Override // android.preference.PreferenceActivity
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        return preference.getKey().equals("button_voice_privacy_key");
    }
}
