package com.android.phone;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
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
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.UiccController;

/* JADX INFO: loaded from: classes.dex */
public class MobileNetworkSettings extends PreferenceActivity implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener, Preference.OnPreferenceChangeListener {
    private CheckBoxPreference mButtonDataEnabled;
    private CheckBoxPreference mButtonDataRoam;
    private ListPreference mButtonEnabledNetworks;
    private CheckBoxPreference mButtonPreferredLte;
    private ListPreference mButtonPreferredNetworkMode;
    CdmaOptions mCdmaOptions;
    private Preference mClickedPreference;
    private Context mContext;
    GsmUmtsOptions mGsmUmtsOptions;
    private MyHandler mHandler;
    private boolean mIsGlobalCdma;
    private Preference mLteDataServicePref;
    private boolean mOkClicked;
    private Phone mPhone;
    private BroadcastReceiver mReceiver = new BroadcastReceiver() { // from class: com.android.phone.MobileNetworkSettings.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("android.intent.action.AIRPLANE_MODE") || action.equals("android.intent.action.SIM_STATE_CHANGED")) {
                MobileNetworkSettings.this.setScreenState();
            }
        }
    };
    private boolean mShow4GForLTE;

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
        if (this.mGsmUmtsOptions != null && this.mGsmUmtsOptions.preferenceTreeClick(preference)) {
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
            int settingsNetworkMode = Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), "preferred_network_mode", 11);
            setPreferredNetworkModeValue(settingsNetworkMode);
            return true;
        }
        if (preference == this.mButtonDataRoam) {
            log("onPreferenceTreeClick: preference == mButtonDataRoam.");
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
            Log.e("NetworkSettings", "Missing SETUP_PREPAID_DATA_SERVICE_URL");
            return true;
        }
        if (preference == this.mButtonEnabledNetworks) {
            int settingsNetworkMode2 = Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), "preferred_network_mode", 11);
            this.mButtonEnabledNetworks.setValue(Integer.toString(settingsNetworkMode2));
            return true;
        }
        if (preference == this.mButtonPreferredLte) {
            Message msg = this.mHandler.obtainMessage(3);
            if (PhoneGlobals.getInstance().mPhoneServiceClient == null) {
                return true;
            }
            PhoneGlobals.getInstance().setTDDDataOnly(0, this.mButtonPreferredLte.isChecked(), msg);
            return true;
        }
        preferenceScreen.setEnabled(false);
        return false;
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onCreate(Bundle icicle) {
        Preference pref;
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.network_setting);
        this.mPhone = PhoneGlobals.getPhone();
        this.mHandler = new MyHandler();
        this.mContext = getApplicationContext();
        try {
            Context con = createPackageContext("com.android.systemui", 0);
            int id = con.getResources().getIdentifier("config_show4GForLTE", "bool", "com.android.systemui");
            this.mShow4GForLTE = con.getResources().getBoolean(id);
        } catch (PackageManager.NameNotFoundException e) {
            loge("NameNotFoundException for show4GFotLTE");
            this.mShow4GForLTE = false;
        }
        IntentFilter intentFilter = new IntentFilter("android.intent.action.AIRPLANE_MODE");
        intentFilter.addAction("android.intent.action.SIM_STATE_CHANGED");
        registerReceiver(this.mReceiver, intentFilter);
        PreferenceScreen prefSet = getPreferenceScreen();
        this.mButtonDataEnabled = (CheckBoxPreference) prefSet.findPreference("button_data_enabled_key");
        prefSet.removePreference(this.mButtonDataEnabled);
        this.mButtonDataRoam = (CheckBoxPreference) prefSet.findPreference("button_roaming_key");
        this.mButtonPreferredNetworkMode = (ListPreference) prefSet.findPreference("preferred_network_mode_key");
        this.mButtonEnabledNetworks = (ListPreference) prefSet.findPreference("enabled_networks_key");
        this.mLteDataServicePref = prefSet.findPreference("cdma_lte_data_service_key");
        this.mButtonPreferredLte = (CheckBoxPreference) prefSet.findPreference("toggle_preferred_lte");
        if (!getResources().getBoolean(R.bool.config_tdd_data_only)) {
            prefSet.removePreference(this.mButtonPreferredLte);
            this.mButtonPreferredLte = null;
        }
        int networkFeature = SystemProperties.getInt("persist.radio.network_feature", 0);
        TelephonyManager telephonyManager = (TelephonyManager) this.mContext.getSystemService("phone");
        String IMSI = null;
        boolean isTestCard = false;
        if (telephonyManager != null) {
            IMSI = telephonyManager.getSubscriberId();
        }
        Log.i("NetworkSettings", "IMSI: " + IMSI);
        if (IMSI != null && !IMSI.equals("") && IMSI.startsWith("00101")) {
            isTestCard = true;
        }
        Log.i("NetworkSettings", "isTestCard: " + isTestCard);
        if (isTestCard) {
            switch (networkFeature) {
                case 0:
                case 2:
                case 3:
                case 4:
                    Log.d("www", "default");
                    this.mButtonPreferredNetworkMode.setEntries(R.array.preferred_network_mode_choices);
                    this.mButtonPreferredNetworkMode.setEntryValues(R.array.preferred_network_mode_values);
                    break;
            }
        } else {
            switch (networkFeature) {
                case 0:
                    Log.d("www", "default");
                    this.mButtonPreferredNetworkMode.setEntries(R.array.preferred_network_mode_choices);
                    this.mButtonPreferredNetworkMode.setEntryValues(R.array.preferred_network_mode_values);
                    break;
                case 1:
                    prefSet.removePreference(this.mButtonPreferredNetworkMode);
                    break;
                case 2:
                    this.mButtonPreferredNetworkMode.setDialogTitle(R.string.preferred_network_mode_dialogtitle_cmcc);
                    if (getResources().getBoolean(R.bool.config_network_cmcc_feature)) {
                        if (UiccController.getInstance().getUiccCard() != null && UiccController.getInstance().getUiccCard().isApplicationOnIcc(IccCardApplicationStatus.AppType.APPTYPE_USIM)) {
                            Log.d("www", "cmcc");
                            this.mButtonPreferredNetworkMode.setEntries(R.array.preferred_network_mode_options_cmcc);
                            this.mButtonPreferredNetworkMode.setEntryValues(R.array.preferred_network_mode_options_values_cmcc);
                        } else {
                            prefSet.removePreference(this.mButtonPreferredNetworkMode);
                        }
                    } else {
                        Log.d("www", "option cmcc");
                        this.mButtonPreferredNetworkMode.setEntries(R.array.preferred_network_mode_choices_cmcc);
                        this.mButtonPreferredNetworkMode.setEntryValues(R.array.preferred_network_mode_values_cmcc);
                    }
                    break;
                case 3:
                    Log.d("www", "td cdma");
                    this.mButtonPreferredNetworkMode.setEntries(R.array.preferred_network_mode_choices_tdscdma);
                    this.mButtonPreferredNetworkMode.setEntryValues(R.array.preferred_network_mode_values_tdscdma);
                    break;
                case 4:
                    Log.d("www", "lte");
                    this.mButtonPreferredNetworkMode.setEntries(R.array.preferred_network_mode_choices_lte);
                    this.mButtonPreferredNetworkMode.setEntryValues(R.array.preferred_network_mode_values_lte);
                    break;
                case 5:
                    this.mButtonPreferredNetworkMode.setEntries(R.array.preferred_network_mode_choices_ct);
                    int default_network = SystemProperties.getInt("ro.telephony.default_network", 10);
                    Log.d("www", "default_network is " + default_network);
                    if (default_network == 8) {
                        Log.d("www", "Phone.NT_MODE_LTE_CDMA_AND_EVDO ");
                        this.mButtonPreferredNetworkMode.setEntryValues(R.array.preferred_network_mode_values_ct_common);
                    } else {
                        this.mButtonPreferredNetworkMode.setEntryValues(R.array.preferred_network_mode_values_ct);
                    }
                    break;
                case 6:
                    this.mButtonPreferredNetworkMode.setEntries(R.array.preferred_network_mode_choices_cu);
                    this.mButtonPreferredNetworkMode.setEntryValues(R.array.preferred_network_mode_values_cu);
                    break;
                case 7:
                    this.mButtonPreferredNetworkMode.setEntries(R.array.preferred_network_mode_choices_cmcc_notds);
                    this.mButtonPreferredNetworkMode.setEntryValues(R.array.preferred_network_mode_values_cmcc_notds);
                    break;
            }
        }
        if (!getResources().getBoolean(R.bool.config_uplmn_for_cta_test)) {
            Preference mUPLMNPref = prefSet.findPreference("button_uplmn_key");
            prefSet.removePreference(mUPLMNPref);
        }
        boolean isLteOnCdma = this.mPhone.getLteOnCdmaMode() == 1;
        this.mIsGlobalCdma = isLteOnCdma && getResources().getBoolean(R.bool.config_show_cdma);
        prefSet.removePreference(this.mButtonEnabledNetworks);
        if (getResources().getBoolean(R.bool.world_phone)) {
            this.mButtonPreferredNetworkMode.setOnPreferenceChangeListener(this);
            setPreferredNetworkModeValue(Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), "preferred_network_mode", 11));
            this.mCdmaOptions = new CdmaOptions(this, prefSet, this.mPhone);
            this.mGsmUmtsOptions = new GsmUmtsOptions(this, prefSet);
        } else {
            if (!isLteOnCdma) {
                prefSet.removePreference(this.mButtonPreferredNetworkMode);
            } else {
                this.mButtonPreferredNetworkMode.setOnPreferenceChangeListener(this);
                setPreferredNetworkModeValue(Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), "preferred_network_mode", 11));
            }
            int phoneType = this.mPhone.getPhoneType();
            if (phoneType == 2) {
                if (isLteOnCdma) {
                    this.mButtonEnabledNetworks.setEntries(R.array.enabled_networks_cdma_choices);
                    this.mButtonEnabledNetworks.setEntryValues(R.array.enabled_networks_cdma_values);
                }
                this.mCdmaOptions = new CdmaOptions(this, prefSet, this.mPhone);
            } else if (phoneType == 1) {
                if (!getResources().getBoolean(R.bool.config_prefer_2g) && !getResources().getBoolean(R.bool.config_enabled_lte)) {
                    this.mButtonEnabledNetworks.setEntries(R.array.enabled_networks_except_gsm_lte_choices);
                    this.mButtonEnabledNetworks.setEntryValues(R.array.enabled_networks_except_gsm_lte_values);
                } else if (!getResources().getBoolean(R.bool.config_prefer_2g)) {
                    int select = this.mShow4GForLTE ? R.array.enabled_networks_except_gsm_4g_choices : R.array.enabled_networks_except_gsm_choices;
                    this.mButtonEnabledNetworks.setEntries(select);
                    this.mButtonEnabledNetworks.setEntryValues(R.array.enabled_networks_except_gsm_values);
                } else if (!getResources().getBoolean(R.bool.config_enabled_lte)) {
                    this.mButtonEnabledNetworks.setEntries(R.array.enabled_networks_except_lte_choices);
                    this.mButtonEnabledNetworks.setEntryValues(R.array.enabled_networks_except_lte_values);
                } else if (this.mIsGlobalCdma) {
                    this.mButtonEnabledNetworks.setEntries(R.array.enabled_networks_cdma_choices);
                    this.mButtonEnabledNetworks.setEntryValues(R.array.enabled_networks_cdma_values);
                } else {
                    int select2 = this.mShow4GForLTE ? R.array.enabled_networks_4g_choices : R.array.enabled_networks_choices;
                    this.mButtonEnabledNetworks.setEntries(select2);
                    this.mButtonEnabledNetworks.setEntryValues(R.array.enabled_networks_values);
                }
                this.mGsmUmtsOptions = new GsmUmtsOptions(this, prefSet);
            } else {
                throw new IllegalStateException("Unexpected phone type: " + phoneType);
            }
            this.mButtonEnabledNetworks.setOnPreferenceChangeListener(this);
            int settingsNetworkMode = Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), "preferred_network_mode", 11);
            log("settingsNetworkMode: " + settingsNetworkMode);
            this.mButtonEnabledNetworks.setValue(Integer.toString(settingsNetworkMode));
        }
        boolean missingDataServiceUrl = TextUtils.isEmpty(Settings.Global.getString(getContentResolver(), "setup_prepaid_data_service_url"));
        if (!isLteOnCdma || missingDataServiceUrl) {
            prefSet.removePreference(this.mLteDataServicePref);
        } else {
            Log.d("NetworkSettings", "keep ltePref");
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
        if (getResources().getBoolean(R.bool.hide_roaming)) {
            prefSet.removePreference(this.mButtonDataRoam);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateButtonPreferredLte() {
        if (this.mButtonPreferredLte != null) {
            try {
                this.mButtonPreferredLte.setEnabled(PhoneUtils.isLTE(Settings.Global.getInt(getContentResolver(), "preferred_network_mode")));
                this.mButtonPreferredLte.setChecked(Settings.Global.getInt(getContentResolver(), "tdd_data_only_user_pref") == 1);
            } catch (Settings.SettingNotFoundException e) {
                Log.d("NetworkSettings", "failed to update lte button", e);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int getAcqValue() {
        try {
            int acq = Settings.Global.getInt(getContentResolver(), "network_acq");
            return acq;
        } catch (Settings.SettingNotFoundException e) {
            Log.d("NetworkSettings", "failed to get acq", e);
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
            Log.d("NetworkSettings", networkmodeString);
            this.mButtonPreferredNetworkMode.setValue(networkmodeString);
            return;
        }
        this.mButtonPreferredNetworkMode.setValue(Integer.toString(settingsNetworkMode));
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        setScreenState();
        ConnectivityManager cm = (ConnectivityManager) getSystemService("connectivity");
        this.mButtonDataEnabled.setChecked(cm.getMobileDataEnabled());
        this.mButtonDataRoam.setChecked(this.mPhone.getDataRoamingEnabled());
        updateButtonPreferredLte();
        if (getPreferenceScreen().findPreference("preferred_network_mode_key") != null) {
            this.mPhone.getPreferredNetworkType(this.mHandler.obtainMessage(0));
        }
        if (getPreferenceScreen().findPreference("enabled_networks_key") != null) {
            this.mPhone.getPreferredNetworkType(this.mHandler.obtainMessage(0));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setScreenState() {
        int simState = TelephonyManager.getDefault().getSimState();
        getPreferenceScreen().setEnabled(simState != 1);
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
            int settingsNetworkMode = Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), "preferred_network_mode", 11);
            int buttonAcq = Integer.valueOf(strAcq).intValue();
            int settingsAcq = getAcqValue();
            if (buttonNetworkMode != settingsNetworkMode || buttonAcq != settingsAcq) {
                switch (buttonNetworkMode) {
                    case 0:
                    case 1:
                    case 2:
                    case 3:
                    case 4:
                    case 5:
                    case 6:
                    case 7:
                    case 8:
                    case 9:
                    case 10:
                    case 11:
                    case 12:
                    case 13:
                    case 14:
                    case 15:
                    case 16:
                    case 17:
                    case 18:
                    case 19:
                    case 20:
                    case 21:
                    case 22:
                        UpdatePreferredNetworkModeSummary(buttonNetworkMode, buttonAcq);
                        Settings.Global.putInt(this.mPhone.getContext().getContentResolver(), "preferred_network_mode", buttonNetworkMode);
                        setPreferredNetworkType(isContainAcq, buttonNetworkMode, strAcq);
                        break;
                    default:
                        loge("Invalid Network Mode (" + buttonNetworkMode + ") chosen. Ignore.");
                        break;
                }
            }
        } else if (preference == this.mButtonEnabledNetworks) {
            this.mButtonEnabledNetworks.setValue((String) objValue);
            int buttonNetworkMode2 = Integer.valueOf((String) objValue).intValue();
            log("buttonNetworkMode: " + buttonNetworkMode2);
            int settingsNetworkMode2 = Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), "preferred_network_mode", 11);
            if (buttonNetworkMode2 != settingsNetworkMode2) {
                switch (buttonNetworkMode2) {
                    case 0:
                    case 1:
                    case 4:
                    case 5:
                    case 8:
                    case 9:
                    case 10:
                        UpdateEnabledNetworksValueAndSummary(buttonNetworkMode2);
                        Settings.Global.putInt(this.mPhone.getContext().getContentResolver(), "preferred_network_mode", buttonNetworkMode2);
                        this.mPhone.setPreferredNetworkType(buttonNetworkMode2, this.mHandler.obtainMessage(1));
                        break;
                    case 2:
                    case 3:
                    case 6:
                    case 7:
                    default:
                        loge("Invalid Network Mode (" + buttonNetworkMode2 + ") chosen. Ignore.");
                        break;
                }
            }
        }
        return true;
    }

    private void setPreferredNetworkType(boolean containAcq, int networkMode, String strAcq) {
        Message msg;
        if (PhoneGlobals.getInstance().mPhoneServiceClient != null) {
            msg = this.mHandler.obtainMessage(2);
        } else {
            msg = this.mHandler.obtainMessage(1);
        }
        if (containAcq) {
            int acq = Integer.valueOf(strAcq).intValue();
            if (PhoneGlobals.getInstance().mPhoneServiceClient != null) {
                PhoneGlobals.getInstance().setPrefNetworWithAcq(0, networkMode, acq, msg);
                return;
            } else {
                this.mPhone.setPreferredNetworkType(networkMode, this.mHandler.obtainMessage(1));
                return;
            }
        }
        if (PhoneGlobals.getInstance().mPhoneServiceClient != null) {
            PhoneGlobals.getInstance().setPrefNetwork(0, networkMode, msg);
        } else {
            this.mPhone.setPreferredNetworkType(networkMode, this.mHandler.obtainMessage(1));
        }
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
            MobileNetworkSettings.this.updateButtonPreferredLte();
        }

        private void handleSetPreferredNetworkTypeWithAcqResponse() {
            if (MobileNetworkSettings.this.mButtonPreferredNetworkMode != null) {
                MobileNetworkSettings.this.UpdatePreferredNetworkModeSummary(Settings.Global.getInt(MobileNetworkSettings.this.mPhone.getContext().getContentResolver(), "preferred_network_mode", 11), MobileNetworkSettings.this.getAcqValue());
            }
            MobileNetworkSettings.this.updateButtonPreferredLte();
        }

        private void handleGetPreferredNetworkTypeResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            if (ar.exception == null) {
                int modemNetworkMode = ((int[]) ar.result)[0];
                MobileNetworkSettings.log("handleGetPreferredNetworkTypeResponse: modemNetworkMode = " + modemNetworkMode);
                int settingsNetworkMode = Settings.Global.getInt(MobileNetworkSettings.this.mPhone.getContext().getContentResolver(), "preferred_network_mode", 11);
                MobileNetworkSettings.log("handleGetPreferredNetworkTypeReponse: settingsNetworkMode = " + settingsNetworkMode);
                if (modemNetworkMode == 0 || modemNetworkMode == 1 || modemNetworkMode == 2 || modemNetworkMode == 3 || modemNetworkMode == 4 || modemNetworkMode == 5 || modemNetworkMode == 6 || modemNetworkMode == 7 || modemNetworkMode == 8 || modemNetworkMode == 9 || modemNetworkMode == 10 || modemNetworkMode == 11 || modemNetworkMode == 12 || modemNetworkMode == 13 || modemNetworkMode == 14 || modemNetworkMode == 15 || modemNetworkMode == 16 || modemNetworkMode == 17 || modemNetworkMode == 18 || modemNetworkMode == 19 || modemNetworkMode == 20 || modemNetworkMode == 21 || modemNetworkMode == 22) {
                    MobileNetworkSettings.log("handleGetPreferredNetworkTypeResponse: if 1: modemNetworkMode = " + modemNetworkMode);
                    if (modemNetworkMode != settingsNetworkMode) {
                        MobileNetworkSettings.log("handleGetPreferredNetworkTypeResponse: if 2: modemNetworkMode != settingsNetworkMode");
                        MobileNetworkSettings.log("handleGetPreferredNetworkTypeResponse: if 2: settingsNetworkMode = " + modemNetworkMode);
                        Settings.Global.putInt(MobileNetworkSettings.this.mPhone.getContext().getContentResolver(), "preferred_network_mode", modemNetworkMode);
                    }
                    int acq = MobileNetworkSettings.this.getAcqValue();
                    if (MobileNetworkSettings.this.mButtonPreferredNetworkMode != null) {
                        MobileNetworkSettings.this.UpdatePreferredNetworkModeSummary(modemNetworkMode, acq);
                        MobileNetworkSettings.this.setPreferredNetworkModeValue(modemNetworkMode);
                    } else if (MobileNetworkSettings.this.mButtonEnabledNetworks != null) {
                        MobileNetworkSettings.this.UpdateEnabledNetworksValueAndSummary(modemNetworkMode);
                        MobileNetworkSettings.this.mButtonEnabledNetworks.setValue(Integer.toString(modemNetworkMode));
                    }
                } else {
                    MobileNetworkSettings.log("handleGetPreferredNetworkTypeResponse: else: reset to default");
                    resetNetworkModeToDefault();
                }
                MobileNetworkSettings.this.updateButtonPreferredLte();
            }
        }

        private void handleSetPreferredNetworkTypeResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            if (ar.exception == null) {
                if (MobileNetworkSettings.this.mButtonPreferredNetworkMode != null) {
                    int networkMode = Integer.valueOf(MobileNetworkSettings.this.mButtonPreferredNetworkMode.getValue()).intValue();
                    Settings.Global.putInt(MobileNetworkSettings.this.mPhone.getContext().getContentResolver(), "preferred_network_mode", networkMode);
                } else if (MobileNetworkSettings.this.mButtonEnabledNetworks != null) {
                    int networkMode2 = Integer.valueOf(MobileNetworkSettings.this.mButtonEnabledNetworks.getValue()).intValue();
                    Settings.Global.putInt(MobileNetworkSettings.this.mPhone.getContext().getContentResolver(), "preferred_network_mode", networkMode2);
                }
                MobileNetworkSettings.this.updateButtonPreferredLte();
                return;
            }
            MobileNetworkSettings.this.mPhone.getPreferredNetworkType(obtainMessage(0));
        }

        private void resetNetworkModeToDefault() {
            MobileNetworkSettings.this.setPreferredNetworkModeValue(11);
            MobileNetworkSettings.this.mButtonEnabledNetworks.setValue(Integer.toString(11));
            Settings.Global.putInt(MobileNetworkSettings.this.mPhone.getContext().getContentResolver(), "preferred_network_mode", 11);
            MobileNetworkSettings.this.mPhone.setPreferredNetworkType(11, obtainMessage(1));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void UpdatePreferredNetworkModeSummary(int i, int i2) {
        int i3 = SystemProperties.getInt("persist.radio.network_feature", 0);
        int i4 = SystemProperties.getInt("ro.telephony.default_network", 10);
        Log.d("NetworkSettings", "default_network is " + i4);
        Log.d("NetworkSettings", "NetworkMode is " + i);
        switch (i) {
            case 0:
                this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_wcdma_perf_summary);
                break;
            case 1:
                this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_gsm_only_summary);
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
                if (i4 == 10) {
                    Log.d("www", "Phone.NT_MODE_LTE_CMDA_EVDO_GSM_WCDMA ");
                    this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_global_summary_10);
                } else {
                    this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_global_summary);
                }
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
                if (i3 == 2) {
                    this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_3g_2g_auto_summary);
                } else if (i3 == 4) {
                    this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_4g_3g_2g_td);
                } else {
                    this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_td_scdma_gsm_wcdma_summary);
                }
                break;
            case 19:
                this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_td_scdma_wcdma_lte_summary);
                break;
            case 20:
                if (i3 == 2) {
                    if (getResources().getBoolean(R.bool.config_network_cmcc_feature)) {
                        this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_4g_3g_2g_auto_summary);
                    } else if (i2 == 1) {
                        this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_4g_3g_2g_4g);
                    } else if (i2 == 2) {
                        this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_4g_3g_2g_3g);
                    } else {
                        this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_4g_3g_2g_auto_summary);
                    }
                } else if (i3 == 4) {
                    if (i2 == 1) {
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
                if (i4 == 10) {
                    this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_global_summary_10);
                } else {
                    this.mButtonPreferredNetworkMode.setSummary(R.string.preferred_network_mode_global_summary);
                }
                break;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void UpdateEnabledNetworksValueAndSummary(int i) {
        int i2 = R.string.network_lte;
        switch (i) {
            case 0:
            case 2:
            case 3:
                if (!this.mIsGlobalCdma) {
                    this.mButtonEnabledNetworks.setValue(Integer.toString(0));
                    this.mButtonEnabledNetworks.setSummary(R.string.network_3G);
                } else {
                    this.mButtonEnabledNetworks.setValue(Integer.toString(10));
                    this.mButtonEnabledNetworks.setSummary(R.string.network_global);
                }
                break;
            case 1:
                if (!this.mIsGlobalCdma) {
                    this.mButtonEnabledNetworks.setValue(Integer.toString(1));
                    this.mButtonEnabledNetworks.setSummary(R.string.network_2G);
                } else {
                    this.mButtonEnabledNetworks.setValue(Integer.toString(10));
                    this.mButtonEnabledNetworks.setSummary(R.string.network_global);
                }
                break;
            case 4:
            case 6:
            case 7:
                this.mButtonEnabledNetworks.setValue(Integer.toString(4));
                this.mButtonEnabledNetworks.setSummary(R.string.network_3G);
                break;
            case 5:
                this.mButtonEnabledNetworks.setValue(Integer.toString(5));
                this.mButtonEnabledNetworks.setSummary(R.string.network_1x);
                break;
            case 8:
                this.mButtonEnabledNetworks.setValue(Integer.toString(8));
                this.mButtonEnabledNetworks.setSummary(R.string.network_lte);
                break;
            case 9:
            case 11:
            case 12:
                if (!this.mIsGlobalCdma) {
                    this.mButtonEnabledNetworks.setValue(Integer.toString(9));
                    ListPreference listPreference = this.mButtonEnabledNetworks;
                    if (this.mShow4GForLTE) {
                        i2 = R.string.network_4G;
                    }
                    listPreference.setSummary(i2);
                } else {
                    this.mButtonEnabledNetworks.setValue(Integer.toString(10));
                    this.mButtonEnabledNetworks.setSummary(R.string.network_global);
                }
                break;
            case 10:
                this.mButtonEnabledNetworks.setValue(Integer.toString(10));
                this.mButtonEnabledNetworks.setSummary(R.string.network_global);
                break;
            default:
                String str = "Invalid Network Mode (" + i + "). Ignore.";
                loge(str);
                this.mButtonEnabledNetworks.setSummary(str);
                break;
        }
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onActivityResult(int i, int i2, Intent intent) {
        switch (i) {
            case 17:
                if (Boolean.valueOf(intent.getBooleanExtra("exit_ecm_result", false)).booleanValue()) {
                    this.mCdmaOptions.showDialog(this.mClickedPreference);
                }
                break;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void log(String msg) {
        Log.d("NetworkSettings", msg);
    }

    private static void loge(String msg) {
        Log.e("NetworkSettings", msg);
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() != 16908332) {
            return super.onOptionsItemSelected(menuItem);
        }
        finish();
        return true;
    }
}
