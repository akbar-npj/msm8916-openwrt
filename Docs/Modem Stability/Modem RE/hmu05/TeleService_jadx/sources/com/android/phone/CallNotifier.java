package com.android.phone;

import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.ContentResolver;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.SystemVibrator;
import android.os.Vibrator;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.EventLog;
import android.util.Log;
import android.widget.Toast;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.CallerInfoAsyncQuery;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyCapabilities;
import com.android.internal.telephony.cdma.CdmaCallWaitingNotification;
import com.android.internal.telephony.cdma.CdmaInformationRecords;
import com.android.internal.telephony.cdma.SignalToneUtil;
import com.android.internal.telephony.gsm.SuppServiceNotification;

/* JADX INFO: loaded from: classes.dex */
public class CallNotifier extends Handler implements CallerInfoAsyncQuery.OnQueryCompleteListener {
    private static final boolean DBG;
    private static final Uri FIREWALL_PROVIDER_URI;
    protected static CallNotifier sInstance;
    protected PhoneGlobals mApplication;
    private AudioManager mAudioManager;
    private BluetoothHeadset mBluetoothHeadset;
    protected final BluetoothManager mBluetoothManager;
    protected CallManager mCM;
    protected CallLogger mCallLogger;
    protected CallModeler mCallModeler;
    protected InCallTonePlayer mCallWaitingTonePlayer;
    protected int mCallerInfoQueryState;
    protected EmergencyTonePlayerVibrator mEmergencyTonePlayerVibrator;
    protected InCallTonePlayer mInCallRingbackTonePlayer;
    protected int mIsEmergencyToneOn;
    protected Phone mPhone;
    protected Call.State mPreviousCdmaCallState;
    protected Ringer mRinger;
    private ToneGenerator mSignalInfoToneGenerator;
    protected boolean mSilentRingerRequested;
    private boolean mCallWaitingTimeOut = false;
    protected Object mCallerInfoQueryStateGuard = new Object();
    protected boolean mNewRingingConnectionProcessDone = false;
    private boolean isForbidden = false;
    protected boolean mVoicePrivacyState = false;
    protected boolean mIsCdmaRedialCall = false;
    protected int mCurrentEmergencyToneState = 0;
    protected Call.State mLastCallState = Call.State.IDLE;
    PhoneStateListener mPhoneStateListener = new PhoneStateListener() { // from class: com.android.phone.CallNotifier.1
        @Override // android.telephony.PhoneStateListener
        public void onMessageWaitingIndicatorChanged(boolean mwi) {
            CallNotifier.this.onMwiChanged(mwi);
        }

        @Override // android.telephony.PhoneStateListener
        public void onCallForwardingIndicatorChanged(boolean cfi) {
            CallNotifier.this.onCfiChanged(cfi);
        }
    };
    private BluetoothProfile.ServiceListener mBluetoothProfileServiceListener = new BluetoothProfile.ServiceListener() { // from class: com.android.phone.CallNotifier.2
        @Override // android.bluetooth.BluetoothProfile.ServiceListener
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            CallNotifier.this.mBluetoothHeadset = (BluetoothHeadset) proxy;
            CallNotifier.this.log("- Got BluetoothHeadset: " + CallNotifier.this.mBluetoothHeadset);
        }

