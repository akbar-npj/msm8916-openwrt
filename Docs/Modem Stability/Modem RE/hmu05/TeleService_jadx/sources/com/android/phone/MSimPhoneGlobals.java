package com.android.phone;

import android.app.KeyguardManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.IPowerManager;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UpdateLock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.MSimTelephonyManager;
import android.telephony.ServiceState;
import android.util.Log;
import android.view.KeyEvent;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyCapabilities;
import com.android.phone.common.CallLogAsync;
import com.codeaurora.telephony.msim.MSimPhoneFactory;
import com.codeaurora.telephony.msim.ModemStackController;
import com.codeaurora.telephony.msim.SubscriptionManager;

/* JADX INFO: loaded from: classes.dex */
public class MSimPhoneGlobals extends PhoneGlobals {
    private static final boolean DBG;
    private static MSPhone[] mMSPhones;
    private int mDefaultSubscription;
    private BroadcastReceiver mMediaButtonReceiver;
    private BroadcastReceiver mReceiver;
    MSimPhoneInterfaceManager phoneMgrMSim;

    static {
        DBG = SystemProperties.getInt("ro.debuggable", 0) == 1;
    }

    MSimPhoneGlobals(Context context) {
        super(context);
        this.mReceiver = new PhoneGlobals.PhoneAppBroadcastReceiver();
        this.mMediaButtonReceiver = new PhoneGlobals.MediaButtonBroadcastReceiver();
        this.mDefaultSubscription = 0;
        Log.d("MSimPhoneGlobals", "MSPhoneApp creation" + this);
    }

