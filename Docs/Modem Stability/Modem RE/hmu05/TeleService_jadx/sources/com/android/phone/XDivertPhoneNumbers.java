package com.android.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.telephony.MSimTelephonyManager;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Arrays;

/* JADX INFO: loaded from: classes.dex */
public class XDivertPhoneNumbers extends Activity {
    private Button mButton;
    private EditText[] mLine1Numbers;
    int mNumPhones;
    XDivertUtility mXDivertUtility;
    private View.OnClickListener mClicked = new View.OnClickListener() { // from class: com.android.phone.XDivertPhoneNumbers.3
        @Override // android.view.View.OnClickListener
        public void onClick(View v) {
            if (v == XDivertPhoneNumbers.this.mLine1Numbers[0]) {
                XDivertPhoneNumbers.this.mLine1Numbers[1].requestFocus();
                return;
            }
            if (v == XDivertPhoneNumbers.this.mLine1Numbers[1]) {
                XDivertPhoneNumbers.this.mButton.requestFocus();
                return;
            }
            if (v == XDivertPhoneNumbers.this.mButton) {
                if (XDivertPhoneNumbers.this.isValidNumbers()) {
                    XDivertPhoneNumbers.this.processXDivert();
                } else {
                    Toast toast = Toast.makeText(XDivertPhoneNumbers.this.getApplicationContext(), R.string.xdivert_enternumber_error, 1);
                    toast.show();
                }
            }
        }
    };
    View.OnFocusChangeListener mOnFocusChangeHandler = new View.OnFocusChangeListener() { // from class: com.android.phone.XDivertPhoneNumbers.4
        @Override // android.view.View.OnFocusChangeListener
        public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus) {
                TextView textView = (TextView) v;
                Selection.selectAll((Spannable) textView.getText());
            }
        }
    };

    @Override // android.app.Activity
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        getIntent();
        this.mNumPhones = MSimTelephonyManager.getDefault().getPhoneCount();
        this.mXDivertUtility = XDivertUtility.getInstance();
        setContentView(R.layout.xdivert_phone_numbers);
        boolean isImsiReady = this.mXDivertUtility.checkImsiReady();
        Log.d("XDivertPhoneNumbers", "onCreate isImsiReady = " + isImsiReady);
        if (!isImsiReady) {
            displayAlertDialog(R.string.xdivert_imsi_not_read);
        } else {
            setupView();
        }
    }

    @Override // android.app.Activity, android.content.ComponentCallbacks
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private void displayAlertDialog(int resId) {
        new AlertDialog.Builder(this).setMessage(resId).setTitle(R.string.xdivert_title).setIcon(android.R.drawable.ic_dialog_alert).setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() { // from class: com.android.phone.XDivertPhoneNumbers.2
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int whichButton) {
                Log.d("XDivertPhoneNumbers", "XDivertPhoneNumbers onClick");
            }
        }).show().setOnDismissListener(new DialogInterface.OnDismissListener() { // from class: com.android.phone.XDivertPhoneNumbers.1
            @Override // android.content.DialogInterface.OnDismissListener
            public void onDismiss(DialogInterface dialog) {
                Log.d("XDivertPhoneNumbers", "XDivertPhoneNumbers onDismiss");
                XDivertPhoneNumbers.this.finish();
            }
        });
    }

    private void setupView() {
        int[] numberEditTextId = {R.id.sub1_number, R.id.sub2_number};
        this.mLine1Numbers = new EditText[this.mNumPhones];
        String[] subLine1Number = this.mXDivertUtility.getLineNumbers();
        for (int i = 0; i < this.mNumPhones; i++) {
            Log.d("XDivertPhoneNumbers", "setupView sub" + (i + 1) + " line number = " + subLine1Number[i]);
            this.mLine1Numbers[i] = (EditText) findViewById(numberEditTextId[i]);
            if (this.mLine1Numbers[i] != null) {
                this.mLine1Numbers[i].setText(subLine1Number[i]);
                this.mLine1Numbers[i].setOnFocusChangeListener(this.mOnFocusChangeHandler);
                this.mLine1Numbers[i].setOnClickListener(this.mClicked);
            }
        }
        this.mButton = (Button) findViewById(R.id.button);
        if (this.mButton != null) {
            this.mButton.setOnClickListener(this.mClicked);
        }
    }

    private String[] getLine1Numbers() {
        String[] line1Numbers = new String[this.mNumPhones];
        for (int i = 0; i < this.mNumPhones; i++) {
            line1Numbers[i] = this.mLine1Numbers[i].getText().toString();
        }
        return line1Numbers;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void processXDivert() {
        Intent intent = new Intent();
        intent.setClass(this, XDivertSetting.class);
        Log.d("XDivertPhoneNumbers", "OnSave: line numbers = " + Arrays.toString(getLine1Numbers()));
        intent.putExtra("Line1Numbers", getLine1Numbers());
        startActivity(intent);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isValidNumbers() {
        for (int i = 0; i < this.mNumPhones; i++) {
            String num = this.mLine1Numbers[i].getText().toString();
            if (TextUtils.isEmpty(num)) {
                return false;
            }
        }
        return true;
    }
}
