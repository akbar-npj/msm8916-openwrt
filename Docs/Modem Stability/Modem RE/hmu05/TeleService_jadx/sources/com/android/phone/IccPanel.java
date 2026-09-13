package com.android.phone;

import android.app.Dialog;
import android.app.StatusBarManager;
import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Window;

/* JADX INFO: loaded from: classes.dex */
public class IccPanel extends Dialog {
    private StatusBarManager mStatusBarManager;

    public IccPanel(Context context) {
        super(context, R.style.IccPanel);
    }

    @Override // android.app.Dialog
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window winP = getWindow();
        winP.setType(2007);
        winP.setLayout(-1, -1);
        winP.setGravity(17);
        PhoneGlobals app = PhoneGlobals.getInstance();
        this.mStatusBarManager = (StatusBarManager) app.getSystemService("statusbar");
        requestWindowFeature(1);
    }

    @Override // android.app.Dialog
    protected void onStart() {
        super.onStart();
        this.mStatusBarManager.disable(65536);
    }

    @Override // android.app.Dialog
    public void onStop() {
        super.onStop();
        this.mStatusBarManager.disable(0);
    }

    @Override // android.app.Dialog, android.view.KeyEvent.Callback
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == 4) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