    @Override // com.android.phone.PhoneGlobals
    public void onCreate() {
        Log.d("MSimPhoneGlobals", "MSimPhoneApp:" + this);
        ContentResolver contentResolver = getContentResolver();
        sVoiceCapable = getResources().getBoolean(android.R.bool.config_audio_ringer_mode_affects_alarm_stream);
        if (this.phone == null) {
            MSimPhoneFactory.makeMultiSimDefaultPhones(this);
            this.phone = MSimPhoneFactory.getDefaultPhone();
            startService(new Intent(this, (Class<?>) TelephonyDebugService.class));
            this.mCM = CallManager.getInstance();
            int phoneCount = MSimTelephonyManager.getDefault().getPhoneCount();
            mMSPhones = new MSPhone[phoneCount];
            for (int i = 0; i < phoneCount; i++) {
                mMSPhones[i] = new MSPhone(i);
                this.mCM.registerPhone(mMSPhones[i].mPhone);
            }
            this.mDefaultSubscription = getDefaultSubscription();
            setDefaultPhone(this.mDefaultSubscription);
            this.mCM.registerPhone(this.phone);
            createImsService();
            createCsvtService();
            this.notificationMgr = MSimNotificationMgr.init(this);
            this.mHandler.sendEmptyMessage(17);
            if (this.phone.getPhoneType() == 2) {
                this.cdmaPhoneCallState = new CdmaPhoneCallState();
                this.cdmaPhoneCallState.CdmaPhoneCallStateInit();
            }
            if (BluetoothAdapter.getDefaultAdapter() != null) {
                startService(new Intent(this, (Class<?>) BluetoothPhoneService.class));
                bindService(new Intent(this, (Class<?>) BluetoothPhoneService.class), this.mBluetoothPhoneConnection, 0);
            } else {
                this.mBluetoothPhone = null;
            }
            this.mReceiver = new MSimPhoneAppBroadcastReceiver();
            this.mMediaButtonReceiver = new MSimMediaButtonBroadcastReceiver();
            this.mPowerManager = (PowerManager) getSystemService("power");
            this.mWakeLock = this.mPowerManager.newWakeLock(26, "MSimPhoneGlobals");
            this.mPartialWakeLock = this.mPowerManager.newWakeLock(536870913, "MSimPhoneGlobals");
            this.mKeyguardManager = (KeyguardManager) getSystemService("keyguard");
            this.mPowerManagerService = IPowerManager.Stub.asInterface(ServiceManager.getService("power"));
            this.mUpdateLock = new UpdateLock("phone");
            if (DBG) {
                Log.d("MSimPhoneGlobals", "onCreate: mUpdateLock: " + this.mUpdateLock);
            }
            CallLogger callLogger = new CallLogger(this, new CallLogAsync());
            this.callGatewayManager = CallGatewayManager.getInstance();
            this.callController = CallController.init(this, callLogger, this.callGatewayManager);
            this.callerInfoCache = CallerInfoCache.init(this);
            this.callStateMonitor = new MSimCallStateMonitor(this.mCM);
            this.callModeler = new CallModeler(this.callStateMonitor, this.mCM, this.callGatewayManager);
            this.dtmfTonePlayer = new DTMFTonePlayer(this.mCM, this.callModeler);
            this.wiredHeadsetManager = new WiredHeadsetManager(this);
            this.wiredHeadsetManager.addWiredHeadsetListener(this);
            this.bluetoothManager = new BluetoothManager(this, this.mCM, this.callModeler);
            this.ringer = Ringer.init(this, this.bluetoothManager);
            this.audioRouter = new AudioRouter(this, this.bluetoothManager, this.wiredHeadsetManager, this.mCM);
            this.callCommandService = new CallCommandService(this, this.mCM, this.callModeler, this.dtmfTonePlayer, this.audioRouter);
            this.callHandlerServiceProxy = new CallHandlerServiceProxy(this, this.callModeler, this.callCommandService, this.audioRouter);
            this.phoneMgr = PhoneInterfaceManager.init(this, this.phone, this.callHandlerServiceProxy, this.callModeler, this.dtmfTonePlayer);
            this.phoneMgrMSim = MSimPhoneInterfaceManager.init(this, this.phone, this.callHandlerServiceProxy);
            this.notifier = MSimCallNotifier.init(this, this.phone, this.ringer, callLogger, this.callStateMonitor, this.bluetoothManager, this.callModeler);
            this.mManagedRoam = ManagedRoaming.init(this);
            XDivertUtility.init(this, this.phone, (MSimCallNotifier) this.notifier, this);
            for (int i2 = 0; i2 < MSimTelephonyManager.getDefault().getPhoneCount(); i2++) {
                IccCard iccCard = getPhone(i2).getIccCard();
                if (iccCard != null) {
                    iccCard.registerForPersoLocked(this.mHandler, 3, new Integer(i2));
                }
            }
            this.mCM.registerForMmiComplete(this.mHandler, 52, (Object) null);
            PhoneUtils.initializeConnectionHandler(this.mCM);
            this.mTtyEnabled = getResources().getBoolean(R.bool.tty_enabled);
            IntentFilter intentFilter = new IntentFilter("android.intent.action.AIRPLANE_MODE");
            intentFilter.addAction("android.intent.action.ANY_DATA_STATE");
            intentFilter.addAction("android.intent.action.DOCK_EVENT");
            intentFilter.addAction("android.intent.action.SIM_STATE_CHANGED");
            intentFilter.addAction("android.intent.action.RADIO_TECHNOLOGY");
            intentFilter.addAction("android.intent.action.SERVICE_STATE");
            intentFilter.addAction("android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED");
            intentFilter.addAction("qualcomm.intent.action.ACTION_DEFAULT_SUBSCRIPTION_CHANGED");
            if (this.mTtyEnabled) {
                intentFilter.addAction("com.android.internal.telephony.cdma.intent.action.TTY_PREFERRED_MODE_CHANGE");
            }
            intentFilter.addAction("android.media.RINGER_MODE_CHANGED");
            registerReceiver(this.mReceiver, intentFilter);
            IntentFilter intentFilter2 = new IntentFilter("android.intent.action.MEDIA_BUTTON");
            intentFilter2.setPriority(1);
            registerReceiver(this.mMediaButtonReceiver, intentFilter2);
            ((AudioManager) getSystemService("audio")).registerMediaButtonEventReceiverForCalls(new ComponentName(getPackageName(), PhoneGlobals.MediaButtonBroadcastReceiver.class.getName()));
            PreferenceManager.setDefaultValues(this, R.xml.network_setting, false);
            PreferenceManager.setDefaultValues(this, R.xml.call_feature_setting, false);
            PhoneUtils.setAudioMode(this.mCM);
        }
        for (int i3 = 0; i3 < MSimTelephonyManager.getDefault().getPhoneCount(); i3++) {
            updatePhoneAppCdmaVariables(i3);
        }
        contentResolver.getType(Uri.parse("content://icc/adn"));
        this.mShouldRestoreMuteOnInCallResume = false;
        if (this.mTtyEnabled) {
            this.mPreferredTtyMode = Settings.Secure.getInt(this.phone.getContext().getContentResolver(), "preferred_tty_mode", 0);
            this.mHandler.sendMessage(this.mHandler.obtainMessage(14, 0));
        }
        if (getResources().getBoolean(R.bool.hac_enabled)) {
            ((AudioManager) getSystemService("audio")).setParameter("HACSetting", Settings.System.getInt(this.phone.getContext().getContentResolver(), "hearing_aid", 0) != 0 ? "ON" : "OFF");
        }
        loadPhoneServiceBinder();
    }

