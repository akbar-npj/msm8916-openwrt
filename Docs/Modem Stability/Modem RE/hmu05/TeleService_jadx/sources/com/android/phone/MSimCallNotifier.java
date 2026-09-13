package com.android.phone;

import android.content.SharedPreferences;
import android.os.AsyncResult;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.MSimTelephonyManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.util.EventLog;
import android.util.Log;
import android.widget.Toast;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyCapabilities;
import com.android.internal.telephony.cdma.CdmaInformationRecords;
import com.android.internal.telephony.cdma.SignalToneUtil;

/* JADX INFO: loaded from: classes.dex */
public class MSimCallNotifier extends CallNotifier {
    private static final boolean DBG;
    private static final boolean sLocalCallHoldToneEnabled;
    private boolean[] mIsPermDiscCauseReceived;
    private int mLchSub;
    private CallNotifier.InCallTonePlayer mLocalCallReminderTonePlayer;
    private CallNotifier.InCallTonePlayer mLocalCallWaitingTonePlayer;
    private CallNotifier.InCallTonePlayer mSupervisoryCallHoldTonePlayer;

    static {
        DBG = SystemProperties.getInt("ro.debuggable", 0) == 1;
        sLocalCallHoldToneEnabled = SystemProperties.getBoolean("persist.radio.lch_inband_tone", false);
    }

    static CallNotifier init(MSimPhoneGlobals mSimPhoneGlobals, Phone phone, Ringer ringer, CallLogger callLogger, CallStateMonitor callStateMonitor, BluetoothManager bluetoothManager, CallModeler callModeler) {
        MSimCallNotifier mSimCallNotifier;
        synchronized (MSimCallNotifier.class) {
            if (sInstance == null) {
                sInstance = new MSimCallNotifier(mSimPhoneGlobals, phone, ringer, callLogger, callStateMonitor, bluetoothManager, callModeler);
            } else {
                Log.wtf("MSimCallNotifier", "init() called multiple times!  sInstance = " + sInstance);
            }
            mSimCallNotifier = (MSimCallNotifier) sInstance;
        }
        return mSimCallNotifier;
    }

    protected MSimCallNotifier(MSimPhoneGlobals app, Phone phone, Ringer ringer, CallLogger callLogger, CallStateMonitor callStateMonitor, BluetoothManager bluetoothManager, CallModeler callModeler) {
        super(app, phone, ringer, callLogger, callStateMonitor, bluetoothManager, callModeler);
        this.mLchSub = -1;
        this.mLocalCallReminderTonePlayer = null;
        this.mSupervisoryCallHoldTonePlayer = null;
        this.mLocalCallWaitingTonePlayer = null;
        this.mIsPermDiscCauseReceived = new boolean[MSimTelephonyManager.getDefault().getPhoneCount()];
    }

