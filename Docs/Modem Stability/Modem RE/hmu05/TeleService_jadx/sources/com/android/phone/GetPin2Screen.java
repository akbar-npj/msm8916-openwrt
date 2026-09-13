package com.android.phone;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.DigitsKeyListener;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/* JADX INFO: loaded from: classes.dex */
public class GetPin2Screen extends Activity implements TextView.OnEditorActionListener {
    private final View.OnClickListener mClicked = new View.OnClickListener() { // from class: com.android.phone.GetPin2Screen.1
        @Override // android.view.View.OnClickListener
        public void onClick(View v) {
            if (!TextUtils.isEmpty(GetPin2Screen.this.mPin2Field.getText())) {
                GetPin2Screen.this.returnResult();
            }
        }
    };
    private Button mOkButton;
    private EditText mPin2Field;

    @Override // android.app.Activity
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.get_pin2_screen);
        this.mPin2Field = (EditText) findViewById(R.id.pin);
        this.mPin2Field.setKeyListener(DigitsKeyListener.getInstance());
        this.mPin2Field.setMovementMethod(null);
        this.mPin2Field.setOnEditorActionListener(this);
        this.mOkButton = (Button) findViewById(R.id.ok);
        this.mOkButton.setOnClickListener(this.mClicked);
    }

    private String getPin2() {
        return this.mPin2Field.getText().toString();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void returnResult() {
        Bundle map = new Bundle();
        map.putString("pin2", getPin2());
        Intent intent = getIntent();
        Uri uri = intent.getData();
        Intent action = new Intent();
        if (uri != null) {
            action.setAction(uri.toString());
        }
        setResult(-1, action.putExtras(map));
        finish();
    }

    @Override // android.widget.TextView.OnEditorActionListener
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId != 6) {
            return false;
        }
        this.mOkButton.performClick();
        return true;
    }
}
