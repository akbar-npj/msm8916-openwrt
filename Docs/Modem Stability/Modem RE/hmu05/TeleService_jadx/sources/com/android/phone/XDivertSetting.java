package com.android.phone;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceScreen;
import android.util.Log;
import java.util.Arrays;

/* JADX INFO: loaded from: classes.dex */
public class XDivertSetting extends TimeConsumingPreferenceActivity {
    private XDivertCheckBoxPreference mXDivertButton;

    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.xdivert);
        Intent intent = getIntent();
        String[] numbers = intent.getStringArrayExtra("Line1Numbers");
        Log.d("XDivertSetting", "onCreate numbers = " + Arrays.toString(numbers));
        PreferenceScreen prefSet = getPreferenceScreen();
        this.mXDivertButton = (XDivertCheckBoxPreference) prefSet.findPreference("xdivert_checkbox");
        this.mXDivertButton.init(this, false, numbers);
    }
}
