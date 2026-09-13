package com.android.phone;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyCapabilities;

/* JADX INFO: loaded from: classes.dex */
public class MSimNotificationMgr extends NotificationMgr {
    private MSimNotificationMgr(PhoneGlobals app) {
        super(app);
    }

    static NotificationMgr init(PhoneGlobals phoneGlobals) {
        NotificationMgr notificationMgr;
        synchronized (MSimNotificationMgr.class) {
            if (sInstance == null) {
                sInstance = new MSimNotificationMgr(phoneGlobals);
                sInstance.updateNotificationsAtStartup();
            } else {
                Log.wtf("MSimNotificationMgr", "init() called multiple times!  sInstance = " + sInstance);
            }
            notificationMgr = sInstance;
        }
        return notificationMgr;
    }

    void updateMwi(boolean visible, Phone phone) {
        String notificationText;
        Uri ringtoneUri;
        int subscription = phone.getSubscription();
        if (DBG) {
            log("updateMwi(): " + visible + " Subscription: " + subscription);
        }
        int[] iconId = {R.drawable.stat_notify_voicemail_sub1, R.drawable.stat_notify_voicemail_sub2, R.drawable.stat_notify_voicemail_sub3};
        int resId = iconId[subscription];
        int notificationId = getNotificationIdBasedOnSubscription(subscription);
        if (visible) {
            String notificationTitle = this.mContext.getString(R.string.notification_voicemail_title);
            String vmNumber = phone.getVoiceMailNumber();
            if (DBG) {
                log("- got vm number: '" + vmNumber + "'");
            }
            if (vmNumber == null && !phone.getIccRecordsLoaded()) {
                if (DBG) {
                    log("- Null vm number: SIM records not loaded (yet)...");
                }
                int i = this.mVmNumberRetriesRemaining;
                this.mVmNumberRetriesRemaining = i - 1;
                if (i > 0) {
                    if (DBG) {
                        log("  - Retrying in 10000 msec...");
                    }
                    ((MSimCallNotifier) this.mApp.notifier).sendMwiChangedDelayed(10000L, phone);
                    return;
                }
                Log.w("MSimNotificationMgr", "NotificationMgr.updateMwi: getVoiceMailNumber() failed after 5 retries; giving up.");
            }
            if (TelephonyCapabilities.supportsVoiceMessageCount(phone)) {
                int vmCount = phone.getVoiceMessageCount();
                String titleFormat = this.mContext.getString(R.string.notification_voicemail_title_count);
                notificationTitle = String.format(titleFormat, Integer.valueOf(vmCount));
            }
            if (TextUtils.isEmpty(vmNumber)) {
                notificationText = this.mContext.getString(R.string.notification_voicemail_no_vm_number);
            } else {
                notificationText = String.format(this.mContext.getString(R.string.notification_voicemail_text_format), PhoneNumberUtils.formatNumber(vmNumber));
            }
            Intent intent = new Intent("android.intent.action.CALL", Uri.fromParts("voicemail", "", null));
            PendingIntent pendingIntent = PendingIntent.getActivity(this.mContext, 0, intent, 0);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
            String uriString = prefs.getString("button_voicemail_notification_ringtone_key" + subscription, null);
            if (!TextUtils.isEmpty(uriString)) {
                ringtoneUri = Uri.parse(uriString);
            } else {
                ringtoneUri = Settings.System.DEFAULT_NOTIFICATION_URI;
            }
            Notification.Builder builder = new Notification.Builder(this.mContext);
            builder.setSmallIcon(resId).setWhen(System.currentTimeMillis()).setContentTitle(notificationTitle).setContentText(notificationText).setContentIntent(pendingIntent).setSound(ringtoneUri);
            Notification notification = builder.getNotification();
            MSimCallFeaturesSubSetting.migrateVoicemailVibrationSettingsIfNeeded(prefs, phone.getSubscription());
            boolean vibrate = prefs.getBoolean("button_voicemail_notification_vibrate_key" + phone.getSubscription(), false);
            if (vibrate) {
                notification.defaults |= 2;
            }
            notification.flags |= 32;
            configureLedNotification(notification);
            this.mNotificationManager.notify(notificationId, notification);
            return;
        }
        this.mNotificationManager.cancel(notificationId);
    }

    void updateCfi(boolean visible, int subscription) {
        int notificationId;
        if (DBG) {
            log("updateCfi(): " + visible + "Sub: " + subscription);
        }
        int[] callfwdIcon = {R.drawable.stat_sys_phone_call_forward_sub1, R.drawable.stat_sys_phone_call_forward_sub2, R.drawable.stat_sys_phone_call_forward_sub3};
        switch (subscription) {
            case 0:
                notificationId = 6;
                break;
            case 1:
                notificationId = 21;
                break;
            case 2:
                notificationId = 24;
                break;
            default:
                Log.e("MSimNotificationMgr", "updateCfi: This should not happen, subscription = " + subscription);
                return;
        }
        if (visible) {
            int resId = callfwdIcon[subscription];
            Intent intent = new Intent("android.intent.action.MAIN");
            intent.addFlags(268435456);
            intent.setClassName("com.android.phone", "com.android.phone.MSimCallFeaturesSetting");
            Notification notification = new Notification(resId, null, 0L);
            notification.setLatestEventInfo(this.mContext, this.mContext.getString(R.string.labelCF), this.mContext.getString(R.string.sum_cfu_enabled_indicator), PendingIntent.getActivity(this.mContext, 0, intent, 0));
            notification.flags |= 2;
            this.mNotificationManager.notify(notificationId, notification);
            return;
        }
        this.mNotificationManager.cancel(notificationId);
    }

    void updateXDivert(boolean visible) {
        Log.d("MSimNotificationMgr", "updateXDivert: " + visible);
        if (visible) {
            Intent intent = new Intent("android.intent.action.MAIN");
            intent.addFlags(268435456);
            intent.setClassName("com.android.phone", "com.android.phone.MSimCallFeaturesSetting");
            Notification notification = new Notification(R.drawable.stat_sys_phone_call_forward_xdivert, null, System.currentTimeMillis());
            notification.setLatestEventInfo(this.mContext, this.mContext.getString(R.string.xdivert_title), this.mContext.getString(R.string.sum_xdivert_enabled), PendingIntent.getActivity(this.mContext, 0, intent, 0));
            this.mNotificationManager.notify(22, notification);
            return;
        }
        this.mNotificationManager.cancel(22);
    }

    private int getNotificationIdBasedOnSubscription(int subscription) {
        switch (subscription) {
            case 0:
                return 5;
            case 1:
                return 20;
            case 2:
                return 23;
            default:
                Log.e("MSimNotificationMgr", "updateMwi: This should not happen, subscription = " + subscription);
                return 5;
        }
    }

    private void log(String msg) {
        Log.d("MSimNotificationMgr", msg);
    }
}
