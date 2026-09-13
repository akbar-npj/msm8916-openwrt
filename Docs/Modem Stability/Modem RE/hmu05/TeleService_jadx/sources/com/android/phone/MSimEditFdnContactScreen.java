package com.android.phone;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.MSimTelephonyManager;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import com.codeaurora.telephony.msim.MSimPhoneFactory;

/* JADX INFO: loaded from: classes.dex */
public class MSimEditFdnContactScreen extends EditFdnContactScreen {
    private static int mSubscription = 0;
    private Handler mHandler = new Handler();

    @Override // com.android.phone.EditFdnContactScreen
    protected void handleSimAbsentIntent(Context context, Intent intent) {
        int sub = intent.getIntExtra("subscription", 0);
        if (sub == mSubscription) {
            super.handleSimAbsentIntent(context, intent);
        }
    }

    @Override // com.android.phone.EditFdnContactScreen, android.app.Activity
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
    }

    @Override // com.android.phone.EditFdnContactScreen
    protected void resolveIntent() {
        Intent intent = getIntent();
        this.mName = intent.getStringExtra("name");
        this.mNumber = intent.getStringExtra("number");
        mSubscription = getIntent().getIntExtra("subscription", 0);
        this.mAddContact = TextUtils.isEmpty(this.mNumber);
    }

    @Override // com.android.phone.EditFdnContactScreen
    protected Uri getContentURI() {
        String[] fdn = {"fdn", "fdn_sub2", "fdn_sub3"};
        if (mSubscription < MSimTelephonyManager.getDefault().getPhoneCount()) {
            return Uri.parse("content://iccmsim/" + fdn[mSubscription]);
        }
        Log.e("MSimEditFdnContactScreen", "Error received invalid sub =" + mSubscription);
        return null;
    }

    @Override // com.android.phone.EditFdnContactScreen
    protected void addContact() {
        String number = PhoneNumberUtils.convertAndStrip(getNumberFromTextField());
        if (!isValidNumber(number)) {
            handleResult(false, true);
            return;
        }
        Uri uri = getContentURI();
        ContentValues bundle = new ContentValues(4);
        bundle.put("tag", getNameFromTextField());
        bundle.put("number", number);
        bundle.put("pin2", this.mPin2);
        bundle.put("subscription", Integer.valueOf(mSubscription));
        this.mQueryHandler = new EditFdnContactScreen.QueryHandler(getContentResolver());
        this.mQueryHandler.startInsert(0, null, uri, bundle);
        displayProgress(true);
        showStatus(getResources().getText(R.string.adding_fdn_contact));
    }

    @Override // com.android.phone.EditFdnContactScreen
    protected void updateContact() {
        String name = getNameFromTextField();
        String number = PhoneNumberUtils.convertAndStrip(getNumberFromTextField());
        if (!isValidNumber(number)) {
            handleResult(false, true);
            return;
        }
        Uri uri = getContentURI();
        ContentValues bundle = new ContentValues();
        bundle.put("tag", this.mName);
        bundle.put("number", this.mNumber);
        bundle.put("newTag", name);
        bundle.put("newNumber", number);
        bundle.put("pin2", this.mPin2);
        bundle.put("subscription", Integer.valueOf(mSubscription));
        this.mQueryHandler = new EditFdnContactScreen.QueryHandler(getContentResolver());
        this.mQueryHandler.startUpdate(0, null, uri, bundle, null, null);
        displayProgress(true);
        showStatus(getResources().getText(R.string.updating_fdn_contact));
    }

    @Override // com.android.phone.EditFdnContactScreen
    protected void deleteSelected() {
        if (!this.mAddContact) {
            Intent intent = new Intent();
            intent.setClass(this, MSimDeleteFdnContactScreen.class);
            intent.putExtra("name", this.mName);
            intent.putExtra("number", this.mNumber);
            intent.putExtra("subscription", mSubscription);
            startActivity(intent);
        }
        finish();
    }

    @Override // com.android.phone.EditFdnContactScreen
    protected void handleResult(boolean success, boolean invalidNumber) {
        if (success) {
            showStatus(getResources().getText(this.mAddContact ? R.string.fdn_contact_added : R.string.fdn_contact_updated));
        } else if (invalidNumber) {
            showStatus(getResources().getText(R.string.fdn_invalid_number));
        } else if (MSimPhoneFactory.getPhone(mSubscription).getIccCard().getIccPin2Blocked()) {
            showStatus(getResources().getText(R.string.fdn_enable_puk2_requested));
        } else if (MSimPhoneFactory.getPhone(mSubscription).getIccCard().getIccPuk2Blocked()) {
            showStatus(getResources().getText(R.string.puk2_blocked));
        } else {
            showStatus(getResources().getText(R.string.pin2_or_fdn_invalid));
        }
        this.mHandler.postDelayed(new Runnable() { // from class: com.android.phone.MSimEditFdnContactScreen.1
            @Override // java.lang.Runnable
            public void run() {
                MSimEditFdnContactScreen.this.finish();
            }
        }, 2000L);
    }
}
