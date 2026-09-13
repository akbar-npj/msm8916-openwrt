package com.android.phone;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import com.android.internal.telephony.Phone;

/* JADX INFO: loaded from: classes.dex */
public class CallBarring extends PreferenceActivity implements DialogInterface.OnClickListener, Preference.OnPreferenceChangeListener, EditPinPreference.OnPinEnteredListener {
    private Phone mPhone;
    private int mOutgoingState = -1;
    private int mIncomingState = -2;
    private int mSetOutgoing = 99;
    private int mSetIncoming = 99;
    private int mDialogState = 0;
    private boolean mCBDataStale = true;
    private boolean mIsBusyDialogAvailable = false;
    private String mPassword = null;
    private String mNewPsw = null;
    private String mError = null;
    private int mSubscription = 0;
    private ListPreference mListOutgoing = null;
    private ListPreference mListIncoming = null;
    private EditPinPreference mDialogCancelAll = null;
    private EditPinPreference mDialogChangePSW = null;
    private Handler mGetAllCBOptionsComplete = new Handler() { // from class: com.android.phone.CallBarring.1
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            switch (msg.what) {
                case 100:
                    int status = CallBarring.this.handleGetCBMessage(ar, msg.arg1);
                    if (status == 100) {
                        switch (msg.arg1) {
                            case 0:
                                CallBarring.this.mPhone.getCallBarringOption("OI", "", Message.obtain(CallBarring.this.mGetAllCBOptionsComplete, 100, 1, 0));
                                break;
                            case 1:
                                CallBarring.this.mPhone.getCallBarringOption("OX", "", Message.obtain(CallBarring.this.mGetAllCBOptionsComplete, 100, 2, 0));
                                break;
                            case 2:
                                CallBarring.this.mPhone.getCallBarringOption("AI", "", Message.obtain(CallBarring.this.mGetAllCBOptionsComplete, 100, 3, 0));
                                break;
                            case 3:
                                CallBarring.this.mPhone.getCallBarringOption("IR", "", Message.obtain(CallBarring.this.mGetAllCBOptionsComplete, 100, 4, 0));
                                break;
                            case 4:
                                CallBarring.this.mCBDataStale = false;
                                CallBarring.this.syncUiWithState();
                                CallBarring.this.removeDialog(500);
                                break;
                        }
                    } else {
                        CallBarring.this.removeDialog(500);
                        Log.d("CallBarring", "EXCEPTION_ERROR!");
                        break;
                    }
                    break;
                case 200:
                    removeMessages(100);
                    CallBarring.this.removeDialog(500);
                    CallBarring.this.finish();
                    break;
            }
        }
    };
    private Handler mSetOptionComplete = new Handler() { // from class: com.android.phone.CallBarring.2
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            switch (msg.what) {
                case 300:
                    CallBarring.this.dismissBusyDialog();
                    if (ar.exception == null) {
                        CallBarring.this.mOutgoingState = -1;
                        CallBarring.this.mIncomingState = -2;
                        CallBarring.this.syncUiWithState();
                        CallBarring.this.showToast(CallBarring.this.getResources().getString(R.string.operation_successfully));
                    } else {
                        CallBarring.this.finish();
                        CallBarring.this.showToast(CallBarring.this.getResources().getString(R.string.response_error));
                    }
                    break;
                case 400:
                    CallBarring.this.dismissBusyDialog();
                    if (ar.exception != null) {
                        CallBarring.this.mSetOutgoing = 99;
                        CallBarring.this.mSetIncoming = 99;
                        CallBarring.this.showToast(CallBarring.this.getResources().getString(R.string.response_error));
                    } else {
                        if (CallBarring.this.mOutgoingState != CallBarring.this.mSetOutgoing && CallBarring.this.mSetOutgoing != 99) {
                            CallBarring.this.mOutgoingState = CallBarring.this.mSetOutgoing;
                        }
                        if (CallBarring.this.mIncomingState != CallBarring.this.mSetIncoming && CallBarring.this.mSetIncoming != 99) {
                            CallBarring.this.mIncomingState = CallBarring.this.mSetIncoming;
                        }
                        CallBarring.this.syncUiWithState();
                        CallBarring.this.showToast(CallBarring.this.getResources().getString(R.string.operation_successfully));
                    }
                    break;
                case 500:
                    CallBarring.this.dismissBusyDialog();
                    if (ar.exception != null) {
                        CallBarring.this.showToast(CallBarring.this.getResources().getString(R.string.response_error));
                    } else {
                        CallBarring.this.showToast(CallBarring.this.getResources().getString(R.string.operation_successfully));
                    }
                    break;
            }
        }
    };

    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.call_barring);
        log("onCreate");
        this.mSubscription = getIntent().getIntExtra("subscription", PhoneGlobals.getInstance().getDefaultSubscription());
        log("mSubscription: " + this.mSubscription);
        this.mPhone = PhoneGlobals.getInstance().getPhone(this.mSubscription);
        PreferenceScreen prefSet = getPreferenceScreen();
        this.mListOutgoing = (ListPreference) prefSet.findPreference("call_barring_outgoing_key");
        this.mListIncoming = (ListPreference) prefSet.findPreference("call_barring_incoming_key");
        this.mListOutgoing.setOnPreferenceChangeListener(this);
        this.mListIncoming.setOnPreferenceChangeListener(this);
        this.mDialogCancelAll = (EditPinPreference) prefSet.findPreference("call_barring_cancel_all_key");
        this.mDialogChangePSW = (EditPinPreference) prefSet.findPreference("call_barring_change_psw_key");
        this.mDialogCancelAll.setOnPinEnteredListener(this);
        this.mDialogChangePSW.setOnPinEnteredListener(this);
        if (icicle != null) {
            this.mOutgoingState = icicle.getInt("call_barring_outgoing_key");
            this.mIncomingState = icicle.getInt("call_barring_incoming_key");
            this.mSetOutgoing = icicle.getInt("SETOUTGOING_KEY");
            this.mSetIncoming = icicle.getInt("SETINCOMING_KEY");
            this.mDialogState = icicle.getInt("DIALOGSTATE_KEY");
            this.mCBDataStale = icicle.getBoolean("CBDATASTALE_KEY");
            this.mIsBusyDialogAvailable = icicle.getBoolean("ISBUSYDIALOGAVAILABLE_KEY");
            this.mPassword = icicle.getString("PASSWORD_KEY");
            this.mNewPsw = icicle.getString("NEW_PSW_KEY");
            this.mError = icicle.getString("ERROR_KEY");
            return;
        }
        this.mOutgoingState = -1;
        this.mIncomingState = -2;
        this.mSetOutgoing = 99;
        this.mSetIncoming = 99;
        this.mDialogState = 3;
        this.mCBDataStale = true;
        this.mIsBusyDialogAvailable = false;
        this.mPassword = null;
        this.mNewPsw = null;
        this.mError = null;
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        log("onResume");
        if (this.mCBDataStale) {
            if (Settings.System.getInt(getContentResolver(), "airplane_mode_on", 0) == 0) {
                queryAllCBOptions();
                return;
            }
            log("onResume: airplane mode on");
            showDialog(400);
            finish();
            return;
        }
        this.mListOutgoing.setValue(String.valueOf(this.mOutgoingState));
        this.mListOutgoing.setSummary(this.mListOutgoing.getEntry());
        this.mListIncoming.setValue(String.valueOf(this.mIncomingState));
        this.mListIncoming.setSummary(this.mListIncoming.getEntry());
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        log("onSaveInstanceState: saving relevant UI state.");
        outState.putInt("call_barring_outgoing_key", this.mOutgoingState);
        outState.putInt("call_barring_incoming_key", this.mIncomingState);
        outState.putInt("SETOUTGOING_KEY", this.mSetOutgoing);
        outState.putInt("SETINCOMING_KEY", this.mSetIncoming);
        outState.putInt("DIALOGSTATE_KEY", this.mDialogState);
        outState.putBoolean("CBDATASTALE_KEY", this.mCBDataStale);
        outState.putBoolean("ISBUSYDIALOGAVAILABLE_KEY", this.mIsBusyDialogAvailable);
        outState.putString("PASSWORD_KEY", this.mPassword);
        outState.putString("NEW_PSW_KEY", this.mNewPsw);
        outState.putString("ERROR_KEY", this.mError);
    }

    private void queryAllCBOptions() {
        showDialog(500);
        this.mPhone.getCallBarringOption("AO", "", Message.obtain(this.mGetAllCBOptionsComplete, 100, 0, 0));
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
    public int handleGetCBMessage(AsyncResult ar, int reason) {
        if (ar.exception != null) {
            Log.e("CallBarring", "handleGetCBMessage: Error getting CB enable state.");
            return 200;
        }
        if (ar.userObj instanceof Throwable) {
            Log.e("CallBarring", "handleGetCBMessage: Error during set call barring, reason: " + reason + " exception: " + ((Throwable) ar.userObj).toString());
            return 300;
        }
        int cbState = ((int[]) ar.result)[0];
        if (cbState == 0) {
            if (this.mOutgoingState == reason) {
                this.mOutgoingState = -1;
            }
            if (this.mIncomingState == reason) {
                this.mIncomingState = -2;
            }
        } else if (cbState == 1) {
            switch (reason) {
                case 0:
                case 1:
                case 2:
                    this.mOutgoingState = reason;
                    break;
                case 3:
                case 4:
                    this.mIncomingState = reason;
                    break;
            }
        } else {
            Log.e("CallBarring", "handleGetCBMessage: Error getting CB state, unexpected value.");
            return 300;
        }
        return 100;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void syncUiWithState() {
        this.mListOutgoing.setValue(String.valueOf(this.mOutgoingState));
        this.mListOutgoing.setSummary(this.mListOutgoing.getEntry());
        this.mListIncoming.setValue(String.valueOf(this.mIncomingState));
        this.mListIncoming.setSummary(this.mListIncoming.getEntry());
        this.mSetOutgoing = 99;
        this.mSetIncoming = 99;
    }

    @Override // android.preference.Preference.OnPreferenceChangeListener
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (this.mListOutgoing == preference) {
            if (!this.mListOutgoing.getValue().equals((String) newValue)) {
                this.mSetOutgoing = Integer.valueOf((String) newValue).intValue();
                resetDialogState(1);
                showPswDialog();
                return false;
            }
        } else if (this.mListIncoming == preference && !this.mListOutgoing.getValue().equals((String) newValue)) {
            this.mSetIncoming = Integer.valueOf((String) newValue).intValue();
            resetDialogState(2);
            showPswDialog();
            return false;
        }
        return true;
    }

    private void resetDialogState(int mode) {
        this.mDialogState = mode;
        this.mPassword = null;
        this.mNewPsw = null;
        this.mError = null;
        this.mDialogChangePSW.setDialogMessage(getResources().getString(R.string.psw_enter_old));
    }

    private void showPswDialog() {
        if (this.mDialogState != 0) {
            String message = null;
            switch (this.mDialogState) {
                case 1:
                case 2:
                    this.mDialogChangePSW.setDialogTitle(getResources().getString(R.string.input_password));
                    break;
                case 3:
                    message = getResources().getString(R.string.psw_enter_old);
                    this.mDialogChangePSW.setDialogTitle(getResources().getString(R.string.labelCbChangePassword));
                    break;
                case 4:
                    message = getResources().getString(R.string.psw_enter_new);
                    this.mDialogChangePSW.setDialogTitle(getResources().getString(R.string.labelCbChangePassword));
                    break;
                case 5:
                    message = getResources().getString(R.string.psw_reenter_new);
                    this.mDialogChangePSW.setDialogTitle(getResources().getString(R.string.labelCbChangePassword));
                    break;
            }
            if (this.mError != null) {
                if (message != null) {
                    message = this.mError + "\n" + message;
                } else {
                    message = this.mError;
                }
                this.mError = null;
            }
            this.mDialogChangePSW.setText("");
            this.mDialogChangePSW.setDialogMessage(message);
            this.mDialogChangePSW.showPinDialog();
        }
    }

    @Override // com.android.phone.EditPinPreference.OnPinEnteredListener
    public void onPinEntered(EditPinPreference preference, boolean positiveResult) {
        boolean lockState;
        String facility;
        if (!positiveResult) {
            this.mSetOutgoing = 99;
            this.mSetIncoming = 99;
            resetDialogState(3);
        }
        String tmpPsw = preference.getText();
        preference.setText("");
        if (preference == this.mDialogCancelAll) {
            if (this.mOutgoingState == -1 && this.mIncomingState == -2) {
                showToast(getResources().getString(R.string.no_call_barring));
                return;
            } else if (!reasonablePSW(tmpPsw)) {
                this.mError = getResources().getString(R.string.invalidPsw);
                showCancelDialog();
                return;
            } else {
                showDialog(100);
                this.mPhone.setCallBarringOption("AB", false, tmpPsw, Message.obtain(this.mSetOptionComplete, 300));
                return;
            }
        }
        if (preference == this.mDialogChangePSW) {
            switch (this.mDialogState) {
                case 1:
                case 2:
                    if (!reasonablePSW(tmpPsw)) {
                        this.mError = getResources().getString(R.string.invalidPsw);
                        showPswDialog();
                    } else {
                        showDialog(100);
                        if (this.mSetOutgoing != 99 && this.mSetOutgoing != this.mOutgoingState) {
                            lockState = this.mSetOutgoing >= 0;
                            facility = getBarringFacility(lockState ? this.mSetOutgoing : this.mOutgoingState);
                        } else if (this.mSetIncoming != 99 && this.mSetIncoming != this.mIncomingState) {
                            lockState = this.mSetIncoming >= 0;
                            facility = getBarringFacility(lockState ? this.mSetIncoming : this.mIncomingState);
                        } else {
                            dismissBusyDialog();
                            showToast(getResources().getString(R.string.no_call_barring));
                            Log.e("CallBarring", "Call barring state error!");
                        }
                        this.mPhone.setCallBarringOption(facility, lockState, tmpPsw, Message.obtain(this.mSetOptionComplete, 400));
                        resetDialogState(3);
                    }
                    break;
                case 3:
                    if (!reasonablePSW(tmpPsw)) {
                        this.mError = getResources().getString(R.string.invalidPsw);
                        showPswDialog();
                    } else {
                        this.mPassword = tmpPsw;
                        this.mDialogState = 4;
                        showPswDialog();
                    }
                    break;
                case 4:
                    if (!reasonablePSW(tmpPsw)) {
                        this.mError = getResources().getString(R.string.invalidPsw);
                        showPswDialog();
                    } else {
                        this.mNewPsw = tmpPsw;
                        this.mDialogState = 5;
                        showPswDialog();
                    }
                    break;
                case 5:
                    if (!tmpPsw.equals(this.mNewPsw)) {
                        this.mError = getResources().getString(R.string.cb_psw_dont_match);
                        this.mNewPsw = null;
                        this.mDialogState = 4;
                        showPswDialog();
                    } else {
                        this.mError = null;
                        showDialog(100);
                        this.mPhone.requestChangeCbPsw("AB", this.mPassword, this.mNewPsw, Message.obtain(this.mSetOptionComplete, 500));
                        resetDialogState(3);
                    }
                    break;
            }
        }
    }

    private void showCancelDialog() {
        this.mDialogCancelAll.setText(this.mPassword);
        this.mDialogCancelAll.setDialogMessage(this.mError);
        this.mDialogCancelAll.showPinDialog();
    }

    private boolean reasonablePSW(String psw) {
        return psw != null && psw.length() >= 4 && psw.length() <= 8;
    }

    @Override // android.content.DialogInterface.OnClickListener
    public void onClick(DialogInterface dialog, int which) {
        dialog.dismiss();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showToast(String message) {
        Toast.makeText(this, message, 0).show();
    }

    @Override // android.app.Activity
    protected Dialog onCreateDialog(int id) {
        int msgId;
        if (id == 100 || id == 500) {
            ProgressDialog dialog = new ProgressDialog(this);
            dialog.setTitle(getText(R.string.updating_title));
            dialog.setIndeterminate(true);
            switch (id) {
                case 100:
                    this.mIsBusyDialogAvailable = true;
                    dialog.setCancelable(false);
                    dialog.setMessage(getText(R.string.updating_settings));
                    return dialog;
                case 500:
                    dialog.setCancelable(true);
                    dialog.setCancelMessage(this.mGetAllCBOptionsComplete.obtainMessage(200));
                    dialog.setMessage(getText(R.string.reading_settings));
                    return dialog;
                default:
                    return dialog;
            }
        }
        if (id == 300 || id == 200 || id == 400) {
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            switch (id) {
                case 300:
                    msgId = R.string.response_error;
                    b.setNegativeButton(R.string.close_dialog, this);
                    break;
                case 400:
                    msgId = R.string.radio_off_error;
                    b.setNeutralButton(R.string.close_dialog, this);
                    break;
                default:
                    msgId = R.string.exception_error;
                    b.setNeutralButton(R.string.close_dialog, this);
                    break;
            }
            b.setTitle(getText(R.string.error_updating_title));
            b.setMessage(getText(msgId));
            b.setCancelable(false);
            AlertDialog dialog2 = b.create();
            dialog2.getWindow().addFlags(4);
            return dialog2;
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void dismissBusyDialog() {
        if (this.mIsBusyDialogAvailable) {
            removeDialog(100);
            this.mIsBusyDialogAvailable = false;
        }
    }

    private String getBarringFacility(int cb) {
        switch (cb) {
            case 0:
                return "AO";
            case 1:
                return "OI";
            case 2:
                return "OX";
            case 3:
                return "AI";
            case 4:
                return "IR";
            case 5:
                return "AB";
            default:
                return null;
        }
    }

    private static void log(String msg) {
        Log.d("CallBarring", msg);
    }
}
