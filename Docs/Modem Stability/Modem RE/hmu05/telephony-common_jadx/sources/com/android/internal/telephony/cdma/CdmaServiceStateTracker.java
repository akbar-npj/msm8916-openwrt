package com.android.internal.telephony.cdma;

import android.R;
import android.app.AlarmManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.cdma.CdmaCellLocation;
import android.text.TextUtils;
import android.util.EventLog;
import android.util.TimeUtils;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.dataconnection.DcTrackerBase;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.RuimRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/* JADX INFO: loaded from: classes.dex */
public class CdmaServiceStateTracker extends ServiceStateTracker {
    static final String LOG_TAG = "CdmaSST";
    private static final int NITZ_UPDATE_DIFF_DEFAULT = 2000;
    private static final int NITZ_UPDATE_SPACING_DEFAULT = 600000;
    private static final String UNACTIVATED_MIN2_VALUE = "000000";
    private static final String UNACTIVATED_MIN_VALUE = "1111110111";
    private static final String WAKELOCK_TAG = "ServiceStateTracker";
    private boolean isRegisteredForReady;
    private boolean isRegisteredForRecordsLoaded;
    private ContentObserver mAutoTimeObserver;
    private ContentObserver mAutoTimeZoneObserver;
    protected RegistrantList mCdmaForSubscriptionInfoReadyRegistrants;
    private boolean mCdmaRoaming;
    private CdmaSubscriptionSourceManager mCdmaSSM;
    CdmaCellLocation mCellLoc;
    private ContentResolver mCr;
    protected String mCurPlmn;
    private String mCurrentCarrier;
    int mCurrentOtaspMode;
    protected boolean mDataRoaming;
    private int mDefaultRoamingIndicator;
    protected boolean mGotCountryCode;
    protected int[] mHomeNetworkId;
    protected int[] mHomeSystemId;
    private boolean mIsEriTextLoaded;
    private boolean mIsInPrl;
    protected boolean mIsMinInfoReady;
    protected boolean mIsSubscriptionFromRuim;
    protected String mMdn;
    protected String mMin;
    protected boolean mNeedFixZone;
    CdmaCellLocation mNewCellLoc;
    private int mNitzUpdateDiff;
    private int mNitzUpdateSpacing;
    protected CDMAPhone mPhone;
    protected String mPrlVersion;
    private String mRegistrationDeniedReason;
    protected int mRegistrationState;
    private int mRoamingIndicator;
    long mSavedAtTime;
    long mSavedTime;
    String mSavedTimeZone;
    private PowerManager.WakeLock mWakeLock;
    private boolean mZoneDst;
    private int mZoneOffset;
    private long mZoneTime;

    public CdmaServiceStateTracker(CDMAPhone phone) {
        this(phone, new CellInfoCdma());
    }

