package com.android.phone;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.MSimTelephonyManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView;
import com.codeaurora.telephony.msim.MSimPhoneFactory;

/* JADX INFO: loaded from: classes.dex */
public class MSimContacts extends SimContacts {
    private static int IMPORT_FROM_ALL = 8;
    private int mSelectedSim = 0;
    String[] mAdnString = {"adn", "adn_sub2", "adn_sub3"};

    @Override // com.android.phone.SimContacts, com.android.phone.ADNList
    protected Uri resolveIntent() {
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        this.mSelectedSim = extras.getInt("sim_index");
        if (this.mSelectedSim == IMPORT_FROM_ALL) {
            intent.setData(Uri.parse("content://iccmsim/adn_all"));
        } else if (this.mSelectedSim < MSimTelephonyManager.getDefault().getPhoneCount()) {
            intent.setData(Uri.parse("content://iccmsim/" + this.mAdnString[this.mSelectedSim]));
        } else {
            Log.e("MSimContacts", "Error: received invalid sub =" + this.mSelectedSim);
        }
        if ("android.intent.action.PICK".equals(intent.getAction())) {
            this.mInitialSelection = intent.getIntExtra("index", 0) - 1;
        } else if ("android.intent.action.VIEW".equals(intent.getAction())) {
            this.mInitialSelection = 0;
        }
        return intent.getData();
    }

    @Override // com.android.phone.SimContacts
    protected Uri getUri() {
        if (this.mSelectedSim < MSimTelephonyManager.getDefault().getPhoneCount()) {
            return Uri.parse("content://iccmsim/" + this.mAdnString[this.mSelectedSim]);
        }
        Log.e("ADNList", "Invalid subcription:" + this.mSelectedSim);
        return null;
    }

    private boolean isImportFromAllSelection() {
        return this.mSelectedSim == IMPORT_FROM_ALL;
    }

    @Override // com.android.phone.SimContacts, android.app.Activity
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        if (isImportFromAllSelection()) {
            Log.i("MSimContacts", "Only import is supported");
            menu.removeItem(3);
            menu.removeItem(4);
            return true;
        }
        return true;
    }

    @Override // com.android.phone.SimContacts, android.app.Activity, android.view.View.OnCreateContextMenuListener
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (menuInfo instanceof AdapterView.AdapterContextMenuInfo) {
            AdapterView.AdapterContextMenuInfo itemInfo = (AdapterView.AdapterContextMenuInfo) menuInfo;
            TextView textView = (TextView) itemInfo.targetView.findViewById(android.R.id.text1);
            if (textView != null) {
                menu.setHeaderTitle(textView.getText());
            }
            menu.add(0, 1, 0, R.string.importSimEntry);
            if (!isImportFromAllSelection()) {
                menu.add(0, 5, 0, R.string.editContact);
                menu.add(0, 6, 0, R.string.sendSms);
                menu.add(0, 7, 0, R.string.dial);
                menu.add(0, 8, 0, R.string.delete);
                return;
            }
            Log.i("MSimContacts", "Only import is supported");
        }
    }

    @Override // com.android.phone.ADNList
    protected boolean isSimPresent() {
        if (this.mSelectedSim == IMPORT_FROM_ALL) {
            for (int i = 0; i < MSimTelephonyManager.getDefault().getPhoneCount(); i++) {
                if (MSimPhoneFactory.getPhone(i).getIccCard().hasIccCard()) {
                    return true;
                }
            }
            return false;
        }
        boolean isSimPresent = MSimPhoneFactory.getPhone(this.mSelectedSim).getIccCard().hasIccCard();
        return isSimPresent;
    }
}
