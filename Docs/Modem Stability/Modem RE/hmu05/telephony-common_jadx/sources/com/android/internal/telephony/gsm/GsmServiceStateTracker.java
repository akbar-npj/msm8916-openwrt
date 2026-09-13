package com.android.internal.telephony.gsm;

import android.R;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellLocation;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.EventLog;
import android.util.TimeUtils;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.RestrictedState;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.dataconnection.DcTrackerBase;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/* JADX INFO: loaded from: classes.dex */
public class GsmServiceStateTracker extends ServiceStateTracker {
    static final int CS_DISABLED = 1004;
    static final int CS_EMERGENCY_ENABLED = 1006;
    static final int CS_ENABLED = 1003;
    static final int CS_NORMAL_ENABLED = 1005;
    static final int CS_NOTIFICATION = 999;
    static final String LOG_TAG = "GsmSST";
    static final int PS_DISABLED = 1002;
    static final int PS_ENABLED = 1001;
    static final int PS_NOTIFICATION = 888;
    static final boolean VDBG = false;
    private static final String WAKELOCK_TAG = "ServiceStateTracker";
    private ContentObserver mAutoTimeObserver;
    private ContentObserver mAutoTimeZoneObserver;
    GsmCellLocation mCellLoc;
    private ContentResolver mCr;
    protected String mCurPlmn;
    protected boolean mCurShowPlmn;
    protected boolean mCurShowSpn;
    protected String mCurSpn;
    private boolean mDataRoaming;
    protected boolean mEmergencyOnly;
    private boolean mGotCountryCode;
    private boolean mGsmRoaming;
    private BroadcastReceiver mIntentReceiver;
    private int mMaxDataCalls;
    private boolean mNeedFixZoneAfterNitz;
    GsmCellLocation mNewCellLoc;
    private int mNewMaxDataCalls;
    private int mNewReasonDataDenied;
    private boolean mNitzUpdatedTime;
    private Notification mNotification;
    protected GSMPhone mPhone;
    int mPreferredNetworkType;
    private int mReasonDataDenied;
    private boolean mReportedGprsNoReg;
    long mSavedAtTime;
    long mSavedTime;
    String mSavedTimeZone;
    private boolean mStartedGprsRegCheck;
    private PowerManager.WakeLock mWakeLock;
    private boolean mZoneDst;
    private int mZoneOffset;
    private long mZoneTime;

    public GsmServiceStateTracker(GSMPhone phone) {
        super(phone, phone.mCi, new CellInfoGsm());
        this.mMaxDataCalls = 1;
        this.mNewMaxDataCalls = 1;
        this.mReasonDataDenied = -1;
        this.mNewReasonDataDenied = -1;
        this.mGsmRoaming = false;
        this.mDataRoaming = false;
        this.mEmergencyOnly = false;
        this.mNeedFixZoneAfterNitz = false;
        this.mGotCountryCode = false;
        this.mNitzUpdatedTime = false;
        this.mStartedGprsRegCheck = false;
        this.mReportedGprsNoReg = false;
        this.mCurSpn = null;
        this.mCurPlmn = null;
        this.mCurShowPlmn = false;
        this.mCurShowSpn = false;
        this.mIntentReceiver = new BroadcastReceiver() { // from class: com.android.internal.telephony.gsm.GsmServiceStateTracker.1
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context, Intent intent) {
                if (!GsmServiceStateTracker.this.mPhone.mIsTheCurrentActivePhone) {
                    Rlog.e(GsmServiceStateTracker.LOG_TAG, "Received Intent " + intent + " while being destroyed. Ignoring.");
                } else if (intent.getAction().equals("android.intent.action.LOCALE_CHANGED")) {
                    GsmServiceStateTracker.this.updateSpnDisplay();
                }
            }
        };
        this.mAutoTimeObserver = new ContentObserver(new Handler()) { // from class: com.android.internal.telephony.gsm.GsmServiceStateTracker.2
            @Override // android.database.ContentObserver
            public void onChange(boolean selfChange) {
                Rlog.i("GsmServiceStateTracker", "Auto time state changed");
                GsmServiceStateTracker.this.revertToNitzTime();
            }
        };
        this.mAutoTimeZoneObserver = new ContentObserver(new Handler()) { // from class: com.android.internal.telephony.gsm.GsmServiceStateTracker.3
            @Override // android.database.ContentObserver
            public void onChange(boolean selfChange) {
                Rlog.i("GsmServiceStateTracker", "Auto time zone state changed");
                GsmServiceStateTracker.this.revertToNitzTimeZone();
            }
        };
        this.mPhone = phone;
        this.mCellLoc = new GsmCellLocation();
        this.mNewCellLoc = new GsmCellLocation();
        PowerManager powerManager = (PowerManager) phone.getContext().getSystemService("power");
        this.mWakeLock = powerManager.newWakeLock(1, WAKELOCK_TAG);
        this.mCi.registerForAvailable(this, 13, null);
        this.mCi.registerForRadioStateChanged(this, 1, null);
        this.mCi.registerForVoiceNetworkStateChanged(this, 2, null);
        this.mCi.setOnNITZTime(this, 11, null);
        this.mCi.setOnRestrictedStateChanged(this, 23, null);
        int airplaneMode = Settings.Global.getInt(phone.getContext().getContentResolver(), "airplane_mode_on", 0);
        this.mDesiredPowerState = airplaneMode <= 0;
        this.mCr = phone.getContext().getContentResolver();
        this.mCr.registerContentObserver(Settings.Global.getUriFor("auto_time"), true, this.mAutoTimeObserver);
        this.mCr.registerContentObserver(Settings.Global.getUriFor("auto_time_zone"), true, this.mAutoTimeZoneObserver);
        setSignalStrengthDefaultValues();
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.LOCALE_CHANGED");
        phone.getContext().registerReceiver(this.mIntentReceiver, filter);
        phone.notifyOtaspChanged(3);
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    public void dispose() {
        checkCorrectThread();
        log("ServiceStateTracker dispose");
        this.mCi.unregisterForAvailable(this);
        this.mCi.unregisterForRadioStateChanged(this);
        this.mCi.unregisterForVoiceNetworkStateChanged(this);
        if (this.mUiccApplcation != null) {
            this.mUiccApplcation.unregisterForReady(this);
        }
        if (this.mIccRecords != null) {
            this.mIccRecords.unregisterForRecordsLoaded(this);
        }
        this.mCi.unSetOnRestrictedStateChanged(this);
        this.mCi.unSetOnNITZTime(this);
        this.mCr.unregisterContentObserver(this.mAutoTimeObserver);
        this.mCr.unregisterContentObserver(this.mAutoTimeZoneObserver);
        this.mPhone.getContext().unregisterReceiver(this.mIntentReceiver);
        super.dispose();
    }

