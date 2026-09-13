package com.android.phone.common;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.os.SystemVibrator;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;

/* JADX INFO: loaded from: classes.dex */
public class HapticFeedback {
    private ContentResolver mContentResolver;
    private boolean mEnabled;
    private long[] mHapticPattern;
    private boolean mSettingEnabled;
    private Settings.System mSystemSettings;
    private Vibrator mVibrator;

    public void init(Context context, boolean enabled) {
        this.mEnabled = enabled;
        if (enabled) {
            this.mVibrator = new SystemVibrator(context);
            this.mHapticPattern = new long[]{0, 10, 20, 30};
            this.mSystemSettings = new Settings.System();
            this.mContentResolver = context.getContentResolver();
        }
    }

    public void checkSystemSetting() {
        if (this.mEnabled) {
            try {
                Settings.System system = this.mSystemSettings;
                int val = Settings.System.getInt(this.mContentResolver, "haptic_feedback_enabled", 0);
                this.mSettingEnabled = val != 0;
            } catch (Resources.NotFoundException nfe) {
                Log.e("HapticFeedback", "Could not retrieve system setting.", nfe);
                this.mSettingEnabled = false;
            }
        }
    }

    public void vibrate() {
        if (this.mEnabled && this.mSettingEnabled) {
            if (this.mHapticPattern != null && this.mHapticPattern.length == 1) {
                this.mVibrator.vibrate(this.mHapticPattern[0]);
            } else {
                this.mVibrator.vibrate(this.mHapticPattern, -1);
            }
        }
    }
}
