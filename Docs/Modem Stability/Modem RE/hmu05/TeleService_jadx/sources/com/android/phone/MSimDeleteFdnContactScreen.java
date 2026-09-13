package com.android.phone;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.MSimTelephonyManager;
import android.text.TextUtils;
import android.util.Log;

/* JADX INFO: loaded from: classes.dex */
public class MSimDeleteFdnContactScreen extends DeleteFdnContactScreen {
    private static int mSubscription = 0;

    @Override // com.android.phone.DeleteFdnContactScreen, android.app.Activity
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
    }

    @Override // com.android.phone.DeleteFdnContactScreen
    protected void resolveIntent() {
        Intent intent = getIntent();
        this.mName = intent.getStringExtra("name");
        this.mNumber = intent.getStringExtra("number");
        mSubscription = getIntent().getIntExtra("subscription", 0);
        if (TextUtils.isEmpty(this.mNumber)) {
            finish();
        }
    }

    @Override // com.android.phone.DeleteFdnContactScreen
    protected void deleteContact() {
        String[] fdn = {"fdn", "fdn_sub2", "fdn_sub3"};
        StringBuilder buf = new StringBuilder();
        Uri uri = null;
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
        if (mSubscription < MSimTelephonyManager.getDefault().getPhoneCount()) {
            uri = Uri.parse("content://iccmsim/" + fdn[mSubscription]);
        } else {
            Log.e("MSimDeleteFdnContactScreen", "Error received invalid sub =" + mSubscription);
        }
        this.mQueryHandler = new DeleteFdnContactScreen.QueryHandler(getContentResolver());
        this.mQueryHandler.startDelete(0, null, uri, buf.toString(), null);
        displayProgress(true);
    }
}
