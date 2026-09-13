package com.android.phone;

import android.content.Context;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IPowerManager;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.SystemVibrator;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import com.android.internal.telephony.Phone;

/* JADX INFO: loaded from: classes.dex */
public class Ringer {
    private static final boolean DBG;
    private static Ringer sInstance;
    private final BluetoothManager mBluetoothManager;
    Context mContext;
    volatile boolean mContinueVibrating;
    Uri mCustomRingtoneUri = Settings.System.DEFAULT_RINGTONE_URI;
    private long mFirstRingEventTime = -1;
    private long mFirstRingStartTime = -1;
    IPowerManager mPowerManager = IPowerManager.Stub.asInterface(ServiceManager.getService("power"));
    private Handler mRingHandler;
    private Worker mRingThread;
    Ringtone mRingtone;
    Vibrator mVibrator;
    VibratorThread mVibratorThread;

    static {
        DBG = SystemProperties.getInt("ro.debuggable", 0) == 1;
    }

    static Ringer init(Context context, BluetoothManager bluetoothManager) {
        Ringer ringer;
        synchronized (Ringer.class) {
            if (sInstance == null) {
                sInstance = new Ringer(context, bluetoothManager);
            } else {
                Log.wtf("Ringer", "init() called multiple times!  sInstance = " + sInstance);
            }
            ringer = sInstance;
        }
        return ringer;
    }

    private Ringer(Context context, BluetoothManager bluetoothManager) {
        this.mContext = context;
        this.mBluetoothManager = bluetoothManager;
        this.mVibrator = new SystemVibrator(context);
    }

    void updateRingerContextAfterRadioTechnologyChange(Phone phone) {
        if (DBG) {
            Log.d("Ringer", "updateRingerContextAfterRadioTechnologyChange...");
        }
        this.mContext = phone.getContext();
    }

    boolean isRinging() {
        boolean z;
        synchronized (this) {
            z = isRingtonePlaying() || isVibrating();
        }
        return z;
    }

    private boolean isRingtonePlaying() {
        boolean z = true;
        synchronized (this) {
            if ((this.mRingtone == null || !this.mRingtone.isPlaying()) && (this.mRingHandler == null || !this.mRingHandler.hasMessages(1))) {
                z = false;
            }
        }
        return z;
    }

    private boolean isVibrating() {
        boolean z;
        synchronized (this) {
            z = this.mVibratorThread != null;
        }
        return z;
    }

    void ring() {
        if (DBG) {
            log("ring()...");
        }
        synchronized (this) {
            try {
                if (this.mBluetoothManager.showBluetoothIndication()) {
                    this.mPowerManager.setAttentionLight(true, 255);
                } else {
                    this.mPowerManager.setAttentionLight(true, 16777215);
                }
            } catch (RemoteException e) {
            }
            if (shouldVibrate() && this.mVibratorThread == null) {
                this.mContinueVibrating = true;
                this.mVibratorThread = new VibratorThread();
                if (DBG) {
                    log("- starting vibrator...");
                }
                this.mVibratorThread.start();
            }
            AudioManager audioManager = (AudioManager) this.mContext.getSystemService("audio");
            if (audioManager.getStreamVolume(2) == 0) {
                if (DBG) {
                    log("skipping ring because volume is zero");
                }
                PhoneUtils.setAudioMode();
                return;
            }
            makeLooper();
            if (this.mFirstRingEventTime < 0) {
                this.mFirstRingEventTime = SystemClock.elapsedRealtime();
                this.mRingHandler.sendEmptyMessage(1);
            } else if (this.mFirstRingStartTime > 0) {
                if (DBG) {
                    log("delaying ring by " + (this.mFirstRingStartTime - this.mFirstRingEventTime));
                }
                this.mRingHandler.sendEmptyMessageDelayed(1, this.mFirstRingStartTime - this.mFirstRingEventTime);
            } else {
                this.mFirstRingEventTime = SystemClock.elapsedRealtime();
            }
        }
    }

    boolean shouldVibrate() {
        AudioManager audioManager = (AudioManager) this.mContext.getSystemService("audio");
        int ringerMode = audioManager.getRingerMode();
        if (CallFeaturesSetting.getVibrateWhenRinging(this.mContext)) {
            return ringerMode != 0;
        }
        return ringerMode == 1;
    }

