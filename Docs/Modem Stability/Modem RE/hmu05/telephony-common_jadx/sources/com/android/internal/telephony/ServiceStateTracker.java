package com.android.internal.telephony;

import android.R;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemClock;
import android.provider.Telephony;
import android.telephony.CellInfo;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.text.TextUtils;
import android.util.Pair;
import android.util.TimeUtils;
import com.android.internal.telephony.dataconnection.DcTrackerBase;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public abstract class ServiceStateTracker extends Handler {
    protected static final boolean DBG = true;
    public static final int DEFAULT_GPRS_CHECK_PERIOD_MILLIS = 60000;
    protected static final int EVENT_CDMA_PRL_VERSION_CHANGED = 40;
    protected static final int EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED = 39;
    protected static final int EVENT_CHECK_REPORT_GPRS = 22;
    protected static final int EVENT_ERI_FILE_LOADED = 36;
    protected static final int EVENT_GET_CELL_INFO_LIST = 43;
    protected static final int EVENT_GET_LOC_DONE = 15;
    protected static final int EVENT_GET_LOC_DONE_CDMA = 31;
    protected static final int EVENT_GET_PREFERRED_NETWORK_TYPE = 19;
    protected static final int EVENT_GET_SIGNAL_STRENGTH = 3;
    protected static final int EVENT_GET_SIGNAL_STRENGTH_CDMA = 29;
    public static final int EVENT_ICC_CHANGED = 42;
    protected static final int EVENT_LOCATION_UPDATES_ENABLED = 18;
    protected static final int EVENT_NETWORK_STATE_CHANGED = 2;
    protected static final int EVENT_NETWORK_STATE_CHANGED_CDMA = 30;
    protected static final int EVENT_NITZ_TIME = 11;
    protected static final int EVENT_NV_LOADED = 33;
    protected static final int EVENT_NV_READY = 35;
    protected static final int EVENT_OTA_PROVISION_STATUS_CHANGE = 37;
    protected static final int EVENT_POLL_SIGNAL_STRENGTH = 10;
    protected static final int EVENT_POLL_SIGNAL_STRENGTH_CDMA = 28;
    protected static final int EVENT_POLL_STATE_CDMA_SUBSCRIPTION = 34;
    protected static final int EVENT_POLL_STATE_GPRS = 5;
    protected static final int EVENT_POLL_STATE_NETWORK_SELECTION_MODE = 14;
    protected static final int EVENT_POLL_STATE_OPERATOR = 6;
    protected static final int EVENT_POLL_STATE_OPERATOR_CDMA = 25;
    protected static final int EVENT_POLL_STATE_REGISTRATION = 4;
    protected static final int EVENT_POLL_STATE_REGISTRATION_CDMA = 24;
    protected static final int EVENT_RADIO_AVAILABLE = 13;
    protected static final int EVENT_RADIO_ON = 41;
    protected static final int EVENT_RADIO_STATE_CHANGED = 1;
    protected static final int EVENT_RESET_PREFERRED_NETWORK_TYPE = 21;
    protected static final int EVENT_RESTRICTED_STATE_CHANGED = 23;
    protected static final int EVENT_RUIM_READY = 26;
    protected static final int EVENT_RUIM_RECORDS_LOADED = 27;
    protected static final int EVENT_SET_PREFERRED_NETWORK_TYPE = 20;
    protected static final int EVENT_SET_RADIO_POWER_OFF = 38;
    protected static final int EVENT_SIGNAL_STRENGTH_UPDATE = 12;
    protected static final int EVENT_SIM_READY = 17;
    protected static final int EVENT_SIM_RECORDS_LOADED = 16;
    protected static final int EVENT_UNSOL_CELL_INFO_LIST = 44;
    protected static final String[] GMT_COUNTRY_CODES = {"bf", "ci", "eh", "fo", "gb", "gh", "gm", "gn", "gw", "ie", "lr", "is", "ma", "ml", "mr", "pt", "sl", "sn", Telephony.BaseMmsColumns.STATUS, "tg"};
    private static final long LAST_CELL_INFO_LIST_MAX_AGE_MS = 2000;
    public static final int OTASP_NEEDED = 2;
    public static final int OTASP_NOT_NEEDED = 3;
    public static final int OTASP_UNINITIALIZED = 0;
    public static final int OTASP_UNKNOWN = 1;
    protected static final int POLL_PERIOD_MILLIS = 20000;
    protected static final String PROP_FORCE_ROAMING = "telephony.test.forceRoaming";
    protected static final String REGISTRATION_DENIED_AUTH = "Authentication Failure";
    protected static final String REGISTRATION_DENIED_GEN = "General";
    protected static final String TIMEZONE_PROPERTY = "persist.sys.timezone";
    protected static final boolean VDBG = false;
    protected final CellInfo mCellInfo;
    protected CommandsInterface mCi;
    protected boolean mDesiredPowerState;
    protected long mLastCellInfoListTime;
    protected PhoneBase mPhoneBase;
    protected int[] mPollingContext;
    protected UiccController mUiccController;
    protected boolean mVoiceCapable;
    private boolean mWantContinuousLocationUpdates;
    private boolean mWantSingleLocationUpdate;
    protected UiccCardApplication mUiccApplcation = null;
    protected IccRecords mIccRecords = null;
    public ServiceState mSS = new ServiceState();
    protected ServiceState mNewSS = new ServiceState();
    protected List<CellInfo> mLastCellInfoList = null;
    protected SignalStrength mSignalStrength = new SignalStrength();
    public RestrictedState mRestrictedState = new RestrictedState();
    protected boolean mDontPollSignalStrength = false;
    protected RegistrantList mRoamingOnRegistrants = new RegistrantList();
    protected RegistrantList mRoamingOffRegistrants = new RegistrantList();
    protected RegistrantList mAttachedRegistrants = new RegistrantList();
    protected RegistrantList mDetachedRegistrants = new RegistrantList();
    protected RegistrantList mIwlanRegistrants = new RegistrantList();
    protected RegistrantList mDataRegStateOrRatChangedRegistrants = new RegistrantList();
    protected RegistrantList mNetworkAttachedRegistrants = new RegistrantList();
    protected RegistrantList mPsRestrictEnabledRegistrants = new RegistrantList();
    protected RegistrantList mPsRestrictDisabledRegistrants = new RegistrantList();
    protected boolean mPendingRadioPowerOffAfterDataOff = false;
    protected int mPendingRadioPowerOffAfterDataOffTag = 0;
    private SignalStrength mLastSignalStrength = null;
    private ArrayList<CellInfoResult> mCellInfoWaitList = new ArrayList<>();

    public abstract int getCurrentDataConnectionState();

    protected abstract Phone getPhone();

    protected abstract void handlePollStateResult(int i, AsyncResult asyncResult);

    protected abstract void hangupAndPowerOff();

    public abstract boolean isConcurrentVoiceAndDataAllowed();

    protected abstract void log(String str);

    protected abstract void loge(String str);

    protected abstract void onUpdateIccAvailability();

    protected abstract void setPowerStateToDesired();

    protected abstract void updateSpnDisplay();

    private class CellInfoResult {
        List<CellInfo> list;
        Object lockObj;

        private CellInfoResult() {
            this.lockObj = new Object();
        }
    }

    protected ServiceStateTracker(PhoneBase phoneBase, CommandsInterface ci, CellInfo cellInfo) {
        this.mUiccController = null;
        this.mPhoneBase = phoneBase;
        this.mCellInfo = cellInfo;
        this.mCi = ci;
        this.mVoiceCapable = this.mPhoneBase.getContext().getResources().getBoolean(R.bool.config_audio_ringer_mode_affects_alarm_stream);
        this.mUiccController = UiccController.getInstance();
        this.mUiccController.registerForIccChanged(this, 42, null);
        this.mCi.setOnSignalStrengthUpdate(this, 12, null);
        this.mCi.registerForCellInfoList(this, 44, null);
        this.mPhoneBase.setSystemProperty("gsm.network.type", ServiceState.rilRadioTechnologyToString(0));
    }

    public void dispose() {
        this.mCi.unSetOnSignalStrengthUpdate(this);
        this.mUiccController.unregisterForIccChanged(this);
        this.mCi.unregisterForCellInfoList(this);
        for (CellInfoResult result : this.mCellInfoWaitList) {
            synchronized (result.lockObj) {
                result.list = null;
                result.lockObj.notify();
            }
        }
        this.mCellInfoWaitList.clear();
    }

    public boolean getDesiredPowerState() {
        return this.mDesiredPowerState;
    }

    protected boolean notifySignalStrength() {
        boolean notified = false;
        synchronized (this.mCellInfo) {
            if (!this.mSignalStrength.equals(this.mLastSignalStrength)) {
                try {
                    this.mPhoneBase.notifySignalStrength();
                    notified = true;
                } catch (NullPointerException ex) {
                    loge("updateSignalStrength() Phone already destroyed: " + ex + "SignalStrength not notified");
                }
            }
        }
        return notified;
    }

    protected void notifyDataRegStateRilRadioTechnologyChanged() {
        int rat = this.mSS.getRilDataRadioTechnology();
        int drs = this.mSS.getDataRegState();
        log("notifyDataRegStateRilRadioTechnologyChanged: drs=" + drs + " rat=" + rat);
        this.mPhoneBase.setSystemProperty("gsm.network.type", ServiceState.rilRadioTechnologyToString(rat));
        this.mDataRegStateOrRatChangedRegistrants.notifyResult(new Pair(Integer.valueOf(drs), Integer.valueOf(rat)));
    }

    protected void useDataRegStateForDataOnlyDevices() {
        if (!this.mVoiceCapable) {
            log("useDataRegStateForDataOnlyDevice: VoiceRegState=" + this.mNewSS.getVoiceRegState() + " DataRegState=" + this.mNewSS.getDataRegState());
            this.mNewSS.setVoiceRegState(this.mNewSS.getDataRegState());
        }
    }

    protected void updatePhoneObject() {
        this.mPhoneBase.updatePhoneObject(this.mSS.getRilVoiceRadioTechnology());
    }

    public void registerForRoamingOn(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mRoamingOnRegistrants.add(r);
        if (this.mSS.getRoaming()) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForRoamingOn(Handler h) {
        this.mRoamingOnRegistrants.remove(h);
    }

    public void registerForRoamingOff(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mRoamingOffRegistrants.add(r);
        if (!this.mSS.getRoaming()) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForRoamingOff(Handler h) {
        this.mRoamingOffRegistrants.remove(h);
    }

    public void reRegisterNetwork(Message onComplete) {
        this.mCi.getPreferredNetworkType(obtainMessage(19, onComplete));
    }

    public void setRadioPower(boolean power) {
        this.mDesiredPowerState = power;
        setPowerStateToDesired();
    }

    public void enableSingleLocationUpdate() {
        if (!this.mWantSingleLocationUpdate && !this.mWantContinuousLocationUpdates) {
            this.mWantSingleLocationUpdate = true;
            this.mCi.setLocationUpdates(true, obtainMessage(18));
        }
    }

    public void enableLocationUpdates() {
        if (!this.mWantSingleLocationUpdate && !this.mWantContinuousLocationUpdates) {
            this.mWantContinuousLocationUpdates = true;
            this.mCi.setLocationUpdates(true, obtainMessage(18));
        }
    }

    protected void disableSingleLocationUpdate() {
        this.mWantSingleLocationUpdate = false;
        if (!this.mWantSingleLocationUpdate && !this.mWantContinuousLocationUpdates) {
            this.mCi.setLocationUpdates(false, null);
        }
    }

    public void disableLocationUpdates() {
        this.mWantContinuousLocationUpdates = false;
        if (!this.mWantSingleLocationUpdate && !this.mWantContinuousLocationUpdates) {
            this.mCi.setLocationUpdates(false, null);
        }
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case EVENT_SET_RADIO_POWER_OFF /* 38 */:
                synchronized (this) {
                    if (this.mPendingRadioPowerOffAfterDataOff && msg.arg1 == this.mPendingRadioPowerOffAfterDataOffTag) {
                        log("EVENT_SET_RADIO_OFF, turn radio off now.");
                        hangupAndPowerOff();
                        this.mPendingRadioPowerOffAfterDataOffTag++;
                        this.mPendingRadioPowerOffAfterDataOff = false;
                    } else {
                        log("EVENT_SET_RADIO_OFF is stale arg1=" + msg.arg1 + "!= tag=" + this.mPendingRadioPowerOffAfterDataOffTag);
                    }
                    break;
                }
                return;
            case 39:
            case EVENT_CDMA_PRL_VERSION_CHANGED /* 40 */:
            case 41:
            default:
                log("Unhandled message with number: " + msg.what);
                return;
            case 42:
                onUpdateIccAvailability();
                return;
            case EVENT_GET_CELL_INFO_LIST /* 43 */:
                AsyncResult ar = (AsyncResult) msg.obj;
                CellInfoResult result = (CellInfoResult) ar.userObj;
                synchronized (result.lockObj) {
                    if (ar.exception != null) {
                        log("EVENT_GET_CELL_INFO_LIST: error ret null, e=" + ar.exception);
                        result.list = null;
                    } else {
                        result.list = (List) ar.result;
                    }
                    this.mLastCellInfoListTime = SystemClock.elapsedRealtime();
                    this.mLastCellInfoList = result.list;
                    result.lockObj.notify();
                    this.mCellInfoWaitList.remove(result);
                    break;
                }
                return;
            case 44:
                AsyncResult ar2 = (AsyncResult) msg.obj;
                if (ar2.exception != null) {
                    log("EVENT_UNSOL_CELL_INFO_LIST: error ignoring, e=" + ar2.exception);
                    return;
                }
                List<CellInfo> list = (List) ar2.result;
                log("EVENT_UNSOL_CELL_INFO_LIST: size=" + list.size() + " list=" + list);
                this.mLastCellInfoListTime = SystemClock.elapsedRealtime();
                this.mLastCellInfoList = list;
                this.mPhoneBase.notifyCellInfo(list);
                return;
        }
    }

    public void registerForDataConnectionAttached(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mAttachedRegistrants.add(r);
        if (getCurrentDataConnectionState() == 0) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForDataConnectionAttached(Handler h) {
        this.mAttachedRegistrants.remove(h);
    }

    public void registerForDataConnectionDetached(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mDetachedRegistrants.add(r);
        if (getCurrentDataConnectionState() != 0) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForDataConnectionDetached(Handler h) {
        this.mDetachedRegistrants.remove(h);
    }

    public void registerForIwlanAvailable(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mIwlanRegistrants.add(r);
    }

    public void unregisterForIwlanAvailable(Handler h) {
        this.mIwlanRegistrants.remove(h);
    }

    public void registerForDataRegStateOrRatChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mDataRegStateOrRatChangedRegistrants.add(r);
        notifyDataRegStateRilRadioTechnologyChanged();
    }

    public void unregisterForDataRegStateOrRatChanged(Handler h) {
        this.mDataRegStateOrRatChangedRegistrants.remove(h);
    }

    public void registerForNetworkAttached(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mNetworkAttachedRegistrants.add(r);
        if (this.mSS.getVoiceRegState() == 0) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForNetworkAttached(Handler h) {
        this.mNetworkAttachedRegistrants.remove(h);
    }

    public void registerForPsRestrictedEnabled(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mPsRestrictEnabledRegistrants.add(r);
        if (this.mRestrictedState.isPsRestricted()) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForPsRestrictedEnabled(Handler h) {
        this.mPsRestrictEnabledRegistrants.remove(h);
    }

    public void registerForPsRestrictedDisabled(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mPsRestrictDisabledRegistrants.add(r);
        if (this.mRestrictedState.isPsRestricted()) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForPsRestrictedDisabled(Handler h) {
        this.mPsRestrictDisabledRegistrants.remove(h);
    }

    public void powerOffRadioSafely(DcTrackerBase dcTracker) {
        synchronized (this) {
            if (!this.mPendingRadioPowerOffAfterDataOff) {
                if (dcTracker.isDisconnected()) {
                    dcTracker.cleanUpAllConnections(Phone.REASON_RADIO_TURNED_OFF);
                    log("Data disconnected, turn off radio right away.");
                    hangupAndPowerOff();
                } else {
                    dcTracker.cleanUpAllConnections(Phone.REASON_RADIO_TURNED_OFF);
                    Message msg = Message.obtain(this);
                    msg.what = EVENT_SET_RADIO_POWER_OFF;
                    int i = this.mPendingRadioPowerOffAfterDataOffTag + 1;
                    this.mPendingRadioPowerOffAfterDataOffTag = i;
                    msg.arg1 = i;
                    if (sendMessageDelayed(msg, 30000L)) {
                        log("Wait upto 30s for data to disconnect, then turn off radio.");
                        this.mPendingRadioPowerOffAfterDataOff = true;
                    } else {
                        log("Cannot send delayed Msg, turn off radio right away.");
                        hangupAndPowerOff();
                    }
                }
            }
        }
    }

    public boolean processPendingRadioPowerOffAfterDataOff() {
        boolean z = false;
        synchronized (this) {
            if (this.mPendingRadioPowerOffAfterDataOff) {
                log("Process pending request to turn radio off.");
                this.mPendingRadioPowerOffAfterDataOffTag++;
                hangupAndPowerOff();
                this.mPendingRadioPowerOffAfterDataOff = false;
                z = true;
            }
        }
        return z;
    }

    protected boolean onSignalStrengthResult(AsyncResult ar, boolean isGsm) {
        SignalStrength signalStrength = this.mSignalStrength;
        if (ar.exception == null && ar.result != null) {
            this.mSignalStrength = (SignalStrength) ar.result;
            this.mSignalStrength.validateInput();
            this.mSignalStrength.setGsm(isGsm);
        } else {
            log("onSignalStrengthResult() Exception from RIL : " + ar.exception);
            this.mSignalStrength = new SignalStrength(isGsm);
        }
        return notifySignalStrength();
    }

    protected void cancelPollState() {
        this.mPollingContext = new int[1];
    }

    protected boolean shouldFixTimeZoneNow(PhoneBase phoneBase, String operatorNumeric, String prevOperatorNumeric, boolean needToFixTimeZone) {
        int prevMcc;
        try {
            int mcc = Integer.parseInt(operatorNumeric.substring(0, 3));
            try {
                prevMcc = Integer.parseInt(prevOperatorNumeric.substring(0, 3));
            } catch (Exception e) {
                prevMcc = mcc + 1;
            }
            boolean iccCardExist = false;
            if (this.mUiccApplcation != null) {
                iccCardExist = this.mUiccApplcation.getState() != IccCardApplicationStatus.AppState.APPSTATE_UNKNOWN;
            }
            boolean retVal = (iccCardExist && mcc != prevMcc) || needToFixTimeZone;
            long ctm = System.currentTimeMillis();
            log("shouldFixTimeZoneNow: retVal=" + retVal + " iccCardExist=" + iccCardExist + " operatorNumeric=" + operatorNumeric + " mcc=" + mcc + " prevOperatorNumeric=" + prevOperatorNumeric + " prevMcc=" + prevMcc + " needToFixTimeZone=" + needToFixTimeZone + " ltod=" + TimeUtils.logTimeOfDay(ctm));
            return retVal;
        } catch (Exception e2) {
            log("shouldFixTimeZoneNow: no mcc, operatorNumeric=" + operatorNumeric + " retVal=false");
            return false;
        }
    }

    public List<CellInfo> getAllCellInfo() {
        CellInfoResult result = new CellInfoResult();
        int ver = this.mCi.getRilVersion();
        if (ver >= 8) {
            if (!isCallerOnDifferentThread()) {
                log("SST.getAllCellInfo(): return last, same thread can't block");
                result.list = this.mLastCellInfoList;
            } else if (SystemClock.elapsedRealtime() - this.mLastCellInfoListTime > LAST_CELL_INFO_LIST_MAX_AGE_MS) {
                Message msg = obtainMessage(EVENT_GET_CELL_INFO_LIST, result);
                synchronized (result.lockObj) {
                    this.mCi.getCellInfoList(msg);
                    try {
                        this.mCellInfoWaitList.add(result);
                        result.lockObj.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        result.list = null;
                        this.mCellInfoWaitList.remove(result);
                    }
                }
            } else {
                log("SST.getAllCellInfo(): return last, back to back calls");
                result.list = this.mLastCellInfoList;
            }
        } else {
            log("SST.getAllCellInfo(): not implemented");
            result.list = null;
        }
        if (result.list != null) {
            log("SST.getAllCellInfo(): X size=" + result.list.size() + " list=" + result.list);
        } else {
            log("SST.getAllCellInfo(): X size=0 list=null");
        }
        return result.list;
    }

    public SignalStrength getSignalStrength() {
        SignalStrength signalStrength;
        synchronized (this.mCellInfo) {
            signalStrength = this.mSignalStrength;
        }
        return signalStrength;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("ServiceStateTracker:");
        pw.println(" mSS=" + this.mSS);
        pw.println(" mNewSS=" + this.mNewSS);
        pw.println(" mCellInfo=" + this.mCellInfo);
        pw.println(" mRestrictedState=" + this.mRestrictedState);
        pw.println(" mPollingContext=" + this.mPollingContext);
        pw.println(" mDesiredPowerState=" + this.mDesiredPowerState);
        pw.println(" mDontPollSignalStrength=" + this.mDontPollSignalStrength);
        pw.println(" mPendingRadioPowerOffAfterDataOff=" + this.mPendingRadioPowerOffAfterDataOff);
        pw.println(" mPendingRadioPowerOffAfterDataOffTag=" + this.mPendingRadioPowerOffAfterDataOffTag);
    }

    protected void checkCorrectThread() {
        if (Thread.currentThread() != getLooper().getThread()) {
            throw new RuntimeException("ServiceStateTracker must be used from within one thread");
        }
    }

    protected boolean isCallerOnDifferentThread() {
        return Thread.currentThread() != getLooper().getThread();
    }

    protected boolean isIwlanFeatureAvailable() {
        boolean iwlanAvailable = this.mPhoneBase.getContext().getResources().getBoolean(R.bool.config_bugReportHandlerEnabled);
        log("Iwlan feature available = " + iwlanAvailable);
        return iwlanAvailable && isWifiConnected();
    }

    protected boolean isWifiConnected() {
        log("isWifiConnected()");
        ConnectivityManager connManager = (ConnectivityManager) this.mPhoneBase.getContext().getSystemService("connectivity");
        NetworkInfo mWifi = connManager.getNetworkInfo(1);
        boolean wifi = mWifi != null ? mWifi.isConnected() : false;
        log("isWifiConnected = " + wifi);
        return wifi;
    }

    protected void handleIwlan() {
        log("handleIwlan");
        DcTrackerBase dcTracker = this.mPhoneBase.mDcTracker;
        dcTracker.disableApnType("default");
    }

    protected void updateCarrierMccMncConfiguration(String newOp, String oldOp, Context context) {
        if ((newOp == null && !TextUtils.isEmpty(oldOp)) || (newOp != null && !newOp.equals(oldOp))) {
            MccTable.updateMccMncConfiguration(context, newOp, true);
        }
    }
}
