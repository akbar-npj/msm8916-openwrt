package com.android.phone;

import android.app.ActionBar;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.telephony.MSimTelephonyManager;
import android.util.Log;
import android.view.MenuItem;
import java.util.ArrayList;

/* JADX INFO: loaded from: classes.dex */
public class GsmUmtsAdditionalCallOptions extends TimeConsumingPreferenceActivity {
    private CLIRListPreference mCLIRButton;
    private CallWaitingCheckBoxPreference mCWButton;
    private final boolean DBG = true;
    private final ArrayList<Preference> mPreferences = new ArrayList<>();
    private int mInitIndex = 0;
    private int mSubscription = 0;

    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.gsm_umts_additional_options);
        this.mSubscription = getIntent().getIntExtra("subscription", PhoneGlobals.getInstance().getDefaultSubscription());
        Log.d("GsmUmtsAdditionalCallOptions", "GsmUmtsAdditionalCallOptions onCreate, subscription: " + this.mSubscription);
        PreferenceScreen prefSet = getPreferenceScreen();
        this.mCLIRButton = (CLIRListPreference) prefSet.findPreference("button_clir_key");
        this.mCWButton = (CallWaitingCheckBoxPreference) prefSet.findPreference("button_cw_key");
        this.mPreferences.add(this.mCLIRButton);
        this.mPreferences.add(this.mCWButton);
        if (icicle == null) {
            Log.d("GsmUmtsAdditionalCallOptions", "start to init ");
            this.mCLIRButton.init(this, false, this.mSubscription);
        } else {
            Log.d("GsmUmtsAdditionalCallOptions", "restore stored states");
            this.mInitIndex = this.mPreferences.size();
            this.mCLIRButton.init(this, true, this.mSubscription);
            this.mCWButton.init(this, true, this.mSubscription);
            int[] clirArray = icicle.getIntArray(this.mCLIRButton.getKey());
            if (clirArray != null) {
                Log.d("GsmUmtsAdditionalCallOptions", "onCreate:  clirArray[0]=" + clirArray[0] + ", clirArray[1]=" + clirArray[1]);
                this.mCLIRButton.handleGetCLIRResult(clirArray);
            } else {
                this.mCLIRButton.init(this, false, this.mSubscription);
            }
        }
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (this.mCLIRButton.clirArray != null) {
            outState.putIntArray(this.mCLIRButton.getKey(), this.mCLIRButton.clirArray);
        }
    }

    @Override // com.android.phone.TimeConsumingPreferenceActivity, com.android.phone.TimeConsumingPreferenceListener
    public void onFinished(Preference preference, boolean reading) {
        if (this.mInitIndex < this.mPreferences.size() - 1 && !isFinishing()) {
            this.mInitIndex++;
            Preference pref = this.mPreferences.get(this.mInitIndex);
            if (pref instanceof CallWaitingCheckBoxPreference) {
                ((CallWaitingCheckBoxPreference) pref).init(this, false, this.mSubscription);
            }
        }
        super.onFinished(preference, reading);
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == 16908332) {
            if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                MSimCallFeaturesSubSetting.goUpToTopLevelSetting(this);
            } else {
                CallFeaturesSetting.goUpToTopLevelSetting(this);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