    boolean isSimPinEnabled(int subscription) {
        MSPhone msPhone = getMSPhone(subscription);
        return msPhone.mIsSimPinEnabled;
    }

    @Override // com.android.phone.PhoneGlobals
    public void onMMIComplete(AsyncResult r) {
        MmiCode mmiCode = (MmiCode) r.result;
        Phone localPhone = mmiCode.getPhone();
        PhoneUtils.displayMMIComplete(localPhone, getInstance(), mmiCode, null, null);
    }

    void initForNewRadioTechnology(int subscription) {
        if (DBG) {
            Log.d("MSimPhoneGlobals", "initForNewRadioTechnology...");
        }
        MSPhone msPhone = getMSPhone(subscription);
        Phone phone = msPhone.mPhone;
        if (TelephonyCapabilities.supportsOtasp(phone)) {
            msPhone.initializeCdmaVariables();
            updatePhoneAppCdmaVariables(subscription);
            clearOtaState();
        }
        this.ringer.updateRingerContextAfterRadioTechnologyChange(this.phone);
        this.notifier.updateCallNotifierRegistrationsAfterRadioTechnologyChange();
        if (this.mBluetoothPhone != null) {
            try {
                this.mBluetoothPhone.updateBtHandsfreeAfterRadioTechnologyChange();
            } catch (RemoteException e) {
                Log.e("MSimPhoneGlobals", Log.getStackTraceString(new Throwable()));
            }
        }
        IccCard sim = phone.getIccCard();
        if (sim != null) {
            if (DBG) {
                Log.d("MSimPhoneGlobals", "Update registration for ICC status...");
            }
            sim.registerForPersoLocked(this.mHandler, 3, (Object) null);
        }
    }

    private class MSimPhoneAppBroadcastReceiver extends PhoneGlobals.PhoneAppBroadcastReceiver {
        private MSimPhoneAppBroadcastReceiver() {
            super();
        }

