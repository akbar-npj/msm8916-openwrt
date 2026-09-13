package com.qualcomm.fastdormancy;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.qualcomm.qcrilhook.QcRilHook;

/* JADX INFO: loaded from: classes.dex */
public class FastDormancyService extends Service {
    static final int AGGRESSIVE_DORMANT_MODE = 2;
    static final int DATA_ACTIVE_MODE = 3;
    static final boolean DEBUG = false;
    static final int DISPLAY_OFF_TIMER = 3000;
    static final int DORMANT_MODE = 1;
    static final String FD_ENABLE_NETWORKS = "persist.fd.enable.networks";
    static final int MSG_DETECT_DATA_ACTIVITY = 2;
    static final int MSG_ENTER_DORMANCY = 1;
    static final String PACKET_MAX_COUNT = "persist.data.pkt_max_count";
    static final int POLLING_TIME = 1000;
    static final String PROPERTY_FD_SCROFF_TIMER = "persist.fd.scroff.timer";
    static final String PROPERTY_FD_SCRON_TIMER = "persist.fd.scron.timer";
    static final String PROPERTY_TX_MASK_ENABLE = "persist.data.tcp_rst_drop";
    static final String TAG = "FastDormancyService";
    AlarmManager mAlarmManager;
    PowerManager.WakeLock mDormancyWL;
    PendingIntent mPendingIntent;
    PowerManager mPowerManager;
    private QcRilHook mQcRilHook;
    private TelephonyManager mTelephonyManager;
    static final int DISPLAY_ON_TIMER = 15000;
    private static int ALARM_TIME = DISPLAY_ON_TIMER;
    private int mFDset = 1;
    private boolean mIsScreenOn = true;
    private boolean mSynPacketTrigger = DEBUG;
    private int mDataState = -1;
    private int mDataActivity = -1;
    private int mNetworkType = -1;
    private int mFdEnableNetwork = -1;
    private Handler myHandler = new Handler() { // from class: com.qualcomm.fastdormancy.FastDormancyService.1
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    FastDormancyService.this.enterDormancy();
                    break;
                case 2:
                    FastDormancyService.this.detectMobileDataActivity(FastDormancyService.DEBUG, FastDormancyService.DEBUG);
                    break;
                default:
                    Log.e(FastDormancyService.TAG, "unexpected event " + msg.what);
                    break;
            }
        }
    };
    private MobileDataStatistics mDataUsage = new MobileDataStatistics();
    PhoneStateListener mPhoneStateListener = new PhoneStateListener() { // from class: com.qualcomm.fastdormancy.FastDormancyService.4
        @Override // android.telephony.PhoneStateListener
        public void onDataConnectionStateChanged(int state, int networkType) {
            FastDormancyService.this.mDataState = state;
            FastDormancyService.this.mNetworkType = networkType;
            FastDormancyService.this.mFdEnableNetwork = SystemProperties.getInt(FastDormancyService.FD_ENABLE_NETWORKS, 42760);
            if ((FastDormancyService.this.mFdEnableNetwork & (1 << FastDormancyService.this.mNetworkType)) == 0 && FastDormancyService.this.mFDset != 1) {
                FastDormancyService.this.myHandler.removeMessages(2);
                FastDormancyService.this.mAlarmManager.cancel(FastDormancyService.this.mPendingIntent);
                FastDormancyService.this.mFDset = 1;
            }
        }

        @Override // android.telephony.PhoneStateListener
        public void onDataActivity(int direction) {
            FastDormancyService.this.mDataActivity = direction;
            FastDormancyService.this.startDataActivityCheck();
        }
    };

    private class MobileDataStatistics {
        private long rx;
        private long rxBytes;
        private long rxTcp;
        private long tx;
        private long txBytes;
        private long txTcp;

        private MobileDataStatistics() {
            this.tx = -1L;
            this.rx = -1L;
            this.txTcp = -1L;
            this.rxTcp = -1L;
            this.txBytes = -1L;
            this.rxBytes = -1L;
        }

        private MobileDataStatistics(MobileDataStatistics s) {
            this.tx = s.tx;
            this.rx = s.rx;
            this.txTcp = s.txTcp;
            this.rxTcp = s.rxTcp;
            this.txBytes = s.txBytes;
            this.rxBytes = s.rxBytes;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void update() {
            this.tx = TrafficStats.getMobileTxPackets();
            this.rx = TrafficStats.getMobileRxPackets();
            this.txTcp = TrafficStats.getMobileTcpTxPackets();
            this.rxTcp = TrafficStats.getMobileTcpRxPackets();
            this.txBytes = TrafficStats.getMobileTxBytes();
            this.rxBytes = TrafficStats.getMobileRxBytes();
        }
    }

    private void scheduleDelayedMsg() {
        this.myHandler.sendMessageDelayed(this.myHandler.obtainMessage(2), 1000L);
    }

    private void scheduleAlarm(int alarmTime) {
        this.mAlarmManager.cancel(this.mPendingIntent);
        this.mAlarmManager.set(0, System.currentTimeMillis() + ((long) alarmTime), this.mPendingIntent);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public synchronized void detectMobileDataActivity(boolean isAlarmExpired, boolean ScrStateChanged) {
        Log.d(TAG, "detect mobile data activity! isAlarmExpired: " + isAlarmExpired + "  mFDset: " + this.mFDset);
        this.myHandler.removeMessages(2);
        if (this.mFDset != 1) {
            MobileDataStatistics prev = new MobileDataStatistics(this.mDataUsage);
            this.mDataUsage.update();
            long sent = this.mDataUsage.tx - prev.tx;
            long received = this.mDataUsage.rx - prev.rx;
            Log.d(TAG, "sent:" + sent + " received:" + received);
            if (!this.mIsScreenOn && this.mFDset == 2) {
                long sentTcp = this.mDataUsage.txTcp - prev.txTcp;
                long sentTxBytes = this.mDataUsage.txBytes - prev.txBytes;
                long receivedTcp = this.mDataUsage.rxTcp - prev.rxTcp;
                long receivedRxBytes = this.mDataUsage.rxBytes - prev.rxBytes;
                int maxPktCnt = SystemProperties.getInt(PACKET_MAX_COUNT, 20);
                if (!SystemProperties.getBoolean(PROPERTY_TX_MASK_ENABLE, true)) {
                    Log.d(TAG, "rst packet drop feature is not enabled");
                } else if (sent == 0 && received <= receivedTcp && receivedTcp > 0 && received < maxPktCnt && receivedRxBytes / receivedTcp <= 60) {
                    this.mSynPacketTrigger = true;
                }
            }
        }
        if (this.mFDset != 1 && ((1 == 0 && 1 == 0) || this.mSynPacketTrigger)) {
            if (isAlarmExpired && (this.mSynPacketTrigger || this.mFDset == DATA_ACTIVE_MODE)) {
                Log.d(TAG, "enter dormancy");
                this.mDormancyWL.acquire();
                if (this.mSynPacketTrigger) {
                    this.mSynPacketTrigger = DEBUG;
                }
                this.myHandler.sendMessage(this.myHandler.obtainMessage(1));
            } else {
                if (ScrStateChanged) {
                    scheduleAlarm(ALARM_TIME);
                }
                if (!this.mSynPacketTrigger && isAlarmExpired) {
                    scheduleAlarm(ALARM_TIME - 1000);
                }
                this.mFDset = DATA_ACTIVE_MODE;
                scheduleDelayedMsg();
            }
        } else if (this.mFDset == 1 && !this.mIsScreenOn) {
            Log.d(TAG, "Alarm:1000");
            scheduleAlarm(POLLING_TIME);
            this.mFDset = 2;
        } else {
            Log.d(TAG, "scheduling timer:1000ms Alarm:" + ALARM_TIME + "ms");
            scheduleDelayedMsg();
            scheduleAlarm(ALARM_TIME);
            this.mFDset = DATA_ACTIVE_MODE;
        }
        if ((this.mFdEnableNetwork & (1 << this.mNetworkType)) == 0) {
            this.myHandler.removeMessages(2);
            this.mAlarmManager.cancel(this.mPendingIntent);
            this.mFDset = 1;
        }
    }

    private void broadcastReceiverInit() {
        BroadcastReceiver mAlarmReceiver = new BroadcastReceiver() { // from class: com.qualcomm.fastdormancy.FastDormancyService.2
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context c, Intent i) {
                FastDormancyService.this.mDormancyWL.acquire();
                FastDormancyService.this.detectMobileDataActivity(true, FastDormancyService.DEBUG);
                FastDormancyService.this.mDormancyWL.release();
            }
        };
        registerReceiver(mAlarmReceiver, new IntentFilter("FDAlarmExpiry"));
        this.mPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent("FDAlarmExpiry"), 0);
        this.mAlarmManager = (AlarmManager) getSystemService("alarm");
    }

    private void screenStateNotification() {
        BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() { // from class: com.qualcomm.fastdormancy.FastDormancyService.3
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context c, Intent intent) {
                if (intent.getAction().equals("android.intent.action.SCREEN_OFF")) {
                    FastDormancyService.this.mIsScreenOn = FastDormancyService.DEBUG;
                    int unused = FastDormancyService.ALARM_TIME = SystemProperties.getInt(FastDormancyService.PROPERTY_FD_SCROFF_TIMER, FastDormancyService.DISPLAY_OFF_TIMER);
                } else if (intent.getAction().equals("android.intent.action.SCREEN_ON")) {
                    FastDormancyService.this.mIsScreenOn = true;
                    int unused2 = FastDormancyService.ALARM_TIME = SystemProperties.getInt(FastDormancyService.PROPERTY_FD_SCRON_TIMER, FastDormancyService.DISPLAY_ON_TIMER);
                }
                if (FastDormancyService.this.mFDset != 1) {
                    FastDormancyService.this.detectMobileDataActivity(FastDormancyService.DEBUG, true);
                }
            }
        };
        IntentFilter filter = new IntentFilter("android.intent.action.SCREEN_ON");
        filter.addAction("android.intent.action.SCREEN_OFF");
        registerReceiver(mScreenStateReceiver, filter);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void startDataActivityCheck() {
        if (this.mDataState == 2 && this.mFDset == 1 && (this.mFdEnableNetwork & (1 << this.mNetworkType)) != 0 && this.mDataActivity < 4) {
            detectMobileDataActivity(DEBUG, DEBUG);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void enterDormancy() {
        this.mQcRilHook.qcRilGoDormant("");
        this.mFDset = 1;
        this.mDormancyWL.release();
    }

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override // android.app.Service
    public void onCreate() {
        this.mQcRilHook = new QcRilHook(getApplicationContext());
        this.mPowerManager = (PowerManager) getSystemService("power");
        this.mDormancyWL = this.mPowerManager.newWakeLock(1, TAG);
        this.mTelephonyManager = (TelephonyManager) getApplicationContext().getSystemService("phone");
        this.mTelephonyManager.listen(this.mPhoneStateListener, 192);
        broadcastReceiverInit();
        screenStateNotification();
        this.mIsScreenOn = this.mPowerManager.isScreenOn();
        if (!this.mIsScreenOn) {
            ALARM_TIME = DISPLAY_OFF_TIMER;
        }
    }
}
