package com.android.phone;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import com.android.internal.telephony.Phone;

/* JADX INFO: loaded from: classes.dex */
public class MSimMobileNetworkSettings extends PreferenceActivity implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener {
    private CheckBoxPreference mButtonDataEnabled;
    private CheckBoxPreference mButtonDataRoam;
    private Preference mLteDataServicePref;
    private boolean mOkClicked;
    private Phone mPhone;

    @Override // android.content.DialogInterface.OnClickListener
    public void onClick(DialogInterface dialog, int which) {
        if (which == -1) {
            this.mPhone.setDataRoamingEnabled(true);
            this.mOkClicked = true;
        } else {
            this.mButtonDataRoam.setChecked(false);
        }
    }

    @Override // android.content.DialogInterface.OnDismissListener
    public void onDismiss(DialogInterface dialog) {
        if (!this.mOkClicked) {
            this.mButtonDataRoam.setChecked(false);
        }
    }

    @Override // android.preference.PreferenceActivity
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == this.mButtonDataRoam) {
            log("onPreferenceTreeClick: preference = mButtonDataRoam");
            if (this.mButtonDataRoam.isChecked()) {
                this.mOkClicked = false;
                new AlertDialog.Builder(this).setMessage(getResources().getString(R.string.roaming_warning)).setTitle(android.R.string.dialog_alert_title).setIconAttribute(android.R.attr.alertDialogIcon).setPositiveButton(android.R.string.yes, this).setNegativeButton(android.R.string.no, this).show().setOnDismissListener(this);
                return true;
            }
            this.mPhone.setDataRoamingEnabled(false);
            return true;
        }
        if (preference == this.mButtonDataEnabled) {
            log("onPreferenceTreeClick: preference == mButtonDataEnabled.");
            ConnectivityManager cm = (ConnectivityManager) getSystemService("connectivity");
            cm.setMobileDataEnabled(this.mButtonDataEnabled.isChecked());
            return true;
        }
        if (preference == this.mLteDataServicePref) {
            String tmpl = Settings.Global.getString(getContentResolver(), "setup_prepaid_data_service_url");
            if (!TextUtils.isEmpty(tmpl)) {
                TelephonyManager tm = (TelephonyManager) getSystemService("phone");
                String imsi = tm.getSubscriberId();
                if (imsi == null) {
                    imsi = "";
                }
                String url = TextUtils.isEmpty(tmpl) ? null : TextUtils.expandTemplate(tmpl, imsi).toString();
                Intent intent = new Intent("android.intent.action.VIEW", Uri.parse(url));
                startActivity(intent);
                return true;
            }
            Log.e("MSimMobileNetworkSettings", "Missing SETUP_PREPAID_DATA_SERVICE_URL");
            return true;
        }
        preferenceScreen.setEnabled(false);
        return false;
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.msim_network_setting);
        this.mPhone = ((MSimPhoneGlobals) PhoneGlobals.getInstance()).getDefaultPhone();
        PreferenceScreen prefSet = getPreferenceScreen();
        this.mButtonDataEnabled = (CheckBoxPreference) prefSet.findPreference("button_data_enabled_key");
        this.mButtonDataRoam = (CheckBoxPreference) prefSet.findPreference("button_roaming_key");
        this.mLteDataServicePref = prefSet.findPreference("cdma_lte_data_service_key");
        boolean isLteOnCdma = this.mPhone.getLteOnCdmaMode() == 1;
        PreferenceScreen manageSub = (PreferenceScreen) prefSet.findPreference("button_settings_manage_sub");
        if (manageSub != null) {
            Intent intent = manageSub.getIntent();
            intent.putExtra("PACKAGE", "com.android.phone");
            intent.putExtra("TARGET_CLASS", "com.android.phone.MSimMobileNetworkSubSettings");
        }
        boolean missingDataServiceUrl = TextUtils.isEmpty(Settings.Global.getString(getContentResolver(), "setup_prepaid_data_service_url"));
        if (!isLteOnCdma || missingDataServiceUrl) {
            prefSet.removePreference(this.mLteDataServicePref);
        } else {
            Log.d("MSimMobileNetworkSettings", "keep ltePref");
        }
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        getPreferenceScreen().setEnabled(true);
        ConnectivityManager cm = (ConnectivityManager) getSystemService("connectivity");
        this.mButtonDataEnabled.setChecked(cm.getMobileDataEnabled());
        this.mButtonDataRoam.setChecked(this.mPhone.getDataRoamingEnabled());
    }

    @Override // android.app.Activity
    protected void onPause() {
        super.onPause();
    }

    private static void log(String msg) {
        Log.d("MSimMobileNetworkSettings", msg);
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId != 16908332) {
            return super.onOptionsItemSelected(item);
        }
        finish();
        return true;
    }
}