        @Override // com.android.phone.PhoneGlobals.PhoneAppBroadcastReceiver, android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.v("MSimPhoneGlobals", "Action intent recieved:" + action);
            int intExtra = intent.getIntExtra("subscription", MSimPhoneGlobals.this.getDefaultSubscription());
            if (action.equals("android.intent.action.AIRPLANE_MODE")) {
                boolean z = Settings.System.getInt(MSimPhoneGlobals.this.getContentResolver(), "airplane_mode_on", 0) == 0;
                Log.d("MSimPhoneGlobals", "Setting property persist.radio.airplane_mode_on = " + (z ? "0" : "1"));
                SystemProperties.set("persist.radio.airplane_mode_on", z ? "0" : "1");
                for (int i = 0; i < MSimTelephonyManager.getDefault().getPhoneCount(); i++) {
                    MSimPhoneGlobals.this.getPhone(i).setRadioPower(z);
                }
                return;
            }
            if (action.equals("android.intent.action.SIM_STATE_CHANGED") && MSimPhoneGlobals.this.mPUKEntryActivity != null) {
                MSimPhoneGlobals.this.mHandler.sendMessage(MSimPhoneGlobals.this.mHandler.obtainMessage(8, intent.getStringExtra("ss")));
                return;
            }
            if (action.equals("android.intent.action.RADIO_TECHNOLOGY")) {
                Log.d("MSimPhoneGlobals", "Radio technology switched. Now " + intent.getStringExtra("phoneName") + " is active.");
                MSimPhoneGlobals.this.initForNewRadioTechnology(intExtra);
                return;
            }
            if (action.equals("android.intent.action.SERVICE_STATE")) {
                MSimPhoneGlobals.this.handleServiceStateChanged(intent, MSimPhoneGlobals.this.getPhone(intExtra));
                return;
            }
            if (action.equals("android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED")) {
                Phone phone = MSimPhoneGlobals.this.getPhone(intExtra);
                if (TelephonyCapabilities.supportsEcm(phone)) {
                    Log.d("MSimPhoneGlobals", "Emergency Callback Mode arrived in PhoneApp on Sub =" + intExtra);
                    if (intent.getBooleanExtra("phoneinECMState", false)) {
                        Intent intent2 = new Intent(context, (Class<?>) EmergencyCallbackModeService.class);
                        intent2.putExtra("subscription", intExtra);
                        context.startService(intent2);
                        return;
                    }
                    return;
                }
                Log.e("MSimPhoneGlobals", "Got ACTION_EMERGENCY_CALLBACK_MODE_CHANGED, but ECM isn't supported for phone: " + phone.getPhoneName());
                return;
            }
            if (action.equals("qualcomm.intent.action.ACTION_DEFAULT_SUBSCRIPTION_CHANGED")) {
                Log.d("MSimPhoneGlobals", "Default subscription changed, subscription: " + intExtra);
                MSimPhoneGlobals.this.mDefaultSubscription = intExtra;
                MSimPhoneGlobals.this.setDefaultPhone(intExtra);
                MSimPhoneGlobals.this.phoneMgr.setPhone(MSimPhoneGlobals.this.phone);
                return;
            }
            super.onReceive(context, intent);
        }
    }

    private class MSimMediaButtonBroadcastReceiver extends PhoneGlobals.MediaButtonBroadcastReceiver {
        private MSimMediaButtonBroadcastReceiver() {
            super();
        }

        @Override // com.android.phone.PhoneGlobals.MediaButtonBroadcastReceiver, android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            KeyEvent event = (KeyEvent) intent.getParcelableExtra("android.intent.extra.KEY_EVENT");
            Log.d("MSimPhoneGlobals", "MediaButtonBroadcastReceiver.onReceive() event = " + event);
            if (event != null && event.getKeyCode() == 79) {
                for (int i = 0; i < MSimTelephonyManager.getDefault().getPhoneCount(); i++) {
                    boolean consumed = PhoneUtils.handleHeadsetHook(MSimPhoneGlobals.this.getPhone(i), event);
                    Log.d("MSimPhoneGlobals", "handleHeadsetHook(): consumed = " + consumed + " on SUB [" + i + "]");
                    if (consumed) {
                        abortBroadcast();
                        return;
                    }
                }
                return;
            }
            if (MSimPhoneGlobals.this.mCM.getState() != PhoneConstants.State.IDLE) {
                abortBroadcast();
            }
        }
    }

    private void updatePhoneAppCdmaVariables(int subscription) {
        Log.v("MSimPhoneGlobals", "updatePhoneAppCdmaVariables for SUB " + subscription);
        MSPhone msPhone = getMSPhone(subscription);
        if (msPhone != null && msPhone.mPhone.getPhoneType() == 2) {
            this.cdmaPhoneCallState = msPhone.mCdmaPhoneCallState;
            this.cdmaOtaProvisionData = msPhone.mCdmaOtaProvisionData;
            this.cdmaOtaConfigData = msPhone.mCdmaOtaConfigData;
            this.cdmaOtaScreenState = msPhone.mCdmaOtaScreenState;
            this.cdmaOtaInCallScreenUiState = msPhone.mCdmaOtaInCallScreenUiState;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleServiceStateChanged(Intent intent, Phone phone) {
        ServiceState ss = ServiceState.newFromBundle(intent.getExtras());
        if (ss != null) {
            int state = ss.getState();
            this.notificationMgr.updateNetworkSelection(state, phone);
        }
    }

    private static MSPhone getMSPhone(int subscription) {
        try {
            return mMSPhones[subscription];
        } catch (IndexOutOfBoundsException e) {
            Log.e("MSimPhoneGlobals", "subscripton Index out of bounds " + e);
            return null;
        }
    }

    Phone getDefaultPhone() {
        return getPhone(getDefaultSubscription());
    }

    @Override // com.android.phone.PhoneGlobals
    Phone getPhone(int subscription) {
        MSPhone msPhone = getMSPhone(subscription);
        if (msPhone != null) {
            return msPhone.mPhone;
        }
        Log.w("MSimPhoneGlobals", "msPhone object is null returning default phone");
        return this.phone;
    }

    @Override // com.android.phone.PhoneGlobals
    public int getVoiceSubscriptionInService() {
        int voiceSub = getVoiceSubscription();
        int sub = -1;
        MSimTelephonyManager tm = MSimTelephonyManager.getDefault();
        int count = tm.getPhoneCount();
        SubscriptionManager subManager = SubscriptionManager.getInstance();
        for (int i = 0; i < count; i++) {
            Phone phone = getPhone(i);
            int ss = phone.getServiceState().getState();
            if ((ss == 0 || phone.getServiceState().isEmergencyOnly()) && (sub = i) == voiceSub) {
                break;
            }
        }
        if (DBG) {
            Log.d("MSimPhoneGlobals", "Voice sub in service = " + sub);
        }
        if (sub == -1) {
            for (int i2 = 0; i2 < count && (tm.getSimState(i2) != 5 || !subManager.isSubActive(i2) || (sub = i2) != voiceSub); i2++) {
            }
            if (sub == -1) {
                sub = ModemStackController.getInstance().getPrimarySub();
            }
        }
        Log.d("MSimPhoneGlobals", "Voice sub in service=" + sub + " preferred sub=" + voiceSub);
        return sub;
    }

    void setDefaultPhone(int subscription) {
        MSPhone msPhone = getMSPhone(subscription);
        this.phone = msPhone.mPhone;
        this.mLastPhoneState = msPhone.mLastPhoneState;
        updatePhoneAppCdmaVariables(subscription);
        this.mDefaultSubscription = subscription;
    }

    @Override // com.android.phone.PhoneGlobals
    public int getDefaultSubscription() {
        return MSimPhoneFactory.getDefaultSubscription();
    }

    @Override // com.android.phone.PhoneGlobals
    public int getVoiceSubscription() {
        return MSimPhoneFactory.getVoiceSubscription();
    }

    @Override // com.android.phone.PhoneGlobals
    public int getDataSubscription() {
        return MSimPhoneFactory.getDataSubscription();
    }

    @Override // com.android.phone.PhoneGlobals
    public int getDefaultDataSubscription() {
        return MSimPhoneFactory.getDefaultDataSubscription();
    }
}