    protected void finalize() {
        log("finalize");
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    protected Phone getPhone() {
        return this.mPhone;
    }

    @Override // com.android.internal.telephony.ServiceStateTracker, android.os.Handler
    public void handleMessage(Message msg) {
        if (!this.mPhone.mIsTheCurrentActivePhone) {
            Rlog.e(LOG_TAG, "Received message " + msg + "[" + msg.what + "] while being destroyed. Ignoring.");
        }
        switch (msg.what) {
            case 1:
                setPowerStateToDesired();
                pollState();
                break;
            case 2:
                pollState();
                break;
            case 3:
                if (this.mCi.getRadioState().isOn()) {
                    onSignalStrengthResult((AsyncResult) msg.obj, true);
                    queueNextSignalStrengthPoll();
                }
                break;
            case 4:
            case 5:
            case 6:
            case 14:
                handlePollStateResult(msg.what, (AsyncResult) msg.obj);
                break;
            case 7:
            case 8:
            case 9:
            default:
                super.handleMessage(msg);
                break;
            case 10:
                this.mCi.getSignalStrength(obtainMessage(3));
                break;
            case 11:
                AsyncResult ar = (AsyncResult) msg.obj;
                String nitzString = (String) ((Object[]) ar.result)[0];
                long nitzReceiveTime = ((Long) ((Object[]) ar.result)[1]).longValue();
                setTimeFromNITZString(nitzString, nitzReceiveTime);
                break;
            case 12:
                AsyncResult ar2 = (AsyncResult) msg.obj;
                this.mDontPollSignalStrength = true;
                onSignalStrengthResult(ar2, true);
                break;
            case 13:
                break;
            case 15:
                AsyncResult ar3 = (AsyncResult) msg.obj;
                if (ar3.exception == null) {
                    String[] states = (String[]) ar3.result;
                    int lac = -1;
                    int cid = -1;
                    if (states.length >= 3) {
                        try {
                            if (states[1] != null && states[1].length() > 0) {
                                lac = Integer.parseInt(states[1], 16);
                            }
                            if (states[2] != null && states[2].length() > 0) {
                                cid = Integer.parseInt(states[2], 16);
                            }
                        } catch (NumberFormatException ex) {
                            Rlog.w(LOG_TAG, "error parsing location: " + ex);
                        }
                    }
                    this.mCellLoc.setLacAndCid(lac, cid);
                    this.mPhone.notifyLocationChanged();
                }
                disableSingleLocationUpdate();
                break;
            case 16:
                log("EVENT_SIM_RECORDS_LOADED: what=" + msg.what);
                updatePhoneObject();
                updateSpnDisplay();
                break;
            case 17:
                this.mCi.setCurrentPreferredNetworkType();
                boolean skipRestoringSelection = this.mPhone.getContext().getResources().getBoolean(R.bool.config_autoResetAirplaneMode);
                if (!skipRestoringSelection) {
                    this.mPhone.restoreSavedNetworkSelection(null);
                }
                pollState();
                queueNextSignalStrengthPoll();
                break;
            case 18:
                if (((AsyncResult) msg.obj).exception == null) {
                    this.mCi.getVoiceRegistrationState(obtainMessage(15, null));
                }
                break;
            case 19:
                AsyncResult ar4 = (AsyncResult) msg.obj;
                if (ar4.exception == null) {
                    this.mPreferredNetworkType = ((int[]) ar4.result)[0];
                } else {
                    this.mPreferredNetworkType = 11;
                }
                Message message = obtainMessage(20, ar4.userObj);
                this.mCi.setPreferredNetworkType(11, message);
                break;
            case 20:
                Message message2 = obtainMessage(21, ((AsyncResult) msg.obj).userObj);
                this.mCi.setPreferredNetworkType(this.mPreferredNetworkType, message2);
                break;
            case 21:
                AsyncResult ar5 = (AsyncResult) msg.obj;
                if (ar5.userObj != null) {
                    AsyncResult.forMessage((Message) ar5.userObj).exception = ar5.exception;
                    ((Message) ar5.userObj).sendToTarget();
                }
                break;
            case 22:
                if (this.mSS != null && !isGprsConsistent(this.mSS.getDataRegState(), this.mSS.getVoiceRegState())) {
                    GsmCellLocation loc = (GsmCellLocation) this.mPhone.getCellLocation();
                    Object[] objArr = new Object[2];
                    objArr[0] = this.mSS.getOperatorNumeric();
                    objArr[1] = Integer.valueOf(loc != null ? loc.getCid() : -1);
                    EventLog.writeEvent(EventLogTags.DATA_NETWORK_REGISTRATION_FAIL, objArr);
                    this.mReportedGprsNoReg = true;
                }
                this.mStartedGprsRegCheck = false;
                break;
            case 23:
                log("EVENT_RESTRICTED_STATE_CHANGED");
                onRestrictedStateChanged((AsyncResult) msg.obj);
                break;
        }
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    protected void setPowerStateToDesired() {
        if (this.mDesiredPowerState && this.mCi.getRadioState() == CommandsInterface.RadioState.RADIO_OFF) {
            this.mCi.setRadioPower(true, null);
        } else if (!this.mDesiredPowerState && this.mCi.getRadioState().isOn()) {
            DcTrackerBase dcTracker = this.mPhone.mDcTracker;
            powerOffRadioSafely(dcTracker);
        }
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    protected void hangupAndPowerOff() {
        if (this.mPhone.isInCall()) {
            this.mPhone.mCT.mRingingCall.hangupIfAlive();
            this.mPhone.mCT.mBackgroundCall.hangupIfAlive();
            this.mPhone.mCT.mForegroundCall.hangupIfAlive();
        }
        this.mCi.setRadioPower(false, null);
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    protected void updateSpnDisplay() {
        IccRecords iccRecords = this.mIccRecords;
        String plmn = null;
        boolean showPlmn = false;
        int rule = iccRecords != null ? iccRecords.getDisplayRule(this.mSS.getOperatorNumeric()) : 0;
        int combinedRegState = getCombinedRegState();
        if (combinedRegState == 1 || combinedRegState == 2) {
            showPlmn = true;
            if (this.mEmergencyOnly) {
                plmn = Resources.getSystem().getText(R.string.date_picker_day_typeface).toString();
            } else {
                plmn = Resources.getSystem().getText(R.string.conversation_title_fallback_one_to_one).toString();
            }
            log("updateSpnDisplay: radio is on but out of service, set plmn='" + plmn + "'");
        } else if (combinedRegState != 0) {
            log("updateSpnDisplay: radio is off w/ showPlmn=false plmn=" + ((String) null));
        } else {
            plmn = this.mSS.getOperatorAlphaLong();
            showPlmn = !TextUtils.isEmpty(plmn) && (rule & 2) == 2;
        }
        String spn = iccRecords != null ? iccRecords.getServiceProviderName() : "";
        boolean showSpn = !TextUtils.isEmpty(spn) && (rule & 1) == 1;
        if (showPlmn != this.mCurShowPlmn || showSpn != this.mCurShowSpn || !TextUtils.equals(spn, this.mCurSpn) || !TextUtils.equals(plmn, this.mCurPlmn)) {
            log(String.format("updateSpnDisplay: changed sending intent rule=" + rule + " showPlmn='%b' plmn='%s' showSpn='%b' spn='%s'", Boolean.valueOf(showPlmn), plmn, Boolean.valueOf(showSpn), spn));
            Intent intent = new Intent("android.provider.Telephony.SPN_STRINGS_UPDATED");
            intent.addFlags(536870912);
            intent.putExtra("showSpn", showSpn);
            intent.putExtra("spn", spn);
            intent.putExtra("showPlmn", showPlmn);
            intent.putExtra(Telephony.CellBroadcasts.PLMN, plmn);
            this.mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        }
        this.mCurShowSpn = showSpn;
        this.mCurShowPlmn = showPlmn;
        this.mCurSpn = spn;
        this.mCurPlmn = plmn;
    }

    private int getCombinedRegState() {
        int regState = this.mSS.getVoiceRegState();
        int dataRegState = this.mSS.getDataRegState();
        if (regState == 1 && dataRegState == 0) {
            log("getCombinedRegState: return STATE_IN_SERVICE as Data is in service");
            return dataRegState;
        }
        return regState;
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    protected void handlePollStateResult(int what, AsyncResult ar) {
        if (ar.userObj == this.mPollingContext) {
            if (ar.exception != null) {
                CommandException.Error err = null;
                if (ar.exception instanceof CommandException) {
                    err = ((CommandException) ar.exception).getCommandError();
                }
                if (err == CommandException.Error.RADIO_NOT_AVAILABLE) {
                    cancelPollState();
                    return;
                } else if (err != CommandException.Error.OP_NOT_ALLOWED_BEFORE_REG_NW) {
                    loge("RIL implementation has returned an error where it must succeed" + ar.exception);
                }
            } else {
                try {
                    switch (what) {
                        case 4:
                            String[] states = (String[]) ar.result;
                            int lac = -1;
                            int cid = -1;
                            int type = 0;
                            int regState = 4;
                            int psc = -1;
                            int cssIndicator = 0;
                            if (states.length > 0) {
                                try {
                                    regState = Integer.parseInt(states[0]);
                                    if (states.length >= 3) {
                                        if (states[1] != null && states[1].length() > 0) {
                                            lac = Integer.parseInt(states[1], 16);
                                        }
                                        if (states[2] != null && states[2].length() > 0) {
                                            cid = Integer.parseInt(states[2], 16);
                                        }
                                        if (states.length >= 4 && states[3] != null) {
                                            type = Integer.parseInt(states[3]);
                                        }
                                    }
                                    if (states.length >= 7 && states[7] != null) {
                                        cssIndicator = Integer.parseInt(states[7]);
                                    }
                                    if (states.length > 14 && states[14] != null && states[14].length() > 0) {
                                        psc = Integer.parseInt(states[14], 16);
                                    }
                                } catch (NumberFormatException ex) {
                                    loge("error parsing RegistrationState: " + ex);
                                }
                            }
                            this.mGsmRoaming = regCodeIsRoaming(regState);
                            this.mNewSS.setState(regCodeToServiceState(regState));
                            this.mNewSS.setRilVoiceRadioTechnology(type);
                            this.mNewSS.setCssIndicator(cssIndicator);
                            if ((regState == 3 || regState == 13) && states.length >= 14) {
                                try {
                                    int rejCode = Integer.parseInt(states[13]);
                                    if (rejCode == 10) {
                                        log(" Posting Managed roaming intent sub = " + this.mPhone.getSubscription());
                                        Intent intent = new Intent("codeaurora.intent.action.ACTION_MANAGED_ROAMING_IND");
                                        intent.putExtra("subscription", this.mPhone.getSubscription());
                                        this.mPhone.getContext().sendBroadcast(intent);
                                    }
                                } catch (NumberFormatException ex2) {
                                    loge("error parsing regCode: " + ex2);
                                }
                            }
                            boolean isVoiceCapable = this.mPhoneBase.getContext().getResources().getBoolean(R.bool.config_audio_ringer_mode_affects_alarm_stream);
                            if ((regState == 13 || regState == 10 || regState == 12 || regState == 14) && isVoiceCapable) {
                                this.mEmergencyOnly = true;
                            } else {
                                this.mEmergencyOnly = false;
                            }
                            this.mNewCellLoc.setLacAndCid(lac, cid);
                            this.mNewCellLoc.setPsc(psc);
                            break;
                        case 5:
                            String[] states2 = (String[]) ar.result;
                            int type2 = 0;
                            int regState2 = 4;
                            this.mNewReasonDataDenied = -1;
                            this.mNewMaxDataCalls = 1;
                            if (states2.length > 0) {
                                try {
                                    regState2 = Integer.parseInt(states2[0]);
                                    if (states2.length >= 4 && states2[3] != null) {
                                        type2 = Integer.parseInt(states2[3]);
                                    }
                                    if (states2.length >= 5 && regState2 == 3) {
                                        this.mNewReasonDataDenied = Integer.parseInt(states2[4]);
                                    }
                                    if (states2.length >= 6) {
                                        this.mNewMaxDataCalls = Integer.parseInt(states2[5]);
                                    }
                                } catch (NumberFormatException ex3) {
                                    loge("error parsing GprsRegistrationState: " + ex3);
                                }
                            }
                            int dataRegState = regCodeToServiceState(regState2);
                            this.mNewSS.setDataRegState(dataRegState);
                            this.mDataRoaming = regCodeIsRoaming(regState2);
                            this.mNewSS.setRilDataRadioTechnology(type2);
                            log("handlPollStateResultMessage: GsmSST setDataRegState=" + dataRegState + " regState=" + regState2 + " dataRadioTechnology=" + type2);
                            break;
                        case 6:
                            String[] opNames = (String[]) ar.result;
                            if (opNames != null && opNames.length >= 3) {
                                this.mNewSS.setOperatorName(opNames[0], opNames[1], opNames[2]);
                            }
                            break;
                        case 14:
                            int[] ints = (int[]) ar.result;
                            this.mNewSS.setIsManualSelection(ints[0] == 1);
                            if (ints[0] == 1 && !this.mPhone.isManualNetSelAllowed()) {
                                this.mPhone.setNetworkSelectionModeAutomatic(null);
                                log(" Forcing Automatic Network Selection, manual selection is not allowed");
                            }
                            break;
                    }
                } catch (RuntimeException ex4) {
                    loge("Exception while polling service state. Probably malformed RIL response." + ex4);
                }
            }
            int[] iArr = this.mPollingContext;
            iArr[0] = iArr[0] - 1;
            if (this.mPollingContext[0] == 0) {
                boolean roaming = this.mGsmRoaming || this.mDataRoaming;
                if ((this.mGsmRoaming && isSameNamedOperators(this.mNewSS) && !isSameNamedOperatorConsideredRoaming(this.mNewSS)) || isOperatorConsideredNonRoaming(this.mNewSS)) {
                    roaming = false;
                }
                this.mNewSS.setRoaming(roaming);
                this.mNewSS.setEmergencyOnly(this.mEmergencyOnly);
                pollStateDone();
            }
        }
    }

    private void setSignalStrengthDefaultValues() {
        this.mSignalStrength = new SignalStrength(true);
    }

    private void pollState() {
        this.mPollingContext = new int[1];
        this.mPollingContext[0] = 0;
        switch (this.mCi.getRadioState()) {
            case RADIO_UNAVAILABLE:
                this.mNewSS.setStateOutOfService();
                this.mNewCellLoc.setStateInvalid();
                setSignalStrengthDefaultValues();
                this.mGotCountryCode = false;
                this.mNitzUpdatedTime = false;
                pollStateDone();
                return;
            case RADIO_OFF:
                this.mNewSS.setStateOff();
                this.mNewCellLoc.setStateInvalid();
                setSignalStrengthDefaultValues();
                this.mGotCountryCode = false;
                this.mNitzUpdatedTime = false;
                pollStateDone();
                if (!isIwlanFeatureAvailable()) {
                    return;
                }
                break;
        }
        int[] iArr = this.mPollingContext;
        iArr[0] = iArr[0] + 1;
        this.mCi.getOperator(obtainMessage(6, this.mPollingContext));
        int[] iArr2 = this.mPollingContext;
        iArr2[0] = iArr2[0] + 1;
        this.mCi.getDataRegistrationState(obtainMessage(5, this.mPollingContext));
        int[] iArr3 = this.mPollingContext;
        iArr3[0] = iArr3[0] + 1;
        this.mCi.getVoiceRegistrationState(obtainMessage(4, this.mPollingContext));
        int[] iArr4 = this.mPollingContext;
        iArr4[0] = iArr4[0] + 1;
        this.mCi.getNetworkSelectionMode(obtainMessage(14, this.mPollingContext));
    }

    private void pollStateDone() {
        TimeZone zone;
        log("Poll ServiceState done:  oldSS=[" + this.mSS + "] newSS=[" + this.mNewSS + "] oldMaxDataCalls=" + this.mMaxDataCalls + " mNewMaxDataCalls=" + this.mNewMaxDataCalls + " oldReasonDataDenied=" + this.mReasonDataDenied + " mNewReasonDataDenied=" + this.mNewReasonDataDenied);
        if (Build.IS_DEBUGGABLE && SystemProperties.getBoolean("telephony.test.forceRoaming", false)) {
            this.mNewSS.setRoaming(true);
        }
        useDataRegStateForDataOnlyDevices();
        boolean hasRegistered = this.mSS.getVoiceRegState() != 0 && this.mNewSS.getVoiceRegState() == 0;
        if (this.mSS.getVoiceRegState() != 0 || this.mNewSS.getVoiceRegState() != 0) {
        }
        boolean hasGprsAttached = this.mSS.getDataRegState() != 0 && this.mNewSS.getDataRegState() == 0;
        boolean hasGprsDetached = this.mSS.getDataRegState() == 0 && this.mNewSS.getDataRegState() != 0;
        boolean hasDataRegStateChanged = this.mSS.getDataRegState() != this.mNewSS.getDataRegState();
        boolean hasVoiceRegStateChanged = this.mSS.getVoiceRegState() != this.mNewSS.getVoiceRegState();
        boolean hasRilVoiceRadioTechnologyChanged = this.mSS.getRilVoiceRadioTechnology() != this.mNewSS.getRilVoiceRadioTechnology();
        boolean hasRilDataRadioTechnologyChanged = this.mSS.getRilDataRadioTechnology() != this.mNewSS.getRilDataRadioTechnology();
        boolean hasChanged = !this.mNewSS.equals(this.mSS);
        boolean hasRoamingOn = !this.mSS.getRoaming() && this.mNewSS.getRoaming();
        boolean hasRoamingOff = this.mSS.getRoaming() && !this.mNewSS.getRoaming();
        boolean hasLocationChanged = !this.mNewCellLoc.equals(this.mCellLoc);
        boolean needNotifyData = this.mSS.getCssIndicator() != this.mNewSS.getCssIndicator();
        if (hasVoiceRegStateChanged || hasDataRegStateChanged) {
            EventLog.writeEvent(EventLogTags.GSM_SERVICE_STATE_CHANGE, Integer.valueOf(this.mSS.getVoiceRegState()), Integer.valueOf(this.mSS.getDataRegState()), Integer.valueOf(this.mNewSS.getVoiceRegState()), Integer.valueOf(this.mNewSS.getDataRegState()));
        }
        if (hasRilVoiceRadioTechnologyChanged) {
            GsmCellLocation loc = this.mNewCellLoc;
            int cid = loc != null ? loc.getCid() : -1;
            EventLog.writeEvent(EventLogTags.GSM_RAT_SWITCHED_NEW, Integer.valueOf(cid), Integer.valueOf(this.mSS.getRilVoiceRadioTechnology()), Integer.valueOf(this.mNewSS.getRilVoiceRadioTechnology()));
            log("RAT switched " + ServiceState.rilRadioTechnologyToString(this.mSS.getRilVoiceRadioTechnology()) + " -> " + ServiceState.rilRadioTechnologyToString(this.mNewSS.getRilVoiceRadioTechnology()) + " at cell " + cid);
        }
        ServiceState tss = this.mSS;
        this.mSS = this.mNewSS;
        this.mNewSS = tss;
        this.mNewSS.setStateOutOfService();
        GsmCellLocation tcl = this.mCellLoc;
        this.mCellLoc = this.mNewCellLoc;
        this.mNewCellLoc = tcl;
        this.mReasonDataDenied = this.mNewReasonDataDenied;
        this.mMaxDataCalls = this.mNewMaxDataCalls;
        if (hasRilVoiceRadioTechnologyChanged) {
            updatePhoneObject();
        }
        if (hasRilDataRadioTechnologyChanged) {
            this.mPhone.setSystemProperty("gsm.network.type", ServiceState.rilRadioTechnologyToString(this.mSS.getRilDataRadioTechnology()));
            if (isIwlanFeatureAvailable() && 18 == this.mSS.getRilDataRadioTechnology()) {
                handleIwlan();
            }
        }
        if (hasRegistered) {
            this.mNetworkAttachedRegistrants.notifyRegistrants();
            log("pollStateDone: registering current mNitzUpdatedTime=" + this.mNitzUpdatedTime + " changing to false");
            this.mNitzUpdatedTime = false;
        }
        if (hasChanged) {
            this.mPhone.setSystemProperty("gsm.operator.alpha", this.mSS.getOperatorAlphaLong());
            String prevOperatorNumeric = SystemProperties.get("gsm.operator.numeric", "");
            String operatorNumeric = this.mSS.getOperatorNumeric();
            this.mPhone.setSystemProperty("gsm.operator.numeric", operatorNumeric);
            updateCarrierMccMncConfiguration(operatorNumeric, prevOperatorNumeric, this.mPhone.getContext());
            if (operatorNumeric == null) {
                log("operatorNumeric is null");
                this.mPhone.setSystemProperty("gsm.operator.iso-country", "");
                this.mGotCountryCode = false;
                this.mNitzUpdatedTime = false;
            } else {
                String iso = "";
                String mcc = "";
                try {
                    mcc = operatorNumeric.substring(0, 3);
                    iso = MccTable.countryCodeForMcc(Integer.parseInt(mcc));
                } catch (NumberFormatException ex) {
                    loge("pollStateDone: countryCodeForMcc error" + ex);
                } catch (StringIndexOutOfBoundsException ex2) {
                    loge("pollStateDone: countryCodeForMcc error" + ex2);
                }
                this.mPhone.setSystemProperty("gsm.operator.iso-country", iso);
                this.mGotCountryCode = true;
                if (!this.mNitzUpdatedTime && !mcc.equals("000") && !TextUtils.isEmpty(iso) && getAutoTimeZone()) {
                    boolean testOneUniqueOffsetPath = SystemProperties.getBoolean("telephony.test.ignore.nitz", false) && (SystemClock.uptimeMillis() & 1) == 0;
                    ArrayList<TimeZone> uniqueZones = TimeUtils.getTimeZonesWithUniqueOffsets(iso);
                    if (uniqueZones.size() == 1 || testOneUniqueOffsetPath) {
                        TimeZone zone2 = uniqueZones.get(0);
                        TimeZone zone3 = zone2;
                        log("pollStateDone: no nitz but one TZ for iso-cc=" + iso + " with zone.getID=" + zone3.getID() + " testOneUniqueOffsetPath=" + testOneUniqueOffsetPath);
                        setAndBroadcastNetworkSetTimeZone(zone3.getID());
                    } else {
                        log("pollStateDone: there are " + uniqueZones.size() + " unique offsets for iso-cc='" + iso + " testOneUniqueOffsetPath=" + testOneUniqueOffsetPath + "', do nothing");
                    }
                }
                if (shouldFixTimeZoneNow(this.mPhone, operatorNumeric, prevOperatorNumeric, this.mNeedFixZoneAfterNitz)) {
                    String zoneName = SystemProperties.get("persist.sys.timezone");
                    log("pollStateDone: fix time zone zoneName='" + zoneName + "' mZoneOffset=" + this.mZoneOffset + " mZoneDst=" + this.mZoneDst + " iso-cc='" + iso + "' iso-cc-idx=" + Arrays.binarySearch(GMT_COUNTRY_CODES, iso));
                    if (this.mZoneOffset == 0 && !this.mZoneDst && zoneName != null && zoneName.length() > 0 && Arrays.binarySearch(GMT_COUNTRY_CODES, iso) < 0) {
                        zone = TimeZone.getDefault();
                        if (this.mNeedFixZoneAfterNitz) {
                            long ctm = System.currentTimeMillis();
                            long tzOffset = zone.getOffset(ctm);
                            log("pollStateDone: tzOffset=" + tzOffset + " ltod=" + TimeUtils.logTimeOfDay(ctm));
                            if (getAutoTime()) {
                                long adj = ctm - tzOffset;
                                log("pollStateDone: adj ltod=" + TimeUtils.logTimeOfDay(adj));
                                setAndBroadcastNetworkSetTime(adj);
                            } else {
                                this.mSavedTime -= tzOffset;
                            }
                        }
                        log("pollStateDone: using default TimeZone");
                    } else if (iso.equals("")) {
                        zone = getNitzTimeZone(this.mZoneOffset, this.mZoneDst, this.mZoneTime);
                        log("pollStateDone: using NITZ TimeZone");
                    } else {
                        zone = TimeUtils.getTimeZone(this.mZoneOffset, this.mZoneDst, this.mZoneTime, iso);
                        log("pollStateDone: using getTimeZone(off, dst, time, iso)");
                    }
                    this.mNeedFixZoneAfterNitz = false;
                    if (zone != null) {
                        log("pollStateDone: zone != null zone.getID=" + zone.getID());
                        if (getAutoTimeZone()) {
                            setAndBroadcastNetworkSetTimeZone(zone.getID());
                        }
                        saveNitzTimeZone(zone.getID());
                    } else {
                        log("pollStateDone: zone == null");
                    }
                }
            }
            this.mPhone.setSystemProperty("gsm.operator.isroaming", this.mSS.getRoaming() ? "true" : "false");
            this.mPhone.notifyServiceStateChanged(this.mSS);
            updateSpnDisplay();
        }
        if (hasGprsDetached) {
            this.mDetachedRegistrants.notifyRegistrants();
        }
        if (hasDataRegStateChanged || hasRilDataRadioTechnologyChanged) {
            notifyDataRegStateRilRadioTechnologyChanged();
            if (isIwlanFeatureAvailable() && 18 == this.mSS.getRilDataRadioTechnology()) {
                this.mPhone.notifyDataConnection(Phone.REASON_IWLAN_AVAILABLE);
                this.mIwlanRegistrants.notifyRegistrants();
                needNotifyData = false;
            } else {
                needNotifyData = true;
            }
        }
        if (needNotifyData) {
            this.mPhone.notifyDataConnection(null);
        }
        if (hasGprsAttached) {
            this.mAttachedRegistrants.notifyRegistrants();
        }
        if (hasRoamingOn) {
            this.mRoamingOnRegistrants.notifyRegistrants();
        }
        if (hasRoamingOff) {
            this.mRoamingOffRegistrants.notifyRegistrants();
        }
        if (hasLocationChanged) {
            this.mPhone.notifyLocationChanged();
        }
        if (!isGprsConsistent(this.mSS.getDataRegState(), this.mSS.getVoiceRegState())) {
            if (!this.mStartedGprsRegCheck && !this.mReportedGprsNoReg) {
                this.mStartedGprsRegCheck = true;
                int check_period = Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), "gprs_register_check_period_ms", ServiceStateTracker.DEFAULT_GPRS_CHECK_PERIOD_MILLIS);
                sendMessageDelayed(obtainMessage(22), check_period);
                return;
            }
            return;
        }
        this.mReportedGprsNoReg = false;
    }

    private boolean isGprsConsistent(int dataRegState, int voiceRegState) {
        return voiceRegState != 0 || dataRegState == 0;
    }

    private TimeZone getNitzTimeZone(int offset, boolean dst, long when) {
        TimeZone guess = findTimeZone(offset, dst, when);
        if (guess == null) {
            guess = findTimeZone(offset, !dst, when);
        }
        log("getNitzTimeZone returning " + (guess == null ? guess : guess.getID()));
        return guess;
    }

    private TimeZone findTimeZone(int offset, boolean dst, long when) {
        int rawOffset = offset;
        if (dst) {
            rawOffset -= 3600000;
        }
        String[] zones = TimeZone.getAvailableIDs(rawOffset);
        Date d = new Date(when);
        for (String zone : zones) {
            TimeZone tz = TimeZone.getTimeZone(zone);
            if (tz.getOffset(when) == offset && tz.inDaylightTime(d) == dst) {
                return tz;
            }
        }
        return null;
    }

    private void queueNextSignalStrengthPoll() {
        if (!this.mDontPollSignalStrength) {
            Message msg = obtainMessage();
            msg.what = 10;
            sendMessageDelayed(msg, 20000L);
        }
    }

    private void onRestrictedStateChanged(AsyncResult ar) {
        RestrictedState newRs = new RestrictedState();
        log("onRestrictedStateChanged: E rs " + this.mRestrictedState);
        if (ar.exception == null) {
            int[] ints = (int[]) ar.result;
            int state = ints[0];
            newRs.setCsEmergencyRestricted(((state & 1) == 0 && (state & 4) == 0) ? false : true);
            if (this.mUiccApplcation != null && this.mUiccApplcation.getState() == IccCardApplicationStatus.AppState.APPSTATE_READY) {
                newRs.setCsNormalRestricted(((state & 2) == 0 && (state & 4) == 0) ? false : true);
                newRs.setPsRestricted((state & 16) != 0);
            }
            log("onRestrictedStateChanged: new rs " + newRs);
            if (!this.mRestrictedState.isPsRestricted() && newRs.isPsRestricted()) {
                this.mPsRestrictEnabledRegistrants.notifyRegistrants();
                setNotification(1001);
            } else if (this.mRestrictedState.isPsRestricted() && !newRs.isPsRestricted()) {
                this.mPsRestrictDisabledRegistrants.notifyRegistrants();
                setNotification(1002);
            }
            if (this.mRestrictedState.isCsRestricted()) {
                if (!newRs.isCsRestricted()) {
                    setNotification(1004);
                } else if (!newRs.isCsNormalRestricted()) {
                    setNotification(1006);
                } else if (!newRs.isCsEmergencyRestricted()) {
                    setNotification(1005);
                }
            } else if (this.mRestrictedState.isCsEmergencyRestricted() && !this.mRestrictedState.isCsNormalRestricted()) {
                if (!newRs.isCsRestricted()) {
                    setNotification(1004);
                } else if (newRs.isCsRestricted()) {
                    setNotification(1003);
                } else if (newRs.isCsNormalRestricted()) {
                    setNotification(1005);
                }
            } else if (!this.mRestrictedState.isCsEmergencyRestricted() && this.mRestrictedState.isCsNormalRestricted()) {
                if (!newRs.isCsRestricted()) {
                    setNotification(1004);
                } else if (newRs.isCsRestricted()) {
                    setNotification(1003);
                } else if (newRs.isCsEmergencyRestricted()) {
                    setNotification(1006);
                }
            } else if (newRs.isCsRestricted()) {
                setNotification(1003);
            } else if (newRs.isCsEmergencyRestricted()) {
                setNotification(1006);
            } else if (newRs.isCsNormalRestricted()) {
                setNotification(1005);
            }
            this.mRestrictedState = newRs;
        }
        log("onRestrictedStateChanged: X rs " + this.mRestrictedState);
    }

    private int regCodeToServiceState(int code) {
        switch (code) {
            case 0:
            case 2:
            case 3:
            case 4:
            case 10:
            case 12:
            case 13:
            case 14:
                return 1;
            case 1:
                return 0;
            case 5:
                return 0;
            case 6:
            case 7:
            case 8:
            case 9:
            case 11:
            default:
                loge("regCodeToServiceState: unexpected service state " + code);
                return 1;
        }
    }

    private boolean regCodeIsRoaming(int code) {
        return 5 == code;
    }

    protected String getSystemProperty(String property, String defValue) {
        return SystemProperties.get(property, defValue);
    }

    private boolean isSameNamedOperators(ServiceState s) {
        String spn = getSystemProperty("gsm.sim.operator.alpha", "empty");
        String onsl = s.getOperatorAlphaLong();
        String onss = s.getOperatorAlphaShort();
        boolean equalsOnsl = onsl != null && spn.equals(onsl);
        boolean equalsOnss = onss != null && spn.equals(onss);
        return currentMccEqualsSimMcc(s) && (equalsOnsl || equalsOnss);
    }

    private boolean currentMccEqualsSimMcc(ServiceState s) {
        String simNumeric = getSystemProperty("gsm.sim.operator.numeric", "");
        String operatorNumeric = s.getOperatorNumeric();
        try {
            boolean equalsMcc = simNumeric.substring(0, 3).equals(operatorNumeric.substring(0, 3));
            return equalsMcc;
        } catch (Exception e) {
            return true;
        }
    }

    private boolean isOperatorConsideredNonRoaming(ServiceState s) {
        String operatorNumeric = s.getOperatorNumeric();
        String[] numericArray = this.mPhone.getContext().getResources().getStringArray(R.array.config_cameraPrivacyLightAlsLuxThresholds);
        if (numericArray.length == 0 || operatorNumeric == null) {
            return false;
        }
        for (String numeric : numericArray) {
            if (operatorNumeric.startsWith(numeric)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSameNamedOperatorConsideredRoaming(ServiceState s) {
        String operatorNumeric = s.getOperatorNumeric();
        String[] numericArray = this.mPhone.getContext().getResources().getStringArray(R.array.config_cameraPrivacyLightColors);
        if (numericArray.length == 0 || operatorNumeric == null) {
            return false;
        }
        for (String numeric : numericArray) {
            if (operatorNumeric.startsWith(numeric)) {
                return true;
            }
        }
        return false;
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    public int getCurrentDataConnectionState() {
        return this.mSS.getDataRegState();
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    public boolean isConcurrentVoiceAndDataAllowed() {
        return this.mSS.getRilDataRadioTechnology() >= 3 || this.mSS.getCssIndicator() == 1;
    }

    public CellLocation getCellLocation() {
        if (this.mCellLoc.getLac() >= 0 && this.mCellLoc.getCid() >= 0) {
            log("getCellLocation(): X good mCellLoc=" + this.mCellLoc);
            return this.mCellLoc;
        }
        List<CellInfo> result = getAllCellInfo();
        if (result != null) {
            GsmCellLocation cellLocOther = new GsmCellLocation();
            for (CellInfo ci : result) {
                if (ci instanceof CellInfoGsm) {
                    CellInfoGsm cellInfoGsm = (CellInfoGsm) ci;
                    CellIdentityGsm cellIdentityGsm = cellInfoGsm.getCellIdentity();
                    cellLocOther.setLacAndCid(cellIdentityGsm.getLac(), cellIdentityGsm.getCid());
                    cellLocOther.setPsc(cellIdentityGsm.getPsc());
                    log("getCellLocation(): X ret GSM info=" + cellLocOther);
                    return cellLocOther;
                }
                if (ci instanceof CellInfoWcdma) {
                    CellInfoWcdma cellInfoWcdma = (CellInfoWcdma) ci;
                    CellIdentityWcdma cellIdentityWcdma = cellInfoWcdma.getCellIdentity();
                    cellLocOther.setLacAndCid(cellIdentityWcdma.getLac(), cellIdentityWcdma.getCid());
                    cellLocOther.setPsc(cellIdentityWcdma.getPsc());
                    log("getCellLocation(): X ret WCDMA info=" + cellLocOther);
                    return cellLocOther;
                }
                if ((ci instanceof CellInfoLte) && (cellLocOther.getLac() < 0 || cellLocOther.getCid() < 0)) {
                    CellInfoLte cellInfoLte = (CellInfoLte) ci;
                    CellIdentityLte cellIdentityLte = cellInfoLte.getCellIdentity();
                    if (cellIdentityLte.getTac() != Integer.MAX_VALUE && cellIdentityLte.getCi() != Integer.MAX_VALUE) {
                        cellLocOther.setLacAndCid(cellIdentityLte.getTac(), cellIdentityLte.getCi());
                        cellLocOther.setPsc(0);
                        log("getCellLocation(): possible LTE cellLocOther=" + cellLocOther);
                    }
                }
            }
            log("getCellLocation(): X ret best answer cellLocOther=" + cellLocOther);
            return cellLocOther;
        }
        log("getCellLocation(): X empty mCellLoc and CellInfo mCellLoc=" + this.mCellLoc);
        return this.mCellLoc;
    }

    /* JADX WARN: Code duplicated, block: B:32:0x0182 A[Catch: RuntimeException -> 0x0250, TryCatch #1 {RuntimeException -> 0x0250, blocks: (B:3:0x004a, B:6:0x00e6, B:8:0x00fb, B:11:0x0107, B:13:0x0122, B:14:0x0132, B:16:0x0142, B:19:0x014c, B:23:0x0156, B:53:0x01f1, B:25:0x0166, B:27:0x0172, B:37:0x01a6, B:39:0x01ac, B:40:0x01b7, B:41:0x01c2, B:43:0x01ca, B:45:0x01d4, B:63:0x0246, B:70:0x02a9, B:75:0x033e, B:77:0x034a, B:78:0x0353, B:32:0x0182, B:35:0x0194, B:58:0x020d, B:60:0x021c, B:62:0x0228, B:69:0x0284, B:73:0x02b9, B:74:0x031e), top: B:80:0x004a, inners: #0 }] */
    /* JADX WARN: Code duplicated, block: B:34:0x0192  */
    /* JADX WARN: Code duplicated, block: B:57:0x020a  */
    private void setTimeFromNITZString(String nitz, long nitzReceiveTime) {
        boolean z;
        long start = SystemClock.elapsedRealtime();
        log("NITZ: " + nitz + "," + nitzReceiveTime + " start=" + start + " delay=" + (start - nitzReceiveTime));
        try {
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
            c.clear();
            c.set(16, 0);
            String[] nitzSubs = nitz.split("[/:,+-]");
            int year = Integer.parseInt(nitzSubs[0]) + 2000;
            c.set(1, year);
            int month = Integer.parseInt(nitzSubs[1]) - 1;
            c.set(2, month);
            int date = Integer.parseInt(nitzSubs[2]);
            c.set(5, date);
            int hour = Integer.parseInt(nitzSubs[3]);
            c.set(10, hour);
            int minute = Integer.parseInt(nitzSubs[4]);
            c.set(12, minute);
            int second = Integer.parseInt(nitzSubs[5]);
            c.set(13, second);
            boolean sign = nitz.indexOf(45) == -1;
            int tzOffset = Integer.parseInt(nitzSubs[6]);
            int dst = nitzSubs.length >= 8 ? Integer.parseInt(nitzSubs[7]) : 0;
            int tzOffset2 = (sign ? 1 : -1) * tzOffset * 15 * 60 * com.android.internal.telephony.cdma.CallFailCause.CDMA_LOCKED_UNTIL_POWER_CYCLE;
            TimeZone zone = null;
            if (nitzSubs.length >= 9) {
                String tzname = nitzSubs[8].replace('!', '/');
                zone = TimeZone.getTimeZone(tzname);
            }
            String iso = getSystemProperty("gsm.operator.iso-country", "");
            if (zone == null && this.mGotCountryCode) {
                if (iso != null && iso.length() > 0) {
                    zone = TimeUtils.getTimeZone(tzOffset2, dst != 0, c.getTimeInMillis(), iso);
                } else {
                    zone = getNitzTimeZone(tzOffset2, dst != 0, c.getTimeInMillis());
                }
            }
            if (zone != null && this.mZoneOffset == tzOffset2) {
                if (this.mZoneDst != (dst != 0)) {
                    this.mNeedFixZoneAfterNitz = true;
                    this.mZoneOffset = tzOffset2;
                    if (dst != 0) {
                        z = true;
                    } else {
                        z = false;
                    }
                    this.mZoneDst = z;
                    this.mZoneTime = c.getTimeInMillis();
                }
            } else {
                this.mNeedFixZoneAfterNitz = true;
                this.mZoneOffset = tzOffset2;
                if (dst != 0) {
                    z = true;
                } else {
                    z = false;
                }
                this.mZoneDst = z;
                this.mZoneTime = c.getTimeInMillis();
            }
            if (zone != null) {
                if (getAutoTimeZone()) {
                    setAndBroadcastNetworkSetTimeZone(zone.getID());
                }
                saveNitzTimeZone(zone.getID());
            }
            String ignore = SystemProperties.get("gsm.ignore-nitz");
            if (ignore != null && ignore.equals("yes")) {
                log("NITZ: Not setting clock because gsm.ignore-nitz is set");
                return;
            }
            try {
                this.mWakeLock.acquire();
                if (getAutoTime()) {
                    long millisSinceNitzReceived = SystemClock.elapsedRealtime() - nitzReceiveTime;
                    if (millisSinceNitzReceived < 0) {
                        log("NITZ: not setting time, clock has rolled backwards since NITZ time was received, " + nitz);
                        return;
                    } else {
                        if (millisSinceNitzReceived > 2147483647L) {
                            log("NITZ: not setting time, processing has taken " + (millisSinceNitzReceived / 86400000) + " days");
                            return;
                        }
                        c.add(14, (int) millisSinceNitzReceived);
                        log("NITZ: Setting time of day to " + c.getTime() + " NITZ receive delay(ms): " + millisSinceNitzReceived + " gained(ms): " + (c.getTimeInMillis() - System.currentTimeMillis()) + " from " + nitz);
                        setAndBroadcastNetworkSetTime(c.getTimeInMillis());
                        Rlog.i(LOG_TAG, "NITZ: after Setting time of day");
                    }
                }
                SystemProperties.set("gsm.nitz.time", String.valueOf(c.getTimeInMillis()));
                saveNitzTime(c.getTimeInMillis());
                this.mNitzUpdatedTime = true;
            } finally {
                this.mWakeLock.release();
            }
        } catch (RuntimeException ex) {
            loge("NITZ: Parsing NITZ time " + nitz + " ex=" + ex);
        }
    }

    private boolean getAutoTime() {
        try {
            return Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), "auto_time") > 0;
        } catch (Settings.SettingNotFoundException e) {
            return true;
        }
    }

    private boolean getAutoTimeZone() {
        try {
            return Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), "auto_time_zone") > 0;
        } catch (Settings.SettingNotFoundException e) {
            return true;
        }
    }

    private void saveNitzTimeZone(String zoneId) {
        this.mSavedTimeZone = zoneId;
    }

    private void saveNitzTime(long time) {
        this.mSavedTime = time;
        this.mSavedAtTime = SystemClock.elapsedRealtime();
    }

    private void setAndBroadcastNetworkSetTimeZone(String zoneId) {
        log("setAndBroadcastNetworkSetTimeZone: setTimeZone=" + zoneId);
        AlarmManager alarm = (AlarmManager) this.mPhone.getContext().getSystemService("alarm");
        alarm.setTimeZone(zoneId);
        Intent intent = new Intent("android.intent.action.NETWORK_SET_TIMEZONE");
        intent.addFlags(536870912);
        intent.putExtra("time-zone", zoneId);
        this.mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        log("setAndBroadcastNetworkSetTimeZone: call alarm.setTimeZone and broadcast zoneId=" + zoneId);
    }

    private void setAndBroadcastNetworkSetTime(long time) {
        log("setAndBroadcastNetworkSetTime: time=" + time + "ms");
        SystemClock.setCurrentTimeMillis(time);
        Intent intent = new Intent("android.intent.action.NETWORK_SET_TIME");
        intent.addFlags(536870912);
        intent.putExtra("time", time);
        this.mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void revertToNitzTime() {
        if (Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), "auto_time", 0) != 0) {
            log("Reverting to NITZ Time: mSavedTime=" + this.mSavedTime + " mSavedAtTime=" + this.mSavedAtTime);
            if (this.mSavedTime != 0 && this.mSavedAtTime != 0) {
                setAndBroadcastNetworkSetTime(this.mSavedTime + (SystemClock.elapsedRealtime() - this.mSavedAtTime));
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void revertToNitzTimeZone() {
        if (Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), "auto_time_zone", 0) != 0) {
            log("Reverting to NITZ TimeZone: tz='" + this.mSavedTimeZone);
            if (this.mSavedTimeZone != null) {
                setAndBroadcastNetworkSetTimeZone(this.mSavedTimeZone);
            }
        }
    }

    private void setNotification(int notifyType) {
        log("setNotification: create notification " + notifyType);
        Context context = this.mPhone.getContext();
        this.mNotification = new Notification();
        this.mNotification.when = System.currentTimeMillis();
        this.mNotification.flags = 16;
        this.mNotification.icon = R.drawable.stat_sys_warning;
        Intent intent = new Intent();
        this.mNotification.contentIntent = PendingIntent.getActivity(context, 0, intent, 268435456);
        CharSequence details = "";
        CharSequence title = context.getText(R.string.PERSOSUBSTATE_RUIM_NETWORK2_PUK_ENTRY);
        int notificationId = CS_NOTIFICATION;
        switch (notifyType) {
            case 1001:
                notificationId = PS_NOTIFICATION;
                details = context.getText(R.string.PERSOSUBSTATE_RUIM_NETWORK2_PUK_ERROR);
                break;
            case 1002:
                notificationId = PS_NOTIFICATION;
                break;
            case 1003:
                details = context.getText(R.string.PERSOSUBSTATE_RUIM_NETWORK2_SUCCESS);
                break;
            case 1005:
                details = context.getText(R.string.PERSOSUBSTATE_RUIM_NETWORK2_PUK_SUCCESS);
                break;
            case 1006:
                details = context.getText(R.string.PERSOSUBSTATE_RUIM_NETWORK2_PUK_IN_PROGRESS);
                break;
        }
        log("setNotification: put notification " + ((Object) title) + " / " + ((Object) details));
        this.mNotification.tickerText = title;
        this.mNotification.setLatestEventInfo(context, title, details, this.mNotification.contentIntent);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService("notification");
        if (notifyType == 1002 || notifyType == 1004) {
            notificationManager.cancel(notificationId);
        } else {
            notificationManager.notify(notificationId, this.mNotification);
        }
    }

    protected UiccCardApplication getUiccCardApplication() {
        return this.mUiccController.getUiccCardApplication(1);
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    protected void onUpdateIccAvailability() {
        UiccCardApplication newUiccApplication;
        if (this.mUiccController != null && this.mUiccApplcation != (newUiccApplication = getUiccCardApplication())) {
            if (this.mUiccApplcation != null) {
                log("Removing stale icc objects.");
                this.mUiccApplcation.unregisterForReady(this);
                if (this.mIccRecords != null) {
                    this.mIccRecords.unregisterForRecordsLoaded(this);
                }
                this.mIccRecords = null;
                this.mUiccApplcation = null;
            }
            if (newUiccApplication != null) {
                log("New card found");
                this.mUiccApplcation = newUiccApplication;
                this.mIccRecords = this.mUiccApplcation.getIccRecords();
                this.mUiccApplcation.registerForReady(this, 17, null);
                if (this.mIccRecords != null) {
                    this.mIccRecords.registerForRecordsLoaded(this, 16, null);
                }
            }
        }
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    protected void log(String s) {
        Rlog.d(LOG_TAG, "[GsmSST] " + s);
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    protected void loge(String s) {
        Rlog.e(LOG_TAG, "[GsmSST] " + s);
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("GsmServiceStateTracker extends:");
        super.dump(fd, pw, args);
        pw.println(" mPhone=" + this.mPhone);
        pw.println(" mSS=" + this.mSS);
        pw.println(" mNewSS=" + this.mNewSS);
        pw.println(" mCellLoc=" + this.mCellLoc);
        pw.println(" mNewCellLoc=" + this.mNewCellLoc);
        pw.println(" mPreferredNetworkType=" + this.mPreferredNetworkType);
        pw.println(" mMaxDataCalls=" + this.mMaxDataCalls);
        pw.println(" mNewMaxDataCalls=" + this.mNewMaxDataCalls);
        pw.println(" mReasonDataDenied=" + this.mReasonDataDenied);
        pw.println(" mNewReasonDataDenied=" + this.mNewReasonDataDenied);
        pw.println(" mGsmRoaming=" + this.mGsmRoaming);
        pw.println(" mDataRoaming=" + this.mDataRoaming);
        pw.println(" mEmergencyOnly=" + this.mEmergencyOnly);
        pw.println(" mNeedFixZoneAfterNitz=" + this.mNeedFixZoneAfterNitz);
        pw.println(" mZoneOffset=" + this.mZoneOffset);
        pw.println(" mZoneDst=" + this.mZoneDst);
        pw.println(" mZoneTime=" + this.mZoneTime);
        pw.println(" mGotCountryCode=" + this.mGotCountryCode);
        pw.println(" mNitzUpdatedTime=" + this.mNitzUpdatedTime);
        pw.println(" mSavedTimeZone=" + this.mSavedTimeZone);
        pw.println(" mSavedTime=" + this.mSavedTime);
        pw.println(" mSavedAtTime=" + this.mSavedAtTime);
        pw.println(" mStartedGprsRegCheck=" + this.mStartedGprsRegCheck);
        pw.println(" mReportedGprsNoReg=" + this.mReportedGprsNoReg);
        pw.println(" mNotification=" + this.mNotification);
        pw.println(" mWakeLock=" + this.mWakeLock);
        pw.println(" mCurSpn=" + this.mCurSpn);
        pw.println(" mCurShowSpn=" + this.mCurShowSpn);
        pw.println(" mCurPlmn=" + this.mCurPlmn);
        pw.println(" mCurShowPlmn=" + this.mCurShowPlmn);
    }
}
