package com.android.internal.telephony.sip;

import android.content.Context;
import android.net.LinkProperties;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.telephony.CellLocation;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneSubInfo;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.dataconnection.DataConnection;
import com.android.internal.telephony.uicc.IccFileHandler;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
abstract class SipPhoneBase extends PhoneBase {
    private static final String LOG_TAG = "SipPhoneBase";
    private RegistrantList mRingbackRegistrants;
    private PhoneConstants.State mState;

    public abstract Call getBackgroundCall();

    public abstract Call getForegroundCall();

    public abstract Call getRingingCall();

    public SipPhoneBase(String name, Context context, PhoneNotifier notifier) {
        super(name, notifier, context, new SipCommandInterface(context), false);
        this.mRingbackRegistrants = new RegistrantList();
        this.mState = PhoneConstants.State.IDLE;
    }

    public Connection dial(String dialString, UUSInfo uusInfo) throws CallStateException {
        return dial(dialString);
    }

    void migrateFrom(SipPhoneBase from) {
        migrate(this.mRingbackRegistrants, from.mRingbackRegistrants);
        migrate(this.mPreciseCallStateRegistrants, from.mPreciseCallStateRegistrants);
        migrate(this.mNewRingingConnectionRegistrants, from.mNewRingingConnectionRegistrants);
        migrate(this.mIncomingRingRegistrants, from.mIncomingRingRegistrants);
        migrate(this.mDisconnectRegistrants, from.mDisconnectRegistrants);
        migrate(this.mServiceStateRegistrants, from.mServiceStateRegistrants);
        migrate(this.mMmiCompleteRegistrants, from.mMmiCompleteRegistrants);
        migrate(this.mMmiRegistrants, from.mMmiRegistrants);
        migrate(this.mUnknownConnectionRegistrants, from.mUnknownConnectionRegistrants);
        migrate(this.mSuppServiceFailedRegistrants, from.mSuppServiceFailedRegistrants);
    }

