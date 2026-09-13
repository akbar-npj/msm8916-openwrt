package com.android.internal.telephony.uicc;

import android.app.ActivityManagerNative;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/* JADX INFO: loaded from: classes.dex */
public class IccCardProxy extends Handler implements IccCard {
    private static final boolean DBG = true;
    private static final int EVENT_APP_READY = 6;
    private static final int EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED = 11;
    private static final int EVENT_ICC_ABSENT = 4;
    private static final int EVENT_ICC_CHANGED = 3;
    private static final int EVENT_ICC_LOCKED = 5;
    protected static final int EVENT_IMSI_READY = 8;
    private static final int EVENT_PERSO_LOCKED = 9;
    private static final int EVENT_RADIO_OFF_OR_UNAVAILABLE = 1;
    private static final int EVENT_RADIO_ON = 2;
    protected static final int EVENT_RECORDS_LOADED = 7;
    private static final String LOG_TAG = "IccCardProxy";
    private CdmaSubscriptionSourceManager mCdmaSSM;
    private CommandsInterface mCi;
    protected Context mContext;
    protected UiccController mUiccController;
    protected final Object mLock = new Object();
    protected RegistrantList mAbsentRegistrants = new RegistrantList();
    private RegistrantList mPinLockedRegistrants = new RegistrantList();
    private RegistrantList mPersoLockedRegistrants = new RegistrantList();
    protected int mCurrentAppType = 1;
    protected UiccCard mUiccCard = null;
    protected UiccCardApplication mUiccApplication = null;
    protected IccRecords mIccRecords = null;
    private boolean mRadioOn = false;
    protected boolean mQuietMode = false;
    private boolean mInitialized = false;
    protected IccCardConstants.State mExternalState = IccCardConstants.State.UNKNOWN;
    private boolean mIsCardStatusAvailable = false;
    private IccCardApplicationStatus.PersoSubState mPersoSubState = IccCardApplicationStatus.PersoSubState.PERSOSUBSTATE_UNKNOWN;

    public IccCardProxy(Context context, CommandsInterface ci) {
        this.mUiccController = null;
        this.mCdmaSSM = null;
        log("Creating");
        this.mContext = context;
        this.mCi = ci;
        this.mCdmaSSM = CdmaSubscriptionSourceManager.getInstance(context, ci, this, 11, null);
        this.mUiccController = UiccController.getInstance();
        this.mUiccController.registerForIccChanged(this, 3, null);
        ci.registerForOn(this, 2, null);
        ci.registerForOffOrNotAvailable(this, 1, null);
        setExternalState(IccCardConstants.State.NOT_READY);
    }

    public void dispose() {
        synchronized (this.mLock) {
            log("Disposing");
            this.mUiccController.unregisterForIccChanged(this);
            this.mUiccController = null;
            this.mCi.unregisterForOn(this);
            this.mCi.unregisterForOffOrNotAvailable(this);
            this.mCdmaSSM.dispose(this);
        }
    }

    public void setVoiceRadioTech(int radioTech) {
        synchronized (this.mLock) {
            log("Setting radio tech " + ServiceState.rilRadioTechnologyToString(radioTech));
            if (ServiceState.isGsm(radioTech)) {
                this.mCurrentAppType = 1;
            } else {
                this.mCurrentAppType = 2;
            }
            updateQuietMode();
            updateActiveRecord();
        }
    }

    protected void updateActiveRecord() {
        log("updateActiveRecord app type = " + this.mCurrentAppType + "mIccRecords = " + this.mIccRecords);
        if (this.mIccRecords != null) {
            if (this.mCurrentAppType == 2) {
                int newSubscriptionSource = this.mCdmaSSM.getCdmaSubscriptionSource();
                if (newSubscriptionSource == 0) {
                    log("Setting Ruim Record as active");
                    this.mIccRecords.recordsRequired();
                    return;
                }
                return;
            }
            if (this.mCurrentAppType == 1) {
                log("Setting SIM Record as active");
                this.mIccRecords.recordsRequired();
            }
        }
    }

