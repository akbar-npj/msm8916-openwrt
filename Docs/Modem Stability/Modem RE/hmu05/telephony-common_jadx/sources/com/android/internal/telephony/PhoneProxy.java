package com.android.internal.telephony;

import android.R;
import android.app.ActivityManagerNative;
import android.content.Context;
import android.content.Intent;
import android.net.LinkCapabilities;
import android.net.LinkProperties;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.CellInfo;
import android.telephony.CellLocation;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import com.android.internal.telephony.test.SimulatedRadioControl;
import com.android.internal.telephony.uicc.IccCardProxy;
import com.android.internal.telephony.uicc.IsimRecords;
import com.android.internal.telephony.uicc.UsimServiceTable;
import java.util.List;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public class PhoneProxy extends Handler implements Phone {
    private static final int EVENT_RADIO_ON = 2;
    private static final int EVENT_REQUEST_VOICE_RADIO_TECH_DONE = 3;
    private static final int EVENT_RIL_CONNECTED = 4;
    private static final int EVENT_UPDATE_PHONE_OBJECT = 5;
    private static final int EVENT_VOICE_RADIO_TECH_CHANGED = 1;
    protected static final String LOG_TAG = "PhoneProxy";
    public static final Object lockForRadioTechnologyChange = new Object();
    protected Phone mActivePhone;
    protected CommandsInterface mCommandsInterface;
    protected IccCardProxy mIccCardProxy;
    protected IccPhoneBookInterfaceManagerProxy mIccPhoneBookInterfaceManagerProxy;
    protected IccSmsInterfaceManager mIccSmsInterfaceManager;
    protected PhoneSubInfoProxy mPhoneSubInfoProxy;
    private boolean mResetModemOnRadioTechnologyChange;
    private int mRilVersion;

    public PhoneProxy(PhoneBase phone) {
        this.mResetModemOnRadioTechnologyChange = false;
        this.mActivePhone = phone;
        this.mResetModemOnRadioTechnologyChange = SystemProperties.getBoolean("persist.radio.reset_on_switch", false);
        this.mIccPhoneBookInterfaceManagerProxy = new IccPhoneBookInterfaceManagerProxy(phone.getIccPhoneBookInterfaceManager());
        this.mPhoneSubInfoProxy = new PhoneSubInfoProxy(phone.getPhoneSubInfo());
        this.mCommandsInterface = ((PhoneBase) this.mActivePhone).mCi;
        this.mCommandsInterface.registerForRilConnected(this, 4, null);
        this.mCommandsInterface.registerForOn(this, 2, null);
        this.mCommandsInterface.registerForVoiceRadioTechChanged(this, 1, null);
        init();
        if (phone.getPhoneType() == 1) {
            this.mIccCardProxy.setVoiceRadioTech(3);
        } else if (phone.getPhoneType() == 2) {
            this.mIccCardProxy.setVoiceRadioTech(6);
        }
    }

    protected void init() {
        this.mIccSmsInterfaceManager = new IccSmsInterfaceManager((PhoneBase) this.mActivePhone);
        this.mIccCardProxy = new IccCardProxy(this.mActivePhone.getContext(), this.mCommandsInterface);
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        AsyncResult ar = (AsyncResult) msg.obj;
        switch (msg.what) {
            case 1:
            case 3:
                String what = msg.what == 1 ? "EVENT_VOICE_RADIO_TECH_CHANGED" : "EVENT_REQUEST_VOICE_RADIO_TECH_DONE";
                if (ar.exception == null) {
                    if (ar.result != null && ((int[]) ar.result).length != 0) {
                        int newVoiceTech = ((int[]) ar.result)[0];
                        logd(what + ": newVoiceTech=" + newVoiceTech);
                        phoneObjectUpdater(newVoiceTech);
                    } else {
                        loge(what + ": has no tech!");
                    }
                } else {
                    loge(what + ": exception=" + ar.exception);
                }
                break;
            case 2:
                this.mCommandsInterface.getVoiceRadioTechnology(obtainMessage(3));
                break;
            case 4:
                if (ar.exception == null && ar.result != null) {
                    this.mRilVersion = ((Integer) ar.result).intValue();
                } else {
                    logd("Unexpected exception on EVENT_RIL_CONNECTED");
                    this.mRilVersion = -1;
                }
                break;
            case 5:
                phoneObjectUpdater(msg.arg1);
                break;
            default:
                loge("Error! This handler was not registered for this message type. Message: " + msg.what);
                break;
        }
        super.handleMessage(msg);
    }

    private static void logd(String msg) {
        Rlog.d(LOG_TAG, "[PhoneProxy] " + msg);
    }

    private void loge(String msg) {
        Rlog.e(LOG_TAG, "[PhoneProxy] " + msg);
    }

    public void phoneObjectUpdater(int newVoiceRadioTech) {
        logd("phoneObjectUpdater: newVoiceRadioTech=" + newVoiceRadioTech);
        if (this.mActivePhone != null) {
            if (newVoiceRadioTech == 14) {
                int volteReplacementRat = this.mActivePhone.getContext().getResources().getInteger(R.integer.config_bluetooth_idle_cur_ma);
                logd("phoneObjectUpdater: volteReplacementRat=" + volteReplacementRat);
                if (volteReplacementRat != 0) {
                    newVoiceRadioTech = volteReplacementRat;
                }
            }
            if (this.mRilVersion == 6 && getLteOnCdmaMode() == 1) {
                if (this.mActivePhone.getPhoneType() == 2) {
                    logd("phoneObjectUpdater: LTE ON CDMA property is set. Use CDMA Phone newVoiceRadioTech=" + newVoiceRadioTech + " mActivePhone=" + this.mActivePhone.getPhoneName());
                    return;
                } else {
                    logd("phoneObjectUpdater: LTE ON CDMA property is set. Switch to CDMALTEPhone newVoiceRadioTech=" + newVoiceRadioTech + " mActivePhone=" + this.mActivePhone.getPhoneName());
                    newVoiceRadioTech = 6;
                }
            } else if ((ServiceState.isCdma(newVoiceRadioTech) && this.mActivePhone.getPhoneType() == 2) || (ServiceState.isGsm(newVoiceRadioTech) && this.mActivePhone.getPhoneType() == 1)) {
                logd("phoneObjectUpdater: No change ignore, newVoiceRadioTech=" + newVoiceRadioTech + " mActivePhone=" + this.mActivePhone.getPhoneName());
                return;
            }
        }
        if (newVoiceRadioTech == 0) {
            logd("phoneObjectUpdater: Unknown rat ignore,  newVoiceRadioTech=Unknown. mActivePhone=" + this.mActivePhone.getPhoneName());
            return;
        }
        boolean oldPowerState = false;
        if (this.mResetModemOnRadioTechnologyChange && this.mCommandsInterface.getRadioState().isOn()) {
            oldPowerState = true;
            logd("phoneObjectUpdater: Setting Radio Power to Off");
            this.mCommandsInterface.setRadioPower(false, null);
        }
        deleteAndCreatePhone(newVoiceRadioTech);
        if (this.mResetModemOnRadioTechnologyChange && oldPowerState) {
            logd("phoneObjectUpdater: Resetting Radio");
            this.mCommandsInterface.setRadioPower(oldPowerState, null);
        }
        this.mIccSmsInterfaceManager.updatePhoneObject((PhoneBase) this.mActivePhone);
        this.mIccPhoneBookInterfaceManagerProxy.setmIccPhoneBookInterfaceManager(this.mActivePhone.getIccPhoneBookInterfaceManager());
        this.mPhoneSubInfoProxy.setmPhoneSubInfo(this.mActivePhone.getPhoneSubInfo());
        this.mCommandsInterface = ((PhoneBase) this.mActivePhone).mCi;
        this.mIccCardProxy.setVoiceRadioTech(newVoiceRadioTech);
        sendBroadcastStickyIntent();
    }

    protected void sendBroadcastStickyIntent() {
        Intent intent = new Intent("android.intent.action.RADIO_TECHNOLOGY");
        intent.addFlags(536870912);
        intent.putExtra("phoneName", this.mActivePhone.getPhoneName());
        ActivityManagerNative.broadcastStickyIntent(intent, (String) null, -1);
    }

    private void deleteAndCreatePhone(int newVoiceRadioTech) {
        String outgoingPhoneName = "Unknown";
        Phone oldPhone = this.mActivePhone;
        CallManager cm = CallManager.getInstance();
        if (oldPhone != null) {
            outgoingPhoneName = ((PhoneBase) oldPhone).getPhoneName();
        }
        logd("Switching Voice Phone : " + outgoingPhoneName + " >>> " + (ServiceState.isGsm(newVoiceRadioTech) ? "GSM" : "CDMA"));
        if (oldPhone != null) {
            logd("Disposing old phone..");
            oldPhone.dispose();
            cm.unregisterPhone(oldPhone);
            cm.unregisterPhone(cm.getImsPhone());
        }
        createNewPhone(newVoiceRadioTech);
        if (oldPhone != null) {
            oldPhone.removeReferences();
        }
        if (this.mActivePhone != null) {
            cm.registerPhone(this.mActivePhone);
            cm.registerPhone(cm.getImsPhone());
        }
    }

    protected void createNewPhone(int newVoiceRadioTech) {
        if (ServiceState.isCdma(newVoiceRadioTech)) {
            this.mActivePhone = PhoneFactory.getCdmaPhone();
        } else if (ServiceState.isGsm(newVoiceRadioTech)) {
            this.mActivePhone = PhoneFactory.getGsmPhone();
        }
    }

    @Override // com.android.internal.telephony.Phone
    public void updatePhoneObject(int voiceRadioTech) {
        logd("updatePhoneObject: radioTechnology=" + voiceRadioTech);
        sendMessage(obtainMessage(5, voiceRadioTech, 0, null));
    }

    @Override // com.android.internal.telephony.Phone
    public ServiceState getServiceState() {
        return this.mActivePhone.getServiceState();
    }

    @Override // com.android.internal.telephony.Phone
    public CellLocation getCellLocation() {
        return this.mActivePhone.getCellLocation();
    }

    @Override // com.android.internal.telephony.Phone
    public List<CellInfo> getAllCellInfo() {
        return this.mActivePhone.getAllCellInfo();
    }

    @Override // com.android.internal.telephony.Phone
    public void setCellInfoListRate(int rateInMillis) {
        this.mActivePhone.setCellInfoListRate(rateInMillis);
    }

    @Override // com.android.internal.telephony.Phone
    public PhoneConstants.DataState getDataConnectionState() {
        return this.mActivePhone.getDataConnectionState("default");
    }

    @Override // com.android.internal.telephony.Phone
    public PhoneConstants.DataState getDataConnectionState(String apnType) {
        return this.mActivePhone.getDataConnectionState(apnType);
    }

    @Override // com.android.internal.telephony.Phone
    public Phone.DataActivityState getDataActivityState() {
        return this.mActivePhone.getDataActivityState();
    }

    @Override // com.android.internal.telephony.Phone
    public Context getContext() {
        return this.mActivePhone.getContext();
    }

    @Override // com.android.internal.telephony.Phone
    public void disableDnsCheck(boolean b) {
        this.mActivePhone.disableDnsCheck(b);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isDnsCheckDisabled() {
        return this.mActivePhone.isDnsCheckDisabled();
    }

    @Override // com.android.internal.telephony.Phone
    public PhoneConstants.State getState() {
        return this.mActivePhone.getState();
    }

    @Override // com.android.internal.telephony.Phone
    public String getPhoneName() {
        return this.mActivePhone.getPhoneName();
    }

    @Override // com.android.internal.telephony.Phone
    public int getPhoneType() {
        return this.mActivePhone.getPhoneType();
    }

    @Override // com.android.internal.telephony.Phone
    public String[] getActiveApnTypes() {
        return this.mActivePhone.getActiveApnTypes();
    }

    @Override // com.android.internal.telephony.Phone
    public String getActiveApnHost(String apnType) {
        return this.mActivePhone.getActiveApnHost(apnType);
    }

    @Override // com.android.internal.telephony.Phone
    public LinkProperties getLinkProperties(String apnType) {
        return this.mActivePhone.getLinkProperties(apnType);
    }

    @Override // com.android.internal.telephony.Phone
    public LinkCapabilities getLinkCapabilities(String apnType) {
        return this.mActivePhone.getLinkCapabilities(apnType);
    }

    @Override // com.android.internal.telephony.Phone
    public SignalStrength getSignalStrength() {
        return this.mActivePhone.getSignalStrength();
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForUnknownConnection(Handler h, int what, Object obj) {
        this.mActivePhone.registerForUnknownConnection(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForUnknownConnection(Handler h) {
        this.mActivePhone.unregisterForUnknownConnection(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForPreciseCallStateChanged(Handler h, int what, Object obj) {
        this.mActivePhone.registerForPreciseCallStateChanged(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForPreciseCallStateChanged(Handler h) {
        this.mActivePhone.unregisterForPreciseCallStateChanged(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForNewRingingConnection(Handler h, int what, Object obj) {
        this.mActivePhone.registerForNewRingingConnection(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForNewRingingConnection(Handler h) {
        this.mActivePhone.unregisterForNewRingingConnection(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForIncomingRing(Handler h, int what, Object obj) {
        this.mActivePhone.registerForIncomingRing(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForIncomingRing(Handler h) {
        this.mActivePhone.unregisterForIncomingRing(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForDisconnect(Handler h, int what, Object obj) {
        this.mActivePhone.registerForDisconnect(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForDisconnect(Handler h) {
        this.mActivePhone.unregisterForDisconnect(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForMmiInitiate(Handler h, int what, Object obj) {
        this.mActivePhone.registerForMmiInitiate(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForMmiInitiate(Handler h) {
        this.mActivePhone.unregisterForMmiInitiate(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForMmiComplete(Handler h, int what, Object obj) {
        this.mActivePhone.registerForMmiComplete(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForMmiComplete(Handler h) {
        this.mActivePhone.unregisterForMmiComplete(h);
    }

    @Override // com.android.internal.telephony.Phone
    public List<? extends MmiCode> getPendingMmiCodes() {
        return this.mActivePhone.getPendingMmiCodes();
    }

    @Override // com.android.internal.telephony.Phone
    public void sendUssdResponse(String ussdMessge) {
        this.mActivePhone.sendUssdResponse(ussdMessge);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForServiceStateChanged(Handler h, int what, Object obj) {
        this.mActivePhone.registerForServiceStateChanged(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForServiceStateChanged(Handler h) {
        this.mActivePhone.unregisterForServiceStateChanged(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForSuppServiceNotification(Handler h, int what, Object obj) {
        this.mActivePhone.registerForSuppServiceNotification(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForSuppServiceNotification(Handler h) {
        this.mActivePhone.unregisterForSuppServiceNotification(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForSuppServiceFailed(Handler h, int what, Object obj) {
        this.mActivePhone.registerForSuppServiceFailed(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForSuppServiceFailed(Handler h) {
        this.mActivePhone.unregisterForSuppServiceFailed(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForInCallVoicePrivacyOn(Handler h, int what, Object obj) {
        this.mActivePhone.registerForInCallVoicePrivacyOn(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForInCallVoicePrivacyOn(Handler h) {
        this.mActivePhone.unregisterForInCallVoicePrivacyOn(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForInCallVoicePrivacyOff(Handler h, int what, Object obj) {
        this.mActivePhone.registerForInCallVoicePrivacyOff(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForInCallVoicePrivacyOff(Handler h) {
        this.mActivePhone.unregisterForInCallVoicePrivacyOff(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForCdmaOtaStatusChange(Handler h, int what, Object obj) {
        this.mActivePhone.registerForCdmaOtaStatusChange(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForCdmaOtaStatusChange(Handler h) {
        this.mActivePhone.unregisterForCdmaOtaStatusChange(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForSubscriptionInfoReady(Handler h, int what, Object obj) {
        this.mActivePhone.registerForSubscriptionInfoReady(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForSubscriptionInfoReady(Handler h) {
        this.mActivePhone.unregisterForSubscriptionInfoReady(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForEcmTimerReset(Handler h, int what, Object obj) {
        this.mActivePhone.registerForEcmTimerReset(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForEcmTimerReset(Handler h) {
        this.mActivePhone.unregisterForEcmTimerReset(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForRingbackTone(Handler h, int what, Object obj) {
        this.mActivePhone.registerForRingbackTone(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForRingbackTone(Handler h) {
        this.mActivePhone.unregisterForRingbackTone(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForResendIncallMute(Handler h, int what, Object obj) {
        this.mActivePhone.registerForResendIncallMute(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForResendIncallMute(Handler h) {
        this.mActivePhone.unregisterForResendIncallMute(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForSimRecordsLoaded(Handler h, int what, Object obj) {
        this.mActivePhone.registerForSimRecordsLoaded(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForSimRecordsLoaded(Handler h) {
        this.mActivePhone.unregisterForSimRecordsLoaded(h);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean getIccRecordsLoaded() {
        return this.mIccCardProxy.getIccRecordsLoaded();
    }

    @Override // com.android.internal.telephony.Phone
    public IccCard getIccCard() {
        return this.mIccCardProxy;
    }

    @Override // com.android.internal.telephony.Phone
    public void acceptCall() throws CallStateException {
        this.mActivePhone.acceptCall();
    }

    @Override // com.android.internal.telephony.Phone
    public void acceptCall(int callType) throws CallStateException {
        this.mActivePhone.acceptCall(callType);
    }

    @Override // com.android.internal.telephony.Phone
    public int getCallType(Call call) throws CallStateException {
        return this.mActivePhone.getCallType(call);
    }

    @Override // com.android.internal.telephony.Phone
    public int getCallDomain(Call call) throws CallStateException {
        return this.mActivePhone.getCallDomain(call);
    }

    @Override // com.android.internal.telephony.Phone
    public void rejectCall() throws CallStateException {
        this.mActivePhone.rejectCall();
    }

    @Override // com.android.internal.telephony.Phone
    public void switchHoldingAndActive() throws CallStateException {
        this.mActivePhone.switchHoldingAndActive();
    }

    @Override // com.android.internal.telephony.Phone
    public boolean canConference() {
        return this.mActivePhone.canConference();
    }

    @Override // com.android.internal.telephony.Phone
    public void conference() throws CallStateException {
        this.mActivePhone.conference();
    }

    @Override // com.android.internal.telephony.Phone
    public void changeConnectionType(Message msg, Connection conn, int newCallType, Map<String, String> newExtras) throws CallStateException {
        this.mActivePhone.changeConnectionType(msg, conn, newCallType, newExtras);
    }

    @Override // com.android.internal.telephony.Phone
    public void acceptConnectionTypeChange(Connection conn, Map<String, String> newExtras) throws CallStateException {
        this.mActivePhone.acceptConnectionTypeChange(conn, newExtras);
    }

    @Override // com.android.internal.telephony.Phone
    public void rejectConnectionTypeChange(Connection conn) throws CallStateException {
        this.mActivePhone.rejectConnectionTypeChange(conn);
    }

    @Override // com.android.internal.telephony.Phone
    public int getProposedConnectionType(Connection conn) throws CallStateException {
        return this.mActivePhone.getProposedConnectionType(conn);
    }

    @Override // com.android.internal.telephony.Phone
    public void enableEnhancedVoicePrivacy(boolean enable, Message onComplete) {
        this.mActivePhone.enableEnhancedVoicePrivacy(enable, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void getEnhancedVoicePrivacy(Message onComplete) {
        this.mActivePhone.getEnhancedVoicePrivacy(onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean canTransfer() {
        return this.mActivePhone.canTransfer();
    }

    @Override // com.android.internal.telephony.Phone
    public void explicitCallTransfer() throws CallStateException {
        this.mActivePhone.explicitCallTransfer();
    }

    @Override // com.android.internal.telephony.Phone
    public void clearDisconnected() {
        this.mActivePhone.clearDisconnected();
    }

    @Override // com.android.internal.telephony.Phone
    public Call getForegroundCall() {
        return this.mActivePhone.getForegroundCall();
    }

    @Override // com.android.internal.telephony.Phone
    public Call getBackgroundCall() {
        return this.mActivePhone.getBackgroundCall();
    }

    @Override // com.android.internal.telephony.Phone
    public Call getRingingCall() {
        return this.mActivePhone.getRingingCall();
    }

    @Override // com.android.internal.telephony.Phone
    public Connection dial(String dialString) throws CallStateException {
        return this.mActivePhone.dial(dialString);
    }

    @Override // com.android.internal.telephony.Phone
    public Connection dial(String dialString, UUSInfo uusInfo) throws CallStateException {
        return this.mActivePhone.dial(dialString, uusInfo);
    }

    @Override // com.android.internal.telephony.Phone
    public Connection dial(String dialString, int callType, String[] extras) throws CallStateException {
        return this.mActivePhone.dial(dialString, callType, extras);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean handlePinMmi(String dialString) {
        return this.mActivePhone.handlePinMmi(dialString);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean handleInCallMmiCommands(String command) throws CallStateException {
        return this.mActivePhone.handleInCallMmiCommands(command);
    }

    @Override // com.android.internal.telephony.Phone
    public void sendDtmf(char c) {
        this.mActivePhone.sendDtmf(c);
    }

    @Override // com.android.internal.telephony.Phone
    public void startDtmf(char c) {
        this.mActivePhone.startDtmf(c);
    }

    @Override // com.android.internal.telephony.Phone
    public void stopDtmf() {
        this.mActivePhone.stopDtmf();
    }

    @Override // com.android.internal.telephony.Phone
    public void setRadioPower(boolean power) {
        this.mActivePhone.setRadioPower(power);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean getMessageWaitingIndicator() {
        return this.mActivePhone.getMessageWaitingIndicator();
    }

    @Override // com.android.internal.telephony.Phone
    public boolean getCallForwardingIndicator() {
        return this.mActivePhone.getCallForwardingIndicator();
    }

    @Override // com.android.internal.telephony.Phone
    public String getLine1Number() {
        return this.mActivePhone.getLine1Number();
    }

    @Override // com.android.internal.telephony.Phone
    public String getCdmaMin() {
        return this.mActivePhone.getCdmaMin();
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isMinInfoReady() {
        return this.mActivePhone.isMinInfoReady();
    }

    @Override // com.android.internal.telephony.Phone
    public String getCdmaPrlVersion() {
        return this.mActivePhone.getCdmaPrlVersion();
    }

    @Override // com.android.internal.telephony.Phone
    public String getLine1AlphaTag() {
        return this.mActivePhone.getLine1AlphaTag();
    }

    @Override // com.android.internal.telephony.Phone
    public void setLine1Number(String alphaTag, String number, Message onComplete) {
        this.mActivePhone.setLine1Number(alphaTag, number, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public String getVoiceMailNumber() {
        return this.mActivePhone.getVoiceMailNumber();
    }

    @Override // com.android.internal.telephony.Phone
    public int getVoiceMessageCount() {
        return this.mActivePhone.getVoiceMessageCount();
    }

    @Override // com.android.internal.telephony.Phone
    public String getVoiceMailAlphaTag() {
        return this.mActivePhone.getVoiceMailAlphaTag();
    }

    @Override // com.android.internal.telephony.Phone
    public void setVoiceMailNumber(String alphaTag, String voiceMailNumber, Message onComplete) {
        this.mActivePhone.setVoiceMailNumber(alphaTag, voiceMailNumber, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void getCallForwardingOption(int commandInterfaceCFReason, Message onComplete) {
        this.mActivePhone.getCallForwardingOption(commandInterfaceCFReason, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void setCallForwardingOption(int commandInterfaceCFReason, int commandInterfaceCFAction, String dialingNumber, int timerSeconds, Message onComplete) {
        this.mActivePhone.setCallForwardingOption(commandInterfaceCFReason, commandInterfaceCFAction, dialingNumber, timerSeconds, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void getOutgoingCallerIdDisplay(Message onComplete) {
        this.mActivePhone.getOutgoingCallerIdDisplay(onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void setOutgoingCallerIdDisplay(int commandInterfaceCLIRMode, Message onComplete) {
        this.mActivePhone.setOutgoingCallerIdDisplay(commandInterfaceCLIRMode, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void getCallWaiting(Message onComplete) {
        this.mActivePhone.getCallWaiting(onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void setCallWaiting(boolean enable, Message onComplete) {
        this.mActivePhone.setCallWaiting(enable, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void getAvailableNetworks(Message response) {
        this.mActivePhone.getAvailableNetworks(response);
    }

    @Override // com.android.internal.telephony.Phone
    public void setNetworkSelectionModeAutomatic(Message response) {
        this.mActivePhone.setNetworkSelectionModeAutomatic(response);
    }

    @Override // com.android.internal.telephony.Phone
    public void selectNetworkManually(OperatorInfo network, Message response) {
        this.mActivePhone.selectNetworkManually(network, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void setPreferredNetworkType(int networkType, Message response) {
        this.mActivePhone.setPreferredNetworkType(networkType, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void getPreferredNetworkType(Message response) {
        this.mActivePhone.getPreferredNetworkType(response);
    }

    @Override // com.android.internal.telephony.Phone
    public void getNeighboringCids(Message response) {
        this.mActivePhone.getNeighboringCids(response);
    }

    @Override // com.android.internal.telephony.Phone
    public void setOnPostDialCharacter(Handler h, int what, Object obj) {
        this.mActivePhone.setOnPostDialCharacter(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void setMute(boolean muted) {
        this.mActivePhone.setMute(muted);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean getMute() {
        return this.mActivePhone.getMute();
    }

    @Override // com.android.internal.telephony.Phone
    public void setEchoSuppressionEnabled(boolean enabled) {
        this.mActivePhone.setEchoSuppressionEnabled(enabled);
    }

    @Override // com.android.internal.telephony.Phone
    public void invokeOemRilRequestRaw(byte[] data, Message response) {
        this.mActivePhone.invokeOemRilRequestRaw(data, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void invokeOemRilRequestStrings(String[] strings, Message response) {
        this.mActivePhone.invokeOemRilRequestStrings(strings, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void getDataCallList(Message response) {
        this.mActivePhone.getDataCallList(response);
    }

    @Override // com.android.internal.telephony.Phone
    public void updateServiceLocation() {
        this.mActivePhone.updateServiceLocation();
    }

    @Override // com.android.internal.telephony.Phone
    public void enableLocationUpdates() {
        this.mActivePhone.enableLocationUpdates();
    }

    @Override // com.android.internal.telephony.Phone
    public void disableLocationUpdates() {
        this.mActivePhone.disableLocationUpdates();
    }

    @Override // com.android.internal.telephony.Phone
    public void setUnitTestMode(boolean f) {
        this.mActivePhone.setUnitTestMode(f);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean getUnitTestMode() {
        return this.mActivePhone.getUnitTestMode();
    }

    @Override // com.android.internal.telephony.Phone
    public void setBandMode(int bandMode, Message response) {
        this.mActivePhone.setBandMode(bandMode, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void queryAvailableBandMode(Message response) {
        this.mActivePhone.queryAvailableBandMode(response);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean getDataRoamingEnabled() {
        return this.mActivePhone.getDataRoamingEnabled();
    }

    @Override // com.android.internal.telephony.Phone
    public void setDataRoamingEnabled(boolean enable) {
        this.mActivePhone.setDataRoamingEnabled(enable);
    }

    @Override // com.android.internal.telephony.Phone
    public void queryCdmaRoamingPreference(Message response) {
        this.mActivePhone.queryCdmaRoamingPreference(response);
    }

    @Override // com.android.internal.telephony.Phone
    public void setCdmaRoamingPreference(int cdmaRoamingType, Message response) {
        this.mActivePhone.setCdmaRoamingPreference(cdmaRoamingType, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void setCdmaSubscription(int cdmaSubscriptionType, Message response) {
        this.mActivePhone.setCdmaSubscription(cdmaSubscriptionType, response);
    }

    @Override // com.android.internal.telephony.Phone
    public SimulatedRadioControl getSimulatedRadioControl() {
        return this.mActivePhone.getSimulatedRadioControl();
    }

    @Override // com.android.internal.telephony.Phone
    public int enableApnType(String type) {
        return this.mActivePhone.enableApnType(type);
    }

    @Override // com.android.internal.telephony.Phone
    public int disableApnType(String type) {
        return this.mActivePhone.disableApnType(type);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isDataConnectivityPossible() {
        return this.mActivePhone.isDataConnectivityPossible("default");
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isDataConnectivityPossible(String apnType) {
        return this.mActivePhone.isDataConnectivityPossible(apnType);
    }

    @Override // com.android.internal.telephony.Phone
    public String getDeviceId() {
        return this.mActivePhone.getDeviceId();
    }

    @Override // com.android.internal.telephony.Phone
    public String getDeviceSvn() {
        return this.mActivePhone.getDeviceSvn();
    }

    @Override // com.android.internal.telephony.Phone
    public String getSubscriberId() {
        return this.mActivePhone.getSubscriberId();
    }

    @Override // com.android.internal.telephony.Phone
    public String getGroupIdLevel1() {
        return this.mActivePhone.getGroupIdLevel1();
    }

    @Override // com.android.internal.telephony.Phone
    public String getIccSerialNumber() {
        return this.mActivePhone.getIccSerialNumber();
    }

    @Override // com.android.internal.telephony.Phone
    public String getEsn() {
        return this.mActivePhone.getEsn();
    }

    @Override // com.android.internal.telephony.Phone
    public String getMeid() {
        return this.mActivePhone.getMeid();
    }

    @Override // com.android.internal.telephony.Phone
    public String getMsisdn() {
        return this.mActivePhone.getMsisdn();
    }

    @Override // com.android.internal.telephony.Phone
    public String getImei() {
        return this.mActivePhone.getImei();
    }

    @Override // com.android.internal.telephony.Phone
    public PhoneSubInfo getPhoneSubInfo() {
        return this.mActivePhone.getPhoneSubInfo();
    }

    @Override // com.android.internal.telephony.Phone
    public IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager() {
        return this.mActivePhone.getIccPhoneBookInterfaceManager();
    }

    @Override // com.android.internal.telephony.Phone
    public void setTTYMode(int ttyMode, Message onComplete) {
        this.mActivePhone.setTTYMode(ttyMode, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void queryTTYMode(Message onComplete) {
        this.mActivePhone.queryTTYMode(onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void activateCellBroadcastSms(int activate, Message response) {
        this.mActivePhone.activateCellBroadcastSms(activate, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void getCellBroadcastSmsConfig(Message response) {
        this.mActivePhone.getCellBroadcastSmsConfig(response);
    }

    @Override // com.android.internal.telephony.Phone
    public void setCellBroadcastSmsConfig(int[] configValuesArray, Message response) {
        this.mActivePhone.setCellBroadcastSmsConfig(configValuesArray, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void notifyDataActivity() {
        this.mActivePhone.notifyDataActivity();
    }

    @Override // com.android.internal.telephony.Phone
    public void getSmscAddress(Message result) {
        this.mActivePhone.getSmscAddress(result);
    }

    @Override // com.android.internal.telephony.Phone
    public void setSmscAddress(String address, Message result) {
        this.mActivePhone.setSmscAddress(address, result);
    }

    @Override // com.android.internal.telephony.Phone
    public int getCdmaEriIconIndex() {
        return this.mActivePhone.getCdmaEriIconIndex();
    }

    @Override // com.android.internal.telephony.Phone
    public String getCdmaEriText() {
        return this.mActivePhone.getCdmaEriText();
    }

    @Override // com.android.internal.telephony.Phone
    public int getCdmaEriIconMode() {
        return this.mActivePhone.getCdmaEriIconMode();
    }

    public Phone getActivePhone() {
        return this.mActivePhone;
    }

    @Override // com.android.internal.telephony.Phone
    public void sendBurstDtmf(String dtmfString, int on, int off, Message onComplete) {
        this.mActivePhone.sendBurstDtmf(dtmfString, on, off, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void exitEmergencyCallbackMode() {
        this.mActivePhone.exitEmergencyCallbackMode();
    }

    @Override // com.android.internal.telephony.Phone
    public boolean needsOtaServiceProvisioning() {
        return this.mActivePhone.needsOtaServiceProvisioning();
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isOtaSpNumber(String dialStr) {
        return this.mActivePhone.isOtaSpNumber(dialStr);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForCallWaiting(Handler h, int what, Object obj) {
        this.mActivePhone.registerForCallWaiting(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForCallWaiting(Handler h) {
        this.mActivePhone.unregisterForCallWaiting(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForSignalInfo(Handler h, int what, Object obj) {
        this.mActivePhone.registerForSignalInfo(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForSignalInfo(Handler h) {
        this.mActivePhone.unregisterForSignalInfo(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForDisplayInfo(Handler h, int what, Object obj) {
        this.mActivePhone.registerForDisplayInfo(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForDisplayInfo(Handler h) {
        this.mActivePhone.unregisterForDisplayInfo(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForNumberInfo(Handler h, int what, Object obj) {
        this.mActivePhone.registerForNumberInfo(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForNumberInfo(Handler h) {
        this.mActivePhone.unregisterForNumberInfo(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForRedirectedNumberInfo(Handler h, int what, Object obj) {
        this.mActivePhone.registerForRedirectedNumberInfo(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForRedirectedNumberInfo(Handler h) {
        this.mActivePhone.unregisterForRedirectedNumberInfo(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForLineControlInfo(Handler h, int what, Object obj) {
        this.mActivePhone.registerForLineControlInfo(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForLineControlInfo(Handler h) {
        this.mActivePhone.unregisterForLineControlInfo(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerFoT53ClirlInfo(Handler h, int what, Object obj) {
        this.mActivePhone.registerFoT53ClirlInfo(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForT53ClirInfo(Handler h) {
        this.mActivePhone.unregisterForT53ClirInfo(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForT53AudioControlInfo(Handler h, int what, Object obj) {
        this.mActivePhone.registerForT53AudioControlInfo(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForT53AudioControlInfo(Handler h) {
        this.mActivePhone.unregisterForT53AudioControlInfo(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void setOnEcbModeExitResponse(Handler h, int what, Object obj) {
        this.mActivePhone.setOnEcbModeExitResponse(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unsetOnEcbModeExitResponse(Handler h) {
        this.mActivePhone.unsetOnEcbModeExitResponse(h);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isManualNetSelAllowed() {
        return this.mActivePhone.isManualNetSelAllowed();
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isCspPlmnEnabled() {
        return this.mActivePhone.isCspPlmnEnabled();
    }

    @Override // com.android.internal.telephony.Phone
    public IsimRecords getIsimRecords() {
        return this.mActivePhone.getIsimRecords();
    }

    @Override // com.android.internal.telephony.Phone
    public void requestIsimAuthentication(String nonce, Message response) {
        this.mActivePhone.requestIsimAuthentication(nonce, response);
    }

    @Override // com.android.internal.telephony.Phone
    public int getLteOnCdmaMode() {
        return this.mActivePhone.getLteOnCdmaMode();
    }

    @Override // com.android.internal.telephony.Phone
    public void setVoiceMessageWaiting(int line, int countWaiting) {
        this.mActivePhone.setVoiceMessageWaiting(line, countWaiting);
    }

    @Override // com.android.internal.telephony.Phone
    public UsimServiceTable getUsimServiceTable() {
        return this.mActivePhone.getUsimServiceTable();
    }

    @Override // com.android.internal.telephony.Phone
    public void dispose() {
        this.mCommandsInterface.unregisterForOn(this);
        this.mCommandsInterface.unregisterForVoiceRadioTechChanged(this);
        this.mCommandsInterface.unregisterForRilConnected(this);
    }

    @Override // com.android.internal.telephony.Phone
    public void removeReferences() {
        this.mActivePhone = null;
        this.mCommandsInterface = null;
    }

    @Override // com.android.internal.telephony.Phone
    public void getCallBarringOption(String facility, String password, Message onComplete) {
        this.mActivePhone.getCallBarringOption(facility, password, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void setCallBarringOption(String facility, boolean lockState, String password, Message onComplete) {
        this.mActivePhone.setCallBarringOption(facility, lockState, password, onComplete);
    }

    @Override // com.android.internal.telephony.Phone
    public void requestChangeCbPsw(String facility, String oldPwd, String newPwd, Message result) {
        this.mActivePhone.requestChangeCbPsw(facility, oldPwd, newPwd, result);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isRadioOn() {
        return this.mCommandsInterface.getRadioState().isOn();
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForModifyCallRequest(Handler h, int what, Object obj) throws CallStateException {
        this.mActivePhone.registerForModifyCallRequest(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isVTModifyAllowed() throws CallStateException {
        throw new CallStateException("isVTModifyAllowed is not supported in this phone " + this);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForModifyCallRequest(Handler h) throws CallStateException {
        this.mActivePhone.unregisterForModifyCallRequest(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void registerForAvpUpgradeFailure(Handler h, int what, Object obj) throws CallStateException {
        this.mActivePhone.registerForAvpUpgradeFailure(h, what, obj);
    }

    @Override // com.android.internal.telephony.Phone
    public void unregisterForAvpUpgradeFailure(Handler h) throws CallStateException {
        this.mActivePhone.unregisterForAvpUpgradeFailure(h);
    }

    @Override // com.android.internal.telephony.Phone
    public void addParticipant(String dialString, int clir, int callType, String[] extras, Message response) throws CallStateException {
        this.mActivePhone.addParticipant(dialString, clir, callType, extras, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void addParticipant(String dialString, int clir, int callType, String[] extras) throws CallStateException {
        addParticipant(dialString, clir, callType, extras, null);
    }

    @Override // com.android.internal.telephony.Phone
    public void hangupWithReason(int callId, String userUri, boolean mpty, int failCause, String errorInfo) throws CallStateException {
        this.mActivePhone.hangupWithReason(callId, userUri, mpty, failCause, errorInfo);
    }

    @Override // com.android.internal.telephony.Phone
    public void deflectCall(int connId, String number, Message response) throws CallStateException {
        this.mActivePhone.deflectCall(connId, number, response);
    }

    @Override // com.android.internal.telephony.Phone
    public int getSubscription() {
        return this.mActivePhone.getSubscription();
    }

    @Override // com.android.internal.telephony.Phone
    public void setTuneAway(boolean tuneAway, Message response) {
        this.mActivePhone.setTuneAway(tuneAway, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void setPrioritySub(int subIndex, Message response) {
        this.mActivePhone.setPrioritySub(subIndex, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void setDefaultVoiceSub(int subIndex, Message response) {
        this.mActivePhone.setDefaultVoiceSub(subIndex, response);
    }

    @Override // com.android.internal.telephony.Phone
    public void setLocalCallHold(int lchStatus, Message response) {
        this.mActivePhone.setLocalCallHold(lchStatus, response);
    }

    @Override // com.android.internal.telephony.Phone
    public boolean isMMI(String dialString) {
        return this.mActivePhone.isMMI(dialString);
    }
}
