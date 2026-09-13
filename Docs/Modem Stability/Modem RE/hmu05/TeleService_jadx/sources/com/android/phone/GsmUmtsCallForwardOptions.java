package com.android.phone;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.telephony.MSimTelephonyManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.MenuItem;
import com.android.internal.telephony.CallForwardInfo;
import java.util.ArrayList;

/* JADX INFO: loaded from: classes.dex */
public class GsmUmtsCallForwardOptions extends TimeConsumingPreferenceActivity implements DialogInterface.OnCancelListener, DialogInterface.OnClickListener {
    private static final String[] NUM_PROJECTION = {"data1"};
    private CallForwardEditPreference mButtonCFB;
    private CallForwardEditPreference mButtonCFNRc;
    private CallForwardEditPreference mButtonCFNRy;
    private CallForwardEditPreference mButtonCFU;
    private CallForwardEditPreference mButtonCFUT;
    private boolean mFirstResume;
    private Bundle mIcicle;
    private final boolean DBG = true;
    private final ArrayList<CallForwardEditPreference> mPreferences = new ArrayList<>();
    private int mInitIndex = 0;
    private int mSubscription = 0;
    private Dialog[] mDialogs = new Dialog[1];

    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onCreate(Bundle icicle) {
        Log.d("GsmUmtsCallForwardOptions", "onCreate");
        super.onCreate(icicle);
        boolean isTestForUTInterface = SystemProperties.getBoolean("persist.radio.cfu.timer", false);
        Log.d("GsmUmtsCallForwardOptions", "isImsRegisterd = " + PhoneGlobals.isIMSRegisterd());
        Log.d("GsmUmtsCallForwardOptions", "networktype = " + getActiveNetworkType());
        Log.d("GsmUmtsCallForwardOptions", "isTestForUTInterface = " + isTestForUTInterface);
        if (getActiveNetworkType() != 0 && PhoneGlobals.isIMSRegisterd() && !isTestForUTInterface) {
            Log.d("GsmUmtsCallForwardOptions", "pls open mobile network for UT settings!");
            Dialog dialog = new AlertDialog.Builder(this).setTitle("No Mobile Data Aviable").setMessage(R.string.cf_mobile_date).setIconAttribute(android.R.attr.alertDialogIcon).setPositiveButton(android.R.string.ok, this).setNegativeButton(android.R.string.cancel, this).setOnCancelListener(this).create();
            if (dialog != null) {
                this.mDialogs[0] = dialog;
            }
            dialog.show();
            return;
        }
        addPreferencesFromResource(R.xml.callforward_options);
        this.mSubscription = getIntent().getIntExtra("subscription", PhoneGlobals.getInstance().getDefaultSubscription());
        Log.d("GsmUmtsCallForwardOptions", "Call Forwarding options, subscription =" + this.mSubscription);
        PreferenceScreen prefSet = getPreferenceScreen();
        this.mButtonCFU = (CallForwardEditPreference) prefSet.findPreference("button_cfu_key");
        this.mButtonCFB = (CallForwardEditPreference) prefSet.findPreference("button_cfb_key");
        this.mButtonCFNRy = (CallForwardEditPreference) prefSet.findPreference("button_cfnry_key");
        this.mButtonCFNRc = (CallForwardEditPreference) prefSet.findPreference("button_cfnrc_key");
        this.mButtonCFUT = (CallForwardEditPreference) prefSet.findPreference("button_cfut_key");
        if (this.mSubscription == 0 && getResources().getBoolean(R.bool.join_call_forward)) {
            int phoneType = getPhoneTypeBySubscription(this.mSubscription);
            if (1 == phoneType && this.mButtonCFNRc != null) {
                prefSet.removePreference(this.mButtonCFNRc);
            }
        }
        if (!PhoneGlobals.isIMSRegisterd()) {
            prefSet.removePreference(this.mButtonCFUT);
        }
        this.mButtonCFU.setParentActivity(this, this.mButtonCFU.reason);
        this.mButtonCFB.setParentActivity(this, this.mButtonCFB.reason);
        this.mButtonCFNRy.setParentActivity(this, this.mButtonCFNRy.reason);
        this.mButtonCFNRc.setParentActivity(this, this.mButtonCFNRc.reason);
        this.mButtonCFUT.setParentActivity(this, this.mButtonCFUT.reason);
        this.mPreferences.add(this.mButtonCFU);
        this.mPreferences.add(this.mButtonCFB);
        this.mPreferences.add(this.mButtonCFNRy);
        this.mPreferences.add(this.mButtonCFNRc);
        this.mPreferences.add(this.mButtonCFUT);
        this.mFirstResume = true;
        this.mIcicle = icicle;
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    private int getPhoneTypeBySubscription(int subscription) {
        if (!MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            int phoneType = TelephonyManager.getDefault().getCurrentPhoneType();
            return phoneType;
        }
        int phoneType2 = MSimTelephonyManager.getDefault().getCurrentPhoneType(subscription);
        return phoneType2;
    }

    @Override // android.content.DialogInterface.OnClickListener
    public void onClick(DialogInterface dialog, int id) {
        if (id == -2) {
            finish();
            return;
        }
        if (dialog == this.mDialogs[0]) {
            if (id == -1) {
                Intent newIntent = new Intent("android.settings.SETTINGS");
                newIntent.addFlags(268435456);
                startActivity(newIntent);
            }
            finish();
        }
    }

    @Override // com.android.phone.TimeConsumingPreferenceActivity, android.app.Activity
    public void onResume() {
        super.onResume();
        if (this.mFirstResume) {
            if (this.mIcicle == null) {
                Log.d("GsmUmtsCallForwardOptions", "start to init ");
                this.mPreferences.get(this.mInitIndex).init(this, false, this.mSubscription);
            } else {
                this.mInitIndex = this.mPreferences.size();
                for (CallForwardEditPreference pref : this.mPreferences) {
                    Bundle bundle = (Bundle) this.mIcicle.getParcelable(pref.getKey());
                    pref.setToggled(bundle.getBoolean("toggle"));
                    CallForwardInfo cf = new CallForwardInfo();
                    cf.number = bundle.getString("number");
                    cf.status = bundle.getInt("status");
                    pref.handleCallForwardResult(cf);
                    pref.init(this, true, this.mSubscription);
                }
            }
            this.mFirstResume = false;
            this.mIcicle = null;
        }
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        for (CallForwardEditPreference pref : this.mPreferences) {
            Bundle bundle = new Bundle();
            bundle.putBoolean("toggle", pref.isToggled());
            if (pref.callForwardInfo != null) {
                bundle.putString("number", pref.callForwardInfo.number);
                bundle.putInt("status", pref.callForwardInfo.status);
            }
            outState.putParcelable(pref.getKey(), bundle);
        }
    }

    @Override // com.android.phone.TimeConsumingPreferenceActivity, com.android.phone.TimeConsumingPreferenceListener
    public void onFinished(Preference preference, boolean reading) {
        if (this.mInitIndex < this.mPreferences.size() - 1 && !isFinishing()) {
            this.mInitIndex++;
            this.mPreferences.get(this.mInitIndex).init(this, false, this.mSubscription);
        }
        super.onFinished(preference, reading);
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d("GsmUmtsCallForwardOptions", "onActivityResult: done");
        if (resultCode != -1) {
            Log.d("GsmUmtsCallForwardOptions", "onActivityResult: contact picker result not OK.");
            return;
        }
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(data.getData(), NUM_PROJECTION, null, null, null);
            if (cursor == null || !cursor.moveToFirst()) {
                Log.d("GsmUmtsCallForwardOptions", "onActivityResult: bad contact data, no results found.");
                if (cursor != null) {
                    return;
                } else {
                    return;
                }
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
                case 6:
                    this.mButtonCFUT.onPickActivityResult(cursor.getString(0));
                    break;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
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

    private int getActiveNetworkType() {
        NetworkInfo ni;
        ConnectivityManager cm = (ConnectivityManager) getSystemService("connectivity");
        if (cm == null || (ni = cm.getActiveNetworkInfo()) == null || !ni.isConnected()) {
            return -1;
        }
        return ni.getType();
    }
}