    @Override // com.android.phone.CallNotifier, android.os.Handler
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 5:
                if (msg.obj != null && ((AsyncResult) msg.obj).result != null) {
                    PhoneBase pb = (PhoneBase) ((AsyncResult) msg.obj).result;
                    if (pb.getState() == PhoneConstants.State.RINGING && !this.mSilentRingerRequested && this.mNewRingingConnectionProcessDone && !this.mCM.hasActiveFgCallAnyPhone()) {
                        if (DBG) {
                            log("RINGING... (PHONE_INCOMING_RING event)");
                        }
                        this.mRinger.ring();
                    } else if (DBG) {
                        log("Skipping generating Ring tone, state = " + pb.getState() + " silence requested = " + this.mSilentRingerRequested);
                    }
                    break;
                }
                break;
            case 15:
                if (DBG) {
                    log("PHONE_ACTIVE_SUBSCRIPTION_CHANGE...");
                }
                AsyncResult r = (AsyncResult) msg.obj;
                log(" Change in subscription " + ((Integer) r.result));
                break;
            case 21:
                Phone phone = (Phone) msg.obj;
                onMwiChanged(this.mApplication.phone.getMessageWaitingIndicator(), phone);
                break;
            case 55:
                if (DBG) {
                    log("PHONE_START_MSIM_INCALL_TONE...");
                }
                startMSimInCallTones();
                break;
            case 56:
                playLchDtmf();
                break;
            case 57:
                stopLchDtmf();
                break;
            default:
                super.handleMessage(msg);
                break;
        }
    }

    @Override // com.android.phone.CallNotifier
    protected void listen() {
        MSimTelephonyManager telephonyManager = (MSimTelephonyManager) this.mApplication.getSystemService("phone_msim");
        for (int i = 0; i < MSimTelephonyManager.getDefault().getPhoneCount(); i++) {
            telephonyManager.listen(getPhoneStateListener(i), 12);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onMwiChanged(boolean visible, Phone phone) {
        if (!PhoneGlobals.sVoiceCapable) {
            Log.w("MSimCallNotifier", "Got onMwiChanged() on non-voice-capable device! Ignoring...");
        } else {
            ((MSimNotificationMgr) this.mApplication.notificationMgr).updateMwi(visible, phone);
        }
    }

    void sendMwiChangedDelayed(long delayMillis, Phone phone) {
        Message message = Message.obtain(this, 21, phone);
        sendMessageDelayed(message, delayMillis);
    }

    protected void onCfiChanged(boolean visible, int subscription) {
        ((MSimNotificationMgr) this.mApplication.notificationMgr).updateCfi(visible, subscription);
    }

    protected void onXDivertChanged(boolean visible) {
        ((MSimNotificationMgr) this.mApplication.notificationMgr).updateXDivert(visible);
    }

    protected boolean getXDivertStatus() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this.mApplication);
        boolean status = sp.getBoolean("xdivert_status_key", false);
        Log.d("MSimCallNotifier", "getXDivertStatus status = " + status);
        return status;
    }

    protected void setXDivertStatus(boolean status) {
        Log.d("MSimCallNotifier", "setXDivertStatus status = " + status);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this.mApplication);
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean("xdivert_status_key", status);
        editor.apply();
    }

    private PhoneStateListener getPhoneStateListener(int sub) {
        Log.d("MSimCallNotifier", "getPhoneStateListener: SUBSCRIPTION == " + sub);
        PhoneStateListener phoneStateListener = new PhoneStateListener(sub) { // from class: com.android.phone.MSimCallNotifier.1
            @Override // android.telephony.PhoneStateListener
            public void onMessageWaitingIndicatorChanged(boolean mwi) {
                MSimCallNotifier.this.onMwiChanged(mwi, PhoneGlobals.getInstance().getPhone(this.mSubscription));
            }

            @Override // android.telephony.PhoneStateListener
            public void onCallForwardingIndicatorChanged(boolean cfi) {
                MSimCallNotifier.this.onCfiChanged(cfi, this.mSubscription);
            }
        };
        return phoneStateListener;
    }

    @Override // com.android.phone.CallNotifier
    protected void onNewRingingConnection(AsyncResult r) {
        Connection c = (Connection) r.result;
        int subscription = c.getCall().getPhone().getSubscription();
        PhoneUtils.setActiveSubscription(subscription);
        log("onNewRingingConnection(): state = " + this.mCM.getState() + ", conn = { " + c + " } subscription = " + subscription);
        Call ringing = c.getCall();
        Phone phone = ringing.getPhone();
        hideUssdResponseDialog();
        if (ignoreAllIncomingCalls(phone) || MSimPhoneGlobals.getInstance().isCsvtActive()) {
            PhoneUtils.hangupRingingCall(ringing);
            return;
        }
        if (!c.isRinging()) {
            Log.i("MSimCallNotifier", "CallNotifier.onNewRingingConnection(): connection not ringing!");
            return;
        }
        stopSignalInfoTone();
        c.getState();
        this.mApplication.requestWakeState(PhoneGlobals.WakeState.PARTIAL);
        startIncomingCallQuery(c);
    }

    private void ringAndNotifyOfIncomingCall(Connection c) {
        if (PhoneUtils.isRealIncomingCall(c.getState())) {
            this.mRinger.ring();
        } else if (this.mCallWaitingTonePlayer == null) {
            this.mCallWaitingTonePlayer = new CallNotifier.InCallTonePlayer(1);
            this.mCallWaitingTonePlayer.start();
        }
        this.mCallModeler.onNewRingingConnection(c);
    }

    @Override // com.android.phone.CallNotifier
    protected void onUnknownConnectionAppeared(AsyncResult r) {
        PhoneBase pb = (PhoneBase) r.result;
        int subscription = pb.getSubscription();
        PhoneConstants.State state = this.mCM.getState(subscription);
        if (state == PhoneConstants.State.OFFHOOK) {
            if (DBG) {
                log("unknown connection appeared...");
            }
            PhoneUtils.setActiveAndConversationSub(subscription);
            onPhoneStateChanged(r);
        }
    }

    @Override // com.android.phone.CallNotifier
    protected void onPhoneStateChanged(AsyncResult r) {
        Connection c;
        PhoneBase pb = (PhoneBase) r.result;
        int subscription = pb.getSubscription();
        PhoneConstants.State state = this.mCM.getState(subscription);
        if (MSimPhoneGlobals.getInstance().isCsvtActive() && state == PhoneConstants.State.OFFHOOK) {
            log("onPhoneStateChanged: CSVT is active");
            return;
        }
        this.mApplication.notificationMgr.statusBarHelper.enableNotificationAlerts(state == PhoneConstants.State.IDLE);
        Phone fgPhone = this.mCM.getFgPhone(subscription);
        vibrateAfterCallConnected(fgPhone);
        if (fgPhone.getForegroundCall().getState() == Call.State.ACTIVE && this.mApplication.getApplicationContext().getResources().getBoolean(R.bool.config_disconnect_other_fgcall)) {
            for (int i = 0; i < MSimTelephonyManager.getDefault().getPhoneCount(); i++) {
                if (i != subscription) {
                    Phone otherFgPhone = this.mCM.getFgPhone(i);
                    if (DBG) {
                        log("otherFgPhoneState: " + otherFgPhone.getForegroundCall().getState());
                    }
                    if (otherFgPhone.getForegroundCall().getState() == Call.State.DIALING || otherFgPhone.getForegroundCall().getState() == Call.State.ALERTING) {
                        PhoneUtils.hangupActiveCall(otherFgPhone.getForegroundCall());
                    }
                }
            }
        }
        if (fgPhone.getPhoneType() == 2) {
            if (fgPhone.getForegroundCall().getState() == Call.State.ACTIVE && (this.mPreviousCdmaCallState == Call.State.DIALING || this.mPreviousCdmaCallState == Call.State.ALERTING)) {
                if (this.mIsCdmaRedialCall) {
                    new CallNotifier.InCallTonePlayer(10).start();
                }
                stopSignalInfoTone();
            }
            this.mPreviousCdmaCallState = fgPhone.getForegroundCall().getState();
        }
        this.mBluetoothManager.updateBluetoothIndication();
        this.mApplication.updatePhoneState(state);
        if (state == PhoneConstants.State.OFFHOOK) {
            if (this.mCallWaitingTonePlayer != null) {
                this.mCallWaitingTonePlayer.stopTone();
                this.mCallWaitingTonePlayer = null;
            }
            manageLocalCallWaitingTone();
            PhoneUtils.setAudioMode(this.mCM);
            if (DBG) {
                log("stopRing()... (OFFHOOK state)");
            }
            this.mRinger.stopRing();
        }
        if (state != PhoneConstants.State.RINGING) {
            this.mNewRingingConnectionProcessDone = false;
        }
        manageMSimInCallTones(false);
        if (fgPhone.getPhoneType() == 2 && (c = fgPhone.getForegroundCall().getLatestConnection()) != null && PhoneNumberUtils.isLocalEmergencyNumber(c.getAddress(), this.mApplication)) {
            Call.State callState = fgPhone.getForegroundCall().getState();
            if (this.mEmergencyTonePlayerVibrator == null) {
                this.mEmergencyTonePlayerVibrator = new CallNotifier.EmergencyTonePlayerVibrator();
            }
            if (callState == Call.State.DIALING || callState == Call.State.ALERTING) {
                this.mIsEmergencyToneOn = Settings.Global.getInt(this.mApplication.getContentResolver(), "emergency_tone", 0);
                if (this.mIsEmergencyToneOn != 0 && this.mCurrentEmergencyToneState == 0 && this.mEmergencyTonePlayerVibrator != null) {
                    this.mEmergencyTonePlayerVibrator.start();
                }
            } else if (callState == Call.State.ACTIVE && this.mCurrentEmergencyToneState != 0 && this.mEmergencyTonePlayerVibrator != null) {
                this.mEmergencyTonePlayerVibrator.stop();
            }
        }
        if (fgPhone.getPhoneType() == 1 || fgPhone.getPhoneType() == 3 || fgPhone.getPhoneType() == 4) {
            Call.State callState2 = this.mCM.getActiveFgCallState(subscription);
            if (!callState2.isDialing() && this.mInCallRingbackTonePlayer != null) {
                this.mInCallRingbackTonePlayer.stopTone();
                this.mInCallRingbackTonePlayer = null;
            }
        }
        if (this.mApplication.getResources().getBoolean(R.bool.config_show_toast_when_dialing)) {
            Call.State callState3 = this.mCM.getActiveFgCallState(subscription);
            if (callState3.isDialing()) {
                Toast toast = Toast.makeText(this.mApplication, R.string.dialing, 0);
                toast.show();
            }
        }
    }

    @Override // com.android.phone.CallNotifier
    protected void onCustomRingQueryComplete(Connection c) {
        Call ringingCall;
        boolean isQueryExecutionTimeExpired = false;
        synchronized (this.mCallerInfoQueryStateGuard) {
            if (this.mCallerInfoQueryState == -1) {
                this.mCallerInfoQueryState = 0;
                isQueryExecutionTimeExpired = true;
            }
        }
        if (isQueryExecutionTimeExpired) {
            Log.w("MSimCallNotifier", "CallerInfo query took too long; falling back to default ringtone");
            EventLog.writeEvent(70304, new Object[0]);
        }
        for (int i = 0; i < MSimTelephonyManager.getDefault().getPhoneCount(); i++) {
            if (this.mCM.getState(i) == PhoneConstants.State.RINGING && (ringingCall = this.mCM.getFirstActiveRingingCall(i)) != null && ringingCall.getLatestConnection() == c) {
                ringAndNotifyOfIncomingCall(c);
                return;
            }
        }
    }

    @Override // com.android.phone.CallNotifier
    protected void onDisconnect(AsyncResult r) {
        Connection.DisconnectCause cause;
        showUssdResponseDialog();
        this.mVoicePrivacyState = false;
        this.mNewRingingConnectionProcessDone = false;
        Connection c = (Connection) r.result;
        int subscription = c.getCall().getPhone().getSubscription();
        if (c != null) {
            log("onDisconnect: cause = " + c.getDisconnectCause() + ", incoming = " + c.isIncoming() + ", date = " + c.getCreateTime() + ", subscription = " + subscription);
        } else {
            Log.w("MSimCallNotifier", "onDisconnect: null connection");
        }
        showCallDurationIfNeed(c);
        int autoretrySetting = 0;
        if (c != null && c.getCall().getPhone().getPhoneType() == 2) {
            autoretrySetting = Settings.Global.getInt(this.mApplication.getContentResolver(), "call_auto_retry", 0);
        }
        stopSignalInfoTone();
        if (c != null && c.getCall().getPhone().getPhoneType() == 2) {
            this.mApplication.cdmaPhoneCallState.resetCdmaPhoneCallState();
            removeMessages(22);
            removeMessages(23);
        }
        Call ringingCall = this.mCM.getFirstActiveRingingCall(subscription);
        if (ringingCall.getPhone().getPhoneType() == 2) {
            if (PhoneUtils.isRealIncomingCall(ringingCall.getState())) {
                if (DBG) {
                    log("cancelCallInProgressNotifications()... (onDisconnect)");
                }
                this.mApplication.notificationMgr.cancelCallInProgressNotifications();
            } else {
                if (DBG) {
                    log("stopRing()... (onDisconnect)");
                }
                this.mRinger.stopRing();
            }
        } else {
            if (DBG) {
                log("stopRing()... (onDisconnect)");
            }
            this.mRinger.stopRing();
        }
        if (this.mCallWaitingTonePlayer != null) {
            this.mCallWaitingTonePlayer.stopTone();
            this.mCallWaitingTonePlayer = null;
        }
        manageLocalCallWaitingTone();
        if (c != null) {
            String number = c.getAddress();
            Phone phone = c.getCall().getPhone();
            Connection.DisconnectCause cause2 = c.getDisconnectCause();
            if (PhoneNumberUtils.isLocalEmergencyNumber(number, this.mApplication) && (cause2 == Connection.DisconnectCause.EMERGENCY_TEMP_FAILURE || cause2 == Connection.DisconnectCause.EMERGENCY_PERM_FAILURE)) {
                int subToCall = phone.getSubscription();
                if (cause2 == Connection.DisconnectCause.EMERGENCY_PERM_FAILURE) {
                    log("EMERGENCY_PERM_FAILURE received on sub:" + phone.getSubscription());
                    this.mIsPermDiscCauseReceived[phone.getSubscription()] = true;
                    subToCall = -1;
                }
                for (int i = PhoneUtils.getNextSubscriptionId(phone.getSubscription()); i != phone.getSubscription(); i = PhoneUtils.getNextSubscriptionId(i)) {
                    if (!this.mIsPermDiscCauseReceived[i]) {
                        subToCall = i;
                        break;
                    }
                }
                if (subToCall == -1) {
                    log("EMERGENCY_PERM_FAILURE recieved on all subs, abort redial");
                } else {
                    log("Redial emergency call on subscription " + subToCall);
                    PhoneUtils.placeCall(this.mApplication, this.mApplication.getPhone(subToCall), number, null, false);
                    return;
                }
            }
        }
        if (c != null && TelephonyCapabilities.supportsOtasp(c.getCall().getPhone())) {
            if (c.getCall().getPhone().isOtaSpNumber(c.getAddress())) {
                if (DBG) {
                    log("onDisconnect: this was an OTASP call!");
                }
                this.mApplication.handleOtaspDisconnect();
            }
        }
        int toneToPlay = 0;
        if (c != null) {
            Connection.DisconnectCause cause3 = c.getDisconnectCause();
            if (cause3 == Connection.DisconnectCause.BUSY) {
                if (DBG) {
                    log("- need to play BUSY tone!");
                }
                toneToPlay = 2;
            } else if (cause3 == Connection.DisconnectCause.CONGESTION) {
                if (DBG) {
                    log("- need to play CONGESTION tone!");
                }
                toneToPlay = 3;
            } else if ((cause3 == Connection.DisconnectCause.NORMAL || cause3 == Connection.DisconnectCause.LOCAL) && this.mApplication.isOtaCallInActiveState()) {
                if (DBG) {
                    log("- need to play OTA_CALL_END tone!");
                }
                toneToPlay = 11;
            } else if (cause3 == Connection.DisconnectCause.CDMA_REORDER) {
                if (DBG) {
                    log("- need to play CDMA_REORDER tone!");
                }
                toneToPlay = 6;
            } else if (cause3 == Connection.DisconnectCause.CDMA_INTERCEPT) {
                if (DBG) {
                    log("- need to play CDMA_INTERCEPT tone!");
                }
                toneToPlay = 7;
            } else if (cause3 == Connection.DisconnectCause.CDMA_DROP) {
                if (DBG) {
                    log("- need to play CDMA_DROP tone!");
                }
                toneToPlay = 8;
            } else if (cause3 == Connection.DisconnectCause.OUT_OF_SERVICE) {
                if (DBG) {
                    log("- need to play OUT OF SERVICE tone!");
                }
                toneToPlay = 9;
            } else if (cause3 == Connection.DisconnectCause.UNOBTAINABLE_NUMBER) {
                if (DBG) {
                    log("- need to play TONE_UNOBTAINABLE_NUMBER tone!");
                }
                toneToPlay = 13;
            } else if (cause3 == Connection.DisconnectCause.ERROR_UNSPECIFIED) {
                if (DBG) {
                    log("- DisconnectCause is ERROR_UNSPECIFIED: play TONE_CALL_ENDED!");
                }
                toneToPlay = 4;
            }
        }
        if (toneToPlay == 0 && this.mCM.getState(subscription) == PhoneConstants.State.IDLE && c != null && ((cause = c.getDisconnectCause()) == Connection.DisconnectCause.NORMAL || cause == Connection.DisconnectCause.LOCAL)) {
            toneToPlay = 4;
            this.mIsCdmaRedialCall = false;
        }
        if (this.mCM.getState(subscription) == PhoneConstants.State.IDLE) {
            if (toneToPlay == 0) {
                resetAudioStateAfterDisconnect();
            }
            this.mApplication.notificationMgr.cancelCallInProgressNotifications();
        }
        if (c != null) {
            this.mCallLogger.logCall(c);
            String number2 = c.getAddress();
            Phone phone2 = c.getCall().getPhone();
            boolean isEmergencyNumber = PhoneNumberUtils.isLocalEmergencyNumber(number2, this.mApplication);
            if (phone2.getPhoneType() == 2 && isEmergencyNumber && this.mCurrentEmergencyToneState != 0 && this.mEmergencyTonePlayerVibrator != null) {
                this.mEmergencyTonePlayerVibrator.stop();
            }
            long date = c.getCreateTime();
            Connection.DisconnectCause cause4 = c.getDisconnectCause();
            boolean missedCall = c.isIncoming() && (cause4 == Connection.DisconnectCause.INCOMING_MISSED || (cause4 == Connection.DisconnectCause.INCOMING_REJECTED && this.mApplication.getResources().getBoolean(R.bool.reject_call_as_missed_call)));
            if (missedCall) {
                showMissedCallNotification(c, date);
            }
            if (toneToPlay != 0) {
                new CallNotifier.InCallTonePlayer(toneToPlay).start();
            }
            if ((this.mPreviousCdmaCallState == Call.State.DIALING || this.mPreviousCdmaCallState == Call.State.ALERTING) && !isEmergencyNumber && cause4 != Connection.DisconnectCause.INCOMING_MISSED && cause4 != Connection.DisconnectCause.NORMAL && cause4 != Connection.DisconnectCause.LOCAL && cause4 != Connection.DisconnectCause.INCOMING_REJECTED) {
                if (!this.mIsCdmaRedialCall && autoretrySetting == 1) {
                    int status = PhoneUtils.placeCall(this.mApplication, phone2, number2, null, false);
                    if (status != 2) {
                        this.mIsCdmaRedialCall = true;
                    }
                } else {
                    this.mIsCdmaRedialCall = false;
                }
            }
            int activeSub = PhoneUtils.getActiveSubscription();
            int ConversationSub = this.mCM.getSubInConversation();
            if (PhoneUtils.getOtherActiveSub(activeSub) == -1 && this.mCM.getState(activeSub) == PhoneConstants.State.IDLE) {
                log("No calls active on both subs");
                PhoneUtils.setSubInConversation(-1);
                return;
            }
            if (subscription == ConversationSub && this.mCM.getState(subscription) == PhoneConstants.State.IDLE) {
                log("No calls active in conversation sub, only update conversation sub");
                this.mCM.setSubInConversation(-1);
            } else if (subscription == activeSub && ConversationSub != -1 && this.mCM.getState(activeSub) == PhoneConstants.State.OFFHOOK) {
                log("Set active sub to conversation sub " + ConversationSub);
                PhoneUtils.setActiveSubscription(ConversationSub);
            }
        }
    }

    void onEmergencyCallDialed() {
        for (int i = 0; i < this.mIsPermDiscCauseReceived.length; i++) {
            this.mIsPermDiscCauseReceived[i] = false;
        }
    }

    @Override // com.android.phone.CallNotifier
    protected void resetAudioStateAfterDisconnect() {
        if (PhoneUtils.isAnySubActive()) {
            if (DBG) {
                log("there is a sub which has active call, Do not reset audio ");
                return;
            }
            return;
        }
        super.resetAudioStateAfterDisconnect();
    }

    @Override // com.android.phone.CallNotifier
    protected void onCdmaCallWaiting(AsyncResult r) {
        int subscription = this.mPhone.getSubscription();
        log("Call Waiting sub = " + subscription);
        PhoneUtils.setActiveSubscription(subscription);
        super.onCdmaCallWaiting(r);
    }

    @Override // com.android.phone.CallNotifier
    protected void onSignalInfo(AsyncResult r) {
        if (!PhoneGlobals.sVoiceCapable) {
            Log.w("MSimCallNotifier", "Got onSignalInfo() on non-voice-capable device! Ignoring...");
            return;
        }
        if (PhoneUtils.isRealIncomingCall(this.mCM.getFirstActiveRingingCall(0).getState())) {
            stopSignalInfoTone();
            return;
        }
        CdmaInformationRecords.CdmaSignalInfoRec signalInfoRec = (CdmaInformationRecords.CdmaSignalInfoRec) r.result;
        if (signalInfoRec != null) {
            boolean isPresent = signalInfoRec.isPresent;
            if (DBG) {
                log("onSignalInfo: isPresent=" + isPresent);
            }
            if (isPresent) {
                int uSignalType = signalInfoRec.signalType;
                int uAlertPitch = signalInfoRec.alertPitch;
                int uSignal = signalInfoRec.signal;
                if (DBG) {
                    log("onSignalInfo: uSignalType=" + uSignalType + ", uAlertPitch=" + uAlertPitch + ", uSignal=" + uSignal);
                }
                int toneID = SignalToneUtil.getAudioToneFromSignalInfo(uSignalType, uAlertPitch, uSignal);
                new CallNotifier.SignalInfoTonePlayer(toneID).start();
            }
        }
    }

    void manageMSimInCallTones(boolean z) {
        int activeSubscription = PhoneUtils.getActiveSubscription();
        int otherActiveSub = PhoneUtils.getOtherActiveSub(activeSubscription);
        if (otherActiveSub != -1 && this.mCM.getState(activeSubscription) != PhoneConstants.State.IDLE) {
            if (this.mCM.getState(activeSubscription) != PhoneConstants.State.RINGING && this.mCM.getState(otherActiveSub) != PhoneConstants.State.RINGING) {
                if (z) {
                    log(" manageMSimInCallTones: re-start playing tones, active sub = " + activeSubscription + " other sub = " + otherActiveSub);
                    reStartMSimInCallTones();
                    return;
                } else {
                    startMSimInCallTones();
                    return;
                }
            }
            return;
        }
        if (!this.mCM.getLocalCallHoldStatus(activeSubscription)) {
            stopMSimInCallTones();
        }
    }

    public void reStartMSimInCallTones() {
        stopMSimInCallTones();
        removeMessages(55);
        Message message = Message.obtain(this, 55);
        sendMessageDelayed(message, 100L);
    }

    private void playLchDtmf() {
        char cCharAt;
        if (this.mLchSub == -1 && !hasMessages(56)) {
            int activeSubscription = PhoneUtils.getActiveSubscription();
            int otherActiveSub = PhoneUtils.getOtherActiveSub(activeSubscription);
            log(" playLchDtmf... activesub " + activeSubscription + " otherSub " + otherActiveSub);
            if (this.mCM.getLocalCallHoldStatus(activeSubscription)) {
                this.mLchSub = activeSubscription;
            } else if (this.mCM.getLocalCallHoldStatus(otherActiveSub)) {
                this.mLchSub = otherActiveSub;
            } else {
                log(" There is no sub on lch, returning... ");
                return;
            }
            removeAnyPendingDtmfMsgs();
            if (this.mCM.getPhoneInCall(this.mLchSub).getPhoneType() == 2) {
                cCharAt = '#';
            } else {
                cCharAt = this.mApplication.getApplicationContext().getResources().getString(R.string.Lch_dtmf_key).charAt(0);
            }
            this.mCM.startDtmf(cCharAt, this.mLchSub);
            sendMessageDelayed(Message.obtain(this, 56), 3000L);
            sendMessageDelayed(Message.obtain(this, 57), 500L);
        }
    }

    private void stopLchDtmf() {
        if (this.mLchSub != -1) {
            this.mCM.stopDtmf(this.mLchSub);
        }
        this.mLchSub = -1;
    }

    private void startMSimInCallTones() {
        if (this.mLocalCallReminderTonePlayer == null) {
            if (DBG) {
                log(" Play local call hold reminder tone ");
            }
            this.mLocalCallReminderTonePlayer = new CallNotifier.InCallTonePlayer(15);
            this.mLocalCallReminderTonePlayer.start();
        }
        if (sLocalCallHoldToneEnabled) {
            if (this.mSupervisoryCallHoldTonePlayer == null) {
                log(" startMSimInCallTones: Supervisory call hold tone ");
                this.mSupervisoryCallHoldTonePlayer = new CallNotifier.InCallTonePlayer(16);
                this.mSupervisoryCallHoldTonePlayer.start();
                return;
            }
            return;
        }
        log(" startMSimInCallTones: Supervisory call hold tone over dtmf ");
        playLchDtmf();
    }

    private void removeAnyPendingDtmfMsgs() {
        removeMessages(56);
        removeMessages(57);
    }

    protected void stopMSimInCallTones() {
        if (this.mLocalCallReminderTonePlayer != null) {
            if (DBG) {
                log(" stopMSimInCallTones: local call hold reminder tone ");
            }
            this.mLocalCallReminderTonePlayer.stopTone();
            this.mLocalCallReminderTonePlayer = null;
        }
        if (this.mSupervisoryCallHoldTonePlayer != null) {
            log(" stopMSimInCallTones: Supervisory call hold tone ");
            this.mSupervisoryCallHoldTonePlayer.stopTone();
            this.mSupervisoryCallHoldTonePlayer = null;
        }
        if (!sLocalCallHoldToneEnabled) {
            log(" stopMSimInCallTones: stop SCH Dtmf call hold tone ");
            stopLchDtmf();
            removeAnyPendingDtmfMsgs();
        }
    }

    void manageLocalCallWaitingTone() {
        int activeSubscription = PhoneUtils.getActiveSubscription();
        int otherActiveSub = PhoneUtils.getOtherActiveSub(activeSubscription);
        if (otherActiveSub != -1 && this.mCM.hasActiveFgCallAnyPhone() && (this.mCM.getState(activeSubscription) == PhoneConstants.State.RINGING || this.mCM.getState(otherActiveSub) == PhoneConstants.State.RINGING)) {
            log(" manageLocalCallWaitingTone : start tone play");
            startLocalCallWaitingTone();
        } else {
            stopLocalCallWaitingTone();
        }
    }

    private void startLocalCallWaitingTone() {
        if (DBG) {
            log("startLocalCallWaitingTone: Local call waiting tone ");
        }
        if (this.mLocalCallWaitingTonePlayer == null) {
            this.mLocalCallWaitingTonePlayer = new CallNotifier.InCallTonePlayer(14);
            this.mLocalCallWaitingTonePlayer.start();
        }
    }

    private void stopLocalCallWaitingTone() {
        if (this.mLocalCallWaitingTonePlayer != null) {
            log(" Stop playing LCW tone ");
            this.mLocalCallWaitingTonePlayer.stopTone();
            this.mLocalCallWaitingTonePlayer = null;
        }
    }

    private void log(String msg) {
        Log.d("MSimCallNotifier", msg);
    }
}
