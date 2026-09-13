package com.android.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.MSimTelephonyManager;
import android.util.Log;

/* JADX INFO: loaded from: classes.dex */
public class ErrorDialogActivity extends Activity {
    private static final String TAG = ErrorDialogActivity.class.getSimpleName();

    @Override // android.app.Activity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean showVoicemailDialog = getIntent().getBooleanExtra("show_missing_voicemail", false);
        if (showVoicemailDialog) {
            showMissingVoicemailErrorDialog();
            return;
        }
        int error = getIntent().getIntExtra("error_message_id", -1);
        if (error == -1) {
            Log.e(TAG, "ErrorDialogActivity called with no error type extra.");
            finish();
        }
        showGenericErrorDialog(error);
    }

    private void showGenericErrorDialog(int resid) {
        CharSequence msg = getResources().getText(resid);
        DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() { // from class: com.android.phone.ErrorDialogActivity.1
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                ErrorDialogActivity.this.finish();
            }
        };
        DialogInterface.OnCancelListener cancelListener = new DialogInterface.OnCancelListener() { // from class: com.android.phone.ErrorDialogActivity.2
            @Override // android.content.DialogInterface.OnCancelListener
            public void onCancel(DialogInterface dialog) {
                ErrorDialogActivity.this.finish();
            }
        };
        AlertDialog errorDialog = new AlertDialog.Builder(this).setMessage(msg).setPositiveButton(R.string.ok, clickListener).setOnCancelListener(cancelListener).create();
        errorDialog.show();
    }

    private void showMissingVoicemailErrorDialog() {
        new AlertDialog.Builder(this).setTitle(R.string.no_vm_number).setMessage(R.string.no_vm_number_msg).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() { // from class: com.android.phone.ErrorDialogActivity.5
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                ErrorDialogActivity.this.dontAddVoiceMailNumber();
            }
        }).setNegativeButton(R.string.add_vm_number_str, new DialogInterface.OnClickListener() { // from class: com.android.phone.ErrorDialogActivity.4
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                ErrorDialogActivity.this.addVoiceMailNumberPanel(dialog);
            }
        }).setOnCancelListener(new DialogInterface.OnCancelListener() { // from class: com.android.phone.ErrorDialogActivity.3
            @Override // android.content.DialogInterface.OnCancelListener
            public void onCancel(DialogInterface dialog) {
                ErrorDialogActivity.this.dontAddVoiceMailNumber();
            }
        }).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void addVoiceMailNumberPanel(DialogInterface dialogInterface) {
        if (dialogInterface != null) {
            dialogInterface.dismiss();
        }
        Intent intent = new Intent("com.android.phone.CallFeaturesSetting.ADD_VOICEMAIL");
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            intent.setClass(this, SelectSubscription.class);
            intent.putExtra("subscription", MSimPhoneGlobals.getInstance().callController.getVoiceMailSub());
            intent.putExtra("PACKAGE", "com.android.phone");
            intent.putExtra("TARGET_CLASS", "com.android.phone.MSimCallFeaturesSubSetting");
        } else {
            intent.setClass(this, CallFeaturesSetting.class);
        }
        startActivity(intent);
        finish();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void dontAddVoiceMailNumber() {
        finish();
    }
}
