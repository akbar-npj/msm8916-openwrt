package com.android.phone;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.telephony.MSimTelephonyManager;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.Phone;

/* JADX INFO: loaded from: classes.dex */
public class FdnSetting extends PreferenceActivity implements DialogInterface.OnCancelListener, EditPinPreference.OnPinEnteredListener {
    private EditPinPreference mButtonChangePin2;
    private EditPinPreference mButtonEnableFDN;
    private boolean mIsPuk2Locked;
    private String mNewPin;
    private String mOldPin;
    private Phone mPhone;
    private int mPinChangeState;
    private String mPuk2;
    private PreferenceScreen mSubscriptionPrefFDN;
    private int mSubscription = 0;
    private final Handler mFDNHandler = new Handler() { // from class: com.android.phone.FdnSetting.1
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 100:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    if (ar.exception != null && (ar.exception instanceof CommandException)) {
                        int attemptsRemaining = msg.arg1;
                        CommandException.Error e = ar.exception.getCommandError();
                        switch (AnonymousClass2.$SwitchMap$com$android$internal$telephony$CommandException$Error[e.ordinal()]) {
                            case 1:
                                FdnSetting.this.displayMessage(R.string.fdn_enable_puk2_requested, attemptsRemaining);
                                FdnSetting.this.resetPinChangeStateForPUK2();
                                break;
                            case 2:
                                FdnSetting.this.displayMessage(R.string.pin2_invalid, attemptsRemaining);
                                break;
                            default:
                                FdnSetting.this.displayMessage(R.string.fdn_failed, attemptsRemaining);
                                break;
                        }
                    }
                    FdnSetting.this.updateEnableFDN();
                    break;
                case 200:
                    AsyncResult ar2 = (AsyncResult) msg.obj;
                    if (ar2.exception == null) {
                        if (FdnSetting.this.mPinChangeState == 3) {
                            FdnSetting.this.displayMessage(R.string.pin2_unblocked);
                        } else {
                            FdnSetting.this.displayMessage(R.string.pin2_changed);
                        }
                        FdnSetting.this.resetPinChangeState();
                    } else {
                        int attemptsRemaining2 = msg.arg1;
                        FdnSetting.this.log("Handle EVENT_PIN2_CHANGE_COMPLETE attemptsRemaining=" + attemptsRemaining2);
                        CommandException ce = ar2.exception;
                        if (ce.getCommandError() != CommandException.Error.SIM_PUK2) {
                            if (!FdnSetting.this.mIsPuk2Locked) {
                                FdnSetting.this.displayMessage(R.string.badPin2, attemptsRemaining2);
                                FdnSetting.this.resetPinChangeState();
                            } else {
                                FdnSetting.this.displayMessage(R.string.badPuk2, attemptsRemaining2);
                                FdnSetting.this.resetPinChangeStateForPUK2();
                            }
                        } else {
                            AlertDialog a = new AlertDialog.Builder(FdnSetting.this).setMessage(R.string.puk2_requested).setCancelable(true).setOnCancelListener(FdnSetting.this).setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() { // from class: com.android.phone.FdnSetting.1.1
                                @Override // android.content.DialogInterface.OnClickListener
                                public void onClick(DialogInterface dialog, int which) {
                                    FdnSetting.this.resetPinChangeStateForPUK2();
                                    FdnSetting.this.displayPinChangeDialog(0, true);
                                }
                            }).create();
                            a.getWindow().addFlags(2);
                            a.show();
                        }
                    }
                    break;
            }
        }
    };

    @Override // com.android.phone.EditPinPreference.OnPinEnteredListener
    public void onPinEntered(EditPinPreference preference, boolean positiveResult) {
        if (preference == this.mButtonEnableFDN) {
            toggleFDNEnable(positiveResult);
        } else if (preference == this.mButtonChangePin2) {
            updatePINChangeState(positiveResult);
        }
    }

    private void toggleFDNEnable(boolean positiveResult) {
        if (positiveResult) {
            String password = this.mButtonEnableFDN.getText();
            if (validatePin(password, false)) {
                boolean isEnabled = this.mPhone.getIccCard().getIccFdnEnabled();
                Message onComplete = this.mFDNHandler.obtainMessage(100);
                this.mPhone.getIccCard().setIccFdnEnabled(isEnabled ? false : true, password, onComplete);
            } else {
                displayMessage(R.string.invalidPin2);
            }
            this.mButtonEnableFDN.setText("");
        }
    }

    private void updatePINChangeState(boolean positiveResult) {
        if (!positiveResult) {
            if (!this.mIsPuk2Locked) {
                resetPinChangeState();
            } else {
                resetPinChangeStateForPUK2();
                return;
            }
        }
        switch (this.mPinChangeState) {
            case 0:
                this.mOldPin = this.mButtonChangePin2.getText();
                this.mButtonChangePin2.setText("");
                if (validatePin(this.mOldPin, false)) {
                    this.mPinChangeState = 1;
                    displayPinChangeDialog();
                } else {
                    displayPinChangeDialog(R.string.invalidPin2, true);
                }
                break;
            case 1:
                this.mNewPin = this.mButtonChangePin2.getText();
                this.mButtonChangePin2.setText("");
                if (validatePin(this.mNewPin, false)) {
                    this.mPinChangeState = 2;
                    displayPinChangeDialog();
                } else {
                    displayPinChangeDialog(R.string.invalidPin2, true);
                }
                break;
            case 2:
                if (!this.mNewPin.equals(this.mButtonChangePin2.getText())) {
                    this.mPinChangeState = 1;
                    this.mButtonChangePin2.setText("");
                    displayPinChangeDialog(R.string.mismatchPin2, true);
                } else {
                    this.mButtonChangePin2.setText("");
                    Message onComplete = this.mFDNHandler.obtainMessage(200);
                    this.mPhone.getIccCard().changeIccFdnPassword(this.mOldPin, this.mNewPin, onComplete);
                }
                break;
            case 3:
                this.mPuk2 = this.mButtonChangePin2.getText();
                this.mButtonChangePin2.setText("");
                if (validatePin(this.mPuk2, true)) {
                    this.mPinChangeState = 4;
                    displayPinChangeDialog();
                } else {
                    displayPinChangeDialog(R.string.invalidPuk2, true);
                }
                break;
            case 4:
                this.mNewPin = this.mButtonChangePin2.getText();
                this.mButtonChangePin2.setText("");
                if (validatePin(this.mNewPin, false)) {
                    this.mPinChangeState = 5;
                    displayPinChangeDialog();
                } else {
                    displayPinChangeDialog(R.string.invalidPin2, true);
                }
                break;
            case 5:
                if (!this.mNewPin.equals(this.mButtonChangePin2.getText())) {
                    this.mPinChangeState = 4;
                    this.mButtonChangePin2.setText("");
                    displayPinChangeDialog(R.string.mismatchPin2, true);
                } else {
                    this.mButtonChangePin2.setText("");
                    Message onComplete2 = this.mFDNHandler.obtainMessage(200);
                    this.mPhone.getIccCard().supplyPuk2(this.mPuk2, this.mNewPin, onComplete2);
                }
                break;
        }
    }

    /* JADX INFO: renamed from: com.android.phone.FdnSetting$2, reason: invalid class name */
    static /* synthetic */ class AnonymousClass2 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$CommandException$Error = new int[CommandException.Error.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$CommandException$Error[CommandException.Error.SIM_PUK2.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$CommandException$Error[CommandException.Error.PASSWORD_INCORRECT.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
        }
    }

    @Override // android.content.DialogInterface.OnCancelListener
    public void onCancel(DialogInterface dialog) {
        resetPinChangeStateForPUK2();
        displayPinChangeDialog(0, true);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void displayMessage(int strId, int attemptsRemaining) {
        String s = getString(strId);
        if (strId == R.string.badPin2 || strId == R.string.badPuk2 || strId == R.string.pin2_invalid) {
            s = attemptsRemaining >= 0 ? getString(strId) + getString(R.string.pin2_attempts, new Object[]{Integer.valueOf(attemptsRemaining)}) : getString(strId);
        }
        log("displayMessage: attemptsRemaining=" + attemptsRemaining + " s=" + s);
        Toast.makeText(this, s, 0).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void displayMessage(int strId) {
        displayMessage(strId, -1);
    }

    private final void displayPinChangeDialog() {
        displayPinChangeDialog(0, true);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void displayPinChangeDialog(int strId, boolean shouldDisplay) {
        int msgId;
        switch (this.mPinChangeState) {
            case 0:
                msgId = R.string.oldPin2Label;
                break;
            case 1:
            case 4:
                msgId = R.string.newPin2Label;
                break;
            case 2:
            case 5:
                msgId = R.string.confirmPin2Label;
                break;
            case 3:
            default:
                msgId = R.string.label_puk2_code;
                break;
        }
        if (strId != 0) {
            this.mButtonChangePin2.setDialogMessage(((Object) getText(msgId)) + "\n" + ((Object) getText(strId)));
        } else {
            this.mButtonChangePin2.setDialogMessage(msgId);
        }
        if (shouldDisplay) {
            this.mButtonChangePin2.showPinDialog();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void resetPinChangeState() {
        this.mPinChangeState = 0;
        displayPinChangeDialog(0, false);
        this.mNewPin = "";
        this.mOldPin = "";
        this.mIsPuk2Locked = false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void resetPinChangeStateForPUK2() {
        this.mPinChangeState = 3;
        displayPinChangeDialog(0, false);
        this.mPuk2 = "";
        this.mNewPin = "";
        this.mOldPin = "";
        this.mIsPuk2Locked = true;
    }

    private boolean validatePin(String pin, boolean isPuk) {
        int pinMinimum = isPuk ? 8 : 4;
        if (pin == null || pin.length() < pinMinimum || pin.length() > 8) {
            return false;
        }
        return true;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateEnableFDN() {
        if (this.mPhone.getIccCard().getIccFdnEnabled()) {
            this.mButtonEnableFDN.setTitle(R.string.enable_fdn_ok);
            this.mButtonEnableFDN.setSummary(R.string.fdn_enabled);
            this.mButtonEnableFDN.setDialogTitle(R.string.disable_fdn);
        } else {
            this.mButtonEnableFDN.setTitle(R.string.disable_fdn_ok);
            this.mButtonEnableFDN.setSummary(R.string.fdn_disabled);
            this.mButtonEnableFDN.setDialogTitle(R.string.enable_fdn);
        }
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            addPreferencesFromResource(R.xml.msim_fdn_setting);
        } else {
            addPreferencesFromResource(R.xml.fdn_setting);
        }
        this.mSubscription = getIntent().getIntExtra("subscription", PhoneGlobals.getInstance().getDefaultSubscription());
        Log.d("PhoneApp", "Getting FDNSetting subscription =" + this.mSubscription);
        this.mPhone = PhoneGlobals.getInstance().getPhone(this.mSubscription);
        this.mSubscriptionPrefFDN = (PreferenceScreen) findPreference("button_fdn_list_key");
        this.mSubscriptionPrefFDN.getIntent().putExtra("subscription", this.mSubscription);
        PreferenceScreen prefSet = getPreferenceScreen();
        this.mButtonEnableFDN = (EditPinPreference) prefSet.findPreference("button_fdn_enable_key");
        this.mButtonChangePin2 = (EditPinPreference) prefSet.findPreference("button_change_pin2_key");
        this.mButtonEnableFDN.setOnPinEnteredListener(this);
        updateEnableFDN();
        this.mButtonChangePin2.setOnPinEnteredListener(this);
        if (icicle == null) {
            resetPinChangeState();
        } else {
            this.mIsPuk2Locked = icicle.getBoolean("skip_old_pin_key");
            this.mPinChangeState = icicle.getInt("pin_change_state_key");
            this.mOldPin = icicle.getString("old_pin_key");
            this.mNewPin = icicle.getString("new_pin_key");
            this.mButtonChangePin2.setDialogMessage(icicle.getString("dialog_message_key"));
            this.mButtonChangePin2.setText(icicle.getString("dialog_pin_entry_key"));
        }
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        this.mPhone = PhoneGlobals.getInstance().getPhone(this.mSubscription);
        updateEnableFDN();
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("skip_old_pin_key", this.mIsPuk2Locked);
        out.putInt("pin_change_state_key", this.mPinChangeState);
        out.putString("old_pin_key", this.mOldPin);
        out.putString("new_pin_key", this.mNewPin);
        out.putString("dialog_message_key", this.mButtonChangePin2.getDialogMessage().toString());
        out.putString("dialog_pin_entry_key", this.mButtonChangePin2.getText());
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

    /* JADX INFO: Access modifiers changed from: private */
    public void log(String msg) {
        Log.d("PhoneApp", "FdnSetting: " + msg);
    }
}
