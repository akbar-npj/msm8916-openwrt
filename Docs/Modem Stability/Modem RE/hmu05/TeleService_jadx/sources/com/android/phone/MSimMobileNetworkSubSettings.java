package com.android.phone;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.MSimTelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.codeaurora.telephony.msim.MSimUiccController;

/* JADX INFO: loaded from: classes.dex */
public class MSimMobileNetworkSubSettings extends PreferenceActivity implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener, Preference.OnPreferenceChangeListener {
    private CheckBoxPreference mButtonDataEnabled;
    private CheckBoxPreference mButtonDataRoam;
    private CheckBoxPreference mButtonPreferredLte;
    private ListPreference mButtonPreferredNetworkMode;
    CdmaOptions mCdmaOptions;
    private Preference mClickedPreference;
    GsmUmtsOptions mGsmUmtsOptions;
    private MyHandler mHandler;
    private Preference mLteDataServicePref;
    private boolean mOkClicked;
    private Phone mPhone;
    private BroadcastReceiver mReceiver = new BroadcastReceiver() { // from class: com.android.phone.MSimMobileNetworkSubSettings.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            MSimMobileNetworkSubSettings.this.setScreenState();
        }
    };
    private int mSubscription;

    @Override // android.content.DialogInterface.OnClickListener
    public void onClick(DialogInterface dialog, int which) {
        if (which == -1) {
            multiSimSetDataRoaming(true, this.mSubscription);
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
        if (this.mGsmUmtsOptions != null && this.mGsmUmtsOptions.preferenceTreeClick(preference)) {
            return true;
        }
        if (preference == this.mButtonDataRoam) {
            log("onPreferenceTreeClick: preference = mButtonDataRoam");
            if (this.mButtonDataRoam.isChecked()) {
                this.mOkClicked = false;
                new AlertDialog.Builder(this).setMessage(getResources().getString(R.string.roaming_warning)).setTitle(android.R.string.dialog_alert_title).setIconAttribute(android.R.attr.alertDialogIcon).setPositiveButton(android.R.string.yes, this).setNegativeButton(android.R.string.no, this).show().setOnDismissListener(this);
                return true;
            }
            multiSimSetDataRoaming(false, this.mSubscription);
            return true;
        }
        if (preference == this.mButtonDataEnabled) {
            log("onPreferenceTreeClick: preference == mButtonDataEnabled.");
            multiSimSetMobileData(this.mButtonDataEnabled.isChecked(), this.mSubscription);
            return true;
        }
        if (preference == this.mButtonPreferredLte) {
            multiSimSetPreferredLte(this.mButtonPreferredLte.isChecked());
            return true;
        }
        if (this.mCdmaOptions != null && this.mCdmaOptions.preferenceTreeClick(preference)) {
            if (!Boolean.parseBoolean(SystemProperties.get("ril.cdma.inecmmode"))) {
                return true;
            }
            this.mClickedPreference = preference;
            startActivityForResult(new Intent("android.intent.action.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS", (Uri) null), 17);
            return true;
        }
        if (preference == this.mButtonPreferredNetworkMode) {
            int settingsNetworkMode = getPreferredNetworkMode();
            setPreferredNetworkModeValue(settingsNetworkMode);
            return true;
        }
        if (preference == this.mLteDataServicePref) {
            String tmpl = Settings.Global.getString(getContentResolver(), "setup_prepaid_data_service_url");
            if (!TextUtils.isEmpty(tmpl)) {
                MSimTelephonyManager tm = (MSimTelephonyManager) getSystemService("phone_msim");
                String imsi = tm.getSubscriberId(this.mSubscription);
                if (imsi == null) {
                    imsi = "";
                }
                String url = TextUtils.isEmpty(tmpl) ? null : TextUtils.expandTemplate(tmpl, imsi).toString();
                Intent intent = new Intent("android.intent.action.VIEW", Uri.parse(url));
                startActivity(intent);
                return true;
            }
            Log.e("MSimMobileNetworkSubSettings", "Missing SETUP_PREPAID_DATA_SERVICE_URL");
            return true;
        }
        preferenceScreen.setEnabled(false);
        return false;
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onCreate(Bundle icicle) {
        Preference pref;
        super.onCreate(icicle);
        PhoneGlobals app = PhoneGlobals.getInstance();
        addPreferencesFromResource(R.xml.msim_network_sub_setting);
        this.mSubscription = getIntent().getIntExtra("subscription", app.getDefaultSubscription());
        log("Settings onCreate subscription =" + this.mSubscription);
        this.mPhone = app.getPhone(this.mSubscription);
        this.mHandler = new MyHandler();
        IntentFilter intentFilter = new IntentFilter("android.intent.action.AIRPLANE_MODE");
        intentFilter.addAction("android.intent.action.SIM_STATE_CHANGED");
        registerReceiver(this.mReceiver, intentFilter);
        PreferenceScreen prefSet = getPreferenceScreen();
        PreferenceCategory pcSettingsLabel = new PreferenceCategory(this);
        pcSettingsLabel.setTitle(R.string.settings_label);
        prefSet.addPreference(pcSettingsLabel);
        this.mButtonDataEnabled = (CheckBoxPreference) prefSet.findPreference("button_data_enabled_key");
        prefSet.removePreference(this.mButtonDataEnabled);
        this.mButtonPreferredNetworkMode = (ListPreference) prefSet.findPreference("preferred_network_mode_key");
        this.mButtonPreferredLte = (CheckBoxPreference) prefSet.findPreference("toggle_preferred_lte");
        if (!getResources().getBoolean(R.bool.config_tdd_data_only) || this.mSubscription != 0) {
            prefSet.removePreference(this.mButtonPreferredLte);
            this.mButtonPreferredLte = null;
        }
        int networkFeature = SystemProperties.getInt("persist.radio.network_feature", 0);
        switch (networkFeature) {
            case 1:
                prefSet.removePreference(this.mButtonPreferredNetworkMode);
                break;
            case 2:
                this.mButtonPreferredNetworkMode.setDialogTitle(R.string.preferred_network_mode_dialogtitle_cmcc);
                if (getResources().getBoolean(R.bool.config_network_cmcc_feature)) {
                    if (MSimUiccController.getInstance().getUiccCard(this.mSubscription) != null && MSimUiccController.getInstance().getUiccCard(this.mSubscription).isApplicationOnIcc(IccCardApplicationStatus.AppType.APPTYPE_USIM) && (PhoneGlobals.getInstance().mPhoneServiceClient == null || PhoneGlobals.getInstance().getCurrentLTESub() == this.mSubscription)) {
                        this.mButtonPreferredNetworkMode.setEntries(R.array.preferred_network_mode_options_cmcc);
                        this.mButtonPreferredNetworkMode.setEntryValues(R.array.preferred_network_mode_options_values_cmcc);
                    } else {
                        prefSet.removePreference(this.mButtonPreferredNetworkMode);
                    }
                } else {
                    this.mButtonPreferredNetworkMode.setEntries(R.array.preferred_network_mode_choices_cmcc);
                    this.mButtonPreferredNetworkMode.setEntryValues(R.array.preferred_network_mode_values_cmcc);
                }
                break;
            case 3:
                this.mButtonPreferredNetworkMode.setEntries(R.array.preferred_network_mode_choices_tdscdma);
                this.mButtonPreferredNetworkMode.setEntryValues(R.array.preferred_network_mode_values_tdscdma);
                break;
            case 4:
                this.mButtonPreferredNetworkMode.setEntries(R.array.preferred_network_mode_choices_lte);
                this.mButtonPreferredNetworkMode.setEntryValues(R.array.preferred_network_mode_values_lte);
                break;
        }
        Preference mUPLMNPref = prefSet.findPreference("button_uplmn_key");
        if (!getResources().getBoolean(R.bool.config_uplmn_for_cta_test)) {
            prefSet.removePreference(mUPLMNPref);
        } else {
            mUPLMNPref.getIntent().putExtra("subscription", this.mSubscription);
        }
        boolean isLteOnCdma = this.mPhone.getLteOnCdmaMode() == 1;
        if (getResources().getBoolean(R.bool.world_phone)) {
            this.mButtonPreferredNetworkMode.setOnPreferenceChangeListener(this);
            int settingsNetworkMode = getPreferredNetworkMode();
            setPreferredNetworkModeValue(settingsNetworkMode);
            this.mCdmaOptions = new CdmaOptions(this, prefSet, this.mPhone, this.mSubscription);
            this.mGsmUmtsOptions = new GsmUmtsOptions(this, prefSet, this.mSubscription);
        } else {
            if (!isLteOnCdma) {
                prefSet.removePreference(this.mButtonPreferredNetworkMode);
            } else {
                this.mButtonPreferredNetworkMode.setOnPreferenceChangeListener(this);
                int settingsNetworkMode2 = getPreferredNetworkMode();
                setPreferredNetworkModeValue(settingsNetworkMode2);
            }
            int phoneType = this.mPhone.getPhoneType();
            if (phoneType == 2) {
                this.mCdmaOptions = new CdmaOptions(this, prefSet, this.mPhone, this.mSubscription);
            } else if (phoneType == 1) {
                this.mGsmUmtsOptions = new GsmUmtsOptions(this, prefSet, this.mSubscription);
            } else {
                throw new IllegalStateException("Unexpected phone type: " + phoneType);
            }
        }
        boolean isCarrierSettingsEnabled = getResources().getBoolean(R.bool.config_carrier_settings_enable);
        if (!isCarrierSettingsEnabled && (pref = prefSet.findPreference("carrier_settings_key")) != null) {
            prefSet.removePreference(pref);
            Preference pref2 = prefSet.findPreference("carrier_settings_key");
            if (pref2 != null) {
                prefSet.removePreference(pref2);
            }
        }
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        PreferenceCategory pcDataSettings = new PreferenceCategory(this);
        pcDataSettings.setTitle(R.string.title_data_settings);
        prefSet.addPreference(pcDataSettings);
        addPreferencesFromResource(R.xml.msim_network_setting);
        prefSet.removePreference(prefSet.findPreference("button_settings_manage_sub"));
        this.mButtonDataRoam = (CheckBoxPreference) prefSet.findPreference("button_roaming_key");
        this.mLteDataServicePref = prefSet.findPreference("cdma_lte_data_service_key");
        boolean missingDataServiceUrl = TextUtils.isEmpty(Settings.Global.getString(getContentResolver(), "setup_prepaid_data_service_url"));
        if (!isLteOnCdma || missingDataServiceUrl) {
            prefSet.removePreference(this.mLteDataServicePref);
        } else {
            Log.d("MSimMobileNetworkSubSettings", "keep ltePref");
        }
        if (getResources().getBoolean(R.bool.hide_roaming)) {
            if (!isLteOnCdma || missingDataServiceUrl) {
                prefSet.removePreference(pcDataSettings);
                prefSet.removePreference(this.mButtonDataRoam);
            } else {
                prefSet.removePreference(this.mButtonDataRoam);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int getAcqValue() {
        try {
            int acq = MSimTelephonyManager.getIntAtIndex(getContentResolver(), "network_acq", this.mSubscription);
            return acq;
        } catch (Settings.SettingNotFoundException e) {
            Log.d("MSimMobileNetworkSubSettings", "failed to get acq", e);
            return 0;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setPreferredNetworkModeValue(int settingsNetworkMode) {
        int networkFeature = SystemProperties.getInt("persist.radio.network_feature", 0);
        if ((networkFeature == 2 || networkFeature == 4) && settingsNetworkMode == 20) {
            if (networkFeature == 2 && getResources().getBoolean(R.bool.config_network_cmcc_feature)) {
                this.mButtonPreferredNetworkMode.setValue(Integer.toString(settingsNetworkMode));
                return;
            }
            int acq = getAcqValue();
            String acqString = acq == 0 ? "1" : Integer.toString(acq);
            String networkmodeString = Integer.toString(settingsNetworkMode) + "-" + acqString;
            Log.d("MSimMobileNetworkSubSettings", networkmodeString);
            this.mButtonPreferredNetworkMode.setValue(networkmodeString);
            return;
        }
        this.mButtonPreferredNetworkMode.setValue(Integer.toString(settingsNetworkMode));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateButtonPreferredLte() {
        if (this.mButtonPreferredLte != null) {
            boolean checked = false;
            boolean enabled = false;
            try {
                enabled = PhoneUtils.isLTE(MSimTelephonyManager.getIntAtIndex(getContentResolver(), "preferred_network_mode", this.mSubscription));
            } catch (Settings.SettingNotFoundException e) {
                Log.d("MSimMobileNetworkSubSettings", "failed to update lte button", e);
            }
            try {
                checked = MSimTelephonyManager.getIntAtIndex(getContentResolver(), "tdd_data_only_user_pref", this.mSubscription) == 1;
            } catch (Settings.SettingNotFoundException e2) {
                Log.d("MSimMobileNetworkSubSettings", "failed to update lte button", e2);
            }
            if (this.mButtonPreferredNetworkMode != null) {
                UpdatePreferredNetworkModeSummary(getPreferredNetworkMode(), getAcqValue());
            }
            this.mButtonPreferredLte.setEnabled(enabled);
            this.mButtonPreferredLte.setChecked(checked);
        }
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        setScreenState();
        this.mButtonDataEnabled.setChecked(multiSimGetMobileData(this.mSubscription));
        this.mButtonDataRoam.setChecked(multiSimGetDataRoaming(this.mSubscription));
        updateButtonPreferredLte();
        if (getPreferenceScreen().findPreference("preferred_network_mode_key") != null) {
            this.mPhone.getPreferredNetworkType(this.mHandler.obtainMessage(0));
        }
        if (this.mGsmUmtsOptions != null) {
            this.mGsmUmtsOptions.enableScreen();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setScreenState() {
        int simState = MSimTelephonyManager.getDefault().getSimState(this.mSubscription);
        getPreferenceScreen().setEnabled(simState == 5);
    }

    @Override // android.app.Activity
    protected void onPause() {
        super.onPause();
    }

    @Override // android.preference.Preference.OnPreferenceChangeListener
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (preference == this.mButtonPreferredNetworkMode) {
            String strMode = (String) objValue;
            String strAcq = "0";
            boolean isContainAcq = strMode.contains("-");
            if (isContainAcq) {
                String[] values = strMode.split("-");
                strMode = values[0];
                strAcq = values[1];
            }
            this.mButtonPreferredNetworkMode.setValue((String) objValue);
            int buttonNetworkMode = Integer.valueOf(strMode).intValue();
            int settingsNetworkMode = getPreferredNetworkMode();
            int buttonAcq = Integer.valueOf(strAcq).intValue();
            int settingsAcq = getAcqValue();
            if (buttonNetworkMode != settingsNetworkMode || buttonAcq != settingsAcq) {
                if (buttonNetworkMode < 0 || buttonNetworkMode > 22) {
                    log("Invalid Network Mode (" + buttonNetworkMode + ") Chosen. Ignore mode");
                } else {
                    UpdatePreferredNetworkModeSummary(buttonNetworkMode, buttonAcq);
                    setPreferredNetworkMode(buttonNetworkMode);
                    setPreferredNetworkType(isContainAcq, buttonNetworkMode, strAcq);
                }
            }
        }
        return true;
    }

    private void setPreferredNetworkType(boolean containAcq, int networkMode, String strAcq) {
        Message msg;
        if (MSimPhoneGlobals.getInstance().mPhoneServiceClient != null) {
            msg = this.mHandler.obtainMessage(2);
        } else {
            msg = this.mHandler.obtainMessage(1);
        }
        if (containAcq) {
            int acq = Integer.valueOf(strAcq).intValue();
            if (MSimPhoneGlobals.getInstance().mPhoneServiceClient != null) {
                MSimPhoneGlobals.getInstance().setPrefNetworWithAcq(this.mSubscription, networkMode, acq, msg);
                return;
            } else {
                this.mPhone.setPreferredNetworkType(networkMode, this.mHandler.obtainMessage(1));
                return;
            }
        }
        if (MSimPhoneGlobals.getInstance().mPhoneServiceClient != null) {
            MSimPhoneGlobals.getInstance().setPrefNetwork(this.mSubscription, networkMode, msg);
        } else {
            this.mPhone.setPreferredNetworkType(networkMode, this.mHandler.obtainMessage(1));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int getPreferredNetworkMode() {
        try {
            int nwMode = MSimTelephonyManager.getIntAtIndex(this.mPhone.getContext().getContentResolver(), "preferred_network_mode", this.mSubscription);
            return nwMode;
        } catch (Settings.SettingNotFoundException e) {
            log("getPreferredNetworkMode: Could not find PREFERRED_NETWORK_MODE!!!");
            return 11;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setPreferredNetworkMode(int nwMode) {
        MSimTelephonyManager.putIntAtIndex(this.mPhone.getContext().getContentResolver(), "preferred_network_mode", this.mSubscription, nwMode);
    }

    private class MyHandler extends Handler {
        private MyHandler() {
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    handleGetPreferredNetworkTypeResponse(msg);
                    break;
                case 1:
                    handleSetPreferredNetworkTypeResponse(msg);
                    break;
                case 2:
                    handleSetPreferredNetworkTypeWithAcqResponse();
                    break;
                case 3:
                    handleSetPreferredLTEResponse();
                    break;
            }
        }

        private void handleSetPreferredLTEResponse() {
            MSimMobileNetworkSubSettings.this.updateButtonPreferredLte();
        }

        private void handleSetPreferredNetworkTypeWithAcqResponse() {
            if (MSimMobileNetworkSubSettings.this.mButtonPreferredNetworkMode != null) {
                MSimMobileNetworkSubSettings.this.UpdatePreferredNetworkModeSummary(MSimMobileNetworkSubSettings.this.getPreferredNetworkMode(), MSimMobileNetworkSubSettings.this.getAcqValue());
            }
            MSimMobileNetworkSubSettings.this.updateButtonPreferredLte();
        }

        private void handleGetPreferredNetworkTypeResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            if (ar.exception == null) {
                int modemNetworkMode = ((int[]) ar.result)[0];
                MSimMobileNetworkSubSettings.log("handleGetPreferredNetworkTypeResponse: modemNetworkMode = " + modemNetworkMode);
                int settingsNetworkMode = MSimMobileNetworkSubSettings.this.getPreferredNetworkMode();
                MSimMobileNetworkSubSettings.log("handleGetPreferredNetworkTypeReponse: settingsNetworkMode = " + settingsNetworkMode);
                if (modemNetworkMode < 0 || modemNetworkMode > 22) {
                    MSimMobileNetworkSubSettings.log("handleGetPreferredNetworkTypeResponse: else: reset to default");
                    resetNetworkModeToDefault();
                } else {
                    MSimMobileNetworkSubSettings.log("handleGetPreferredNetworkTypeResponse: if 1: modemNetworkMode = " + modemNetworkMode);
                    if (modemNetworkMode != settingsNetworkMode) {
                        MSimMobileNetworkSubSettings.log("handleGetPreferredNetworkTypeResponse: if 2: modemNetworkMode != settingsNetworkMode");
                        MSimMobileNetworkSubSettings.log("handleGetPreferredNetworkTypeResponse: if 2: settingsNetworkMode = " + modemNetworkMode);
                        MSimMobileNetworkSubSettings.this.setPreferredNetworkMode(modemNetworkMode);
                    }
                    int acq = MSimMobileNetworkSubSettings.this.getAcqValue();
                    MSimMobileNetworkSubSettings.this.UpdatePreferredNetworkModeSummary(modemNetworkMode, acq);
                    MSimMobileNetworkSubSettings.this.setPreferredNetworkModeValue(modemNetworkMode);
                }
                MSimMobileNetworkSubSettings.this.updateButtonPreferredLte();
            }
        }

        private void handleSetPreferredNetworkTypeResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            if (ar.exception == null) {
                int networkMode = Integer.valueOf(MSimMobileNetworkSubSettings.this.mButtonPreferredNetworkMode.getValue()).intValue();
                MSimMobileNetworkSubSettings.this.setPreferredNetworkMode(networkMode);
                MSimMobileNetworkSubSettings.this.updateButtonPreferredLte();
                return;
            }
            MSimMobileNetworkSubSettings.this.mPhone.getPreferredNetworkType(obtainMessage(0));
        }

        private void resetNetworkModeToDefault() {
            MSimMobileNetworkSubSettings.this.setPreferredNetworkModeValue(11);
            MSimMobileNetworkSubSettings.this.setPreferredNetworkMode(11);
            MSimMobileNetworkSubSettings.this.mPhone.setPreferredNetworkType(11, obtainMessage(1));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void UpdatePreferredNetworkModeSummary(int NetworkMode, int acq) {
        int networkFeature = SystemProperties.getInt("persist.radio.network_feature", 0);
        switch (NetworkMode) {
            case 0:
                this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_wcdma_perf_summary);
                break;
            case 1:
                this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_gsm_only_summary);
                if (networkFeature == 2 || networkFeature == 4) {
                    if (PhoneGlobals.getInstance().mPhoneServiceClient == null || PhoneGlobals.getInstance().getPreferredLTESub() != this.mSubscription) {
                        this.mButtonPreferredNetworkMode.setEnabled(false);
                    }
                }
                break;
            case 2:
                this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_wcdma_only_summary);
                break;
            case 3:
                this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_gsm_wcdma_summary);
                break;
            case 4:
                switch (this.mPhone.getLteOnCdmaMode()) {
                    case 1:
                        this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_cdma_summary);
                        break;
                    default:
                        this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_cdma_evdo_summary);
                        break;
                }
                break;
            case 5:
                this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_cdma_only_summary);
                break;
            case 6:
                this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_evdo_only_summary);
                break;
            case 7:
                this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_cdma_evdo_gsm_wcdma_summary);
                break;
            case 8:
                this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_lte_cdma_evdo_summary);
                break;
            case 9:
                this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_lte_gsm_wcdma_summary);
                break;
            case 10:
                this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_global_summary);
                break;
            case 11:
                this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_lte_summary);
                break;
            case 12:
                this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_lte_wcdma_summary);
                break;
            case 13:
                this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_td_scdma_only_summary);
                break;
            case 14:
                this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_td_scdma_wcdma_summary);
                break;
            case 15:
                this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_td_scdma_lte_summary);
                break;
            case 16:
                this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_td_scdma_gsm_summary);
                break;
            case 17:
                this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_td_scdma_gsm_lte_summary);
                break;
            case 18:
                if (networkFeature == 2) {
                    this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_3g_2g_auto_summary);
                } else if (networkFeature == 4) {
                    this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_4g_3g_2g_td);
                } else {
                    this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_td_scdma_gsm_wcdma_summary);
                }
                break;
            case 19:
                this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_td_scdma_wcdma_lte_summary);
                break;
            case 20:
                if (networkFeature == 2) {
                    if (getResources().getBoolean(R.bool.config_network_cmcc_feature)) {
                        this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_4g_3g_2g_auto_summary);
                    } else if (acq == 1) {
                        this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_4g_3g_2g_4g);
                    } else if (acq == 2) {
                        this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_4g_3g_2g_3g);
                    } else {
                        this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_4g_3g_2g_auto_summary);
                    }
                } else if (networkFeature == 4) {
                    if (acq == 1) {
                        this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_4g_3g_2g_lte);
                    } else {
                        this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_td_scdma_gsm_wcdma_lte_summary);
                    }
                } else {
                    this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_td_scdma_gsm_wcdma_lte_summary);
                }
                break;
            case 21:
                this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_td_scdma_cdma_evdo_gsm_wcdma_summary);
                break;
            case 22:
                this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_td_scdma_lte_cdma_evdo_gsm_wcdma_summary);
                break;
            default:
                this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_global_summary);
                break;
        }
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case 17:
                Boolean isChoiceYes = Boolean.valueOf(data.getBooleanExtra("exit_ecm_result", false));
                if (isChoiceYes.booleanValue()) {
                    this.mCdmaOptions.showDialog(this.mClickedPreference);
                }
                break;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void log(String msg) {
        Log.d("MSimMobileNetworkSubSettings", msg);
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

    private boolean multiSimGetDataRoaming(int sub) {
        boolean enabled = Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), new StringBuilder().append("data_roaming").append(sub).toString(), 0) != 0;
        log("Get Data Roaming for SUB-" + sub + " is " + enabled);
        return enabled;
    }

    private void multiSimSetDataRoaming(boolean enabled, int sub) {
        Settings.Global.putInt(this.mPhone.getContext().getContentResolver(), "data_roaming" + sub, enabled ? 1 : 0);
        log("Set Data Roaming for SUB-" + sub + " is " + enabled);
        if (sub == MSimTelephonyManager.getDefault().getPreferredDataSubscription()) {
            this.mPhone.setDataRoamingEnabled(enabled);
            log("Set Data Roaming for DDS-" + sub + " is " + enabled);
        }
    }

    private boolean multiSimGetMobileData(int sub) {
        boolean enabled = Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), new StringBuilder().append("mobile_data").append(sub).toString(), 0) != 0;
        log("Get Mobile Data for SUB-" + sub + " is " + enabled);
        return enabled;
    }

    private void multiSimSetPreferredLte(boolean mode) {
        Message msg = this.mHandler.obtainMessage(3);
        if (MSimPhoneGlobals.getInstance().mPhoneServiceClient != null) {
            MSimPhoneGlobals.getInstance().setTDDDataOnly(this.mSubscription, mode, msg);
        }
    }

    private void multiSimSetMobileData(boolean z, int i) {
        Settings.Global.putInt(this.mPhone.getContext().getContentResolver(), "mobile_data" + i, z ? 1 : 0);
        log("Set Mobile Data for SUB-" + i + " is " + z);
        ((ConnectivityManager) getSystemService("connectivity")).setMobileDataEnabledOnSubscription(z, i);
        log("Set Mobile Data for DDS-" + i + " is " + z);
    }
}
