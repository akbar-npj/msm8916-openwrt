package com.android.phone;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.StatusBarManager;
import android.content.AsyncQueryHandler;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyCapabilities;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class NotificationMgr {
    private static final String[] CALL_LOG_PROJECTION;
    protected static final boolean DBG;
    static final String[] PHONES_PROJECTION;
    protected static NotificationMgr sInstance;
    protected PhoneGlobals mApp;
    protected CallManager mCM;
    protected Context mContext;
    protected NotificationManager mNotificationManager;
    private Phone mPhone;
    private boolean mShowingMuteIcon;
    private boolean mShowingSpeakerphoneIcon;
    private StatusBarManager mStatusBarManager;
    private Toast mToast;
    private int mNumberMissedCalls = 0;
    private int mNumberMissedVideoCalls = 0;
    private boolean mSelectedUnavailableNotify = false;
    protected int mVmNumberRetriesRemaining = 5;
    private QueryHandler mQueryHandler = null;
    public StatusBarHelper statusBarHelper = new StatusBarHelper();

    static {
        DBG = SystemProperties.getInt("ro.debuggable", 0) == 1;
        CALL_LOG_PROJECTION = new String[]{"_id", "number", "presentation", "date", "duration", "type"};
        PHONES_PROJECTION = new String[]{"number", "display_name", "_id"};
    }

    protected NotificationMgr(PhoneGlobals app) {
        this.mApp = app;
        this.mContext = app;
        this.mNotificationManager = (NotificationManager) app.getSystemService("notification");
        this.mStatusBarManager = (StatusBarManager) app.getSystemService("statusbar");
        this.mPhone = app.phone;
        this.mCM = app.mCM;
    }

    static NotificationMgr init(PhoneGlobals phoneGlobals) {
        NotificationMgr notificationMgr;
        synchronized (NotificationMgr.class) {
            if (sInstance == null) {
                sInstance = new NotificationMgr(phoneGlobals);
                sInstance.updateNotificationsAtStartup();
            } else {
                Log.wtf("NotificationMgr", "init() called multiple times!  sInstance = " + sInstance);
            }
            notificationMgr = sInstance;
        }
        return notificationMgr;
    }

    public class StatusBarHelper {
        private boolean mIsExpandedViewEnabled;
        private boolean mIsNotificationEnabled;
        private boolean mIsSystemBarNavigationEnabled;

        private StatusBarHelper() {
            this.mIsNotificationEnabled = true;
            this.mIsExpandedViewEnabled = true;
            this.mIsSystemBarNavigationEnabled = true;
        }

        public void enableNotificationAlerts(boolean enable) {
            if (this.mIsNotificationEnabled != enable) {
                this.mIsNotificationEnabled = enable;
                updateStatusBar();
            }
        }

        public void enableExpandedView(boolean enable) {
            if (this.mIsExpandedViewEnabled != enable) {
                this.mIsExpandedViewEnabled = enable;
                updateStatusBar();
            }
        }

        public void enableSystemBarNavigation(boolean enable) {
            if (this.mIsSystemBarNavigationEnabled != enable) {
                this.mIsSystemBarNavigationEnabled = enable;
                updateStatusBar();
            }
        }

        private void updateStatusBar() {
            int state = this.mIsExpandedViewEnabled ? 0 : 0 | 65536;
            if (!this.mIsNotificationEnabled) {
                state |= 262144;
            }
            if (!this.mIsSystemBarNavigationEnabled) {
                state = state | 2097152 | 16777216 | 4194304 | 33554432;
            }
            if (NotificationMgr.DBG) {
                NotificationMgr.this.log("updateStatusBar: state = 0x" + Integer.toHexString(state));
            }
            NotificationMgr.this.mStatusBarManager.disable(state);
        }
    }

    protected void updateNotificationsAtStartup() {
        StringBuilder where;
        if (DBG) {
            log("updateNotificationsAtStartup()...");
        }
        this.mQueryHandler = new QueryHandler(this.mContext.getContentResolver());
        if (PhoneUtils.isCallOnCsvtEnabled()) {
            where = new StringBuilder("(type=");
            where.append(3);
            where.append(" OR ");
            where.append("type=");
            where.append(7);
            where.append(")");
            where.append(" AND new=1");
        } else {
            where = new StringBuilder("type=");
            where.append(3);
            where.append(" AND new=1");
        }
        if (DBG) {
            log("- start call log query...");
        }
        this.mQueryHandler.startQuery(-1, null, CallLog.Calls.CONTENT_URI, CALL_LOG_PROJECTION, where.toString(), null, "date DESC");
    }

    private class QueryHandler extends AsyncQueryHandler implements ContactsAsyncHelper.OnImageLoadCompleteListener {

        private class NotificationInfo {
            public long date;
            public String name;
            public String number;
            public int presentation;
            public String type;

            private NotificationInfo() {
            }
        }

        public QueryHandler(ContentResolver cr) {
            super(cr);
        }

        @Override // android.content.AsyncQueryHandler
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            switch (token) {
                case -2:
                    if (NotificationMgr.DBG) {
                        NotificationMgr.this.log("contact query complete.");
                    }
                    if (cursor != null && cookie != null) {
                        NotificationInfo n = (NotificationInfo) cookie;
                        Uri personUri = null;
                        if (cursor.moveToFirst()) {
                            n.name = cursor.getString(cursor.getColumnIndexOrThrow("display_name"));
                            long person_id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
                            if (NotificationMgr.DBG) {
                                NotificationMgr.this.log("contact :" + n.name + " found for phone: " + n.number + ". id : " + person_id);
                            }
                            personUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, person_id);
                        }
                        if (personUri != null) {
                            if (NotificationMgr.DBG) {
                                NotificationMgr.this.log("Start obtaining picture for the missed call. Uri: " + personUri);
                            }
                            ContactsAsyncHelper.startObtainPhotoAsync(0, NotificationMgr.this.mContext, personUri, this, n);
                        } else {
                            if (NotificationMgr.DBG) {
                                NotificationMgr.this.log("Failed to find Uri for obtaining photo. Just send notification without it.");
                            }
                            if (String.valueOf(7).equals(n.type)) {
                                NotificationMgr.this.notifyMissedVideoCall(n.name, n.number, n.type, n.date);
                            } else {
                                NotificationMgr.this.notifyMissedCall(n.name, n.number, n.presentation, n.type, null, null, n.date);
                            }
                        }
                        if (NotificationMgr.DBG) {
                            NotificationMgr.this.log("closing contact cursor.");
                        }
                        cursor.close();
                        break;
                    }
                    break;
                case -1:
                    if (NotificationMgr.DBG) {
                        NotificationMgr.this.log("call log query complete.");
                    }
                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            NotificationInfo n2 = getNotificationInfo(cursor);
                            if (NotificationMgr.DBG) {
                                NotificationMgr.this.log("query contacts for number: " + n2.number);
                            }
                            NotificationMgr.this.mQueryHandler.startQuery(-2, n2, Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, n2.number), NotificationMgr.PHONES_PROJECTION, null, null, "number");
                        }
                        if (NotificationMgr.DBG) {
                            NotificationMgr.this.log("closing call log cursor.");
                        }
                        cursor.close();
                    }
                    break;
            }
        }

        @Override // com.android.phone.ContactsAsyncHelper.OnImageLoadCompleteListener
        public void onImageLoadComplete(int token, Drawable photo, Bitmap photoIcon, Object cookie) {
            if (NotificationMgr.DBG) {
                NotificationMgr.this.log("Finished loading image: " + photo);
            }
            NotificationInfo n = (NotificationInfo) cookie;
            if (String.valueOf(7).equals(n.type)) {
                NotificationMgr.this.notifyMissedVideoCall(n.name, n.number, n.type, n.date);
            } else {
                NotificationMgr.this.notifyMissedCall(n.name, n.number, n.presentation, n.type, photo, photoIcon, n.date);
            }
        }

        private final NotificationInfo getNotificationInfo(Cursor cursor) {
            NotificationInfo n = new NotificationInfo();
            n.name = null;
            n.number = cursor.getString(cursor.getColumnIndexOrThrow("number"));
            n.presentation = cursor.getInt(cursor.getColumnIndexOrThrow("presentation"));
            n.type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
            n.date = cursor.getLong(cursor.getColumnIndexOrThrow("date"));
            if (n.presentation != 1) {
                n.number = null;
            }
            if (NotificationMgr.DBG) {
                NotificationMgr.this.log("NotificationInfo constructed for number: " + n.number);
            }
            return n;
        }
    }

    protected static void configureLedNotification(Notification note) {
        note.flags |= 1;
        note.defaults |= 4;
    }

    void notifyMissedCall(String name, String number, int presentation, String type, Drawable photo, Bitmap photoIcon, long date) {
        String callName;
        int titleResId;
        String expandedText;
        PendingIntent pendingCallLogIntent = PhoneGlobals.createPendingCallLogIntent(this.mContext);
        if (!PhoneGlobals.sVoiceCapable) {
            if (DBG) {
                log("notifyMissedCall: non-voice-capable device, not posting notification");
                return;
            }
            return;
        }
        this.mNumberMissedCalls++;
        if (name != null && TextUtils.isGraphic(name)) {
            callName = name;
        } else if (!TextUtils.isEmpty(number)) {
            BidiFormatter bidiFormatter = BidiFormatter.getInstance();
            callName = bidiFormatter.unicodeWrap(number, TextDirectionHeuristics.LTR);
        } else {
            callName = this.mContext.getString(R.string.unknown);
        }
        if (this.mNumberMissedCalls == 1) {
            titleResId = R.string.notification_missedCallTitle;
            expandedText = callName;
        } else {
            titleResId = R.string.notification_missedCallsTitle;
            expandedText = this.mContext.getString(R.string.notification_missedCallsMsg, Integer.valueOf(this.mNumberMissedCalls));
        }
        Notification.Builder builder = new Notification.Builder(this.mContext);
        builder.setSmallIcon(android.R.drawable.stat_notify_missed_call).setTicker(this.mContext.getString(R.string.notification_missedCallTicker, callName)).setWhen(date).setContentTitle(this.mContext.getText(titleResId)).setContentText(expandedText).setContentIntent(pendingCallLogIntent).setAutoCancel(true).setDeleteIntent(createClearMissedCallsIntent());
        if (this.mNumberMissedCalls == 1 && !TextUtils.isEmpty(number) && (presentation == PhoneConstants.PRESENTATION_ALLOWED || presentation == PhoneConstants.PRESENTATION_PAYPHONE)) {
            if (DBG) {
                log("Add actions with the number " + number);
            }
            builder.addAction(R.drawable.stat_sys_phone_call, this.mContext.getString(R.string.notification_missedCall_call_back), PhoneGlobals.getCallBackPendingIntent(this.mContext, number));
            builder.addAction(R.drawable.ic_text_holo_dark, this.mContext.getString(R.string.notification_missedCall_message), PhoneGlobals.getSendSmsFromNotificationPendingIntent(this.mContext, number));
            if (photoIcon != null) {
                builder.setLargeIcon(photoIcon);
            } else if (photo instanceof BitmapDrawable) {
                builder.setLargeIcon(((BitmapDrawable) photo).getBitmap());
            }
        } else if (DBG) {
            log("Suppress actions. number: " + number + ", missedCalls: " + this.mNumberMissedCalls);
        }
        Notification notification = builder.getNotification();
        configureLedNotification(notification);
        this.mNotificationManager.notify(1, notification);
    }

    private PendingIntent createClearMissedCallsIntent() {
        Intent intent = new Intent(this.mContext, (Class<?>) ClearMissedCallsService.class);
        intent.setAction("com.android.phone.intent.CLEAR_MISSED_CALLS");
        return PendingIntent.getService(this.mContext, 0, intent, 0);
    }

    void cancelMissedCallNotification() {
        this.mNumberMissedCalls = 0;
        this.mNumberMissedVideoCalls = 0;
        this.mNotificationManager.cancel(1);
        this.mNotificationManager.cancel(100);
    }

    void notifyMissedVideoCall(String name, String number, String label, long date) {
        String callName;
        int titleResId;
        String expandedText;
        Intent callLogIntent = PhoneGlobals.createCallLogIntent();
        this.mNumberMissedVideoCalls++;
        if (name != null && TextUtils.isGraphic(name)) {
            callName = name;
        } else if (!TextUtils.isEmpty(number)) {
            callName = number;
        } else {
            callName = this.mContext.getString(R.string.unknown);
        }
        if (this.mNumberMissedVideoCalls == 1) {
            titleResId = R.string.notification_missedVideoCallTitle;
            expandedText = callName;
        } else {
            titleResId = R.string.notification_missedVideoCallsTitle;
            expandedText = this.mContext.getString(R.string.notification_missedCallsMsg, Integer.valueOf(this.mNumberMissedVideoCalls));
        }
        Notification note = new Notification(android.R.drawable.stat_notify_missed_call, this.mContext.getString(R.string.notification_missedVideoCallTicker, callName), date);
        note.setLatestEventInfo(this.mContext, this.mContext.getText(titleResId), expandedText, PendingIntent.getActivity(this.mContext, 0, callLogIntent, 0));
        note.flags |= 16;
        note.deleteIntent = createClearMissedVideoCallsIntent();
        configureLedNotification(note);
        this.mNotificationManager.notify(100, note);
    }

    private PendingIntent createClearMissedVideoCallsIntent() {
        Intent intent = new Intent(this.mContext, (Class<?>) ClearMissedCallsService.class);
        intent.setAction("com.android.phone.intent.CLEAR_MISSED_CALLS");
        return PendingIntent.getService(this.mContext, 0, intent, 0);
    }

    private void notifySpeakerphone() {
        if (!this.mShowingSpeakerphoneIcon) {
            this.mStatusBarManager.setIcon("speakerphone", android.R.drawable.stat_sys_speakerphone, 0, this.mContext.getString(R.string.accessibility_speakerphone_enabled));
            this.mShowingSpeakerphoneIcon = true;
        }
    }

    private void cancelSpeakerphone() {
        if (this.mShowingSpeakerphoneIcon) {
            this.mStatusBarManager.removeIcon("speakerphone");
            this.mShowingSpeakerphoneIcon = false;
        }
    }

    public void updateSpeakerNotification(boolean showNotification) {
        if (DBG) {
            log("updateSpeakerNotification(" + showNotification + ")...");
        }
        if (showNotification) {
            notifySpeakerphone();
        } else {
            cancelSpeakerphone();
        }
    }

    protected void notifyMute() {
        if (!this.mShowingMuteIcon) {
            this.mStatusBarManager.setIcon("mute", android.R.drawable.stat_notify_call_mute, 0, this.mContext.getString(R.string.accessibility_call_muted));
            this.mShowingMuteIcon = true;
        }
    }

    protected void cancelMute() {
        if (this.mShowingMuteIcon) {
            this.mStatusBarManager.removeIcon("mute");
            this.mShowingMuteIcon = false;
        }
    }

    void updateMuteNotification() {
        if (this.mCM.getState() == PhoneConstants.State.OFFHOOK && PhoneUtils.getMute()) {
            if (DBG) {
                log("updateMuteNotification: MUTED");
            }
            notifyMute();
        } else {
            if (DBG) {
                log("updateMuteNotification: not muted (or not offhook)");
            }
            cancelMute();
        }
    }

    void cancelCallInProgressNotifications() {
        if (DBG) {
            log("cancelCallInProgressNotifications");
        }
        cancelMute();
        cancelSpeakerphone();
    }

    void updateMwi(boolean visible) {
        String notificationText;
        Uri ringtoneUri;
        if (DBG) {
            log("updateMwi(): " + visible);
        }
        if (visible) {
            String notificationTitle = this.mContext.getString(R.string.notification_voicemail_title);
            String vmNumber = this.mPhone.getVoiceMailNumber();
            if (DBG) {
                log("- got vm number: '" + vmNumber + "'");
            }
            if (vmNumber == null && !this.mPhone.getIccRecordsLoaded()) {
                if (DBG) {
                    log("- Null vm number: SIM records not loaded (yet)...");
                }
                int i = this.mVmNumberRetriesRemaining;
                this.mVmNumberRetriesRemaining = i - 1;
                if (i > 0) {
                    if (DBG) {
                        log("  - Retrying in 10000 msec...");
                    }
                    this.mApp.notifier.sendMwiChangedDelayed(10000L);
                    return;
                }
                Log.w("NotificationMgr", "NotificationMgr.updateMwi: getVoiceMailNumber() failed after 5 retries; giving up.");
            }
            if (TelephonyCapabilities.supportsVoiceMessageCount(this.mPhone)) {
                int vmCount = this.mPhone.getVoiceMessageCount();
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
            String uriString = prefs.getString("button_voicemail_notification_ringtone_key", null);
            if (!TextUtils.isEmpty(uriString)) {
                ringtoneUri = Uri.parse(uriString);
            } else {
                ringtoneUri = Settings.System.DEFAULT_NOTIFICATION_URI;
            }
            Notification.Builder builder = new Notification.Builder(this.mContext);
            builder.setSmallIcon(android.R.drawable.stat_notify_voicemail).setWhen(System.currentTimeMillis()).setContentTitle(notificationTitle).setContentText(notificationText).setContentIntent(pendingIntent).setSound(ringtoneUri);
            Notification notification = builder.getNotification();
            CallFeaturesSetting.migrateVoicemailVibrationSettingsIfNeeded(prefs);
            boolean vibrate = prefs.getBoolean("button_voicemail_notification_vibrate_key", false);
            if (vibrate) {
                notification.defaults |= 2;
            }
            notification.flags |= 32;
            configureLedNotification(notification);
            this.mNotificationManager.notify(5, notification);
            return;
        }
        this.mNotificationManager.cancel(5);
    }

    void updateCfi(boolean visible) {
        if (DBG) {
            log("updateCfi(): " + visible);
        }
        if (visible) {
            Intent intent = new Intent("android.intent.action.MAIN");
            intent.addFlags(268435456);
            intent.setClassName("com.android.phone", "com.android.phone.CallFeaturesSetting");
            Notification notification = new Notification(R.drawable.stat_sys_phone_call_forward, null, 0L);
            notification.setLatestEventInfo(this.mContext, this.mContext.getString(R.string.labelCF), this.mContext.getString(R.string.sum_cfu_enabled_indicator), PendingIntent.getActivity(this.mContext, 0, intent, 0));
            notification.flags |= 2;
            this.mNotificationManager.notify(6, notification);
            return;
        }
        this.mNotificationManager.cancel(6);
    }

    void updateImsRegistration(boolean registered) {
        SystemProperties.set("persist.radio.ims.registered", registered ? "1" : "0");
        if (registered) {
            Intent intent = new Intent("android.intent.action.MAIN");
            intent.addFlags(268435456);
            intent.setClassName("com.android.phone", "com.android.phone.CallFeaturesSetting");
            PendingIntent pIntent = PendingIntent.getActivity(this.mContext, 0, intent, 0);
            Notification notification = new Notification.Builder(this.mContext).setSmallIcon(R.drawable.ims_state).setContentTitle(this.mContext.getText(R.string.ims_registration)).setContentText(this.mContext.getText(R.string.ims_registration_details)).setContentIntent(pIntent).build();
            notification.flags |= 2;
            this.mNotificationManager.notify(10, notification);
            return;
        }
        this.mNotificationManager.cancel(10);
    }

    void showDataDisconnectedRoaming() {
        if (DBG) {
            log("showDataDisconnectedRoaming()...");
        }
        Intent intent = new Intent(this.mContext, (Class<?>) MobileNetworkSettings.class);
        CharSequence text = this.mContext.getText(R.string.roaming_reenable_message);
        Notification.Builder builder = new Notification.Builder(this.mContext);
        builder.setSmallIcon(android.R.drawable.stat_sys_warning);
        builder.setContentTitle(this.mContext.getText(R.string.roaming));
        builder.setContentText(text);
        builder.setContentIntent(PendingIntent.getActivity(this.mContext, 0, intent, 0));
        this.mNotificationManager.notify(7, new Notification.BigTextStyle(builder).bigText(text).build());
    }

    void hideDataDisconnectedRoaming() {
        if (DBG) {
            log("hideDataDisconnectedRoaming()...");
        }
        this.mNotificationManager.cancel(7);
    }

    private void showNetworkSelection(String operator) {
        Intent intent;
        if (DBG) {
            log("showNetworkSelection(" + operator + ")...");
        }
        String titleText = this.mContext.getString(R.string.notification_network_selection_title);
        String expandedText = this.mContext.getString(R.string.notification_network_selection_text, operator);
        Notification notification = new Notification();
        notification.icon = android.R.drawable.stat_sys_warning;
        notification.when = 0L;
        notification.flags = 2;
        notification.tickerText = null;
        if (isAppInstalled("org.codeaurora.settings.NETWORK_OPERATOR_SETTINGS_ASYNC")) {
            intent = new Intent("org.codeaurora.settings.NETWORK_OPERATOR_SETTINGS_ASYNC");
        } else {
            intent = new Intent("android.intent.action.MAIN");
            intent.setComponent(new ComponentName("com.android.phone", "com.android.phone.NetworkSetting"));
        }
        intent.setFlags(270532608);
        PendingIntent pi = PendingIntent.getActivity(this.mContext, 0, intent, 0);
        notification.setLatestEventInfo(this.mContext, titleText, expandedText, pi);
        this.mNotificationManager.notify(8, notification);
    }

    private boolean isAppInstalled(String action) {
        PackageManager pm = this.mContext.getPackageManager();
        List<ResolveInfo> list = pm.queryIntentActivities(new Intent(action), 0);
        int listSize = list.size();
        for (int i = 0; i < listSize; i++) {
            ResolveInfo resolveInfo = list.get(i);
            if ((resolveInfo.activityInfo.applicationInfo.flags & 1) != 0) {
                return true;
            }
        }
        return false;
    }

    private void cancelNetworkSelection() {
        if (DBG) {
            log("cancelNetworkSelection()...");
        }
        this.mNotificationManager.cancel(8);
    }

    void updateNetworkSelection(int serviceState, Phone phone) {
        if (TelephonyCapabilities.supportsNetworkSelection(phone)) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this.mContext);
            String networkSelection = sp.getString("network_selection_name_key", "");
            if (TextUtils.isEmpty(networkSelection)) {
                networkSelection = sp.getString("network_selection_key", "");
            }
            if (DBG) {
                log("updateNetworkSelection()...state = " + serviceState + " new network " + networkSelection);
            }
            if (serviceState == 1 && !TextUtils.isEmpty(networkSelection)) {
                if (!this.mSelectedUnavailableNotify) {
                    showNetworkSelection(networkSelection);
                    this.mSelectedUnavailableNotify = true;
                    return;
                }
                return;
            }
            if (this.mSelectedUnavailableNotify) {
                cancelNetworkSelection();
                this.mSelectedUnavailableNotify = false;
            }
        }
    }

    void postTransientNotification(int notifyId, CharSequence msg) {
        if (this.mToast != null) {
            this.mToast.cancel();
        }
        this.mToast = Toast.makeText(this.mContext, msg, 1);
        this.mToast.show();
    }

    void showHeadSetPlugin() {
        if (DBG) {
            log("showHeadSetPlugin()...");
        }
        String string = this.mContext.getString(R.string.headset_plugin_view_title);
        String string2 = this.mContext.getString(R.string.headset_plugin_view_text);
        Notification notification = new Notification();
        notification.icon = android.R.drawable.stat_sys_headset;
        notification.flags |= 32;
        notification.tickerText = string;
        Intent intent = new Intent("android.intent.action.NO_ACTION");
        intent.addFlags(268435456);
        notification.setLatestEventInfo(this.mContext, string, string2, PendingIntent.getActivity(this.mContext, 0, intent, 0));
        this.mNotificationManager.notify(9, notification);
    }

    void cancelHeadSetPlugin() {
        if (DBG) {
            log("cancelHeadSetPlugin()...");
        }
        this.mNotificationManager.cancel(9);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void log(String msg) {
        Log.d("NotificationMgr", msg);
    }
}
