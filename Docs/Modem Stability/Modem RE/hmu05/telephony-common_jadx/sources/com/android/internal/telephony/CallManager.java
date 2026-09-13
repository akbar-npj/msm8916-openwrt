package com.android.internal.telephony;

import android.R;
import android.content.Context;
import android.media.AudioManager;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.telephony.MSimTelephonyManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import com.android.internal.telephony.sip.SipPhone;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class CallManager {
    private static final boolean DBG = true;
    private static final int EVENT_CALL_MODIFY = 121;
    private static final int EVENT_CALL_WAITING = 108;
    private static final int EVENT_CDMA_OTA_STATUS_CHANGE = 111;
    private static final int EVENT_DISCONNECT = 100;
    private static final int EVENT_DISPLAY_INFO = 109;
    private static final int EVENT_ECM_TIMER_RESET = 115;
    private static final int EVENT_INCOMING_RING = 104;
    private static final int EVENT_IN_CALL_VOICE_PRIVACY_OFF = 107;
    private static final int EVENT_IN_CALL_VOICE_PRIVACY_ON = 106;
    private static final int EVENT_MMI_COMPLETE = 114;
    private static final int EVENT_MMI_INITIATE = 113;
    protected static final int EVENT_NEW_RINGING_CONNECTION = 102;
    private static final int EVENT_POST_DIAL_CHARACTER = 119;
    private static final int EVENT_PRECISE_CALL_STATE_CHANGED = 101;
    private static final int EVENT_RESEND_INCALL_MUTE = 112;
    private static final int EVENT_RINGBACK_TONE = 105;
    private static final int EVENT_SERVICE_STATE_CHANGED = 118;
    private static final int EVENT_SIGNAL_INFO = 110;
    private static final int EVENT_SUBSCRIPTION_INFO_READY = 116;
    private static final int EVENT_SUPP_SERVICE_FAILED = 117;
    private static final int EVENT_SUPP_SERVICE_NOTIFY = 120;
    private static final int EVENT_UNKNOWN_CONNECTION = 103;
    protected static CallManager INSTANCE = null;
    private static final String LOG_TAG = "CallManager";
    private static final String PROPERTY_QCHAT_ENABLED = "persist.atel.qchat_enabled";
    private static final boolean VDBG = false;
    protected String mDialString;
    protected CmHandler mHandler;
    protected final ArrayList<Connection> mEmptyConnections = new ArrayList<>();
    protected boolean mSpeedUpAudioForMtCall = false;
    protected final RegistrantList mPreciseCallStateRegistrants = new RegistrantList();
    protected final RegistrantList mNewRingingConnectionRegistrants = new RegistrantList();
    protected final RegistrantList mIncomingRingRegistrants = new RegistrantList();
    protected final RegistrantList mDisconnectRegistrants = new RegistrantList();
    protected final RegistrantList mMmiRegistrants = new RegistrantList();
    protected final RegistrantList mUnknownConnectionRegistrants = new RegistrantList();
    protected final RegistrantList mRingbackToneRegistrants = new RegistrantList();
    protected final RegistrantList mInCallVoicePrivacyOnRegistrants = new RegistrantList();
    protected final RegistrantList mInCallVoicePrivacyOffRegistrants = new RegistrantList();
    protected final RegistrantList mCallWaitingRegistrants = new RegistrantList();
    protected final RegistrantList mDisplayInfoRegistrants = new RegistrantList();
    protected final RegistrantList mSignalInfoRegistrants = new RegistrantList();
    protected final RegistrantList mCdmaOtaStatusChangeRegistrants = new RegistrantList();
    protected final RegistrantList mResendIncallMuteRegistrants = new RegistrantList();
    protected final RegistrantList mMmiInitiateRegistrants = new RegistrantList();
    protected final RegistrantList mMmiCompleteRegistrants = new RegistrantList();
    protected final RegistrantList mEcmTimerResetRegistrants = new RegistrantList();
    protected final RegistrantList mSubscriptionInfoReadyRegistrants = new RegistrantList();
    protected final RegistrantList mSuppServiceFailedRegistrants = new RegistrantList();
    protected final RegistrantList mSuppServiceNotificationRegistrants = new RegistrantList();
    protected final RegistrantList mServiceStateChangedRegistrants = new RegistrantList();
    protected final RegistrantList mPostDialCharacterRegistrants = new RegistrantList();
    protected final RegistrantList mCallModifyRegistrants = new RegistrantList();
    protected final ArrayList<Phone> mPhones = new ArrayList<>();
    protected final ArrayList<Call> mRingingCalls = new ArrayList<>();
    protected final ArrayList<Call> mBackgroundCalls = new ArrayList<>();
    protected final ArrayList<Call> mForegroundCalls = new ArrayList<>();
    private Phone mDefaultPhone = null;
    private Phone mImsPhone = null;

    protected CallManager() {
        initHandler();
    }

    public static CallManager getInstance() {
        if (INSTANCE == null) {
            if (isUseExtCallManager()) {
                INSTANCE = new ExtCallManager();
            } else {
                INSTANCE = new CallManager();
            }
        }
        return INSTANCE;
    }

    private static boolean isUseExtCallManager() {
        return MSimTelephonyManager.getDefault().isMultiSimEnabled() || SystemProperties.getBoolean(PROPERTY_QCHAT_ENABLED, false);
    }

    protected void initHandler() {
        if (this.mHandler == null) {
            this.mHandler = new CmHandler();
        }
    }

    protected static Phone getPhoneBase(Phone phone) {
        if (phone instanceof PhoneProxy) {
            return phone.getForegroundCall().getPhone();
        }
        return phone;
    }

    public static boolean isSamePhone(Phone p1, Phone p2) {
        return getPhoneBase(p1) == getPhoneBase(p2);
    }

    public static boolean isCallOnCsvtEnabled() {
        return SystemProperties.getBoolean("persist.radio.csvt.enabled", false);
    }

    public static boolean isCallOnImsEnabled() {
        return SystemProperties.getBoolean("persist.radio.calls.on.ims", false);
    }

    public List<Phone> getAllPhones() {
        return Collections.unmodifiableList(this.mPhones);
    }

    public PhoneConstants.State getState() {
        PhoneConstants.State s = PhoneConstants.State.IDLE;
        for (Phone phone : this.mPhones) {
            if (phone.getState() == PhoneConstants.State.RINGING) {
                s = PhoneConstants.State.RINGING;
            } else if (phone.getState() == PhoneConstants.State.OFFHOOK && s == PhoneConstants.State.IDLE) {
                s = PhoneConstants.State.OFFHOOK;
            }
        }
        return s;
    }

    public int getServiceState() {
        int resultState = 1;
        for (Phone phone : this.mPhones) {
            int serviceState = phone.getServiceState().getState();
            if (serviceState == 0) {
                return serviceState;
            }
            if (serviceState == 1) {
                if (resultState == 2 || resultState == 3) {
                    resultState = serviceState;
                }
            } else if (serviceState == 2 && resultState == 3) {
                resultState = serviceState;
            }
        }
        return resultState;
    }

    public boolean registerPhone(Phone phone) {
        Phone basePhone = getPhoneBase(phone);
        if (phone != null && phone.getPhoneType() == 4) {
            this.mImsPhone = phone;
        }
        if (basePhone == null || this.mPhones.contains(basePhone)) {
            return false;
        }
        Rlog.d(LOG_TAG, "registerPhone(" + phone.getPhoneName() + " " + phone + ")");
        if (this.mPhones.isEmpty()) {
            this.mDefaultPhone = basePhone;
        }
        this.mPhones.add(basePhone);
        this.mRingingCalls.add(basePhone.getRingingCall());
        this.mBackgroundCalls.add(basePhone.getBackgroundCall());
        this.mForegroundCalls.add(basePhone.getForegroundCall());
        registerForPhoneStates(basePhone);
        return true;
    }

    public void unregisterPhone(Phone phone) {
        Phone basePhone = getPhoneBase(phone);
        if (basePhone != null && this.mPhones.contains(basePhone)) {
            Rlog.d(LOG_TAG, "unregisterPhone(" + phone.getPhoneName() + " " + phone + ")");
            this.mPhones.remove(basePhone);
            this.mRingingCalls.remove(basePhone.getRingingCall());
            this.mBackgroundCalls.remove(basePhone.getBackgroundCall());
            this.mForegroundCalls.remove(basePhone.getForegroundCall());
            unregisterForPhoneStates(basePhone);
            if (basePhone == this.mDefaultPhone) {
                if (this.mPhones.isEmpty()) {
                    this.mDefaultPhone = null;
                } else {
                    this.mDefaultPhone = this.mPhones.get(0);
                }
            }
        }
    }

    public Phone getDefaultPhone() {
        return this.mDefaultPhone;
    }

    public Phone getImsPhone() {
        return this.mImsPhone;
    }

    public Phone getFgPhone() {
        return getActiveFgCall().getPhone();
    }

    public Phone getBgPhone() {
        return getFirstActiveBgCall().getPhone();
    }

    public Phone getRingingPhone() {
        return getFirstActiveRingingCall().getPhone();
    }

    public Phone getPhoneInCall() {
        if (!getFirstActiveRingingCall().isIdle()) {
            Phone phone = getFirstActiveRingingCall().getPhone();
            return phone;
        }
        if (!getActiveFgCall().isIdle()) {
            Phone phone2 = getActiveFgCall().getPhone();
            return phone2;
        }
        Phone phone3 = getFirstActiveBgCall().getPhone();
        return phone3;
    }

    public void setAudioMode() {
        Context context = getContext();
        if (context != null) {
            AudioManager audioManager = (AudioManager) context.getSystemService("audio");
            switch (AnonymousClass1.$SwitchMap$com$android$internal$telephony$PhoneConstants$State[getState().ordinal()]) {
                case 1:
                    int curAudioMode = audioManager.getMode();
                    if (curAudioMode != 1) {
                        if (audioManager.getStreamVolume(2) >= 0) {
                            audioManager.requestAudioFocusForCall(2, 2);
                        }
                        if (!this.mSpeedUpAudioForMtCall) {
                            audioManager.setMode(1);
                        }
                    }
                    if (this.mSpeedUpAudioForMtCall && curAudioMode != 2) {
                        audioManager.setMode(2);
                    }
                    break;
                case 2:
                    Phone offhookPhone = getFgPhone();
                    if (getActiveFgCallState() == Call.State.IDLE) {
                        offhookPhone = getBgPhone();
                    }
                    int newAudioMode = 2;
                    if (offhookPhone instanceof SipPhone) {
                        Rlog.d(LOG_TAG, "setAudioMode Set audio mode for SIP call!");
                        newAudioMode = 3;
                    }
                    int currMode = audioManager.getMode();
                    if (currMode != newAudioMode || this.mSpeedUpAudioForMtCall) {
                        audioManager.requestAudioFocusForCall(0, 2);
                        Rlog.d(LOG_TAG, "setAudioMode Setting audio mode from " + currMode + " to " + newAudioMode);
                        audioManager.setMode(newAudioMode);
                    }
                    this.mSpeedUpAudioForMtCall = false;
                    break;
                case 3:
                    if (audioManager.getMode() != 0) {
                        audioManager.setMode(0);
                        audioManager.abandonAudioFocusForCall();
                    }
                    this.mSpeedUpAudioForMtCall = false;
                    break;
            }
            Rlog.d(LOG_TAG, "setAudioMode state = " + getState());
        }
    }

    /* JADX INFO: renamed from: com.android.internal.telephony.CallManager$1, reason: invalid class name */
    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$PhoneConstants$State = new int[PhoneConstants.State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$PhoneConstants$State[PhoneConstants.State.RINGING.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$PhoneConstants$State[PhoneConstants.State.OFFHOOK.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$PhoneConstants$State[PhoneConstants.State.IDLE.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
        }
    }

    protected Context getContext() {
        Phone defaultPhone = getDefaultPhone();
        if (defaultPhone == null) {
            return null;
        }
        return defaultPhone.getContext();
    }

    private void registerForPhoneStates(Phone phone) {
        phone.registerForPreciseCallStateChanged(this.mHandler, EVENT_PRECISE_CALL_STATE_CHANGED, null);
        phone.registerForDisconnect(this.mHandler, 100, null);
        phone.registerForNewRingingConnection(this.mHandler, EVENT_NEW_RINGING_CONNECTION, null);
        phone.registerForUnknownConnection(this.mHandler, EVENT_UNKNOWN_CONNECTION, null);
        phone.registerForIncomingRing(this.mHandler, EVENT_INCOMING_RING, null);
        phone.registerForRingbackTone(this.mHandler, EVENT_RINGBACK_TONE, null);
        phone.registerForInCallVoicePrivacyOn(this.mHandler, EVENT_IN_CALL_VOICE_PRIVACY_ON, null);
        phone.registerForInCallVoicePrivacyOff(this.mHandler, EVENT_IN_CALL_VOICE_PRIVACY_OFF, null);
        phone.registerForDisplayInfo(this.mHandler, EVENT_DISPLAY_INFO, null);
        phone.registerForSignalInfo(this.mHandler, EVENT_SIGNAL_INFO, null);
        phone.registerForResendIncallMute(this.mHandler, EVENT_RESEND_INCALL_MUTE, null);
        phone.registerForMmiInitiate(this.mHandler, EVENT_MMI_INITIATE, null);
        phone.registerForMmiComplete(this.mHandler, EVENT_MMI_COMPLETE, null);
        phone.registerForSuppServiceFailed(this.mHandler, EVENT_SUPP_SERVICE_FAILED, null);
        phone.registerForServiceStateChanged(this.mHandler, EVENT_SERVICE_STATE_CHANGED, null);
        if (phone.getPhoneType() == 1 || phone.getPhoneType() == 4) {
            phone.registerForSuppServiceNotification(this.mHandler, EVENT_SUPP_SERVICE_NOTIFY, null);
        }
        if (phone.getPhoneType() == 1 || phone.getPhoneType() == 2) {
            phone.setOnPostDialCharacter(this.mHandler, EVENT_POST_DIAL_CHARACTER, null);
        }
        if (phone.getPhoneType() == 2) {
            phone.registerForCdmaOtaStatusChange(this.mHandler, EVENT_CDMA_OTA_STATUS_CHANGE, null);
            phone.registerForSubscriptionInfoReady(this.mHandler, EVENT_SUBSCRIPTION_INFO_READY, null);
            phone.registerForCallWaiting(this.mHandler, EVENT_CALL_WAITING, null);
            phone.registerForEcmTimerReset(this.mHandler, EVENT_ECM_TIMER_RESET, null);
        }
        if (phone.getPhoneType() == 4) {
            phone.registerForEcmTimerReset(this.mHandler, EVENT_ECM_TIMER_RESET, null);
            try {
                phone.registerForModifyCallRequest(this.mHandler, EVENT_CALL_MODIFY, null);
            } catch (CallStateException e) {
                Rlog.e(LOG_TAG, "registerForModifyCallRequest: CallStateException:" + e);
            }
        }
    }

    private void unregisterForPhoneStates(Phone phone) {
        phone.unregisterForPreciseCallStateChanged(this.mHandler);
        phone.unregisterForDisconnect(this.mHandler);
        phone.unregisterForNewRingingConnection(this.mHandler);
        phone.unregisterForUnknownConnection(this.mHandler);
        phone.unregisterForIncomingRing(this.mHandler);
        phone.unregisterForRingbackTone(this.mHandler);
        phone.unregisterForInCallVoicePrivacyOn(this.mHandler);
        phone.unregisterForInCallVoicePrivacyOff(this.mHandler);
        phone.unregisterForDisplayInfo(this.mHandler);
        phone.unregisterForSignalInfo(this.mHandler);
        phone.unregisterForResendIncallMute(this.mHandler);
        phone.unregisterForMmiInitiate(this.mHandler);
        phone.unregisterForMmiComplete(this.mHandler);
        phone.unregisterForSuppServiceFailed(this.mHandler);
        if (phone.getPhoneType() == 1 || phone.getPhoneType() == 4) {
            phone.unregisterForSuppServiceNotification(this.mHandler);
        }
        phone.unregisterForServiceStateChanged(this.mHandler);
        if (phone.getPhoneType() == 1 || phone.getPhoneType() == 2) {
            phone.setOnPostDialCharacter(null, EVENT_POST_DIAL_CHARACTER, null);
        }
        if (phone.getPhoneType() == 2) {
            phone.unregisterForCdmaOtaStatusChange(this.mHandler);
            phone.unregisterForSubscriptionInfoReady(this.mHandler);
            phone.unregisterForCallWaiting(this.mHandler);
            phone.unregisterForEcmTimerReset(this.mHandler);
        }
        if (phone.getPhoneType() == 4) {
            phone.unregisterForEcmTimerReset(this.mHandler);
            try {
                phone.unregisterForModifyCallRequest(this.mHandler);
            } catch (CallStateException e) {
                Rlog.e(LOG_TAG, "unregisterForModifyCallRequest ", e);
            }
        }
    }

    public void acceptCall(Call ringingCall) throws CallStateException {
        acceptCall(ringingCall, 0);
    }

    public void acceptCall(Call ringingCall, int callType) throws CallStateException {
        Phone ringingPhone = ringingCall.getPhone();
        if (hasActiveFgCall()) {
            Phone activePhone = getActiveFgCall().getPhone();
            boolean hasBgCall = !activePhone.getBackgroundCall().isIdle();
            boolean sameChannel = activePhone == ringingPhone;
            if (sameChannel && hasBgCall) {
                getActiveFgCall().hangup();
            } else if (!sameChannel && !hasBgCall) {
                activePhone.switchHoldingAndActive();
            } else if (!sameChannel && hasBgCall) {
                getActiveFgCall().hangup();
            }
        }
        Context context = getContext();
        if (context == null) {
            Rlog.d(LOG_TAG, "Speedup Audio Path enhancement: Context is null");
        } else if (context.getResources().getBoolean(R.bool.config_batteryStatsResetOnUnplugHighBatteryLevel)) {
            Rlog.d(LOG_TAG, "Speedup Audio Path enhancement");
            AudioManager audioManager = (AudioManager) context.getSystemService("audio");
            int currMode = audioManager.getMode();
            if (!(ringingPhone instanceof SipPhone)) {
                if (currMode != 2) {
                    Rlog.d(LOG_TAG, "setAudioMode Setting audio mode from " + currMode + " to 2");
                    audioManager.setMode(2);
                }
                this.mSpeedUpAudioForMtCall = true;
            }
        }
        if (ringingPhone.getPhoneType() == 4) {
            ringingPhone.acceptCall(callType);
        } else {
            ringingPhone.acceptCall();
        }
    }

    public void rejectCall(Call ringingCall) throws CallStateException {
        Phone ringingPhone = ringingCall.getPhone();
        ringingPhone.rejectCall();
    }

    public void switchHoldingAndActive(Call heldCall) throws CallStateException {
        Phone activePhone = null;
        Phone heldPhone = null;
        if (hasActiveFgCall()) {
            activePhone = getActiveFgCall().getPhone();
        }
        if (heldCall != null) {
            heldPhone = heldCall.getPhone();
        }
        if (activePhone != null) {
            activePhone.switchHoldingAndActive();
        }
        if (heldPhone != null && heldPhone != activePhone) {
            heldPhone.switchHoldingAndActive();
        }
    }

    public void hangupForegroundResumeBackground(Call heldCall) throws CallStateException {
        if (hasActiveFgCall()) {
            Phone foregroundPhone = getFgPhone();
            if (heldCall != null) {
                Phone backgroundPhone = heldCall.getPhone();
                if (foregroundPhone == backgroundPhone) {
                    getActiveFgCall().hangup();
                } else {
                    getActiveFgCall().hangup();
                    switchHoldingAndActive(heldCall);
                }
            }
        }
    }

    public boolean canConference(Call heldCall) {
        Phone activePhone = null;
        Phone heldPhone = null;
        if (hasActiveFgCall()) {
            activePhone = getActiveFgCall().getPhone();
        }
        if (heldCall != null) {
            heldPhone = heldCall.getPhone();
        }
        return heldPhone.getClass().equals(activePhone.getClass());
    }

    public void conference(Call heldCall) throws CallStateException {
        Phone fgPhone = getFgPhone();
        if (fgPhone instanceof SipPhone) {
            ((SipPhone) fgPhone).conference(heldCall);
        } else {
            if (canConference(heldCall)) {
                fgPhone.conference();
                return;
            }
            throw new CallStateException("Can't conference foreground and selected background call");
        }
    }

    public Connection dial(Phone phone, String dialString) throws CallStateException {
        return dial(phone, dialString, 0, null);
    }

    public Connection dial(Phone phone, String dialString, int callType, String[] extras) throws CallStateException {
        Phone basePhone = getPhoneBase(phone);
        this.mDialString = dialString;
        if (!canDial(phone)) {
            throw new CallStateException("cannot dial in current state");
        }
        if (hasActiveFgCall()) {
            Phone activePhone = getActiveFgCall().getPhone();
            boolean hasBgCall = !activePhone.getBackgroundCall().isIdle();
            Rlog.d(LOG_TAG, "hasBgCall: " + hasBgCall + " sameChannel:" + (activePhone == basePhone));
            if (activePhone != basePhone) {
                if (hasBgCall) {
                    Rlog.d(LOG_TAG, "Hangup");
                    getActiveFgCall().hangup();
                } else {
                    Rlog.d(LOG_TAG, "Switch");
                    activePhone.switchHoldingAndActive();
                }
            }
        }
        if (phone.getPhoneType() == 4) {
            Connection result = basePhone.dial(dialString, callType, extras);
            return result;
        }
        Connection result2 = basePhone.dial(dialString);
        return result2;
    }

    protected boolean isExplicitCallTransferMMI(String dialString) {
        String newDialString = PhoneNumberUtils.stripSeparators(dialString);
        if (newDialString == null || newDialString.length() != 1) {
            return false;
        }
        char ch = newDialString.charAt(0);
        if (ch != '4') {
            return false;
        }
        return true;
    }

    public Connection dial(Phone phone, String dialString, UUSInfo uusInfo) throws CallStateException {
        return phone.dial(dialString, uusInfo);
    }

    public void clearDisconnected() {
        for (Phone phone : this.mPhones) {
            phone.clearDisconnected();
        }
    }

    protected boolean canDial(Phone phone) {
        int serviceState = phone.getServiceState().getState();
        boolean hasRingingCall = hasActiveRingingCall();
        Call.State fgCallState = getActiveFgCallState();
        boolean result = (serviceState == 3 || hasRingingCall || (fgCallState != Call.State.ACTIVE && fgCallState != Call.State.IDLE && ((fgCallState != Call.State.ALERTING || !isExplicitCallTransferMMI(this.mDialString)) && fgCallState != Call.State.DISCONNECTED))) ? false : true;
        if (!result) {
            Rlog.d(LOG_TAG, "canDial serviceState=" + serviceState + " hasRingingCall=" + hasRingingCall + " fgCallState=" + fgCallState);
        }
        return result;
    }

    public boolean canTransfer(Call heldCall) {
        Phone activePhone = null;
        Phone heldPhone = null;
        if (hasActiveFgCall()) {
            activePhone = getActiveFgCall().getPhone();
        }
        if (heldCall != null) {
            heldPhone = heldCall.getPhone();
        }
        return heldPhone == activePhone && activePhone.canTransfer();
    }

    public void explicitCallTransfer(Call heldCall) throws CallStateException {
        if (canTransfer(heldCall)) {
            heldCall.getPhone().explicitCallTransfer();
        }
    }

    public List<? extends MmiCode> getPendingMmiCodes(Phone phone) {
        Rlog.e(LOG_TAG, "getPendingMmiCodes not implemented");
        return null;
    }

    public boolean sendUssdResponse(Phone phone, String ussdMessge) {
        Rlog.e(LOG_TAG, "sendUssdResponse not implemented");
        return false;
    }

    public void setMute(boolean muted) {
        if (hasActiveFgCall()) {
            getActiveFgCall().getPhone().setMute(muted);
        }
    }

    public boolean getMute() {
        if (hasActiveFgCall()) {
            return getActiveFgCall().getPhone().getMute();
        }
        if (hasActiveBgCall()) {
            return getFirstActiveBgCall().getPhone().getMute();
        }
        return false;
    }

    public void setEchoSuppressionEnabled(boolean enabled) {
        if (hasActiveFgCall()) {
            getActiveFgCall().getPhone().setEchoSuppressionEnabled(enabled);
        }
    }

    public boolean sendDtmf(char c) {
        if (!hasActiveFgCall()) {
            return false;
        }
        getActiveFgCall().getPhone().sendDtmf(c);
        return true;
    }

    public boolean startDtmf(char c) {
        if (!hasActiveFgCall()) {
            return false;
        }
        getActiveFgCall().getPhone().startDtmf(c);
        return true;
    }

    public void stopDtmf() {
        if (hasActiveFgCall()) {
            getFgPhone().stopDtmf();
        }
    }

    public boolean sendBurstDtmf(String dtmfString, int on, int off, Message onComplete) {
        if (!hasActiveFgCall()) {
            return false;
        }
        getActiveFgCall().getPhone().sendBurstDtmf(dtmfString, on, off, onComplete);
        return true;
    }

    public void registerForDisconnect(Handler h, int what, Object obj) {
        this.mDisconnectRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForDisconnect(Handler h) {
        this.mDisconnectRegistrants.remove(h);
    }

    public void registerForPreciseCallStateChanged(Handler h, int what, Object obj) {
        this.mPreciseCallStateRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForPreciseCallStateChanged(Handler h) {
        this.mPreciseCallStateRegistrants.remove(h);
    }

    public void registerForUnknownConnection(Handler h, int what, Object obj) {
        this.mUnknownConnectionRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForUnknownConnection(Handler h) {
        this.mUnknownConnectionRegistrants.remove(h);
    }

    public void registerForNewRingingConnection(Handler h, int what, Object obj) {
        this.mNewRingingConnectionRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForNewRingingConnection(Handler h) {
        this.mNewRingingConnectionRegistrants.remove(h);
    }

    public void registerForIncomingRing(Handler h, int what, Object obj) {
        this.mIncomingRingRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForIncomingRing(Handler h) {
        this.mIncomingRingRegistrants.remove(h);
    }

    public void registerForRingbackTone(Handler h, int what, Object obj) {
        this.mRingbackToneRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForRingbackTone(Handler h) {
        this.mRingbackToneRegistrants.remove(h);
    }

    public void registerForResendIncallMute(Handler h, int what, Object obj) {
        this.mResendIncallMuteRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForResendIncallMute(Handler h) {
        this.mResendIncallMuteRegistrants.remove(h);
    }

    public void registerForMmiInitiate(Handler h, int what, Object obj) {
        this.mMmiInitiateRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForMmiInitiate(Handler h) {
        this.mMmiInitiateRegistrants.remove(h);
    }

    public void registerForMmiComplete(Handler h, int what, Object obj) {
        this.mMmiCompleteRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForMmiComplete(Handler h) {
        this.mMmiCompleteRegistrants.remove(h);
    }

    public void registerForEcmTimerReset(Handler h, int what, Object obj) {
        this.mEcmTimerResetRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForEcmTimerReset(Handler h) {
        this.mEcmTimerResetRegistrants.remove(h);
    }

    public void registerForServiceStateChanged(Handler h, int what, Object obj) {
        this.mServiceStateChangedRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForServiceStateChanged(Handler h) {
        this.mServiceStateChangedRegistrants.remove(h);
    }

    public void registerForSuppServiceFailed(Handler h, int what, Object obj) {
        this.mSuppServiceFailedRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForSuppServiceFailed(Handler h) {
        this.mSuppServiceFailedRegistrants.remove(h);
    }

    public void registerForSuppServiceNotification(Handler h, int what, Object obj) {
        this.mSuppServiceNotificationRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForSuppServiceNotification(Handler h) {
        this.mSuppServiceNotificationRegistrants.remove(h);
    }

    public void registerForInCallVoicePrivacyOn(Handler h, int what, Object obj) {
        this.mInCallVoicePrivacyOnRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForInCallVoicePrivacyOn(Handler h) {
        this.mInCallVoicePrivacyOnRegistrants.remove(h);
    }

    public void registerForInCallVoicePrivacyOff(Handler h, int what, Object obj) {
        this.mInCallVoicePrivacyOffRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForInCallVoicePrivacyOff(Handler h) {
        this.mInCallVoicePrivacyOffRegistrants.remove(h);
    }

    public void registerForCallWaiting(Handler h, int what, Object obj) {
        this.mCallWaitingRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForCallWaiting(Handler h) {
        this.mCallWaitingRegistrants.remove(h);
    }

    public void registerForSignalInfo(Handler h, int what, Object obj) {
        this.mSignalInfoRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForSignalInfo(Handler h) {
        this.mSignalInfoRegistrants.remove(h);
    }

    public void registerForDisplayInfo(Handler h, int what, Object obj) {
        this.mDisplayInfoRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForDisplayInfo(Handler h) {
        this.mDisplayInfoRegistrants.remove(h);
    }

    public void registerForCdmaOtaStatusChange(Handler h, int what, Object obj) {
        this.mCdmaOtaStatusChangeRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForCdmaOtaStatusChange(Handler h) {
        this.mCdmaOtaStatusChangeRegistrants.remove(h);
    }

    public void registerForSubscriptionInfoReady(Handler h, int what, Object obj) {
        this.mSubscriptionInfoReadyRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForSubscriptionInfoReady(Handler h) {
        this.mSubscriptionInfoReadyRegistrants.remove(h);
    }

    public void registerForPostDialCharacter(Handler h, int what, Object obj) {
        this.mPostDialCharacterRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForPostDialCharacter(Handler h) {
        this.mPostDialCharacterRegistrants.remove(h);
    }

    public void registerForCallModify(Handler h, int what, Object obj) {
        this.mCallModifyRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForCallModify(Handler h) {
        this.mCallModifyRegistrants.remove(h);
    }

    public List<Call> getRingingCalls() {
        return Collections.unmodifiableList(this.mRingingCalls);
    }

    public List<Call> getForegroundCalls() {
        return Collections.unmodifiableList(this.mForegroundCalls);
    }

    public List<Call> getBackgroundCalls() {
        return Collections.unmodifiableList(this.mBackgroundCalls);
    }

    public boolean hasActiveFgCall() {
        return getFirstActiveCall(this.mForegroundCalls) != null;
    }

    public boolean hasActiveBgCall() {
        return getFirstActiveCall(this.mBackgroundCalls) != null;
    }

    public boolean hasActiveRingingCall() {
        return getFirstActiveCall(this.mRingingCalls) != null;
    }

    public Call getActiveFgCall() {
        Call call = getFirstNonIdleCall(this.mForegroundCalls);
        if (call == null) {
            if (this.mDefaultPhone == null) {
                return null;
            }
            return this.mDefaultPhone.getForegroundCall();
        }
        return call;
    }

    private Call getFirstNonIdleCall(List<Call> calls) {
        Call result = null;
        for (Call call : calls) {
            if (call.isIdle()) {
                if (call.getState() != Call.State.IDLE && result == null) {
                    result = call;
                }
            } else {
                return call;
            }
        }
        return result;
    }

    public Call getFirstActiveBgCall() {
        Call call = getFirstNonIdleCall(this.mBackgroundCalls);
        if (call == null) {
            if (this.mDefaultPhone == null) {
                return null;
            }
            return this.mDefaultPhone.getBackgroundCall();
        }
        return call;
    }

    public Call getFirstActiveRingingCall() {
        Call call = getFirstNonIdleCall(this.mRingingCalls);
        if (call == null) {
            if (this.mDefaultPhone == null) {
                return null;
            }
            return this.mDefaultPhone.getRingingCall();
        }
        return call;
    }

    public Call.State getActiveFgCallState() {
        Call fgCall = getActiveFgCall();
        return fgCall != null ? fgCall.getState() : Call.State.IDLE;
    }

    public List<Connection> getFgCallConnections() {
        Call fgCall = getActiveFgCall();
        return fgCall != null ? fgCall.getConnections() : this.mEmptyConnections;
    }

    public List<Connection> getBgCallConnections() {
        Call bgCall = getFirstActiveBgCall();
        return bgCall != null ? bgCall.getConnections() : this.mEmptyConnections;
    }

    public Connection getFgCallLatestConnection() {
        Call fgCall = getActiveFgCall();
        if (fgCall != null) {
            return fgCall.getLatestConnection();
        }
        return null;
    }

    public boolean hasDisconnectedFgCall() {
        return getFirstCallOfState(this.mForegroundCalls, Call.State.DISCONNECTED) != null;
    }

    public boolean hasDisconnectedBgCall() {
        return getFirstCallOfState(this.mBackgroundCalls, Call.State.DISCONNECTED) != null;
    }

    private Call getFirstActiveCall(ArrayList<Call> calls) {
        for (Call call : calls) {
            if (!call.isIdle()) {
                return call;
            }
        }
        return null;
    }

    private Call getFirstCallOfState(ArrayList<Call> calls, Call.State state) {
        for (Call call : calls) {
            if (call.getState() == state) {
                return call;
            }
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean hasMoreThanOneRingingCall() {
        int count = 0;
        for (Call call : this.mRingingCalls) {
            if (call.getState().isRinging() && (count = count + 1) > 1) {
                return true;
            }
        }
        return false;
    }

    public boolean isImsPhoneActive() {
        for (Phone phone : this.mPhones) {
            if (phone.getPhoneType() == 4 && phone.getState() != PhoneConstants.State.IDLE) {
                return true;
            }
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isRingingDuplicateCall() {
        return this.mRingingCalls.size() > 1 && this.mRingingCalls.get(0).getLatestConnection().getAddress().equals(this.mRingingCalls.get(1).getLatestConnection().getAddress());
    }

    protected class CmHandler extends Handler {
        protected CmHandler() {
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 100:
                    CallManager.this.mDisconnectRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    break;
                case CallManager.EVENT_PRECISE_CALL_STATE_CHANGED /* 101 */:
                    CallManager.this.mPreciseCallStateRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    break;
                case CallManager.EVENT_NEW_RINGING_CONNECTION /* 102 */:
                    if (CallManager.this.getActiveFgCallState().isDialing() || (CallManager.this.hasMoreThanOneRingingCall() && !CallManager.this.isRingingDuplicateCall())) {
                        Connection c = (Connection) ((AsyncResult) msg.obj).result;
                        try {
                            Rlog.d(CallManager.LOG_TAG, "silently drop incoming call: " + c.getCall());
                            c.getCall().hangup();
                        } catch (CallStateException e) {
                            Rlog.w(CallManager.LOG_TAG, "new ringing connection", e);
                            return;
                        }
                    } else {
                        CallManager.this.mNewRingingConnectionRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    }
                    break;
                case CallManager.EVENT_UNKNOWN_CONNECTION /* 103 */:
                    CallManager.this.mUnknownConnectionRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    break;
                case CallManager.EVENT_INCOMING_RING /* 104 */:
                    if (!CallManager.this.hasActiveFgCall()) {
                        CallManager.this.mIncomingRingRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    }
                    break;
                case CallManager.EVENT_RINGBACK_TONE /* 105 */:
                    CallManager.this.mRingbackToneRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    break;
                case CallManager.EVENT_IN_CALL_VOICE_PRIVACY_ON /* 106 */:
                    CallManager.this.mInCallVoicePrivacyOnRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    break;
                case CallManager.EVENT_IN_CALL_VOICE_PRIVACY_OFF /* 107 */:
                    CallManager.this.mInCallVoicePrivacyOffRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    break;
                case CallManager.EVENT_CALL_WAITING /* 108 */:
                    CallManager.this.mCallWaitingRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    break;
                case CallManager.EVENT_DISPLAY_INFO /* 109 */:
                    CallManager.this.mDisplayInfoRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    break;
                case CallManager.EVENT_SIGNAL_INFO /* 110 */:
                    CallManager.this.mSignalInfoRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    break;
                case CallManager.EVENT_CDMA_OTA_STATUS_CHANGE /* 111 */:
                    CallManager.this.mCdmaOtaStatusChangeRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    break;
                case CallManager.EVENT_RESEND_INCALL_MUTE /* 112 */:
                    CallManager.this.mResendIncallMuteRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    break;
                case CallManager.EVENT_MMI_INITIATE /* 113 */:
                    CallManager.this.mMmiInitiateRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    break;
                case CallManager.EVENT_MMI_COMPLETE /* 114 */:
                    CallManager.this.mMmiCompleteRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    break;
                case CallManager.EVENT_ECM_TIMER_RESET /* 115 */:
                    CallManager.this.mEcmTimerResetRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    break;
                case CallManager.EVENT_SUBSCRIPTION_INFO_READY /* 116 */:
                    CallManager.this.mSubscriptionInfoReadyRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    break;
                case CallManager.EVENT_SUPP_SERVICE_FAILED /* 117 */:
                    CallManager.this.mSuppServiceFailedRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    break;
                case CallManager.EVENT_SERVICE_STATE_CHANGED /* 118 */:
                    CallManager.this.mServiceStateChangedRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    break;
                case CallManager.EVENT_POST_DIAL_CHARACTER /* 119 */:
                    for (int i = 0; i < CallManager.this.mPostDialCharacterRegistrants.size(); i++) {
                        Message notifyMsg = ((Registrant) CallManager.this.mPostDialCharacterRegistrants.get(i)).messageForRegistrant();
                        notifyMsg.obj = msg.obj;
                        notifyMsg.arg1 = msg.arg1;
                        notifyMsg.sendToTarget();
                    }
                    break;
                case CallManager.EVENT_SUPP_SERVICE_NOTIFY /* 120 */:
                    CallManager.this.mSuppServiceNotificationRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    break;
                case CallManager.EVENT_CALL_MODIFY /* 121 */:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    if (ar != null && ar.result != null && ar.exception == null) {
                        CallManager.this.mCallModifyRegistrants.notifyRegistrants(new AsyncResult((Object) null, (Connection) ar.result, (Throwable) null));
                    } else {
                        Rlog.e(CallManager.LOG_TAG, "Error EVENT_MODIFY_CALL AsyncResult ar= " + ar);
                    }
                    break;
            }
        }
    }

    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("CallManager {");
        b.append("\nstate = " + getState());
        Call call = getActiveFgCall();
        b.append("\n- Foreground: " + getActiveFgCallState());
        b.append(" from " + call.getPhone());
        b.append("\n  Conn: ").append(getFgCallConnections());
        Call call2 = getFirstActiveBgCall();
        b.append("\n- Background: " + call2.getState());
        b.append(" from " + call2.getPhone());
        b.append("\n  Conn: ").append(getBgCallConnections());
        Call call3 = getFirstActiveRingingCall();
        b.append("\n- Ringing: " + call3.getState());
        b.append(" from " + call3.getPhone());
        for (Phone phone : getAllPhones()) {
            if (phone != null) {
                b.append("\nPhone: " + phone + ", name = " + phone.getPhoneName() + ", state = " + phone.getState());
                Call call4 = phone.getForegroundCall();
                b.append("\n- Foreground: ").append(call4);
                Call call5 = phone.getBackgroundCall();
                b.append(" Background: ").append(call5);
                Call call6 = phone.getRingingCall();
                b.append(" Ringing: ").append(call6);
            }
        }
        b.append("\n}");
        return b.toString();
    }

    public void registerForSubscriptionChange(Handler h, int what, Object obj) {
        Rlog.e(LOG_TAG, "registerForSubscriptionChange for subscription not supported");
    }

    public void unregisterForSubscriptionChange(Handler h) {
        Rlog.e(LOG_TAG, "unregisterForSubscriptionChange for subscription not supported");
    }

    public boolean hasActiveFgCallAnyPhone() {
        return hasActiveFgCall();
    }

    public int getServiceState(int subscription) {
        Rlog.e(LOG_TAG, " getServiceState for subscription not supported");
        return 1;
    }

    public PhoneConstants.State getState(int subscription) {
        Rlog.e(LOG_TAG, " getState for subscription not supported");
        return PhoneConstants.State.IDLE;
    }

    public Phone getFgPhone(int subscription) {
        Rlog.e(LOG_TAG, " getFgPhone for subscription not supported");
        return null;
    }

    public Phone getBgPhone(int subscription) {
        Rlog.e(LOG_TAG, " getBgPhone for subscription not supported");
        return null;
    }

    public Phone getRingingPhone(int subscription) {
        Rlog.e(LOG_TAG, " getRingingPhone for subscription not supported");
        return null;
    }

    public Phone getPhoneInCall(int subscription) {
        Rlog.e(LOG_TAG, " getPhoneInCall for subscription not supported");
        return null;
    }

    public Call getFirstActiveRingingCall(int subscription) {
        return getFirstActiveRingingCall();
    }

    public Call getFirstActiveBgCall(int subscription) {
        return getFirstActiveBgCall();
    }

    public Call getActiveFgCall(int subscription) {
        Rlog.e(LOG_TAG, " getActiveFgCall for subscription not supported");
        return null;
    }

    public Call.State getActiveFgCallState(int subscription) {
        Rlog.e(LOG_TAG, " getActiveFgCallState for subscription not supported");
        return Call.State.IDLE;
    }

    public boolean hasActiveRingingCall(int subscription) {
        Rlog.e(LOG_TAG, " hasActiveRingingCall for subscription not supported");
        return false;
    }

    public boolean hasActiveFgCall(int subscription) {
        Rlog.e(LOG_TAG, " hasActiveFgCall for subscription not supported");
        return false;
    }

    public boolean hasActiveBgCall(int subscription) {
        Rlog.e(LOG_TAG, " hasActiveBgCall for subscription not supported");
        return false;
    }

    public boolean hasDisconnectedFgCall(int subscription) {
        Rlog.e(LOG_TAG, " hasDisconnectedFgCall for subscription not supported");
        return false;
    }

    public boolean hasDisconnectedBgCall(int subscription) {
        Rlog.e(LOG_TAG, " hasDisconnectedBgCall for subscription not supported");
        return false;
    }

    public void clearDisconnected(int subscription) {
        Rlog.e(LOG_TAG, " clearDisconnected for subscription not supported");
    }

    public List<Connection> getFgCallConnections(int subscription) {
        Rlog.e(LOG_TAG, " getFgCallConnections for subscription not supported");
        return null;
    }

    public Connection getFgCallLatestConnection(int subscription) {
        Rlog.e(LOG_TAG, " getFgCallLatestConnection for subscription not supported");
        return null;
    }

    public void setActiveSubscription(int subscription) {
        Rlog.e(LOG_TAG, " setActiveSubscription for subscription not supported");
    }

    public int getActiveSubscription() {
        Rlog.e(LOG_TAG, " getActiveSubscription for subscription not supported");
        return 0;
    }

    public void switchToLocalHold(int subscription, boolean switchTo) {
        Rlog.e(LOG_TAG, " switchToLocalHold for subscription not supported");
    }

    public boolean getLocalCallHoldStatus(int subscription) {
        Rlog.e(LOG_TAG, " getLocalCallHoldStatus for subscription not supported");
        return false;
    }

    public boolean canConference(Call heldCall, int subscription) {
        Rlog.e(LOG_TAG, " canConference for subscription not supported");
        return false;
    }

    public void startDtmf(char c, int subscription) {
        Rlog.e(LOG_TAG, " startDtmf not supported for subscription");
    }

    public void stopDtmf(int subscription) {
        Rlog.e(LOG_TAG, " stopDtmf not supported for subscription");
    }

    public void setSubInConversation(int subscription) {
        Rlog.e(LOG_TAG, " setSubInConversation not supported");
    }

    public int getSubInConversation() {
        Rlog.e(LOG_TAG, " getSubInConversation not supported");
        return 0;
    }
}
