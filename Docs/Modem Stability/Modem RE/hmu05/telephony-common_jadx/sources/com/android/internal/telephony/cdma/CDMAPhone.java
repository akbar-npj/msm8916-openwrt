package com.android.internal.telephony.cdma;

import android.R;
import android.app.ActivityManagerNative;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.CellLocation;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.cdma.CdmaCellLocation;
import android.text.TextUtils;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.PhoneSubInfo;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.uicc.IccException;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* JADX INFO: loaded from: classes.dex */
public class CDMAPhone extends PhoneBase {
    static final int CANCEL_ECM_TIMER = 1;
    private static final boolean DBG = true;
    private static final int DEFAULT_ECM_EXIT_TIMER_VALUE = 300000;
    private static final int INVALID_SYSTEM_SELECTION_CODE = -1;
    private static final String IS683A_FEATURE_CODE = "*228";
    private static final int IS683A_FEATURE_CODE_NUM_DIGITS = 4;
    private static final int IS683A_SYS_SEL_CODE_NUM_DIGITS = 2;
    private static final int IS683A_SYS_SEL_CODE_OFFSET = 4;
    private static final int IS683_CONST_1900MHZ_A_BLOCK = 2;
    private static final int IS683_CONST_1900MHZ_B_BLOCK = 3;
    private static final int IS683_CONST_1900MHZ_C_BLOCK = 4;
    private static final int IS683_CONST_1900MHZ_D_BLOCK = 5;
    private static final int IS683_CONST_1900MHZ_E_BLOCK = 6;
    private static final int IS683_CONST_1900MHZ_F_BLOCK = 7;
    private static final int IS683_CONST_800MHZ_A_BAND = 0;
    private static final int IS683_CONST_800MHZ_B_BAND = 1;
    static final String LOG_TAG = "CDMAPhone";
    public static final String PROPERTY_CDMA_HOME_OPERATOR_NUMERIC = "ro.cdma.home.operator.numeric";
    static final int RESTART_ECM_TIMER = 0;
    private static final boolean VDBG = false;
    protected static final String VM_NUMBER_CDMA = "vm_number_key_cdma";
    private static Pattern pOtaSpNumSchema = Pattern.compile("[,\\s]+");
    protected CdmaCallTracker mCT;
    protected String mCarrierOtaSpNumSchema;
    protected CdmaSubscriptionSourceManager mCdmaSSM;
    protected int mCdmaSubscriptionSource;
    private Registrant mEcmExitRespRegistrant;
    private final RegistrantList mEcmTimerResetRegistrants;
    private final RegistrantList mEriFileLoadedRegistrants;
    protected EriManager mEriManager;
    private String mEsn;
    private Runnable mExitEcmRunnable;
    protected String mImei;
    protected String mImeiSv;
    protected boolean mIsPhoneInEcmState;
    private String mMeid;
    ArrayList<CdmaMmiCode> mPendingMmis;
    Registrant mPostDialHandler;
    protected UiccCard mRuimCard;
    protected RuimPhoneBookInterfaceManager mRuimPhoneBookInterfaceManager;
    protected CdmaServiceStateTracker mSST;
    protected PhoneSubInfo mSubInfo;
    private String mVmNumber;
    protected PowerManager.WakeLock mWakeLock;

    public CDMAPhone(Context context, CommandsInterface ci, PhoneNotifier notifier) {
        super("CDMA", notifier, context, ci, false);
        this.mVmNumber = null;
        this.mPendingMmis = new ArrayList<>();
        this.mCdmaSubscriptionSource = -1;
        this.mRuimCard = null;
        this.mEriFileLoadedRegistrants = new RegistrantList();
        this.mEcmTimerResetRegistrants = new RegistrantList();
        this.mExitEcmRunnable = new Runnable() { // from class: com.android.internal.telephony.cdma.CDMAPhone.1
            @Override // java.lang.Runnable
            public void run() {
                CDMAPhone.this.exitEmergencyCallbackMode();
            }
        };
        initSstIcc();
        init(context, notifier);
    }

    public CDMAPhone(Context context, CommandsInterface ci, PhoneNotifier notifier, boolean unitTestMode) {
        super("CDMA", notifier, context, ci, unitTestMode);
        this.mVmNumber = null;
        this.mPendingMmis = new ArrayList<>();
        this.mCdmaSubscriptionSource = -1;
        this.mRuimCard = null;
        this.mEriFileLoadedRegistrants = new RegistrantList();
        this.mEcmTimerResetRegistrants = new RegistrantList();
        this.mExitEcmRunnable = new Runnable() { // from class: com.android.internal.telephony.cdma.CDMAPhone.1
            @Override // java.lang.Runnable
            public void run() {
                CDMAPhone.this.exitEmergencyCallbackMode();
            }
        };
        initSstIcc();
        init(context, notifier);
    }

    protected void initSstIcc() {
        this.mSST = new CdmaServiceStateTracker(this);
    }