    void stopRing() {
        synchronized (this) {
            if (DBG) {
                log("stopRing()...");
            }
            try {
                this.mPowerManager.setAttentionLight(false, 0);
            } catch (RemoteException e) {
            }
            if (this.mRingHandler != null) {
                this.mRingHandler.removeCallbacksAndMessages(null);
                Message msg = this.mRingHandler.obtainMessage(3);
                msg.obj = this.mRingtone;
                this.mRingHandler.sendMessage(msg);
                PhoneUtils.setAudioMode();
                this.mRingThread = null;
                this.mRingHandler = null;
                this.mRingtone = null;
                this.mFirstRingEventTime = -1L;
                this.mFirstRingStartTime = -1L;
            } else if (DBG) {
                log("- stopRing: null mRingHandler!");
            }
            if (this.mVibratorThread != null) {
                if (DBG) {
                    log("- stopRing: cleaning up vibrator thread...");
                }
                this.mContinueVibrating = false;
                this.mVibratorThread = null;
            }
            this.mVibrator.cancel();
        }
    }

    private class VibratorThread extends Thread {
        private VibratorThread() {
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            while (Ringer.this.mContinueVibrating) {
                Ringer.this.mVibrator.vibrate(1000L);
                SystemClock.sleep(2000L);
            }
        }
    }

    private class Worker implements Runnable {
        private final Object mLock = new Object();
        private Looper mLooper;

        Worker(String name) {
            Thread t = new Thread(null, this, name);
            t.start();
            synchronized (this.mLock) {
                while (this.mLooper == null) {
                    try {
                        this.mLock.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
        }

        public Looper getLooper() {
            return this.mLooper;
        }

        @Override // java.lang.Runnable
        public void run() {
            synchronized (this.mLock) {
                Looper.prepare();
                this.mLooper = Looper.myLooper();
                this.mLock.notifyAll();
            }
            Looper.loop();
        }
    }

    void setCustomRingtoneUri(Uri uri) {
        if (uri != null) {
            this.mCustomRingtoneUri = uri;
        }
    }

    private void makeLooper() {
        if (this.mRingThread == null) {
            this.mRingThread = new Worker("ringer");
            this.mRingHandler = new Handler(this.mRingThread.getLooper()) { // from class: com.android.phone.Ringer.1
                @Override // android.os.Handler
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case 1:
                            if (Ringer.DBG) {
                                Ringer.log("mRingHandler: PLAY_RING_ONCE...");
                            }
                            if (Ringer.this.mRingtone == null && !hasMessages(3)) {
                                if (Ringer.DBG) {
                                    Ringer.log("creating ringtone: " + Ringer.this.mCustomRingtoneUri);
                                }
                                Ringtone r = RingtoneManager.getRingtone(Ringer.this.mContext, Ringer.this.mCustomRingtoneUri);
                                synchronized (Ringer.this) {
                                    if (!hasMessages(3)) {
                                        Ringer.this.mRingtone = r;
                                    }
                                    break;
                                }
                            }
                            Ringtone r2 = Ringer.this.mRingtone;
                            if (r2 != null && !hasMessages(3) && !r2.isPlaying()) {
                                PhoneUtils.setAudioMode();
                                r2.play();
                                synchronized (Ringer.this) {
                                    if (Ringer.this.mFirstRingStartTime < 0) {
                                        Ringer.this.mFirstRingStartTime = SystemClock.elapsedRealtime();
                                    }
                                    break;
                                }
                                return;
                            }
                            return;
                        case 2:
                        default:
                            return;
                        case 3:
                            if (Ringer.DBG) {
                                Ringer.log("mRingHandler: STOP_RING...");
                            }
                            Ringtone r3 = (Ringtone) msg.obj;
                            if (r3 == null) {
                                if (Ringer.DBG) {
                                    Ringer.log("- STOP_RING with null ringtone!  msg = " + msg);
                                }
                            } else {
                                r3.stop();
                            }
                            getLooper().quit();
                            return;
                    }
                }
            };
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void log(String msg) {
        Log.d("Ringer", msg);
    }
}
