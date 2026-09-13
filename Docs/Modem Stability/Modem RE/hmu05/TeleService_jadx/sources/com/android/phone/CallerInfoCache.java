package com.android.phone;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import java.util.HashMap;

/* JADX INFO: loaded from: classes.dex */
public class CallerInfoCache {
    private static final boolean DBG;
    private static final String LOG_TAG = CallerInfoCache.class.getSimpleName();
    private static final String[] PROJECTION;
    private CacheAsyncTask mCacheAsyncTask;
    private final Context mContext;
    private volatile HashMap<String, CacheEntry> mNumberToEntry = new HashMap<>();

    static {
        DBG = SystemProperties.getInt("ro.debuggable", 0) == 1;
        PROJECTION = new String[]{"data1", "data4", "custom_ringtone", "send_to_voicemail"};
    }

    public static class CacheEntry {
        public final String customRingtone;
        public final boolean sendToVoicemail;

        public CacheEntry(String customRingtone, boolean shouldSendToVoicemail) {
            this.customRingtone = customRingtone;
            this.sendToVoicemail = shouldSendToVoicemail;
        }

        public String toString() {
            return "ringtone: " + this.customRingtone + ", " + this.sendToVoicemail;
        }
    }

    private class CacheAsyncTask extends AsyncTask<Void, Void, Void> {
        private PowerManager.WakeLock mWakeLock;

        private CacheAsyncTask() {
        }

        public void acquireWakeLockAndExecute() {
            PowerManager pm = (PowerManager) CallerInfoCache.this.mContext.getSystemService("power");
            this.mWakeLock = pm.newWakeLock(1, CallerInfoCache.LOG_TAG);
            this.mWakeLock.acquire();
            execute(new Void[0]);
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public Void doInBackground(Void... params) {
            if (CallerInfoCache.DBG) {
                CallerInfoCache.log("Start refreshing cache.");
            }
            CallerInfoCache.this.refreshCacheEntry();
            return null;
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public void onPostExecute(Void result) {
            super.onPostExecute(result);
            releaseWakeLock();
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public void onCancelled(Void result) {
            super.onCancelled(result);
            releaseWakeLock();
        }

        private void releaseWakeLock() {
            if (this.mWakeLock != null && this.mWakeLock.isHeld()) {
                this.mWakeLock.release();
            }
        }
    }

    public static CallerInfoCache init(Context context) {
        if (DBG) {
            log("init()");
        }
        CallerInfoCache cache = new CallerInfoCache(context);
        cache.startAsyncCache();
        cache.setRepeatingCacheUpdateAlarm();
        return cache;
    }

    private CallerInfoCache(Context context) {
        this.mContext = context;
    }

    void startAsyncCache() {
        if (DBG) {
            log("startAsyncCache");
        }
        if (this.mCacheAsyncTask != null) {
            Log.w(LOG_TAG, "Previous cache task is remaining.");
            this.mCacheAsyncTask.cancel(true);
        }
        this.mCacheAsyncTask = new CacheAsyncTask();
        this.mCacheAsyncTask.acquireWakeLockAndExecute();
    }

    private void setRepeatingCacheUpdateAlarm() {
        if (DBG) {
            log("setRepeatingCacheUpdateAlarm");
        }
        Intent intent = new Intent("com.android.phone.UPDATE_CALLER_INFO_CACHE");
        intent.setClass(this.mContext, CallerInfoCacheUpdateReceiver.class);
        ((AlarmManager) this.mContext.getSystemService("alarm")).setInexactRepeating(3, SystemClock.uptimeMillis() + 28800000, 28800000L, PendingIntent.getBroadcast(this.mContext, 0, intent, 268435456));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void refreshCacheEntry() {
        Cursor cursor = null;
        try {
            Cursor cursor2 = this.mContext.getContentResolver().query(ContactsContract.CommonDataKinds.Callable.CONTENT_URI, PROJECTION, "((custom_ringtone IS NOT NULL OR send_to_voicemail=1) AND data1 IS NOT NULL)", null, null);
            if (cursor2 != null) {
                HashMap<String, CacheEntry> newNumberToEntry = new HashMap<>(cursor2.getCount());
                while (cursor2.moveToNext()) {
                    String number = cursor2.getString(0);
                    String normalizedNumber = cursor2.getString(1);
                    if (normalizedNumber == null) {
                        normalizedNumber = PhoneNumberUtils.normalizeNumber(number);
                    }
                    String customRingtone = cursor2.getString(2);
                    boolean sendToVoicemail = cursor2.getInt(3) == 1;
                    if (PhoneNumberUtils.isUriNumber(number)) {
                        putNewEntryWhenAppropriate(newNumberToEntry, number, customRingtone, sendToVoicemail);
                    } else {
                        int length = normalizedNumber.length();
                        String key = length > 7 ? normalizedNumber.substring(length - 7, length) : normalizedNumber;
                        putNewEntryWhenAppropriate(newNumberToEntry, key, customRingtone, sendToVoicemail);
                    }
                }
                this.mNumberToEntry = newNumberToEntry;
                if (DBG) {
                    log("Caching entries are done. Total: " + newNumberToEntry.size());
                }
            } else {
                Log.w(LOG_TAG, "cursor is null");
            }
            if (cursor2 != null) {
                cursor2.close();
            }
        } catch (Throwable th) {
            if (0 != 0) {
                cursor.close();
            }
            throw th;
        }
    }

    private void putNewEntryWhenAppropriate(HashMap<String, CacheEntry> newNumberToEntry, String numberOrSipAddress, String customRingtone, boolean sendToVoicemail) {
        if (newNumberToEntry.containsKey(numberOrSipAddress)) {
            CacheEntry entry = newNumberToEntry.get(numberOrSipAddress);
            if (!entry.sendToVoicemail && sendToVoicemail) {
                newNumberToEntry.put(numberOrSipAddress, new CacheEntry(customRingtone, sendToVoicemail));
                return;
            }
            return;
        }
        newNumberToEntry.put(numberOrSipAddress, new CacheEntry(customRingtone, sendToVoicemail));
    }

    public CacheEntry getCacheEntry(String number) {
        if (this.mNumberToEntry == null) {
            Log.w(LOG_TAG, "Fallback cache isn't ready.");
            return null;
        }
        if (PhoneNumberUtils.isUriNumber(number)) {
            return this.mNumberToEntry.get(number);
        }
        String normalizedNumber = PhoneNumberUtils.normalizeNumber(number);
        int length = normalizedNumber.length();
        String key = length > 7 ? normalizedNumber.substring(length - 7, length) : normalizedNumber;
        return this.mNumberToEntry.get(key);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
