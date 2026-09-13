package com.android.phone;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.util.Log;
import java.util.ArrayList;

/* JADX INFO: loaded from: classes.dex */
public class CdmaCallForwardOptions extends PreferenceActivity {
    private static final String[] NUM_PROJECTION = {"data1"};
    private CdmaCallForwardEditPreference mButtonCFB;
    private CdmaCallForwardEditPreference mButtonCFNRc;
    private CdmaCallForwardEditPreference mButtonCFNRy;
    private CdmaCallForwardEditPreference mButtonCFU;
    private CdmaCallOptionsSetting mCallOptionSettings;
    private PreferenceScreen mCfDeactAllPref;
    private PreferenceScreen mCfbDeactPref;
    private PreferenceScreen mCfnrcDeactPref;
    private PreferenceScreen mCfnryDeactPref;
    private PreferenceScreen mCfuDeactPref;
    private boolean mFirstResume;
    private final boolean DBG = true;
    private final ArrayList<CdmaCallForwardEditPreference> mPreferences = new ArrayList<>();
    private final ArrayList<PreferenceScreen> mDeactPreScreens = new ArrayList<>();
    private int mSubscription = 0;

    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.callforward_cdma_options);
        this.mSubscription = getIntent().getIntExtra("subscription", 0);
        Log.d("CdmaCallForwardOptions", "Inside CF options, Getting subscription =" + this.mSubscription);
        PreferenceScreen prefSet = getPreferenceScreen();
        this.mButtonCFU = (CdmaCallForwardEditPreference) prefSet.findPreference("button_cfu_key");
        this.mButtonCFB = (CdmaCallForwardEditPreference) prefSet.findPreference("button_cfb_key");
        this.mButtonCFNRy = (CdmaCallForwardEditPreference) prefSet.findPreference("button_cfnry_key");
        this.mButtonCFNRc = (CdmaCallForwardEditPreference) prefSet.findPreference("button_cfnrc_key");
        this.mButtonCFU.setParentActivity(this, this.mButtonCFU.reason);
        this.mButtonCFB.setParentActivity(this, this.mButtonCFB.reason);
        this.mButtonCFNRy.setParentActivity(this, this.mButtonCFNRy.reason);
        this.mButtonCFNRc.setParentActivity(this, this.mButtonCFNRc.reason);
        this.mPreferences.add(this.mButtonCFU);
        this.mPreferences.add(this.mButtonCFB);
        this.mPreferences.add(this.mButtonCFNRy);
        this.mPreferences.add(this.mButtonCFNRc);
        this.mCfuDeactPref = (PreferenceScreen) prefSet.findPreference("button_cfu_deact_key");
        this.mCfbDeactPref = (PreferenceScreen) prefSet.findPreference("button_cfb_deact_key");
        this.mCfnryDeactPref = (PreferenceScreen) prefSet.findPreference("button_cfnry_deact_key");
        this.mCfnrcDeactPref = (PreferenceScreen) prefSet.findPreference("button_cfnrc_deact_key");
        this.mCfDeactAllPref = (PreferenceScreen) prefSet.findPreference("button_cf_deact_all_key");
        this.mDeactPreScreens.add(this.mCfuDeactPref);
        this.mDeactPreScreens.add(this.mCfbDeactPref);
        this.mDeactPreScreens.add(this.mCfnryDeactPref);
        this.mDeactPreScreens.add(this.mCfnrcDeactPref);
        this.mDeactPreScreens.add(this.mCfDeactAllPref);
        this.mFirstResume = true;
    }

    @Override // android.app.Activity
    public void onResume() {
        super.onResume();
        if (this.mFirstResume) {
            for (int i = 0; i < 5; i++) {
                this.mCallOptionSettings = new CdmaCallOptionsSetting(this, i, 1, this.mSubscription);
                if (i < this.mPreferences.size()) {
                    this.mPreferences.get(i).init(this, this.mSubscription, this.mCallOptionSettings.getActivateNumber());
                }
                this.mDeactPreScreens.get(i).getIntent().putExtra("subscription", this.mSubscription).putExtra("Cdma_Supp", true);
                Log.d("CdmaCallForwardOptions", "call option on type: " + i + " Getting deact num =" + this.mCallOptionSettings.getDeactivateNumber());
                this.mDeactPreScreens.get(i).getIntent().setData(Uri.fromParts("tel", this.mCallOptionSettings.getDeactivateNumber(), null));
                this.mDeactPreScreens.get(i).setSummary(this.mCallOptionSettings.getDeactivateNumber());
            }
            this.mFirstResume = false;
        }
    }

    @Override // android.app.Activity
    public void startActivityForResult(Intent intent, int requestCode) {
        if (requestCode == -1) {
            super.startActivityForResult(intent, requestCode);
        } else {
            Log.d("CdmaCallForwardOptions", "startSubActivity: starting requested subactivity");
            super.startActivityForResult(intent, requestCode);
        }
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d("CdmaCallForwardOptions", "onActivityResult: done");
        if (resultCode != -1) {
            Log.d("CdmaCallForwardOptions", "onActivityResult: contact picker result not OK.");
        }
        Cursor cursor = getContentResolver().query(data.getData(), NUM_PROJECTION, null, null, null);
        if (cursor == null || !cursor.moveToFirst()) {
            Log.d("CdmaCallForwardOptions", "onActivityResult: bad contact data, no results found.");
            return;
        }
        switch (requestCode) {
            case 0:
                this.mButtonCFU.onPickActivityResult(cursor.getString(0));
                break;
            case 1:
                this.mButtonCFB.onPickActivityResult(cursor.getString(0));
                break;
            case 2:
                this.mButtonCFNRy.onPickActivityResult(cursor.getString(0));
                break;
            case 3:
                this.mButtonCFNRc.onPickActivityResult(cursor.getString(0));
                break;
        }
    }
}
