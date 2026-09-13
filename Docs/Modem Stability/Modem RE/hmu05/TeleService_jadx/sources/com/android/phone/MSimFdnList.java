package com.android.phone;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.MSimTelephonyManager;
import android.util.Log;

/* JADX INFO: loaded from: classes.dex */
public class MSimFdnList extends FdnList {
    private int mSubscription = 0;

    @Override // com.android.phone.FdnList, com.android.phone.ADNList, android.app.Activity
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
    }

    @Override // com.android.phone.FdnList, com.android.phone.ADNList
    protected Uri resolveIntent() {
        String[] fdn = {"fdn", "fdn_sub2", "fdn_sub3"};
        Intent intent = getIntent();
        this.mSubscription = getIntent().getIntExtra("subscription", 0);
        if (this.mSubscription < MSimTelephonyManager.getDefault().getPhoneCount()) {
            intent.setData(Uri.parse("content://iccmsim/" + fdn[this.mSubscription]));
        } else {
            Log.e("MSimFdnList", "Error received invalid sub =" + this.mSubscription);
        }
        return intent.getData();
    }

    @Override // com.android.phone.FdnList
    protected void addContact() {
        Intent intent = new Intent();
        intent.putExtra("subscription", this.mSubscription);
        intent.setClass(this, MSimEditFdnContactScreen.class);
        startActivity(intent);
    }

    @Override // com.android.phone.FdnList
    protected void editSelected(int i) {
        if (this.mCursor.moveToPosition(i)) {
            String string = this.mCursor.getString(0);
            String string2 = this.mCursor.getString(1);
            Intent intent = new Intent();
            intent.putExtra("subscription", this.mSubscription);
            intent.setClass(this, MSimEditFdnContactScreen.class);
            intent.putExtra("name", string);
            intent.putExtra("number", string2);
            startActivity(intent);
        }
    }

    @Override // com.android.phone.FdnList
    protected void deleteSelected() {
        if (this.mCursor.moveToPosition(getSelectedItemPosition())) {
            String string = this.mCursor.getString(0);
            String string2 = this.mCursor.getString(1);
            Intent intent = new Intent();
            intent.putExtra("subscription", this.mSubscription);
            intent.setClass(this, MSimDeleteFdnContactScreen.class);
            intent.putExtra("name", string);
            intent.putExtra("number", string2);
            startActivity(intent);
        }
    }
}
