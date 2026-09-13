package com.android.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.method.DigitsKeyListener;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.Phone;

/* JADX INFO: loaded from: classes.dex */
public class ChangeIccPinScreen extends Activity {
    private TextView mBadPinError;
    private Button mButton;
    private boolean mChangePin2;
    private LinearLayout mIccPUKPanel;
    private TextView mMismatchError;
    private EditText mNewPin1;
    private EditText mNewPin2;
    private EditText mOldPin;
    private AlertDialog mPUKAlert;
    private EditText mPUKCode;
    private Button mPUKSubmit;
    private Phone mPhone;
    private ScrollView mScrollView;
    private EntryState mState;
    private Handler mHandler = new Handler() { // from class: com.android.phone.ChangeIccPinScreen.1
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 100:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    ChangeIccPinScreen.this.handleResult(ar);
                    break;
            }
        }
    };
    private View.OnClickListener mClicked = new View.OnClickListener() { // from class: com.android.phone.ChangeIccPinScreen.2
        @Override // android.view.View.OnClickListener
        public void onClick(View v) {
            CharSequence text;
            if (v == ChangeIccPinScreen.this.mOldPin) {
                ChangeIccPinScreen.this.mNewPin1.requestFocus();
                return;
            }
            if (v == ChangeIccPinScreen.this.mNewPin1) {
                ChangeIccPinScreen.this.mNewPin2.requestFocus();
                return;
            }
            if (v == ChangeIccPinScreen.this.mNewPin2) {
                ChangeIccPinScreen.this.mButton.requestFocus();
                return;
            }
            if (v == ChangeIccPinScreen.this.mButton) {
                IccCard iccCardInterface = ChangeIccPinScreen.this.mPhone.getIccCard();
                if (iccCardInterface != null) {
                    String oldPin = ChangeIccPinScreen.this.mOldPin.getText().toString();
                    String newPin1 = ChangeIccPinScreen.this.mNewPin1.getText().toString();
                    String newPin2 = ChangeIccPinScreen.this.mNewPin2.getText().toString();
                    int error = ChangeIccPinScreen.this.validateNewPin(newPin1, newPin2);
                    switch (error) {
                        case 1:
                        case 2:
                            ChangeIccPinScreen.this.mNewPin1.getText().clear();
                            ChangeIccPinScreen.this.mNewPin2.getText().clear();
                            ChangeIccPinScreen.this.mMismatchError.setVisibility(0);
                            Resources r = ChangeIccPinScreen.this.getResources();
                            if (error == 1) {
                                text = r.getString(R.string.mismatchPin);
                            } else {
                                text = r.getString(R.string.invalidPin);
                            }
                            ChangeIccPinScreen.this.mMismatchError.setText(text);
                            break;
                        default:
                            Message callBack = Message.obtain(ChangeIccPinScreen.this.mHandler, 100);
                            ChangeIccPinScreen.this.reset();
                            if (ChangeIccPinScreen.this.mChangePin2) {
                                iccCardInterface.changeIccFdnPassword(oldPin, newPin1, callBack);
                            } else {
                                iccCardInterface.changeIccLockPassword(oldPin, newPin1, callBack);
                            }
                            break;
                    }
                    return;
                }
                return;
            }
            if (v == ChangeIccPinScreen.this.mPUKCode) {
                ChangeIccPinScreen.this.mPUKSubmit.requestFocus();
            } else if (v == ChangeIccPinScreen.this.mPUKSubmit) {
                ChangeIccPinScreen.this.mPhone.getIccCard().supplyPuk2(ChangeIccPinScreen.this.mPUKCode.getText().toString(), ChangeIccPinScreen.this.mNewPin1.getText().toString(), Message.obtain(ChangeIccPinScreen.this.mHandler, 100));
            }
        }
    };

    private enum EntryState {
        ES_PIN,
        ES_PUK
    }

    @Override // android.app.Activity
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        this.mPhone = PhoneGlobals.getPhone();
        resolveIntent();
        setContentView(R.layout.change_sim_pin_screen);
        this.mOldPin = (EditText) findViewById(R.id.old_pin);
        this.mOldPin.setKeyListener(DigitsKeyListener.getInstance());
        this.mOldPin.setMovementMethod(null);
        this.mOldPin.setOnClickListener(this.mClicked);
        this.mNewPin1 = (EditText) findViewById(R.id.new_pin1);
        this.mNewPin1.setKeyListener(DigitsKeyListener.getInstance());
        this.mNewPin1.setMovementMethod(null);
        this.mNewPin1.setOnClickListener(this.mClicked);
        this.mNewPin2 = (EditText) findViewById(R.id.new_pin2);
        this.mNewPin2.setKeyListener(DigitsKeyListener.getInstance());
        this.mNewPin2.setMovementMethod(null);
        this.mNewPin2.setOnClickListener(this.mClicked);
        this.mBadPinError = (TextView) findViewById(R.id.bad_pin);
        this.mMismatchError = (TextView) findViewById(R.id.mismatch);
        this.mButton = (Button) findViewById(R.id.button);
        this.mButton.setOnClickListener(this.mClicked);
        this.mScrollView = (ScrollView) findViewById(R.id.scroll);
        this.mPUKCode = (EditText) findViewById(R.id.puk_code);
        this.mPUKCode.setKeyListener(DigitsKeyListener.getInstance());
        this.mPUKCode.setMovementMethod(null);
        this.mPUKCode.setOnClickListener(this.mClicked);
        this.mPUKSubmit = (Button) findViewById(R.id.puk_submit);
        this.mPUKSubmit.setOnClickListener(this.mClicked);
        this.mIccPUKPanel = (LinearLayout) findViewById(R.id.puk_panel);
        int id = this.mChangePin2 ? R.string.change_pin2 : R.string.change_pin;
        setTitle(getResources().getText(id));
        this.mState = EntryState.ES_PIN;
    }

    private void resolveIntent() {
        Intent intent = getIntent();
        this.mChangePin2 = intent.getBooleanExtra("pin2", this.mChangePin2);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void reset() {
        this.mScrollView.scrollTo(0, 0);
        this.mBadPinError.setVisibility(8);
        this.mMismatchError.setVisibility(8);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int validateNewPin(String p1, String p2) {
        if (p1 == null) {
            return 2;
        }
        if (!p1.equals(p2)) {
            return 1;
        }
        int len1 = p1.length();
        return (len1 < 4 || len1 > 8) ? 2 : 0;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleResult(AsyncResult ar) {
        if (ar.exception == null) {
            if (this.mState == EntryState.ES_PUK) {
                this.mScrollView.setVisibility(0);
                this.mIccPUKPanel.setVisibility(8);
            }
            showConfirmation();
            this.mHandler.postDelayed(new Runnable() { // from class: com.android.phone.ChangeIccPinScreen.3
                @Override // java.lang.Runnable
                public void run() {
                    ChangeIccPinScreen.this.finish();
                }
            }, 3000L);
            return;
        }
        if (ar.exception instanceof CommandException) {
            if (this.mState == EntryState.ES_PIN) {
                this.mOldPin.getText().clear();
                this.mBadPinError.setVisibility(0);
                CommandException ce = ar.exception;
                if (ce.getCommandError() == CommandException.Error.SIM_PUK2) {
                    this.mState = EntryState.ES_PUK;
                    displayPUKAlert();
                    this.mScrollView.setVisibility(8);
                    this.mIccPUKPanel.setVisibility(0);
                    this.mPUKCode.requestFocus();
                    return;
                }
                return;
            }
            if (this.mState == EntryState.ES_PUK) {
                displayPUKAlert();
                this.mPUKCode.getText().clear();
                this.mPUKCode.requestFocus();
            }
        }
    }

    private void displayPUKAlert() {
        if (this.mPUKAlert == null) {
            this.mPUKAlert = new AlertDialog.Builder(this).setMessage(R.string.puk_requested).setCancelable(false).show();
        } else {
            this.mPUKAlert.show();
        }
        this.mHandler.postDelayed(new Runnable() { // from class: com.android.phone.ChangeIccPinScreen.4
            @Override // java.lang.Runnable
            public void run() {
                ChangeIccPinScreen.this.mPUKAlert.dismiss();
            }
        }, 3000L);
    }

    private void showConfirmation() {
        int id = this.mChangePin2 ? R.string.pin2_changed : R.string.pin_changed;
        Toast.makeText(this, id, 0).show();
    }
}
