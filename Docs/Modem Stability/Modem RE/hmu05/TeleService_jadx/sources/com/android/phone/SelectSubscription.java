package com.android.phone;

import android.app.TabActivity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.MSimTelephonyManager;
import android.util.Log;
import android.widget.TabHost;

/* JADX INFO: loaded from: classes.dex */
public class SelectSubscription extends TabActivity {
    private static int[] subString = {R.string.sub_1, R.string.sub_2, R.string.sub_3};
    private TabHost.TabSpec subscriptionPref;

    @Override // android.app.ActivityGroup, android.app.Activity
    public void onPause() {
        super.onPause();
    }

    @Override // android.app.ActivityGroup, android.app.Activity
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        log("Creating activity");
        setContentView(R.layout.multi_sim_setting);
        TabHost tabHost = getTabHost();
        Intent intent = getIntent();
        String pkg = intent.getStringExtra("PACKAGE");
        String targetClass = intent.getStringExtra("TARGET_CLASS");
        int numPhones = MSimTelephonyManager.getDefault().getPhoneCount();
        for (int i = 0; i < numPhones; i++) {
            log("Creating SelectSub activity = " + i);
            String tabLabel = Settings.System.getString(getContentResolver(), "perferred_name_sub" + (i + 1));
            if (tabLabel.equals("")) {
                tabLabel = getString(subString[i]);
            }
            this.subscriptionPref = tabHost.newTabSpec(tabLabel);
            this.subscriptionPref.setIndicator(tabLabel);
            intent = new Intent().setClassName(pkg, targetClass).setAction(intent.getAction()).putExtra("subscription", i);
            this.subscriptionPref.setContent(intent);
            tabHost.addTab(this.subscriptionPref);
        }
        tabHost.setCurrentTab(getIntent().getIntExtra("subscription", 0));
        if ("com.android.phone.MSimMobileNetworkSubSettings".equals(targetClass)) {
            setTitle(getResources().getText(R.string.mobile_networks));
        }
    }

    @Override // android.app.ActivityGroup, android.app.Activity
    protected void onResume() {
        super.onResume();
    }

    private static void log(String msg) {
        Log.d("SelectSubscription", msg);
    }
}
