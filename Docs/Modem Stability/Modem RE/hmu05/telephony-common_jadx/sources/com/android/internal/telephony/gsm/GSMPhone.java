package com.android.internal.telephony.gsm;

import android.R;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
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
import android.text.TextUtils;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.PhoneSubInfo;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.test.SimulatedRadioControl;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccVmNotSupportedException;
import com.android.internal.telephony.uicc.UiccCardApplication;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class GSMPhone extends PhoneBase {
    public static final String CF_ENABLED = "cf_enabled_key";
    private static final int CHECK_CALLFORWARDING_STATUS = 75;
    public static final String CIPHERING_KEY = "ciphering_key";
    private static final boolean DBG_PORT = false;
    private static final boolean LOCAL_DEBUG = true;
    protected static final String LOG_TAG = "GSMPhone";
    private static final boolean VDBG = false;
    public static final String VM_NUMBER = "vm_number_key";
    public static final String VM_SIM_IMSI = "vm_sim_imsi_key";
    GsmCallTracker mCT;
    Thread mDebugPortThread;
    ServerSocket mDebugSocket;
    protected String mImei;
    protected String mImeiSv;
    ArrayList<GsmMmiCode> mPendingMMIs;
    Registrant mPostDialHandler;
    protected GsmServiceStateTracker mSST;
    protected SimPhoneBookInterfaceManager mSimPhoneBookIntManager;
    RegistrantList mSsnRegistrants;
    PhoneSubInfo mSubInfo;
    private String mVmNumber;

    private static class Cfu {
        final Message mOnComplete;
        final String mSetCfNumber;

        Cfu(String cfNumber, Message onComplete) {
            this.mSetCfNumber = cfNumber;
            this.mOnComplete = onComplete;
        }
    }

    public GSMPhone(Context context, CommandsInterface ci, PhoneNotifier notifier) {
        this(context, ci, notifier, false);
    }

    public GSMPhone(Context context, CommandsInterface ci, PhoneNotifier notifier, boolean unitTestMode) {
        super("GSM", notifier, context, ci, unitTestMode);
        this.mPendingMMIs = new ArrayList<>();
        this.mSsnRegistrants = new RegistrantList();
        if (ci instanceof SimulatedRadioControl) {
            this.mSimulatedRadioControl = (SimulatedRadioControl) ci;
        }
        this.mCi.setPhoneType(1);
        this.mCT = new GsmCallTracker(this);
        initSubscriptionSpecifics();
        if (!unitTestMode) {
            this.mSimPhoneBookIntManager = new SimPhoneBookInterfaceManager(this);
            this.mSubInfo = new PhoneSubInfo(this);
        }
        this.mCi.registerForAvailable(this, 1, null);
        this.mCi.registerForOffOrNotAvailable(this, 8, null);
        this.mCi.registerForOn(this, 5, null);
        this.mCi.setOnUSSD(this, 7, null);
        this.mCi.setOnSuppServiceNotification(this, 2, null);
        this.mSST.registerForNetworkAttached(this, 19, null);
        this.mCi.setOnSs(this, 31, null);
        setProperties();
    }

    protected void setProperties() {
        SystemProperties.set("gsm.current.phone-type", new Integer(1).toString());
    }

    protected void initSubscriptionSpecifics() {
        this.mSST = new GsmServiceStateTracker(this);
        this.mDcTracker = new DcTracker(this);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void dispose() {
        synchronized (PhoneProxy.lockForRadioTechnologyChange) {
            super.dispose();
            this.mCi.unregisterForAvailable(this);
            unregisterForSimRecordEvents();
            this.mCi.unregisterForOffOrNotAvailable(this);
            this.mCi.unregisterForOn(this);
            this.mSST.unregisterForNetworkAttached(this);
            this.mCi.unSetOnUSSD(this);
            this.mCi.unSetOnSuppServiceNotification(this);
            this.mCi.unSetOnUSSD(this);
            this.mCi.unSetOnSs(this);
            this.mPendingMMIs.clear();
            this.mCT.dispose();
            this.mDcTracker.dispose();
            this.mSST.dispose();
            if (this.mSimPhoneBookIntManager != null) {
                this.mSimPhoneBookIntManager.dispose();
            }
            this.mSubInfo.dispose();
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void removeReferences() {
        Rlog.d(LOG_TAG, "removeReferences");
        this.mSimulatedRadioControl = null;
        this.mSimPhoneBookIntManager = null;
        this.mSubInfo = null;
        this.mCT = null;
        this.mSST = null;
        super.removeReferences();
    }

    protected void finalize() {
        Rlog.d(LOG_TAG, "GSMPhone finalized");
    }

    @Override // com.android.internal.telephony.Phone
    public ServiceState getServiceState() {
        return this.mSST != null ? this.mSST.mSS : new ServiceState();
    }

    @Override // com.android.internal.telephony.Phone
    public CellLocation getCellLocation() {
        return this.mSST.getCellLocation();
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public PhoneConstants.State getState() {
        return this.mCT.mState;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public int getPhoneType() {
        return 1;
    }

    @Override // com.android.internal.telephony.PhoneBase
    public ServiceStateTracker getServiceStateTracker() {
        return this.mSST;
    }

    @Override // com.android.internal.telephony.PhoneBase
    public CallTracker getCallTracker() {
        return this.mCT;
    }

    private void updateVoiceMail() {
        int countVoiceMessages = 0;
        IccRecords r = this.mIccRecords.get();
        if (r != null) {
            countVoiceMessages = r.getVoiceMessageCount();
        }
        if (countVoiceMessages == -1) {
            countVoiceMessages = getStoredVoiceMessageCount();
        }
        Rlog.d(LOG_TAG, "updateVoiceMail countVoiceMessages = " + countVoiceMessages);
        setVoiceMessageCount(countVoiceMessages);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public boolean getCallForwardingIndicator() {
        boolean cf = false;
        IccRecords r = this.mIccRecords.get();
        if (r != null) {
            cf = r.getVoiceCallForwardingFlag();
        }
        if (!cf) {
            boolean cf2 = getCallForwardingPreference();
            return cf2;
        }
        return cf;
    }

    @Override // com.android.internal.telephony.Phone
    public List<? extends MmiCode> getPendingMmiCodes() {
        return this.mPendingMMIs;
    }

    @Override // com.android.internal.telephony.Phone
    public PhoneConstants.DataState getDataConnectionState(String apnType) {
        PhoneConstants.DataState ret = PhoneConstants.DataState.DISCONNECTED;
        if (this.mSST == null) {
            PhoneConstants.DataState ret2 = PhoneConstants.DataState.DISCONNECTED;
            return ret2;
        }
        if (this.mSST.getCurrentDataConnectionState() != 0 && this.mOosIsDisconnect) {
            PhoneConstants.DataState ret3 = PhoneConstants.DataState.DISCONNECTED;
            Rlog.d(LOG_TAG, "getDataConnectionState: Data is Out of Service. ret = " + ret3);
            return ret3;
        }
        if (!this.mDcTracker.isApnTypeEnabled(apnType) || !this.mDcTracker.isApnTypeActive(apnType)) {
            PhoneConstants.DataState ret4 = PhoneConstants.DataState.DISCONNECTED;
            return ret4;
        }
        switch (AnonymousClass2.$SwitchMap$com$android$internal$telephony$DctConstants$State[this.mDcTracker.getState(apnType).ordinal()]) {
            case 1:
            case 2:
            case 3:
                PhoneConstants.DataState ret5 = PhoneConstants.DataState.DISCONNECTED;
                return ret5;
            case 4:
            case 5:
                if (this.mCT.mState != PhoneConstants.State.IDLE && !this.mSST.isConcurrentVoiceAndDataAllowed()) {
                    PhoneConstants.DataState ret6 = PhoneConstants.DataState.SUSPENDED;
                    return ret6;
                }
                PhoneConstants.DataState ret7 = PhoneConstants.DataState.CONNECTED;
                return ret7;
            case 6:
            case 7:
                PhoneConstants.DataState ret8 = PhoneConstants.DataState.CONNECTING;
                return ret8;
            default:
                return ret;
        }
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

    /* JADX INFO: renamed from: com.android.internal.telephony.gsm.GSMPhone$2, reason: invalid class name */
    static /* synthetic */ class AnonymousClass2 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DctConstants$Activity = new int[DctConstants.Activity.values().length];
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DctConstants$State;

        static {
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[DctConstants.Activity.DATAIN.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[DctConstants.Activity.DATAOUT.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[DctConstants.Activity.DATAINANDOUT.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$Activity[DctConstants.Activity.DORMANT.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            $SwitchMap$com$android$internal$telephony$DctConstants$State = new int[DctConstants.State.values().length];
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.RETRYING.ordinal()] = 1;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.FAILED.ordinal()] = 2;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.IDLE.ordinal()] = 3;
            } catch (NoSuchFieldError e7) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.CONNECTED.ordinal()] = 4;
            } catch (NoSuchFieldError e8) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.DISCONNECTING.ordinal()] = 5;
            } catch (NoSuchFieldError e9) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.CONNECTING.ordinal()] = 6;
            } catch (NoSuchFieldError e10) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.SCANNING.ordinal()] = 7;
            } catch (NoSuchFieldError e11) {
            }
        }
    }

    void notifyPhoneStateChanged() {
        this.mNotifier.notifyPhoneState(this);
    }

    void notifyPreciseCallStateChanged() {
        super.notifyPreciseCallStateChangedP();
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

    void notifySuppServiceFailed(Phone.SuppService code) {
        this.mSuppServiceFailedRegistrants.notifyResult(code);
    }

    void notifyServiceStateChanged(ServiceState ss) {
        super.notifyServiceStateChangedP(ss);
    }

    void notifyLocationChanged() {
        this.mNotifier.notifyCellLocation(this);
    }

    @Override // com.android.internal.telephony.PhoneBase
    public void notifyCallForwardingIndicator() {
        this.mNotifier.notifyCallForwardingChanged(this);
    }

    @Override // com.android.internal.telephony.PhoneBase
    public void setSystemProperty(String property, String value) {
        super.setSystemProperty(property, value);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForSuppServiceNotification(Handler h, int what, Object obj) {
        this.mSsnRegistrants.addUnique(h, what, obj);
        if (this.mSsnRegistrants.size() == 1) {
            this.mCi.setSuppServiceNotifications(true, null);
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForSuppServiceNotification(Handler h) {
        this.mSsnRegistrants.remove(h);
        if (this.mSsnRegistrants.size() == 0) {
            this.mCi.setSuppServiceNotifications(false, null);
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void registerForSimRecordsLoaded(Handler h, int what, Object obj) {
        this.mSimRecordsLoadedRegistrants.addUnique(h, what, obj);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void unregisterForSimRecordsLoaded(Handler h) {
        this.mSimRecordsLoadedRegistrants.remove(h);
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

    @Override // com.android.internal.telephony.Phone
    public boolean canConference() {
        return this.mCT.canConference();
    }

    public boolean canDial() {
        return this.mCT.canDial();
    }

    @Override // com.android.internal.telephony.Phone
    public void conference() {
        this.mCT.conference();
    }

    @Override // com.android.internal.telephony.Phone
    public void clearDisconnected() {
        this.mCT.clearDisconnected();
    }

    @Override // com.android.internal.telephony.Phone
    public boolean canTransfer() {
        return this.mCT.canTransfer();
    }

    @Override // com.android.internal.telephony.Phone
    public void explicitCallTransfer() {
        this.mCT.explicitCallTransfer();
    }

    @Override // com.android.internal.telephony.Phone
    public GsmCall getForegroundCall() {
        return this.mCT.mForegroundCall;
    }

    @Override // com.android.internal.telephony.Phone
    public GsmCall getBackgroundCall() {
        return this.mCT.mBackgroundCall;
    }

    @Override // com.android.internal.telephony.Phone
    public GsmCall getRingingCall() {
        return this.mCT.mRingingCall;
    }

    private boolean handleCallDeflectionIncallSupplementaryService(String dialString) {
        if (dialString.length() > 1) {
            return false;
        }
        if (getRingingCall().getState() != Call.State.IDLE) {
            Rlog.d(LOG_TAG, "MmiCode 0: rejectCall");
            try {
                this.mCT.rejectCall();
                return true;
            } catch (CallStateException e) {
                Rlog.d(LOG_TAG, "reject failed", e);
                notifySuppServiceFailed(Phone.SuppService.REJECT);
                return true;
            }
        }
        if (getBackgroundCall().getState() == Call.State.IDLE) {
            return true;
        }
        Rlog.d(LOG_TAG, "MmiCode 0: hangupWaitingOrBackground");
        this.mCT.hangupWaitingOrBackground();
        return true;
    }

    private boolean handleCallWaitingIncallSupplementaryService(String dialString) {
        int len = dialString.length();
        if (len > 2) {
            return false;
        }
        GsmCall call = getForegroundCall();
        try {
            if (len > 1) {
                char ch = dialString.charAt(1);
                int callIndex = ch - '0';
                if (callIndex >= 1 && callIndex <= 7) {
                    Rlog.d(LOG_TAG, "MmiCode 1: hangupConnectionByIndex " + callIndex);
                    this.mCT.hangupConnectionByIndex(call, callIndex);
                }
            } else if (call.getState() != Call.State.IDLE) {
                Rlog.d(LOG_TAG, "MmiCode 1: hangup foreground");
                this.mCT.hangup(call);
            } else {
                Rlog.d(LOG_TAG, "MmiCode 1: switchWaitingOrHoldingAndActive");
                this.mCT.switchWaitingOrHoldingAndActive();
            }
            return true;
        } catch (CallStateException e) {
            Rlog.d(LOG_TAG, "hangup failed", e);
            notifySuppServiceFailed(Phone.SuppService.HANGUP);
            return true;
        }
    }

    private boolean handleCallHoldIncallSupplementaryService(String dialString) {
        int len = dialString.length();
        if (len > 2) {
            return false;
        }
        GsmCall call = getForegroundCall();
        if (len > 1) {
            try {
                char ch = dialString.charAt(1);
                int callIndex = ch - '0';
                GsmConnection conn = this.mCT.getConnectionByIndex(call, callIndex);
                if (conn != null && callIndex >= 1 && callIndex <= 7) {
                    Rlog.d(LOG_TAG, "MmiCode 2: separate call " + callIndex);
                    this.mCT.separate(conn);
                } else {
                    Rlog.d(LOG_TAG, "separate: invalid call index " + callIndex);
                    notifySuppServiceFailed(Phone.SuppService.SEPARATE);
                }
                return true;
            } catch (CallStateException e) {
                Rlog.d(LOG_TAG, "separate failed", e);
                notifySuppServiceFailed(Phone.SuppService.SEPARATE);
                return true;
            }
        }
        try {
            if (getRingingCall().getState() != Call.State.IDLE) {
                Rlog.d(LOG_TAG, "MmiCode 2: accept ringing call");
                this.mCT.acceptCall();
            } else {
                Rlog.d(LOG_TAG, "MmiCode 2: switchWaitingOrHoldingAndActive");
                this.mCT.switchWaitingOrHoldingAndActive();
            }
            return true;
        } catch (CallStateException e2) {
            Rlog.d(LOG_TAG, "switch failed", e2);
            notifySuppServiceFailed(Phone.SuppService.SWITCH);
            return true;
        }
    }

    private boolean handleMultipartyIncallSupplementaryService(String dialString) {
        if (dialString.length() > 1) {
            return false;
        }
        Rlog.d(LOG_TAG, "MmiCode 3: merge calls");
        conference();
        return true;
    }

    private boolean handleEctIncallSupplementaryService(String dialString) {
        int len = dialString.length();
        if (len != 1) {
            return false;
        }
        Rlog.d(LOG_TAG, "MmiCode 4: explicit call transfer");
        explicitCallTransfer();
        return true;
    }

    private boolean handleCcbsIncallSupplementaryService(String dialString) {
        if (dialString.length() > 1) {
            return false;
        }
        Rlog.i(LOG_TAG, "MmiCode 5: CCBS not supported!");
        notifySuppServiceFailed(Phone.SuppService.UNKNOWN);
        return true;
    }

    @Override // com.android.internal.telephony.Phone
    public boolean handleInCallMmiCommands(String dialString) {
        if (isInCall() && !TextUtils.isEmpty(dialString)) {
            char ch = dialString.charAt(0);
            switch (ch) {
                case '0':
                    boolean result = handleCallDeflectionIncallSupplementaryService(dialString);
                    return result;
                case CallFailCause.QOS_NOT_AVAIL /* 49 */:
                    boolean result2 = handleCallWaitingIncallSupplementaryService(dialString);
                    return result2;
                case '2':
                    boolean result3 = handleCallHoldIncallSupplementaryService(dialString);
                    return result3;
                case '3':
                    boolean result4 = handleMultipartyIncallSupplementaryService(dialString);
                    return result4;
                case '4':
                    boolean result5 = handleEctIncallSupplementaryService(dialString);
                    return result5;
                case '5':
                    boolean result6 = handleCcbsIncallSupplementaryService(dialString);
                    return result6;
                default:
                    return false;
            }
        }
        return false;
    }

    boolean isInCall() {
        Call.State foregroundCallState = getForegroundCall().getState();
        Call.State backgroundCallState = getBackgroundCall().getState();
        Call.State ringingCallState = getRingingCall().getState();
        return foregroundCallState.isAlive() || backgroundCallState.isAlive() || ringingCallState.isAlive();
    }

    @Override // com.android.internal.telephony.Phone
    public Connection dial(String dialString) throws CallStateException {
        return dial(dialString, null);
    }

    @Override // com.android.internal.telephony.Phone
    public Connection dial(String dialString, UUSInfo uusInfo) throws CallStateException {
        String newDialString = PhoneNumberUtils.stripSeparators(dialString);
        if (handleInCallMmiCommands(newDialString)) {
            return null;
        }
        String networkPortion = PhoneNumberUtils.extractNetworkPortionAlt(newDialString);
        GsmMmiCode mmi = GsmMmiCode.newFromDialString(networkPortion, this, this.mUiccApplication.get());
        Rlog.d(LOG_TAG, "dialing w/ mmi '" + mmi + "'...");
        if (mmi == null) {
            return this.mCT.dial(newDialString, uusInfo);
        }
        if (mmi.isTemporaryModeCLIR()) {
            return this.mCT.dial(mmi.mDialingNumber, mmi.getCLIRMode(), uusInfo);
        }
        this.mPendingMMIs.add(mmi);
        this.mMmiRegistrants.notifyRegistrants(new AsyncResult((Object) null, mmi, (Throwable) null));
        mmi.processCode();
        return null;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public boolean isMMI(String dialString) {
        String newDialString = PhoneNumberUtils.stripSeparators(dialString);
        String networkPortion = PhoneNumberUtils.extractNetworkPortionAlt(newDialString);
        GsmMmiCode mmi = GsmMmiCode.newFromDialString(networkPortion, this, this.mUiccApplication.get());
        return mmi != null;
    }

    @Override // com.android.internal.telephony.Phone
    public boolean handlePinMmi(String dialString) {
        GsmMmiCode mmi = GsmMmiCode.newFromDialString(dialString, this, this.mUiccApplication.get());
        if (mmi == null || !mmi.isPinPukCommand()) {
            return false;
        }
        this.mPendingMMIs.add(mmi);
        this.mMmiRegistrants.notifyRegistrants(new AsyncResult((Object) null, mmi, (Throwable) null));
        mmi.processCode();
        return true;
    }

    @Override // com.android.internal.telephony.Phone
    public void sendUssdResponse(String ussdMessge) {
        GsmMmiCode mmi = GsmMmiCode.newFromUssdUserInput(ussdMessge, this, this.mUiccApplication.get());
        this.mPendingMMIs.add(mmi);
        this.mMmiRegistrants.notifyRegistrants(new AsyncResult((Object) null, mmi, (Throwable) null));
        mmi.sendUssd(ussdMessge);
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

    public void sendBurstDtmf(String dtmfString) {
        Rlog.e(LOG_TAG, "[GSMPhone] sendBurstDtmf() is a CDMA method");
    }

    @Override // com.android.internal.telephony.Phone
    public void setRadioPower(boolean power) {
        this.mSST.setRadioPower(power);
    }

    protected void storeVoiceMailNumber(String number) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(VM_NUMBER, number);
        editor.apply();
        setVmSimImsi(getSubscriberId());
    }

    @Override // com.android.internal.telephony.Phone
    public String getVoiceMailNumber() {
        IccRecords r = this.mIccRecords.get();
        String number = r != null ? r.getVoiceMailNumber() : "";
        if (TextUtils.isEmpty(number)) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
            return sp.getString(VM_NUMBER, null);
        }
        return number;
    }

    protected String getVmSimImsi() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        return sp.getString(VM_SIM_IMSI, null);
    }

    protected void setVmSimImsi(String imsi) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(VM_SIM_IMSI, imsi);
        editor.apply();
    }

    @Override // com.android.internal.telephony.Phone
    public String getVoiceMailAlphaTag() {
        IccRecords r = this.mIccRecords.get();
        String ret = r != null ? r.getVoiceMailAlphaTag() : "";
        if (ret == null || ret.length() == 0) {
            return this.mContext.getText(R.string.defaultVoiceMailAlphaTag).toString();
        }
        return ret;
    }

    @Override // com.android.internal.telephony.Phone
    public String getDeviceId() {
        return this.mImei;
    }

    @Override // com.android.internal.telephony.Phone
    public String getDeviceSvn() {
        return this.mImeiSv;
    }

    @Override // com.android.internal.telephony.Phone
    public String getImei() {
        return this.mImei;
    }

    @Override // com.android.internal.telephony.Phone
    public String getEsn() {
        Rlog.e(LOG_TAG, "[GSMPhone] getEsn() is a CDMA method");
        return "0";
    }

    @Override // com.android.internal.telephony.Phone
    public String getMeid() {
        Rlog.e(LOG_TAG, "[GSMPhone] getMeid() is a CDMA method");
        return "0";
    }

    @Override // com.android.internal.telephony.Phone
    public String getSubscriberId() {
        IccRecords r = this.mIccRecords.get();
        if (r != null) {
            return r.getIMSI();
        }
        return null;
    }

    @Override // com.android.internal.telephony.Phone
    public String getGroupIdLevel1() {
        IccRecords r = this.mIccRecords.get();
        if (r != null) {
            return r.getGid1();
        }
        return null;
    }

    @Override // com.android.internal.telephony.Phone
    public String getLine1Number() {
        IccRecords r = this.mIccRecords.get();
        if (r != null) {
            return r.getMsisdnNumber();
        }
        return null;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public String getMsisdn() {
        IccRecords r = this.mIccRecords.get();
        if (r != null) {
            return r.getMsisdnNumber();
        }
        return null;
    }

    @Override // com.android.internal.telephony.Phone
    public String getLine1AlphaTag() {
        IccRecords r = this.mIccRecords.get();
        if (r != null) {
            return r.getMsisdnAlphaTag();
        }
        return null;
    }

    @Override // com.android.internal.telephony.Phone
    public void setLine1Number(String alphaTag, String number, Message onComplete) {
        IccRecords r = this.mIccRecords.get();
        if (r != null) {
            r.setMsisdnNumber(alphaTag, number, onComplete);
        }
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

    private boolean isValidCommandInterfaceCFReason(int commandInterfaceCFReason) {
        switch (commandInterfaceCFReason) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                return true;
            default:
                return false;
        }
    }

    private boolean isValidCommandInterfaceCFAction(int commandInterfaceCFAction) {
        switch (commandInterfaceCFAction) {
            case 0:
            case 1:
            case 3:
            case 4:
                return true;
            case 2:
            default:
                return false;
        }
    }

    protected boolean isCfEnable(int action) {
        return action == 1 || action == 3;
    }

    @Override // com.android.internal.telephony.Phone
    public void getCallForwardingOption(int commandInterfaceCFReason, Message onComplete) {
        Message resp;
        if (isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
            Rlog.d(LOG_TAG, "requesting call forwarding query.");
            if (commandInterfaceCFReason == 0) {
                resp = obtainMessage(13, onComplete);
            } else {
                resp = onComplete;
            }
            this.mCi.queryCallForwardStatus(commandInterfaceCFReason, 0, null, resp);
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void setCallForwardingOption(int commandInterfaceCFAction, int commandInterfaceCFReason, String dialingNumber, int timerSeconds, Message onComplete) {
        Message resp;
        if (isValidCommandInterfaceCFAction(commandInterfaceCFAction) && isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
            if (commandInterfaceCFReason == 0) {
                Cfu cfu = new Cfu(dialingNumber, onComplete);
                resp = obtainMessage(12, isCfEnable(commandInterfaceCFAction) ? 1 : 0, 0, cfu);
            } else {
                resp = onComplete;
            }
            this.mCi.setCallForward(commandInterfaceCFAction, commandInterfaceCFReason, 1, dialingNumber, timerSeconds, resp);
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void getOutgoingCallerIdDisplay(Message onComplete) {
        this.mCi.getCLIR(onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void setOutgoingCallerIdDisplay(int commandInterfaceCLIRMode, Message onComplete) {
        this.mCi.setCLIR(commandInterfaceCLIRMode, obtainMessage(18, commandInterfaceCLIRMode, 0, onComplete));
    }

    @Override // com.android.internal.telephony.Phone
    public void getCallWaiting(Message onComplete) {
        this.mCi.queryCallWaiting(0, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void setCallWaiting(boolean enable, Message onComplete) {
        this.mCi.setCallWaiting(enable, 1, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void getAvailableNetworks(Message response) {
        this.mCi.getAvailableNetworks(response);
    }

    private static class NetworkSelectMessage {
        public Message message;
        public String operatorAlphaLong;
        public String operatorNumeric;

        private NetworkSelectMessage() {
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void setNetworkSelectionModeAutomatic(Message response) {
        NetworkSelectMessage nsm = new NetworkSelectMessage();
        nsm.message = response;
        nsm.operatorNumeric = "";
        nsm.operatorAlphaLong = "";
        Message msg = obtainMessage(17, nsm);
        Rlog.d(LOG_TAG, "wrapping and sending message to connect automatically");
        this.mCi.setNetworkSelectionModeAutomatic(msg);
    }

    @Override // com.android.internal.telephony.Phone
    public void selectNetworkManually(OperatorInfo network, Message response) {
        NetworkSelectMessage nsm = new NetworkSelectMessage();
        nsm.message = response;
        nsm.operatorNumeric = network.getOperatorNumeric();
        nsm.operatorAlphaLong = network.getOperatorAlphaLong();
        Message msg = obtainMessage(16, nsm);
        if (network.getRadioTech().equals("")) {
            this.mCi.setNetworkSelectionModeManual(network.getOperatorNumeric(), msg);
        } else {
            this.mCi.setNetworkSelectionModeManual(network.getOperatorNumeric() + "+" + network.getRadioTech(), msg);
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void getNeighboringCids(Message response) {
        this.mCi.getNeighboringCids(response);
    }

    @Override // com.android.internal.telephony.Phone
    public void setOnPostDialCharacter(Handler h, int what, Object obj) {
        this.mPostDialHandler = new Registrant(h, what, obj);
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
    public void getDataCallList(Message response) {
        this.mCi.getDataCallList(response);
    }

    @Override // com.android.internal.telephony.Phone
    public void updateServiceLocation() {
        this.mSST.enableSingleLocationUpdate();
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
    public boolean getDataRoamingEnabled() {
        return this.mDcTracker.getDataOnRoamingEnabled();
    }

    @Override // com.android.internal.telephony.Phone
    public void setDataRoamingEnabled(boolean enable) {
        this.mDcTracker.setDataOnRoamingEnabled(enable);
    }

    void onMMIDone(GsmMmiCode mmi) {
        if (this.mPendingMMIs.remove(mmi) || mmi.isUssdRequest() || mmi.isSsInfo()) {
            this.mMmiCompleteRegistrants.notifyRegistrants(new AsyncResult((Object) null, mmi, (Throwable) null));
        }
    }

    protected void setCallForwardingPreference(boolean enabled) {
        Rlog.d(LOG_TAG, "Set callforwarding info to perferences");
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor edit = sp.edit();
        edit.putBoolean(CF_ENABLED, enabled);
        edit.commit();
        setVmSimImsi(getSubscriberId());
    }

    protected boolean getCallForwardingPreference() {
        Rlog.d(LOG_TAG, "Get callforwarding info from perferences");
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        boolean cf = sp.getBoolean(CF_ENABLED, false);
        return cf;
    }

    private void updateCallForwardStatus() {
        Rlog.d(LOG_TAG, "updateCallForwardStatus got sim records");
        IccRecords r = this.mIccRecords.get();
        if (r != null && r.isCallForwardStatusStored()) {
            Rlog.d(LOG_TAG, "Callforwarding info is present on sim");
            notifyCallForwardingIndicator();
        } else {
            Message msg = obtainMessage(CHECK_CALLFORWARDING_STATUS);
            sendMessage(msg);
        }
    }

    private void onNetworkInitiatedUssd(GsmMmiCode mmi) {
        this.mMmiCompleteRegistrants.notifyRegistrants(new AsyncResult((Object) null, mmi, (Throwable) null));
    }

    private void onIncomingUSSD(int ussdMode, String ussdMessage) {
        boolean isUssdRequest = ussdMode == 1;
        boolean isUssdError = (ussdMode == 0 || ussdMode == 1) ? false : true;
        GsmMmiCode found = null;
        int s = this.mPendingMMIs.size();
        for (int i = 0; i < s; i++) {
            if (this.mPendingMMIs.get(i).isPendingUSSD()) {
                GsmMmiCode found2 = this.mPendingMMIs.get(i);
                found = found2;
                break;
            }
        }
        if (found != null) {
            if (isUssdError) {
                found.onUssdFinishedError();
                return;
            } else {
                found.onUssdFinished(ussdMessage, isUssdRequest);
                return;
            }
        }
        if (!isUssdError && ussdMessage != null) {
            GsmMmiCode mmi = GsmMmiCode.newNetworkInitiatedUssd(ussdMessage, isUssdRequest, this, this.mUiccApplication.get());
            onNetworkInitiatedUssd(mmi);
        }
    }

    protected void syncClirSetting() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        int clirSetting = sp.getInt(PhoneBase.CLIR_KEY, -1);
        if (clirSetting >= 0) {
            this.mCi.setCLIR(clirSetting, null);
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, android.os.Handler
    public void handleMessage(Message msg) {
        if (!this.mIsTheCurrentActivePhone) {
            Rlog.e(LOG_TAG, "Received message " + msg + "[" + msg.what + "] while being destroyed. Ignoring.");
        }
        switch (msg.what) {
            case 1:
                this.mCi.getBasebandVersion(obtainMessage(6));
                this.mCi.getIMEI(obtainMessage(9));
                this.mCi.getIMEISV(obtainMessage(10));
                break;
            case 2:
                AsyncResult ar = (AsyncResult) msg.obj;
                this.mSsnRegistrants.notifyRegistrants(ar);
                break;
            case 3:
                updateCurrentCarrierInProvider();
                String imsi = getVmSimImsi();
                String imsiFromSIM = getSubscriberId();
                if (imsi != null && imsiFromSIM != null && !imsiFromSIM.equals(imsi)) {
                    storeVoiceMailNumber(null);
                    setCallForwardingPreference(false);
                    setVmSimImsi(null);
                }
                updateCallForwardStatus();
                this.mSimRecordsLoadedRegistrants.notifyRegistrants();
                updateVoiceMail();
                break;
            case 5:
                break;
            case 6:
                AsyncResult ar2 = (AsyncResult) msg.obj;
                if (ar2.exception == null) {
                    Rlog.d(LOG_TAG, "Baseband version: " + ar2.result);
                    setSystemProperty("gsm.version.baseband", (String) ar2.result);
                }
                break;
            case 7:
                AsyncResult ar3 = (AsyncResult) msg.obj;
                String[] ussdResult = (String[]) ar3.result;
                if (ussdResult.length > 1) {
                    try {
                        onIncomingUSSD(Integer.parseInt(ussdResult[0]), ussdResult[1]);
                    } catch (NumberFormatException e) {
                        Rlog.w(LOG_TAG, "error parsing USSD");
                        return;
                    }
                }
                break;
            case 8:
                for (int i = this.mPendingMMIs.size() - 1; i >= 0; i--) {
                    if (this.mPendingMMIs.get(i).isPendingUSSD()) {
                        this.mPendingMMIs.get(i).onUssdFinishedError();
                    }
                }
                break;
            case 9:
                AsyncResult ar4 = (AsyncResult) msg.obj;
                if (ar4.exception == null) {
                    this.mImei = (String) ar4.result;
                }
                break;
            case 10:
                AsyncResult ar5 = (AsyncResult) msg.obj;
                if (ar5.exception == null) {
                    this.mImeiSv = (String) ar5.result;
                }
                break;
            case 12:
                AsyncResult ar6 = (AsyncResult) msg.obj;
                IccRecords r = this.mIccRecords.get();
                Cfu cfu = (Cfu) ar6.userObj;
                if (ar6.exception == null && r != null) {
                    r.setVoiceCallForwardingFlag(1, msg.arg1 == 1, cfu.mSetCfNumber);
                    setCallForwardingPreference(msg.arg1 == 1);
                }
                if (cfu.mOnComplete != null) {
                    AsyncResult.forMessage(cfu.mOnComplete, ar6.result, ar6.exception);
                    cfu.mOnComplete.sendToTarget();
                }
                break;
            case 13:
                AsyncResult ar7 = (AsyncResult) msg.obj;
                if (ar7.exception == null) {
                    handleCfuQueryResult((CallForwardInfo[]) ar7.result);
                }
                Message onComplete = (Message) ar7.userObj;
                if (onComplete != null) {
                    AsyncResult.forMessage(onComplete, ar7.result, ar7.exception);
                    onComplete.sendToTarget();
                }
                break;
            case 16:
            case 17:
                handleSetSelectNetwork((AsyncResult) msg.obj);
                break;
            case 18:
                AsyncResult ar8 = (AsyncResult) msg.obj;
                if (ar8.exception == null) {
                    saveClirSetting(msg.arg1);
                }
                Message onComplete2 = (Message) ar8.userObj;
                if (onComplete2 != null) {
                    AsyncResult.forMessage(onComplete2, ar8.result, ar8.exception);
                    onComplete2.sendToTarget();
                }
                break;
            case 19:
                syncClirSetting();
                break;
            case 20:
                AsyncResult ar9 = (AsyncResult) msg.obj;
                if (IccVmNotSupportedException.class.isInstance(ar9.exception)) {
                    storeVoiceMailNumber(this.mVmNumber);
                    ar9.exception = null;
                }
                Message onComplete3 = (Message) ar9.userObj;
                if (onComplete3 != null) {
                    AsyncResult.forMessage(onComplete3, ar9.result, ar9.exception);
                    onComplete3.sendToTarget();
                }
                break;
            case 28:
                AsyncResult ar10 = (AsyncResult) msg.obj;
                if (this.mSST.mSS.getIsManualSelection()) {
                    setNetworkSelectionModeAutomatic((Message) ar10.result);
                } else {
                    Rlog.d(LOG_TAG, "Stop duplicate SET_NETWORK_SELECTION_AUTOMATIC to Ril ");
                }
                break;
            case IccRecords.EVENT_REFRESH_OEM /* 29 */:
                AsyncResult ar11 = (AsyncResult) msg.obj;
                processIccRecordEvents(((Integer) ar11.result).intValue());
                break;
            case 31:
                AsyncResult ar12 = (AsyncResult) msg.obj;
                Rlog.d(LOG_TAG, "Event EVENT_SS received");
                GsmMmiCode mmi = new GsmMmiCode(this, this.mUiccApplication.get());
                mmi.processSsData(ar12);
                break;
            case CHECK_CALLFORWARDING_STATUS /* 75 */:
                boolean cfEnabled = getCallForwardingPreference();
                Rlog.d(LOG_TAG, "Callforwarding is " + cfEnabled);
                if (cfEnabled) {
                    notifyCallForwardingIndicator();
                }
                break;
            default:
                super.handleMessage(msg);
                break;
        }
    }

    protected UiccCardApplication getUiccCardApplication() {
        return this.mUiccController.getUiccCardApplication(1);
    }

    @Override // com.android.internal.telephony.PhoneBase
    protected void setCardInPhoneBook() {
        if (this.mUiccController != null && this.mSimPhoneBookIntManager != null) {
            this.mSimPhoneBookIntManager.setIccCard(this.mUiccController.getUiccCard());
        }
    }

    @Override // com.android.internal.telephony.PhoneBase
    protected void onUpdateIccAvailability() {
        if (this.mUiccController != null) {
            setCardInPhoneBook();
            UiccCardApplication newUiccApplication = getUiccCardApplication();
            UiccCardApplication app = this.mUiccApplication.get();
            if (app != newUiccApplication) {
                if (app != null) {
                    log("Removing stale icc objects.");
                    if (this.mIccRecords.get() != null) {
                        unregisterForSimRecordEvents();
                    }
                    this.mIccRecords.set(null);
                    this.mUiccApplication.set(null);
                }
                if (newUiccApplication != null) {
                    log("New Uicc application found");
                    this.mUiccApplication.set(newUiccApplication);
                    this.mIccRecords.set(newUiccApplication.getIccRecords());
                    registerForSimRecordEvents();
                }
            }
        }
    }

    private void processIccRecordEvents(int eventCode) {
        switch (eventCode) {
            case 0:
                notifyCallForwardingIndicator();
                break;
        }
    }

    public boolean updateCurrentCarrierInProvider() {
        IccRecords r = this.mIccRecords.get();
        if (r != null) {
            try {
                Uri uri = Uri.withAppendedPath(Telephony.Carriers.CONTENT_URI, Telephony.Carriers.CURRENT);
                ContentValues map = new ContentValues();
                map.put("numeric", r.getOperatorNumeric());
                this.mContext.getContentResolver().insert(uri, map);
                return true;
            } catch (SQLException e) {
                Rlog.e(LOG_TAG, "Can't store current operator", e);
            }
        }
        return false;
    }

    private void handleSetSelectNetwork(AsyncResult ar) {
        if (!(ar.userObj instanceof NetworkSelectMessage)) {
            Rlog.d(LOG_TAG, "unexpected result from user object.");
            return;
        }
        NetworkSelectMessage nsm = (NetworkSelectMessage) ar.userObj;
        if (nsm.message != null) {
            Rlog.d(LOG_TAG, "sending original message to recipient");
            AsyncResult.forMessage(nsm.message, ar.result, ar.exception);
            nsm.message.sendToTarget();
        }
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(PhoneBase.NETWORK_SELECTION_KEY, nsm.operatorNumeric);
        editor.putString(PhoneBase.NETWORK_SELECTION_NAME_KEY, nsm.operatorAlphaLong);
        if (!editor.commit()) {
            Rlog.e(LOG_TAG, "failed to commit network selection preference");
        }
    }

    public void saveClirSetting(int commandInterfaceCLIRMode) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = sp.edit();
        editor.putInt(PhoneBase.CLIR_KEY, commandInterfaceCLIRMode);
        if (!editor.commit()) {
            Rlog.e(LOG_TAG, "failed to commit CLIR preference");
        }
    }

    private void handleCfuQueryResult(CallForwardInfo[] infos) {
        IccRecords r = this.mIccRecords.get();
        if (r != null) {
            if (infos == null || infos.length == 0) {
                r.setVoiceCallForwardingFlag(1, false, null);
                return;
            }
            int s = infos.length;
            for (int i = 0; i < s; i++) {
                if ((infos[i].serviceClass & 1) != 0) {
                    setCallForwardingPreference(infos[i].status == 1);
                    r.setVoiceCallForwardingFlag(1, infos[i].status == 1, infos[i].number);
                    return;
                }
            }
        }
    }

    @Override // com.android.internal.telephony.Phone
    public PhoneSubInfo getPhoneSubInfo() {
        return this.mSubInfo;
    }

    @Override // com.android.internal.telephony.Phone
    public IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager() {
        return this.mSimPhoneBookIntManager;
    }

    @Override // com.android.internal.telephony.Phone
    public void activateCellBroadcastSms(int activate, Message response) {
        Rlog.e(LOG_TAG, "[GSMPhone] activateCellBroadcastSms() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    @Override // com.android.internal.telephony.Phone
    public void getCellBroadcastSmsConfig(Message response) {
        Rlog.e(LOG_TAG, "[GSMPhone] getCellBroadcastSmsConfig() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    @Override // com.android.internal.telephony.Phone
    public void setCellBroadcastSmsConfig(int[] configValuesArray, Message response) {
        Rlog.e(LOG_TAG, "[GSMPhone] setCellBroadcastSmsConfig() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public boolean isCspPlmnEnabled() {
        IccRecords r = this.mIccRecords.get();
        if (r != null) {
            return r.isCspPlmnEnabled();
        }
        return false;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public boolean isManualNetSelAllowed() {
        int nwMode = Settings.Global.getInt(this.mContext.getContentResolver(), "preferred_network_mode", 11);
        Rlog.d(LOG_TAG, "isManualNetSelAllowed in mode = " + nwMode);
        if (!SystemProperties.getBoolean(PhoneBase.PROPERTY_MULTIMODE_CDMA, false) || (nwMode != 10 && nwMode != 7)) {
            return true;
        }
        Rlog.d(LOG_TAG, "Manual selection not supported in mode = " + nwMode);
        return false;
    }

    private void registerForSimRecordEvents() {
        IccRecords r = this.mIccRecords.get();
        if (r != null) {
            r.registerForNetworkSelectionModeAutomatic(this, 28, null);
            r.registerForRecordsEvents(this, 29, null);
            r.registerForRecordsLoaded(this, 3, null);
        }
    }

    private void unregisterForSimRecordEvents() {
        IccRecords r = this.mIccRecords.get();
        if (r != null) {
            r.unregisterForNetworkSelectionModeAutomatic(this);
            r.unregisterForRecordsEvents(this);
            r.unregisterForRecordsLoaded(this);
        }
    }

    @Override // com.android.internal.telephony.PhoneBase
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("GSMPhone extends:");
        super.dump(fd, pw, args);
        pw.println(" mCT=" + this.mCT);
        pw.println(" mSST=" + this.mSST);
        pw.println(" mPendingMMIs=" + this.mPendingMMIs);
        pw.println(" mSimPhoneBookIntManager=" + this.mSimPhoneBookIntManager);
        pw.println(" mSubInfo=" + this.mSubInfo);
        pw.println(" mVmNumber=" + this.mVmNumber);
    }

    protected void log(String s) {
        Rlog.d(LOG_TAG, "[GSMPhone] " + s);
    }

    private boolean isValidFacilityString(String facility) {
        return facility.equals(CommandsInterface.CB_FACILITY_BAOC) || facility.equals(CommandsInterface.CB_FACILITY_BAOIC) || facility.equals(CommandsInterface.CB_FACILITY_BAOICxH) || facility.equals(CommandsInterface.CB_FACILITY_BAIC) || facility.equals(CommandsInterface.CB_FACILITY_BAICr) || facility.equals(CommandsInterface.CB_FACILITY_BA_ALL) || facility.equals(CommandsInterface.CB_FACILITY_BA_MO) || facility.equals(CommandsInterface.CB_FACILITY_BA_MT) || facility.equals(CommandsInterface.CB_FACILITY_BA_SIM) || facility.equals(CommandsInterface.CB_FACILITY_BA_FD);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void getCallBarringOption(String facility, String password, Message onComplete) {
        if (isValidFacilityString(facility)) {
            this.mCi.queryFacilityLock(facility, password, 0, onComplete);
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setCallBarringOption(String facility, boolean lockState, String password, Message onComplete) {
        if (isValidFacilityString(facility)) {
            this.mCi.setFacilityLock(facility, lockState, password, 1, onComplete);
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void requestChangeCbPsw(String facility, String oldPwd, String newPwd, Message result) {
        this.mCi.changeBarringPassword(facility, oldPwd, newPwd, result);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void setVoiceMessageWaiting(int line, int countWaiting) {
        IccRecords r = this.mIccRecords.get();
        if (r != null) {
            r.setVoiceMessageWaiting(line, countWaiting);
        } else {
            log("SIM Records not found, MWI not updated");
        }
    }
}
