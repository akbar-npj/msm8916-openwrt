package com.android.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.telephony.MSimTelephonyManager;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.widget.TextView;

/* JADX INFO: loaded from: classes.dex */
public class ExportContactsToSim extends Activity {
    private static final String[] COLUMN_NAMES = {"name", "number", "emails"};
    private TextView mEmptyText;
    private int mResult = 1;
    protected boolean mIsForeground = false;
    private boolean mSimContactsLoaded = false;
    private Handler mHandler = new Handler() { // from class: com.android.phone.ExportContactsToSim.3
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    int result = ((Integer) msg.obj).intValue();
                    if (result == 1) {
                        ExportContactsToSim.this.showAlertDialog(ExportContactsToSim.this.getString(R.string.exportAllcontatsSuccess));
                    } else if (result == 2) {
                        ExportContactsToSim.this.showAlertDialog(ExportContactsToSim.this.getString(R.string.exportAllcontatsNoContacts));
                    } else {
                        ExportContactsToSim.this.showAlertDialog(ExportContactsToSim.this.getString(R.string.exportAllcontatsFailed));
                    }
                    break;
            }
        }
    };

    @Override // android.app.Activity
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().requestFeature(5);
        setContentView(R.layout.export_contact_screen);
        this.mEmptyText = (TextView) findViewById(android.R.id.empty);
        doExportToSim();
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        this.mIsForeground = true;
    }

    @Override // android.app.Activity
    protected void onPause() {
        super.onPause();
        this.mIsForeground = false;
    }

    private void doExportToSim() {
        displayProgress(true);
        new Thread(new Runnable() { // from class: com.android.phone.ExportContactsToSim.1
            @Override // java.lang.Runnable
            public void run() {
                Cursor contactsCursor = ExportContactsToSim.this.getContactsContentCursor();
                if (contactsCursor.getCount() < 1) {
                    ExportContactsToSim.this.mResult = 2;
                } else {
                    if (!ExportContactsToSim.this.mSimContactsLoaded) {
                        ExportContactsToSim.this.getContentResolver().query(ExportContactsToSim.this.getUri(), null, null, null, null);
                        ExportContactsToSim.this.mSimContactsLoaded = true;
                    }
                    int i = 0;
                    while (contactsCursor.moveToNext()) {
                        ExportContactsToSim.this.populateContactDataFromCursor(contactsCursor);
                        i++;
                    }
                }
                contactsCursor.close();
                Message message = Message.obtain(ExportContactsToSim.this.mHandler, 1, Integer.valueOf(ExportContactsToSim.this.mResult));
                ExportContactsToSim.this.mHandler.sendMessage(message);
            }
        }).start();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public Cursor getContactsContentCursor() {
        Uri phoneBookContentUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] selectionArg = {"SIM"};
        Cursor contactsCursor = getContentResolver().query(phoneBookContentUri, null, "has_phone_number='1' AND (account_type is NULL OR account_type !=?)", selectionArg, null);
        return contactsCursor;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void populateContactDataFromCursor(Cursor dataCursor) {
        Uri uri = getUri();
        if (uri == null) {
            Log.d("ExportContactsToSim", " populateContactDataFromCursor: uri is null, return ");
            return;
        }
        int nameIdx = dataCursor.getColumnIndex("display_name");
        int phoneIdx = dataCursor.getColumnIndex("data1");
        String name = dataCursor.getString(nameIdx);
        String rawNumber = dataCursor.getString(phoneIdx);
        String number = PhoneNumberUtils.normalizeNumber(rawNumber);
        ContentValues values = new ContentValues();
        values.put("tag", name);
        values.put("number", number);
        Log.d("ExportContactsToSim", "name : " + name + " number : " + number);
        Uri contactUri = getContentResolver().insert(uri, values);
        if (contactUri == null) {
            Log.e("ExportContactsToSim", "Failed to export contact to SIM for name : " + name + " number : " + number);
            this.mResult = 0;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showAlertDialog(String value) {
        if (!this.mIsForeground) {
            Log.d("ExportContactsToSim", "The activitiy is not in foreground. Do not display dialog!!!");
            return;
        }
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle("Result...");
        alertDialog.setMessage(value);
        alertDialog.setCanceledOnTouchOutside(false);
        alertDialog.setButton("OK", new DialogInterface.OnClickListener() { // from class: com.android.phone.ExportContactsToSim.2
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                ExportContactsToSim.this.finish();
            }
        });
        alertDialog.show();
    }

    private void displayProgress(boolean loading) {
        this.mEmptyText.setText(R.string.exportContacts);
        getWindow().setFeatureInt(5, loading ? -1 : -2);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public Uri getUri() {
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            int subscription = extras.getInt("sim_index");
            String[] adnString = {"adn", "adn_sub2", "adn_sub3"};
            Log.d("ExportContactsToSim", " subscription : " + subscription);
            if (subscription < MSimTelephonyManager.getDefault().getPhoneCount()) {
                return Uri.parse("content://iccmsim/" + adnString[subscription]);
            }
            Log.e("ExportContactsToSim", "Invalid subcription:" + subscription);
            return null;
        }
        return Uri.parse("content://icc/adn");
    }
}
