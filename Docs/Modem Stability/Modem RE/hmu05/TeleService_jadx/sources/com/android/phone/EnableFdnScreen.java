package com.android.phone;

import android.app.Activity;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.text.method.DigitsKeyListener;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.Phone;

/* JADX INFO: loaded from: classes.dex */
public class EnableFdnScreen extends Activity {
    private boolean mEnable;
    private Phone mPhone;
    private EditText mPin2Field;
    private LinearLayout mPinFieldContainer;
    private TextView mStatusField;
    private Handler mHandler = new Handler() { // from class: com.android.phone.EnableFdnScreen.1
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 100:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    EnableFdnScreen.this.handleResult(ar);
                    break;
            }
        }
    };
    private View.OnClickListener mClicked = new View.OnClickListener() { // from class: com.android.phone.EnableFdnScreen.3
        @Override // android.view.View.OnClickListener
        public void onClick(View v) {
            if (!TextUtils.isEmpty(EnableFdnScreen.this.mPin2Field.getText())) {
                EnableFdnScreen.this.showStatus(EnableFdnScreen.this.getResources().getText(R.string.enable_in_progress));
                EnableFdnScreen.this.enableFdn();
            }
        }
    };

    @Override // android.app.Activity
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.enable_fdn_screen);
        setupView();
        this.mPhone = PhoneGlobals.getPhone();
        this.mEnable = !this.mPhone.getIccCard().getIccFdnEnabled();
        int id = this.mEnable ? R.string.enable_fdn : R.string.disable_fdn;
        setTitle(getResources().getText(id));
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        this.mPhone = PhoneGlobals.getPhone();
    }

    private void setupView() {
        this.mPin2Field = (EditText) findViewById(R.id.pin);
        this.mPin2Field.setKeyListener(DigitsKeyListener.getInstance());
        this.mPin2Field.setMovementMethod(null);
        this.mPin2Field.setOnClickListener(this.mClicked);
        this.mPinFieldContainer = (LinearLayout) findViewById(R.id.pinc);
        this.mStatusField = (TextView) findViewById(R.id.status);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showStatus(CharSequence statusMsg) {
        if (statusMsg != null) {
            this.mStatusField.setText(statusMsg);
            this.mStatusField.setVisibility(0);
            this.mPinFieldContainer.setVisibility(8);
        } else {
            this.mPinFieldContainer.setVisibility(0);
            this.mStatusField.setVisibility(8);
        }
    }

    private String getPin2() {
        return this.mPin2Field.getText().toString();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void enableFdn() {
        Message callback = Message.obtain(this.mHandler, 100);
        this.mPhone.getIccCard().setIccFdnEnabled(this.mEnable, getPin2(), callback);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleResult(AsyncResult ar) {
        if (ar.exception == null) {
            showStatus(getResources().getText(this.mEnable ? R.string.enable_fdn_ok : R.string.disable_fdn_ok));
        } else if (ar.exception instanceof CommandException) {
            showStatus(getResources().getText(R.string.pin_failed));
        }
        this.mHandler.postDelayed(new Runnable() { // from class: com.android.phone.EnableFdnScreen.2
            @Override // java.lang.Runnable
            public void run() {
                EnableFdnScreen.this.finish();
            }
        }, 3000L);
    }
}
