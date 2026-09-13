package com.android.phone;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioManager;
import android.net.sip.SipManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.MSimTelephonyManager;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import com.android.internal.telephony.Phone;
import com.android.phone.sip.SipSharedPreferences;
import com.codeaurora.telephony.msim.SubscriptionManager;
import java.util.Arrays;

/* JADX INFO: loaded from: classes.dex */
public class MSimCallFeaturesSetting extends PreferenceActivity implements Preference.OnPreferenceChangeListener {
    public static ListPreference ImsRegistration;
    private AudioManager mAudioManager;
    private CheckBoxPreference mButtonAutoRetry;
    private ListPreference mButtonDTMF;
    private CheckBoxPreference mButtonHAC;
    private CheckBoxPreference mButtonProximity;
    private ListPreference mButtonSipCallOptions;
    private ListPreference mButtonTTY;
    private PreferenceScreen mButtonXDivert;
    private PreferenceScreen mEmergencyCall;
    private boolean mForeground;
    private int mNumPhones;
    private Phone mPhone;
    private CheckBoxPreference mPlayDtmfTone;
    private CheckBoxPreference mShowDurationCheckBox;
    private SipManager mSipManager;
    private SipSharedPreferences mSipSharedPreferences;
    private SubscriptionManager mSubManager;
    private CheckBoxPreference mVibrateAfterConnected;