        @Override // android.bluetooth.BluetoothProfile.ServiceListener
        public void onServiceDisconnected(int profile) {
            CallNotifier.this.mBluetoothHeadset = null;
        }
    };

    static {
        DBG = SystemProperties.getInt("ro.debuggable", 0) == 1;
        FIREWALL_PROVIDER_URI = Uri.parse("content://com.android.firewall");
    }

    static CallNotifier init(PhoneGlobals phoneGlobals, Phone phone, Ringer ringer, CallLogger callLogger, CallStateMonitor callStateMonitor, BluetoothManager bluetoothManager, CallModeler callModeler) {
        CallNotifier callNotifier;
        synchronized (CallNotifier.class) {
            if (sInstance == null) {
                sInstance = new CallNotifier(phoneGlobals, phone, ringer, callLogger, callStateMonitor, bluetoothManager, callModeler);
            } else {
                Log.wtf("CallNotifier", "init() called multiple times!  sInstance = " + sInstance);
            }
            callNotifier = sInstance;
        }
        return callNotifier;
    }

    protected CallNotifier(PhoneGlobals app, Phone phone, Ringer ringer, CallLogger callLogger, CallStateMonitor callStateMonitor, BluetoothManager bluetoothManager, CallModeler callModeler) {
        this.mApplication = app;
        this.mPhone = phone;
        this.mCM = app.mCM;
        this.mCallLogger = callLogger;
        this.mBluetoothManager = bluetoothManager;
        this.mCallModeler = callModeler;
        this.mAudioManager = (AudioManager) this.mApplication.getSystemService("audio");
        callStateMonitor.addListener(this);
        createSignalInfoToneGenerator();
        this.mRinger = ringer;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            adapter.getProfileProxy(this.mApplication.getApplicationContext(), this.mBluetoothProfileServiceListener, 1);
        }
        listen();
    }

    protected void listen() {
        TelephonyManager telephonyManager = (TelephonyManager) this.mApplication.getSystemService("phone");
        telephonyManager.listen(this.mPhoneStateListener, 12);
    }

    private void createSignalInfoToneGenerator() {
        if (this.mSignalInfoToneGenerator == null) {
            try {
                this.mSignalInfoToneGenerator = new ToneGenerator(0, 80);
                Log.d("CallNotifier", "CallNotifier: mSignalInfoToneGenerator created when toneplay");
                return;
            } catch (RuntimeException e) {
                Log.w("CallNotifier", "CallNotifier: Exception caught while creating mSignalInfoToneGenerator: " + e);
                this.mSignalInfoToneGenerator = null;
                return;
            }
        }
        Log.d("CallNotifier", "mSignalInfoToneGenerator created already, hence skipping");
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 1:
                onPhoneStateChanged((AsyncResult) msg.obj);
                break;
            case 2:
                log("RINGING... (new)");
                this.isForbidden = false;
                ContentResolver cr = this.mApplication.getContentResolver();
                if (cr.acquireProvider(FIREWALL_PROVIDER_URI) != null) {
                    AsyncResult r = (AsyncResult) msg.obj;
                    Connection c = (Connection) r.result;
                    if (c != null && c.isRinging() && c.getCall() != null) {
                        Phone phone = c.getCall().getPhone();
                        String number = c.getAddress();
                        int subscription = phone.getSubscription();
                        Bundle extras = new Bundle();
                        extras.putInt("subscription", subscription);
                        extras.putString("phonenumber", number);
                        Bundle extras2 = cr.call(FIREWALL_PROVIDER_URI, "isForbidden", (String) null, extras);
                        if (extras2 != null) {
                            this.isForbidden = extras2.getBoolean("isForbidden");
                            if (this.isForbidden) {
                                PhoneUtils.hangupRingingCall(c.getCall());
                            }
                        }
                    }
                }
                onNewRingingConnection((AsyncResult) msg.obj);
                this.mSilentRingerRequested = false;
                this.mNewRingingConnectionProcessDone = true;
                break;
            case 3:
                if (DBG) {
                    log("DISCONNECT");
                }
                onDisconnect((AsyncResult) msg.obj);
                break;
            case 4:
                onUnknownConnectionAppeared((AsyncResult) msg.obj);
                break;
            case 5:
                if (msg.obj != null && ((AsyncResult) msg.obj).result != null) {
                    PhoneBase pb = (PhoneBase) ((AsyncResult) msg.obj).result;
                    if (pb.getState() == PhoneConstants.State.RINGING && this.mNewRingingConnectionProcessDone && !this.mSilentRingerRequested) {
                        if (DBG) {
                            log("RINGING... (PHONE_INCOMING_RING event)");
                        }
                        this.mRinger.ring();
                    } else if (DBG) {
                        log("RING before NEW_RING, skipping");
                    }
                    break;
                }
                break;
            case 6:
                if (DBG) {
                    log("Received PHONE_STATE_DISPLAYINFO event");
                }
                onDisplayInfo((AsyncResult) msg.obj);
                break;
            case 7:
                if (DBG) {
                    log("Received PHONE_STATE_SIGNALINFO event");
                }
                onSignalInfo((AsyncResult) msg.obj);
                break;
            case 8:
                if (DBG) {
                    log("Received PHONE_CDMA_CALL_WAITING event");
                }
                onCdmaCallWaiting((AsyncResult) msg.obj);
                break;
            case 9:
                if (DBG) {
                    log("PHONE_ENHANCED_VP_ON...");
                }
                if (!this.mVoicePrivacyState) {
                    new InCallTonePlayer(5).start();
                    this.mVoicePrivacyState = true;
                }
                break;
            case 10:
                if (DBG) {
                    log("PHONE_ENHANCED_VP_OFF...");
                }
                if (this.mVoicePrivacyState) {
                    new InCallTonePlayer(5).start();
                    this.mVoicePrivacyState = false;
                }
                break;
            case 11:
                onRingbackTone((AsyncResult) msg.obj);
                break;
            case 12:
                onResendMute();
                break;
            case 14:
                if (DBG) {
                    log("Received Supplementary Notification");
                }
                if (msg.obj != null && ((AsyncResult) msg.obj).result != null) {
                    SuppServiceNotification suppSvcNotification = (SuppServiceNotification) ((AsyncResult) msg.obj).result;
                    String callForwardText = getSuppSvcNotificationText(suppSvcNotification);
                    if (callForwardText != null && !callForwardText.isEmpty()) {
                        Toast.makeText(this.mApplication, callForwardText, 1).show();
                        break;
                    }
                }
                break;
            case 16:
                onUnsolCallModify((AsyncResult) msg.obj);
                break;
            case 20:
                if (DBG) {
                    log("EVENT_OTA_PROVISION_CHANGE...");
                }
                this.mApplication.handleOtaspEvent(msg);
                break;
            case 21:
                onMwiChanged(this.mApplication.phone.getMessageWaitingIndicator());
                break;
            case 22:
                Log.i("CallNotifier", "Received CALLWAITING_CALLERINFO_DISPLAY_DONE event");
                this.mCallWaitingTimeOut = true;
                onCdmaCallWaitingReject();
                break;
            case 23:
                if (DBG) {
                    log("Received CALLWAITING_ADDCALL_DISABLE_TIMEOUT event ...");
                }
                this.mApplication.cdmaPhoneCallState.setAddCallMenuStateAfterCallWaiting(true);
                break;
            case 24:
                if (DBG) {
                    log("Received Display Info notification done event ...");
                }
                CdmaDisplayInfo.dismissDisplayInfoRecord();
                break;
            case 26:
                Log.i("CallNotifier", "Received CDMA_CALL_WAITING_REJECT event");
                onCdmaCallWaitingReject();
                break;
            case 100:
                onCustomRingtoneQueryTimeout((Connection) msg.obj);
                break;
        }
    }

    protected void onNewRingingConnection(AsyncResult r) {
        Connection c = (Connection) r.result;
        log("onNewRingingConnection(): state = " + this.mCM.getState() + ", conn = { " + c + " }");
        Call ringing = c.getCall();
        Phone phone = ringing.getPhone();
        hideUssdResponseDialog();
        if (ignoreAllIncomingCalls(phone)) {
            PhoneUtils.hangupRingingCall(ringing);
            return;
        }
        if (!c.isRinging()) {
            Log.i("CallNotifier", "CallNotifier.onNewRingingConnection(): connection not ringing!");
            return;
        }
        stopSignalInfoTone();
        Call.State state = c.getState();
        log("- connection is ringing!  state = " + state);
        log("Holding wake lock on new incoming connection.");
        this.mApplication.requestWakeState(PhoneGlobals.WakeState.PARTIAL);
        startIncomingCallQuery(c);
        log("- onNewRingingConnection() done.");
    }

    protected void hideUssdResponseDialog() {
        Dialog ussdRespDialog = this.mApplication.getUSSDResponseDialog();
        if (this.mCM.getState() == PhoneConstants.State.RINGING && ussdRespDialog != null && ussdRespDialog.isShowing()) {
            log("hide ussd dialog...");
            ussdRespDialog.hide();
        }
    }

    protected boolean ignoreAllIncomingCalls(Phone phone) {
        if (!PhoneGlobals.sVoiceCapable) {
            Log.w("CallNotifier", "Got onNewRingingConnection() on non-voice-capable device! Ignoring...");
            return true;
        }
        if (PhoneUtils.isPhoneInEcm(phone)) {
            if (DBG) {
                log("Incoming call while in ECM: always allow...");
            }
            return false;
        }
        boolean provisioned = Settings.Global.getInt(this.mApplication.getContentResolver(), "device_provisioned", 0) != 0;
        if (!provisioned) {
            Log.i("CallNotifier", "Ignoring incoming call: not provisioned");
            return true;
        }
        if (TelephonyCapabilities.supportsOtasp(phone)) {
            boolean activateState = this.mApplication.cdmaOtaScreenState.otaScreenState == OtaUtils.CdmaOtaScreenState.OtaScreenState.OTA_STATUS_ACTIVATION;
            boolean dialogState = this.mApplication.cdmaOtaScreenState.otaScreenState == OtaUtils.CdmaOtaScreenState.OtaScreenState.OTA_STATUS_SUCCESS_FAILURE_DLG;
            boolean spcState = this.mApplication.cdmaOtaProvisionData.inOtaSpcState;
            if (spcState) {
                Log.i("CallNotifier", "Ignoring incoming call: OTA call is active");
                return true;
            }
            if (activateState || dialogState) {
                if (dialogState) {
                    this.mApplication.dismissOtaDialogs();
                }
                this.mApplication.clearOtaState();
                return false;
            }
        }
        return false;
    }

    protected void startIncomingCallQuery(Connection c) {
        boolean shouldStartQuery = false;
        synchronized (this.mCallerInfoQueryStateGuard) {
            if (this.mCallerInfoQueryState == 0) {
                this.mCallerInfoQueryState = -1;
                shouldStartQuery = true;
            }
        }
        if (shouldStartQuery) {
            int subscription = 0;
            if (c != null && c.getCall() != null) {
                Phone phone = c.getCall().getPhone();
                subscription = phone.getSubscription();
            }
            this.mRinger.setCustomRingtoneUri(subscription == 0 ? Settings.System.DEFAULT_RINGTONE_URI : Settings.System.DEFAULT_RINGTONE_URI_2);
            PhoneUtils.CallerInfoToken cit = PhoneUtils.startGetCallerInfo(this.mApplication, c, this, c);
            if (cit.isFinal) {
                log("- CallerInfo already up to date, using available data");
                onQueryComplete(0, c, cit.currentInfo);
                return;
            } else {
                log("- Starting query, posting timeout message.");
                sendMessageDelayed(Message.obtain(this, 100, c), 500L);
                return;
            }
        }
        EventLog.writeEvent(70305, new Object[0]);
        ringAndNotifyOfIncomingCall(c);
    }

    protected void onCustomRingQueryComplete(Connection c) {
        boolean isQueryExecutionTimeExpired = false;
        synchronized (this.mCallerInfoQueryStateGuard) {
            if (this.mCallerInfoQueryState == -1) {
                this.mCallerInfoQueryState = 0;
                isQueryExecutionTimeExpired = true;
            }
        }
        if (isQueryExecutionTimeExpired) {
            Log.w("CallNotifier", "CallerInfo query took too long; falling back to default ringtone");
            EventLog.writeEvent(70304, new Object[0]);
        }
        if (this.mCM.getState() != PhoneConstants.State.RINGING) {
            Log.i("CallNotifier", "onCustomRingQueryComplete: No incoming call! Bailing out...");
            return;
        }
        Call ringingCall = this.mCM.getFirstActiveRingingCall();
        if (ringingCall != null && ringingCall.getLatestConnection() == c) {
            ringAndNotifyOfIncomingCall(c);
        }
    }

    protected void onUnknownConnectionAppeared(AsyncResult r) {
        PhoneConstants.State state = this.mCM.getState();
        if (state == PhoneConstants.State.OFFHOOK) {
            if (DBG) {
                log("unknown connection appeared...");
            }
            onPhoneStateChanged(r);
        }
    }

    private void ringAndNotifyOfIncomingCall(Connection c) {
        if (PhoneUtils.isRealIncomingCall(c.getState())) {
            this.mRinger.ring();
        } else {
            log("- starting call waiting tone...");
            if (this.mCallWaitingTonePlayer == null) {
                this.mCallWaitingTonePlayer = new InCallTonePlayer(1);
                this.mCallWaitingTonePlayer.start();
            }
        }
        this.mCallModeler.onNewRingingConnection(c);
    }

    protected void onPhoneStateChanged(AsyncResult r) {
        Connection c;
        PhoneConstants.State state = this.mCM.getState();
        if (PhoneGlobals.getInstance().isCsvtActive() && state == PhoneConstants.State.OFFHOOK) {
            log("onPhoneStateChanged: CSVT is active");
            return;
        }
        log("onPhoneStateChanged: state = " + state);
        this.mApplication.notificationMgr.statusBarHelper.enableNotificationAlerts(state == PhoneConstants.State.IDLE);
        Phone fgPhone = this.mCM.getFgPhone();
        vibrateAfterCallConnected(fgPhone);
        if (fgPhone.getPhoneType() == 2) {
            if (fgPhone.getForegroundCall().getState() == Call.State.ACTIVE && (this.mPreviousCdmaCallState == Call.State.DIALING || this.mPreviousCdmaCallState == Call.State.ALERTING)) {
                if (this.mIsCdmaRedialCall) {
                    new InCallTonePlayer(10).start();
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
            log("onPhoneStateChanged: OFF HOOK");
            PhoneUtils.setAudioMode(this.mCM);
            if (DBG) {
                log("stopRing()... (OFFHOOK state)");
            }
            this.mRinger.stopRing();
        }
        if (state != PhoneConstants.State.RINGING) {
            this.mNewRingingConnectionProcessDone = false;
        }
        if (fgPhone.getPhoneType() == 2 && (c = fgPhone.getForegroundCall().getLatestConnection()) != null && PhoneNumberUtils.isLocalEmergencyNumber(c.getAddress(), this.mApplication)) {
            log("onPhoneStateChanged: it is an emergency call.");
            Call.State callState = fgPhone.getForegroundCall().getState();
            if (this.mEmergencyTonePlayerVibrator == null) {
                this.mEmergencyTonePlayerVibrator = new EmergencyTonePlayerVibrator();
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
            Call.State callState2 = this.mCM.getActiveFgCallState();
            if (!callState2.isDialing() && this.mInCallRingbackTonePlayer != null) {
                this.mInCallRingbackTonePlayer.stopTone();
                this.mInCallRingbackTonePlayer = null;
            }
        }
        if (this.mApplication.getResources().getBoolean(R.bool.config_show_toast_when_dialing)) {
            Call.State callState3 = this.mCM.getActiveFgCallState();
            if (callState3.isDialing()) {
                Toast toast = Toast.makeText(this.mApplication, R.string.dialing, 0);
                toast.show();
            }
        }
    }

    protected void vibrateAfterCallConnected(Phone phone) {
        if (phone.getForegroundCall().getState() == Call.State.ACTIVE && ((this.mLastCallState == Call.State.DIALING || this.mLastCallState == Call.State.ALERTING) && Settings.System.getInt(this.mApplication.getContentResolver(), "vibrate_on_accepted", 1) == 1)) {
            SystemVibrator systemVibrator = new SystemVibrator();
            systemVibrator.vibrate(100);
            SystemClock.sleep(100);
            systemVibrator.cancel();
        }
        this.mLastCallState = phone.getForegroundCall().getState();
    }

    void updateCallNotifierRegistrationsAfterRadioTechnologyChange() {
        if (DBG) {
            Log.d("CallNotifier", "updateCallNotifierRegistrationsAfterRadioTechnologyChange...");
        }
        this.mInCallRingbackTonePlayer = null;
        this.mCallWaitingTonePlayer = null;
        createSignalInfoToneGenerator();
    }

    public void onQueryComplete(int i, Object obj, CallerInfo callerInfo) {
        boolean z = false;
        if (obj instanceof Long) {
            log("CallerInfo query complete, posting missed call notification");
            this.mApplication.notificationMgr.notifyMissedCall(callerInfo.name, callerInfo.phoneNumber, callerInfo.numberPresentation, callerInfo.phoneLabel, callerInfo.cachedPhoto, callerInfo.cachedPhotoIcon, ((Long) obj).longValue());
            return;
        }
        if (obj instanceof Connection) {
            Connection connection = (Connection) obj;
            log("CallerInfo query complete (for CallNotifier), updating state for incoming call..");
            removeMessages(100);
            synchronized (this.mCallerInfoQueryStateGuard) {
                if (this.mCallerInfoQueryState == -1) {
                    this.mCallerInfoQueryState = 0;
                    z = true;
                }
            }
            if (z) {
                if (callerInfo.shouldSendToVoicemail) {
                    if (DBG) {
                        log("send to voicemail flag detected. hanging up.");
                    }
                    Call firstActiveRingingCall = this.mCM.getFirstActiveRingingCall(connection.getCall().getPhone().getSubscription());
                    if (firstActiveRingingCall != null && firstActiveRingingCall.getLatestConnection() == connection) {
                        PhoneUtils.hangupRingingCall(firstActiveRingingCall);
                        return;
                    }
                }
                if (callerInfo.contactRingtoneUri != null) {
                    if (DBG) {
                        log("custom ringtone found, setting up ringer.");
                    }
                    this.mRinger.setCustomRingtoneUri(callerInfo.contactRingtoneUri);
                }
                onCustomRingQueryComplete(connection);
            }
        }
    }

    private void onCustomRingtoneQueryTimeout(Connection connection) {
        Log.w("CallNotifier", "CallerInfo query took too long; look up local fallback cache.");
        if (connection != null) {
            CallerInfoCache.CacheEntry cacheEntry = this.mApplication.callerInfoCache.getCacheEntry(connection.getAddress());
            if (cacheEntry != null) {
                if (cacheEntry.sendToVoicemail) {
                    log("send to voicemail flag detected (in fallback cache). hanging up.");
                    Call firstActiveRingingCall = this.mCM.getFirstActiveRingingCall(connection.getCall().getPhone().getSubscription());
                    if (firstActiveRingingCall.getLatestConnection() == connection) {
                        PhoneUtils.hangupRingingCall(firstActiveRingingCall);
                        return;
                    }
                }
                if (cacheEntry.customRingtone != null) {
                    log("custom ringtone found (in fallback cache), setting up ringer: " + cacheEntry.customRingtone);
                    this.mRinger.setCustomRingtoneUri(Uri.parse(cacheEntry.customRingtone));
                }
            } else {
                log("Failed to find fallback cache. Use default ringer tone.");
            }
        }
        onCustomRingQueryComplete(connection);
    }

    protected void showCallDurationIfNeed(Connection connection) {
        if (connection == null) {
            log("not need show duration, connection is null");
            return;
        }
        Connection.DisconnectCause disconnectCause = connection.getDisconnectCause();
        if (disconnectCause == Connection.DisconnectCause.INCOMING_MISSED || disconnectCause == Connection.DisconnectCause.CALL_BARRED || disconnectCause == Connection.DisconnectCause.FDN_BLOCKED || disconnectCause == Connection.DisconnectCause.CS_RESTRICTED || disconnectCause == Connection.DisconnectCause.CS_RESTRICTED_EMERGENCY || disconnectCause == Connection.DisconnectCause.CS_RESTRICTED_NORMAL || disconnectCause == Connection.DisconnectCause.DIAL_MODIFIED_TO_USSD || disconnectCause == Connection.DisconnectCause.DIAL_MODIFIED_TO_SS || disconnectCause == Connection.DisconnectCause.DIAL_MODIFIED_TO_DIAL) {
            log("not need show duration, caused by " + disconnectCause);
            return;
        }
        if (disconnectCause == Connection.DisconnectCause.INCOMING_REJECTED && this.mApplication.getResources().getBoolean(R.bool.reject_call_as_missed_call)) {
            log("not need show duration, caused by " + disconnectCause);
            return;
        }
        if (Settings.System.getInt(this.mApplication.getContentResolver(), "show_call_duration", 1) != 1) {
            log("not need show duration, setting is disabled ");
        } else if (this.isForbidden) {
            log("not need show duration, firewall intercept the incoming call ");
        } else {
            this.mApplication.showCallDuration(connection.getDurationMillis());
        }
    }

    protected void onDisconnect(AsyncResult asyncResult) {
        Connection.DisconnectCause disconnectCause;
        log("onDisconnect()...  CallManager state: " + this.mCM.getState());
        showUssdResponseDialog();
        this.mVoicePrivacyState = false;
        this.mNewRingingConnectionProcessDone = false;
        Connection connection = (Connection) asyncResult.result;
        if (connection != null) {
            log("onDisconnect: cause = " + connection.getDisconnectCause() + ", incoming = " + connection.isIncoming() + ", date = " + connection.getCreateTime());
        } else {
            Log.w("CallNotifier", "onDisconnect: null connection");
        }
        showCallDurationIfNeed(connection);
        if (connection.getDisconnectCause() == Connection.DisconnectCause.SRVCC_CALL_DROP) {
            log("SRVCC case so do not process onDisconnect");
            return;
        }
        int i = 0;
        if (connection != null && connection.getCall().getPhone().getPhoneType() == 2) {
            i = Settings.Global.getInt(this.mApplication.getContentResolver(), "call_auto_retry", 0);
        }
        stopSignalInfoTone();
        if (connection != null && connection.getCall().getPhone().getPhoneType() == 2) {
            this.mApplication.cdmaPhoneCallState.resetCdmaPhoneCallState();
            removeMessages(22);
            removeMessages(23);
        }
        Call firstActiveRingingCall = this.mCM.getFirstActiveRingingCall();
        if (firstActiveRingingCall.getPhone().getPhoneType() == 2) {
            if (PhoneUtils.isRealIncomingCall(firstActiveRingingCall.getState())) {
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
        if (connection != null && TelephonyCapabilities.supportsOtasp(connection.getCall().getPhone())) {
            if (connection.getCall().getPhone().isOtaSpNumber(connection.getAddress())) {
                if (DBG) {
                    log("onDisconnect: this was an OTASP call!");
                }
                this.mApplication.handleOtaspDisconnect();
            }
        }
        int i2 = 0;
        if (connection != null) {
            Connection.DisconnectCause disconnectCause2 = connection.getDisconnectCause();
            if (disconnectCause2 == Connection.DisconnectCause.BUSY) {
                if (DBG) {
                    log("- need to play BUSY tone!");
                }
                i2 = 2;
            } else if (disconnectCause2 == Connection.DisconnectCause.CONGESTION) {
                if (DBG) {
                    log("- need to play CONGESTION tone!");
                }
                i2 = 3;
            } else if ((disconnectCause2 == Connection.DisconnectCause.NORMAL || disconnectCause2 == Connection.DisconnectCause.LOCAL) && this.mApplication.isOtaCallInActiveState()) {
                if (DBG) {
                    log("- need to play OTA_CALL_END tone!");
                }
                i2 = 11;
            } else if (disconnectCause2 == Connection.DisconnectCause.CDMA_REORDER) {
                if (DBG) {
                    log("- need to play CDMA_REORDER tone!");
                }
                i2 = 6;
            } else if (disconnectCause2 == Connection.DisconnectCause.CDMA_INTERCEPT) {
                if (DBG) {
                    log("- need to play CDMA_INTERCEPT tone!");
                }
                i2 = 7;
            } else if (disconnectCause2 == Connection.DisconnectCause.CDMA_DROP) {
                if (DBG) {
                    log("- need to play CDMA_DROP tone!");
                }
                i2 = 8;
            } else if (disconnectCause2 == Connection.DisconnectCause.OUT_OF_SERVICE) {
                if (DBG) {
                    log("- need to play OUT OF SERVICE tone!");
                }
                i2 = 9;
            } else if (disconnectCause2 == Connection.DisconnectCause.UNOBTAINABLE_NUMBER) {
                if (DBG) {
                    log("- need to play TONE_UNOBTAINABLE_NUMBER tone!");
                }
                i2 = 13;
            } else if (disconnectCause2 == Connection.DisconnectCause.ERROR_UNSPECIFIED) {
                if (DBG) {
                    log("- DisconnectCause is ERROR_UNSPECIFIED: play TONE_CALL_ENDED!");
                }
                i2 = 4;
            }
        }
        if (i2 == 0 && this.mCM.getState() == PhoneConstants.State.IDLE && connection != null && ((disconnectCause = connection.getDisconnectCause()) == Connection.DisconnectCause.NORMAL || disconnectCause == Connection.DisconnectCause.LOCAL)) {
            log("- need to play CALL_ENDED tone!");
            i2 = 4;
            this.mIsCdmaRedialCall = false;
        }
        if (this.mCM.getState() == PhoneConstants.State.IDLE) {
            if (i2 == 0) {
                resetAudioStateAfterDisconnect();
            }
            this.mApplication.notificationMgr.cancelCallInProgressNotifications();
        }
        if (connection != null) {
            this.mCallLogger.logCall(connection);
            String address = connection.getAddress();
            Phone phone = connection.getCall().getPhone();
            boolean zIsLocalEmergencyNumber = PhoneNumberUtils.isLocalEmergencyNumber(address, this.mApplication);
            if (phone.getPhoneType() == 2 && zIsLocalEmergencyNumber && this.mCurrentEmergencyToneState != 0 && this.mEmergencyTonePlayerVibrator != null) {
                this.mEmergencyTonePlayerVibrator.stop();
            }
            long createTime = connection.getCreateTime();
            Connection.DisconnectCause disconnectCause3 = connection.getDisconnectCause();
            if (connection.isIncoming() && (disconnectCause3 == Connection.DisconnectCause.INCOMING_MISSED || (disconnectCause3 == Connection.DisconnectCause.INCOMING_REJECTED && this.mApplication.getResources().getBoolean(R.bool.reject_call_as_missed_call)))) {
                showMissedCallNotification(connection, createTime);
            }
            if (i2 != 0) {
                log("- starting post-disconnect tone (" + i2 + ")...");
                new InCallTonePlayer(i2).start();
            }
            if ((this.mPreviousCdmaCallState == Call.State.DIALING || this.mPreviousCdmaCallState == Call.State.ALERTING) && !zIsLocalEmergencyNumber && disconnectCause3 != Connection.DisconnectCause.INCOMING_MISSED && disconnectCause3 != Connection.DisconnectCause.NORMAL && disconnectCause3 != Connection.DisconnectCause.LOCAL && disconnectCause3 != Connection.DisconnectCause.INCOMING_REJECTED) {
                if (!this.mIsCdmaRedialCall) {
                    if (i == 1) {
                        if (PhoneUtils.placeCall(this.mApplication, phone, address, null, false) != 2) {
                            this.mIsCdmaRedialCall = true;
                            return;
                        }
                        return;
                    }
                    this.mIsCdmaRedialCall = false;
                    return;
                }
                this.mIsCdmaRedialCall = false;
            }
        }
    }

    protected void showUssdResponseDialog() {
        Dialog uSSDResponseDialog = this.mApplication.getUSSDResponseDialog();
        if (this.mCM.getState() == PhoneConstants.State.IDLE && uSSDResponseDialog != null) {
            log("show ussd dialog...");
            uSSDResponseDialog.show();
        }
    }

    protected void resetAudioStateAfterDisconnect() {
        log("resetAudioStateAfterDisconnect()...");
        if (this.mBluetoothHeadset != null) {
            this.mBluetoothHeadset.disconnectAudio();
        }
        PhoneUtils.turnOnSpeaker(this.mApplication, false, true);
        PhoneUtils.setAudioMode(this.mCM);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onMwiChanged(boolean z) {
        log("onMwiChanged(): " + z);
        if (!PhoneGlobals.sVoiceCapable) {
            Log.w("CallNotifier", "Got onMwiChanged() on non-voice-capable device! Ignoring...");
        } else {
            this.mApplication.notificationMgr.updateMwi(z);
        }
    }

    void sendMwiChangedDelayed(long delayMillis) {
        Message message = Message.obtain(this, 21);
        sendMessageDelayed(message, delayMillis);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onCfiChanged(boolean z) {
        log("onCfiChanged(): " + z);
        this.mApplication.notificationMgr.updateCfi(z);
    }

    boolean isRinging() {
        return this.mRinger.isRinging();
    }

    void silenceRinger() {
        this.mSilentRingerRequested = true;
        if (DBG) {
            log("stopRing()... (silenceRinger)");
        }
        this.mRinger.stopRing();
    }

    protected class InCallTonePlayer extends Thread {
        private int mState = 0;
        private int mToneId;

        InCallTonePlayer(int toneId) {
            this.mToneId = toneId;
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            int toneType;
            int toneVolume;
            int toneLengthMillis;
            ToneGenerator toneGenerator;
            int stream = 0;
            CallNotifier.this.log("InCallTonePlayer.run(toneId = " + this.mToneId + ")...");
            int phoneType = CallNotifier.this.mCM.getFgPhone().getPhoneType();
            switch (this.mToneId) {
                case 1:
                    toneType = 22;
                    toneVolume = 80;
                    toneLengthMillis = 2147483627;
                    break;
                case 2:
                    if (phoneType == 2) {
                        toneType = 96;
                        toneVolume = 50;
                        toneLengthMillis = 1000;
                    } else if (phoneType == 1 || phoneType == 3 || phoneType == 4) {
                        toneType = 17;
                        toneVolume = 80;
                        toneLengthMillis = 4000;
                    } else {
                        throw new IllegalStateException("Unexpected phone type: " + phoneType);
                    }
                    break;
                case 3:
                    toneType = 18;
                    toneVolume = 80;
                    toneLengthMillis = 4000;
                    break;
                case 4:
                    toneType = 27;
                    toneVolume = 80;
                    toneLengthMillis = 200;
                    break;
                case 5:
                    toneType = 86;
                    toneVolume = 80;
                    toneLengthMillis = 5000;
                    break;
                case 6:
                    toneType = 38;
                    toneVolume = 80;
                    toneLengthMillis = 4000;
                    break;
                case 7:
                    toneType = 37;
                    toneVolume = 50;
                    toneLengthMillis = 500;
                    break;
                case 8:
                case 9:
                    toneType = 95;
                    toneVolume = 50;
                    toneLengthMillis = 375;
                    break;
                case 10:
                    toneType = 87;
                    toneVolume = 50;
                    toneLengthMillis = 5000;
                    break;
                case 11:
                    if (CallNotifier.this.mApplication.cdmaOtaConfigData.otaPlaySuccessFailureTone == 1) {
                        toneType = 93;
                        toneVolume = 80;
                        toneLengthMillis = 750;
                    } else {
                        toneType = 27;
                        toneVolume = 80;
                        toneLengthMillis = 200;
                    }
                    break;
                case 12:
                    toneType = 23;
                    toneVolume = 80;
                    toneLengthMillis = 2147483627;
                    break;
                case 13:
                    toneType = 21;
                    toneVolume = 80;
                    toneLengthMillis = 4000;
                    break;
                case 14:
                    toneType = 99;
                    toneVolume = 80;
                    toneLengthMillis = 2147483627;
                    break;
                case 15:
                    toneType = 101;
                    toneVolume = 80;
                    toneLengthMillis = 2147483627;
                    break;
                case 16:
                    toneType = 100;
                    toneVolume = 80;
                    toneLengthMillis = 2147483627;
                    break;
                default:
                    throw new IllegalArgumentException("Bad toneId: " + this.mToneId);
            }
            try {
                if (CallNotifier.this.mBluetoothHeadset != null) {
                    if (CallNotifier.this.mBluetoothHeadset.isAudioOn()) {
                        stream = 6;
                    }
                } else {
                    stream = 0;
                }
                if (toneType == 100) {
                    stream = 10;
                }
                toneGenerator = new ToneGenerator(stream, toneVolume);
            } catch (RuntimeException e) {
                Log.w("CallNotifier", "InCallTonePlayer: Exception caught while creating ToneGenerator: " + e);
                toneGenerator = null;
            }
            boolean needToStopTone = true;
            boolean okToPlayTone = false;
            if (toneGenerator != null) {
                int ringerMode = CallNotifier.this.mAudioManager.getRingerMode();
                if (phoneType == 2) {
                    if (toneType == 93) {
                        if (ringerMode != 0 && ringerMode != 1) {
                            if (CallNotifier.DBG) {
                                CallNotifier.this.log("- InCallTonePlayer: start playing call tone=" + toneType);
                            }
                            okToPlayTone = true;
                            needToStopTone = false;
                        }
                    } else if (toneType == 96 || toneType == 38 || toneType == 39 || toneType == 37 || toneType == 95) {
                        if (ringerMode != 0) {
                            if (CallNotifier.DBG) {
                                CallNotifier.this.log("InCallTonePlayer:playing call fail tone:" + toneType);
                            }
                            okToPlayTone = true;
                            needToStopTone = false;
                        }
                    } else if (toneType == 87 || toneType == 86) {
                        if (ringerMode != 0 && ringerMode != 1) {
                            if (CallNotifier.DBG) {
                                CallNotifier.this.log("InCallTonePlayer:playing tone for toneType=" + toneType);
                            }
                            okToPlayTone = true;
                            needToStopTone = false;
                        }
                    } else {
                        okToPlayTone = true;
                    }
                } else {
                    okToPlayTone = true;
                }
                synchronized (this) {
                    if (okToPlayTone) {
                        if (this.mState != 2) {
                            this.mState = 1;
                            toneGenerator.startTone(toneType);
                            try {
                                wait(toneLengthMillis + 20);
                            } catch (InterruptedException e2) {
                                Log.w("CallNotifier", "InCallTonePlayer stopped: " + e2);
                            }
                            if (needToStopTone) {
                                toneGenerator.stopTone();
                            }
                        }
                    }
                    toneGenerator.release();
                    this.mState = 0;
                }
            }
            if (CallNotifier.this.mCM.getState() == PhoneConstants.State.IDLE) {
                CallNotifier.this.resetAudioStateAfterDisconnect();
            }
        }

        public void stopTone() {
            synchronized (this) {
                if (this.mState == 1) {
                    notify();
                }
                this.mState = 2;
            }
        }
    }

    private void onDisplayInfo(AsyncResult asyncResult) {
        CdmaInformationRecords.CdmaDisplayInfoRec cdmaDisplayInfoRec = (CdmaInformationRecords.CdmaDisplayInfoRec) asyncResult.result;
        if (cdmaDisplayInfoRec != null) {
            String str = cdmaDisplayInfoRec.alpha;
            if (DBG) {
                log("onDisplayInfo: displayInfo=" + str);
            }
            CdmaDisplayInfo.displayInfoRecord(this.mApplication, str);
            sendEmptyMessageDelayed(24, 2000L);
        }
    }

    protected class SignalInfoTonePlayer extends Thread {
        private int mToneId;

        SignalInfoTonePlayer(int toneId) {
            this.mToneId = toneId;
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            CallNotifier.this.log("SignalInfoTonePlayer.run(toneId = " + this.mToneId + ")...");
            if (CallNotifier.this.mSignalInfoToneGenerator != null) {
                CallNotifier.this.mSignalInfoToneGenerator.stopTone();
                CallNotifier.this.mSignalInfoToneGenerator.startTone(this.mToneId);
            }
        }
    }

    protected void onSignalInfo(AsyncResult asyncResult) {
        if (!PhoneGlobals.sVoiceCapable) {
            Log.w("CallNotifier", "Got onSignalInfo() on non-voice-capable device! Ignoring...");
            return;
        }
        if (PhoneUtils.isRealIncomingCall(this.mCM.getFirstActiveRingingCall().getState())) {
            stopSignalInfoTone();
            return;
        }
        CdmaInformationRecords.CdmaSignalInfoRec cdmaSignalInfoRec = (CdmaInformationRecords.CdmaSignalInfoRec) asyncResult.result;
        if (cdmaSignalInfoRec != null) {
            boolean z = cdmaSignalInfoRec.isPresent;
            if (DBG) {
                log("onSignalInfo: isPresent=" + z);
            }
            if (z) {
                int i = cdmaSignalInfoRec.signalType;
                int i2 = cdmaSignalInfoRec.alertPitch;
                int i3 = cdmaSignalInfoRec.signal;
                if (DBG) {
                    log("onSignalInfo: uSignalType=" + i + ", uAlertPitch=" + i2 + ", uSignal=" + i3);
                }
                new SignalInfoTonePlayer(SignalToneUtil.getAudioToneFromSignalInfo(i, i2, i3)).start();
            }
        }
    }

    void stopSignalInfoTone() {
        if (DBG) {
            log("stopSignalInfoTone: Stopping SignalInfo tone player");
        }
        new SignalInfoTonePlayer(98).start();
    }

    protected void onCdmaCallWaiting(AsyncResult asyncResult) {
        handlePendingCdmaWaitingCall();
        removeMessages(22);
        removeMessages(23);
        this.mApplication.cdmaPhoneCallState.setCurrentCallState(CdmaPhoneCallState.PhoneCallState.SINGLE_ACTIVE);
        this.mCallWaitingTimeOut = false;
        sendEmptyMessageDelayed(22, 20000L);
        this.mApplication.cdmaPhoneCallState.setAddCallMenuStateAfterCallWaiting(false);
        sendEmptyMessageDelayed(23, 30000L);
        CdmaCallWaitingNotification cdmaCallWaitingNotification = (CdmaCallWaitingNotification) asyncResult.result;
        int i = cdmaCallWaitingNotification.isPresent;
        if (DBG) {
            log("onCdmaCallWaiting: isPresent=" + i);
        }
        if (i == 1) {
            int i2 = cdmaCallWaitingNotification.signalType;
            int i3 = cdmaCallWaitingNotification.alertPitch;
            int i4 = cdmaCallWaitingNotification.signal;
            if (DBG) {
                log("onCdmaCallWaiting: uSignalType=" + i2 + ", uAlertPitch=" + i3 + ", uSignal=" + i4);
            }
            new SignalInfoTonePlayer(SignalToneUtil.getAudioToneFromSignalInfo(i2, i3, i4)).start();
        }
        this.mCallModeler.onCdmaCallWaiting(cdmaCallWaitingNotification);
    }

    private void handlePendingCdmaWaitingCall() {
        Call ringingCall = this.mCM.getFirstActiveRingingCall();
        Connection c = ringingCall.getEarliestConnection();
        if (ringingCall.getState() == Call.State.WAITING && ringingCall.getConnections().size() > 1 && c != null) {
            this.mCallLogger.logCall(c, 3);
            showMissedCallNotification(c, c.getCreateTime());
            PhoneUtils.hangup(c);
            this.mCallModeler.onCdmaCallWaitingReject();
        }
    }

    void sendCdmaCallWaitingReject() {
        sendEmptyMessage(26);
    }

    private void onCdmaCallWaitingReject() {
        Call ringingCall = this.mCM.getFirstActiveRingingCall();
        if (ringingCall.getState() == Call.State.WAITING) {
            Connection c = ringingCall.getLatestConnection();
            if (c != null) {
                int callLogType = this.mCallWaitingTimeOut ? 3 : 1;
                this.mCallLogger.logCall(c, callLogType);
                long date = c.getCreateTime();
                if (callLogType == 3) {
                    showMissedCallNotification(c, date);
                } else {
                    removeMessages(22);
                }
                PhoneUtils.hangup(c);
            }
            this.mCallWaitingTimeOut = false;
        }
        this.mCallModeler.onCdmaCallWaitingReject();
    }

    boolean getIsCdmaRedialCall() {
        return this.mIsCdmaRedialCall;
    }

    protected void showMissedCallNotification(Connection connection, long j) {
        PhoneUtils.CallerInfoToken callerInfoTokenStartGetCallerInfo = PhoneUtils.startGetCallerInfo(this.mApplication, connection, this, Long.valueOf(j));
        if (callerInfoTokenStartGetCallerInfo != null) {
            log("showMissedCallNotification: Querying for CallerInfo on missed call...");
            if (callerInfoTokenStartGetCallerInfo.isFinal) {
                CallerInfo callerInfo = callerInfoTokenStartGetCallerInfo.currentInfo;
                String string = callerInfo.name;
                String strModifyForSpecialCnapCases = callerInfo.phoneNumber;
                if (callerInfo.numberPresentation == PhoneConstants.PRESENTATION_RESTRICTED) {
                    string = this.mApplication.getString(R.string.private_num);
                } else if (callerInfo.numberPresentation != PhoneConstants.PRESENTATION_ALLOWED) {
                    string = this.mApplication.getString(R.string.unknown);
                } else {
                    strModifyForSpecialCnapCases = PhoneUtils.modifyForSpecialCnapCases(this.mApplication, callerInfo, strModifyForSpecialCnapCases, callerInfo.numberPresentation);
                }
                this.mApplication.notificationMgr.notifyMissedCall(string, strModifyForSpecialCnapCases, callerInfo.numberPresentation, callerInfo.phoneLabel, callerInfo.cachedPhoto, callerInfo.cachedPhotoIcon, j);
                return;
            }
            return;
        }
        Log.w("CallNotifier", "showMissedCallNotification: got null CallerInfo for Connection " + connection);
    }

    protected class EmergencyTonePlayerVibrator {
        private int mInCallVolume;
        private ToneGenerator mToneGenerator;
        private final int EMG_VIBRATE_LENGTH = 1000;
        private final int EMG_VIBRATE_PAUSE = 1000;
        private final long[] mVibratePattern = {1000, 1000};
        private Vibrator mEmgVibrator = new SystemVibrator();

        public EmergencyTonePlayerVibrator() {
        }

        protected void start() {
            CallNotifier.this.log("call startEmergencyToneOrVibrate.");
            int ringerMode = CallNotifier.this.mAudioManager.getRingerMode();
            if (CallNotifier.this.mIsEmergencyToneOn == 1 && ringerMode == 2) {
                CallNotifier.this.log("EmergencyTonePlayerVibrator.start(): emergency tone...");
                this.mToneGenerator = new ToneGenerator(0, 100);
                if (this.mToneGenerator != null) {
                    this.mInCallVolume = CallNotifier.this.mAudioManager.getStreamVolume(0);
                    CallNotifier.this.mAudioManager.setStreamVolume(0, CallNotifier.this.mAudioManager.getStreamMaxVolume(0), 0);
                    this.mToneGenerator.startTone(92);
                    CallNotifier.this.mCurrentEmergencyToneState = 1;
                    return;
                }
                return;
            }
            if (CallNotifier.this.mIsEmergencyToneOn == 2) {
                CallNotifier.this.log("EmergencyTonePlayerVibrator.start(): emergency vibrate...");
                if (this.mEmgVibrator != null) {
                    this.mEmgVibrator.vibrate(this.mVibratePattern, 0);
                    CallNotifier.this.mCurrentEmergencyToneState = 2;
                }
            }
        }

        protected void stop() {
            CallNotifier.this.log("call stopEmergencyToneOrVibrate.");
            if (CallNotifier.this.mCurrentEmergencyToneState == 1 && this.mToneGenerator != null) {
                this.mToneGenerator.stopTone();
                this.mToneGenerator.release();
                CallNotifier.this.mAudioManager.setStreamVolume(0, this.mInCallVolume, 0);
            } else if (CallNotifier.this.mCurrentEmergencyToneState == 2 && this.mEmgVibrator != null) {
                this.mEmgVibrator.cancel();
            }
            CallNotifier.this.mCurrentEmergencyToneState = 0;
        }
    }

    private void onRingbackTone(AsyncResult r) {
        boolean playTone = ((Boolean) r.result).booleanValue();
        if (playTone) {
            if (this.mCM.getActiveFgCallState().isDialing() && this.mInCallRingbackTonePlayer == null) {
                this.mInCallRingbackTonePlayer = new InCallTonePlayer(12);
                this.mInCallRingbackTonePlayer.start();
                return;
            }
            return;
        }
        if (this.mInCallRingbackTonePlayer != null) {
            this.mInCallRingbackTonePlayer.stopTone();
            this.mInCallRingbackTonePlayer = null;
        }
    }

    private void onResendMute() {
        boolean muteState = PhoneUtils.getMute();
        PhoneUtils.setMute(!muteState);
        PhoneUtils.setMute(muteState);
    }

    private void onUnsolCallModify(AsyncResult asyncResult) {
        Log.v("CallNotifier", " handleMessage (EVENT_CALL_MODIFY)");
        if (asyncResult != null && asyncResult.result != null && asyncResult.exception == null) {
            this.mCallModeler.onUnsolCallModify((Connection) asyncResult.result);
        } else {
            Log.e("CallNotifier", "Error EVENT_MODIFY_CALL AsyncResult ar= " + asyncResult);
        }
    }

    private String getMoSsNotificationText(int i) {
        switch (i) {
            case 0:
                return this.mApplication.getString(R.string.card_title_unconditionalCF);
            case 1:
                return this.mApplication.getString(R.string.card_title_conditionalCF);
            case 2:
                return this.mApplication.getString(R.string.card_title_MOcall_forwarding);
            case 3:
                return this.mApplication.getString(R.string.card_title_calliswaiting);
            case 4:
                return this.mApplication.getString(R.string.card_title_cugcall);
            case 5:
                return this.mApplication.getString(R.string.card_title_outgoing_barred);
            case 6:
                return this.mApplication.getString(R.string.card_title_incoming_barred);
            case 7:
                return this.mApplication.getString(R.string.card_title_clir_suppression_rejected);
            case 8:
                return this.mApplication.getString(R.string.card_title_call_deflected);
            default:
                log("Received unsupported MO SS Notification :" + i);
                return "";
        }
    }

    private String getMtSsNotificationText(int i) {
        switch (i) {
            case 0:
                return this.mApplication.getString(R.string.card_title_forwarded_MTcall);
            case 1:
                return this.mApplication.getString(R.string.card_title_cugcall);
            case 2:
                return this.mApplication.getString(R.string.card_title_callonhold);
            case 3:
                return this.mApplication.getString(R.string.card_title_callretrieved);
            case 4:
                return this.mApplication.getString(R.string.card_title_multipartycall);
            case 5:
                return this.mApplication.getString(R.string.card_title_callonhold_released);
            case 6:
                return this.mApplication.getString(R.string.card_title_forwardcheckreceived);
            case 7:
                return this.mApplication.getString(R.string.card_title_callconnectingect);
            case 8:
                return this.mApplication.getString(R.string.card_title_callconnectedect);
            case 9:
                return this.mApplication.getString(R.string.card_title_deflectedcall);
            case 10:
                return this.mApplication.getString(R.string.card_title_MTcall_forwarding);
            default:
                log("Received unsupported MT SS Notification :" + i);
                return "";
        }
    }

    private String getSuppSvcNotificationText(SuppServiceNotification suppServiceNotification) {
        if (suppServiceNotification == null) {
            return "";
        }
        switch (suppServiceNotification.notificationType) {
            case 0:
                return getMoSsNotificationText(suppServiceNotification.code);
            case 1:
                return getMtSsNotificationText(suppServiceNotification.code);
            default:
                Log.e("CallNotifier", "Received invalid Notification Type :" + suppServiceNotification.notificationType);
                return "";
        }
    }

    void onCdmaCallWaitingAnswered() {
        removeMessages(22);
        removeMessages(23);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void log(String msg) {
        Log.d("CallNotifier", msg);
    }
}
