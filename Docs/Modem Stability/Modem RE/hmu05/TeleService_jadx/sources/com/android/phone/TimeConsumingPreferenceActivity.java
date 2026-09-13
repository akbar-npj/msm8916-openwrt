package com.android.phone;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;
import com.android.internal.telephony.CommandException;
import java.util.ArrayList;

/* JADX INFO: loaded from: classes.dex */
public class TimeConsumingPreferenceActivity extends PreferenceActivity implements DialogInterface.OnCancelListener, TimeConsumingPreferenceListener {
    private final DialogInterface.OnClickListener mDismiss;
    private final DialogInterface.OnClickListener mDismissAndFinish;
    private final boolean DBG = true;
    private final ArrayList<String> mBusyList = new ArrayList<>();
    protected boolean mIsForeground = false;

    public TimeConsumingPreferenceActivity() {
        this.mDismiss = new DismissOnClickListener();
        this.mDismissAndFinish = new DismissAndFinishOnClickListener();
    }

    private class DismissOnClickListener implements DialogInterface.OnClickListener {
        private DismissOnClickListener() {
        }

        @Override // android.content.DialogInterface.OnClickListener
        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
        }
    }

    private class DismissAndFinishOnClickListener implements DialogInterface.OnClickListener {
        private DismissAndFinishOnClickListener() {
        }

        @Override // android.content.DialogInterface.OnClickListener
        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            TimeConsumingPreferenceActivity.this.finish();
        }
    }

    @Override // android.app.Activity
    protected Dialog onCreateDialog(int id) {
        int msgId;
        if (id == 100 || id == 200) {
            ProgressDialog dialog = new ProgressDialog(this);
            dialog.setTitle(getText(R.string.updating_title));
            dialog.setIndeterminate(true);
            switch (id) {
                case 100:
                    dialog.setCancelable(true);
                    dialog.setOnCancelListener(this);
                    dialog.setMessage(getText(R.string.reading_settings));
                    return dialog;
                case 200:
                    dialog.setCancelable(false);
                    dialog.setMessage(getText(R.string.updating_settings));
                    return dialog;
                default:
                    return null;
            }
        }
        if (id != 400 && id != 500 && id != 300 && id != 600 && id != 700 && id != 800 && id != 900) {
            return null;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        switch (id) {
            case 300:
                msgId = R.string.exception_error;
                builder.setPositiveButton(R.string.close_dialog, this.mDismiss);
                break;
            case 400:
                msgId = R.string.response_error;
                builder.setPositiveButton(R.string.close_dialog, this.mDismiss);
                break;
            case 500:
                msgId = R.string.radio_off_error;
                builder.setPositiveButton(R.string.close_dialog, this.mDismissAndFinish);
                break;
            case 600:
                msgId = R.string.fdn_check_failure;
                builder.setPositiveButton(R.string.close_dialog, this.mDismiss);
                break;
            case 700:
                msgId = R.string.stk_cc_ss_to_dial_error;
                builder.setPositiveButton(R.string.close_dialog, this.mDismiss);
                break;
            case 800:
                msgId = R.string.stk_cc_ss_to_ussd_error;
                builder.setPositiveButton(R.string.close_dialog, this.mDismiss);
                break;
            case 900:
                msgId = R.string.stk_cc_ss_to_ss_error;
                builder.setPositiveButton(R.string.close_dialog, this.mDismiss);
                break;
            default:
                msgId = R.string.exception_error;
                builder.setPositiveButton(R.string.close_dialog, this.mDismissAndFinish);
                break;
        }
        builder.setTitle(getText(R.string.error_updating_title));
        builder.setMessage(getText(msgId));
        builder.setCancelable(false);
        AlertDialog dialog2 = builder.create();
        dialog2.getWindow().addFlags(4);
        return dialog2;
    }

    @Override // android.app.Activity
    public void onResume() {
        super.onResume();
        this.mIsForeground = true;
    }

    @Override // android.app.Activity
    public void onPause() {
        super.onPause();
        this.mIsForeground = false;
    }

    @Override // com.android.phone.TimeConsumingPreferenceListener
    public void onStarted(Preference preference, boolean reading) {
        dumpState();
        Log.d("TimeConsumingPreferenceActivity", "onStarted, preference=" + preference.getKey() + ", reading=" + reading);
        this.mBusyList.add(preference.getKey());
        if (this.mIsForeground) {
            if (reading) {
                showDialog(100);
            } else {
                showDialog(200);
            }
        }
    }

    public void onFinished(Preference preference, boolean reading) {
        dumpState();
        Log.d("TimeConsumingPreferenceActivity", "onFinished, preference=" + preference.getKey() + ", reading=" + reading);
        this.mBusyList.remove(preference.getKey());
        if (this.mBusyList.isEmpty()) {
            if (reading) {
                dismissDialogSafely(100);
            } else {
                dismissDialogSafely(200);
            }
        }
        preference.setEnabled(true);
    }

    @Override // com.android.phone.TimeConsumingPreferenceListener
    public void onError(Preference preference, int error) {
        dumpState();
        Log.d("TimeConsumingPreferenceActivity", "onError, preference=" + preference.getKey() + ", error=" + error);
        if (this.mIsForeground) {
            showDialog(error);
        }
    }

    @Override // com.android.phone.TimeConsumingPreferenceListener
    public void onException(Preference preference, CommandException exception) {
        if (exception.getCommandError() == CommandException.Error.FDN_CHECK_FAILURE) {
            onError(preference, 600);
        } else {
            preference.setEnabled(false);
            onError(preference, 300);
        }
    }

    @Override // android.content.DialogInterface.OnCancelListener
    public void onCancel(DialogInterface dialog) {
        dumpState();
        finish();
    }

    private void dismissDialogSafely(int id) {
        try {
            dismissDialog(id);
        } catch (IllegalArgumentException e) {
        }
    }

    void dumpState() {
        Log.d("TimeConsumingPreferenceActivity", "dumpState begin");
        for (String key : this.mBusyList) {
            Log.d("TimeConsumingPreferenceActivity", "mBusyList: key=" + key);
        }
        Log.d("TimeConsumingPreferenceActivity", "dumpState end");
    }
}