    protected CdmaServiceStateTracker(CDMAPhone phone, CellInfo cellInfo) {
        super(phone, phone.mCi, cellInfo);
        this.mCurrentOtaspMode = 0;
        this.mNitzUpdateSpacing = SystemProperties.getInt("ro.nitz_update_spacing", NITZ_UPDATE_SPACING_DEFAULT);
        this.mNitzUpdateDiff = SystemProperties.getInt("ro.nitz_update_diff", NITZ_UPDATE_DIFF_DEFAULT);
        this.mCdmaRoaming = false;
        this.mDataRoaming = false;
        this.mRoamingIndicator = 1;
        this.mDefaultRoamingIndicator = 1;
        this.mRegistrationState = -1;
        this.mCdmaForSubscriptionInfoReadyRegistrants = new RegistrantList();
        this.mNeedFixZone = false;
        this.mGotCountryCode = false;
        this.mCurPlmn = null;
        this.mHomeSystemId = null;
        this.mHomeNetworkId = null;
        this.mIsMinInfoReady = false;
        this.mIsEriTextLoaded = false;
        this.mIsSubscriptionFromRuim = false;
        this.mCurrentCarrier = null;
        this.isRegisteredForRecordsLoaded = false;
        this.isRegisteredForReady = false;
        this.mAutoTimeObserver = new ContentObserver(new Handler()) { // from class: com.android.internal.telephony.cdma.CdmaServiceStateTracker.1
            @Override // android.database.ContentObserver
            public void onChange(boolean selfChange) {
                CdmaServiceStateTracker.this.log("Auto time state changed");
                CdmaServiceStateTracker.this.revertToNitzTime();
            }
        };
        this.mAutoTimeZoneObserver = new ContentObserver(new Handler()) { // from class: com.android.internal.telephony.cdma.CdmaServiceStateTracker.2
            @Override // android.database.ContentObserver
            public void onChange(boolean selfChange) {
                CdmaServiceStateTracker.this.log("Auto time zone state changed");
                CdmaServiceStateTracker.this.revertToNitzTimeZone();
            }
        };
        this.mPhone = phone;
        this.mCr = phone.getContext().getContentResolver();
        this.mCellLoc = new CdmaCellLocation();
        this.mNewCellLoc = new CdmaCellLocation();
        this.mCdmaSSM = CdmaSubscriptionSourceManager.getInstance(phone.getContext(), this.mCi, this, 39, null);
        this.mIsSubscriptionFromRuim = this.mCdmaSSM.getCdmaSubscriptionSource() == 0;
        PowerManager powerManager = (PowerManager) phone.getContext().getSystemService("power");
        this.mWakeLock = powerManager.newWakeLock(1, WAKELOCK_TAG);
        this.mCi.registerForRadioStateChanged(this, 1, null);
        this.mCi.registerForVoiceNetworkStateChanged(this, 30, null);
        this.mCi.setOnNITZTime(this, 11, null);
        this.mCi.registerForCdmaPrlChanged(this, 40, null);
        phone.registerForEriFileLoaded(this, 36, null);
        this.mCi.registerForCdmaOtaProvision(this, 37, null);
        int airplaneMode = Settings.Global.getInt(this.mCr, "airplane_mode_on", 0);
        this.mDesiredPowerState = airplaneMode <= 0;
        this.mCr.registerContentObserver(Settings.Global.getUriFor("auto_time"), true, this.mAutoTimeObserver);
        this.mCr.registerContentObserver(Settings.Global.getUriFor("auto_time_zone"), true, this.mAutoTimeZoneObserver);
        setSignalStrengthDefaultValues();
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    public void dispose() {
        checkCorrectThread();
        log("ServiceStateTracker dispose");
        this.mCi.unregisterForRadioStateChanged(this);
        this.mCi.unregisterForVoiceNetworkStateChanged(this);
        this.mCi.unregisterForCdmaOtaProvision(this);
        this.mPhone.unregisterForEriFileLoaded(this);
        if (this.mUiccApplcation != null) {
            this.mUiccApplcation.unregisterForReady(this);
            this.isRegisteredForReady = false;
        }
        if (this.mIccRecords != null) {
            this.mIccRecords.unregisterForRecordsLoaded(this);
            this.isRegisteredForRecordsLoaded = false;
        }
        this.mCi.unSetOnNITZTime(this);
        this.mCr.unregisterContentObserver(this.mAutoTimeObserver);
        this.mCr.unregisterContentObserver(this.mAutoTimeZoneObserver);
        this.mCdmaSSM.dispose(this);
        this.mCi.unregisterForCdmaPrlChanged(this);
        super.dispose();
    }

    protected void finalize() {
        log("CdmaServiceStateTracker finalized");
    }

    public void registerForSubscriptionInfoReady(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mCdmaForSubscriptionInfoReadyRegistrants.add(r);
        if (isMinInfoReady()) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForSubscriptionInfoReady(Handler h) {
        this.mCdmaForSubscriptionInfoReadyRegistrants.remove(h);
    }

    private void saveCdmaSubscriptionSource(int source) {
        log("Storing cdma subscription source: " + source);
        Settings.Global.putInt(this.mPhone.getContext().getContentResolver(), "subscription_mode", source);
    }

    private void getSubscriptionInfoAndStartPollingThreads() {
        this.mCi.getCDMASubscription(obtainMessage(34));
        pollState();
    }

    @Override // com.android.internal.telephony.ServiceStateTracker, android.os.Handler
    public void handleMessage(Message msg) {
        if (!this.mPhone.mIsTheCurrentActivePhone) {
            loge("Received message " + msg + "[" + msg.what + "] while being destroyed. Ignoring.");
        }
        switch (msg.what) {
            case 1:
                if (this.mCi.getRadioState() == CommandsInterface.RadioState.RADIO_ON) {
                    handleCdmaSubscriptionSource(this.mCdmaSSM.getCdmaSubscriptionSource());
                    queueNextSignalStrengthPoll();
                }
                setPowerStateToDesired();
                pollState();
                break;
            case 2:
            case 4:
            case 6:
            case 7:
            case 8:
            case 9:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
            case 19:
            case 20:
            case 21:
            case 22:
            case 23:
            case 28:
            case IccRecords.EVENT_REFRESH_OEM /* 29 */:
            case 32:
            case SmsHeader.ELT_ID_HYPERLINK_FORMAT_ELEMENT /* 33 */:
            case 38:
            default:
                super.handleMessage(msg);
                break;
            case 3:
                if (this.mCi.getRadioState().isOn()) {
                    onSignalStrengthResult((AsyncResult) msg.obj, false);
                    queueNextSignalStrengthPoll();
                }
                break;
            case 5:
            case SmsHeader.ELT_ID_STANDARD_WVG_OBJECT /* 24 */:
            case SmsHeader.ELT_ID_CHARACTER_SIZE_WVG_OBJECT /* 25 */:
                handlePollStateResult(msg.what, (AsyncResult) msg.obj);
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
                onSignalStrengthResult(ar2, false);
                break;
            case 18:
                if (((AsyncResult) msg.obj).exception == null) {
                    this.mCi.getVoiceRegistrationState(obtainMessage(31, null));
                }
                break;
            case SmsHeader.ELT_ID_EXTENDED_OBJECT_DATA_REQUEST_CMD /* 26 */:
                log("Receive EVENT_RUIM_READY");
                pollState();
                this.mPhone.prepareEri();
                break;
            case 27:
                log("EVENT_RUIM_RECORDS_LOADED: what=" + msg.what);
                updatePhoneObject();
                RuimRecords ruim = (RuimRecords) this.mIccRecords;
                if (ruim != null && ruim.isProvisioned()) {
                    this.mMdn = ruim.getMdn();
                    this.mMin = ruim.getMin();
                    parseSidNid(ruim.getSid(), ruim.getNid());
                    this.mPrlVersion = ruim.getPrlVersion();
                    this.mIsMinInfoReady = true;
                    updateOtaspState();
                }
                getSubscriptionInfoAndStartPollingThreads();
                break;
            case com.android.internal.telephony.gsm.CallFailCause.STATUS_ENQUIRY /* 30 */:
                pollState();
                break;
            case 31:
                AsyncResult ar3 = (AsyncResult) msg.obj;
                if (ar3.exception == null) {
                    String[] states = (String[]) ar3.result;
                    int baseStationId = -1;
                    int baseStationLatitude = Integer.MAX_VALUE;
                    int baseStationLongitude = Integer.MAX_VALUE;
                    int systemId = -1;
                    int networkId = -1;
                    if (states.length > 9) {
                        try {
                            if (states[4] != null) {
                                baseStationId = Integer.parseInt(states[4]);
                            }
                            if (states[5] != null) {
                                baseStationLatitude = Integer.parseInt(states[5]);
                            }
                            if (states[6] != null) {
                                baseStationLongitude = Integer.parseInt(states[6]);
                            }
                            if (baseStationLatitude == 0 && baseStationLongitude == 0) {
                                baseStationLatitude = Integer.MAX_VALUE;
                                baseStationLongitude = Integer.MAX_VALUE;
                            }
                            if (states[8] != null) {
                                systemId = Integer.parseInt(states[8]);
                            }
                            if (states[9] != null) {
                                networkId = Integer.parseInt(states[9]);
                            }
                        } catch (NumberFormatException ex) {
                            loge("error parsing cell location data: " + ex);
                        }
                    }
                    this.mCellLoc.setCellLocationData(baseStationId, baseStationLatitude, baseStationLongitude, systemId, networkId);
                    this.mPhone.notifyLocationChanged();
                }
                disableSingleLocationUpdate();
                break;
            case 34:
                AsyncResult ar4 = (AsyncResult) msg.obj;
                if (ar4.exception == null) {
                    String[] cdmaSubscription = (String[]) ar4.result;
                    if (cdmaSubscription != null && cdmaSubscription.length >= 5) {
                        if (cdmaSubscription[0] != null) {
                            this.mMdn = cdmaSubscription[0];
                        }
                        parseSidNid(cdmaSubscription[1], cdmaSubscription[2]);
                        if (cdmaSubscription[3] != null) {
                            this.mMin = cdmaSubscription[3];
                        }
                        if (cdmaSubscription[4] != null) {
                            this.mPrlVersion = cdmaSubscription[4];
                        }
                        log("GET_CDMA_SUBSCRIPTION: MDN=" + this.mMdn);
                        this.mIsMinInfoReady = true;
                        updateOtaspState();
                    } else {
                        log("GET_CDMA_SUBSCRIPTION: error parsing cdmaSubscription params num=" + cdmaSubscription.length);
                    }
                }
                break;
            case 35:
                updatePhoneObject();
                getSubscriptionInfoAndStartPollingThreads();
                break;
            case 36:
                log("[CdmaServiceStateTracker] ERI file has been loaded, repolling.");
                pollState();
                break;
            case SmsHeader.ELT_ID_NATIONAL_LANGUAGE_LOCKING_SHIFT /* 37 */:
                AsyncResult ar5 = (AsyncResult) msg.obj;
                if (ar5.exception == null) {
                    int[] ints = (int[]) ar5.result;
                    int otaStatus = ints[0];
                    if (otaStatus == 8 || otaStatus == 10) {
                        log("EVENT_OTA_PROVISION_STATUS_CHANGE: Complete, Reload MDN");
                        this.mCi.getCDMASubscription(obtainMessage(34));
                    }
                }
                break;
            case CommandsInterface.CDMA_SMS_FAIL_CAUSE_OTHER_TERMINAL_PROBLEM /* 39 */:
                handleCdmaSubscriptionSource(this.mCdmaSSM.getCdmaSubscriptionSource());
                break;
            case 40:
                AsyncResult ar6 = (AsyncResult) msg.obj;
                if (ar6.exception == null) {
                    int[] ints2 = (int[]) ar6.result;
                    this.mPrlVersion = Integer.toString(ints2[0]);
                }
                break;
        }
    }

    private void handleCdmaSubscriptionSource(int newSubscriptionSource) {
        log("Subscription Source : " + newSubscriptionSource);
        this.mIsSubscriptionFromRuim = newSubscriptionSource == 0;
        saveCdmaSubscriptionSource(newSubscriptionSource);
        if (!this.mIsSubscriptionFromRuim) {
            sendMessage(obtainMessage(35));
        } else {
            registerForRuimEvents();
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
    protected void updateSpnDisplay() {
        String plmn = this.mSS.getOperatorAlphaLong();
        int combinedRegState = getCombinedRegState();
        if (combinedRegState == 1) {
            plmn = Resources.getSystem().getText(R.string.conversation_title_fallback_one_to_one).toString();
            log("updateSpnDisplay: radio is on but out of service, set plmn='" + plmn + "'");
        }
        if (!TextUtils.equals(plmn, this.mCurPlmn)) {
            boolean showPlmn = plmn != null;
            log(String.format("updateSpnDisplay: changed sending intent showPlmn='%b' plmn='%s'", Boolean.valueOf(showPlmn), plmn));
            Intent intent = new Intent("android.provider.Telephony.SPN_STRINGS_UPDATED");
            intent.addFlags(536870912);
            intent.putExtra("showSpn", false);
            intent.putExtra("spn", "");
            intent.putExtra("showPlmn", showPlmn);
            intent.putExtra(Telephony.CellBroadcasts.PLMN, plmn);
            this.mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        }
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
    protected Phone getPhone() {
        return this.mPhone;
    }

    protected String getSystemProperty(String property, String defValue) {
        return SystemProperties.get(property, defValue);
    }

    protected void handlePollStateResultMessage(int what, AsyncResult ar) {
        switch (what) {
            case 5:
                String[] states = (String[]) ar.result;
                log("handlePollStateResultMessage: EVENT_POLL_STATE_GPRS states.length=" + states.length + " states=" + states);
                int regState = 4;
                int dataRadioTechnology = 0;
                if (states.length > 0) {
                    try {
                        regState = Integer.parseInt(states[0]);
                        if (states.length >= 4 && states[3] != null) {
                            dataRadioTechnology = Integer.parseInt(states[3]);
                        }
                    } catch (NumberFormatException ex) {
                        loge("handlePollStateResultMessage: error parsing GprsRegistrationState: " + ex);
                    }
                    break;
                }
                int dataRegState = regCodeToServiceState(regState);
                this.mNewSS.setDataRegState(dataRegState);
                this.mNewSS.setRilDataRadioTechnology(dataRadioTechnology);
                log("handlPollStateResultMessage: cdma setDataRegState=" + dataRegState + " regState=" + regState + " dataRadioTechnology=" + dataRadioTechnology);
                return;
            case SmsHeader.ELT_ID_STANDARD_WVG_OBJECT /* 24 */:
                String[] states2 = (String[]) ar.result;
                int registrationState = 4;
                int radioTechnology = -1;
                int baseStationId = -1;
                int baseStationLatitude = Integer.MAX_VALUE;
                int baseStationLongitude = Integer.MAX_VALUE;
                int cssIndicator = 0;
                int systemId = 0;
                int networkId = 0;
                int roamingIndicator = 1;
                int systemIsInPrl = 0;
                int defaultRoamingIndicator = 1;
                int reasonForDenial = 0;
                if (states2.length >= 14) {
                    try {
                        if (states2[0] != null) {
                            registrationState = Integer.parseInt(states2[0]);
                        }
                        if (states2[3] != null) {
                            radioTechnology = Integer.parseInt(states2[3]);
                        }
                        if (states2[4] != null) {
                            baseStationId = Integer.parseInt(states2[4]);
                        }
                        if (states2[5] != null) {
                            baseStationLatitude = Integer.parseInt(states2[5]);
                        }
                        if (states2[6] != null) {
                            baseStationLongitude = Integer.parseInt(states2[6]);
                        }
                        if (baseStationLatitude == 0 && baseStationLongitude == 0) {
                            baseStationLatitude = Integer.MAX_VALUE;
                            baseStationLongitude = Integer.MAX_VALUE;
                        }
                        if (states2[7] != null) {
                            cssIndicator = Integer.parseInt(states2[7]);
                        }
                        if (states2[8] != null) {
                            systemId = Integer.parseInt(states2[8]);
                        }
                        if (states2[9] != null) {
                            networkId = Integer.parseInt(states2[9]);
                        }
                        if (states2[10] != null) {
                            roamingIndicator = Integer.parseInt(states2[10]);
                        }
                        if (states2[11] != null) {
                            systemIsInPrl = Integer.parseInt(states2[11]);
                        }
                        if (states2[12] != null) {
                            defaultRoamingIndicator = Integer.parseInt(states2[12]);
                        }
                        if (states2[13] != null) {
                            reasonForDenial = Integer.parseInt(states2[13]);
                        }
                        break;
                    } catch (NumberFormatException ex2) {
                        loge("EVENT_POLL_STATE_REGISTRATION_CDMA: error parsing: " + ex2);
                    }
                    this.mRegistrationState = registrationState;
                    this.mCdmaRoaming = regCodeIsRoaming(registrationState);
                    this.mNewSS.setState(regCodeToServiceState(registrationState));
                    this.mNewSS.setRilVoiceRadioTechnology(radioTechnology);
                    this.mNewSS.setCssIndicator(cssIndicator);
                    this.mNewSS.setSystemAndNetworkId(systemId, networkId);
                    this.mRoamingIndicator = roamingIndicator;
                    this.mIsInPrl = systemIsInPrl != 0;
                    this.mDefaultRoamingIndicator = defaultRoamingIndicator;
                    this.mNewCellLoc.setCellLocationData(baseStationId, baseStationLatitude, baseStationLongitude, systemId, networkId);
                    if (reasonForDenial == 0) {
                        this.mRegistrationDeniedReason = "General";
                    } else if (reasonForDenial == 1) {
                        this.mRegistrationDeniedReason = "Authentication Failure";
                    } else {
                        this.mRegistrationDeniedReason = "";
                    }
                    if (this.mRegistrationState == 3) {
                        log("Registration denied, " + this.mRegistrationDeniedReason);
                        return;
                    }
                    return;
                }
                throw new RuntimeException("Warning! Wrong number of parameters returned from RIL_REQUEST_REGISTRATION_STATE: expected 14 or more strings and got " + states2.length + " strings");
            case SmsHeader.ELT_ID_CHARACTER_SIZE_WVG_OBJECT /* 25 */:
                String[] opNames = (String[]) ar.result;
                if (opNames != null && opNames.length >= 3) {
                    if (opNames[2] == null || opNames[2].length() < 5 || "00000".equals(opNames[2])) {
                        opNames[2] = SystemProperties.get(CDMAPhone.PROPERTY_CDMA_HOME_OPERATOR_NUMERIC, "00000");
                        log("RIL_REQUEST_OPERATOR.response[2], the numeric,  is bad. Using SystemProperties 'ro.cdma.home.operator.numeric'= " + opNames[2]);
                    }
                    if (!this.mIsSubscriptionFromRuim) {
                        this.mNewSS.setOperatorName(null, opNames[1], opNames[2]);
                        return;
                    } else {
                        this.mNewSS.setOperatorName(opNames[0], opNames[1], opNames[2]);
                        return;
                    }
                }
                log("EVENT_POLL_STATE_OPERATOR_CDMA: error parsing opNames");
                return;
            default:
                loge("handlePollStateResultMessage: RIL response handle in wrong phone! Expected CDMA RIL request and get GSM RIL request.");
                return;
        }
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
                    loge("handlePollStateResult: RIL returned an error where it must succeed" + ar.exception);
                }
            } else {
                try {
                    handlePollStateResultMessage(what, ar);
                } catch (RuntimeException ex) {
                    loge("handlePollStateResult: Exception while polling service state. Probably malformed RIL response." + ex);
                }
            }
            int[] iArr = this.mPollingContext;
            iArr[0] = iArr[0] - 1;
            if (this.mPollingContext[0] == 0) {
                boolean namMatch = false;
                if (!isSidsAllZeros() && isHomeSid(this.mNewSS.getSystemId())) {
                    namMatch = true;
                }
                this.mCdmaRoaming = (this.mCdmaRoaming || this.mDataRoaming) && !isRoamIndForHomeSystem(String.valueOf(this.mRoamingIndicator));
                if (this.mIsSubscriptionFromRuim) {
                    this.mNewSS.setRoaming(isRoamingBetweenOperators(this.mCdmaRoaming, this.mNewSS));
                } else {
                    this.mNewSS.setRoaming(this.mCdmaRoaming);
                }
                this.mNewSS.setCdmaDefaultRoamingIndicator(this.mDefaultRoamingIndicator);
                this.mNewSS.setCdmaRoamingIndicator(this.mRoamingIndicator);
                boolean isPrlLoaded = true;
                if (TextUtils.isEmpty(this.mPrlVersion)) {
                    isPrlLoaded = false;
                }
                if (!isPrlLoaded || this.mNewSS.getRilVoiceRadioTechnology() == 0) {
                    log("Turn off roaming indicator if !isPrlLoaded or voice RAT is unknown");
                    this.mNewSS.setCdmaRoamingIndicator(1);
                } else if (!isSidsAllZeros()) {
                    if (!namMatch && !this.mIsInPrl) {
                        this.mNewSS.setCdmaRoamingIndicator(this.mDefaultRoamingIndicator);
                    } else if (namMatch && !this.mIsInPrl) {
                        if (this.mNewSS.getRilVoiceRadioTechnology() == 14) {
                            log("Turn off roaming indicator as voice is LTE");
                            this.mNewSS.setCdmaRoamingIndicator(1);
                        } else {
                            this.mNewSS.setCdmaRoamingIndicator(2);
                        }
                    } else if ((namMatch || !this.mIsInPrl) && this.mRoamingIndicator <= 2) {
                        this.mNewSS.setCdmaRoamingIndicator(1);
                    } else {
                        this.mNewSS.setCdmaRoamingIndicator(this.mRoamingIndicator);
                    }
                }
                int roamingIndicator = this.mNewSS.getCdmaRoamingIndicator();
                this.mNewSS.setCdmaEriIconIndex(this.mPhone.mEriManager.getCdmaEriIconIndex(roamingIndicator, this.mDefaultRoamingIndicator));
                this.mNewSS.setCdmaEriIconMode(this.mPhone.mEriManager.getCdmaEriIconMode(roamingIndicator, this.mDefaultRoamingIndicator));
                log("Set CDMA Roaming Indicator to: " + this.mNewSS.getCdmaRoamingIndicator() + ". mCdmaRoaming = " + this.mCdmaRoaming + ", isPrlLoaded = " + isPrlLoaded + ". namMatch = " + namMatch + " , mIsInPrl = " + this.mIsInPrl + ", mRoamingIndicator = " + this.mRoamingIndicator + ", mDefaultRoamingIndicator= " + this.mDefaultRoamingIndicator);
                pollStateDone();
            }
        }
    }

    protected void setSignalStrengthDefaultValues() {
        this.mSignalStrength = new SignalStrength(false);
    }

    protected void pollState() {
        this.mPollingContext = new int[1];
        this.mPollingContext[0] = 0;
        switch (this.mCi.getRadioState()) {
            case RADIO_UNAVAILABLE:
                this.mNewSS.setStateOutOfService();
                this.mNewCellLoc.setStateInvalid();
                setSignalStrengthDefaultValues();
                this.mGotCountryCode = false;
                pollStateDone();
                return;
            case RADIO_OFF:
                this.mNewSS.setStateOff();
                this.mNewCellLoc.setStateInvalid();
                setSignalStrengthDefaultValues();
                this.mGotCountryCode = false;
                pollStateDone();
                if (!isIwlanFeatureAvailable()) {
                    return;
                }
                break;
        }
        int[] iArr = this.mPollingContext;
        iArr[0] = iArr[0] + 1;
        this.mCi.getOperator(obtainMessage(25, this.mPollingContext));
        int[] iArr2 = this.mPollingContext;
        iArr2[0] = iArr2[0] + 1;
        this.mCi.getVoiceRegistrationState(obtainMessage(24, this.mPollingContext));
        int[] iArr3 = this.mPollingContext;
        iArr3[0] = iArr3[0] + 1;
        this.mCi.getDataRegistrationState(obtainMessage(5, this.mPollingContext));
    }

    protected void fixTimeZone(String isoCountryCode) {
        TimeZone zone;
        String zoneName = SystemProperties.get("persist.sys.timezone");
        log("fixTimeZone zoneName='" + zoneName + "' mZoneOffset=" + this.mZoneOffset + " mZoneDst=" + this.mZoneDst + " iso-cc='" + isoCountryCode + "' iso-cc-idx=" + Arrays.binarySearch(GMT_COUNTRY_CODES, isoCountryCode));
        if (this.mZoneOffset == 0 && !this.mZoneDst && zoneName != null && zoneName.length() > 0 && Arrays.binarySearch(GMT_COUNTRY_CODES, isoCountryCode) < 0) {
            zone = TimeZone.getDefault();
            if (this.mNeedFixZone) {
                long ctm = System.currentTimeMillis();
                long tzOffset = zone.getOffset(ctm);
                log("fixTimeZone: tzOffset=" + tzOffset + " ltod=" + TimeUtils.logTimeOfDay(ctm));
                if (getAutoTime()) {
                    long adj = ctm - tzOffset;
                    log("fixTimeZone: adj ltod=" + TimeUtils.logTimeOfDay(adj));
                    setAndBroadcastNetworkSetTime(adj);
                } else {
                    this.mSavedTime -= tzOffset;
                    log("fixTimeZone: adj mSavedTime=" + this.mSavedTime);
                }
            }
            log("fixTimeZone: using default TimeZone");
        } else if (isoCountryCode.equals("")) {
            zone = getNitzTimeZone(this.mZoneOffset, this.mZoneDst, this.mZoneTime);
            log("fixTimeZone: using NITZ TimeZone");
        } else {
            zone = TimeUtils.getTimeZone(this.mZoneOffset, this.mZoneDst, this.mZoneTime, isoCountryCode);
            log("fixTimeZone: using getTimeZone(off, dst, time, iso)");
        }
        this.mNeedFixZone = false;
        if (zone != null) {
            log("fixTimeZone: zone != null zone.getID=" + zone.getID());
            if (getAutoTimeZone()) {
                setAndBroadcastNetworkSetTimeZone(zone.getID());
            } else {
                log("fixTimeZone: skip changing zone as getAutoTimeZone was false");
            }
            saveNitzTimeZone(zone.getID());
            return;
        }
        log("fixTimeZone: zone == null, do nothing for zone");
    }

    protected void pollStateDone() {
        String eriText;
        log("pollStateDone: cdma oldSS=[" + this.mSS + "] newSS=[" + this.mNewSS + "]");
        if (Build.IS_DEBUGGABLE && SystemProperties.getBoolean("telephony.test.forceRoaming", false)) {
            this.mNewSS.setRoaming(true);
        }
        useDataRegStateForDataOnlyDevices();
        boolean hasRegistered = this.mSS.getVoiceRegState() != 0 && this.mNewSS.getVoiceRegState() == 0;
        if (this.mSS.getVoiceRegState() != 0 || this.mNewSS.getVoiceRegState() != 0) {
        }
        boolean hasCdmaDataConnectionAttached = this.mSS.getDataRegState() != 0 && this.mNewSS.getDataRegState() == 0;
        boolean hasCdmaDataConnectionDetached = this.mSS.getDataRegState() == 0 && this.mNewSS.getDataRegState() != 0;
        boolean hasCdmaDataConnectionChanged = this.mSS.getDataRegState() != this.mNewSS.getDataRegState();
        boolean hasRilVoiceRadioTechnologyChanged = this.mSS.getRilVoiceRadioTechnology() != this.mNewSS.getRilVoiceRadioTechnology();
        boolean hasRilDataRadioTechnologyChanged = this.mSS.getRilDataRadioTechnology() != this.mNewSS.getRilDataRadioTechnology();
        boolean hasChanged = !this.mNewSS.equals(this.mSS);
        boolean hasRoamingOn = !this.mSS.getRoaming() && this.mNewSS.getRoaming();
        boolean hasRoamingOff = this.mSS.getRoaming() && !this.mNewSS.getRoaming();
        boolean hasLocationChanged = !this.mNewCellLoc.equals(this.mCellLoc);
        if (this.mSS.getVoiceRegState() != this.mNewSS.getVoiceRegState() || this.mSS.getDataRegState() != this.mNewSS.getDataRegState()) {
            EventLog.writeEvent(EventLogTags.CDMA_SERVICE_STATE_CHANGE, Integer.valueOf(this.mSS.getVoiceRegState()), Integer.valueOf(this.mSS.getDataRegState()), Integer.valueOf(this.mNewSS.getVoiceRegState()), Integer.valueOf(this.mNewSS.getDataRegState()));
        }
        ServiceState tss = this.mSS;
        this.mSS = this.mNewSS;
        this.mNewSS = tss;
        this.mNewSS.setStateOutOfService();
        CdmaCellLocation tcl = this.mCellLoc;
        this.mCellLoc = this.mNewCellLoc;
        this.mNewCellLoc = tcl;
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
        }
        if (hasChanged) {
            if (this.mCi.getRadioState().isOn() && !this.mIsSubscriptionFromRuim) {
                if (this.mSS.getVoiceRegState() == 0) {
                    eriText = this.mPhone.getCdmaEriText();
                } else {
                    eriText = this.mPhone.getContext().getText(R.string.PERSOSUBSTATE_SIM_ICCID_ERROR).toString();
                }
                this.mSS.setOperatorAlphaLong(eriText);
            }
            this.mPhone.setSystemProperty("gsm.operator.alpha", this.mSS.getOperatorAlphaLong());
            String prevOperatorNumeric = SystemProperties.get("gsm.operator.numeric", "");
            String operatorNumeric = this.mSS.getOperatorNumeric();
            this.mPhone.setSystemProperty("gsm.operator.numeric", operatorNumeric);
            updateCarrierMccMncConfiguration(operatorNumeric, prevOperatorNumeric, this.mPhone.getContext());
            if (operatorNumeric == null) {
                log("operatorNumeric is null");
                this.mPhone.setSystemProperty("gsm.operator.iso-country", "");
                this.mGotCountryCode = false;
            } else {
                String isoCountryCode = "";
                operatorNumeric.substring(0, 3);
                try {
                    isoCountryCode = MccTable.countryCodeForMcc(Integer.parseInt(operatorNumeric.substring(0, 3)));
                } catch (NumberFormatException ex) {
                    loge("pollStateDone: countryCodeForMcc error" + ex);
                } catch (StringIndexOutOfBoundsException ex2) {
                    loge("pollStateDone: countryCodeForMcc error" + ex2);
                }
                this.mPhone.setSystemProperty("gsm.operator.iso-country", isoCountryCode);
                this.mGotCountryCode = true;
                if (shouldFixTimeZoneNow(this.mPhone, operatorNumeric, prevOperatorNumeric, this.mNeedFixZone)) {
                    fixTimeZone(isoCountryCode);
                }
            }
            this.mPhone.setSystemProperty("gsm.operator.isroaming", this.mSS.getRoaming() ? "true" : "false");
            this.mPhone.notifyServiceStateChanged(this.mSS);
            updateSpnDisplay();
        }
        if (hasCdmaDataConnectionDetached) {
            this.mDetachedRegistrants.notifyRegistrants();
        }
        if (hasCdmaDataConnectionChanged || hasRilDataRadioTechnologyChanged) {
            notifyDataRegStateRilRadioTechnologyChanged();
            if (isIwlanFeatureAvailable() && 18 == this.mSS.getRilDataRadioTechnology()) {
                this.mPhone.notifyDataConnection(Phone.REASON_IWLAN_AVAILABLE);
                this.mIwlanRegistrants.notifyRegistrants();
            } else {
                this.mPhone.notifyDataConnection(null);
            }
        }
        if (hasCdmaDataConnectionAttached) {
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

    protected int radioTechnologyToDataServiceState(int code) {
        switch (code) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                return 1;
            case 6:
            case 7:
            case 8:
            case 12:
            case 13:
                return 0;
            case 9:
            case 10:
            case 11:
            default:
                loge("radioTechnologyToDataServiceState: Wrong radioTechnology code.");
                return 1;
        }
    }

    protected int regCodeToServiceState(int code) {
        switch (code) {
            case 0:
            case 2:
            case 3:
            case 4:
                return 1;
            case 1:
                return 0;
            case 5:
                return 0;
            default:
                loge("regCodeToServiceState: unexpected service state " + code);
                return 1;
        }
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    public int getCurrentDataConnectionState() {
        return this.mSS.getDataRegState();
    }

    protected boolean regCodeIsRoaming(int code) {
        return 5 == code;
    }

    private boolean isRoamIndForHomeSystem(String roamInd) {
        String homeRoamIndicators = SystemProperties.get("ro.cdma.homesystem");
        if (TextUtils.isEmpty(homeRoamIndicators)) {
            return false;
        }
        String[] arr$ = homeRoamIndicators.split(",");
        for (String homeRoamInd : arr$) {
            if (homeRoamInd.equals(roamInd)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRoamingBetweenOperators(boolean cdmaRoaming, ServiceState s) {
        String spn = getSystemProperty("gsm.sim.operator.alpha", "empty");
        String onsl = s.getOperatorAlphaLong();
        String onss = s.getOperatorAlphaShort();
        boolean equalsOnsl = onsl != null && spn.equals(onsl);
        boolean equalsOnss = onss != null && spn.equals(onss);
        return (!cdmaRoaming || equalsOnsl || equalsOnss) ? false : true;
    }

    /* JADX WARN: Code duplicated, block: B:32:0x0186 A[Catch: RuntimeException -> 0x02ea, TryCatch #1 {RuntimeException -> 0x02ea, blocks: (B:3:0x004a, B:6:0x00ea, B:8:0x00ff, B:11:0x010b, B:13:0x0126, B:14:0x0136, B:16:0x0146, B:19:0x0150, B:23:0x015a, B:57:0x025f, B:25:0x016a, B:27:0x0176, B:36:0x01a8, B:38:0x01cf, B:39:0x01d3, B:41:0x0214, B:43:0x021a, B:44:0x0225, B:45:0x0230, B:47:0x0238, B:49:0x0242, B:66:0x02b3, B:73:0x0343, B:89:0x04cc, B:86:0x0465, B:91:0x0504, B:92:0x0539, B:32:0x0186, B:35:0x0198, B:63:0x0280, B:65:0x0295, B:72:0x031e, B:76:0x0381, B:78:0x038e, B:80:0x03d4, B:82:0x03dd, B:88:0x049c, B:84:0x03ea, B:85:0x043b), top: B:94:0x004a, inners: #0 }] */
    /* JADX WARN: Code duplicated, block: B:34:0x0196  */
    /* JADX WARN: Code duplicated, block: B:60:0x0278  */
    private void setTimeFromNITZString(String nitz, long nitzReceiveTime) {
        boolean z;
        long start = SystemClock.elapsedRealtime();
        log("NITZ: " + nitz + "," + nitzReceiveTime + " start=" + start + " delay=" + (start - nitzReceiveTime));
        try {
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
            c.clear();
            c.set(16, 0);
            String[] nitzSubs = nitz.split("[/:,+-]");
            int year = Integer.parseInt(nitzSubs[0]) + NITZ_UPDATE_DIFF_DEFAULT;
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
            int tzOffset2 = (sign ? 1 : -1) * tzOffset * 15 * 60 * CallFailCause.CDMA_LOCKED_UNTIL_POWER_CYCLE;
            TimeZone zone = null;
            if (nitzSubs.length >= 9) {
                String tzname = nitzSubs[8].replace('!', '/');
                zone = TimeZone.getTimeZone(tzname);
            }
            String iso = getSystemProperty("gsm.operator.iso-country", "");
            if (zone == null && this.mGotCountryCode) {
                if (iso == null || iso.length() <= 0) {
                    zone = getNitzTimeZone(tzOffset2, dst != 0, c.getTimeInMillis());
                } else {
                    zone = TimeUtils.getTimeZone(tzOffset2, dst != 0, c.getTimeInMillis(), iso);
                }
            }
            if (zone == null || this.mZoneOffset != tzOffset2) {
                this.mNeedFixZone = true;
                this.mZoneOffset = tzOffset2;
                if (dst != 0) {
                    z = true;
                } else {
                    z = false;
                }
                this.mZoneDst = z;
                this.mZoneTime = c.getTimeInMillis();
            } else {
                if (this.mZoneDst != (dst != 0)) {
                    this.mNeedFixZone = true;
                    this.mZoneOffset = tzOffset2;
                    if (dst != 0) {
                        z = true;
                    } else {
                        z = false;
                    }
                    this.mZoneDst = z;
                    this.mZoneTime = c.getTimeInMillis();
                }
            }
            log("NITZ: tzOffset=" + tzOffset2 + " dst=" + dst + " zone=" + (zone != null ? zone.getID() : "NULL") + " iso=" + iso + " mGotCountryCode=" + this.mGotCountryCode + " mNeedFixZone=" + this.mNeedFixZone);
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
                long millisSinceNitzReceived = SystemClock.elapsedRealtime() - nitzReceiveTime;
                if (millisSinceNitzReceived < 0) {
                    log("NITZ: not setting time, clock has rolled backwards since NITZ time was received, " + nitz);
                    return;
                }
                if (millisSinceNitzReceived > 2147483647L) {
                    log("NITZ: not setting time, processing has taken " + (millisSinceNitzReceived / 86400000) + " days");
                    return;
                }
                c.add(14, (int) millisSinceNitzReceived);
                if (getAutoTime()) {
                    long gained = c.getTimeInMillis() - System.currentTimeMillis();
                    long timeSinceLastUpdate = SystemClock.elapsedRealtime() - this.mSavedAtTime;
                    int nitzUpdateSpacing = Settings.Global.getInt(this.mCr, "nitz_update_spacing", this.mNitzUpdateSpacing);
                    int nitzUpdateDiff = Settings.Global.getInt(this.mCr, "nitz_update_diff", this.mNitzUpdateDiff);
                    if (this.mSavedAtTime != 0 && timeSinceLastUpdate <= nitzUpdateSpacing && Math.abs(gained) <= nitzUpdateDiff) {
                        log("NITZ: ignore, a previous update was " + timeSinceLastUpdate + "ms ago and gained=" + gained + "ms");
                        return;
                    } else {
                        log("NITZ: Auto updating time of day to " + c.getTime() + " NITZ receive delay=" + millisSinceNitzReceived + "ms gained=" + gained + "ms from " + nitz);
                        setAndBroadcastNetworkSetTime(c.getTimeInMillis());
                    }
                }
                log("NITZ: update nitz time property");
                SystemProperties.set("gsm.nitz.time", String.valueOf(c.getTimeInMillis()));
                this.mSavedTime = c.getTimeInMillis();
                this.mSavedAtTime = SystemClock.elapsedRealtime();
            } finally {
                long end = SystemClock.elapsedRealtime();
                log("NITZ: end=" + end + " dur=" + (end - start));
                this.mWakeLock.release();
            }
        } catch (RuntimeException ex) {
            loge("NITZ: Parsing NITZ time " + nitz + " ex=" + ex);
        }
    }

    private boolean getAutoTime() {
        try {
            return Settings.Global.getInt(this.mCr, "auto_time") > 0;
        } catch (Settings.SettingNotFoundException e) {
            return true;
        }
    }

    private boolean getAutoTimeZone() {
        try {
            return Settings.Global.getInt(this.mCr, "auto_time_zone") > 0;
        } catch (Settings.SettingNotFoundException e) {
            return true;
        }
    }

    private void saveNitzTimeZone(String zoneId) {
        this.mSavedTimeZone = zoneId;
    }

    private void setAndBroadcastNetworkSetTimeZone(String zoneId) {
        log("setAndBroadcastNetworkSetTimeZone: setTimeZone=" + zoneId);
        AlarmManager alarm = (AlarmManager) this.mPhone.getContext().getSystemService("alarm");
        alarm.setTimeZone(zoneId);
        Intent intent = new Intent("android.intent.action.NETWORK_SET_TIMEZONE");
        intent.addFlags(536870912);
        intent.putExtra("time-zone", zoneId);
        this.mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
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
        if (Settings.Global.getInt(this.mCr, "auto_time", 0) != 0) {
            log("revertToNitzTime: mSavedTime=" + this.mSavedTime + " mSavedAtTime=" + this.mSavedAtTime);
            if (this.mSavedTime != 0 && this.mSavedAtTime != 0) {
                setAndBroadcastNetworkSetTime(this.mSavedTime + (SystemClock.elapsedRealtime() - this.mSavedAtTime));
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void revertToNitzTimeZone() {
        if (Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), "auto_time_zone", 0) != 0) {
            log("revertToNitzTimeZone: tz='" + this.mSavedTimeZone);
            if (this.mSavedTimeZone != null) {
                setAndBroadcastNetworkSetTimeZone(this.mSavedTimeZone);
            }
        }
    }

    protected boolean isSidsAllZeros() {
        if (this.mHomeSystemId != null) {
            for (int i = 0; i < this.mHomeSystemId.length; i++) {
                if (this.mHomeSystemId[i] != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isHomeSid(int sid) {
        if (this.mHomeSystemId != null) {
            for (int i = 0; i < this.mHomeSystemId.length; i++) {
                if (sid == this.mHomeSystemId[i]) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    public boolean isConcurrentVoiceAndDataAllowed() {
        return false;
    }

    public String getMdnNumber() {
        return this.mMdn;
    }

    public String getCdmaMin() {
        return this.mMin;
    }

    public String getPrlVersion() {
        return this.mPrlVersion;
    }

    public String getNvImsi() {
        String operatorNumeric = getSystemProperty(CDMAPhone.PROPERTY_CDMA_HOME_OPERATOR_NUMERIC, "");
        if (TextUtils.isEmpty(operatorNumeric) || getCdmaMin() == null) {
            return null;
        }
        return operatorNumeric + getCdmaMin();
    }

    public boolean isMinInfoReady() {
        return this.mIsMinInfoReady;
    }

    int getOtasp() {
        int provisioningState;
        if (this.mMin == null || this.mMin.length() < 6) {
            log("getOtasp: bad mMin='" + this.mMin + "'");
            provisioningState = 1;
        } else if (this.mMin.equals(UNACTIVATED_MIN_VALUE) || this.mMin.substring(0, 6).equals(UNACTIVATED_MIN2_VALUE) || SystemProperties.getBoolean("test_cdma_setup", false)) {
            provisioningState = 2;
        } else {
            provisioningState = 3;
        }
        log("getOtasp: state=" + provisioningState);
        return provisioningState;
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    protected void hangupAndPowerOff() {
        this.mPhone.mCT.mRingingCall.hangupIfAlive();
        this.mPhone.mCT.mBackgroundCall.hangupIfAlive();
        this.mPhone.mCT.mForegroundCall.hangupIfAlive();
        this.mCi.setRadioPower(false, null);
    }

    protected void parseSidNid(String sidStr, String nidStr) {
        if (sidStr != null) {
            String[] sid = sidStr.split(",");
            this.mHomeSystemId = new int[sid.length];
            for (int i = 0; i < sid.length; i++) {
                try {
                    this.mHomeSystemId[i] = Integer.parseInt(sid[i]);
                } catch (NumberFormatException ex) {
                    loge("error parsing system id: " + ex);
                }
            }
        }
        log("CDMA_SUBSCRIPTION: SID=" + sidStr);
        if (nidStr != null) {
            String[] nid = nidStr.split(",");
            this.mHomeNetworkId = new int[nid.length];
            for (int i2 = 0; i2 < nid.length; i2++) {
                try {
                    this.mHomeNetworkId[i2] = Integer.parseInt(nid[i2]);
                } catch (NumberFormatException ex2) {
                    loge("CDMA_SUBSCRIPTION: error parsing network id: " + ex2);
                }
            }
        }
        log("CDMA_SUBSCRIPTION: NID=" + nidStr);
    }

    protected void updateOtaspState() {
        int otaspMode = getOtasp();
        int oldOtaspMode = this.mCurrentOtaspMode;
        this.mCurrentOtaspMode = otaspMode;
        if (this.mCdmaForSubscriptionInfoReadyRegistrants != null) {
            log("CDMA_SUBSCRIPTION: call notifyRegistrants()");
            this.mCdmaForSubscriptionInfoReadyRegistrants.notifyRegistrants();
        }
        if (oldOtaspMode != this.mCurrentOtaspMode) {
            log("CDMA_SUBSCRIPTION: call notifyOtaspChanged old otaspMode=" + oldOtaspMode + " new otaspMode=" + this.mCurrentOtaspMode);
            this.mPhone.notifyOtaspChanged(this.mCurrentOtaspMode);
        }
    }

    private void registerForRuimEvents() {
        log("registerForRuimEvents");
        if (this.mUiccApplcation != null && !this.isRegisteredForReady) {
            this.mUiccApplcation.registerForReady(this, 26, null);
            this.isRegisteredForReady = true;
        }
        if (this.mIccRecords != null && !this.isRegisteredForRecordsLoaded) {
            this.mIccRecords.registerForRecordsLoaded(this, 27, null);
            this.isRegisteredForRecordsLoaded = true;
        }
    }

    protected UiccCardApplication getUiccCardApplication() {
        return this.mUiccController.getUiccCardApplication(2);
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    protected void onUpdateIccAvailability() {
        UiccCardApplication newUiccApplication;
        if (this.mUiccController != null && this.mUiccApplcation != (newUiccApplication = getUiccCardApplication())) {
            if (this.mUiccApplcation != null) {
                log("Removing stale icc objects.");
                this.mUiccApplcation.unregisterForReady(this);
                this.isRegisteredForReady = false;
                if (this.mIccRecords != null) {
                    this.mIccRecords.unregisterForRecordsLoaded(this);
                    this.isRegisteredForRecordsLoaded = false;
                }
                this.mIccRecords = null;
                this.mUiccApplcation = null;
            }
            if (newUiccApplication != null) {
                log("New card found");
                this.mUiccApplcation = newUiccApplication;
                this.mIccRecords = this.mUiccApplcation.getIccRecords();
                if (this.mIsSubscriptionFromRuim) {
                    registerForRuimEvents();
                }
            }
        }
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    protected void log(String s) {
        Rlog.d(LOG_TAG, "[CdmaSST] " + s);
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    protected void loge(String s) {
        Rlog.e(LOG_TAG, "[CdmaSST] " + s);
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("CdmaServiceStateTracker extends:");
        super.dump(fd, pw, args);
        pw.println(" mPhone=" + this.mPhone);
        pw.println(" mSS=" + this.mSS);
        pw.println(" mNewSS=" + this.mNewSS);
        pw.println(" mCellLoc=" + this.mCellLoc);
        pw.println(" mNewCellLoc=" + this.mNewCellLoc);
        pw.println(" mCurrentOtaspMode=" + this.mCurrentOtaspMode);
        pw.println(" mCdmaRoaming=" + this.mCdmaRoaming);
        pw.println(" mRoamingIndicator=" + this.mRoamingIndicator);
        pw.println(" mIsInPrl=" + this.mIsInPrl);
        pw.println(" mDefaultRoamingIndicator=" + this.mDefaultRoamingIndicator);
        pw.println(" mRegistrationState=" + this.mRegistrationState);
        pw.println(" mNeedFixZone=" + this.mNeedFixZone);
        pw.println(" mZoneOffset=" + this.mZoneOffset);
        pw.println(" mZoneDst=" + this.mZoneDst);
        pw.println(" mZoneTime=" + this.mZoneTime);
        pw.println(" mGotCountryCode=" + this.mGotCountryCode);
        pw.println(" mSavedTimeZone=" + this.mSavedTimeZone);
        pw.println(" mSavedTime=" + this.mSavedTime);
        pw.println(" mSavedAtTime=" + this.mSavedAtTime);
        pw.println(" mWakeLock=" + this.mWakeLock);
        pw.println(" mCurPlmn=" + this.mCurPlmn);
        pw.println(" mMdn=" + this.mMdn);
        pw.println(" mHomeSystemId=" + this.mHomeSystemId);
        pw.println(" mHomeNetworkId=" + this.mHomeNetworkId);
        pw.println(" mMin=" + this.mMin);
        pw.println(" mPrlVersion=" + this.mPrlVersion);
        pw.println(" mIsMinInfoReady=" + this.mIsMinInfoReady);
        pw.println(" mIsEriTextLoaded=" + this.mIsEriTextLoaded);
        pw.println(" mIsSubscriptionFromRuim=" + this.mIsSubscriptionFromRuim);
        pw.println(" mCdmaSSM=" + this.mCdmaSSM);
        pw.println(" mRegistrationDeniedReason=" + this.mRegistrationDeniedReason);
        pw.println(" mCurrentCarrier=" + this.mCurrentCarrier);
    }
}