    private void updateQuietMode() {
        boolean newQuietMode;
        synchronized (this.mLock) {
            boolean z = this.mQuietMode;
            int cdmaSource = -1;
            if (this.mCurrentAppType == 1) {
                newQuietMode = false;
                log("updateQuietMode: 3GPP subscription -> newQuietMode=false");
            } else {
                cdmaSource = this.mCdmaSSM != null ? this.mCdmaSSM.getCdmaSubscriptionSource() : -1;
                newQuietMode = cdmaSource == 1 && this.mCurrentAppType == 2;
            }
            if (!this.mQuietMode && newQuietMode) {
                log("Switching to QuietMode.");
                setExternalState(IccCardConstants.State.READY);
                this.mQuietMode = newQuietMode;
            } else if (this.mQuietMode && !newQuietMode) {
                log("updateQuietMode: Switching out from QuietMode. Force broadcast of current state=" + this.mExternalState);
                this.mQuietMode = newQuietMode;
                setExternalState(this.mExternalState, true);
            }
            log("updateQuietMode: QuietMode is " + this.mQuietMode + " (app_type=" + this.mCurrentAppType + " cdmaSource=" + cdmaSource + ")");
            this.mInitialized = true;
            if (this.mIsCardStatusAvailable) {
                sendMessage(obtainMessage(3));
            }
        }
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 1:
                this.mRadioOn = false;
                if (CommandsInterface.RadioState.RADIO_UNAVAILABLE == this.mCi.getRadioState()) {
                    setExternalState(IccCardConstants.State.NOT_READY);
                }
                break;
            case 2:
                this.mRadioOn = true;
                if (!this.mInitialized) {
                    updateQuietMode();
                }
                break;
            case 3:
                this.mIsCardStatusAvailable = true;
                if (this.mInitialized) {
                    updateIccAvailability();
                }
                break;
            case 4:
                this.mAbsentRegistrants.notifyRegistrants();
                setExternalState(IccCardConstants.State.ABSENT);
                break;
            case 5:
                processLockedState();
                break;
            case 6:
                setExternalState(IccCardConstants.State.READY);
                break;
            case 7:
                broadcastIccStateChangedIntent("LOADED", null);
                break;
            case 8:
                broadcastIccStateChangedIntent("IMSI", null);
                break;
            case 9:
                this.mPersoSubState = this.mUiccApplication.getPersoSubState();
                this.mPersoLockedRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                setExternalState(IccCardConstants.State.PERSO_LOCKED);
                break;
            case 10:
            default:
                loge("Unhandled message with number: " + msg.what);
                break;
            case 11:
                updateQuietMode();
                updateActiveRecord();
                break;
        }
    }

    protected void updateIccAvailability() {
        synchronized (this.mLock) {
            UiccCard newCard = this.mUiccController.getUiccCard();
            UiccCardApplication newApp = null;
            IccRecords newRecords = null;
            if (newCard != null) {
                newApp = newCard.getApplication(this.mCurrentAppType);
                if (newApp != null) {
                    newRecords = newApp.getIccRecords();
                }
            } else {
                log("No card available");
            }
            if (this.mIccRecords != newRecords || this.mUiccApplication != newApp || this.mUiccCard != newCard) {
                log("Icc changed. Reregestering.");
                unregisterUiccCardEvents();
                this.mUiccCard = newCard;
                this.mUiccApplication = newApp;
                this.mIccRecords = newRecords;
                registerUiccCardEvents();
                updateActiveRecord();
            }
            updateExternalState();
        }
    }

    protected void HandleDetectedState() {
        setExternalState(IccCardConstants.State.UNKNOWN);
    }

    protected void updateExternalState() {
        if (this.mUiccCard == null || this.mUiccCard.getCardState() == IccCardStatus.CardState.CARDSTATE_ABSENT) {
            if (this.mRadioOn) {
                setExternalState(IccCardConstants.State.ABSENT);
            } else {
                setExternalState(IccCardConstants.State.NOT_READY);
                return;
            }
        }
        if (this.mUiccCard.getCardState() == IccCardStatus.CardState.CARDSTATE_ERROR) {
            setExternalState(IccCardConstants.State.CARD_IO_ERROR);
            return;
        }
        if (this.mUiccApplication == null) {
            setExternalState(IccCardConstants.State.NOT_READY);
            return;
        }
        switch (this.mUiccApplication.getState()) {
            case APPSTATE_UNKNOWN:
                setExternalState(IccCardConstants.State.UNKNOWN);
                break;
            case APPSTATE_DETECTED:
                HandleDetectedState();
                break;
            case APPSTATE_PIN:
                setExternalState(IccCardConstants.State.PIN_REQUIRED);
                break;
            case APPSTATE_PUK:
                setExternalState(IccCardConstants.State.PUK_REQUIRED);
                break;
            case APPSTATE_SUBSCRIPTION_PERSO:
                if (this.mUiccApplication.isPersoLocked()) {
                    this.mPersoSubState = this.mUiccApplication.getPersoSubState();
                    setExternalState(IccCardConstants.State.PERSO_LOCKED);
                } else {
                    setExternalState(IccCardConstants.State.UNKNOWN);
                }
                break;
            case APPSTATE_READY:
                setExternalState(IccCardConstants.State.READY);
                break;
        }
    }

    protected void registerUiccCardEvents() {
        if (this.mUiccCard != null) {
            this.mUiccCard.registerForAbsent(this, 4, null);
        }
        if (this.mUiccApplication != null) {
            this.mUiccApplication.registerForReady(this, 6, null);
            this.mUiccApplication.registerForLocked(this, 5, null);
            this.mUiccApplication.registerForPersoLocked(this, 9, null);
        }
        if (this.mIccRecords != null) {
            this.mIccRecords.registerForImsiReady(this, 8, null);
            this.mIccRecords.registerForRecordsLoaded(this, 7, null);
        }
    }

    protected void unregisterUiccCardEvents() {
        if (this.mUiccCard != null) {
            this.mUiccCard.unregisterForAbsent(this);
        }
        if (this.mUiccApplication != null) {
            this.mUiccApplication.unregisterForReady(this);
        }
        if (this.mUiccApplication != null) {
            this.mUiccApplication.unregisterForLocked(this);
        }
        if (this.mUiccApplication != null) {
            this.mUiccApplication.unregisterForPersoLocked(this);
        }
        if (this.mIccRecords != null) {
            this.mIccRecords.unregisterForImsiReady(this);
        }
        if (this.mIccRecords != null) {
            this.mIccRecords.unregisterForRecordsLoaded(this);
        }
    }

    protected void broadcastIccStateChangedIntent(String value, String reason) {
        synchronized (this.mLock) {
            if (this.mQuietMode) {
                log("QuietMode: NOT Broadcasting intent ACTION_SIM_STATE_CHANGED " + value + " reason " + reason);
                return;
            }
            Intent intent = new Intent("android.intent.action.SIM_STATE_CHANGED");
            intent.addFlags(536870912);
            intent.putExtra("phoneName", "Phone");
            intent.putExtra("ss", value);
            intent.putExtra("reason", reason);
            log("Broadcasting intent ACTION_SIM_STATE_CHANGED " + value + " reason " + reason);
            ActivityManagerNative.broadcastStickyIntent(intent, "android.permission.READ_PHONE_STATE", -1);
        }
    }

    private void processLockedState() {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                IccCardStatus.PinState pin1State = this.mUiccApplication.getPin1State();
                if (pin1State == IccCardStatus.PinState.PINSTATE_ENABLED_PERM_BLOCKED) {
                    setExternalState(IccCardConstants.State.PERM_DISABLED);
                    return;
                }
                IccCardApplicationStatus.AppState appState = this.mUiccApplication.getState();
                switch (appState) {
                    case APPSTATE_PIN:
                        this.mPinLockedRegistrants.notifyRegistrants();
                        setExternalState(IccCardConstants.State.PIN_REQUIRED);
                        break;
                    case APPSTATE_PUK:
                        setExternalState(IccCardConstants.State.PUK_REQUIRED);
                        break;
                }
            }
        }
    }

    /* JADX WARN: Code duplicated, block: B:9:0x000b A[Catch: all -> 0x0029, TryCatch #0 {, blocks: (B:5:0x0005, B:7:0x0009, B:9:0x000b, B:10:0x0027), top: B:16:0x0005 }] */
    protected void setExternalState(IccCardConstants.State newState, boolean override) {
        synchronized (this.mLock) {
            if (override) {
                this.mExternalState = newState;
                SystemProperties.set("gsm.sim.state", this.mExternalState.toString());
                broadcastIccStateChangedIntent(getIccStateIntentString(this.mExternalState), getIccStateReason(this.mExternalState));
            } else if (newState != this.mExternalState) {
                this.mExternalState = newState;
                SystemProperties.set("gsm.sim.state", this.mExternalState.toString());
                broadcastIccStateChangedIntent(getIccStateIntentString(this.mExternalState), getIccStateReason(this.mExternalState));
            }
            throw th;
        }
    }

    private void setExternalState(IccCardConstants.State newState) {
        setExternalState(newState, false);
    }

    public boolean getIccRecordsLoaded() {
        boolean recordsLoaded;
        synchronized (this.mLock) {
            recordsLoaded = this.mIccRecords != null ? this.mIccRecords.getRecordsLoaded() : false;
        }
        return recordsLoaded;
    }

    /* JADX INFO: renamed from: com.android.internal.telephony.uicc.IccCardProxy$1, reason: invalid class name */
    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$IccCardConstants$State = new int[IccCardConstants.State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[IccCardConstants.State.ABSENT.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[IccCardConstants.State.PIN_REQUIRED.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[IccCardConstants.State.PUK_REQUIRED.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[IccCardConstants.State.PERSO_LOCKED.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[IccCardConstants.State.READY.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[IccCardConstants.State.NOT_READY.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[IccCardConstants.State.PERM_DISABLED.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[IccCardConstants.State.CARD_IO_ERROR.ordinal()] = 8;
            } catch (NoSuchFieldError e8) {
            }
            $SwitchMap$com$android$internal$telephony$uicc$IccCardApplicationStatus$AppState = new int[IccCardApplicationStatus.AppState.values().length];
            try {
                $SwitchMap$com$android$internal$telephony$uicc$IccCardApplicationStatus$AppState[IccCardApplicationStatus.AppState.APPSTATE_UNKNOWN.ordinal()] = 1;
            } catch (NoSuchFieldError e9) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$uicc$IccCardApplicationStatus$AppState[IccCardApplicationStatus.AppState.APPSTATE_DETECTED.ordinal()] = 2;
            } catch (NoSuchFieldError e10) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$uicc$IccCardApplicationStatus$AppState[IccCardApplicationStatus.AppState.APPSTATE_PIN.ordinal()] = 3;
            } catch (NoSuchFieldError e11) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$uicc$IccCardApplicationStatus$AppState[IccCardApplicationStatus.AppState.APPSTATE_PUK.ordinal()] = 4;
            } catch (NoSuchFieldError e12) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$uicc$IccCardApplicationStatus$AppState[IccCardApplicationStatus.AppState.APPSTATE_SUBSCRIPTION_PERSO.ordinal()] = 5;
            } catch (NoSuchFieldError e13) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$uicc$IccCardApplicationStatus$AppState[IccCardApplicationStatus.AppState.APPSTATE_READY.ordinal()] = 6;
            } catch (NoSuchFieldError e14) {
            }
        }
    }

    protected String getIccStateIntentString(IccCardConstants.State state) {
        switch (AnonymousClass1.$SwitchMap$com$android$internal$telephony$IccCardConstants$State[state.ordinal()]) {
            case 1:
                return "ABSENT";
            case 2:
                return "LOCKED";
            case 3:
                return "LOCKED";
            case 4:
                return "LOCKED";
            case 5:
                return "READY";
            case 6:
                return "NOT_READY";
            case 7:
                return "LOCKED";
            case 8:
                return "CARD_IO_ERROR";
            default:
                return "UNKNOWN";
        }
    }

    protected String getIccStateReason(IccCardConstants.State state) {
        switch (AnonymousClass1.$SwitchMap$com$android$internal$telephony$IccCardConstants$State[state.ordinal()]) {
            case 2:
                return "PIN";
            case 3:
                return "PUK";
            case 4:
                return "PERSO";
            case 5:
            case 6:
            default:
                return null;
            case 7:
                return "PERM_DISABLED";
            case 8:
                return "CARD_IO_ERROR";
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public IccCardConstants.State getState() {
        IccCardConstants.State state;
        synchronized (this.mLock) {
            state = this.mExternalState;
        }
        return state;
    }

    @Override // com.android.internal.telephony.IccCard
    public IccRecords getIccRecords() {
        IccRecords iccRecords;
        synchronized (this.mLock) {
            iccRecords = this.mIccRecords;
        }
        return iccRecords;
    }

    @Override // com.android.internal.telephony.IccCard
    public IccFileHandler getIccFileHandler() {
        IccFileHandler iccFileHandler;
        synchronized (this.mLock) {
            iccFileHandler = this.mUiccApplication != null ? this.mUiccApplication.getIccFileHandler() : null;
        }
        return iccFileHandler;
    }

    @Override // com.android.internal.telephony.IccCard
    public void registerForAbsent(Handler h, int what, Object obj) {
        synchronized (this.mLock) {
            Registrant r = new Registrant(h, what, obj);
            this.mAbsentRegistrants.add(r);
            if (getState() == IccCardConstants.State.ABSENT) {
                r.notifyRegistrant();
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void unregisterForAbsent(Handler h) {
        synchronized (this.mLock) {
            this.mAbsentRegistrants.remove(h);
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void registerForPersoLocked(Handler h, int what, Object obj) {
        synchronized (this.mLock) {
            Registrant r = new Registrant(h, what, obj);
            this.mPersoLockedRegistrants.add(r);
            if (getState() == IccCardConstants.State.PERSO_LOCKED) {
                r.notifyRegistrant(new AsyncResult((Object) null, Integer.valueOf(this.mPersoSubState.ordinal()), (Throwable) null));
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void unregisterForPersoLocked(Handler h) {
        synchronized (this.mLock) {
            this.mPersoLockedRegistrants.remove(h);
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void registerForLocked(Handler h, int what, Object obj) {
        synchronized (this.mLock) {
            Registrant r = new Registrant(h, what, obj);
            this.mPinLockedRegistrants.add(r);
            if (getState().isPinLocked()) {
                r.notifyRegistrant();
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void unregisterForLocked(Handler h) {
        synchronized (this.mLock) {
            this.mPinLockedRegistrants.remove(h);
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void supplyPin(String pin, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.supplyPin(pin, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("ICC card is absent.");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void supplyPuk(String puk, String newPin, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.supplyPuk(puk, newPin, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("ICC card is absent.");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void supplyPin2(String pin2, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.supplyPin2(pin2, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("ICC card is absent.");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void supplyPuk2(String puk2, String newPin2, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.supplyPuk2(puk2, newPin2, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("ICC card is absent.");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void supplyDepersonalization(String pin, String type, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.supplyDepersonalization(pin, type, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("CommandsInterface is not set.");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public boolean getIccLockEnabled() {
        boolean zBooleanValue;
        synchronized (this.mLock) {
            Boolean retValue = Boolean.valueOf(this.mUiccApplication != null ? this.mUiccApplication.getIccLockEnabled() : false);
            zBooleanValue = retValue.booleanValue();
        }
        return zBooleanValue;
    }

    @Override // com.android.internal.telephony.IccCard
    public boolean getIccFdnEnabled() {
        boolean zBooleanValue;
        synchronized (this.mLock) {
            Boolean retValue = Boolean.valueOf(this.mUiccApplication != null ? this.mUiccApplication.getIccFdnEnabled() : false);
            zBooleanValue = retValue.booleanValue();
        }
        return zBooleanValue;
    }

    @Override // com.android.internal.telephony.IccCard
    public boolean getIccFdnAvailable() {
        if (this.mUiccApplication == null) {
            return false;
        }
        boolean retValue = this.mUiccApplication.getIccFdnAvailable();
        return retValue;
    }

    @Override // com.android.internal.telephony.IccCard
    public int getIccPin1RetryCount() {
        if (this.mUiccApplication == null) {
            return -1;
        }
        int retValue = this.mUiccApplication.getIccPin1RetryCount();
        return retValue;
    }

    @Override // com.android.internal.telephony.IccCard
    public int getIccPin2RetryCount() {
        if (this.mUiccApplication == null) {
            return -1;
        }
        int retValue = this.mUiccApplication.getIccPin2RetryCount();
        return retValue;
    }

    @Override // com.android.internal.telephony.IccCard
    public boolean getIccPin2Blocked() {
        Boolean retValue = Boolean.valueOf(this.mUiccApplication != null ? this.mUiccApplication.getIccPin2Blocked() : false);
        return retValue.booleanValue();
    }

    @Override // com.android.internal.telephony.IccCard
    public boolean getIccPuk2Blocked() {
        Boolean retValue = Boolean.valueOf(this.mUiccApplication != null ? this.mUiccApplication.getIccPuk2Blocked() : false);
        return retValue.booleanValue();
    }

    @Override // com.android.internal.telephony.IccCard
    public void setIccLockEnabled(boolean enabled, String password, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.setIccLockEnabled(enabled, password, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("ICC card is absent.");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void setIccFdnEnabled(boolean enabled, String password, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.setIccFdnEnabled(enabled, password, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("ICC card is absent.");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void changeIccLockPassword(String oldPassword, String newPassword, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.changeIccLockPassword(oldPassword, newPassword, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("ICC card is absent.");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void changeIccFdnPassword(String oldPassword, String newPassword, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.changeIccFdnPassword(oldPassword, newPassword, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("ICC card is absent.");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public String getServiceProviderName() {
        String serviceProviderName;
        synchronized (this.mLock) {
            serviceProviderName = this.mIccRecords != null ? this.mIccRecords.getServiceProviderName() : null;
        }
        return serviceProviderName;
    }

    @Override // com.android.internal.telephony.IccCard
    public boolean isApplicationOnIcc(IccCardApplicationStatus.AppType type) {
        boolean zBooleanValue;
        synchronized (this.mLock) {
            Boolean retValue = Boolean.valueOf(this.mUiccCard != null ? this.mUiccCard.isApplicationOnIcc(type) : false);
            zBooleanValue = retValue.booleanValue();
        }
        return zBooleanValue;
    }

    @Override // com.android.internal.telephony.IccCard
    public boolean hasIccCard() {
        boolean z;
        synchronized (this.mLock) {
            z = (this.mUiccCard == null || this.mUiccCard.getCardState() == IccCardStatus.CardState.CARDSTATE_ABSENT) ? false : true;
        }
        return z;
    }

    protected void log(String s) {
        Rlog.d(LOG_TAG, s);
    }

    protected void loge(String msg) {
        Rlog.e(LOG_TAG, msg);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("IccCardProxy: " + this);
        pw.println(" mContext=" + this.mContext);
        pw.println(" mCi=" + this.mCi);
        pw.println(" mAbsentRegistrants: size=" + this.mAbsentRegistrants.size());
        for (int i = 0; i < this.mAbsentRegistrants.size(); i++) {
            pw.println("  mAbsentRegistrants[" + i + "]=" + ((Registrant) this.mAbsentRegistrants.get(i)).getHandler());
        }
        pw.println(" mPinLockedRegistrants: size=" + this.mPinLockedRegistrants.size());
        for (int i2 = 0; i2 < this.mPinLockedRegistrants.size(); i2++) {
            pw.println("  mPinLockedRegistrants[" + i2 + "]=" + ((Registrant) this.mPinLockedRegistrants.get(i2)).getHandler());
        }
        pw.println(" mPersoLockedRegistrants: size=" + this.mPersoLockedRegistrants.size());
        for (int i3 = 0; i3 < this.mPersoLockedRegistrants.size(); i3++) {
            pw.println("  mPersoLockedRegistrants[" + i3 + "]=" + ((Registrant) this.mPersoLockedRegistrants.get(i3)).getHandler());
        }
        pw.println(" mCurrentAppType=" + this.mCurrentAppType);
        pw.println(" mUiccController=" + this.mUiccController);
        pw.println(" mUiccCard=" + this.mUiccCard);
        pw.println(" mUiccApplication=" + this.mUiccApplication);
        pw.println(" mIccRecords=" + this.mIccRecords);
        pw.println(" mCdmaSSM=" + this.mCdmaSSM);
        pw.println(" mRadioOn=" + this.mRadioOn);
        pw.println(" mQuietMode=" + this.mQuietMode);
        pw.println(" mInitialized=" + this.mInitialized);
        pw.println(" mExternalState=" + this.mExternalState);
        pw.flush();
    }

    @Override // com.android.internal.telephony.IccCard
    public void exchangeApdu(int cla, int command, int channel, int p1, int p2, int p3, String data, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.exchangeApdu(cla, command, channel, p1, p2, p3, data, onComplete);
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void openLogicalChannel(String aid, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.openLogicalChannel(aid, onComplete);
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void closeLogicalChannel(int channel, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.closeLogicalChannel(channel, onComplete);
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void exchangeIccIo(int fileId, int command, int p1, int p2, int p3, String pathId, Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.exchangeIccIo(fileId, command, p1, p2, p3, pathId, onComplete);
            }
        }
    }

    @Override // com.android.internal.telephony.IccCard
    public void getAtr(Message onComplete) {
        synchronized (this.mLock) {
            if (this.mUiccApplication != null) {
                this.mUiccApplication.getAtr(onComplete);
            }
        }
    }
}
