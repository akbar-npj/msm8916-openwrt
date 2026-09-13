package com.android.phone;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

/* JADX INFO: loaded from: classes.dex */
public class ContactScreenActivity extends Activity {
    String mName;
    String mNewName;
    String mNewPhoneNumber;
    String mPhoneNumber;

    @Override // android.app.Activity
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.contact_screen);
        try {
            final EditText editName = (EditText) findViewById(R.id.name);
            final EditText editPhoneNumber = (EditText) findViewById(R.id.phoneNumber);
            Intent intent = getIntent();
            this.mName = intent.getStringExtra("NAME");
            this.mPhoneNumber = intent.getStringExtra("PHONE");
            editName.setText(this.mName, TextView.BufferType.EDITABLE);
            editPhoneNumber.setText(this.mPhoneNumber, TextView.BufferType.EDITABLE);
            View.OnClickListener handler = new View.OnClickListener() { // from class: com.android.phone.ContactScreenActivity.1
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    switch (v.getId()) {
                        case R.id.save /* 2131165205 */:
                            ContactScreenActivity.this.mNewName = editName.getText().toString();
                            ContactScreenActivity.this.mNewPhoneNumber = editPhoneNumber.getText().toString();
                            Log.d("ContactScreenActivity", "Name: " + ContactScreenActivity.this.mName + " Number: " + ContactScreenActivity.this.mPhoneNumber);
                            Log.d("ContactScreenActivity", " After edited Name: " + ContactScreenActivity.this.mNewName + " Number: " + ContactScreenActivity.this.mNewPhoneNumber);
                            Intent intent2 = new Intent();
                            intent2.putExtra("NAME", ContactScreenActivity.this.mName);
                            intent2.putExtra("PHONE", ContactScreenActivity.this.mPhoneNumber);
                            intent2.putExtra("NEWNAME", ContactScreenActivity.this.mNewName);
                            intent2.putExtra("NEWPHONE", ContactScreenActivity.this.mNewPhoneNumber);
                            ContactScreenActivity.this.setResult(-1, intent2);
                            ContactScreenActivity.this.finish();
                            break;
                        case R.id.cancel /* 2131165206 */:
                            ContactScreenActivity.this.finish();
                            break;
                    }
                }
            };
            findViewById(R.id.save).setOnClickListener(handler);
            findViewById(R.id.cancel).setOnClickListener(handler);
        } catch (Exception e) {
            Log.e("ContactScreenActivity ", e.toString());
        }
    }
}