    @Override // android.preference.PreferenceActivity
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == this.mButtonDTMF) {
            return true;
        }
        if (preference == this.mPlayDtmfTone) {
            Settings.System.putInt(getContentResolver(), "dtmf_tone", this.mPlayDtmfTone.isChecked() ? 1 : 0);
            return true;
        }
        if (preference == this.mButtonTTY) {
            return true;
        }
        if (preference == this.mButtonAutoRetry) {
            Settings.Global.putInt(this.mPhone.getContext().getContentResolver(), "call_auto_retry", this.mButtonAutoRetry.isChecked() ? 1 : 0);
            return true;
        }
        if (preference == this.mButtonHAC) {
            int hac = this.mButtonHAC.isChecked() ? 1 : 0;
            Settings.System.putInt(this.mPhone.getContext().getContentResolver(), "hearing_aid", hac);
            this.mAudioManager.setParameter("HACSetting", hac != 0 ? "ON" : "OFF");
            return true;
        }
        if (preference != this.mButtonXDivert) {
            return false;
        }
        processXDivert();
        return true;
    }

    @Override // android.preference.Preference.OnPreferenceChangeListener
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        log("onPreferenceChange(). preferenece: \"" + preference + "\", value: \"" + objValue + "\"");
        if (preference == this.mButtonDTMF) {
            int index = this.mButtonDTMF.findIndexOfValue((String) objValue);
            Settings.System.putInt(this.mPhone.getContext().getContentResolver(), "dtmf_tone_type", index);
        } else if (preference == this.mButtonTTY) {
            handleTTYChange(preference, objValue);
        } else if (preference == this.mButtonProximity) {
            boolean checked = ((Boolean) objValue).booleanValue();
            Settings.System.putInt(this.mPhone.getContext().getContentResolver(), "proximity_sensor", checked ? 1 : 0);
            this.mButtonProximity.setSummary(checked ? R.string.proximity_on_summary : R.string.proximity_off_summary);
        } else if (preference == this.mShowDurationCheckBox) {
            boolean checked2 = ((Boolean) objValue).booleanValue();
            Settings.System.putInt(this.mPhone.getContext().getContentResolver(), "show_call_duration", checked2 ? 1 : 0);
            this.mShowDurationCheckBox.setSummary(checked2 ? R.string.duration_enable_summary : R.string.duration_disable_summary);
        } else if (preference == this.mVibrateAfterConnected) {
            boolean doVibrate = ((Boolean) objValue).booleanValue();
            Settings.System.putInt(this.mPhone.getContext().getContentResolver(), "vibrate_on_accepted", doVibrate ? 1 : 0);
        } else if (preference == ImsRegistration) {
            log("Update IMS Registration");
            String value = objValue == null ? "" : objValue.toString();
            ImsRegistration.setSummary(value);
            log("onPreferencechange value is " + value);
            ImsRegistration.setValue(value);
            PhoneGlobals.updateImsRegistration(ImsRegistration);
        } else if (preference == this.mButtonSipCallOptions) {
            handleSipCallOptionsChange(objValue);
        }
        return true;
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        log("onCreate(). Intent: " + getIntent());
        PhoneGlobals.getInstance();
        this.mPhone = PhoneGlobals.getPhone();
        addPreferencesFromResource(R.xml.msim_call_feature_setting);
        this.mAudioManager = (AudioManager) getSystemService("audio");
        this.mSubManager = SubscriptionManager.getInstance();
        PreferenceScreen prefSet = getPreferenceScreen();
        this.mPlayDtmfTone = (CheckBoxPreference) findPreference("button_play_dtmf_tone");
        this.mButtonDTMF = (ListPreference) findPreference("button_dtmf_settings");
        this.mButtonAutoRetry = (CheckBoxPreference) findPreference("button_auto_retry_key");
        this.mButtonHAC = (CheckBoxPreference) findPreference("button_hac_key");
        this.mButtonTTY = (ListPreference) findPreference("button_tty_mode_key");
        this.mButtonXDivert = (PreferenceScreen) findPreference("button_xdivert");
        this.mButtonProximity = (CheckBoxPreference) findPreference("button_proximity_key");
        this.mShowDurationCheckBox = (CheckBoxPreference) findPreference("duration_enable_key");
        this.mVibrateAfterConnected = (CheckBoxPreference) findPreference("button_vibrate_after_connected");
        ContentResolver contentResolver = getContentResolver();
        this.mEmergencyCall = (PreferenceScreen) findPreference("emergency_call_list");
        if (!getResources().getBoolean(R.bool.show_emergency_call_list)) {
            prefSet.removePreference(this.mEmergencyCall);
        }
        if (!getResources().getBoolean(R.bool.config_show_xdivert)) {
            prefSet.removePreference(this.mButtonXDivert);
        }
        if (this.mButtonProximity != null) {
            this.mButtonProximity.setOnPreferenceChangeListener(this);
        }
        if (this.mShowDurationCheckBox != null) {
            this.mShowDurationCheckBox.setOnPreferenceChangeListener(this);
        }
        if (this.mVibrateAfterConnected != null) {
            this.mVibrateAfterConnected.setOnPreferenceChangeListener(this);
        }
        if (this.mPlayDtmfTone != null) {
            this.mPlayDtmfTone.setChecked(Settings.System.getInt(contentResolver, "dtmf_tone", 1) != 0);
        }
        if (this.mButtonDTMF != null) {
            if (getResources().getBoolean(R.bool.dtmf_type_enabled)) {
                this.mButtonDTMF.setOnPreferenceChangeListener(this);
            } else {
                prefSet.removePreference(this.mButtonDTMF);
                this.mButtonDTMF = null;
            }
        }
        if (this.mButtonAutoRetry != null) {
            if (getResources().getBoolean(R.bool.auto_retry_enabled)) {
                this.mButtonAutoRetry.setOnPreferenceChangeListener(this);
            } else {
                prefSet.removePreference(this.mButtonAutoRetry);
                this.mButtonAutoRetry = null;
            }
        }
        if (this.mButtonHAC != null) {
            if (getResources().getBoolean(R.bool.hac_enabled)) {
                this.mButtonHAC.setOnPreferenceChangeListener(this);
            } else {
                prefSet.removePreference(this.mButtonHAC);
                this.mButtonHAC = null;
            }
        }
        if (this.mButtonTTY != null) {
            if (getResources().getBoolean(R.bool.tty_enabled)) {
                this.mButtonTTY.setOnPreferenceChangeListener(this);
            } else {
                prefSet.removePreference(this.mButtonTTY);
                this.mButtonTTY = null;
            }
        }
        createSipCallSettings();
        createImsSettings();
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        PreferenceScreen selectSub = (PreferenceScreen) findPreference("button_call_independent_serv");
        if (selectSub != null) {
            Intent intent = selectSub.getIntent();
            intent.putExtra("PACKAGE", "com.android.phone");
            intent.putExtra("TARGET_CLASS", "com.android.phone.MSimCallFeaturesSubSetting");
        }
        this.mNumPhones = MSimTelephonyManager.getDefault().getPhoneCount();
        if (this.mButtonXDivert != null) {
            this.mButtonXDivert.setOnPreferenceChangeListener(this);
        }
    }

    private boolean isAllSubActive() {
        for (int i = 0; i < this.mNumPhones; i++) {
            if (!this.mSubManager.isSubActive(i)) {
                return false;
            }
        }
        return true;
    }

    private boolean isAnySubCdma() {
        for (int i = 0; i < this.mNumPhones; i++) {
            Phone phone = MSimPhoneGlobals.getInstance().getPhone(i);
            if (phone.getPhoneType() == 2) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidLine1Number(String[] line1Numbers) {
        for (int i = 0; i < this.mNumPhones; i++) {
            if (TextUtils.isEmpty(line1Numbers[i])) {
                return false;
            }
        }
        return true;
    }

    private void processXDivert() {
        String[] strArr = new String[this.mNumPhones];
        for (int i = 0; i < this.mNumPhones; i++) {
            Phone phone = MSimPhoneGlobals.getInstance().getPhone(i);
            String line1Number = phone.getLine1Number();
            if (!TextUtils.isEmpty(line1Number)) {
                strArr[i] = PhoneNumberUtils.formatNumber(line1Number);
            }
            Log.d("MSimCallFeaturesSetting", "SUB:" + i + " phonetype = " + phone.getPhoneType() + " isSubActive = " + this.mSubManager.isSubActive(i) + " line1Number = " + strArr[i]);
        }
        if (!isAllSubActive()) {
            displayAlertDialog(R.string.xdivert_sub_absent);
            return;
        }
        if (isAnySubCdma()) {
            displayAlertDialog(R.string.xdivert_not_supported);
        } else {
            if (!isValidLine1Number(strArr)) {
                Intent intent = new Intent();
                intent.setClass(this, XDivertPhoneNumbers.class);
                startActivity(intent);
                return;
            }
            processXDivertCheckBox(strArr);
        }
    }

    private void displayAlertDialog(int resId) {
        new AlertDialog.Builder(this).setMessage(resId).setTitle(R.string.xdivert_title).setIcon(android.R.drawable.ic_dialog_alert).setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() { // from class: com.android.phone.MSimCallFeaturesSetting.2
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int whichButton) {
                Log.d("MSimCallFeaturesSetting", "X-Divert onClick");
            }
        }).show().setOnDismissListener(new DialogInterface.OnDismissListener() { // from class: com.android.phone.MSimCallFeaturesSetting.1
            @Override // android.content.DialogInterface.OnDismissListener
            public void onDismiss(DialogInterface dialog) {
                Log.d("MSimCallFeaturesSetting", "X-Divert onDismiss");
            }
        });
    }

    private void processXDivertCheckBox(String[] strArr) {
        Log.d("MSimCallFeaturesSetting", "processXDivertCheckBox line1Numbers = " + Arrays.toString(strArr));
        Intent intent = new Intent();
        intent.setClass(this, XDivertSetting.class);
        intent.putExtra("Line1Numbers", strArr);
        startActivity(intent);
    }

    private void createSipCallSettings() {
        if (PhoneUtils.isVoipSupported(this)) {
            this.mSipManager = SipManager.newInstance(this);
            this.mSipSharedPreferences = new SipSharedPreferences(this);
            addPreferencesFromResource(R.xml.sip_settings_category);
            this.mButtonSipCallOptions = getSipCallOptionPreference();
            this.mButtonSipCallOptions.setOnPreferenceChangeListener(this);
            this.mButtonSipCallOptions.setValueIndex(this.mButtonSipCallOptions.findIndexOfValue(this.mSipSharedPreferences.getSipCallOption()));
            this.mButtonSipCallOptions.setSummary(this.mButtonSipCallOptions.getEntry());
        }
    }

    private void createImsSettings() {
        addPreferencesFromResource(R.xml.ims_settings_category);
        ImsRegistration = (ListPreference) findPreference("ims_registration");
        ImsRegistration.setOnPreferenceChangeListener(this);
        if (PhoneGlobals.mImsService != null) {
            ImsRegistration.setEnabled(true);
            PhoneGlobals.loadImsRegistration(ImsRegistration, PhoneGlobals.getIMSRegistrationState());
        } else {
            ImsRegistration.setEnabled(true);
        }
    }

    private ListPreference getSipCallOptionPreference() {
        ListPreference wifiAnd3G = (ListPreference) findPreference("sip_call_options_key");
        ListPreference wifiOnly = (ListPreference) findPreference("sip_call_options_wifi_only_key");
        PreferenceGroup sipSettings = (PreferenceGroup) findPreference("sip_settings_category_key");
        if (SipManager.isSipWifiOnly(this)) {
            sipSettings.removePreference(wifiAnd3G);
            return wifiOnly;
        }
        sipSettings.removePreference(wifiOnly);
        return wifiAnd3G;
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        this.mForeground = true;
        if (isAirplaneModeOn()) {
            Preference sipSettings = findPreference("sip_settings_category_key");
            PreferenceScreen screen = getPreferenceScreen();
            int count = screen.getPreferenceCount();
            for (int i = 0; i < count; i++) {
                Preference pref = screen.getPreference(i);
                if (pref != sipSettings) {
                    pref.setEnabled(false);
                }
            }
            return;
        }
        if (this.mButtonDTMF != null) {
            int dtmf = Settings.System.getInt(getContentResolver(), "dtmf_tone_type", 0);
            this.mButtonDTMF.setValueIndex(dtmf);
        }
        if (this.mButtonAutoRetry != null) {
            int autoretry = Settings.Global.getInt(getContentResolver(), "call_auto_retry", 0);
            this.mButtonAutoRetry.setChecked(autoretry != 0);
        }
        if (this.mButtonHAC != null) {
            int hac = Settings.System.getInt(getContentResolver(), "hearing_aid", 0);
            this.mButtonHAC.setChecked(hac != 0);
        }
        if (this.mButtonTTY != null) {
            int settingsTtyMode = Settings.Secure.getInt(getContentResolver(), "preferred_tty_mode", 0);
            this.mButtonTTY.setValue(Integer.toString(settingsTtyMode));
            updatePreferredTtyModeSummary(settingsTtyMode);
        }
        if (this.mButtonXDivert != null && !isAllSubActive()) {
            this.mButtonXDivert.setEnabled(false);
        }
        if (this.mButtonProximity != null) {
            boolean checked = Settings.System.getInt(getContentResolver(), "proximity_sensor", 1) == 1;
            this.mButtonProximity.setChecked(checked);
            this.mButtonProximity.setSummary(checked ? R.string.proximity_on_summary : R.string.proximity_off_summary);
        }
        if (this.mShowDurationCheckBox != null) {
            boolean checked2 = Settings.System.getInt(getContentResolver(), "show_call_duration", 1) == 1;
            this.mShowDurationCheckBox.setChecked(checked2);
            this.mShowDurationCheckBox.setSummary(checked2 ? R.string.duration_enable_summary : R.string.duration_disable_summary);
        }
        if (this.mVibrateAfterConnected != null) {
            this.mVibrateAfterConnected.setChecked(Settings.System.getInt(getContentResolver(), "vibrate_on_accepted", 1) == 1);
        }
    }

    private boolean isAirplaneModeOn() {
        return Settings.System.getInt(getContentResolver(), "airplane_mode_on", 0) != 0;
    }

    private void handleTTYChange(Preference preference, Object objValue) {
        int buttonTtyMode = Integer.valueOf((String) objValue).intValue();
        int settingsTtyMode = Settings.Secure.getInt(getContentResolver(), "preferred_tty_mode", 0);
        log("handleTTYChange: requesting set TTY mode enable (TTY) to" + Integer.toString(buttonTtyMode));
        if (buttonTtyMode != settingsTtyMode) {
            switch (buttonTtyMode) {
                case 0:
                case 1:
                case 2:
                case 3:
                    Settings.Secure.putInt(getContentResolver(), "preferred_tty_mode", buttonTtyMode);
                    break;
                default:
                    buttonTtyMode = 0;
                    break;
            }
            this.mButtonTTY.setValue(Integer.toString(buttonTtyMode));
            updatePreferredTtyModeSummary(buttonTtyMode);
            Intent ttyModeChanged = new Intent("com.android.internal.telephony.cdma.intent.action.TTY_PREFERRED_MODE_CHANGE");
            ttyModeChanged.putExtra("ttyPreferredMode", buttonTtyMode);
            sendBroadcast(ttyModeChanged);
        }
    }

    private void handleSipCallOptionsChange(Object objValue) {
        String option = objValue.toString();
        this.mSipSharedPreferences.setSipCallOption(option);
        this.mButtonSipCallOptions.setValueIndex(this.mButtonSipCallOptions.findIndexOfValue(option));
        this.mButtonSipCallOptions.setSummary(this.mButtonSipCallOptions.getEntry());
    }

    private void updatePreferredTtyModeSummary(int TtyMode) {
        String[] txts = getResources().getStringArray(R.array.tty_mode_entries);
        switch (TtyMode) {
            case 0:
            case 1:
            case 2:
            case 3:
                this.mButtonTTY.setSummary(txts[TtyMode]);
                break;
            default:
                this.mButtonTTY.setEnabled(false);
                this.mButtonTTY.setSummary(txts[0]);
                break;
        }
    }

    private static void log(String msg) {
        Log.d("MSimCallFeaturesSetting", msg);
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId != 16908332) {
            return super.onOptionsItemSelected(item);
        }
        Intent intent = new Intent();
        intent.setClassName("com.android.dialer", "com.android.dialer.DialtactsActivity");
        intent.addFlags(67108864);
        startActivity(intent);
        finish();
        return true;
    }

    public static void goUpToTopLevelSetting(Activity activity) {
        Intent intent = new Intent(activity, (Class<?>) MSimCallFeaturesSetting.class);
        intent.setAction("android.intent.action.MAIN");
        intent.addFlags(67108864);
        activity.startActivity(intent);
        activity.finish();
    }
}
