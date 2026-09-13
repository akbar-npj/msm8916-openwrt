package com.android.phone;

import android.app.ActionBar;
import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.telephony.MSimTelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

/* JADX INFO: loaded from: classes.dex */
public class RespondViaSmsManager {
    private static final boolean DBG;

    static {
        DBG = SystemProperties.getInt("ro.debuggable", 0) == 1;
    }

    public static class Settings extends PreferenceActivity implements Preference.OnPreferenceChangeListener {
        @Override // android.preference.PreferenceActivity, android.app.Activity
        protected void onCreate(Bundle icicle) {
            super.onCreate(icicle);
            if (RespondViaSmsManager.DBG) {
                RespondViaSmsManager.log("Settings: onCreate()...");
            }
            getPreferenceManager().setSharedPreferencesName("respond_via_sms_prefs");
            addPreferencesFromResource(R.xml.respond_via_sms_settings);
            EditTextPreference pref = (EditTextPreference) findPreference("canned_response_pref_1");
            pref.setTitle(pref.getText());
            pref.setOnPreferenceChangeListener(this);
            EditTextPreference pref2 = (EditTextPreference) findPreference("canned_response_pref_2");
            pref2.setTitle(pref2.getText());
            pref2.setOnPreferenceChangeListener(this);
            EditTextPreference pref3 = (EditTextPreference) findPreference("canned_response_pref_3");
            pref3.setTitle(pref3.getText());
            pref3.setOnPreferenceChangeListener(this);
            EditTextPreference pref4 = (EditTextPreference) findPreference("canned_response_pref_4");
            pref4.setTitle(pref4.getText());
            pref4.setOnPreferenceChangeListener(this);
            ActionBar actionBar = getActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
        }

        @Override // android.preference.Preference.OnPreferenceChangeListener
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (RespondViaSmsManager.DBG) {
                RespondViaSmsManager.log("onPreferenceChange: key = " + preference.getKey());
            }
            if (TextUtils.isEmpty((String) newValue)) {
                Toast.makeText(getApplicationContext(), R.string.respond_via_sms_cannot_be_empty, 0).show();
                return false;
            }
            EditTextPreference pref = (EditTextPreference) preference;
            pref.setTitle((String) newValue);
            return true;
        }

        @Override // android.preference.PreferenceActivity, android.app.Activity
        public boolean onOptionsItemSelected(MenuItem item) {
            int itemId = item.getItemId();
            switch (itemId) {
                case android.R.id.home:
                    if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                        MSimCallFeaturesSubSetting.goUpToTopLevelSetting(this);
                    } else {
                        CallFeaturesSetting.goUpToTopLevelSetting(this);
                    }
                    return true;
                default:
                    return super.onOptionsItemSelected(item);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void log(String msg) {
        Log.e("RespondViaSmsManager", msg);
    }
}
