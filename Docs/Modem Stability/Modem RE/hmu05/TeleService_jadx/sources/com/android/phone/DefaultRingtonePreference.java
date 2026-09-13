package com.android.phone;

import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.preference.RingtonePreference;
import android.util.AttributeSet;

/* JADX INFO: loaded from: classes.dex */
public class DefaultRingtonePreference extends RingtonePreference {
    public DefaultRingtonePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override // android.preference.RingtonePreference
    protected void onPrepareRingtonePickerIntent(Intent ringtonePickerIntent) {
        super.onPrepareRingtonePickerIntent(ringtonePickerIntent);
        ringtonePickerIntent.putExtra("android.intent.extra.ringtone.SHOW_DEFAULT", false);
    }

    @Override // android.preference.RingtonePreference
    protected void onSaveRingtone(Uri ringtoneUri) {
        if (getRingtoneType() == 1) {
            RingtoneManager.setActualRingtoneUriBySubId(getContext(), getSubId(), ringtoneUri);
        } else {
            RingtoneManager.setActualDefaultRingtoneUri(getContext(), getRingtoneType(), ringtoneUri);
        }
    }

    @Override // android.preference.RingtonePreference
    protected Uri onRestoreRingtone() {
        return getRingtoneType() == 1 ? RingtoneManager.getActualRingtoneUriBySubId(getContext(), getSubId()) : RingtoneManager.getActualDefaultRingtoneUri(getContext(), getRingtoneType());
    }
}
