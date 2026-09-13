package com.android.internal.telephony.dataconnection;

import android.R;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.LinkCapabilities;
import android.net.LinkProperties;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.MSimTelephonyManager;
import android.telephony.Rlog;
import android.text.TextUtils;
import android.util.EventLog;
import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.AsyncChannel;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/* JADX INFO: loaded from: classes.dex */
public abstract class DcTrackerBase extends Handler {
    protected static final int APN_DELAY_DEFAULT_MILLIS = 20000;
    protected static final int APN_FAIL_FAST_DELAY_DEFAULT_MILLIS = 3000;
    protected static final String APN_RESTORE_DELAY_PROP_NAME = "android.telephony.apn-restore";
    protected static final int DATA_CONNECTION_ACTIVE_PH_LINK_DOWN = 1;
    protected static final int DATA_CONNECTION_ACTIVE_PH_LINK_INACTIVE = 0;
    protected static final int DATA_CONNECTION_ACTIVE_PH_LINK_UP = 2;
    protected static final int DATA_STALL_ALARM_AGGRESSIVE_DELAY_IN_MS_DEFAULT = 60000;
    protected static final int DATA_STALL_ALARM_NON_AGGRESSIVE_DELAY_IN_MS_DEFAULT = 180000;
    protected static final String DATA_STALL_ALARM_TAG_EXTRA = "data.stall.alram.tag";
    protected static final boolean DATA_STALL_NOT_SUSPECTED = false;
    protected static final int DATA_STALL_NO_RECV_POLL_LIMIT = 1;
    protected static final boolean DATA_STALL_SUSPECTED = true;
    protected static final boolean DBG = true;
    protected static final String DEBUG_PROV_APN_ALARM = "persist.debug.prov_apn_alarm";
    protected static final String DEFALUT_DATA_ON_BOOT_PROP = "net.def_data_on_boot";
    protected static final String DEFAULT_DATA_RETRY_CONFIG = "default_randomization=2000,5000,10000,20000,40000,80000:5000,160000:5000,320000:5000,640000:5000,1280000:5000,1800000:5000";
    protected static final int DEFAULT_MAX_PDP_RESET_FAIL = 3;
    private static final int DEFAULT_MDC_INITIAL_RETRY = 1;
    protected static final String INTENT_DATA_STALL_ALARM = "com.android.internal.telephony.data-stall";
    protected static final String INTENT_PROVISIONING_APN_ALARM = "com.android.internal.telephony.provisioning_apn_alarm";
    protected static final String INTENT_RECONNECT_ALARM = "com.android.internal.telephony.data-reconnect";
    protected static final String INTENT_RECONNECT_ALARM_EXTRA_REASON = "reconnect_alarm_extra_reason";
    protected static final String INTENT_RECONNECT_ALARM_EXTRA_TYPE = "reconnect_alarm_extra_type";
    protected static final String INTENT_RESTART_TRYSETUP_ALARM = "com.android.internal.telephony.data-restart-trysetup";
    protected static final String INTENT_RESTART_TRYSETUP_ALARM_EXTRA_TYPE = "restart_trysetup_alarm_extra_type";
    protected static final int NO_RECV_POLL_LIMIT = 24;
    protected static final String NULL_IP = "0.0.0.0";
    protected static final int NUMBER_SENT_PACKETS_OF_HANG = 10;
    protected static final int POLL_LONGEST_RTT = 120000;
    protected static final int POLL_NETSTAT_MILLIS = 1000;
    protected static final int POLL_NETSTAT_SCREEN_OFF_MILLIS = 600000;
    protected static final int POLL_NETSTAT_SLOW_MILLIS = 5000;
    protected static final int PROVISIONING_APN_ALARM_DELAY_IN_MS_DEFAULT = 900000;
    protected static final String PROVISIONING_APN_ALARM_TAG_EXTRA = "provisioning.apn.alarm.tag";
    protected static final boolean RADIO_TESTS = false;
    protected static final int RESTORE_DEFAULT_APN_DELAY = 60000;
    protected static final String SECONDARY_DATA_RETRY_CONFIG = "max_retries=3, 5000, 5000, 5000";
    protected static final boolean VDBG = false;
    protected static final boolean VDBG_STALL = true;
    protected DataProfile mActiveDp;
    AlarmManager mAlarmManager;
    protected boolean mAutoAttachOnCreation;
    protected int mCidActive;
    ConnectivityManager mCm;
    private final DataRoamingSettingObserver mDataRoamingSettingObserver;
    protected DcTesterFailBringUpAll mDcTesterFailBringUpAll;
    protected DcController mDcc;
    protected int mNetStatPollPeriod;
    protected PhoneBase mPhone;
    protected ContentResolver mResolver;
    protected long mRxPkts;
    protected long mSentSinceLastRecv;
    protected long mTxPkts;
    protected UiccController mUiccController;
    protected boolean mUserDataEnabled;
    protected static boolean sPolicyDataEnabled = true;
    protected static int sEnableFailFastRefCounter = 0;
    static LinkedHashMap<String, Integer> mApnPriorities = new LinkedHashMap<String, Integer>() { // from class: com.android.internal.telephony.dataconnection.DcTrackerBase.1
        {
            put("ia", 8);
            put("cbs", 7);
            put("ims", 6);
            put("fota", 5);
            put("hipri", 4);
            put("dun", 3);
            put("supl", 2);
            put("mms", 1);
            put("default", 0);
        }
    };
    protected Object mDataEnabledLock = new Object();
    protected boolean mInternalDataEnabled = true;
    protected boolean[] mDataEnabled = new boolean[9];
    private int mEnabledCount = 0;
    protected String mRequestedApnType = "default";
    protected String RADIO_RESET_PROPERTY = "gsm.radioreset";
    protected AtomicReference<IccRecords> mIccRecords = new AtomicReference<>();
    protected DctConstants.Activity mActivity = DctConstants.Activity.NONE;
    protected DctConstants.State mState = DctConstants.State.IDLE;
    protected Handler mDataConnectionTracker = null;
    protected boolean mNetStatPollEnabled = false;
    protected TxRxSum mDataStallTxRxSum = new TxRxSum(0, 0);
    protected int mDataStallAlarmTag = (int) SystemClock.elapsedRealtime();
    protected PendingIntent mDataStallAlarmIntent = null;
    protected int mNoRecvPollCount = 0;
    protected volatile boolean mDataStallDetectionEnabled = true;
    protected volatile boolean mFailFast = false;
    protected boolean mInVoiceCall = false;
    protected boolean mIsWifiConnected = false;
    protected PendingIntent mReconnectIntent = null;
    protected boolean mIsScreenOn = true;
    protected AtomicInteger mUniqueIdGenerator = new AtomicInteger(0);
    protected HashMap<Integer, DataConnection> mDataConnections = new HashMap<>();
    protected HashMap<Integer, DcAsyncChannel> mDataConnectionAcHashMap = new HashMap<>();
    protected HashMap<String, Integer> mApnToDataConnectionId = new HashMap<>();
    protected final ConcurrentHashMap<String, ApnContext> mApnContexts = new ConcurrentHashMap<>();
    protected final PriorityQueue<ApnContext> mPrioritySortedApnContexts = new PriorityQueue<>(5, new Comparator<ApnContext>() { // from class: com.android.internal.telephony.dataconnection.DcTrackerBase.2
        @Override // java.util.Comparator
        public int compare(ApnContext c1, ApnContext c2) {
            return c2.priority - c1.priority;
        }
    });
    protected ArrayList<DataProfile> mAllDps = new ArrayList<>();
    protected DataProfile mPreferredDp = null;
    protected boolean mIsPsRestricted = false;
    protected boolean mIsDisposed = false;
    protected boolean mIsProvisioning = false;
    protected String mProvisioningUrl = null;
    protected PendingIntent mProvisioningApnAlarmIntent = null;
    protected int mProvisioningApnAlarmTag = (int) SystemClock.elapsedRealtime();
    protected AsyncChannel mReplyAc = new AsyncChannel();
    protected BroadcastReceiver mIntentReceiver = new BroadcastReceiver() { // from class: com.android.internal.telephony.dataconnection.DcTrackerBase.3
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            DcTrackerBase.this.log("onReceive: action=" + action);
            if (action.equals("android.intent.action.SCREEN_ON")) {
                DcTrackerBase.this.mIsScreenOn = true;
                DcTrackerBase.this.stopNetStatPoll();
                DcTrackerBase.this.startNetStatPoll();
                DcTrackerBase.this.restartDataStallAlarm();
                return;
            }
            if (action.equals("android.intent.action.SCREEN_OFF")) {
                DcTrackerBase.this.mIsScreenOn = false;
                DcTrackerBase.this.stopNetStatPoll();
                DcTrackerBase.this.startNetStatPoll();
                DcTrackerBase.this.restartDataStallAlarm();
                return;
            }
            if (action.startsWith(DcTrackerBase.INTENT_RECONNECT_ALARM)) {
                DcTrackerBase.this.log("Reconnect alarm. Previous state was " + DcTrackerBase.this.mState);
                DcTrackerBase.this.onActionIntentReconnectAlarm(intent);
                return;
            }
            if (action.startsWith(DcTrackerBase.INTENT_RESTART_TRYSETUP_ALARM)) {
                DcTrackerBase.this.log("Restart trySetup alarm");
                DcTrackerBase.this.onActionIntentRestartTrySetupAlarm(intent);
                return;
            }
            if (action.equals(DcTrackerBase.INTENT_DATA_STALL_ALARM)) {
                DcTrackerBase.this.onActionIntentDataStallAlarm(intent);
                return;
            }
            if (action.equals(DcTrackerBase.INTENT_PROVISIONING_APN_ALARM)) {
                DcTrackerBase.this.onActionIntentProvisioningApnAlarm(intent);
                return;
            }
            if (action.equals("android.net.wifi.STATE_CHANGE")) {
                NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra("networkInfo");
                DcTrackerBase.this.mIsWifiConnected = networkInfo != null && networkInfo.isConnected();
                DcTrackerBase.this.log("NETWORK_STATE_CHANGED_ACTION: mIsWifiConnected=" + DcTrackerBase.this.mIsWifiConnected);
            } else if (action.equals("android.net.wifi.WIFI_STATE_CHANGED")) {
                boolean enabled = intent.getIntExtra("wifi_state", 4) == 3;
                if (!enabled) {
                    DcTrackerBase.this.mIsWifiConnected = false;
                }
                DcTrackerBase.this.log("WIFI_STATE_CHANGED_ACTION: enabled=" + enabled + " mIsWifiConnected=" + DcTrackerBase.this.mIsWifiConnected);
            }
        }
    };
    private Runnable mPollNetStat = new Runnable() { // from class: com.android.internal.telephony.dataconnection.DcTrackerBase.4
        @Override // java.lang.Runnable
        public void run() {
            DcTrackerBase.this.updateDataActivity();
            if (DcTrackerBase.this.mIsScreenOn) {
                DcTrackerBase.this.mNetStatPollPeriod = Settings.Global.getInt(DcTrackerBase.this.mResolver, "pdp_watchdog_poll_interval_ms", 1000);
            } else {
                DcTrackerBase.this.mNetStatPollPeriod = Settings.Global.getInt(DcTrackerBase.this.mResolver, "pdp_watchdog_long_poll_interval_ms", DcTrackerBase.POLL_NETSTAT_SCREEN_OFF_MILLIS);
            }
            if (DcTrackerBase.this.mNetStatPollEnabled) {
                DcTrackerBase.this.mDataConnectionTracker.postDelayed(this, DcTrackerBase.this.mNetStatPollPeriod);
            }
        }
    };

    protected abstract void completeConnection(ApnContext apnContext);

    protected abstract boolean disconnectOneLowerPriorityCall(String str);

    protected abstract DctConstants.State getOverallState();

    public abstract DctConstants.State getState(String str);

    protected abstract void gotoIdleAndNotifyDataConnection(String str);

    protected abstract boolean isApnTypeAvailable(String str);

    protected abstract boolean isDataAllowed();

    public abstract boolean isDataPossible(String str);

    public abstract boolean isDisconnected();

    protected abstract boolean isProvisioningApn(String str);

    protected abstract void log(String str);

    protected abstract void loge(String str);

    protected abstract void onCleanUpAllConnections(String str);

    protected abstract void onCleanUpConnection(boolean z, int i, String str);

    protected abstract void onDataSetupComplete(AsyncResult asyncResult);

    protected abstract void onDataSetupCompleteError(AsyncResult asyncResult);

    protected abstract void onDisconnectDcRetrying(int i, AsyncResult asyncResult);

    protected abstract void onDisconnectDone(int i, AsyncResult asyncResult);

    protected abstract void onRadioAvailable();

    protected abstract void onRadioOffOrNotAvailable();

    protected abstract void onRoamingOff();

    protected abstract void onRoamingOn();

    protected abstract boolean onTrySetupData(String str);

    protected abstract boolean onUpdateIcc();

    protected abstract void onVoiceCallEnded();

    protected abstract void onVoiceCallStarted();

    protected abstract void restartRadio();

    protected abstract void setState(DctConstants.State state);

    private class DataRoamingSettingObserver extends ContentObserver {
        public DataRoamingSettingObserver(Handler handler, Context context) {
            super(handler);
            DcTrackerBase.this.mResolver = context.getContentResolver();
        }

        public void register() {
            DcTrackerBase.this.mResolver.registerContentObserver(Settings.Global.getUriFor("data_roaming"), false, this);
        }

        public void unregister() {
            DcTrackerBase.this.mResolver.unregisterContentObserver(this);
        }

        @Override // android.database.ContentObserver
        public void onChange(boolean selfChange) {
            if (DcTrackerBase.this.mPhone.getServiceState().getRoaming()) {
                DcTrackerBase.this.sendMessage(DcTrackerBase.this.obtainMessage(270347));
            }
        }
    }

    protected int getInitialMaxRetry() {
        if (this.mFailFast) {
            return 0;
        }
        int value = SystemProperties.getInt("mdc_initial_max_retry", 1);
        return Settings.Global.getInt(this.mResolver, "mdc_initial_max_retry", value);
    }

    public class TxRxSum {
        public long rxPkts;
        public long txPkts;

        public TxRxSum() {
            reset();
        }

        public TxRxSum(long txPkts, long rxPkts) {
            this.txPkts = txPkts;
            this.rxPkts = rxPkts;
        }

        public TxRxSum(TxRxSum sum) {
            this.txPkts = sum.txPkts;
            this.rxPkts = sum.rxPkts;
        }

        public void reset() {
            this.txPkts = -1L;
            this.rxPkts = -1L;
        }

        public String toString() {
            return "{txSum=" + this.txPkts + " rxSum=" + this.rxPkts + "}";
        }

        public void updateTxRxSum() {
            this.txPkts = TrafficStats.getMobileTcpTxPackets();
            this.rxPkts = TrafficStats.getMobileTcpRxPackets();
        }
    }

    protected void onActionIntentReconnectAlarm(Intent intent) {
        String reason = intent.getStringExtra(INTENT_RECONNECT_ALARM_EXTRA_REASON);
        String apnType = intent.getStringExtra(INTENT_RECONNECT_ALARM_EXTRA_TYPE);
        ApnContext apnContext = this.mApnContexts.get(apnType);
        log("onActionIntentReconnectAlarm: mState=" + this.mState + " reason=" + reason + " apnType=" + apnType + " apnContext=" + apnContext + " mDataConnectionAsyncChannels=" + this.mDataConnectionAcHashMap);
        if (apnContext != null && apnContext.isEnabled()) {
            apnContext.setReason(reason);
            DctConstants.State apnContextState = apnContext.getState();
            log("onActionIntentReconnectAlarm: apnContext state=" + apnContextState);
            if (apnContextState == DctConstants.State.FAILED || apnContextState == DctConstants.State.IDLE) {
                log("onActionIntentReconnectAlarm: state is FAILED|IDLE, disassociate");
                DcAsyncChannel dcac = apnContext.getDcAc();
                if (dcac != null) {
                    dcac.tearDown(apnContext, "", null);
                }
                apnContext.setDataConnectionAc(null);
                apnContext.setState(DctConstants.State.IDLE);
            } else {
                log("onActionIntentReconnectAlarm: keep associated");
            }
            sendMessage(obtainMessage(270339, apnContext));
            apnContext.setReconnectIntent(null);
        }
    }

    protected void onActionIntentRestartTrySetupAlarm(Intent intent) {
        String apnType = intent.getStringExtra(INTENT_RESTART_TRYSETUP_ALARM_EXTRA_TYPE);
        ApnContext apnContext = this.mApnContexts.get(apnType);
        log("onActionIntentRestartTrySetupAlarm: mState=" + this.mState + " apnType=" + apnType + " apnContext=" + apnContext + " mDataConnectionAsyncChannels=" + this.mDataConnectionAcHashMap);
        sendMessage(obtainMessage(270339, apnContext));
    }

    protected void onActionIntentDataStallAlarm(Intent intent) {
        log("onActionIntentDataStallAlarm: action=" + intent.getAction());
        Message msg = obtainMessage(270353, intent.getAction());
        msg.arg1 = intent.getIntExtra(DATA_STALL_ALARM_TAG_EXTRA, 0);
        sendMessage(msg);
    }

    protected DcTrackerBase(PhoneBase phone) {
        this.mUserDataEnabled = true;
        this.mAutoAttachOnCreation = false;
        log("DCT.constructor");
        this.mPhone = phone;
        this.mResolver = this.mPhone.getContext().getContentResolver();
        this.mUiccController = UiccController.getInstance();
        this.mUiccController.registerForIccChanged(this, 270369, null);
        this.mAlarmManager = (AlarmManager) this.mPhone.getContext().getSystemService("alarm");
        this.mCm = (ConnectivityManager) this.mPhone.getContext().getSystemService("connectivity");
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.SCREEN_ON");
        filter.addAction("android.intent.action.SCREEN_OFF");
        filter.addAction("android.net.wifi.STATE_CHANGE");
        filter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
        filter.addAction(INTENT_DATA_STALL_ALARM);
        filter.addAction(INTENT_PROVISIONING_APN_ALARM);
        this.mUserDataEnabled = Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), "mobile_data", 1) == 1;
        this.mPhone.getContext().registerReceiver(this.mIntentReceiver, filter, null, this.mPhone);
        this.mDataEnabled[0] = SystemProperties.getBoolean(DEFALUT_DATA_ON_BOOT_PROP, true);
        if (this.mDataEnabled[0]) {
            this.mEnabledCount++;
        }
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this.mPhone.getContext());
        this.mAutoAttachOnCreation = sp.getBoolean(PhoneBase.DATA_DISABLED_ON_BOOT_KEY, false);
        this.mDataRoamingSettingObserver = new DataRoamingSettingObserver(this.mPhone, this.mPhone.getContext());
        this.mDataRoamingSettingObserver.register();
        HandlerThread dcHandlerThread = new HandlerThread("DcHandlerThread");
        dcHandlerThread.start();
        Handler dcHandler = new Handler(dcHandlerThread.getLooper());
        this.mDcc = DcController.makeDcc(this.mPhone, this, dcHandler);
        this.mDcTesterFailBringUpAll = new DcTesterFailBringUpAll(this.mPhone, dcHandler);
    }

    public void dispose() {
        log("DCT.dispose");
        for (DcAsyncChannel dcac : this.mDataConnectionAcHashMap.values()) {
            dcac.disconnect();
        }
        this.mDataConnectionAcHashMap.clear();
        this.mIsDisposed = true;
        this.mPhone.getContext().unregisterReceiver(this.mIntentReceiver);
        this.mUiccController.unregisterForIccChanged(this);
        this.mDataRoamingSettingObserver.unregister();
        this.mDcc.dispose();
        this.mDcTesterFailBringUpAll.dispose();
    }

    public DctConstants.Activity getActivity() {
        return this.mActivity;
    }

    private void setActivity(DctConstants.Activity activity) {
        log("setActivity =" + activity);
        this.mActivity = activity;
        this.mPhone.notifyDataActivity();
    }

    public boolean isApnTypeActive(String type) {
        DataProfile dunApn;
        if (!"dun".equals(type) || (dunApn = fetchDunApn()) == null) {
            return this.mActiveDp != null && this.mActiveDp.canHandleType(type);
        }
        return this.mActiveDp != null && dunApn.toHash().equals(this.mActiveDp.toHash());
    }

    protected DataProfile fetchDunApn() {
        if (SystemProperties.getBoolean("net.tethering.noprovisioning", false)) {
            log("fetchDunApn: net.tethering.noprovisioning=true ret: null");
            return null;
        }
        Context c = this.mPhone.getContext();
        String apnData = Settings.Global.getString(c.getContentResolver(), "tether_dun_apn");
        ApnSetting dunSetting = ApnSetting.fromString(apnData);
        if (dunSetting != null) {
            IccRecords r = this.mIccRecords.get();
            String operator = r != null ? r.getOperatorNumeric() : "";
            if (dunSetting.numeric.equals(operator)) {
                return dunSetting;
            }
        }
        String apnData2 = c.getResources().getString(R.string.paste_as_plain_text);
        return ApnSetting.fromString(apnData2);
    }

    public String[] getActiveApnTypes() {
        if (this.mActiveDp != null) {
            return this.mActiveDp.types;
        }
        String[] result = {"default"};
        return result;
    }

    public String getActiveApnString(String apnType) {
        if (this.mActiveDp == null) {
            return null;
        }
        String result = this.mActiveDp.apn;
        return result;
    }

    public void setDataOnRoamingEnabled(boolean enabled) {
        if (getDataOnRoamingEnabled() != enabled) {
            ContentResolver resolver = this.mPhone.getContext().getContentResolver();
            Settings.Global.putInt(resolver, "data_roaming", enabled ? 1 : 0);
        }
    }

    public boolean getDataOnRoamingEnabled() {
        try {
            ContentResolver resolver = this.mPhone.getContext().getContentResolver();
            return Settings.Global.getInt(resolver, "data_roaming") != 0;
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        boolean isProvApn;
        String apnType;
        switch (msg.what) {
            case 69636:
                log("DISCONNECTED_CONNECTED: msg=" + msg);
                DcAsyncChannel dcac = (DcAsyncChannel) msg.obj;
                this.mDataConnectionAcHashMap.remove(Integer.valueOf(dcac.getDataConnectionIdSync()));
                dcac.disconnected();
                break;
            case 270336:
                this.mCidActive = msg.arg1;
                onDataSetupComplete((AsyncResult) msg.obj);
                break;
            case 270337:
                onRadioAvailable();
                break;
            case 270339:
                String reason = null;
                if (msg.obj instanceof String) {
                    reason = (String) msg.obj;
                }
                onTrySetupData(reason);
                break;
            case 270342:
                onRadioOffOrNotAvailable();
                break;
            case 270343:
                onVoiceCallStarted();
                break;
            case 270344:
                onVoiceCallEnded();
                break;
            case 270347:
                onRoamingOn();
                break;
            case 270348:
                onRoamingOff();
                break;
            case 270349:
                onEnableApn(msg.arg1, msg.arg2);
                break;
            case 270351:
                log("DataConnectionTracker.handleMessage: EVENT_DISCONNECT_DONE msg=" + msg);
                onDisconnectDone(msg.arg1, (AsyncResult) msg.obj);
                break;
            case 270353:
                onDataStallAlarm(msg.arg1);
                break;
            case 270360:
                boolean tearDown = msg.arg1 != 0;
                onCleanUpConnection(tearDown, msg.arg2, (String) msg.obj);
                break;
            case 270362:
                restartRadio();
                break;
            case 270363:
                onSetInternalDataEnabled(msg.arg1 == 1);
                break;
            case 270364:
                log("EVENT_RESET_DONE");
                onResetDone((AsyncResult) msg.obj);
                break;
            case 270365:
                onCleanUpAllConnections((String) msg.obj);
                break;
            case 270366:
                boolean enabled = msg.arg1 == 1;
                log("CMD_SET_USER_DATA_ENABLE enabled=" + enabled);
                onSetUserDataEnabled(enabled);
                break;
            case 270367:
                boolean met = msg.arg1 == 1;
                log("CMD_SET_DEPENDENCY_MET met=" + met);
                Bundle bundle = msg.getData();
                if (bundle != null && (apnType = (String) bundle.get("apnType")) != null) {
                    onSetDependencyMet(apnType, met);
                    break;
                }
                break;
            case 270368:
                onSetPolicyDataEnabled(msg.arg1 == 1);
                break;
            case 270369:
                onUpdateIcc();
                break;
            case 270370:
                log("DataConnectionTracker.handleMessage: EVENT_DISCONNECT_DC_RETRYING msg=" + msg);
                onDisconnectDcRetrying(msg.arg1, (AsyncResult) msg.obj);
                break;
            case 270371:
                onDataSetupCompleteError((AsyncResult) msg.obj);
                break;
            case 270372:
                sEnableFailFastRefCounter = (msg.arg1 == 1 ? 1 : -1) + sEnableFailFastRefCounter;
                log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA:  sEnableFailFastRefCounter=" + sEnableFailFastRefCounter);
                if (sEnableFailFastRefCounter < 0) {
                    String s = "CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: sEnableFailFastRefCounter:" + sEnableFailFastRefCounter + " < 0";
                    loge(s);
                    sEnableFailFastRefCounter = 0;
                }
                boolean enabled2 = sEnableFailFastRefCounter > 0;
                log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: enabled=" + enabled2 + " sEnableFailFastRefCounter=" + sEnableFailFastRefCounter);
                if (this.mFailFast != enabled2) {
                    this.mFailFast = enabled2;
                    this.mDataStallDetectionEnabled = !enabled2;
                    if (this.mDataStallDetectionEnabled && getOverallState() == DctConstants.State.CONNECTED && (!this.mInVoiceCall || this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed())) {
                        log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: start data stall");
                        stopDataStallAlarm();
                        startDataStallAlarm(false);
                    } else {
                        log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: stop data stall");
                        stopDataStallAlarm();
                    }
                }
                break;
            case 270373:
                Bundle bundle2 = msg.getData();
                if (bundle2 != null) {
                    try {
                        this.mProvisioningUrl = (String) bundle2.get("provisioningUrl");
                    } catch (ClassCastException e) {
                        loge("CMD_ENABLE_MOBILE_PROVISIONING: provisioning url not a string" + e);
                        this.mProvisioningUrl = null;
                    }
                }
                if (TextUtils.isEmpty(this.mProvisioningUrl)) {
                    loge("CMD_ENABLE_MOBILE_PROVISIONING: provisioning url is empty, ignoring");
                    this.mIsProvisioning = false;
                    this.mProvisioningUrl = null;
                } else {
                    loge("CMD_ENABLE_MOBILE_PROVISIONING: provisioningUrl=" + this.mProvisioningUrl);
                    this.mIsProvisioning = true;
                    startProvisioningApnAlarm();
                    onSetInternalDataEnabled(true);
                    enableApnType("default");
                }
                break;
            case 270374:
                log("CMD_IS_PROVISIONING_APN");
                String apnType2 = null;
                try {
                    Bundle bundle3 = msg.getData();
                    if (bundle3 != null) {
                        apnType2 = (String) bundle3.get("apnType");
                    }
                    if (TextUtils.isEmpty(apnType2)) {
                        loge("CMD_IS_PROVISIONING_APN: apnType is empty");
                        isProvApn = false;
                    } else {
                        isProvApn = isProvisioningApn(apnType2);
                    }
                } catch (ClassCastException e2) {
                    loge("CMD_IS_PROVISIONING_APN: NO provisioning url ignoring");
                    isProvApn = false;
                }
                log("CMD_IS_PROVISIONING_APN: ret=" + isProvApn);
                this.mReplyAc.replyToMessage(msg, 270374, isProvApn ? 1 : 0);
                break;
            case 270375:
                log("EVENT_PROVISIONING_APN_ALARM");
                ApnContext apnCtx = this.mApnContexts.get("default");
                if (apnCtx.isProvisioningApn() && apnCtx.isConnectedOrConnecting()) {
                    if (this.mProvisioningApnAlarmTag == msg.arg1) {
                        log("EVENT_PROVISIONING_APN_ALARM: Disconnecting");
                        this.mIsProvisioning = false;
                        this.mProvisioningUrl = null;
                        stopProvisioningApnAlarm();
                        sendCleanUpConnection(true, apnCtx);
                    } else {
                        log("EVENT_PROVISIONING_APN_ALARM: ignore stale tag, mProvisioningApnAlarmTag:" + this.mProvisioningApnAlarmTag + " != arg1:" + msg.arg1);
                    }
                } else {
                    log("EVENT_PROVISIONING_APN_ALARM: Not connected ignore");
                }
                break;
            case 270378:
                if (msg.arg1 == 1) {
                    handleStartNetStatPoll((DctConstants.Activity) msg.obj);
                } else if (msg.arg1 == 0) {
                    handleStopNetStatPoll((DctConstants.Activity) msg.obj);
                }
                break;
            default:
                Rlog.e("DATA", "Unidentified event msg=" + msg);
                break;
        }
    }

    public boolean getAnyDataEnabled() {
        boolean result;
        synchronized (this.mDataEnabledLock) {
            result = this.mInternalDataEnabled && this.mUserDataEnabled && sPolicyDataEnabled && this.mEnabledCount != 0;
        }
        if (!result) {
            log("getAnyDataEnabled " + result);
        }
        return result;
    }

    protected boolean isEmergency() {
        boolean result;
        synchronized (this.mDataEnabledLock) {
            result = this.mPhone.isInEcm() || this.mPhone.isInEmergencyCall();
        }
        log("isEmergency: result=" + result);
        return result;
    }

    protected int apnTypeToId(String type) {
        if (TextUtils.equals(type, "default")) {
            return 0;
        }
        if (TextUtils.equals(type, "mms")) {
            return 1;
        }
        if (TextUtils.equals(type, "supl")) {
            return 2;
        }
        if (TextUtils.equals(type, "dun")) {
            return 3;
        }
        if (TextUtils.equals(type, "hipri")) {
            return 4;
        }
        if (TextUtils.equals(type, "ims")) {
            return 5;
        }
        if (TextUtils.equals(type, "fota")) {
            return 6;
        }
        if (TextUtils.equals(type, "cbs")) {
            return 7;
        }
        if (TextUtils.equals(type, "ia")) {
            return 8;
        }
        return -1;
    }

    protected String apnIdToType(int id) {
        switch (id) {
            case 0:
                return "default";
            case 1:
                return "mms";
            case 2:
                return "supl";
            case 3:
                return "dun";
            case 4:
                return "hipri";
            case 5:
                return "ims";
            case 6:
                return "fota";
            case 7:
                return "cbs";
            case 8:
                return "ia";
            default:
                log("Unknown id (" + id + ") in apnIdToType");
                return "default";
        }
    }

    public LinkProperties getLinkProperties(String apnType) {
        int id = apnTypeToId(apnType);
        if (!isApnIdEnabled(id)) {
            return new LinkProperties();
        }
        DcAsyncChannel dcac = this.mDataConnectionAcHashMap.get(0);
        return dcac.getLinkPropertiesSync();
    }

    public LinkCapabilities getLinkCapabilities(String apnType) {
        int id = apnTypeToId(apnType);
        if (!isApnIdEnabled(id)) {
            return new LinkCapabilities();
        }
        DcAsyncChannel dcac = this.mDataConnectionAcHashMap.get(0);
        return dcac.getLinkCapabilitiesSync();
    }

    protected void notifyDataConnection(String reason) {
        for (int id = 0; id < 9; id++) {
            if (this.mDataEnabled[id]) {
                this.mPhone.notifyDataConnection(reason, apnIdToType(id));
            }
        }
        notifyOffApnsOfAvailability(reason);
    }

    /* JADX INFO: renamed from: com.android.internal.telephony.dataconnection.DcTrackerBase$5, reason: invalid class name */
    static /* synthetic */ class AnonymousClass5 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DctConstants$State = new int[DctConstants.State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.IDLE.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.RETRYING.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.CONNECTING.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.SCANNING.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.CONNECTED.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.DISCONNECTING.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
        }
    }

    private void notifyApnIdUpToCurrent(String reason, int apnId) {
        switch (AnonymousClass5.$SwitchMap$com$android$internal$telephony$DctConstants$State[this.mState.ordinal()]) {
            case 2:
            case 3:
            case 4:
                this.mPhone.notifyDataConnection(reason, apnIdToType(apnId), PhoneConstants.DataState.CONNECTING);
                break;
            case 5:
            case 6:
                this.mPhone.notifyDataConnection(reason, apnIdToType(apnId), PhoneConstants.DataState.CONNECTING);
                this.mPhone.notifyDataConnection(reason, apnIdToType(apnId), PhoneConstants.DataState.CONNECTED);
                break;
        }
    }

    private void notifyApnIdDisconnected(String reason, int apnId) {
        this.mPhone.notifyDataConnection(reason, apnIdToType(apnId), PhoneConstants.DataState.DISCONNECTED);
    }

    protected void notifyOffApnsOfAvailability(String reason) {
        log("notifyOffApnsOfAvailability - reason= " + reason);
        for (int id = 0; id < 9; id++) {
            if (!isApnIdEnabled(id)) {
                notifyApnIdDisconnected(reason, id);
            }
        }
    }

    public boolean isApnTypeEnabled(String apnType) {
        if (apnType == null) {
            return false;
        }
        return isApnIdEnabled(apnTypeToId(apnType));
    }

    protected synchronized boolean isApnIdEnabled(int id) {
        return id != -1 ? this.mDataEnabled[id] : false;
    }

    public synchronized int enableApnType(String type) {
        int i = 1;
        synchronized (this) {
            log("DcTrackerBase: enableApnType");
            int id = apnTypeToId(type);
            if (id == -1) {
                i = 3;
            } else {
                log("enableApnType(" + type + "), isApnTypeActive = " + isApnTypeActive(type) + ", isApnIdEnabled =" + isApnIdEnabled(id) + " and state = " + this.mState);
                if (!isApnTypeAvailable(type)) {
                    log("enableApnType: not available, type=" + type);
                    i = 2;
                } else if (isApnIdEnabled(id)) {
                    log("enableApnType: already active, type=" + type);
                    i = 0;
                } else {
                    setEnabled(id, true);
                }
            }
        }
        return i;
    }

    public synchronized int disableApnType(String type) {
        int i = 3;
        synchronized (this) {
            log("disableApnType(" + type + ")");
            int id = apnTypeToId(type);
            if (id != -1 && isApnIdEnabled(id)) {
                setEnabled(id, false);
                i = (isApnTypeActive("default") && this.mDataEnabled[0]) ? 0 : 1;
            }
        }
        return i;
    }

    protected void setEnabled(int id, boolean enable) {
        log("setEnabled(" + id + ", " + enable + ") with old state = " + this.mDataEnabled[id] + " and enabledCount = " + this.mEnabledCount);
        Message msg = obtainMessage(270349);
        msg.arg1 = id;
        msg.arg2 = enable ? 1 : 0;
        sendMessage(msg);
    }

    protected void onEnableApn(int apnId, int enabled) {
        log("EVENT_APN_ENABLE_REQUEST apnId=" + apnId + ", apnType=" + apnIdToType(apnId) + ", enabled=" + enabled + ", dataEnabled = " + this.mDataEnabled[apnId] + ", enabledCount = " + this.mEnabledCount + ", isApnTypeActive = " + isApnTypeActive(apnIdToType(apnId)));
        if (enabled == 1) {
            synchronized (this) {
                if (!this.mDataEnabled[apnId]) {
                    this.mDataEnabled[apnId] = true;
                    this.mEnabledCount++;
                }
            }
            String type = apnIdToType(apnId);
            if (!isApnTypeActive(type)) {
                this.mRequestedApnType = type;
                onEnableNewApn();
                return;
            } else {
                notifyApnIdUpToCurrent(Phone.REASON_APN_SWITCHED, apnId);
                return;
            }
        }
        boolean didDisable = false;
        synchronized (this) {
            if (this.mDataEnabled[apnId]) {
                this.mDataEnabled[apnId] = false;
                this.mEnabledCount--;
                didDisable = true;
            }
        }
        if (didDisable) {
            if (this.mEnabledCount == 0 || apnId == 3) {
                this.mRequestedApnType = "default";
                onCleanUpConnection(true, apnId, Phone.REASON_DATA_DISABLED);
            }
            notifyApnIdDisconnected(Phone.REASON_DATA_DISABLED, apnId);
            if (this.mDataEnabled[0] && !isApnTypeActive("default")) {
                this.mRequestedApnType = "default";
                onEnableNewApn();
            }
        }
    }

    protected void onEnableNewApn() {
    }

    protected void onResetDone(AsyncResult ar) {
        log("EVENT_RESET_DONE");
        String reason = null;
        if (ar.userObj instanceof String) {
            reason = (String) ar.userObj;
        }
        gotoIdleAndNotifyDataConnection(reason);
    }

    public boolean setInternalDataEnabled(boolean enable) {
        log("setInternalDataEnabled(" + enable + ")");
        Message msg = obtainMessage(270363);
        msg.arg1 = enable ? 1 : 0;
        sendMessage(msg);
        return true;
    }

    protected void onSetInternalDataEnabled(boolean enabled) {
        synchronized (this.mDataEnabledLock) {
            this.mInternalDataEnabled = enabled;
            if (enabled) {
                log("onSetInternalDataEnabled: changed to enabled, try to setup data call");
                onTrySetupData(Phone.REASON_DATA_ENABLED);
            } else {
                log("onSetInternalDataEnabled: changed to disabled, cleanUpAllConnections");
                cleanUpAllConnections(null);
            }
        }
    }

    public void cleanUpAllConnections(String cause) {
        Message msg = obtainMessage(270365);
        msg.obj = cause;
        sendMessage(msg);
    }

    protected void onSetUserDataEnabled(boolean enabled) {
        synchronized (this.mDataEnabledLock) {
            if (this.mUserDataEnabled != enabled) {
                this.mUserDataEnabled = enabled;
                Settings.Global.putInt(this.mPhone.getContext().getContentResolver(), "mobile_data", enabled ? 1 : 0);
                if (!getDataOnRoamingEnabled() && this.mPhone.getServiceState().getRoaming()) {
                    if (enabled) {
                        notifyOffApnsOfAvailability(Phone.REASON_ROAMING_ON);
                    } else {
                        notifyOffApnsOfAvailability(Phone.REASON_DATA_DISABLED);
                    }
                }
                if (enabled) {
                    onTrySetupData(Phone.REASON_DATA_ENABLED);
                } else {
                    onCleanUpAllConnections(Phone.REASON_DATA_DISABLED);
                }
            }
        }
    }

    protected void onSetDependencyMet(String apnType, boolean met) {
    }

    protected void onSetPolicyDataEnabled(boolean enabled) {
        synchronized (this.mDataEnabledLock) {
            if (sPolicyDataEnabled != enabled) {
                sPolicyDataEnabled = enabled;
                if (enabled) {
                    onTrySetupData(Phone.REASON_DATA_ENABLED);
                } else {
                    onCleanUpAllConnections(Phone.REASON_DATA_DISABLED);
                }
            }
        }
    }

    protected List<ApnContext> getPrioritySortedApnContextList() {
        ArrayList<ApnContext> sortedList = new ArrayList<>();
        Iterator<String> it = mApnPriorities.keySet().iterator();
        while (it.hasNext()) {
            ApnContext apnContext = this.mApnContexts.get(it.next());
            if (apnContext != null) {
                sortedList.add(apnContext);
            }
        }
        return sortedList;
    }

    protected String getReryConfig(boolean forDefault) {
        int nt = this.mPhone.getServiceState().getNetworkType();
        if (nt == 4 || nt == 7 || nt == 5 || nt == 6 || nt == 12 || nt == 14) {
            return SystemProperties.get("ro.cdma.data_retry_config");
        }
        if (forDefault) {
            return SystemProperties.get("ro.gsm.data_retry_config");
        }
        return SystemProperties.get("ro.gsm.2nd_data_retry_config");
    }

    protected void resetPollStats() {
        this.mTxPkts = -1L;
        this.mRxPkts = -1L;
        this.mNetStatPollPeriod = 1000;
    }

    protected void startNetStatPoll() {
        if (getOverallState() == DctConstants.State.CONNECTED && !this.mNetStatPollEnabled) {
            log("startNetStatPoll");
            resetPollStats();
            this.mNetStatPollEnabled = true;
            this.mPollNetStat.run();
        }
    }

    public void sendStartNetStatPoll(DctConstants.Activity activity) {
        Message msg = obtainMessage(270378, 1, -1, activity);
        sendMessage(msg);
    }

    protected void handleStartNetStatPoll(DctConstants.Activity activity) {
        startNetStatPoll();
        startDataStallAlarm(false);
        setActivity(activity);
    }

    protected void stopNetStatPoll() {
        this.mNetStatPollEnabled = false;
        removeCallbacks(this.mPollNetStat);
        log("stopNetStatPoll");
    }

    public void sendStopNetStatPoll(DctConstants.Activity activity) {
        Message msg = obtainMessage(270378, 0, -1, activity);
        sendMessage(msg);
    }

    protected void handleStopNetStatPoll(DctConstants.Activity activity) {
        stopNetStatPoll();
        stopDataStallAlarm();
        setActivity(activity);
    }

    public void updateDataActivity() {
        DctConstants.Activity newActivity;
        TxRxSum preTxRxSum = new TxRxSum(this.mTxPkts, this.mRxPkts);
        TxRxSum curTxRxSum = new TxRxSum();
        curTxRxSum.updateTxRxSum();
        this.mTxPkts = curTxRxSum.txPkts;
        this.mRxPkts = curTxRxSum.rxPkts;
        if (this.mNetStatPollEnabled) {
            if (preTxRxSum.txPkts > 0 || preTxRxSum.rxPkts > 0) {
                long sent = this.mTxPkts - preTxRxSum.txPkts;
                long received = this.mRxPkts - preTxRxSum.rxPkts;
                if (sent > 0 && received > 0) {
                    newActivity = DctConstants.Activity.DATAINANDOUT;
                } else if (sent > 0 && received == 0) {
                    newActivity = DctConstants.Activity.DATAOUT;
                } else if (sent == 0 && received > 0) {
                    newActivity = DctConstants.Activity.DATAIN;
                } else {
                    newActivity = this.mActivity == DctConstants.Activity.DORMANT ? this.mActivity : DctConstants.Activity.NONE;
                }
                if (this.mActivity != newActivity && this.mIsScreenOn) {
                    this.mActivity = newActivity;
                    this.mPhone.notifyDataActivity();
                }
            }
        }
    }

    protected static class RecoveryAction {
        public static final int CLEANUP = 1;
        public static final int GET_DATA_CALL_LIST = 0;
        public static final int RADIO_RESTART = 3;
        public static final int RADIO_RESTART_WITH_PROP = 4;
        public static final int REREGISTER = 2;

        protected RecoveryAction() {
        }

        /* JADX INFO: Access modifiers changed from: private */
        public static boolean isAggressiveRecovery(int value) {
            return value == 1 || value == 2 || value == 3 || value == 4;
        }
    }

    public int getRecoveryAction() {
        int action = Settings.System.getInt(this.mPhone.getContext().getContentResolver(), "radio.data.stall.recovery.action", 0);
        log("getRecoveryAction: " + action);
        return action;
    }

    public void putRecoveryAction(int action) {
        Settings.System.putInt(this.mPhone.getContext().getContentResolver(), "radio.data.stall.recovery.action", action);
        log("putRecoveryAction: " + action);
    }

    protected boolean isConnected() {
        return false;
    }

    protected void doRecovery() {
        if (getOverallState() == DctConstants.State.CONNECTED) {
            int recoveryAction = getRecoveryAction();
            switch (recoveryAction) {
                case 0:
                    EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_GET_DATA_CALL_LIST, this.mSentSinceLastRecv);
                    log("doRecovery() get data call list");
                    this.mPhone.mCi.getDataCallList(obtainMessage(270340));
                    putRecoveryAction(1);
                    break;
                case 1:
                    EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_CLEANUP, this.mSentSinceLastRecv);
                    log("doRecovery() cleanup all connections");
                    cleanUpAllConnections(Phone.REASON_PDP_RESET);
                    putRecoveryAction(2);
                    break;
                case 2:
                    EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_REREGISTER, this.mSentSinceLastRecv);
                    log("doRecovery() re-register");
                    this.mPhone.getServiceStateTracker().reRegisterNetwork(null);
                    putRecoveryAction(3);
                    break;
                case 3:
                    EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_RADIO_RESTART, this.mSentSinceLastRecv);
                    log("restarting radio");
                    putRecoveryAction(4);
                    restartRadio();
                    break;
                case 4:
                    EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_RADIO_RESTART_WITH_PROP, -1);
                    log("restarting radio with gsm.radioreset to true");
                    SystemProperties.set(this.RADIO_RESET_PROPERTY, "true");
                    try {
                        Thread.sleep(1000L);
                        break;
                    } catch (InterruptedException e) {
                    }
                    restartRadio();
                    putRecoveryAction(0);
                    break;
                default:
                    throw new RuntimeException("doRecovery: Invalid recoveryAction=" + recoveryAction);
            }
            this.mSentSinceLastRecv = 0L;
        }
    }

    private void updateDataStallInfo() {
        TxRxSum preTxRxSum = new TxRxSum(this.mDataStallTxRxSum);
        this.mDataStallTxRxSum.updateTxRxSum();
        log("updateDataStallInfo: mDataStallTxRxSum=" + this.mDataStallTxRxSum + " preTxRxSum=" + preTxRxSum);
        long sent = this.mDataStallTxRxSum.txPkts - preTxRxSum.txPkts;
        long received = this.mDataStallTxRxSum.rxPkts - preTxRxSum.rxPkts;
        if (sent > 0 && received > 0) {
            log("updateDataStallInfo: IN/OUT");
            this.mSentSinceLastRecv = 0L;
            putRecoveryAction(0);
        } else {
            if (sent > 0 && received == 0) {
                if (isPhoneStateIdle()) {
                    this.mSentSinceLastRecv += sent;
                } else {
                    this.mSentSinceLastRecv = 0L;
                }
                log("updateDataStallInfo: OUT sent=" + sent + " mSentSinceLastRecv=" + this.mSentSinceLastRecv);
                return;
            }
            if (sent == 0 && received > 0) {
                log("updateDataStallInfo: IN");
                this.mSentSinceLastRecv = 0L;
                putRecoveryAction(0);
                return;
            }
            log("updateDataStallInfo: NONE");
        }
    }

    protected void onDataStallAlarm(int tag) {
        if (this.mDataStallAlarmTag != tag) {
            log("onDataStallAlarm: ignore, tag=" + tag + " expecting " + this.mDataStallAlarmTag);
            return;
        }
        updateDataStallInfo();
        int hangWatchdogTrigger = Settings.Global.getInt(this.mResolver, "pdp_watchdog_trigger_packet_count", 10);
        boolean suspectedStall = false;
        if (this.mSentSinceLastRecv >= hangWatchdogTrigger && 1 == 0) {
            log("onDataStallAlarm: tag=" + tag + " do recovery action=" + getRecoveryAction());
            suspectedStall = true;
            sendMessage(obtainMessage(270354));
        } else {
            log("onDataStallAlarm: tag=" + tag + " Sent " + String.valueOf(this.mSentSinceLastRecv) + " pkts since last received, < watchdogTrigger=" + hangWatchdogTrigger);
        }
        startDataStallAlarm(suspectedStall);
    }

    protected void startDataStallAlarm(boolean suspectedStall) {
        int delayInMs;
        int nextAction = getRecoveryAction();
        if (this.mDataStallDetectionEnabled && getOverallState() == DctConstants.State.CONNECTED) {
            if (this.mIsScreenOn || suspectedStall || RecoveryAction.isAggressiveRecovery(nextAction)) {
                delayInMs = Settings.Global.getInt(this.mResolver, "data_stall_alarm_aggressive_delay_in_ms", ServiceStateTracker.DEFAULT_GPRS_CHECK_PERIOD_MILLIS);
            } else {
                delayInMs = Settings.Global.getInt(this.mResolver, "data_stall_alarm_non_aggressive_delay_in_ms", DATA_STALL_ALARM_NON_AGGRESSIVE_DELAY_IN_MS_DEFAULT);
            }
            this.mDataStallAlarmTag++;
            log("startDataStallAlarm: tag=" + this.mDataStallAlarmTag + " delay=" + (delayInMs / 1000) + "s");
            Intent intent = new Intent(INTENT_DATA_STALL_ALARM);
            intent.putExtra(DATA_STALL_ALARM_TAG_EXTRA, this.mDataStallAlarmTag);
            this.mDataStallAlarmIntent = PendingIntent.getBroadcast(this.mPhone.getContext(), 0, intent, 134217728);
            this.mAlarmManager.set(2, SystemClock.elapsedRealtime() + ((long) delayInMs), this.mDataStallAlarmIntent);
            return;
        }
        log("startDataStallAlarm: NOT started, no connection tag=" + this.mDataStallAlarmTag);
    }

    protected void stopDataStallAlarm() {
        log("stopDataStallAlarm: current tag=" + this.mDataStallAlarmTag + " mDataStallAlarmIntent=" + this.mDataStallAlarmIntent);
        this.mDataStallAlarmTag++;
        if (this.mDataStallAlarmIntent != null) {
            this.mAlarmManager.cancel(this.mDataStallAlarmIntent);
            this.mDataStallAlarmIntent = null;
        }
    }

    protected void restartDataStallAlarm() {
        if (isConnected()) {
            int nextAction = getRecoveryAction();
            if (RecoveryAction.isAggressiveRecovery(nextAction)) {
                log("restartDataStallAlarm: action is pending. not resetting the alarm.");
                return;
            }
            log("restartDataStallAlarm: stop then start.");
            stopDataStallAlarm();
            startDataStallAlarm(false);
        }
    }

    protected boolean isPhoneStateIdle() {
        return this.mPhone.getState() == PhoneConstants.State.IDLE;
    }

    protected void setInitialAttachApn() {
        MSimTelephonyManager mtmgr = (MSimTelephonyManager) this.mPhone.getContext().getSystemService("phone_msim");
        if (this.mPhone.getSubscription() != mtmgr.getDefaultDataSubscription()) {
            log("setInitialAttachApn is not allowed on non-dds subscription");
            return;
        }
        DataProfile iaApnSetting = null;
        DataProfile defaultApnSetting = null;
        DataProfile firstApnSetting = null;
        log("setInitialApn: E mPreferredApn=" + this.mPreferredDp);
        if (this.mAllDps != null && !this.mAllDps.isEmpty()) {
            firstApnSetting = this.mAllDps.get(0);
            log("setInitialApn: firstApnSetting=" + firstApnSetting);
            for (DataProfile apn : this.mAllDps) {
                if (ArrayUtils.contains(apn.types, "ia")) {
                    log("setInitialApn: iaApnSetting=" + apn);
                    iaApnSetting = apn;
                    break;
                } else if (defaultApnSetting == null && apn.canHandleType("default")) {
                    log("setInitialApn: defaultApnSetting=" + apn);
                    defaultApnSetting = apn;
                }
            }
        }
        DataProfile initialAttachApnSetting = null;
        if (iaApnSetting != null) {
            log("setInitialAttachApn: using iaApnSetting");
            initialAttachApnSetting = iaApnSetting;
        } else if (this.mPreferredDp != null) {
            log("setInitialAttachApn: using mPreferredApn");
            initialAttachApnSetting = this.mPreferredDp;
        } else if (defaultApnSetting != null) {
            log("setInitialAttachApn: using defaultApnSetting");
            initialAttachApnSetting = defaultApnSetting;
        } else if (firstApnSetting != null) {
            log("setInitialAttachApn: using firstApnSetting");
            initialAttachApnSetting = firstApnSetting;
        }
        if (initialAttachApnSetting == null) {
            log("setInitialAttachApn: X There in no available apn");
        } else {
            log("setInitialAttachApn: X selected Apn=" + initialAttachApnSetting);
            this.mPhone.mCi.setInitialAttachApn(initialAttachApnSetting.apn, initialAttachApnSetting.protocol, initialAttachApnSetting.authType, initialAttachApnSetting.user, initialAttachApnSetting.password, null);
        }
    }

    protected void onActionIntentProvisioningApnAlarm(Intent intent) {
        log("onActionIntentProvisioningApnAlarm: action=" + intent.getAction());
        Message msg = obtainMessage(270375, intent.getAction());
        msg.arg1 = intent.getIntExtra(PROVISIONING_APN_ALARM_TAG_EXTRA, 0);
        sendMessage(msg);
    }

    protected void startProvisioningApnAlarm() {
        int delayInMs = Settings.Global.getInt(this.mResolver, "provisioning_apn_alarm_delay_in_ms", PROVISIONING_APN_ALARM_DELAY_IN_MS_DEFAULT);
        if (Build.IS_DEBUGGABLE) {
            String delayInMsStrg = Integer.toString(delayInMs);
            try {
                delayInMs = Integer.parseInt(System.getProperty(DEBUG_PROV_APN_ALARM, delayInMsStrg));
            } catch (NumberFormatException e) {
                loge("startProvisioningApnAlarm: e=" + e);
            }
        }
        this.mProvisioningApnAlarmTag++;
        log("startProvisioningApnAlarm: tag=" + this.mProvisioningApnAlarmTag + " delay=" + (delayInMs / 1000) + "s");
        Intent intent = new Intent(INTENT_PROVISIONING_APN_ALARM);
        intent.putExtra(PROVISIONING_APN_ALARM_TAG_EXTRA, this.mProvisioningApnAlarmTag);
        this.mProvisioningApnAlarmIntent = PendingIntent.getBroadcast(this.mPhone.getContext(), 0, intent, 134217728);
        this.mAlarmManager.set(2, SystemClock.elapsedRealtime() + ((long) delayInMs), this.mProvisioningApnAlarmIntent);
    }

    protected void stopProvisioningApnAlarm() {
        log("stopProvisioningApnAlarm: current tag=" + this.mProvisioningApnAlarmTag + " mProvsioningApnAlarmIntent=" + this.mProvisioningApnAlarmIntent);
        this.mProvisioningApnAlarmTag++;
        if (this.mProvisioningApnAlarmIntent != null) {
            this.mAlarmManager.cancel(this.mProvisioningApnAlarmIntent);
            this.mProvisioningApnAlarmIntent = null;
        }
    }

    void sendCleanUpConnection(boolean tearDown, ApnContext apnContext) {
        log("sendCleanUpConnection: tearDown=" + tearDown + " apnContext=" + apnContext);
        Message msg = obtainMessage(270360);
        msg.arg1 = tearDown ? 1 : 0;
        msg.arg2 = 0;
        msg.obj = apnContext;
        sendMessage(msg);
    }

    void sendRestartRadio() {
        log("sendRestartRadio:");
        Message msg = obtainMessage(270362);
        sendMessage(msg);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("DataConnectionTrackerBase:");
        pw.println(" RADIO_TESTS=false");
        pw.println(" mInternalDataEnabled=" + this.mInternalDataEnabled);
        pw.println(" mUserDataEnabled=" + this.mUserDataEnabled);
        pw.println(" sPolicyDataEnabed=" + sPolicyDataEnabled);
        pw.println(" mDataEnabled:");
        for (int i = 0; i < this.mDataEnabled.length; i++) {
            pw.printf("  mDataEnabled[%d]=%b\n", Integer.valueOf(i), Boolean.valueOf(this.mDataEnabled[i]));
        }
        pw.flush();
        pw.println(" mEnabledCount=" + this.mEnabledCount);
        pw.println(" mRequestedApnType=" + this.mRequestedApnType);
        pw.println(" mPhone=" + this.mPhone.getPhoneName());
        pw.println(" mActivity=" + this.mActivity);
        pw.println(" mState=" + this.mState);
        pw.println(" mTxPkts=" + this.mTxPkts);
        pw.println(" mRxPkts=" + this.mRxPkts);
        pw.println(" mNetStatPollPeriod=" + this.mNetStatPollPeriod);
        pw.println(" mNetStatPollEnabled=" + this.mNetStatPollEnabled);
        pw.println(" mDataStallTxRxSum=" + this.mDataStallTxRxSum);
        pw.println(" mDataStallAlarmTag=" + this.mDataStallAlarmTag);
        pw.println(" mDataStallDetectionEanbled=" + this.mDataStallDetectionEnabled);
        pw.println(" mSentSinceLastRecv=" + this.mSentSinceLastRecv);
        pw.println(" mNoRecvPollCount=" + this.mNoRecvPollCount);
        pw.println(" mResolver=" + this.mResolver);
        pw.println(" mIsWifiConnected=" + this.mIsWifiConnected);
        pw.println(" mReconnectIntent=" + this.mReconnectIntent);
        pw.println(" mCidActive=" + this.mCidActive);
        pw.println(" mAutoAttachOnCreation=" + this.mAutoAttachOnCreation);
        pw.println(" mIsScreenOn=" + this.mIsScreenOn);
        pw.println(" mUniqueIdGenerator=" + this.mUniqueIdGenerator);
        pw.flush();
        pw.println(" ***************************************");
        DcController dcc = this.mDcc;
        if (dcc != null) {
            dcc.dump(fd, pw, args);
        } else {
            pw.println(" mDcc=null");
        }
        pw.println(" ***************************************");
        HashMap<Integer, DataConnection> dcs = this.mDataConnections;
        if (dcs != null) {
            Set<Map.Entry<Integer, DataConnection>> mDcSet = this.mDataConnections.entrySet();
            pw.println(" mDataConnections: count=" + mDcSet.size());
            for (Map.Entry<Integer, DataConnection> entry : mDcSet) {
                pw.printf(" *** mDataConnection[%d] \n", entry.getKey());
                entry.getValue().dump(fd, pw, args);
            }
        } else {
            pw.println("mDataConnections=null");
        }
        pw.println(" ***************************************");
        pw.flush();
        HashMap<String, Integer> apnToDcId = this.mApnToDataConnectionId;
        if (apnToDcId != null) {
            Set<Map.Entry<String, Integer>> apnToDcIdSet = apnToDcId.entrySet();
            pw.println(" mApnToDataConnectonId size=" + apnToDcIdSet.size());
            for (Map.Entry<String, Integer> entry2 : apnToDcIdSet) {
                pw.printf(" mApnToDataConnectonId[%s]=%d\n", entry2.getKey(), entry2.getValue());
            }
        } else {
            pw.println("mApnToDataConnectionId=null");
        }
        pw.println(" ***************************************");
        pw.flush();
        ConcurrentHashMap<String, ApnContext> apnCtxs = this.mApnContexts;
        if (apnCtxs != null) {
            Set<Map.Entry<String, ApnContext>> apnCtxsSet = apnCtxs.entrySet();
            pw.println(" mApnContexts size=" + apnCtxsSet.size());
            Iterator<Map.Entry<String, ApnContext>> it = apnCtxsSet.iterator();
            while (it.hasNext()) {
                it.next().getValue().dump(fd, pw, args);
            }
            pw.println(" ***************************************");
        } else {
            pw.println(" mApnContexts=null");
        }
        pw.flush();
        pw.println(" mActiveDp=" + this.mActiveDp);
        ArrayList<DataProfile> apnSettings = this.mAllDps;
        if (apnSettings != null) {
            pw.println(" mAllDps size=" + apnSettings.size());
            for (int i2 = 0; i2 < apnSettings.size(); i2++) {
                pw.printf(" mAllDps[%d]: %s\n", Integer.valueOf(i2), apnSettings.get(i2));
            }
            pw.flush();
        } else {
            pw.println(" mAllDps=null");
        }
        pw.println(" mPreferredDp=" + this.mPreferredDp);
        pw.println(" mIsPsRestricted=" + this.mIsPsRestricted);
        pw.println(" mIsDisposed=" + this.mIsDisposed);
        pw.println(" mIntentReceiver=" + this.mIntentReceiver);
        pw.println(" mDataRoamingSettingObserver=" + this.mDataRoamingSettingObserver);
        pw.flush();
    }
}
