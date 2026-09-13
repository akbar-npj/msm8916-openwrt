package com.android.phone;

import android.preference.Preference;
import com.android.internal.telephony.CommandException;

/* JADX INFO: compiled from: TimeConsumingPreferenceActivity.java */
/* JADX INFO: loaded from: classes.dex */
interface TimeConsumingPreferenceListener {
    void onError(Preference preference, int i);

    void onException(Preference preference, CommandException commandException);

    void onFinished(Preference preference, boolean z);

    void onStarted(Preference preference, boolean z);
}