    protected void init(Context context, PhoneNotifier notifier) {
        this.mCi.setPhoneType(2);
        this.mCT = new CdmaCallTracker(this);
        this.mCdmaSSM = CdmaSubscriptionSourceManager.getInstance(context, this.mCi, this, 27, null);
        this.mDcTracker = new DcTracker(this);
        this.mRuimPhoneBookInterfaceManager = new RuimPhoneBookInterfaceManager(this);
        this.mSubInfo = new PhoneSubInfo(this);
        this.mEriManager = new EriManager(this, context, 0);
        this.mCi.registerForAvailable(this, 1, null);
        this.mCi.registerForOffOrNotAvailable(this, 8, null);
        this.mCi.registerForOn(this, 5, null);
        this.mCi.setOnSuppServiceNotification(this, 2, null);
        this.mSST.registerForNetworkAttached(this, 19, null);
        this.mCi.setEmergencyCallbackMode(this, 25, null);
        this.mCi.registerForExitEmergencyCallbackMode(this, 26, null);
        PowerManager pm = (PowerManager) context.getSystemService("power");
        this.mWakeLock = pm.newWakeLock(1, LOG_TAG);
        SystemProperties.set("gsm.current.phone-type", Integer.toString(2));
        String inEcm = SystemProperties.get("ril.cdma.inecmmode", "false");
        this.mIsPhoneInEcmState = inEcm.equals("true");
        if (this.mIsPhoneInEcmState) {
            this.mCi.exitEmergencyCallbackMode(obtainMessage(26));
        }
        this.mCarrierOtaSpNumSchema = SystemProperties.get("ro.cdma.otaspnumschema", "");
        String operatorAlpha = SystemProperties.get("ro.cdma.home.operator.alpha");
        String operatorNumeric = SystemProperties.get(PROPERTY_CDMA_HOME_OPERATOR_NUMERIC);
        log("init: operatorAlpha='" + operatorAlpha + "' operatorNumeric='" + operatorNumeric + "'");
        if (this.mUiccController.getUiccCardApplication(1) == null) {
            log("init: APP_FAM_3GPP == NULL");
            if (!TextUtils.isEmpty(operatorAlpha)) {
                log("init: set 'gsm.sim.operator.alpha' to operator='" + operatorAlpha + "'");
                setSystemProperty("gsm.sim.operator.alpha", operatorAlpha);
            }
            if (!TextUtils.isEmpty(operatorNumeric)) {
                log("init: set 'gsm.sim.operator.numeric' to operator='" + operatorNumeric + "'");
                setSystemProperty("gsm.sim.operator.numeric", operatorNumeric);
            }
            setIsoCountryProperty(operatorNumeric);
        }
        updateCurrentCarrierInProvider(operatorNumeric);
        updateVoiceMail();
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void dispose() {
        synchronized (PhoneProxy.lockForRadioTechnologyChange) {
            super.dispose();
            log("dispose");
            unregisterForRuimRecordEvents();
            this.mCi.unregisterForAvailable(this);
            this.mCi.unregisterForOffOrNotAvailable(this);
            this.mCi.unregisterForOn(this);
            this.mSST.unregisterForNetworkAttached(this);
            this.mCi.unSetOnSuppServiceNotification(this);
            this.mCi.unregisterForExitEmergencyCallbackMode(this);
            removeCallbacks(this.mExitEcmRunnable);
            this.mPendingMmis.clear();
            this.mCT.dispose();
            this.mDcTracker.dispose();
            this.mSST.dispose();
            this.mCdmaSSM.dispose(this);
            this.mRuimPhoneBookInterfaceManager.dispose();
            this.mSubInfo.dispose();
            this.mEriManager.dispose();
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void removeReferences() {
        log("removeReferences");
        this.mRuimPhoneBookInterfaceManager = null;
        this.mSubInfo = null;
        this.mCT = null;
        this.mSST = null;
        this.mEriManager = null;
        this.mExitEcmRunnable = null;
        super.removeReferences();
    }

    protected void finalize() {
        Rlog.d(LOG_TAG, "CDMAPhone finalized");
        if (this.mWakeLock.isHeld()) {
            Rlog.e(LOG_TAG, "UNEXPECTED; mWakeLock is held when finalizing.");
            this.mWakeLock.release();
        }
    }

    @Override // com.android.internal.telephony.Phone
    public ServiceState getServiceState() {
        return this.mSST.mSS;
    }

    @Override // com.android.internal.telephony.PhoneBase
    public CallTracker getCallTracker() {
        return this.mCT;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public PhoneConstants.State getState() {
        return this.mCT.mState;
    }

    @Override // com.android.internal.telephony.PhoneBase
    public ServiceStateTracker getServiceStateTracker() {
        return this.mSST;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public int getPhoneType() {
        return 2;
    }

    @Override // com.android.internal.telephony.Phone
    public boolean canTransfer() {
        Rlog.e(LOG_TAG, "canTransfer: not possible in CDMA");
        return false;
    }

    @Override // com.android.internal.telephony.Phone
    public CdmaCall getRingingCall() {
        return this.mCT.mRingingCall;
    }

    @Override // com.android.internal.telephony.Phone
    public void setMute(boolean muted) {
        this.mCT.setMute(muted);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean getMute() {
        return this.mCT.getMute();
    }

    @Override // com.android.internal.telephony.Phone
    public void conference() {
        Rlog.e(LOG_TAG, "conference: not possible in CDMA");
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void enableEnhancedVoicePrivacy(boolean enable, Message onComplete) {
        this.mCi.setPreferredVoicePrivacy(enable, onComplete);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void getEnhancedVoicePrivacy(Message onComplete) {
        this.mCi.getPreferredVoicePrivacy(onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void clearDisconnected() {
        this.mCT.clearDisconnected();
    }

    @Override // com.android.internal.telephony.Phone
    public Phone.DataActivityState getDataActivityState() {
        Phone.DataActivityState ret = Phone.DataActivityState.NONE;
        if (this.mSST.getCurrentDataConnectionState() == 0) {
            switch (AnonymousClass2.$SwitchMap$com$android$internal$telephony$DctConstants$Activity[this.mDcTracker.getActivity().ordinal()]) {
                case 1:
                    Phone.DataActivityState ret2 = Phone.DataActivityState.DATAIN;
                    return ret2;
                case 2:
                    Phone.DataActivityState ret3 = Phone.DataActivityState.DATAOUT;
                    return ret3;
                case 3:
                    Phone.DataActivityState ret4 = Phone.DataActivityState.DATAINANDOUT;
                    return ret4;
                case 4:
                    Phone.DataActivityState ret5 = Phone.DataActivityState.DORMANT;
                    return ret5;
                default:
                    Phone.DataActivityState ret6 = Phone.DataActivityState.NONE;
                    return ret6;
            }
        }
        return ret;
    }

    @Override // com.android.internal.telephony.Phone
    public Connection dial(String dialString) throws CallStateException {
        String newDialString = PhoneNumberUtils.stripSeparators(dialString);
        return this.mCT.dial(newDialString);
    }

    @Override // com.android.internal.telephony.Phone
    public Connection dial(String dialString, UUSInfo uusInfo) throws CallStateException {
        throw new CallStateException("Sending UUS information NOT supported in CDMA!");
    }

    @Override // com.android.internal.telephony.Phone
    public List<? extends MmiCode> getPendingMmiCodes() {
        return this.mPendingMmis;
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForSuppServiceNotification(Handler h, int what, Object obj) {
        Rlog.e(LOG_TAG, "method registerForSuppServiceNotification is NOT supported in CDMA!");
    }

    @Override // com.android.internal.telephony.Phone
    public CdmaCall getBackgroundCall() {
        return this.mCT.mBackgroundCall;
    }

    @Override // com.android.internal.telephony.Phone
    public boolean handleInCallMmiCommands(String dialString) {
        Rlog.e(LOG_TAG, "method handleInCallMmiCommands is NOT supported in CDMA!");
        return false;
    }

    boolean isInCall() {
        Call.State foregroundCallState = getForegroundCall().getState();
        Call.State backgroundCallState = getBackgroundCall().getState();
        Call.State ringingCallState = getRingingCall().getState();
        return foregroundCallState.isAlive() || backgroundCallState.isAlive() || ringingCallState.isAlive();
    }

    @Override // com.android.internal.telephony.Phone
    public void setNetworkSelectionModeAutomatic(Message response) {
        Rlog.e(LOG_TAG, "method setNetworkSelectionModeAutomatic is NOT supported in CDMA!");
        if (response != null) {
            Rlog.e(LOG_TAG, "setNetworkSelectionModeAutomatic: not possible in CDMA- Posting exception");
            CommandException ce = new CommandException(CommandException.Error.REQUEST_NOT_SUPPORTED);
            AsyncResult.forMessage(response).exception = ce;
            response.sendToTarget();
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForSuppServiceNotification(Handler h) {
        Rlog.e(LOG_TAG, "method unregisterForSuppServiceNotification is NOT supported in CDMA!");
    }

    @Override // com.android.internal.telephony.Phone
    public void acceptCall() throws CallStateException {
        this.mCT.acceptCall();
    }

    @Override // com.android.internal.telephony.Phone
    public void rejectCall() throws CallStateException {
        this.mCT.rejectCall();
    }

    @Override // com.android.internal.telephony.Phone
    public void switchHoldingAndActive() throws CallStateException {
        this.mCT.switchWaitingOrHoldingAndActive();
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public String getIccSerialNumber() {
        IccRecords r = this.mIccRecords.get();
        if (r == null) {
            r = this.mUiccController.getIccRecords(1);
        }
        if (r != null) {
            return r.getIccId();
        }
        return null;
    }

    @Override // com.android.internal.telephony.Phone
    public String getLine1Number() {
        return this.mSST.getMdnNumber();
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public String getCdmaPrlVersion() {
        return this.mSST.getPrlVersion();
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public String getCdmaMin() {
        return this.mSST.getCdmaMin();
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public boolean isMinInfoReady() {
        return this.mSST.isMinInfoReady();
    }

    @Override // com.android.internal.telephony.Phone
    public void getCallWaiting(Message onComplete) {
        this.mCi.queryCallWaiting(1, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void setRadioPower(boolean power) {
        this.mSST.setRadioPower(power);
    }

    @Override // com.android.internal.telephony.Phone
    public String getEsn() {
        return this.mEsn;
    }

    @Override // com.android.internal.telephony.Phone
    public String getMeid() {
        return this.mMeid;
    }

    public String getDeviceId() {
        String id = getMeid();
        if (id == null || id.matches("^0*$")) {
            Rlog.d(LOG_TAG, "getDeviceId(): MEID is not initialized use ESN");
            return getEsn();
        }
        return id;
    }

    public String getDeviceSvn() {
        Rlog.d(LOG_TAG, "getDeviceSvn(): return 0");
        return "0";
    }

    public String getSubscriberId() {
        if (this.mCdmaSubscriptionSource == 1) {
            return this.mSST.getNvImsi();
        }
        IccRecords r = this.mIccRecords.get();
        if (r != null) {
            return r.getIMSI();
        }
        return null;
    }

    public String getGroupIdLevel1() {
        Rlog.e(LOG_TAG, "GID1 is not available in CDMA");
        return null;
    }

    public String getImei() {
        Rlog.e(LOG_TAG, "IMEI is not available in CDMA");
        return this.mImei;
    }

    @Override // com.android.internal.telephony.Phone
    public boolean canConference() {
        Rlog.e(LOG_TAG, "canConference: not possible in CDMA");
        return false;
    }

    @Override // com.android.internal.telephony.Phone
    public CellLocation getCellLocation() {
        CdmaCellLocation loc = this.mSST.mCellLoc;
        int mode = Settings.Secure.getInt(getContext().getContentResolver(), "location_mode", 0);
        if (mode == 0) {
            CdmaCellLocation privateLoc = new CdmaCellLocation();
            privateLoc.setCellLocationData(loc.getBaseStationId(), Integer.MAX_VALUE, Integer.MAX_VALUE, loc.getSystemId(), loc.getNetworkId());
            return privateLoc;
        }
        return loc;
    }

    @Override // com.android.internal.telephony.Phone
    public CdmaCall getForegroundCall() {
        return this.mCT.mForegroundCall;
    }

    public void selectNetworkManually(OperatorInfo network, Message response) {
        Rlog.e(LOG_TAG, "selectNetworkManually: not possible in CDMA");
        if (response != null) {
            CommandException ce = new CommandException(CommandException.Error.REQUEST_NOT_SUPPORTED);
            AsyncResult.forMessage(response).exception = ce;
            response.sendToTarget();
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void setOnPostDialCharacter(Handler h, int what, Object obj) {
        this.mPostDialHandler = new Registrant(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean handlePinMmi(String dialString) {
        CdmaMmiCode mmi = CdmaMmiCode.newFromDialString(dialString, this, this.mUiccApplication.get());
        if (mmi == null) {
            Rlog.e(LOG_TAG, "Mmi is NULL!");
            return false;
        }
        if (mmi.isPinPukCommand()) {
            this.mPendingMmis.add(mmi);
            this.mMmiRegistrants.notifyRegistrants(new AsyncResult((Object) null, mmi, (Throwable) null));
            mmi.processCode();
            return true;
        }
        Rlog.e(LOG_TAG, "Unrecognized mmi!");
        return false;
    }

    void onMMIDone(CdmaMmiCode mmi) {
        if (this.mPendingMmis.remove(mmi)) {
            this.mMmiCompleteRegistrants.notifyRegistrants(new AsyncResult((Object) null, mmi, (Throwable) null));
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void setLine1Number(String alphaTag, String number, Message onComplete) {
        Rlog.e(LOG_TAG, "setLine1Number: not possible in CDMA");
    }

    @Override // com.android.internal.telephony.Phone
    public void setCallWaiting(boolean enable, Message onComplete) {
        Rlog.e(LOG_TAG, "method setCallWaiting is NOT supported in CDMA!");
    }

    @Override // com.android.internal.telephony.Phone
    public void updateServiceLocation() {
        this.mSST.enableSingleLocationUpdate();
    }

    @Override // com.android.internal.telephony.Phone
    public void setDataRoamingEnabled(boolean enable) {
        this.mDcTracker.setDataOnRoamingEnabled(enable);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void registerForCdmaOtaStatusChange(Handler h, int what, Object obj) {
        this.mCi.registerForCdmaOtaProvision(h, what, obj);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void unregisterForCdmaOtaStatusChange(Handler h) {
        this.mCi.unregisterForCdmaOtaProvision(h);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void registerForSubscriptionInfoReady(Handler h, int what, Object obj) {
        this.mSST.registerForSubscriptionInfoReady(h, what, obj);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void unregisterForSubscriptionInfoReady(Handler h) {
        this.mSST.unregisterForSubscriptionInfoReady(h);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setOnEcbModeExitResponse(Handler h, int what, Object obj) {
        this.mEcmExitRespRegistrant = new Registrant(h, what, obj);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void unsetOnEcbModeExitResponse(Handler h) {
        this.mEcmExitRespRegistrant.clear();
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void registerForCallWaiting(Handler h, int what, Object obj) {
        this.mCT.registerForCallWaiting(h, what, obj);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void unregisterForCallWaiting(Handler h) {
        this.mCT.unregisterForCallWaiting(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void getNeighboringCids(Message response) {
        if (response != null) {
            CommandException ce = new CommandException(CommandException.Error.REQUEST_NOT_SUPPORTED);
            AsyncResult.forMessage(response).exception = ce;
            response.sendToTarget();
        }
    }

    public PhoneConstants.DataState getDataConnectionState(String apnType) {
        PhoneConstants.DataState ret = PhoneConstants.DataState.DISCONNECTED;
        if (this.mSST == null) {
            ret = PhoneConstants.DataState.DISCONNECTED;
        } else if (this.mSST.getCurrentDataConnectionState() != 0 && this.mOosIsDisconnect) {
            ret = PhoneConstants.DataState.DISCONNECTED;
            log("getDataConnectionState: Data is Out of Service. ret = " + ret);
        } else if (!this.mDcTracker.isApnTypeEnabled(apnType) || !this.mDcTracker.isApnTypeActive(apnType)) {
            ret = PhoneConstants.DataState.DISCONNECTED;
        } else {
            switch (AnonymousClass2.$SwitchMap$com$android$internal$telephony$DctConstants$State[this.mDcTracker.getState(apnType).ordinal()]) {
                case 1:
                case 2:
                case 3:
                    ret = PhoneConstants.DataState.DISCONNECTED;
                    break;
                case 4:
                case 5:
                    if (this.mCT.mState != PhoneConstants.State.IDLE && !this.mSST.isConcurrentVoiceAndDataAllowed()) {
                        ret = PhoneConstants.DataState.SUSPENDED;
                    } else {
                        ret = PhoneConstants.DataState.CONNECTED;
                    }
                    break;
                case 6:
                case 7:
                    ret = PhoneConstants.DataState.CONNECTING;
                    break;
            }
        }
        log("getDataConnectionState apnType=" + apnType + " ret=" + ret);
        return ret;
    }

    /* JADX INFO: renamed from: com.android.internal.telephony.cdma.CDMAPhone$2, reason: invalid class name */
    static /* synthetic */ class AnonymousClass2 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DctConstants$Activity;
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DctConstants$State = new int[DctConstants.State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.RETRYING.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.FAILED.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.IDLE.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.CONNECTED.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.DISCONNECTING.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.CONNECTING.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.SCANNING.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
            $SwitchMap$com$android$internal$telephony$DctConstants$Activity = new int[DctConstants.Activity.values().length];
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[DctConstants.Activity.DATAIN.ordinal()] = 1;
            } catch (NoSuchFieldError e8) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[DctConstants.Activity.DATAOUT.ordinal()] = 2;
            } catch (NoSuchFieldError e9) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[DctConstants.Activity.DATAINANDOUT.ordinal()] = 3;
            } catch (NoSuchFieldError e10) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[DctConstants.Activity.DORMANT.ordinal()] = 4;
            } catch (NoSuchFieldError e11) {
            }
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void sendUssdResponse(String ussdMessge) {
        Rlog.e(LOG_TAG, "sendUssdResponse: not possible in CDMA");
    }

    @Override // com.android.internal.telephony.Phone
    public void sendDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            Rlog.e(LOG_TAG, "sendDtmf called with invalid character '" + c + "'");
        } else if (this.mCT.mState == PhoneConstants.State.OFFHOOK) {
            this.mCi.sendDtmf(c, null);
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void startDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c) && (c < 'A' || c > 'D')) {
            Rlog.e(LOG_TAG, "startDtmf called with invalid character '" + c + "'");
        } else {
            this.mCi.startDtmf(c, null);
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void stopDtmf() {
        this.mCi.stopDtmf(null);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void sendBurstDtmf(String dtmfString, int on, int off, Message onComplete) {
        boolean check = true;
        for (int itr = 0; itr < dtmfString.length(); itr++) {
            if (!PhoneNumberUtils.is12Key(dtmfString.charAt(itr))) {
                Rlog.e(LOG_TAG, "sendDtmf called with invalid character '" + dtmfString.charAt(itr) + "'");
                check = false;
                break;
            }
        }
        if (this.mCT.mState == PhoneConstants.State.OFFHOOK && check) {
            this.mCi.sendBurstDtmf(dtmfString, on, off, onComplete);
        }
    }

    public void getAvailableNetworks(Message response) {
        Rlog.e(LOG_TAG, "getAvailableNetworks: not possible in CDMA");
        if (response != null) {
            CommandException ce = new CommandException(CommandException.Error.REQUEST_NOT_SUPPORTED);
            AsyncResult.forMessage(response).exception = ce;
            response.sendToTarget();
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void setOutgoingCallerIdDisplay(int commandInterfaceCLIRMode, Message onComplete) {
        Rlog.e(LOG_TAG, "setOutgoingCallerIdDisplay: not possible in CDMA");
    }

    @Override // com.android.internal.telephony.Phone
    public void enableLocationUpdates() {
        this.mSST.enableLocationUpdates();
    }

    @Override // com.android.internal.telephony.Phone
    public void disableLocationUpdates() {
        this.mSST.disableLocationUpdates();
    }

    @Override // com.android.internal.telephony.Phone
    public void getDataCallList(Message response) {
        this.mCi.getDataCallList(response);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean getDataRoamingEnabled() {
        return this.mDcTracker.getDataOnRoamingEnabled();
    }

    @Override // com.android.internal.telephony.Phone
    public void setVoiceMailNumber(String alphaTag, String voiceMailNumber, Message onComplete) {
        this.mVmNumber = voiceMailNumber;
        Message resp = obtainMessage(20, 0, 0, onComplete);
        IccRecords r = this.mIccRecords.get();
        if (r != null) {
            r.setVoiceMailNumber(alphaTag, this.mVmNumber, resp);
        }
    }

    @Override // com.android.internal.telephony.Phone
    public String getVoiceMailNumber() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        if (getContext().getResources().getBoolean(R.bool.config_automatic_brightness_available)) {
            String number = sp.getString(VM_NUMBER_CDMA, getLine1Number());
            return number;
        }
        String number2 = sp.getString(VM_NUMBER_CDMA, "*86");
        return number2;
    }

    protected void updateVoiceMail() {
        setVoiceMessageCount(getStoredVoiceMessageCount());
    }

    @Override // com.android.internal.telephony.Phone
    public String getVoiceMailAlphaTag() {
        if ("" != 0 && "".length() != 0) {
            return "";
        }
        String ret = this.mContext.getText(R.string.defaultVoiceMailAlphaTag).toString();
        return ret;
    }

    @Override // com.android.internal.telephony.Phone
    public void getCallForwardingOption(int commandInterfaceCFReason, Message onComplete) {
        Rlog.e(LOG_TAG, "getCallForwardingOption: not possible in CDMA");
    }

    @Override // com.android.internal.telephony.Phone
    public void setCallForwardingOption(int commandInterfaceCFAction, int commandInterfaceCFReason, String dialingNumber, int timerSeconds, Message onComplete) {
        Rlog.e(LOG_TAG, "setCallForwardingOption: not possible in CDMA");
    }

    @Override // com.android.internal.telephony.Phone
    public void getOutgoingCallerIdDisplay(Message onComplete) {
        Rlog.e(LOG_TAG, "getOutgoingCallerIdDisplay: not possible in CDMA");
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public boolean getCallForwardingIndicator() {
        Rlog.e(LOG_TAG, "getCallForwardingIndicator: not possible in CDMA");
        return false;
    }

    @Override // com.android.internal.telephony.Phone
    public void explicitCallTransfer() {
        Rlog.e(LOG_TAG, "explicitCallTransfer: not possible in CDMA");
    }

    @Override // com.android.internal.telephony.Phone
    public String getLine1AlphaTag() {
        Rlog.e(LOG_TAG, "getLine1AlphaTag: not possible in CDMA");
        return null;
    }

    void notifyPhoneStateChanged() {
        this.mNotifier.notifyPhoneState(this);
    }

    void notifyPreciseCallStateChanged() {
        super.notifyPreciseCallStateChangedP();
    }

    void notifyServiceStateChanged(ServiceState ss) {
        super.notifyServiceStateChangedP(ss);
    }

    void notifyLocationChanged() {
        this.mNotifier.notifyCellLocation(this);
    }

    void notifyNewRingingConnection(Connection c) {
        super.notifyNewRingingConnectionP(c);
    }

    void notifyDisconnect(Connection cn) {
        this.mDisconnectRegistrants.notifyResult(cn);
    }

    void notifyUnknownConnection() {
        this.mUnknownConnectionRegistrants.notifyResult(this);
    }

    @Override // com.android.internal.telephony.PhoneBase
    public boolean isInEmergencyCall() {
        return this.mCT.isInEmergencyCall();
    }

    @Override // com.android.internal.telephony.PhoneBase
    public boolean isInEcm() {
        return this.mIsPhoneInEcmState;
    }

    protected void sendEmergencyCallbackModeChange() {
        Intent intent = new Intent("android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED");
        intent.putExtra("phoneinECMState", this.mIsPhoneInEcmState);
        ActivityManagerNative.broadcastStickyIntent(intent, (String) null, -1);
        Rlog.d(LOG_TAG, "sendEmergencyCallbackModeChange");
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void exitEmergencyCallbackMode() {
        if (this.mWakeLock.isHeld()) {
            this.mWakeLock.release();
        }
        this.mCi.exitEmergencyCallbackMode(obtainMessage(26));
    }

    private void handleEnterEmergencyCallbackMode(Message msg) {
        Rlog.d(LOG_TAG, "handleEnterEmergencyCallbackMode,mIsPhoneInEcmState= " + this.mIsPhoneInEcmState);
        if (!this.mIsPhoneInEcmState) {
            this.mIsPhoneInEcmState = true;
            sendEmergencyCallbackModeChange();
            super.setSystemProperty("ril.cdma.inecmmode", "true");
            long delayInMillis = SystemProperties.getLong("ro.cdma.ecmexittimer", 300000L);
            postDelayed(this.mExitEcmRunnable, delayInMillis);
            this.mWakeLock.acquire();
        }
    }

    private void handleExitEmergencyCallbackMode(Message msg) {
        AsyncResult ar = (AsyncResult) msg.obj;
        Rlog.d(LOG_TAG, "handleExitEmergencyCallbackMode,ar.exception , mIsPhoneInEcmState " + ar.exception + this.mIsPhoneInEcmState);
        removeCallbacks(this.mExitEcmRunnable);
        if (this.mEcmExitRespRegistrant != null) {
            this.mEcmExitRespRegistrant.notifyRegistrant(ar);
        }
        if (ar.exception == null) {
            if (this.mIsPhoneInEcmState) {
                this.mIsPhoneInEcmState = false;
                super.setSystemProperty("ril.cdma.inecmmode", "false");
            }
            sendEmergencyCallbackModeChange();
            this.mDcTracker.setInternalDataEnabled(true);
        }
    }

    void handleTimerInEmergencyCallbackMode(int action) {
        switch (action) {
            case 0:
                long delayInMillis = SystemProperties.getLong("ro.cdma.ecmexittimer", 300000L);
                postDelayed(this.mExitEcmRunnable, delayInMillis);
                this.mEcmTimerResetRegistrants.notifyResult(Boolean.FALSE);
                break;
            case 1:
                removeCallbacks(this.mExitEcmRunnable);
                this.mEcmTimerResetRegistrants.notifyResult(Boolean.TRUE);
                break;
            default:
                Rlog.e(LOG_TAG, "handleTimerInEmergencyCallbackMode, unsupported action " + action);
                break;
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void registerForEcmTimerReset(Handler h, int what, Object obj) {
        this.mEcmTimerResetRegistrants.addUnique(h, what, obj);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void unregisterForEcmTimerReset(Handler h) {
        this.mEcmTimerResetRegistrants.remove(h);
    }

    @Override // com.android.internal.telephony.PhoneBase, android.os.Handler
    public void handleMessage(Message msg) {
        if (!this.mIsTheCurrentActivePhone) {
            Rlog.e(LOG_TAG, "Received message " + msg + "[" + msg.what + "] while being destroyed. Ignoring.");
        }
        switch (msg.what) {
            case 1:
                this.mCi.getBasebandVersion(obtainMessage(6));
                this.mCi.getDeviceIdentity(obtainMessage(21));
                break;
            case 2:
                Rlog.d(LOG_TAG, "Event EVENT_SSN Received");
                break;
            case 3:
            case 4:
            case 7:
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
            case 18:
            case SmsHeader.ELT_ID_STANDARD_WVG_OBJECT /* 24 */:
            default:
                super.handleMessage(msg);
                break;
            case 5:
                Rlog.d(LOG_TAG, "Event EVENT_RADIO_ON Received");
                handleCdmaSubscriptionSource(this.mCdmaSSM.getCdmaSubscriptionSource());
                break;
            case 6:
                AsyncResult ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    Rlog.d(LOG_TAG, "Baseband version: " + ar.result);
                    setSystemProperty("gsm.version.baseband", (String) ar.result);
                }
                break;
            case 8:
                Rlog.d(LOG_TAG, "Event EVENT_RADIO_OFF_OR_NOT_AVAILABLE Received");
                break;
            case 19:
                Rlog.d(LOG_TAG, "Event EVENT_REGISTERED_TO_NETWORK Received");
                break;
            case 20:
                AsyncResult ar2 = (AsyncResult) msg.obj;
                if (IccException.class.isInstance(ar2.exception)) {
                    storeVoiceMailNumber(this.mVmNumber);
                    ar2.exception = null;
                }
                Message onComplete = (Message) ar2.userObj;
                if (onComplete != null) {
                    AsyncResult.forMessage(onComplete, ar2.result, ar2.exception);
                    onComplete.sendToTarget();
                }
                break;
            case 21:
                AsyncResult ar3 = (AsyncResult) msg.obj;
                if (ar3.exception == null) {
                    String[] respId = (String[]) ar3.result;
                    this.mImei = respId[0];
                    this.mImeiSv = respId[1];
                    this.mEsn = respId[2];
                    this.mMeid = respId[3];
                }
                break;
            case 22:
                Rlog.d(LOG_TAG, "Event EVENT_RUIM_RECORDS_LOADED Received");
                break;
            case 23:
                Rlog.d(LOG_TAG, "Event EVENT_NV_READY Received");
                prepareEri();
                break;
            case SmsHeader.ELT_ID_CHARACTER_SIZE_WVG_OBJECT /* 25 */:
                handleEnterEmergencyCallbackMode(msg);
                break;
            case SmsHeader.ELT_ID_EXTENDED_OBJECT_DATA_REQUEST_CMD /* 26 */:
                handleExitEmergencyCallbackMode(msg);
                break;
            case 27:
                Rlog.d(LOG_TAG, "EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED");
                handleCdmaSubscriptionSource(this.mCdmaSSM.getCdmaSubscriptionSource());
                break;
        }
    }

    protected UiccCardApplication getUiccCardApplication() {
        return this.mUiccController.getUiccCardApplication(2);
    }

    @Override // com.android.internal.telephony.PhoneBase
    protected void setCardInPhoneBook() {
        if (this.mUiccController != null) {
            this.mRuimPhoneBookInterfaceManager.setIccCard(this.mUiccController.getUiccCard());
        }
    }

    @Override // com.android.internal.telephony.PhoneBase
    protected void onUpdateIccAvailability() {
        if (this.mUiccController != null) {
            setCardInPhoneBook();
            UiccCardApplication newUiccApplication = getUiccCardApplication();
            if (newUiccApplication == null) {
                log("can't find 3GPP2 application; trying APP_FAM_3GPP");
                newUiccApplication = this.mUiccController.getUiccCardApplication(1);
            }
            UiccCardApplication app = this.mUiccApplication.get();
            if (app != newUiccApplication) {
                if (app != null) {
                    log("Removing stale icc objects.");
                    if (this.mIccRecords.get() != null) {
                        unregisterForRuimRecordEvents();
                    }
                    this.mIccRecords.set(null);
                    this.mUiccApplication.set(null);
                }
                if (newUiccApplication != null) {
                    log("New Uicc application found");
                    this.mUiccApplication.set(newUiccApplication);
                    this.mIccRecords.set(newUiccApplication.getIccRecords());
                    registerForRuimRecordEvents();
                }
            }
        }
    }

    private void handleCdmaSubscriptionSource(int newSubscriptionSource) {
        if (newSubscriptionSource != this.mCdmaSubscriptionSource) {
            this.mCdmaSubscriptionSource = newSubscriptionSource;
            if (newSubscriptionSource == 1) {
                sendMessage(obtainMessage(23));
            }
        }
    }

    @Override // com.android.internal.telephony.Phone
    public PhoneSubInfo getPhoneSubInfo() {
        return this.mSubInfo;
    }

    @Override // com.android.internal.telephony.Phone
    public IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager() {
        return this.mRuimPhoneBookInterfaceManager;
    }

    public void registerForEriFileLoaded(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mEriFileLoadedRegistrants.add(r);
    }

    public void unregisterForEriFileLoaded(Handler h) {
        this.mEriFileLoadedRegistrants.remove(h);
    }

    @Override // com.android.internal.telephony.PhoneBase
    public void setSystemProperty(String property, String value) {
        super.setSystemProperty(property, value);
    }

    @Override // com.android.internal.telephony.Phone
    public void activateCellBroadcastSms(int activate, Message response) {
        Rlog.e(LOG_TAG, "[CDMAPhone] activateCellBroadcastSms() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    @Override // com.android.internal.telephony.Phone
    public void getCellBroadcastSmsConfig(Message response) {
        Rlog.e(LOG_TAG, "[CDMAPhone] getCellBroadcastSmsConfig() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    @Override // com.android.internal.telephony.Phone
    public void setCellBroadcastSmsConfig(int[] configValuesArray, Message response) {
        Rlog.e(LOG_TAG, "[CDMAPhone] setCellBroadcastSmsConfig() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public boolean needsOtaServiceProvisioning() {
        return this.mSST.getOtasp() != 3;
    }

    private static boolean isIs683OtaSpDialStr(String dialStr) {
        int dialStrLen = dialStr.length();
        if (dialStrLen == 4) {
            if (!dialStr.equals(IS683A_FEATURE_CODE)) {
                return false;
            }
            return true;
        }
        int sysSelCodeInt = extractSelCodeFromOtaSpNum(dialStr);
        switch (sysSelCodeInt) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
                return true;
            default:
                return false;
        }
    }

    private static int extractSelCodeFromOtaSpNum(String dialStr) {
        int dialStrLen = dialStr.length();
        int sysSelCodeInt = -1;
        if (dialStr.regionMatches(0, IS683A_FEATURE_CODE, 0, 4) && dialStrLen >= 6) {
            sysSelCodeInt = Integer.parseInt(dialStr.substring(4, 6));
        }
        Rlog.d(LOG_TAG, "extractSelCodeFromOtaSpNum " + sysSelCodeInt);
        return sysSelCodeInt;
    }

    private static boolean checkOtaSpNumBasedOnSysSelCode(int sysSelCodeInt, String[] sch) {
        try {
            int selRc = Integer.parseInt(sch[1]);
            for (int i = 0; i < selRc; i++) {
                if (!TextUtils.isEmpty(sch[i + 2]) && !TextUtils.isEmpty(sch[i + 3])) {
                    int selMin = Integer.parseInt(sch[i + 2]);
                    int selMax = Integer.parseInt(sch[i + 3]);
                    if (sysSelCodeInt >= selMin && sysSelCodeInt <= selMax) {
                        return true;
                    }
                }
            }
            return false;
        } catch (NumberFormatException ex) {
            Rlog.e(LOG_TAG, "checkOtaSpNumBasedOnSysSelCode, error", ex);
            return false;
        }
    }

    private boolean isCarrierOtaSpNum(String dialStr) {
        boolean isOtaSpNum = false;
        int sysSelCodeInt = extractSelCodeFromOtaSpNum(dialStr);
        if (sysSelCodeInt == -1) {
            return false;
        }
        if (!TextUtils.isEmpty(this.mCarrierOtaSpNumSchema)) {
            Matcher m = pOtaSpNumSchema.matcher(this.mCarrierOtaSpNumSchema);
            Rlog.d(LOG_TAG, "isCarrierOtaSpNum,schema" + this.mCarrierOtaSpNumSchema);
            if (m.find()) {
                String[] sch = pOtaSpNumSchema.split(this.mCarrierOtaSpNumSchema);
                if (!TextUtils.isEmpty(sch[0]) && sch[0].equals("SELC")) {
                    if (sysSelCodeInt != -1) {
                        isOtaSpNum = checkOtaSpNumBasedOnSysSelCode(sysSelCodeInt, sch);
                    } else {
                        Rlog.d(LOG_TAG, "isCarrierOtaSpNum,sysSelCodeInt is invalid");
                    }
                } else if (!TextUtils.isEmpty(sch[0]) && sch[0].equals("FC")) {
                    int fcLen = Integer.parseInt(sch[1]);
                    String fc = sch[2];
                    if (dialStr.regionMatches(0, fc, 0, fcLen)) {
                        isOtaSpNum = true;
                    } else {
                        Rlog.d(LOG_TAG, "isCarrierOtaSpNum,not otasp number");
                    }
                } else {
                    Rlog.d(LOG_TAG, "isCarrierOtaSpNum,ota schema not supported" + sch[0]);
                }
            } else {
                Rlog.d(LOG_TAG, "isCarrierOtaSpNum,ota schema pattern not right" + this.mCarrierOtaSpNumSchema);
            }
        } else {
            Rlog.d(LOG_TAG, "isCarrierOtaSpNum,ota schema pattern empty");
        }
        return isOtaSpNum;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public boolean isOtaSpNumber(String dialStr) {
        boolean isOtaSpNum = false;
        String dialableStr = PhoneNumberUtils.extractNetworkPortionAlt(dialStr);
        if (dialableStr != null && !(isOtaSpNum = isIs683OtaSpDialStr(dialableStr))) {
            isOtaSpNum = isCarrierOtaSpNum(dialableStr);
        }
        Rlog.d(LOG_TAG, "isOtaSpNumber " + isOtaSpNum);
        return isOtaSpNum;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public int getCdmaEriIconIndex() {
        return getServiceState().getCdmaEriIconIndex();
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public int getCdmaEriIconMode() {
        return getServiceState().getCdmaEriIconMode();
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public String getCdmaEriText() {
        int roamInd = getServiceState().getCdmaRoamingIndicator();
        int defRoamInd = getServiceState().getCdmaDefaultRoamingIndicator();
        return this.mEriManager.getCdmaEriText(roamInd, defRoamInd);
    }

    protected void storeVoiceMailNumber(String number) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(VM_NUMBER_CDMA, number);
        editor.apply();
    }

    protected void setIsoCountryProperty(String operatorNumeric) {
        if (TextUtils.isEmpty(operatorNumeric)) {
            log("setIsoCountryProperty: clear 'gsm.sim.operator.iso-country'");
            setSystemProperty("gsm.sim.operator.iso-country", "");
            return;
        }
        String iso = "";
        try {
            iso = MccTable.countryCodeForMcc(Integer.parseInt(operatorNumeric.substring(0, 3)));
        } catch (NumberFormatException ex) {
            loge("setIsoCountryProperty: countryCodeForMcc error", ex);
        } catch (StringIndexOutOfBoundsException ex2) {
            loge("setIsoCountryProperty: countryCodeForMcc error", ex2);
        }
        log("setIsoCountryProperty: set 'gsm.sim.operator.iso-country' to iso=" + iso);
        setSystemProperty("gsm.sim.operator.iso-country", iso);
    }

    public boolean updateCurrentCarrierInProvider(String operatorNumeric) {
        log("CDMAPhone: updateCurrentCarrierInProvider called");
        if (TextUtils.isEmpty(operatorNumeric)) {
            return false;
        }
        try {
            Uri uri = Uri.withAppendedPath(Telephony.Carriers.CONTENT_URI, Telephony.Carriers.CURRENT);
            ContentValues map = new ContentValues();
            map.put("numeric", operatorNumeric);
            log("updateCurrentCarrierInProvider with: numeric=" + operatorNumeric);
            getContext().getContentResolver().insert(uri, map);
            MccTable.updateMccMncConfiguration(this.mContext, operatorNumeric, false);
            return true;
        } catch (SQLException e) {
            Rlog.e(LOG_TAG, "Can't store current operator", e);
            return false;
        }
    }

    boolean updateCurrentCarrierInProvider() {
        return true;
    }

    public void prepareEri() {
        if (this.mEriManager == null) {
            Rlog.e(LOG_TAG, "PrepareEri: Trying to access stale objects");
            return;
        }
        this.mEriManager.loadEriFile();
        if (this.mEriManager.isEriFileLoaded()) {
            log("ERI read, notify registrants");
            this.mEriFileLoadedRegistrants.notifyRegistrants();
        }
    }

    public boolean isEriFileLoaded() {
        return this.mEriManager.isEriFileLoaded();
    }

    protected void registerForRuimRecordEvents() {
        IccRecords r = this.mIccRecords.get();
        if (r != null) {
            r.registerForRecordsLoaded(this, 22, null);
        }
    }

    protected void unregisterForRuimRecordEvents() {
        IccRecords r = this.mIccRecords.get();
        if (r != null) {
            r.unregisterForRecordsLoaded(this);
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setVoiceMessageWaiting(int line, int countWaiting) {
        setVoiceMessageCount(countWaiting);
    }

    protected void log(String s) {
        Rlog.d(LOG_TAG, s);
    }

    protected void loge(String s, Exception e) {
        Rlog.e(LOG_TAG, s, e);
    }

    @Override // com.android.internal.telephony.PhoneBase
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("CDMAPhone extends:");
        super.dump(fd, pw, args);
        pw.println(" mVmNumber=" + this.mVmNumber);
        pw.println(" mCT=" + this.mCT);
        pw.println(" mSST=" + this.mSST);
        pw.println(" mCdmaSSM=" + this.mCdmaSSM);
        pw.println(" mPendingMmis=" + this.mPendingMmis);
        pw.println(" mRuimPhoneBookInterfaceManager=" + this.mRuimPhoneBookInterfaceManager);
        pw.println(" mCdmaSubscriptionSource=" + this.mCdmaSubscriptionSource);
        pw.println(" mSubInfo=" + this.mSubInfo);
        pw.println(" mEriManager=" + this.mEriManager);
        pw.println(" mWakeLock=" + this.mWakeLock);
        pw.println(" mIsPhoneInEcmState=" + this.mIsPhoneInEcmState);
        pw.println(" mCarrierOtaSpNumSchema=" + this.mCarrierOtaSpNumSchema);
        pw.println(" getCdmaEriIconIndex()=" + getCdmaEriIconIndex());
        pw.println(" getCdmaEriIconMode()=" + getCdmaEriIconMode());
        pw.println(" getCdmaEriText()=" + getCdmaEriText());
        pw.println(" isMinInfoReady()=" + isMinInfoReady());
        pw.println(" isCspPlmnEnabled()=" + isCspPlmnEnabled());
    }
}
