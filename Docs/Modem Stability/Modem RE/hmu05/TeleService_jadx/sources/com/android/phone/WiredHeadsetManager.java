package com.android.phone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemProperties;
import android.util.Log;
import com.google.android.collect.Lists;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class WiredHeadsetManager {
    private static final boolean DBG;
    private static final String LOG_TAG = WiredHeadsetManager.class.getSimpleName();
    private boolean mIsHeadsetPlugged = false;
    private final List<WiredHeadsetListener> mListeners = Lists.newArrayList();
    private final WiredHeadsetBroadcastReceiver mReceiver = new WiredHeadsetBroadcastReceiver();

    public interface WiredHeadsetListener {
        void onWiredHeadsetConnection(boolean z);
    }

    static {
        DBG = SystemProperties.getInt("ro.debuggable", 0) == 1;
    }

    public WiredHeadsetManager(Context context) {
        IntentFilter intentFilter = new IntentFilter("android.intent.action.HEADSET_PLUG");
        context.registerReceiver(this.mReceiver, intentFilter);
    }

    public boolean isHeadsetPlugged() {
        return this.mIsHeadsetPlugged;
    }

    public void addWiredHeadsetListener(WiredHeadsetListener listener) {
        if (!this.mListeners.contains(listener)) {
            this.mListeners.add(listener);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onHeadsetConnection(boolean pluggedIn) {
        if (DBG) {
            Log.d(LOG_TAG, "Wired headset connected: " + pluggedIn);
        }
        this.mIsHeadsetPlugged = pluggedIn;
        notifyListeners();
    }

    private void notifyListeners() {
        for (int i = 0; i < this.mListeners.size(); i++) {
            this.mListeners.get(i).onWiredHeadsetConnection(this.mIsHeadsetPlugged);
        }
    }

    private class WiredHeadsetBroadcastReceiver extends BroadcastReceiver {
        private WiredHeadsetBroadcastReceiver() {
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("android.intent.action.HEADSET_PLUG")) {
                Log.d(WiredHeadsetManager.LOG_TAG, "mReceiver: ACTION_HEADSET_PLUG");
                Log.d(WiredHeadsetManager.LOG_TAG, "    state: " + intent.getIntExtra("state", 0));
                Log.d(WiredHeadsetManager.LOG_TAG, "    name: " + intent.getStringExtra("name"));
                WiredHeadsetManager.this.onHeadsetConnection(intent.getIntExtra("state", 0) == 1);
            }
        }
    }
}