    static void migrate(RegistrantList to, RegistrantList from) {
        from.removeCleared();
        int n = from.size();
        for (int i = 0; i < n; i++) {
            to.add((Registrant) from.get(i));
        }
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void registerForRingbackTone(Handler h, int what, Object obj) {
        this.mRingbackRegistrants.addUnique(h, what, obj);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void unregisterForRingbackTone(Handler h) {
        this.mRingbackRegistrants.remove(h);
    }

    protected void startRingbackTone() {
        AsyncResult result = new AsyncResult((Object) null, Boolean.TRUE, (Throwable) null);
        this.mRingbackRegistrants.notifyRegistrants(result);
    }

    protected void stopRingbackTone() {
        AsyncResult result = new AsyncResult((Object) null, Boolean.FALSE, (Throwable) null);
        this.mRingbackRegistrants.notifyRegistrants(result);
    }

    public ServiceState getServiceState() {
        ServiceState s = new ServiceState();
        s.setState(0);
        return s;
    }

    public CellLocation getCellLocation() {
        return null;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public PhoneConstants.State getState() {
        return this.mState;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public int getPhoneType() {
        return 3;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public SignalStrength getSignalStrength() {
        return new SignalStrength();
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public boolean getMessageWaitingIndicator() {
        return false;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public boolean getCallForwardingIndicator() {
        return false;
    }

    public List<? extends MmiCode> getPendingMmiCodes() {
        return new ArrayList(0);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public PhoneConstants.DataState getDataConnectionState() {
        return PhoneConstants.DataState.DISCONNECTED;
    }

    public PhoneConstants.DataState getDataConnectionState(String apnType) {
        return PhoneConstants.DataState.DISCONNECTED;
    }

    public Phone.DataActivityState getDataActivityState() {
        return Phone.DataActivityState.NONE;
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

    @Override // com.android.internal.telephony.PhoneBase
    public void notifyCallForwardingIndicator() {
        this.mNotifier.notifyCallForwardingChanged(this);
    }

    public boolean canDial() {
        int serviceState = getServiceState().getState();
        Rlog.v(LOG_TAG, "canDial(): serviceState = " + serviceState);
        if (serviceState == 3) {
            return false;
        }
        String disableCall = SystemProperties.get("ro.telephony.disable-call", "false");
        Rlog.v(LOG_TAG, "canDial(): disableCall = " + disableCall);
        if (disableCall.equals("true")) {
            return false;
        }
        Rlog.v(LOG_TAG, "canDial(): ringingCall: " + getRingingCall().getState());
        Rlog.v(LOG_TAG, "canDial(): foregndCall: " + getForegroundCall().getState());
        Rlog.v(LOG_TAG, "canDial(): backgndCall: " + getBackgroundCall().getState());
        if (getRingingCall().isRinging()) {
            return false;
        }
        return (getForegroundCall().getState().isAlive() && getBackgroundCall().getState().isAlive()) ? false : true;
    }

    public boolean handleInCallMmiCommands(String dialString) {
        return false;
    }

    boolean isInCall() {
        Call.State foregroundCallState = getForegroundCall().getState();
        Call.State backgroundCallState = getBackgroundCall().getState();
        Call.State ringingCallState = getRingingCall().getState();
        return foregroundCallState.isAlive() || backgroundCallState.isAlive() || ringingCallState.isAlive();
    }

    public boolean handlePinMmi(String dialString) {
        return false;
    }

    public void sendUssdResponse(String ussdMessge) {
    }

    public void registerForSuppServiceNotification(Handler h, int what, Object obj) {
    }

    public void unregisterForSuppServiceNotification(Handler h) {
    }

    public void setRadioPower(boolean power) {
    }

    public String getVoiceMailNumber() {
        return null;
    }

    public String getVoiceMailAlphaTag() {
        return null;
    }

    public String getDeviceId() {
        return null;
    }

    public String getDeviceSvn() {
        return null;
    }

    public String getImei() {
        return null;
    }

    public String getEsn() {
        Rlog.e(LOG_TAG, "[SipPhone] getEsn() is a CDMA method");
        return "0";
    }

    public String getMeid() {
        Rlog.e(LOG_TAG, "[SipPhone] getMeid() is a CDMA method");
        return "0";
    }

    public String getSubscriberId() {
        return null;
    }

    public String getGroupIdLevel1() {
        return null;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public String getIccSerialNumber() {
        return null;
    }

    public String getLine1Number() {
        return null;
    }

    public String getLine1AlphaTag() {
        return null;
    }

    public void setLine1Number(String alphaTag, String number, Message onComplete) {
        AsyncResult.forMessage(onComplete, (Object) null, (Throwable) null);
        onComplete.sendToTarget();
    }

    public void setVoiceMailNumber(String alphaTag, String voiceMailNumber, Message onComplete) {
        AsyncResult.forMessage(onComplete, (Object) null, (Throwable) null);
        onComplete.sendToTarget();
    }

    public void getCallForwardingOption(int commandInterfaceCFReason, Message onComplete) {
    }

    public void setCallForwardingOption(int commandInterfaceCFAction, int commandInterfaceCFReason, String dialingNumber, int timerSeconds, Message onComplete) {
    }

    public void getOutgoingCallerIdDisplay(Message onComplete) {
        AsyncResult.forMessage(onComplete, (Object) null, (Throwable) null);
        onComplete.sendToTarget();
    }

    public void setOutgoingCallerIdDisplay(int commandInterfaceCLIRMode, Message onComplete) {
        AsyncResult.forMessage(onComplete, (Object) null, (Throwable) null);
        onComplete.sendToTarget();
    }

    public void getCallWaiting(Message onComplete) {
        AsyncResult.forMessage(onComplete, (Object) null, (Throwable) null);
        onComplete.sendToTarget();
    }

    public void setCallWaiting(boolean enable, Message onComplete) {
        Rlog.e(LOG_TAG, "call waiting not supported");
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public boolean getIccRecordsLoaded() {
        return false;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public IccCard getIccCard() {
        return null;
    }

    public void getAvailableNetworks(Message response) {
    }

    public void setNetworkSelectionModeAutomatic(Message response) {
    }

    public void selectNetworkManually(OperatorInfo network, Message response) {
    }

    public void getNeighboringCids(Message response) {
    }

    public void setOnPostDialCharacter(Handler h, int what, Object obj) {
    }

    public void getDataCallList(Message response) {
    }

    public List<DataConnection> getCurrentDataConnectionList() {
        return null;
    }

    public void updateServiceLocation() {
    }

    public void enableLocationUpdates() {
    }

    public void disableLocationUpdates() {
    }

    public boolean getDataRoamingEnabled() {
        return false;
    }

    public void setDataRoamingEnabled(boolean enable) {
    }

    public boolean enableDataConnectivity() {
        return false;
    }

    public boolean disableDataConnectivity() {
        return false;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public boolean isDataConnectivityPossible() {
        return false;
    }

    boolean updateCurrentCarrierInProvider() {
        return false;
    }

    public void saveClirSetting(int commandInterfaceCLIRMode) {
    }

    public PhoneSubInfo getPhoneSubInfo() {
        return null;
    }

    public IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager() {
        return null;
    }

    @Override // com.android.internal.telephony.PhoneBase
    public IccFileHandler getIccFileHandler() {
        return null;
    }

    public void activateCellBroadcastSms(int activate, Message response) {
        Rlog.e(LOG_TAG, "Error! This functionality is not implemented for SIP.");
    }

    public void getCellBroadcastSmsConfig(Message response) {
        Rlog.e(LOG_TAG, "Error! This functionality is not implemented for SIP.");
    }

    public void setCellBroadcastSmsConfig(int[] configValuesArray, Message response) {
        Rlog.e(LOG_TAG, "Error! This functionality is not implemented for SIP.");
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public boolean needsOtaServiceProvisioning() {
        return false;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public LinkProperties getLinkProperties(String apnType) {
        return null;
    }

    void updatePhoneState() {
        PhoneConstants.State oldState = this.mState;
        if (getRingingCall().isRinging()) {
            this.mState = PhoneConstants.State.RINGING;
        } else if (getForegroundCall().isIdle() && getBackgroundCall().isIdle()) {
            this.mState = PhoneConstants.State.IDLE;
        } else {
            this.mState = PhoneConstants.State.OFFHOOK;
        }
        if (this.mState != oldState) {
            Rlog.d(LOG_TAG, " ^^^ new phone state: " + this.mState);
            notifyPhoneStateChanged();
        }
    }

    @Override // com.android.internal.telephony.PhoneBase
    protected void onUpdateIccAvailability() {
    }
}
