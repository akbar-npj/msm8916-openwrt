package com.android.phone;

import android.app.Activity;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.widget.Toast;

/* JADX INFO: loaded from: classes.dex */
public class DeleteFdnContactScreen extends Activity {
    private Handler mHandler = new Handler();
    protected String mName;
    protected String mNumber;
    protected String mPin2;
    protected QueryHandler mQueryHandler;

    @Override // android.app.Activity
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        resolveIntent();
        authenticatePin2();
        getWindow().requestFeature(5);
        setContentView(R.layout.delete_fdn_contact_screen);
    }

    @Override // android.app.Activity
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case 100:
                Bundle extras = intent != null ? intent.getExtras() : null;
                if (extras != null) {
                    this.mPin2 = extras.getString("pin2");
                    showStatus(getResources().getText(R.string.deleting_fdn_contact));
                    deleteContact();
                } else {
                    displayProgress(false);
                    finish();
                }
                break;
        }
    }

    protected void resolveIntent() {
        Intent intent = getIntent();
        this.mName = intent.getStringExtra("name");
        this.mNumber = intent.getStringExtra("number");
        if (TextUtils.isEmpty(this.mNumber)) {
            finish();
        }
    }

    protected void deleteContact() {
        StringBuilder buf = new StringBuilder();
        if (TextUtils.isEmpty(this.mName)) {
            buf.append("number='");
        } else {
            buf.append("tag='");
            buf.append(this.mName);
            buf.append("' AND number='");
        }
        buf.append(this.mNumber);
        buf.append("' AND pin2='");
        buf.append(this.mPin2);
        buf.append("'");
        Uri uri = Uri.parse("content://icc/fdn");
        this.mQueryHandler = new QueryHandler(getContentResolver());
        this.mQueryHandler.startDelete(0, null, uri, buf.toString(), null);
        displayProgress(true);
    }

    private void authenticatePin2() {
        Intent intent = new Intent();
        intent.setClass(this, GetPin2Screen.class);
        startActivityForResult(intent, 100);
    }

    protected void displayProgress(boolean flag) {
        getWindow().setFeatureInt(5, flag ? -1 : -2);
    }

    private void showStatus(CharSequence statusMsg) {
        if (statusMsg != null) {
            Toast.makeText(this, statusMsg, 0).show();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleResult(boolean success) {
        if (success) {
            showStatus(getResources().getText(R.string.fdn_contact_deleted));
        } else {
            showStatus(getResources().getText(R.string.pin2_invalid));
        }
        this.mHandler.postDelayed(new Runnable() { // from class: com.android.phone.DeleteFdnContactScreen.1
            @Override // java.lang.Runnable
            public void run() {
                DeleteFdnContactScreen.this.finish();
            }
        }, 2000L);
    }

    protected class QueryHandler extends AsyncQueryHandler {
        public QueryHandler(ContentResolver cr) {
            super(cr);
        }

        @Override // android.content.AsyncQueryHandler
        protected void onQueryComplete(int token, Object cookie, Cursor c) {
        }

        @Override // android.content.AsyncQueryHandler
        protected void onInsertComplete(int token, Object cookie, Uri uri) {
        }

        @Override // android.content.AsyncQueryHandler
        protected void onUpdateComplete(int token, Object cookie, int result) {
        }

        @Override // android.content.AsyncQueryHandler
        protected void onDeleteComplete(int token, Object cookie, int result) {
            DeleteFdnContactScreen.this.displayProgress(false);
            DeleteFdnContactScreen.this.handleResult(result > 0);
        }
    }
}
