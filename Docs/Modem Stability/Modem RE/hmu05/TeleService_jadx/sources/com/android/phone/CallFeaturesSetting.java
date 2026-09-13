package com.android.phone;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.net.sip.SipManager;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.os.Vibrator;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListAdapter;
import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.Phone;
import com.android.phone.sip.SipSharedPreferences;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public class CallFeaturesSetting extends PreferenceActivity implements DialogInterface.OnClickListener, Preference.OnPreferenceChangeListener, EditPhoneNumberPreference.GetDefaultNumberListener, EditPhoneNumberPreference.OnDialogClosedListener {
    public static ListPreference ImsRegistration;
    private AudioManager mAudioManager;
    private CheckBoxPreference mButtonAutoRetry;
    private ListPreference mButtonDTMF;
    private CheckBoxPreference mButtonHAC;
    private CheckBoxPreference mButtonProximity;
    private ListPreference mButtonSipCallOptions;
    private ListPreference mButtonTTY;
    private Intent mContactListIntent;
    private PreferenceScreen mEmergencyCall;
    private boolean mForeground;
    private PreferenceScreen mIPPrefix;
    private CallForwardInfo[] mNewFwdSettings;
    private String mNewVMNumber;
    private String mOldVmNumber;
    private SharedPreferences mPerProviderSavedVMNumbers;
    private Phone mPhone;
    private CheckBoxPreference mPlayDtmfTone;
    private Runnable mRingtoneLookupRunnable;
    private Preference mRingtonePreference;
    private CheckBoxPreference mShowDurationCheckBox;
    private SipManager mSipManager;
    private SipSharedPreferences mSipSharedPreferences;
    private EditPhoneNumberPreference mSubMenuVoicemailSettings;
    private CheckBoxPreference mVibrateAfterConnected;
    private CheckBoxPreference mVibrateWhenRinging;
    private PreferenceScreen mVoicemailCategory;
    private Preference mVoicemailNotificationRingtone;
    private CheckBoxPreference mVoicemailNotificationVibrate;
    private ListPreference mVoicemailProviders;
    private PreferenceScreen mVoicemailSettings;
    public static final CallForwardInfo[] FWD_SETTINGS_DONT_TOUCH = null;
    private static final String[] NUM_PROJECTION = {"data1"};
    private static final int[] FORWARDING_SETTINGS_REASONS = {0, 1, 2, 3, 6};
    private final Handler mRingtoneLookupComplete = new Handler() { // from class: com.android.phone.CallFeaturesSetting.1
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    CallFeaturesSetting.this.mRingtonePreference.setSummary((CharSequence) msg.obj);
                    break;
                case 2:
                    CallFeaturesSetting.this.mVoicemailNotificationRingtone.setSummary((CharSequence) msg.obj);
                    break;
            }
        }
    };
    private boolean isSpeedDialListStarted = false;
    private CallForwardInfo[] mForwardingReadResults = null;
    private Map<Integer, AsyncResult> mForwardingChangeResults = null;
    private Collection<Integer> mExpectedChangeResultReasons = null;
    private AsyncResult mVoicemailChangeResult = null;
    private String mPreviousVMProviderKey = null;
    private int mCurrentDialogId = 0;
    private boolean mVMProviderSettingsForced = false;
    private boolean mChangingVMorFwdDueToProviderChange = false;
    private boolean mVMChangeCompletedSuccessfully = false;
    private boolean mFwdChangesRequireRollback = false;
    private int mVMOrFwdSetError = 0;
    private final Map<String, VoiceMailProvider> mVMProvidersData = new HashMap();
    private boolean mReadingSettingsForDefaultProvider = false;
    private final Handler mGetOptionComplete = new Handler() { // from class: com.android.phone.CallFeaturesSetting.4
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            AsyncResult result = (AsyncResult) msg.obj;
            switch (msg.what) {
                case 502:
                    CallFeaturesSetting.this.handleForwardingSettingsReadResult(result, msg.arg1);
                    break;
            }
        }
    };
    private final Handler mSetOptionComplete = new Handler() { // from class: com.android.phone.CallFeaturesSetting.5
        /* JADX WARN: Code duplicated, block: B:31:0x0113  */
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            AsyncResult result = (AsyncResult) msg.obj;
            boolean done = false;
            switch (msg.what) {
                case 500:
                    CallFeaturesSetting.this.mVoicemailChangeResult = result;
                    CallFeaturesSetting.this.mVMChangeCompletedSuccessfully = CallFeaturesSetting.this.checkVMChangeSuccess() == null;
                    CallFeaturesSetting.log("VM change complete msg, VM change done = " + String.valueOf(CallFeaturesSetting.this.mVMChangeCompletedSuccessfully));
                    done = true;
                    break;
                case 501:
                    CallFeaturesSetting.this.mForwardingChangeResults.put(Integer.valueOf(msg.arg1), result);
                    if (result.exception == null) {
                        CallFeaturesSetting.log("Success in setting fwd# " + msg.arg1);
                    } else {
                        Log.w("CallFeaturesSetting", "Error in setting fwd# " + msg.arg1 + ": " + result.exception.getMessage());
                    }
                    boolean completed = CallFeaturesSetting.this.checkForwardingCompleted();
                    if (completed) {
                        if (CallFeaturesSetting.this.checkFwdChangeSuccess() == null) {
                            CallFeaturesSetting.log("Overall fwd changes completed ok, starting vm change");
                            CallFeaturesSetting.this.setVMNumberWithCarrier();
                        } else {
                            Log.w("CallFeaturesSetting", "Overall fwd changes completed in failure. Check if we need to try rollback for some settings.");
                            CallFeaturesSetting.this.mFwdChangesRequireRollback = false;
                            for (Map.Entry<Integer, AsyncResult> entry : CallFeaturesSetting.this.mForwardingChangeResults.entrySet()) {
                                if (entry.getValue().exception == null) {
                                    Log.i("CallFeaturesSetting", "Rollback will be required");
                                    CallFeaturesSetting.this.mFwdChangesRequireRollback = true;
                                    if (!CallFeaturesSetting.this.mFwdChangesRequireRollback) {
                                        Log.i("CallFeaturesSetting", "No rollback needed.");
                                    }
                                    done = true;
                                    break;
                                }
                            }
                            if (!CallFeaturesSetting.this.mFwdChangesRequireRollback) {
                                Log.i("CallFeaturesSetting", "No rollback needed.");
                            }
                            done = true;
                        }
                    }
                    break;
            }
            if (done) {
                CallFeaturesSetting.log("All VM provider related changes done");
                if (CallFeaturesSetting.this.mForwardingChangeResults != null) {
                    CallFeaturesSetting.this.dismissDialogSafely(601);
                }
                CallFeaturesSetting.this.handleSetVMOrFwdMessage();
            }
        }
    };
    private final Handler mRevertOptionComplete = new Handler() { // from class: com.android.phone.CallFeaturesSetting.6
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            AsyncResult result = (AsyncResult) msg.obj;
            switch (msg.what) {
                case 500:
                    CallFeaturesSetting.this.mVoicemailChangeResult = result;
                    CallFeaturesSetting.log("VM revert complete msg");
                    break;
                case 501:
                    CallFeaturesSetting.this.mForwardingChangeResults.put(Integer.valueOf(msg.arg1), result);
                    if (result.exception != null) {
                        CallFeaturesSetting.log("Error in reverting fwd# " + msg.arg1 + ": " + result.exception.getMessage());
                    } else {
                        CallFeaturesSetting.log("Success in reverting fwd# " + msg.arg1);
                    }
                    CallFeaturesSetting.log("FWD revert complete msg ");
                    break;
            }
            boolean done = !(CallFeaturesSetting.this.mVMChangeCompletedSuccessfully && CallFeaturesSetting.this.mVoicemailChangeResult == null) && (!CallFeaturesSetting.this.mFwdChangesRequireRollback || CallFeaturesSetting.this.checkForwardingCompleted());
            if (done) {
                CallFeaturesSetting.log("All VM reverts done");
                CallFeaturesSetting.this.dismissDialogSafely(603);
                CallFeaturesSetting.this.onRevertDone();
            }
        }
    };

    private class VoiceMailProvider {
        public Intent intent;
        public String name;

        public VoiceMailProvider(String name, Intent intent) {
            this.name = name;
            this.intent = intent;
        }
    }

    private class VoiceMailProviderSettings {
        public CallForwardInfo[] forwardingSettings;
        public String voicemailNumber;

        public VoiceMailProviderSettings(String voicemailNumber, String forwardingNumber, int timeSeconds) {
            this.voicemailNumber = voicemailNumber;
            if (forwardingNumber != null && forwardingNumber.length() != 0) {
                this.forwardingSettings = new CallForwardInfo[CallFeaturesSetting.FORWARDING_SETTINGS_REASONS.length];
                for (int i = 0; i < this.forwardingSettings.length; i++) {
                    CallForwardInfo fi = new CallForwardInfo();
                    this.forwardingSettings[i] = fi;
                    fi.reason = CallFeaturesSetting.FORWARDING_SETTINGS_REASONS[i];
                    fi.status = fi.reason == 0 ? 0 : 1;
                    fi.serviceClass = 1;
                    fi.toa = 145;
                    fi.number = forwardingNumber;
                    fi.timeSeconds = timeSeconds;
                }
                return;
            }
            this.forwardingSettings = CallFeaturesSetting.FWD_SETTINGS_DONT_TOUCH;
        }

        public VoiceMailProviderSettings(String voicemailNumber, CallForwardInfo[] infos) {
            this.voicemailNumber = voicemailNumber;
            this.forwardingSettings = infos;
        }

        public boolean equals(Object o) {
            if (o == null || !(o instanceof VoiceMailProviderSettings)) {
                return false;
            }
            VoiceMailProviderSettings v = (VoiceMailProviderSettings) o;
            return ((this.voicemailNumber == null && v.voicemailNumber == null) || (this.voicemailNumber != null && this.voicemailNumber.equals(v.voicemailNumber))) && forwardingSettingsEqual(this.forwardingSettings, v.forwardingSettings);
        }

        private boolean forwardingSettingsEqual(CallForwardInfo[] infos1, CallForwardInfo[] infos2) {
            if (infos1 == infos2) {
                return true;
            }
            if (infos1 == null || infos2 == null) {
                return false;
            }
            if (infos1.length != infos2.length) {
                return false;
            }
            for (int i = 0; i < infos1.length; i++) {
                CallForwardInfo i1 = infos1[i];
                CallForwardInfo i2 = infos2[i];
                if (i1.status != i2.status || i1.reason != i2.reason || i1.serviceClass != i2.serviceClass || i1.toa != i2.toa || i1.number != i2.number || i1.timeSeconds != i2.timeSeconds) {
                    return false;
                }
            }
            return true;
        }

        public String toString() {
            return this.voicemailNumber + (this.forwardingSettings != null ? ", " + this.forwardingSettings.toString() : "");
        }
    }

    @Override // android.app.Activity
    public void onPause() {
        super.onPause();
        this.mForeground = false;
    }

    @Override // android.preference.PreferenceActivity
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == this.mIPPrefix) {
            View v = getLayoutInflater().inflate(R.layout.ip_prefix, (ViewGroup) null);
            final EditText edit = (EditText) v.findViewById(R.id.ip_prefix_dialog_edit);
            String ip_prefix = Settings.System.getString(getContentResolver(), "ip_call_prefix_sub1");
            edit.setText(ip_prefix);
            new AlertDialog.Builder(this).setTitle(R.string.ipcall_dialog_title).setIcon(android.R.drawable.ic_dialog_info).setView(v).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() { // from class: com.android.phone.CallFeaturesSetting.2
                @Override // android.content.DialogInterface.OnClickListener
                public void onClick(DialogInterface dialog, int which) {
                    String ip_prefix2 = edit.getText().toString();
                    Settings.System.putString(CallFeaturesSetting.this.getContentResolver(), "ip_call_prefix_sub1", ip_prefix2);
                    if (TextUtils.isEmpty(ip_prefix2)) {
                        CallFeaturesSetting.this.mIPPrefix.setSummary(R.string.ipcall_sub_summery);
                    } else {
                        CallFeaturesSetting.this.mIPPrefix.setSummary(edit.getText());
                    }
                    CallFeaturesSetting.this.onResume();
                }
            }).setNegativeButton(android.R.string.cancel, (DialogInterface.OnClickListener) null).show();
            return true;
        }
        if (preference == this.mSubMenuVoicemailSettings) {
            return true;
        }
        if (preference == this.mPlayDtmfTone) {
            Settings.System.putInt(getContentResolver(), "dtmf_tone", this.mPlayDtmfTone.isChecked() ? 1 : 0);
        } else {
            if (preference == this.mButtonDTMF) {
                return true;
            }
            if (preference == this.mButtonTTY) {
                if (!PhoneUtils.isImsVtCallPresent()) {
                    return true;
                }
                showDialog(800);
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
            if (preference == this.mVoicemailCategory) {
                Dialog voicemailDialog = this.mVoicemailCategory.getDialog();
                if (voicemailDialog == null) {
                    return true;
                }
                voicemailDialog.setOnKeyListener(new DialogInterface.OnKeyListener() { // from class: com.android.phone.CallFeaturesSetting.3
                    @Override // android.content.DialogInterface.OnKeyListener
                    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                        if (keyCode == 4 && CallFeaturesSetting.this.isSpeedDialListStarted && "com.android.phone.CallFeaturesSetting.ADD_VOICEMAIL".equals(CallFeaturesSetting.this.getIntent().getAction())) {
                            CallFeaturesSetting.this.isSpeedDialListStarted = false;
                            CallFeaturesSetting.this.finish();
                        }
                        return false;
                    }
                });
                return true;
            }
            if (preference == this.mVoicemailSettings) {
                log("onPreferenceTreeClick: Voicemail Settings Preference is clicked.");
                if (preference.getIntent() != null) {
                    log("onPreferenceTreeClick: Invoking cfg intent " + preference.getIntent().getPackage());
                    startActivityForResult(preference.getIntent(), 2);
                    return true;
                }
                log("onPreferenceTreeClick: No Intent is available. Use default behavior defined in xml.");
                this.mPreviousVMProviderKey = "";
                this.mVMProviderSettingsForced = false;
                return false;
            }
        }
        return false;
    }

    @Override // android.preference.Preference.OnPreferenceChangeListener
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        log("onPreferenceChange(). preferenece: \"" + preference + "\", value: \"" + objValue + "\"");
        if (preference == this.mVibrateWhenRinging) {
            boolean doVibrate = ((Boolean) objValue).booleanValue();
            Settings.System.putInt(this.mPhone.getContext().getContentResolver(), "vibrate_when_ringing", doVibrate ? 1 : 0);
        } else if (preference == this.mButtonDTMF) {
            int index = this.mButtonDTMF.findIndexOfValue((String) objValue);
            Settings.System.putInt(this.mPhone.getContext().getContentResolver(), "dtmf_tone_type", index);
        } else if (preference == this.mShowDurationCheckBox) {
            boolean checked = ((Boolean) objValue).booleanValue();
            Settings.System.putInt(this.mPhone.getContext().getContentResolver(), "show_call_duration", checked ? 1 : 0);
            this.mShowDurationCheckBox.setSummary(checked ? R.string.duration_enable_summary : R.string.duration_disable_summary);
        } else if (preference == this.mButtonTTY) {
            handleTTYChange(preference, objValue);
        } else if (preference == this.mButtonProximity) {
            boolean checked2 = ((Boolean) objValue).booleanValue();
            Settings.System.putInt(this.mPhone.getContext().getContentResolver(), "proximity_sensor", checked2 ? 1 : 0);
            this.mButtonProximity.setSummary(checked2 ? R.string.proximity_on_summary : R.string.proximity_off_summary);
        } else if (preference == this.mVibrateAfterConnected) {
            boolean doVibrate2 = ((Boolean) objValue).booleanValue();
            Settings.System.putInt(this.mPhone.getContext().getContentResolver(), "vibrate_on_accepted", doVibrate2 ? 1 : 0);
        } else if (preference == ImsRegistration) {
            log("Update IMS Registration");
            String value = objValue == null ? "" : objValue.toString();
            ImsRegistration.setSummary(value);
            log("onPreferencechange value is " + value);
            ImsRegistration.setValue(value);
            PhoneGlobals.updateImsRegistration(ImsRegistration);
        } else if (preference == this.mVoicemailProviders) {
            String newProviderKey = (String) objValue;
            log("Voicemail Provider changes from \"" + this.mPreviousVMProviderKey + "\" to \"" + newProviderKey + "\".");
            if (this.mPreviousVMProviderKey.equals(newProviderKey)) {
                log("No change is made toward VM provider setting.");
            } else {
                updateVMPreferenceWidgets(newProviderKey);
                VoiceMailProviderSettings newProviderSettings = loadSettingsForVoiceMailProvider(newProviderKey);
                if (newProviderSettings == null) {
                    Log.w("CallFeaturesSetting", "Saved preferences not found - invoking config");
                    this.mVMProviderSettingsForced = true;
                    simulatePreferenceClick(this.mVoicemailSettings);
                } else {
                    log("Saved preferences found - switching to them");
                    this.mChangingVMorFwdDueToProviderChange = true;
                    saveVoiceMailAndForwardingNumber(newProviderKey, newProviderSettings);
                }
            }
        } else if (preference == this.mButtonSipCallOptions) {
            handleSipCallOptionsChange(objValue);
        }
        return true;
    }

    @Override // com.android.phone.EditPhoneNumberPreference.OnDialogClosedListener
    public void onDialogClosed(EditPhoneNumberPreference preference, int buttonClicked) {
        log("onPreferenceClick: request preference click on dialog close: " + buttonClicked);
        if (buttonClicked != -2 && preference == this.mSubMenuVoicemailSettings) {
            handleVMBtnClickRequest();
        }
    }

    @Override // com.android.phone.EditPhoneNumberPreference.GetDefaultNumberListener
    public String onGetDefaultNumber(EditPhoneNumberPreference preference) {
        if (preference == this.mSubMenuVoicemailSettings) {
            log("updating default for voicemail dialog");
            updateVoiceNumberField();
            return null;
        }
        String vmDisplay = this.mPhone.getVoiceMailNumber();
        if (TextUtils.isEmpty(vmDisplay)) {
            return null;
        }
        log("updating default for call forwarding dialogs");
        return getString(R.string.voicemail_abbreviated) + " " + vmDisplay;
    }

    @Override // android.app.Activity
    public void startActivityForResult(Intent intent, int requestCode) {
        if (requestCode == -1) {
            super.startActivityForResult(intent, requestCode);
        } else {
            log("startSubActivity: starting requested subactivity");
            super.startActivityForResult(intent, requestCode);
        }
    }

    private void switchToPreviousVoicemailProvider() {
        log("switchToPreviousVoicemailProvider " + this.mPreviousVMProviderKey);
        if (this.mPreviousVMProviderKey != null) {
            if (this.mVMChangeCompletedSuccessfully || this.mFwdChangesRequireRollback) {
                log("Needs to rollback. mVMChangeCompletedSuccessfully=" + this.mVMChangeCompletedSuccessfully + ", mFwdChangesRequireRollback=" + this.mFwdChangesRequireRollback);
                showDialogIfForeground(603);
                VoiceMailProviderSettings prevSettings = loadSettingsForVoiceMailProvider(this.mPreviousVMProviderKey);
                if (prevSettings == null) {
                    Log.e("CallFeaturesSetting", "VoiceMailProviderSettings for the key \"" + this.mPreviousVMProviderKey + "\" becomes null, which is unexpected.");
                    Log.e("CallFeaturesSetting", "mVMChangeCompletedSuccessfully: " + this.mVMChangeCompletedSuccessfully + ", mFwdChangesRequireRollback: " + this.mFwdChangesRequireRollback);
                }
                if (this.mVMChangeCompletedSuccessfully) {
                    this.mNewVMNumber = prevSettings.voicemailNumber;
                    Log.i("CallFeaturesSetting", "VM change is already completed successfully.Have to revert VM back to " + this.mNewVMNumber + " again.");
                    this.mPhone.setVoiceMailNumber(this.mPhone.getVoiceMailAlphaTag().toString(), this.mNewVMNumber, Message.obtain(this.mRevertOptionComplete, 500));
                }
                if (this.mFwdChangesRequireRollback) {
                    Log.i("CallFeaturesSetting", "Requested to rollback Fwd changes.");
                    CallForwardInfo[] prevFwdSettings = prevSettings.forwardingSettings;
                    if (prevFwdSettings != null) {
                        Map<Integer, AsyncResult> results = this.mForwardingChangeResults;
                        resetForwardingChangeState();
                        for (int i = 0; i < prevFwdSettings.length; i++) {
                            CallForwardInfo fi = prevFwdSettings[i];
                            log("Reverting fwd #: " + i + ": " + fi.toString());
                            AsyncResult result = results.get(Integer.valueOf(fi.reason));
                            if (result != null && result.exception == null) {
                                this.mExpectedChangeResultReasons.add(Integer.valueOf(fi.reason));
                                this.mPhone.setCallForwardingOption(fi.status == 1 ? 3 : 0, fi.reason, fi.number, fi.timeSeconds, this.mRevertOptionComplete.obtainMessage(501, i, 0));
                            }
                        }
                        return;
                    }
                    return;
                }
                return;
            }
            log("No need to revert");
            onRevertDone();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onRevertDone() {
        log("Flipping provider key back to " + this.mPreviousVMProviderKey);
        this.mVoicemailProviders.setValue(this.mPreviousVMProviderKey);
        updateVMPreferenceWidgets(this.mPreviousVMProviderKey);
        updateVoiceNumberField();
        if (this.mVMOrFwdSetError != 0) {
            showVMDialog(this.mVMOrFwdSetError);
            this.mVMOrFwdSetError = 0;
        }
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        log("onActivityResult: requestCode: " + requestCode + ", resultCode: " + resultCode + ", data: " + data);
        if (requestCode == 2) {
            boolean failure = false;
            log("mVMProviderSettingsForced: " + this.mVMProviderSettingsForced);
            boolean isVMProviderSettingsForced = this.mVMProviderSettingsForced;
            this.mVMProviderSettingsForced = false;
            String vmNum = null;
            if (resultCode != -1) {
                log("onActivityResult: vm provider cfg result not OK.");
                failure = true;
            } else if (data == null) {
                log("onActivityResult: vm provider cfg result has no data");
                failure = true;
            } else {
                if (data.getBooleanExtra("com.android.phone.Signout", false)) {
                    log("Provider requested signout");
                    if (isVMProviderSettingsForced) {
                        log("Going back to previous provider on signout");
                        switchToPreviousVoicemailProvider();
                        return;
                    }
                    String victim = getCurrentVoicemailProviderKey();
                    log("Relaunching activity and ignoring " + victim);
                    Intent i = new Intent("com.android.phone.CallFeaturesSetting.ADD_VOICEMAIL");
                    i.putExtra("com.android.phone.ProviderToIgnore", victim);
                    i.setFlags(67108864);
                    startActivity(i);
                    return;
                }
                vmNum = data.getStringExtra("com.android.phone.VoicemailNumber");
                if (vmNum == null || vmNum.length() == 0) {
                    log("onActivityResult: vm provider cfg result has no vmnum");
                    failure = true;
                }
            }
            if (failure) {
                log("Failure in return from voicemail provider");
                if (isVMProviderSettingsForced) {
                    switchToPreviousVoicemailProvider();
                    return;
                } else {
                    log("Not switching back the provider since this is not forced config");
                    return;
                }
            }
            this.mChangingVMorFwdDueToProviderChange = isVMProviderSettingsForced;
            String fwdNum = data.getStringExtra("com.android.phone.ForwardingNumber");
            int fwdNumTime = data.getIntExtra("com.android.phone.ForwardingNumberTime", 20);
            log("onActivityResult: vm provider cfg result " + (fwdNum != null ? "has" : " does not have") + " forwarding number");
            saveVoiceMailAndForwardingNumber(getCurrentVoicemailProviderKey(), new VoiceMailProviderSettings(vmNum, fwdNum, fwdNumTime));
            return;
        }
        if (requestCode == 1) {
            if (resultCode != -1) {
                log("onActivityResult: contact picker result not OK.");
                return;
            }
            Cursor cursor = null;
            try {
                cursor = getContentResolver().query(data.getData(), NUM_PROJECTION, null, null, null);
                if (cursor == null || !cursor.moveToFirst()) {
                    log("onActivityResult: bad contact data, no results found.");
                } else {
                    this.mSubMenuVoicemailSettings.onPickActivityResult(cursor.getString(0));
                }
                return;
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void handleVMBtnClickRequest() {
        saveVoiceMailAndForwardingNumber(getCurrentVoicemailProviderKey(), new VoiceMailProviderSettings(this.mSubMenuVoicemailSettings.getPhoneNumber(), FWD_SETTINGS_DONT_TOUCH));
    }

    private void showDialogIfForeground(int id) {
        if (this.mForeground) {
            showDialog(id);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void dismissDialogSafely(int id) {
        try {
            dismissDialog(id);
        } catch (IllegalArgumentException e) {
        }
    }

    private void saveVoiceMailAndForwardingNumber(String str, VoiceMailProviderSettings voiceMailProviderSettings) {
        log("saveVoiceMailAndForwardingNumber: " + voiceMailProviderSettings.toString());
        this.mNewVMNumber = voiceMailProviderSettings.voicemailNumber;
        if (this.mNewVMNumber == null) {
            this.mNewVMNumber = "";
        }
        this.mNewFwdSettings = voiceMailProviderSettings.forwardingSettings;
        log("newFwdNumber " + String.valueOf(this.mNewFwdSettings != null ? this.mNewFwdSettings.length : 0) + " settings");
        if (this.mPhone.getPhoneType() == 2) {
            log("ignoring forwarding setting since this is CDMA phone");
            this.mNewFwdSettings = FWD_SETTINGS_DONT_TOUCH;
        }
        if (this.mNewVMNumber.equals(this.mOldVmNumber) && this.mNewFwdSettings == FWD_SETTINGS_DONT_TOUCH) {
            showVMDialog(700);
            return;
        }
        maybeSaveSettingsForVoicemailProvider(str, voiceMailProviderSettings);
        this.mVMChangeCompletedSuccessfully = false;
        this.mFwdChangesRequireRollback = false;
        this.mVMOrFwdSetError = 0;
        if (!str.equals(this.mPreviousVMProviderKey)) {
            this.mReadingSettingsForDefaultProvider = this.mPreviousVMProviderKey.equals("");
            log("Reading current forwarding settings");
            this.mForwardingReadResults = new CallForwardInfo[FORWARDING_SETTINGS_REASONS.length];
            for (int i = 0; i < FORWARDING_SETTINGS_REASONS.length; i++) {
                this.mForwardingReadResults[i] = null;
                this.mPhone.getCallForwardingOption(FORWARDING_SETTINGS_REASONS[i], this.mGetOptionComplete.obtainMessage(502, i, 0));
            }
            showDialogIfForeground(602);
            return;
        }
        saveVoiceMailAndForwardingNumberStage2();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleForwardingSettingsReadResult(AsyncResult asyncResult, int i) {
        Throwable th;
        CallForwardInfo callForwardInfo;
        boolean z;
        Log.d("CallFeaturesSetting", "handleForwardingSettingsReadResult: " + i);
        if (asyncResult.exception != null) {
            Log.d("CallFeaturesSetting", "FwdRead: ar.exception=" + asyncResult.exception.getMessage());
            th = asyncResult.exception;
        } else {
            th = null;
        }
        if (asyncResult.userObj instanceof Throwable) {
            Log.d("CallFeaturesSetting", "FwdRead: userObj=" + ((Throwable) asyncResult.userObj).getMessage());
            th = (Throwable) asyncResult.userObj;
        }
        if (this.mForwardingReadResults == null) {
            Log.d("CallFeaturesSetting", "ignoring fwd reading result: " + i);
            return;
        }
        if (th != null) {
            Log.d("CallFeaturesSetting", "Error discovered for fwd read : " + i);
            this.mForwardingReadResults = null;
            dismissDialogSafely(602);
            showVMDialog(402);
            return;
        }
        CallForwardInfo[] callForwardInfoArr = (CallForwardInfo[]) asyncResult.result;
        int i2 = 0;
        while (true) {
            if (i2 >= callForwardInfoArr.length) {
                callForwardInfo = null;
                break;
            } else if ((callForwardInfoArr[i2].serviceClass & 1) == 0) {
                i2++;
            } else {
                callForwardInfo = callForwardInfoArr[i2];
                break;
            }
        }
        if (callForwardInfo == null) {
            Log.d("CallFeaturesSetting", "Creating default info for " + i);
            callForwardInfo = new CallForwardInfo();
            callForwardInfo.status = 0;
            callForwardInfo.reason = FORWARDING_SETTINGS_REASONS[i];
            callForwardInfo.serviceClass = 1;
        } else {
            if (callForwardInfo.number == null || callForwardInfo.number.length() == 0) {
                callForwardInfo.status = 0;
            }
            Log.d("CallFeaturesSetting", "Got  " + callForwardInfo.toString() + " for " + i);
        }
        this.mForwardingReadResults[i] = callForwardInfo;
        int i3 = 0;
        while (true) {
            if (i3 >= this.mForwardingReadResults.length) {
                z = true;
                break;
            } else {
                if (this.mForwardingReadResults[i3] == null) {
                    z = false;
                    break;
                }
                i3++;
            }
        }
        if (z) {
            Log.d("CallFeaturesSetting", "Done receiving fwd info");
            dismissDialogSafely(602);
            if (this.mReadingSettingsForDefaultProvider) {
                maybeSaveSettingsForVoicemailProvider("", new VoiceMailProviderSettings(this.mOldVmNumber, this.mForwardingReadResults));
                this.mReadingSettingsForDefaultProvider = false;
            }
            saveVoiceMailAndForwardingNumberStage2();
            return;
        }
        Log.d("CallFeaturesSetting", "Not done receiving fwd info");
    }

    private CallForwardInfo infoForReason(CallForwardInfo[] infos, int reason) {
        if (infos == null) {
            return null;
        }
        for (CallForwardInfo info : infos) {
            if (info.reason == reason) {
                return info;
            }
        }
        return null;
    }

    private boolean isUpdateRequired(CallForwardInfo oldInfo, CallForwardInfo newInfo) {
        if (newInfo.status != 0 || oldInfo == null || oldInfo.status != 0) {
            return true;
        }
        return false;
    }

    private void resetForwardingChangeState() {
        this.mForwardingChangeResults = new HashMap();
        this.mExpectedChangeResultReasons = new HashSet();
    }

    private void saveVoiceMailAndForwardingNumberStage2() {
        this.mForwardingChangeResults = null;
        this.mVoicemailChangeResult = null;
        if (this.mNewFwdSettings != FWD_SETTINGS_DONT_TOUCH) {
            resetForwardingChangeState();
            for (int i = 0; i < this.mNewFwdSettings.length; i++) {
                CallForwardInfo callForwardInfo = this.mNewFwdSettings[i];
                if (isUpdateRequired(infoForReason(this.mForwardingReadResults, callForwardInfo.reason), callForwardInfo)) {
                    log("Setting fwd #: " + i + ": " + callForwardInfo.toString());
                    this.mExpectedChangeResultReasons.add(Integer.valueOf(i));
                    this.mPhone.setCallForwardingOption(callForwardInfo.status == 1 ? 3 : 0, callForwardInfo.reason, callForwardInfo.number, callForwardInfo.timeSeconds, this.mSetOptionComplete.obtainMessage(501, callForwardInfo.reason, 0));
                }
            }
            showDialogIfForeground(601);
            return;
        }
        log("Not touching fwd #");
        setVMNumberWithCarrier();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setVMNumberWithCarrier() {
        log("save voicemail #: " + this.mNewVMNumber);
        this.mPhone.setVoiceMailNumber(this.mPhone.getVoiceMailAlphaTag().toString(), this.mNewVMNumber, Message.obtain(this.mSetOptionComplete, 500));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean checkForwardingCompleted() {
        if (this.mForwardingChangeResults == null) {
            return true;
        }
        for (Integer reason : this.mExpectedChangeResultReasons) {
            if (this.mForwardingChangeResults.get(reason) == null) {
                return false;
            }
        }
        return true;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public String checkFwdChangeSuccess() {
        for (Map.Entry<Integer, AsyncResult> entry : this.mForwardingChangeResults.entrySet()) {
            Throwable exception = entry.getValue().exception;
            if (exception != null) {
                String result = exception.getMessage();
                if (result == null) {
                    return "";
                }
                return result;
            }
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public String checkVMChangeSuccess() {
        if (this.mVoicemailChangeResult.exception == null) {
            return null;
        }
        String msg = this.mVoicemailChangeResult.exception.getMessage();
        if (msg == null) {
            return "";
        }
        return msg;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleSetVMOrFwdMessage() {
        boolean z;
        boolean z2 = true;
        log("handleSetVMMessage: set VM request complete");
        String strCheckVMChangeSuccess = "";
        if (this.mForwardingChangeResults == null || (strCheckVMChangeSuccess = checkFwdChangeSuccess()) == null) {
            z = false;
        } else {
            z = true;
            z2 = false;
        }
        if ((!z2 || (strCheckVMChangeSuccess = checkVMChangeSuccess()) == null) ? z2 : false) {
            log("change VM success!");
            handleVMAndFwdSetSuccess(600);
        } else if (z) {
            Log.w("CallFeaturesSetting", "Failed to change fowarding setting. Reason: " + strCheckVMChangeSuccess);
            handleVMOrFwdSetError(401);
        } else {
            Log.w("CallFeaturesSetting", "Failed to change voicemail. Reason: " + strCheckVMChangeSuccess);
            handleVMOrFwdSetError(400);
        }
    }

    private void handleVMOrFwdSetError(int msgId) {
        if (this.mChangingVMorFwdDueToProviderChange) {
            this.mVMOrFwdSetError = msgId;
            this.mChangingVMorFwdDueToProviderChange = false;
            switchToPreviousVoicemailProvider();
        } else {
            this.mChangingVMorFwdDueToProviderChange = false;
            showVMDialog(msgId);
            updateVoiceNumberField();
        }
    }

    private void handleVMAndFwdSetSuccess(int i) {
        log("handleVMAndFwdSetSuccess(). current voicemail provider key: " + getCurrentVoicemailProviderKey());
        this.mPreviousVMProviderKey = getCurrentVoicemailProviderKey();
        this.mChangingVMorFwdDueToProviderChange = false;
        showVMDialog(i);
        updateVoiceNumberField();
    }

    private void updateVoiceNumberField() {
        log("updateVoiceNumberField(). mSubMenuVoicemailSettings=" + this.mSubMenuVoicemailSettings);
        if (this.mSubMenuVoicemailSettings != null) {
            this.mOldVmNumber = this.mPhone.getVoiceMailNumber();
            if (this.mOldVmNumber == null) {
                this.mOldVmNumber = "";
            }
            this.mSubMenuVoicemailSettings.setPhoneNumber(this.mOldVmNumber);
            this.mSubMenuVoicemailSettings.setSummary(this.mOldVmNumber.length() > 0 ? this.mOldVmNumber : getString(R.string.voicemail_number_not_set));
        }
    }

    @Override // android.app.Activity
    protected void onPrepareDialog(int id, Dialog dialog) {
        super.onPrepareDialog(id, dialog);
        this.mCurrentDialogId = id;
    }

    @Override // android.app.Activity
    protected Dialog onCreateDialog(int i) {
        int i2;
        int i3;
        if (i == 500 || i == 400 || i == 501 || i == 502 || i == 600) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            int i4 = R.string.error_updating_title;
            switch (i) {
                case 400:
                    i2 = R.string.no_change;
                    i4 = R.string.voicemail;
                    builder.setNegativeButton(R.string.close_dialog, this);
                    break;
                case 500:
                    i2 = R.string.vm_change_failed;
                    builder.setPositiveButton(R.string.close_dialog, this);
                    break;
                case 501:
                    i2 = R.string.fw_change_failed;
                    builder.setPositiveButton(R.string.close_dialog, this);
                    break;
                case 502:
                    i2 = R.string.fw_get_in_vm_failed;
                    builder.setPositiveButton(R.string.alert_dialog_yes, this);
                    builder.setNegativeButton(R.string.alert_dialog_no, this);
                    break;
                case 600:
                    i2 = R.string.vm_changed;
                    i4 = R.string.voicemail;
                    builder.setNegativeButton(R.string.close_dialog, this);
                    break;
                default:
                    i2 = R.string.exception_error;
                    builder.setNeutralButton(R.string.close_dialog, this);
                    break;
            }
            builder.setTitle(getText(i4));
            builder.setMessage(getText(i2).toString());
            builder.setCancelable(false);
            AlertDialog alertDialogCreate = builder.create();
            alertDialogCreate.getWindow().addFlags(4);
            return alertDialogCreate;
        }
        if (i == 601 || i == 602 || i == 603) {
            ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setTitle(getText(R.string.updating_title));
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
            if (i == 601) {
                i3 = R.string.updating_settings;
            } else {
                i3 = i == 603 ? R.string.reverting_settings : R.string.reading_settings;
            }
            progressDialog.setMessage(getText(i3));
            return progressDialog;
        }
        if (i == 800) {
            AlertDialog.Builder builder2 = new AlertDialog.Builder(this);
            builder2.setTitle(getText(R.string.tty_mode_option_title));
            builder2.setMessage(getText(R.string.tty_mode_not_allowed_vt_call));
            builder2.setIconAttribute(android.R.attr.alertDialogIcon);
            builder2.setPositiveButton(R.string.ok, this);
            builder2.setCancelable(false);
            AlertDialog alertDialogCreate2 = builder2.create();
            alertDialogCreate2.getWindow().addFlags(4);
            return alertDialogCreate2;
        }
        return null;
    }

    @Override // android.content.DialogInterface.OnClickListener
    public void onClick(DialogInterface dialogInterface, int i) {
        dialogInterface.dismiss();
        switch (i) {
            case -3:
                log("Neutral button");
                break;
            case -2:
                log("Negative button");
                if (this.mCurrentDialogId == 502) {
                    switchToPreviousVoicemailProvider();
                }
                break;
            case -1:
                log("Positive button");
                if (this.mCurrentDialogId == 502) {
                    saveVoiceMailAndForwardingNumberStage2();
                    return;
                } else {
                    finish();
                    return;
                }
        }
        if (getIntent().getAction().equals("com.android.phone.CallFeaturesSetting.ADD_VOICEMAIL")) {
            finish();
        }
    }

    private void showVMDialog(int msgStatus) {
        switch (msgStatus) {
            case 400:
                showDialogIfForeground(500);
                break;
            case 401:
                showDialogIfForeground(501);
                break;
            case 402:
                showDialogIfForeground(502);
                break;
            case 600:
                showDialogIfForeground(600);
                break;
            case 700:
                showDialogIfForeground(400);
                break;
        }
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        log("onCreate(). Intent: " + getIntent());
        this.mPhone = PhoneGlobals.getPhone();
        addPreferencesFromResource(R.xml.call_feature_setting);
        this.mAudioManager = (AudioManager) getSystemService("audio");
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        this.mVoicemailCategory = (PreferenceScreen) findPreference("button_voicemail_category_key");
        this.mSubMenuVoicemailSettings = (EditPhoneNumberPreference) findPreference("button_voicemail_key");
        if (this.mSubMenuVoicemailSettings != null) {
            this.mSubMenuVoicemailSettings.setParentActivity(this, 1, this);
            this.mSubMenuVoicemailSettings.setDialogOnClosedListener(this);
            this.mSubMenuVoicemailSettings.setDialogTitle(R.string.voicemail_settings_number_label);
        }
        this.mRingtonePreference = findPreference("button_ringtone_key");
        this.mVibrateWhenRinging = (CheckBoxPreference) findPreference("button_vibrate_on_ring");
        this.mPlayDtmfTone = (CheckBoxPreference) findPreference("button_play_dtmf_tone");
        this.mButtonDTMF = (ListPreference) findPreference("button_dtmf_settings");
        this.mButtonAutoRetry = (CheckBoxPreference) findPreference("button_auto_retry_key");
        this.mButtonHAC = (CheckBoxPreference) findPreference("button_hac_key");
        this.mButtonTTY = (ListPreference) findPreference("button_tty_mode_key");
        this.mVoicemailProviders = (ListPreference) findPreference("button_voicemail_provider_key");
        this.mButtonProximity = (CheckBoxPreference) findPreference("button_proximity_key");
        this.mShowDurationCheckBox = (CheckBoxPreference) findPreference("duration_enable_key");
        this.mVibrateAfterConnected = (CheckBoxPreference) findPreference("button_vibrate_after_connected");
        this.mIPPrefix = (PreferenceScreen) findPreference("button_ipprefix_key");
        if (this.mVoicemailProviders != null) {
            this.mVoicemailProviders.setOnPreferenceChangeListener(this);
            this.mVoicemailSettings = (PreferenceScreen) findPreference("button_voicemail_setting_key");
            this.mVoicemailNotificationRingtone = findPreference("button_voicemail_notification_ringtone_key");
            this.mVoicemailNotificationVibrate = (CheckBoxPreference) findPreference("button_voicemail_notification_vibrate_key");
            initVoiceMailProviders();
        }
        if (this.mVibrateWhenRinging != null) {
            Vibrator vibrator = (Vibrator) getSystemService("vibrator");
            if (vibrator != null && vibrator.hasVibrator()) {
                this.mVibrateWhenRinging.setOnPreferenceChangeListener(this);
            } else {
                preferenceScreen.removePreference(this.mVibrateWhenRinging);
                this.mVibrateWhenRinging = null;
            }
        }
        if (this.mIPPrefix != null) {
            String string = Settings.System.getString(getContentResolver(), "ip_call_prefix_sub1");
            if (TextUtils.isEmpty(string)) {
                this.mIPPrefix.setSummary(R.string.ipcall_sub_summery);
            } else {
                this.mIPPrefix.setSummary(string);
            }
        }
        ContentResolver contentResolver = getContentResolver();
        this.mEmergencyCall = (PreferenceScreen) findPreference("emergency_call_list");
        if (!getResources().getBoolean(R.bool.show_emergency_call_list)) {
            preferenceScreen.removePreference(this.mEmergencyCall);
        }
        if (this.mPlayDtmfTone != null) {
            this.mPlayDtmfTone.setChecked(Settings.System.getInt(contentResolver, "dtmf_tone", 1) != 0);
        }
        if (this.mButtonDTMF != null) {
            if (getResources().getBoolean(R.bool.dtmf_type_enabled)) {
                this.mButtonDTMF.setOnPreferenceChangeListener(this);
            } else {
                preferenceScreen.removePreference(this.mButtonDTMF);
                this.mButtonDTMF = null;
            }
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
        if (this.mButtonAutoRetry != null) {
            if (getResources().getBoolean(R.bool.auto_retry_enabled)) {
                this.mButtonAutoRetry.setOnPreferenceChangeListener(this);
            } else {
                preferenceScreen.removePreference(this.mButtonAutoRetry);
                this.mButtonAutoRetry = null;
            }
        }
        if (this.mButtonHAC != null) {
            if (getResources().getBoolean(R.bool.hac_enabled)) {
                this.mButtonHAC.setOnPreferenceChangeListener(this);
            } else {
                preferenceScreen.removePreference(this.mButtonHAC);
                this.mButtonHAC = null;
            }
        }
        if (this.mButtonTTY != null) {
            if (getResources().getBoolean(R.bool.tty_enabled)) {
                this.mButtonTTY.setOnPreferenceChangeListener(this);
            } else {
                preferenceScreen.removePreference(this.mButtonTTY);
                this.mButtonTTY = null;
            }
        }
        if (!getResources().getBoolean(R.bool.world_phone)) {
            Preference preferenceFindPreference = preferenceScreen.findPreference("button_cdma_more_expand_key");
            if (preferenceFindPreference != null) {
                preferenceScreen.removePreference(preferenceFindPreference);
            }
            Preference preferenceFindPreference2 = preferenceScreen.findPreference("button_gsm_more_expand_key");
            if (preferenceFindPreference2 != null) {
                preferenceScreen.removePreference(preferenceFindPreference2);
            }
            int phoneType = this.mPhone.getPhoneType();
            if (phoneType == 2) {
                Preference preferenceFindPreference3 = preferenceScreen.findPreference("button_fdn_key");
                if (preferenceFindPreference3 != null && getResources().getBoolean(R.bool.config_fdn_disable)) {
                    preferenceScreen.removePreference(preferenceFindPreference3);
                }
                if (!getResources().getBoolean(R.bool.config_voice_privacy_disable)) {
                    addPreferencesFromResource(R.xml.cdma_call_privacy);
                    PhoneGlobals.initCallWaitingPref(this, 0);
                }
            } else if (phoneType == 1) {
                addPreferencesFromResource(R.xml.gsm_umts_call_options);
            } else {
                throw new IllegalStateException("Unexpected phone type: " + phoneType);
            }
        }
        this.mContactListIntent = new Intent("android.intent.action.GET_CONTENT");
        this.mContactListIntent.setType("vnd.android.cursor.item/phone");
        if (bundle == null && getIntent().getAction().equals("com.android.phone.CallFeaturesSetting.ADD_VOICEMAIL") && this.mVoicemailProviders != null) {
            log("ACTION_ADD_VOICEMAIL Intent is thrown. current VM data size: " + this.mVMProvidersData.size());
            if (this.mVMProvidersData.size() > 1) {
                simulatePreferenceClick(this.mVoicemailProviders);
            } else {
                onPreferenceChange(this.mVoicemailProviders, "");
                this.mVoicemailProviders.setValue("");
                this.isSpeedDialListStarted = true;
                simulatePreferenceClick(this.mVoicemailCategory);
            }
        }
        updateVoiceNumberField();
        this.mVMProviderSettingsForced = false;
        createSipCallSettings();
        createImsSettings();
        this.mRingtoneLookupRunnable = new Runnable() { // from class: com.android.phone.CallFeaturesSetting.7
            @Override // java.lang.Runnable
            public void run() {
                if (CallFeaturesSetting.this.mRingtonePreference != null) {
                    CallFeaturesSetting.this.updateRingtoneName(1, CallFeaturesSetting.this.mRingtonePreference, 1);
                }
                if (CallFeaturesSetting.this.mVoicemailNotificationRingtone != null) {
                    CallFeaturesSetting.this.updateRingtoneName(2, CallFeaturesSetting.this.mVoicemailNotificationRingtone, 2);
                }
            }
        };
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowTitleEnabled(true);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateRingtoneName(int i, Preference preference, int i2) {
        boolean z;
        String string;
        Uri actualDefaultRingtoneUri = null;
        if (preference != null) {
            if (i == 1) {
                actualDefaultRingtoneUri = RingtoneManager.getActualDefaultRingtoneUri(this, i);
                z = false;
            } else {
                String string2 = PreferenceManager.getDefaultSharedPreferences(this.mPhone.getContext()).getString(preference.getKey(), null);
                if (TextUtils.isEmpty(string2)) {
                    z = false;
                } else if (string2.equals(Settings.System.DEFAULT_NOTIFICATION_URI.toString())) {
                    actualDefaultRingtoneUri = RingtoneManager.getActualDefaultRingtoneUri(this, i);
                    z = true;
                } else {
                    actualDefaultRingtoneUri = Uri.parse(string2);
                    z = false;
                }
            }
            String string3 = getString(android.R.string.fingerprint_error_bad_calibration);
            if (actualDefaultRingtoneUri == null) {
                string = getString(android.R.string.fingerprint_dialog_default_subtitle);
            } else {
                try {
                    Cursor cursorQuery = getContentResolver().query(actualDefaultRingtoneUri, new String[]{"title"}, null, null, null);
                    if (cursorQuery != null) {
                        string = cursorQuery.moveToFirst() ? cursorQuery.getString(0) : string3;
                        try {
                            cursorQuery.close();
                        } catch (SQLiteException e) {
                            string3 = string;
                            string = string3;
                        }
                    } else {
                        string = string3;
                    }
                } catch (SQLiteException e2) {
                }
            }
            if (z) {
                string = this.mPhone.getContext().getString(R.string.default_notification_description, string);
            }
            this.mRingtoneLookupComplete.sendMessage(this.mRingtoneLookupComplete.obtainMessage(i2, string));
        }
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
        ListPreference listPreference = (ListPreference) findPreference("sip_call_options_key");
        ListPreference listPreference2 = (ListPreference) findPreference("sip_call_options_wifi_only_key");
        PreferenceGroup preferenceGroup = (PreferenceGroup) findPreference("sip_settings_category_key");
        if (SipManager.isSipWifiOnly(this)) {
            preferenceGroup.removePreference(listPreference);
            return listPreference2;
        }
        preferenceGroup.removePreference(listPreference2);
        return listPreference;
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        this.mForeground = true;
        if (isAirplaneModeOn()) {
            Preference preferenceFindPreference = findPreference("sip_settings_category_key");
            PreferenceScreen preferenceScreen = getPreferenceScreen();
            int preferenceCount = preferenceScreen.getPreferenceCount();
            for (int i = 0; i < preferenceCount; i++) {
                Preference preference = preferenceScreen.getPreference(i);
                if (preference != preferenceFindPreference) {
                    preference.setEnabled(false);
                }
            }
            return;
        }
        if (this.mVibrateWhenRinging != null) {
            this.mVibrateWhenRinging.setChecked(getVibrateWhenRinging(this));
        }
        if (this.mButtonDTMF != null) {
            this.mButtonDTMF.setValueIndex(Settings.System.getInt(getContentResolver(), "dtmf_tone_type", 0));
        }
        if (this.mButtonAutoRetry != null) {
            this.mButtonAutoRetry.setChecked(Settings.Global.getInt(getContentResolver(), "call_auto_retry", 0) != 0);
        }
        if (this.mButtonHAC != null) {
            this.mButtonHAC.setChecked(Settings.System.getInt(getContentResolver(), "hearing_aid", 0) != 0);
        }
        if (this.mButtonTTY != null) {
            int i2 = Settings.Secure.getInt(getContentResolver(), "preferred_tty_mode", 0);
            this.mButtonTTY.setValue(Integer.toString(i2));
            updatePreferredTtyModeSummary(i2);
        }
        SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.mPhone.getContext());
        if (migrateVoicemailVibrationSettingsIfNeeded(defaultSharedPreferences)) {
            this.mVoicemailNotificationVibrate.setChecked(defaultSharedPreferences.getBoolean("button_voicemail_notification_vibrate_key", false));
        }
        if (this.mButtonProximity != null) {
            boolean z = Settings.System.getInt(getContentResolver(), "proximity_sensor", 1) == 1;
            this.mButtonProximity.setChecked(z);
            this.mButtonProximity.setSummary(z ? R.string.proximity_on_summary : R.string.proximity_off_summary);
        }
        if (this.mShowDurationCheckBox != null) {
            boolean z2 = Settings.System.getInt(getContentResolver(), "show_call_duration", 1) == 1;
            this.mShowDurationCheckBox.setChecked(z2);
            this.mShowDurationCheckBox.setSummary(z2 ? R.string.duration_enable_summary : R.string.duration_disable_summary);
        }
        if (this.mVibrateAfterConnected != null) {
            this.mVibrateAfterConnected.setChecked(Settings.System.getInt(getContentResolver(), "vibrate_on_accepted", 1) == 1);
        }
        lookupRingtoneName();
    }

    public static boolean migrateVoicemailVibrationSettingsIfNeeded(SharedPreferences sharedPreferences) {
        if (sharedPreferences.contains("button_voicemail_notification_vibrate_key")) {
            return false;
        }
        boolean zEquals = sharedPreferences.getString("button_voicemail_notification_vibrate_when_key", "never").equals("always");
        SharedPreferences.Editor editorEdit = sharedPreferences.edit();
        editorEdit.putBoolean("button_voicemail_notification_vibrate_key", zEquals);
        editorEdit.commit();
        return true;
    }

    public static boolean getVibrateWhenRinging(Context context) {
        Vibrator vibrator = (Vibrator) context.getSystemService("vibrator");
        if (vibrator == null || !vibrator.hasVibrator()) {
            return false;
        }
        return Settings.System.getInt(context.getContentResolver(), "vibrate_when_ringing", 0) != 0;
    }

    private void lookupRingtoneName() {
        new Thread(this.mRingtoneLookupRunnable).start();
    }

    private boolean isAirplaneModeOn() {
        return Settings.System.getInt(getContentResolver(), "airplane_mode_on", 0) != 0;
    }

    private void handleTTYChange(Preference preference, Object obj) {
        int iIntValue = Integer.valueOf((String) obj).intValue();
        int i = Settings.Secure.getInt(getContentResolver(), "preferred_tty_mode", 0);
        log("handleTTYChange: requesting set TTY mode enable (TTY) to" + Integer.toString(iIntValue));
        if (iIntValue != i) {
            switch (iIntValue) {
                case 0:
                case 1:
                case 2:
                case 3:
                    Settings.Secure.putInt(getContentResolver(), "preferred_tty_mode", iIntValue);
                    break;
                default:
                    iIntValue = 0;
                    break;
            }
            this.mButtonTTY.setValue(Integer.toString(iIntValue));
            updatePreferredTtyModeSummary(iIntValue);
            Intent intent = new Intent("com.android.internal.telephony.cdma.intent.action.TTY_PREFERRED_MODE_CHANGE");
            intent.putExtra("ttyPreferredMode", iIntValue);
            sendBroadcastAsUser(intent, UserHandle.ALL);
        }
    }

    private void handleSipCallOptionsChange(Object objValue) {
        String option = objValue.toString();
        this.mSipSharedPreferences.setSipCallOption(option);
        this.mButtonSipCallOptions.setValueIndex(this.mButtonSipCallOptions.findIndexOfValue(option));
        this.mButtonSipCallOptions.setSummary(this.mButtonSipCallOptions.getEntry());
    }

    private void updatePreferredTtyModeSummary(int i) {
        String[] stringArray = getResources().getStringArray(R.array.tty_mode_entries);
        switch (i) {
            case 0:
            case 1:
            case 2:
            case 3:
                this.mButtonTTY.setSummary(stringArray[i]);
                break;
            default:
                this.mButtonTTY.setEnabled(false);
                this.mButtonTTY.setSummary(stringArray[0]);
                break;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void log(String msg) {
        Log.d("CallFeaturesSetting", msg);
    }

    private void updateVMPreferenceWidgets(String str) {
        VoiceMailProvider voiceMailProvider = this.mVMProvidersData.get(str);
        if (voiceMailProvider == null) {
            log("updateVMPreferenceWidget: provider for the key \"" + str + "\" is null.");
            this.mVoicemailProviders.setSummary(getString(R.string.sum_voicemail_choose_provider));
            this.mVoicemailSettings.setEnabled(false);
            this.mVoicemailSettings.setIntent(null);
            this.mVoicemailNotificationVibrate.setEnabled(false);
            return;
        }
        log("updateVMPreferenceWidget: provider for the key \"" + str + "\"..name: " + voiceMailProvider.name + ", intent: " + voiceMailProvider.intent);
        this.mVoicemailProviders.setSummary(voiceMailProvider.name);
        this.mVoicemailSettings.setEnabled(true);
        this.mVoicemailSettings.setIntent(voiceMailProvider.intent);
        this.mVoicemailNotificationVibrate.setEnabled(true);
    }

    private void initVoiceMailProviders() {
        String str;
        int i = 0;
        log("initVoiceMailProviders()");
        this.mPerProviderSavedVMNumbers = getApplicationContext().getSharedPreferences("vm_numbers", 0);
        if (getIntent().getAction().equals("com.android.phone.CallFeaturesSetting.ADD_VOICEMAIL")) {
            String stringExtra = getIntent().hasExtra("com.android.phone.ProviderToIgnore") ? getIntent().getStringExtra("com.android.phone.ProviderToIgnore") : null;
            log("Found ACTION_ADD_VOICEMAIL. providerToIgnore=" + stringExtra);
            if (stringExtra != null) {
                deleteSettingsForVoicemailProvider(stringExtra);
            }
            str = stringExtra;
        } else {
            str = null;
        }
        this.mVMProvidersData.clear();
        String string = getString(R.string.voicemail_default);
        this.mVMProvidersData.put("", new VoiceMailProvider(string, null));
        PackageManager packageManager = getPackageManager();
        Intent intent = new Intent();
        intent.setAction("com.android.phone.CallFeaturesSetting.CONFIGURE_VOICEMAIL");
        List<ResolveInfo> listQueryIntentActivities = packageManager.queryIntentActivities(intent, 0);
        int size = listQueryIntentActivities.size() + 1;
        for (int i2 = 0; i2 < listQueryIntentActivities.size(); i2++) {
            ResolveInfo resolveInfo = listQueryIntentActivities.get(i2);
            ActivityInfo activityInfo = resolveInfo.activityInfo;
            String strMakeKeyForActivity = makeKeyForActivity(activityInfo);
            if (strMakeKeyForActivity.equals(str)) {
                log("Ignoring key: " + strMakeKeyForActivity);
                size--;
            } else {
                log("Loading key: " + strMakeKeyForActivity);
                String string2 = resolveInfo.loadLabel(packageManager).toString();
                Intent intent2 = new Intent();
                intent2.setAction("com.android.phone.CallFeaturesSetting.CONFIGURE_VOICEMAIL");
                intent2.setClassName(activityInfo.packageName, activityInfo.name);
                log("Store loaded VoiceMailProvider. key: " + strMakeKeyForActivity + " -> name: " + string2 + ", intent: " + intent2);
                this.mVMProvidersData.put(strMakeKeyForActivity, new VoiceMailProvider(string2, intent2));
            }
        }
        String[] strArr = new String[size];
        String[] strArr2 = new String[size];
        strArr[0] = string;
        strArr2[0] = "";
        int i3 = 1;
        while (true) {
            int i4 = i3;
            if (i < listQueryIntentActivities.size()) {
                String strMakeKeyForActivity2 = makeKeyForActivity(listQueryIntentActivities.get(i).activityInfo);
                if (this.mVMProvidersData.containsKey(strMakeKeyForActivity2)) {
                    strArr[i4] = this.mVMProvidersData.get(strMakeKeyForActivity2).name;
                    strArr2[i4] = strMakeKeyForActivity2;
                    i3 = i4 + 1;
                } else {
                    i3 = i4;
                }
                i++;
            } else {
                this.mVoicemailProviders.setEntries(strArr);
                this.mVoicemailProviders.setEntryValues(strArr2);
                this.mPreviousVMProviderKey = getCurrentVoicemailProviderKey();
                log("Set up the first mPreviousVMProviderKey: " + this.mPreviousVMProviderKey);
                updateVMPreferenceWidgets(this.mPreviousVMProviderKey);
                return;
            }
        }
    }

    private String makeKeyForActivity(ActivityInfo ai) {
        return ai.name;
    }

    private void simulatePreferenceClick(Preference preference) {
        ListAdapter adapter = getPreferenceScreen().getRootAdapter();
        for (int idx = 0; idx < adapter.getCount(); idx++) {
            if (adapter.getItem(idx) == preference) {
                getPreferenceScreen().onItemClick(getListView(), null, idx, adapter.getItemId(idx));
                return;
            }
        }
    }

    private void maybeSaveSettingsForVoicemailProvider(String str, VoiceMailProviderSettings voiceMailProviderSettings) {
        if (this.mVoicemailProviders != null) {
            if (voiceMailProviderSettings.equals(loadSettingsForVoiceMailProvider(str))) {
                log("maybeSaveSettingsForVoicemailProvider: Not saving setting for " + str + " since they have not changed");
                return;
            }
            log("Saving settings for " + str + ": " + voiceMailProviderSettings.toString());
            SharedPreferences.Editor editorEdit = this.mPerProviderSavedVMNumbers.edit();
            editorEdit.putString(str + "#VMNumber", voiceMailProviderSettings.voicemailNumber);
            String str2 = str + "#FWDSettings";
            CallForwardInfo[] callForwardInfoArr = voiceMailProviderSettings.forwardingSettings;
            if (callForwardInfoArr != FWD_SETTINGS_DONT_TOUCH) {
                editorEdit.putInt(str2 + "#Length", callForwardInfoArr.length);
                for (int i = 0; i < callForwardInfoArr.length; i++) {
                    String str3 = str2 + "#Setting" + String.valueOf(i);
                    CallForwardInfo callForwardInfo = callForwardInfoArr[i];
                    editorEdit.putInt(str3 + "#Status", callForwardInfo.status);
                    editorEdit.putInt(str3 + "#Reason", callForwardInfo.reason);
                    editorEdit.putString(str3 + "#Number", callForwardInfo.number);
                    editorEdit.putInt(str3 + "#Time", callForwardInfo.timeSeconds);
                }
            } else {
                editorEdit.putInt(str2 + "#Length", 0);
            }
            editorEdit.apply();
        }
    }

    private VoiceMailProviderSettings loadSettingsForVoiceMailProvider(String str) {
        String string = this.mPerProviderSavedVMNumbers.getString(str + "#VMNumber", null);
        if (string == null) {
            Log.w("CallFeaturesSetting", "VoiceMailProvider settings for the key \"" + str + "\" was not found. Returning null.");
            return null;
        }
        CallForwardInfo[] callForwardInfoArr = FWD_SETTINGS_DONT_TOUCH;
        String str2 = str + "#FWDSettings";
        int i = this.mPerProviderSavedVMNumbers.getInt(str2 + "#Length", 0);
        if (i > 0) {
            CallForwardInfo[] callForwardInfoArr2 = new CallForwardInfo[i];
            for (int i2 = 0; i2 < callForwardInfoArr2.length; i2++) {
                String str3 = str2 + "#Setting" + String.valueOf(i2);
                callForwardInfoArr2[i2] = new CallForwardInfo();
                callForwardInfoArr2[i2].status = this.mPerProviderSavedVMNumbers.getInt(str3 + "#Status", 0);
                callForwardInfoArr2[i2].reason = this.mPerProviderSavedVMNumbers.getInt(str3 + "#Reason", 5);
                callForwardInfoArr2[i2].serviceClass = 1;
                callForwardInfoArr2[i2].toa = 145;
                callForwardInfoArr2[i2].number = this.mPerProviderSavedVMNumbers.getString(str3 + "#Number", "");
                callForwardInfoArr2[i2].timeSeconds = this.mPerProviderSavedVMNumbers.getInt(str3 + "#Time", 20);
            }
            callForwardInfoArr = callForwardInfoArr2;
        }
        VoiceMailProviderSettings voiceMailProviderSettings = new VoiceMailProviderSettings(string, callForwardInfoArr);
        log("Loaded settings for " + str + ": " + voiceMailProviderSettings.toString());
        return voiceMailProviderSettings;
    }

    private void deleteSettingsForVoicemailProvider(String str) {
        log("Deleting settings for" + str);
        if (this.mVoicemailProviders != null) {
            this.mPerProviderSavedVMNumbers.edit().putString(str + "#VMNumber", null).putInt(str + "#FWDSettings#Length", 0).commit();
        }
    }

    private String getCurrentVoicemailProviderKey() {
        String key = this.mVoicemailProviders.getValue();
        return key != null ? key : "";
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() != 16908332) {
            return super.onOptionsItemSelected(menuItem);
        }
        onBackPressed();
        return true;
    }

    public static void goUpToTopLevelSetting(Activity activity) {
        Intent intent = new Intent(activity, (Class<?>) CallFeaturesSetting.class);
        intent.setAction("android.intent.action.MAIN");
        intent.addFlags(67108864);
        activity.startActivity(intent);
        activity.finish();
    }
}
