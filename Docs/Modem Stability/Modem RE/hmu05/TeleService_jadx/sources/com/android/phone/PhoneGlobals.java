package com.android.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.TaskStackBuilder;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.IBluetoothHeadsetPhone;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.IBinder;
import android.os.IPowerManager;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UpdateLock;
import android.os.UserHandle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyCapabilities;
import com.android.phone.common.CallLogAsync;
import com.android.server.sip.SipService;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.codeaurora.ims.IImsService;
import org.codeaurora.ims.IImsServiceListener;
import org.codeaurora.ims.csvt.CallForwardInfoP;
import org.codeaurora.ims.csvt.ICsvtService;
import org.codeaurora.ims.csvt.ICsvtServiceListener;

/* JADX INFO: loaded from: classes.dex */
public class PhoneGlobals extends ContextWrapper implements WiredHeadsetManager.WiredHeadsetListener {
    private static final boolean DBG;
    public static ICsvtService mCsvtService;
    private static ServiceConnection mCsvtServiceConnection;
    private static ICsvtServiceListener mCsvtServiceListener;
    static int mDockState;
    public static Handler mIMSHandler;
    public static IImsService mImsService;
    public static boolean mIsImsListenerRegistered;
    private static int sImsVideoSrvStatus;
    private static int sImsVoiceSrvStatus;
    protected static PhoneGlobals sMe;
    static boolean sVoiceCapable;
    private ServiceConnection ImsServiceConnection;
    protected AudioRouter audioRouter;
    protected BluetoothManager bluetoothManager;
    protected CallCommandService callCommandService;
    CallController callController;
    protected CallGatewayManager callGatewayManager;
    protected CallHandlerServiceProxy callHandlerServiceProxy;
    protected CallModeler callModeler;
    protected CallStateMonitor callStateMonitor;
    CallerInfoCache callerInfoCache;
    public OtaUtils.CdmaOtaConfigData cdmaOtaConfigData;
    public OtaUtils.CdmaOtaInCallScreenUiState cdmaOtaInCallScreenUiState;
    public OtaUtils.CdmaOtaProvisionData cdmaOtaProvisionData;
    public OtaUtils.CdmaOtaScreenState cdmaOtaScreenState;
    CdmaPhoneCallState cdmaPhoneCallState;
    protected DTMFTonePlayer dtmfTonePlayer;
    IImsServiceListener imsServListener;
    protected IBluetoothHeadsetPhone mBluetoothPhone;
    protected final ServiceConnection mBluetoothPhoneConnection;
    CallManager mCM;
    private Dialog mCallDurationDialog;
    Handler mHandler;
    Phone mImsPhone;
    private boolean mIsSimPinEnabled;
    protected KeyguardManager mKeyguardManager;
    protected PhoneConstants.State mLastPhoneState;
    ManagedRoaming mManagedRoam;
    private final BroadcastReceiver mMediaButtonReceiver;
    protected Activity mPUKEntryActivity;
    private ProgressDialog mPUKEntryProgressDialog;
    protected PowerManager.WakeLock mPartialWakeLock;
    public Object mPhoneServiceClient;
    protected PowerManager mPowerManager;
    protected IPowerManager mPowerManagerService;
    protected int mPreferredTtyMode;
    private Object mProxy;
    private final BroadcastReceiver mReceiver;
    protected boolean mShouldRestoreMuteOnInCallResume;
    protected boolean mTtyEnabled;
    private Dialog mUSSDResponseDialog;
    protected UpdateLock mUpdateLock;
    protected PowerManager.WakeLock mWakeLock;
    private WakeState mWakeState;
    NotificationMgr notificationMgr;
    CallNotifier notifier;
    public OtaUtils otaUtils;
    Phone phone;
    PhoneInterfaceManager phoneMgr;
    protected Ringer ringer;
    protected WiredHeadsetManager wiredHeadsetManager;

    public enum WakeState {
        SLEEP,
        PARTIAL,
        FULL
    }

    static {
        DBG = SystemProperties.getInt("ro.debuggable", 0) == 1;
        mIsImsListenerRegistered = false;
        mDockState = 0;
        sVoiceCapable = true;
        sImsVoiceSrvStatus = 3;
        sImsVideoSrvStatus = 3;
        mIMSHandler = new Handler() { // from class: com.android.phone.PhoneGlobals.2
            @Override // android.os.Handler
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case 101:
                        Log.e("PhoneApp", "ims Register fail");
                        Toast.makeText(PhoneGlobals.sMe.getApplicationContext(), R.string.ims_registration_state_changed_fail, 1).show();
                        if (CallFeaturesSetting.ImsRegistration != null) {
                            PhoneGlobals.loadImsRegistration(CallFeaturesSetting.ImsRegistration, PhoneGlobals.getIMSRegistrationState());
                        } else if (MSimCallFeaturesSetting.ImsRegistration != null) {
                            PhoneGlobals.loadImsRegistration(MSimCallFeaturesSetting.ImsRegistration, PhoneGlobals.getIMSRegistrationState());
                        }
                        break;
                    case 102:
                        Log.e("PhoneApp", "ims deregister fail");
                        Toast.makeText(PhoneGlobals.sMe.getApplicationContext(), R.string.ims_registration_state_changed_de_fail, 1).show();
                        if (CallFeaturesSetting.ImsRegistration != null) {
                            PhoneGlobals.loadImsRegistration(CallFeaturesSetting.ImsRegistration, PhoneGlobals.getIMSRegistrationState());
                        } else if (MSimCallFeaturesSetting.ImsRegistration != null) {
                            PhoneGlobals.loadImsRegistration(MSimCallFeaturesSetting.ImsRegistration, PhoneGlobals.getIMSRegistrationState());
                        }
                        break;
                }
            }
        };
        mCsvtServiceConnection = new ServiceConnection() { // from class: com.android.phone.PhoneGlobals.5
            @Override // android.content.ServiceConnection
            public void onServiceConnected(ComponentName name, IBinder service) {
                PhoneGlobals.mCsvtService = ICsvtService.Stub.asInterface(service);
                Log.d("PhoneApp", "Csvt Service Connected: " + PhoneGlobals.mCsvtService);
                if (PhoneGlobals.mCsvtService != null) {
                    try {
                        PhoneGlobals.mCsvtService.registerListener(PhoneGlobals.mCsvtServiceListener);
                        Log.d("PhoneApp", "Csvt Service register ICsvtServiceListener");
                    } catch (RemoteException e) {
                        Log.e("PhoneApp", Log.getStackTraceString(new Throwable()));
                    }
                }
            }

            @Override // android.content.ServiceConnection
            public void onServiceDisconnected(ComponentName arg0) {
                Log.w("PhoneApp", "Csvt Service onServiceDisconnected");
                PhoneGlobals.mCsvtService = null;
            }
        };
        mCsvtServiceListener = new ICsvtServiceListener.Stub() { // from class: com.android.phone.PhoneGlobals.6
            public void onPhoneStateChanged(int state) {
                Log.d("PhoneApp", "onPhoneStateChanged");
                Intent intent = new Intent("intent.action.CSVT_PRECISE_CALL_STATE_CHANGED");
                PhoneGlobals.getInstance().sendBroadcast(intent);
            }

            public void onCallStatus(int result) {
            }

            public void onCallWaiting(boolean enabled) {
            }

            public void onCallForwardingOptions(List<CallForwardInfoP> fi) {
            }

            public void onRingbackTone(boolean playTone) {
            }
        };
    }

    void setRestoreMuteOnInCallResume(boolean mode) {
        this.mShouldRestoreMuteOnInCallResume = mode;
    }

    public PhoneGlobals(Context context) {
        super(context);
        this.mLastPhoneState = PhoneConstants.State.IDLE;
        this.mWakeState = WakeState.SLEEP;
        this.mReceiver = new PhoneAppBroadcastReceiver();
        this.mMediaButtonReceiver = new MediaButtonBroadcastReceiver();
        this.mPreferredTtyMode = 0;
        this.mHandler = new Handler() { // from class: com.android.phone.PhoneGlobals.1
            @Override // android.os.Handler
            public void handleMessage(Message msg) {
                int ttyMode;
                switch (msg.what) {
                    case 3:
                        if (PhoneGlobals.this.getResources().getBoolean(R.bool.ignore_perso_locked_events)) {
                            Log.i("PhoneApp", "Ignoring EVENT_PERSO_LOCKED event; not showing 'PERSO unlock' PIN entry screen");
                        } else {
                            Log.i("PhoneApp", "show depersonal panel");
                            int subtype = ((Integer) ((AsyncResult) msg.obj).result).intValue();
                            IccDepersonalizationPanel dpPanel = new IccDepersonalizationPanel(PhoneGlobals.getInstance(), subtype);
                            dpPanel.show();
                        }
                        break;
                    case 8:
                        if (msg.obj.equals("READY")) {
                            if (PhoneGlobals.this.mPUKEntryActivity != null) {
                                PhoneGlobals.this.mPUKEntryActivity.finish();
                                PhoneGlobals.this.mPUKEntryActivity = null;
                            }
                            if (PhoneGlobals.this.mPUKEntryProgressDialog != null) {
                                PhoneGlobals.this.mPUKEntryProgressDialog.dismiss();
                                PhoneGlobals.this.mPUKEntryProgressDialog = null;
                            }
                        }
                        break;
                    case 10:
                        PhoneGlobals.this.notificationMgr.showDataDisconnectedRoaming();
                        break;
                    case 11:
                        PhoneGlobals.this.notificationMgr.hideDataDisconnectedRoaming();
                        break;
                    case 13:
                        boolean inDockMode = false;
                        if (PhoneGlobals.mDockState != 0) {
                            inDockMode = true;
                        }
                        Log.d("PhoneApp", "received EVENT_DOCK_STATE_CHANGED. Phone inDock = " + inDockMode);
                        PhoneConstants.State phoneState = PhoneGlobals.this.mCM.getState();
                        if (phoneState == PhoneConstants.State.OFFHOOK && !PhoneGlobals.this.wiredHeadsetManager.isHeadsetPlugged() && !PhoneGlobals.this.bluetoothManager.isBluetoothHeadsetAudioOn()) {
                            PhoneGlobals.this.audioRouter.setSpeaker(inDockMode);
                            PhoneUtils.turnOnSpeaker(PhoneGlobals.this.getApplicationContext(), inDockMode, true);
                            break;
                        }
                        break;
                    case 14:
                        if (PhoneGlobals.this.wiredHeadsetManager.isHeadsetPlugged()) {
                            ttyMode = PhoneGlobals.this.mPreferredTtyMode;
                        } else {
                            ttyMode = 0;
                        }
                        PhoneGlobals.this.phone.setTTYMode(ttyMode, PhoneGlobals.this.mHandler.obtainMessage(16));
                        break;
                    case 15:
                        PhoneGlobals.this.handleQueryTTYModeResponse(msg);
                        break;
                    case 16:
                        PhoneGlobals.this.handleSetTTYModeResponse(msg);
                        break;
                    case 17:
                        SipService.start(PhoneGlobals.this.getApplicationContext());
                        break;
                    case 18:
                        AsyncResult ar = (AsyncResult) msg.obj;
                        if (ar != null && ar.exception != null) {
                            Log.e("PhoneApp", msg.what + " failed " + ar.exception.toString());
                            break;
                        }
                        break;
                    case 19:
                        PhoneGlobals.this.mPhoneServiceClient = PhoneGlobals.invokeMethod("com.qualcomm.qti.phonefeature.PhoneServiceClient", "getServiceBinder", PhoneGlobals.this.mProxy, null, null);
                        break;
                    case 52:
                        PhoneGlobals.this.onMMIComplete((AsyncResult) msg.obj);
                        break;
                    case 53:
                        PhoneUtils.cancelMmiCode(PhoneGlobals.this.phone);
                        break;
                }
            }
        };
        this.ImsServiceConnection = new ServiceConnection() { // from class: com.android.phone.PhoneGlobals.3
            @Override // android.content.ServiceConnection
            public void onServiceConnected(ComponentName name, IBinder service) {
                PhoneGlobals.mImsService = IImsService.Stub.asInterface(service);
                Log.d("PhoneApp", "Ims Service Connected" + PhoneGlobals.mImsService);
                if (PhoneGlobals.mImsService != null) {
                    try {
                        int result = PhoneGlobals.mImsService.registerCallback(PhoneGlobals.this.imsServListener);
                        if (result == 0) {
                            Log.d("PhoneApp", "Callback registered successfully");
                            PhoneGlobals.mIsImsListenerRegistered = true;
                            PhoneGlobals.mImsService.queryImsServiceStatus(18, new Messenger(PhoneGlobals.this.mHandler));
                        }
                        Phone phone = PhoneUtils.getImsPhone(PhoneGlobals.getInstance().mCM);
                        if (phone != null && phone.getServiceState().getState() == 0) {
                            PhoneGlobals.this.notificationMgr.updateImsRegistration(true);
                        }
                    } catch (RemoteException e) {
                        Log.e("PhoneApp", "Remote Exception in mImsService.registerCallback");
                    }
                }
            }

            @Override // android.content.ServiceConnection
            public void onServiceDisconnected(ComponentName arg0) {
                Log.w("PhoneApp", "Ims Service onServiceDisconnected");
                int unused = PhoneGlobals.sImsVoiceSrvStatus = 3;
                int unused2 = PhoneGlobals.sImsVideoSrvStatus = 3;
                PhoneGlobals.mImsService = null;
                PhoneGlobals.this.notificationMgr.updateImsRegistration(false);
            }
        };
        this.imsServListener = new IImsServiceListener.Stub() { // from class: com.android.phone.PhoneGlobals.4
            @Override // org.codeaurora.ims.IImsServiceListener
            public void imsUpdateServiceStatus(int service, int status) {
                Log.v("PhoneApp", "imsUpdateServiceStatus response service " + service + "status = " + status);
                if (service == 0) {
                    int unused = PhoneGlobals.sImsVoiceSrvStatus = status;
                } else if (service == 3) {
                    int unused2 = PhoneGlobals.sImsVideoSrvStatus = status;
                }
            }

            @Override // org.codeaurora.ims.IImsServiceListener
            public void imsRegStateChanged(int imsRegState) {
                PhoneGlobals.this.notificationMgr.updateImsRegistration(imsRegState == 1);
                if (CallFeaturesSetting.ImsRegistration != null) {
                    PhoneGlobals.loadImsRegistration(CallFeaturesSetting.ImsRegistration, PhoneGlobals.getIMSRegistrationState());
                } else if (MSimCallFeaturesSetting.ImsRegistration != null) {
                    PhoneGlobals.loadImsRegistration(MSimCallFeaturesSetting.ImsRegistration, PhoneGlobals.getIMSRegistrationState());
                }
                PhoneGlobals.mIMSHandler.removeMessages(101);
                PhoneGlobals.mIMSHandler.removeMessages(102);
            }

            @Override // org.codeaurora.ims.IImsServiceListener
            public void imsRegStateChangeReqFailed() {
                Log.e("PhoneApp", "imsRegStateChangeReqFailed!");
                Toast.makeText(PhoneGlobals.this.getApplicationContext(), R.string.ims_registration_state_changed_fail, 1).show();
                if (CallFeaturesSetting.ImsRegistration != null) {
                    PhoneGlobals.loadImsRegistration(CallFeaturesSetting.ImsRegistration, PhoneGlobals.getIMSRegistrationState());
                } else if (MSimCallFeaturesSetting.ImsRegistration != null) {
                    PhoneGlobals.loadImsRegistration(MSimCallFeaturesSetting.ImsRegistration, PhoneGlobals.getIMSRegistrationState());
                }
            }
        };
        this.mBluetoothPhoneConnection = new ServiceConnection() { // from class: com.android.phone.PhoneGlobals.8
            @Override // android.content.ServiceConnection
            public void onServiceConnected(ComponentName className, IBinder service) {
                Log.i("PhoneApp", "Headset phone created, binding local service.");
                PhoneGlobals.this.mBluetoothPhone = IBluetoothHeadsetPhone.Stub.asInterface(service);
            }

            @Override // android.content.ServiceConnection
            public void onServiceDisconnected(ComponentName className) {
                Log.i("PhoneApp", "Headset phone disconnected, cleaning local binding.");
                PhoneGlobals.this.mBluetoothPhone = null;
            }
        };
        sMe = this;
    }

    public void loadPhoneServiceBinder() {
        this.mProxy = loadClassObj("com.qualcomm.qti.phonefeature.PhoneServiceClient", new Class[]{Context.class, Message.class}, new Object[]{this, this.mHandler.obtainMessage(19)});
    }

    /* JADX WARN: Code duplicated, block: B:8:0x0012  */
    public static Object loadClassObj(String className, Class<?>[] paramClasses, Object[] params) {
        Throwable exception;
        try {
            Class<?> targetClass = Class.forName(className);
            return targetClass.getDeclaredConstructor(paramClasses).newInstance(params);
        } catch (ClassNotFoundException e) {
            exception = e;
            if (exception != null) {
                Log.e("PhoneApp", "failed to load class obj!", exception);
            }
            return null;
        } catch (IllegalAccessException e2) {
            exception = e2;
            if (exception != null) {
                Log.e("PhoneApp", "failed to load class obj!", exception);
            }
            return null;
        } catch (IllegalArgumentException e3) {
            exception = e3;
            if (exception != null) {
                Log.e("PhoneApp", "failed to load class obj!", exception);
            }
            return null;
        } catch (InstantiationException e4) {
            exception = e4;
            if (exception != null) {
                Log.e("PhoneApp", "failed to load class obj!", exception);
            }
            return null;
        } catch (NoSuchMethodException e5) {
            exception = e5;
            if (exception != null) {
                Log.e("PhoneApp", "failed to load class obj!", exception);
            }
            return null;
        } catch (InvocationTargetException e6) {
            exception = e6;
            if (exception != null) {
                Log.e("PhoneApp", "failed to load class obj!", exception);
            }
            return null;
        }
    }

    /* JADX WARN: Code duplicated, block: B:8:0x0012  */
    public static Object invokeMethod(String className, String methodName, Object instance, Class<?>[] paramClasses, Object[] params) {
        Throwable exception;
        try {
            Class<?> targetClass = Class.forName(className);
            return targetClass.getDeclaredMethod(methodName, paramClasses).invoke(instance, params);
        } catch (ClassNotFoundException e) {
            exception = e;
            if (exception != null) {
                Log.e("PhoneApp", "failed to invoke method!", exception);
            }
            return null;
        } catch (IllegalAccessException e2) {
            exception = e2;
            if (exception != null) {
                Log.e("PhoneApp", "failed to invoke method!", exception);
            }
            return null;
        } catch (IllegalArgumentException e3) {
            exception = e3;
            if (exception != null) {
                Log.e("PhoneApp", "failed to invoke method!", exception);
            }
            return null;
        } catch (NoSuchMethodException e4) {
            exception = e4;
            if (exception != null) {
                Log.e("PhoneApp", "failed to invoke method!", exception);
            }
            return null;
        } catch (InvocationTargetException e5) {
            exception = e5;
            if (exception != null) {
                Log.e("PhoneApp", "failed to invoke method!", exception);
            }
            return null;
        }
    }

    public void setTDDDataOnly(int i, boolean z, Message message) {
        if (message != null) {
            message.replyTo = new Messenger(message.getTarget());
        }
        invokeMethod("com.qualcomm.qti.phonefeature.IServiceBinder", "setTDDDataOnly", this.mPhoneServiceClient, new Class[]{Integer.TYPE, Boolean.TYPE, Message.class}, new Object[]{Integer.valueOf(i), Boolean.valueOf(z), message});
    }

    public void setPrefNetwork(int i, int i2, Message message) {
        if (message != null) {
            message.replyTo = new Messenger(message.getTarget());
        }
        invokeMethod("com.qualcomm.qti.phonefeature.IServiceBinder", "setPreferredNetwork", this.mPhoneServiceClient, new Class[]{Integer.TYPE, Integer.TYPE, Message.class}, new Object[]{Integer.valueOf(i), Integer.valueOf(i2), message});
    }

    public void setPrefNetworWithAcq(int i, int i2, int i3, Message message) {
        if (message != null) {
            message.replyTo = new Messenger(message.getTarget());
        }
        invokeMethod("com.qualcomm.qti.phonefeature.IServiceBinder", "setPreferredNetworkWithAcq", this.mPhoneServiceClient, new Class[]{Integer.TYPE, Integer.TYPE, Integer.TYPE, Message.class}, new Object[]{Integer.valueOf(i), Integer.valueOf(i2), Integer.valueOf(i3), message});
    }

    public int getPreferredLTESub() {
        Object result = invokeMethod("com.qualcomm.qti.phonefeature.IServiceBinder", "getPreferredLteSub", this.mPhoneServiceClient, null, null);
        if (result != null) {
            return ((Integer) result).intValue();
        }
        return -1;
    }

    public int getCurrentLTESub() {
        Object result = invokeMethod("com.qualcomm.qti.phonefeature.IServiceBinder", "getCurrentLteSub", this.mPhoneServiceClient, null, null);
        if (result != null) {
            return ((Integer) result).intValue();
        }
        return -1;
    }

    public void onCreate() {
        Log.v("PhoneApp", "onCreate()...");
        ContentResolver contentResolver = getContentResolver();
        sVoiceCapable = getResources().getBoolean(android.R.bool.config_audio_ringer_mode_affects_alarm_stream);
        if (this.phone == null) {
            PhoneFactory.makeDefaultPhones(this);
            this.phone = PhoneFactory.getDefaultPhone();
            startService(new Intent(this, (Class<?>) TelephonyDebugService.class));
            this.mCM = CallManager.getInstance();
            this.mCM.registerPhone(this.phone);
            createImsService();
            createCsvtService();
            this.notificationMgr = NotificationMgr.init(this);
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
            this.mPowerManager = (PowerManager) getSystemService("power");
            this.mWakeLock = this.mPowerManager.newWakeLock(26, "PhoneApp");
            this.mPartialWakeLock = this.mPowerManager.newWakeLock(536870913, "PhoneApp");
            this.mKeyguardManager = (KeyguardManager) getSystemService("keyguard");
            this.mPowerManagerService = IPowerManager.Stub.asInterface(ServiceManager.getService("power"));
            this.mUpdateLock = new UpdateLock("phone");
            if (DBG) {
                Log.d("PhoneApp", "onCreate: mUpdateLock: " + this.mUpdateLock);
            }
            CallLogger callLogger = new CallLogger(this, new CallLogAsync());
            this.callGatewayManager = CallGatewayManager.getInstance();
            this.callController = CallController.init(this, callLogger, this.callGatewayManager);
            this.callerInfoCache = CallerInfoCache.init(this);
            this.callStateMonitor = new CallStateMonitor(this.mCM);
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
            this.notifier = CallNotifier.init(this, this.phone, this.ringer, callLogger, this.callStateMonitor, this.bluetoothManager, this.callModeler);
            this.mManagedRoam = ManagedRoaming.init(this);
            IccCard iccCard = this.phone.getIccCard();
            if (iccCard != null) {
                Log.v("PhoneApp", "register for ICC status");
                iccCard.registerForPersoLocked(this.mHandler, 3, (Object) null);
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
            if (this.mTtyEnabled) {
                intentFilter.addAction("com.android.internal.telephony.cdma.intent.action.TTY_PREFERRED_MODE_CHANGE");
            }
            intentFilter.addAction("android.media.RINGER_MODE_CHANGED");
            registerReceiver(this.mReceiver, intentFilter);
            IntentFilter intentFilter2 = new IntentFilter("android.intent.action.MEDIA_BUTTON");
            intentFilter2.setPriority(1);
            registerReceiver(this.mMediaButtonReceiver, intentFilter2);
            ((AudioManager) getSystemService("audio")).registerMediaButtonEventReceiverForCalls(new ComponentName(getPackageName(), MediaButtonBroadcastReceiver.class.getName()));
            PreferenceManager.setDefaultValues(this, R.xml.network_setting, false);
            PreferenceManager.setDefaultValues(this, R.xml.call_feature_setting, false);
            PhoneUtils.setAudioMode(this.mCM);
        }
        if (TelephonyCapabilities.supportsOtasp(this.phone)) {
            this.cdmaOtaProvisionData = new OtaUtils.CdmaOtaProvisionData();
            this.cdmaOtaConfigData = new OtaUtils.CdmaOtaConfigData();
            this.cdmaOtaScreenState = new OtaUtils.CdmaOtaScreenState();
            this.cdmaOtaInCallScreenUiState = new OtaUtils.CdmaOtaInCallScreenUiState();
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

    public void createImsService() {
        try {
            boolean bound = bindService(new Intent("org.codeaurora.ims.IImsService"), this.ImsServiceConnection, 1);
            Log.d("PhoneApp", "IMSService bound request : " + bound);
        } catch (NoClassDefFoundError e) {
            Log.w("PhoneApp", "Ignoring IMS class not found exception " + e);
        }
    }

    public static int getIMSRegistrationState() {
        int imsRegState = 2;
        try {
            imsRegState = mImsService.getRegistrationState();
        } catch (RemoteException e) {
            Log.d("PhoneApp", "getIMSRegistrationState Exception");
        }
        Log.d("PhoneApp", "getIMSRegistrationState: " + imsRegState);
        return imsRegState;
    }

    public static void loadImsRegistration(ListPreference listPreference, int i) {
        listPreference.setEnabled(true);
        Log.d("PhoneApp", "loadImsRegistration: imsRegState = " + i);
        if (i == 1) {
            Log.d("PhoneApp", "loadImsRegistration: setval IMS_REG_STATE_REGISTERED");
            listPreference.setValue("4G call");
            listPreference.setSummary(R.string.ims_registered_summary);
        } else {
            Log.d("PhoneApp", "loadImsRegistration: setval IMS_REG_STATE_DEREGISTERED");
            listPreference.setValue("2G/3G call");
            listPreference.setSummary(R.string.ims_deregistered_summary);
        }
    }

    public static void setIMSRegistrationState(int i) {
        Log.d("PhoneApp", "setIMSRegistrationState: " + i);
        try {
            mImsService.setRegistrationState(i);
        } catch (RemoteException e) {
            Log.d("PhoneApp", "setIMSRegistrationState Exception");
        }
    }

    public static void updateImsRegistration(ListPreference listPreference) {
        if (!mIsImsListenerRegistered) {
            Log.d("PhoneApp", "updateImsRegistration fail as IMS Service Listener not registered.");
        }
        Log.d("PhoneApp", "updateImsRegistration Called with value - " + listPreference.getValue());
        listPreference.setEnabled(false);
        if ("4G call".equalsIgnoreCase(listPreference.getValue()) && getIMSRegistrationState() != 1) {
            listPreference.setSummary("Registering...");
            mIMSHandler.sendMessageDelayed(mIMSHandler.obtainMessage(101, 1), 5000L);
            setIMSRegistrationState(1);
            return;
        }
        if (getIMSRegistrationState() != 2) {
            listPreference.setSummary("Deregistering...");
            mIMSHandler.sendMessageDelayed(mIMSHandler.obtainMessage(102, 1), 5000L);
            setIMSRegistrationState(2);
        }
    }

    public static int getImsServiceStatus(int i) {
        switch (i) {
            case 0:
                return sImsVoiceSrvStatus;
            case 1:
            case 2:
            default:
                Log.e("PhoneApp", "Unsupported service for API usage");
                return 3;
            case 3:
                return sImsVideoSrvStatus;
        }
    }

    public static boolean isIMSRegisterd() {
        int registrationState;
        boolean z = false;
        if (!SystemProperties.getBoolean("persist.radio.csvt.enabled", false)) {
            try {
                registrationState = mImsService.getRegistrationState();
            } catch (Exception e) {
                Log.e("PhoneApp", "Exception in getRegistrationState(), IMSRegistrationState = 0");
                registrationState = 0;
            }
            switch (registrationState) {
                case 1:
                    z = true;
                    break;
                case 2:
                    break;
                default:
                    Log.e("PhoneApp", "getRegistrationState() failed");
                    break;
            }
            Log.v("PhoneApp", "isIMSRegisterd= " + z + ",IMSRegistrationState= " + registrationState);
        }
        return z;
    }

    public boolean isCsvtActive() {
        boolean zIsActive = false;
        if (mCsvtService == null) {
            return false;
        }
        try {
            zIsActive = mCsvtService.isActive();
            Log.d("PhoneApp", "mCsvtService.isActive = " + zIsActive);
            return zIsActive;
        } catch (RemoteException e) {
            Log.e("PhoneApp", Log.getStackTraceString(new Throwable()));
            return zIsActive;
        }
    }

    public void createCsvtService() {
        if (PhoneUtils.isCallOnCsvtEnabled()) {
            try {
                Log.d("PhoneApp", "ICsvtService bound request : " + bindService(new Intent("org.codeaurora.ims.csvt.ICsvtService"), mCsvtServiceConnection, 1));
            } catch (NoClassDefFoundError e) {
                Log.w("PhoneApp", "Ignoring ICsvtService class not found exception " + e);
            }
        }
    }

    public static PhoneGlobals getInstance() {
        if (sMe == null) {
            throw new IllegalStateException("No PhoneGlobals here!");
        }
        return sMe;
    }

    static PhoneGlobals getInstanceIfPrimary() {
        return sMe;
    }

    static Phone getPhone() {
        return getInstance().phone;
    }

    Phone getPhone(int subscription) {
        return this.phone;
    }

    IBluetoothHeadsetPhone getBluetoothPhoneService() {
        return this.mBluetoothPhone;
    }

    BluetoothManager getBluetoothManager() {
        return this.bluetoothManager;
    }

    WiredHeadsetManager getWiredHeadsetManager() {
        return this.wiredHeadsetManager;
    }

    AudioRouter getAudioRouter() {
        return this.audioRouter;
    }

    CallModeler getCallModeler() {
        return this.callModeler;
    }

    CallManager getCallManager() {
        return this.mCM;
    }

    static Intent createCallLogIntent() {
        Intent intent = new Intent("android.intent.action.VIEW", (Uri) null);
        intent.setType("vnd.android.cursor.dir/calls");
        return intent;
    }

    static PendingIntent createPendingCallLogIntent(Context context) {
        Intent callLogIntent = createCallLogIntent();
        TaskStackBuilder taskStackBuilder = TaskStackBuilder.create(context);
        taskStackBuilder.addNextIntent(callLogIntent);
        return taskStackBuilder.getPendingIntent(0, 0);
    }

    static PendingIntent getCallBackPendingIntent(Context context, String str) {
        return PendingIntent.getBroadcast(context, 0, new Intent("com.android.phone.ACTION_CALL_BACK_FROM_NOTIFICATION", Uri.fromParts("tel", str, null), context, NotificationBroadcastReceiver.class), 0);
    }

    static PendingIntent getSendSmsFromNotificationPendingIntent(Context context, String str) {
        return PendingIntent.getBroadcast(context, 0, new Intent("com.android.phone.ACTION_SEND_SMS_FROM_NOTIFICATION", Uri.fromParts("smsto", str, null), context, NotificationBroadcastReceiver.class), 0);
    }

    static void initCallWaitingPref(PreferenceActivity preferenceActivity, int i) {
        PreferenceScreen preferenceScreen = (PreferenceScreen) preferenceActivity.findPreference("button_cw_act_key");
        PreferenceScreen preferenceScreen2 = (PreferenceScreen) preferenceActivity.findPreference("button_cw_deact_key");
        CdmaCallOptionsSetting cdmaCallOptionsSetting = new CdmaCallOptionsSetting(preferenceActivity, 7, i);
        preferenceScreen.getIntent().putExtra("subscription", i).setData(Uri.fromParts("tel", cdmaCallOptionsSetting.getActivateNumber(), null));
        preferenceScreen.setSummary(cdmaCallOptionsSetting.getActivateNumber());
        preferenceScreen2.getIntent().putExtra("subscription", i).setData(Uri.fromParts("tel", cdmaCallOptionsSetting.getDeactivateNumber(), null));
        preferenceScreen2.setSummary(cdmaCallOptionsSetting.getDeactivateNumber());
    }

    boolean isSimPinEnabled() {
        return this.mIsSimPinEnabled;
    }

    void handleOtaspEvent(Message message) {
        if (DBG) {
            Log.d("PhoneApp", "handleOtaspEvent(message " + message + ")...");
        }
        if (this.otaUtils == null) {
            Log.w("PhoneApp", "handleOtaEvents: got an event but otaUtils is null! message = " + message);
        } else {
            this.otaUtils.onOtaProvisionStatusChanged((AsyncResult) message.obj);
        }
    }

    void handleOtaspDisconnect() {
        if (DBG) {
            Log.d("PhoneApp", "handleOtaspDisconnect()...");
        }
        if (this.otaUtils == null) {
            Log.w("PhoneApp", "handleOtaspDisconnect: otaUtils is null!");
        } else {
            this.otaUtils.onOtaspDisconnect();
        }
    }

    void setPukEntryActivity(Activity activity) {
        this.mPUKEntryActivity = activity;
    }

    Activity getPUKEntryActivity() {
        return this.mPUKEntryActivity;
    }

    void setPukEntryProgressDialog(ProgressDialog dialog) {
        this.mPUKEntryProgressDialog = dialog;
    }

    Dialog getUSSDResponseDialog() {
        return this.mUSSDResponseDialog;
    }

    void requestWakeState(WakeState wakeState) {
        Log.d("PhoneApp", "requestWakeState(" + wakeState + ")...");
        synchronized (this) {
            if (this.mWakeState != wakeState) {
                switch (wakeState) {
                    case PARTIAL:
                        this.mPartialWakeLock.acquire();
                        if (this.mWakeLock.isHeld()) {
                            this.mWakeLock.release();
                        }
                        break;
                    case FULL:
                        this.mWakeLock.acquire();
                        if (this.mPartialWakeLock.isHeld()) {
                            this.mPartialWakeLock.release();
                        }
                        break;
                    default:
                        if (this.mWakeLock.isHeld()) {
                            this.mWakeLock.release();
                        }
                        if (this.mPartialWakeLock.isHeld()) {
                            this.mPartialWakeLock.release();
                        }
                        break;
                }
                this.mWakeState = wakeState;
            }
        }
    }

    void wakeUpScreen() {
        synchronized (this) {
            if (this.mWakeState == WakeState.SLEEP) {
                if (DBG) {
                    Log.d("PhoneApp", "pulse screen lock");
                }
                this.mPowerManager.wakeUp(SystemClock.uptimeMillis());
            }
        }
    }

    void updateWakeState() {
        PhoneConstants.State state = this.mCM.getState();
        if (state != PhoneConstants.State.OFFHOOK || PhoneUtils.isSpeakerOn(this)) {
        }
        boolean isRinging = state == PhoneConstants.State.RINGING;
        boolean isDialing = this.phone.getForegroundCall().getState() == Call.State.DIALING;
        boolean isVideoCallActive = PhoneUtils.isImsVideoCallActive(this.mCM.getActiveFgCall());
        boolean keepScreenOn = isRinging || isDialing || isVideoCallActive;
        requestWakeState(keepScreenOn ? WakeState.FULL : WakeState.SLEEP);
    }

    void pokeUserActivity() {
        Log.d("PhoneApp", "pokeUserActivity()...");
        this.mPowerManager.userActivity(SystemClock.uptimeMillis(), false);
    }

    void updatePhoneState(PhoneConstants.State state) {
        if (state != this.mLastPhoneState) {
            this.mLastPhoneState = state;
            if (state != PhoneConstants.State.IDLE) {
                if (!this.mUpdateLock.isHeld()) {
                    this.mUpdateLock.acquire();
                }
            } else if (this.mUpdateLock.isHeld()) {
                this.mUpdateLock.release();
            }
        }
    }

    KeyguardManager getKeyguardManager() {
        return this.mKeyguardManager;
    }

    protected void onMMIComplete(AsyncResult asyncResult) {
        Log.d("PhoneApp", "onMMIComplete()...");
        PhoneUtils.displayMMIComplete(this.phone, getInstance(), (MmiCode) asyncResult.result, null, null);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void initForNewRadioTechnology() {
        if (DBG) {
            Log.d("PhoneApp", "initForNewRadioTechnology...");
        }
        if (this.phone.getPhoneType() == 2) {
            this.cdmaPhoneCallState = new CdmaPhoneCallState();
            this.cdmaPhoneCallState.CdmaPhoneCallStateInit();
        }
        if (TelephonyCapabilities.supportsOtasp(this.phone)) {
            if (this.cdmaOtaProvisionData == null) {
                this.cdmaOtaProvisionData = new OtaUtils.CdmaOtaProvisionData();
            }
            if (this.cdmaOtaConfigData == null) {
                this.cdmaOtaConfigData = new OtaUtils.CdmaOtaConfigData();
            }
            if (this.cdmaOtaScreenState == null) {
                this.cdmaOtaScreenState = new OtaUtils.CdmaOtaScreenState();
            }
            if (this.cdmaOtaInCallScreenUiState == null) {
                this.cdmaOtaInCallScreenUiState = new OtaUtils.CdmaOtaInCallScreenUiState();
            }
        } else {
            clearOtaState();
        }
        this.ringer.updateRingerContextAfterRadioTechnologyChange(this.phone);
        this.notifier.updateCallNotifierRegistrationsAfterRadioTechnologyChange();
        this.callStateMonitor.updateAfterRadioTechnologyChange();
        if (this.mBluetoothPhone != null) {
            try {
                this.mBluetoothPhone.updateBtHandsfreeAfterRadioTechnologyChange();
            } catch (RemoteException e) {
                Log.e("PhoneApp", Log.getStackTraceString(new Throwable()));
            }
        }
        if (this.phone.getIccCard() == null || !DBG) {
            return;
        }
        Log.d("PhoneApp", "Update registration for ICC status...");
    }

    @Override // com.android.phone.WiredHeadsetManager.WiredHeadsetListener
    public void onWiredHeadsetConnection(boolean pluggedIn) {
        this.mCM.getState();
        if (this.mTtyEnabled) {
            this.mHandler.sendMessage(this.mHandler.obtainMessage(14, 0));
        }
        if (pluggedIn) {
            this.notificationMgr.showHeadSetPlugin();
        } else {
            this.notificationMgr.cancelHeadSetPlugin();
        }
    }

    protected class PhoneAppBroadcastReceiver extends BroadcastReceiver {
        protected PhoneAppBroadcastReceiver() {
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("android.intent.action.AIRPLANE_MODE")) {
                boolean z = Settings.System.getInt(PhoneGlobals.this.getContentResolver(), "airplane_mode_on", 0) == 0;
                Log.d("PhoneApp", "Setting property persist.radio.airplane_mode_on");
                SystemProperties.set("persist.radio.airplane_mode_on", z ? "0" : "1");
                PhoneGlobals.this.phone.setRadioPower(z);
                return;
            }
            if (action.equals("android.intent.action.ANY_DATA_STATE")) {
                Log.d("PhoneApp", "mReceiver: ACTION_ANY_DATA_CONNECTION_STATE_CHANGED");
                Log.d("PhoneApp", "- state: " + intent.getStringExtra("state"));
                Log.d("PhoneApp", "- reason: " + intent.getStringExtra("reason"));
                Log.d("PhoneApp", "- subscription: " + intent.getIntExtra("subscription", PhoneGlobals.this.getDefaultSubscription()));
                PhoneGlobals.this.mHandler.sendEmptyMessage(!PhoneGlobals.this.phone.getDataRoamingEnabled() && "DISCONNECTED".equals(intent.getStringExtra("state")) && "roamingOn".equals(intent.getStringExtra("reason")) ? 10 : 11);
                return;
            }
            if (action.equals("android.intent.action.SIM_STATE_CHANGED") && PhoneGlobals.this.mPUKEntryActivity != null) {
                PhoneGlobals.this.mHandler.sendMessage(PhoneGlobals.this.mHandler.obtainMessage(8, intent.getStringExtra("ss")));
                return;
            }
            if (action.equals("android.intent.action.RADIO_TECHNOLOGY")) {
                Log.d("PhoneApp", "Radio technology switched. Now " + intent.getStringExtra("phoneName") + " is active.");
                PhoneGlobals.this.initForNewRadioTechnology();
                return;
            }
            if (action.equals("android.intent.action.SERVICE_STATE")) {
                PhoneGlobals.this.handleServiceStateChanged(intent);
                return;
            }
            if (action.equals("android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED")) {
                boolean booleanExtra = intent.getBooleanExtra("ims_phone", false);
                if (booleanExtra) {
                    PhoneGlobals.this.mImsPhone = PhoneUtils.getImsPhone(PhoneGlobals.getInstance().mCM);
                }
                if (TelephonyCapabilities.supportsEcm(PhoneGlobals.this.phone) || TelephonyCapabilities.supportsEcm(PhoneGlobals.this.mImsPhone)) {
                    Log.d("PhoneApp", "Emergency Callback Mode arrived in PhoneApp.");
                    if (intent.getBooleanExtra("phoneinECMState", false)) {
                        context.startService(new Intent(context, (Class<?>) EmergencyCallbackModeService.class).putExtra("ims_phone", booleanExtra));
                        return;
                    }
                    return;
                }
                Log.e("PhoneApp", "Got ACTION_EMERGENCY_CALLBACK_MODE_CHANGED, but ECM isn't supported for phone: " + PhoneGlobals.this.phone.getPhoneName());
                return;
            }
            if (action.equals("android.intent.action.DOCK_EVENT")) {
                PhoneGlobals.mDockState = intent.getIntExtra("android.intent.extra.DOCK_STATE", 0);
                Log.d("PhoneApp", "ACTION_DOCK_EVENT -> mDockState = " + PhoneGlobals.mDockState);
                PhoneGlobals.this.mHandler.sendMessage(PhoneGlobals.this.mHandler.obtainMessage(13, 0));
            } else {
                if (action.equals("com.android.internal.telephony.cdma.intent.action.TTY_PREFERRED_MODE_CHANGE")) {
                    PhoneGlobals.this.mPreferredTtyMode = intent.getIntExtra("ttyPreferredMode", 0);
                    Log.d("PhoneApp", "mReceiver: TTY_PREFERRED_MODE_CHANGE_ACTION");
                    Log.d("PhoneApp", "    mode: " + PhoneGlobals.this.mPreferredTtyMode);
                    PhoneGlobals.this.mHandler.sendMessage(PhoneGlobals.this.mHandler.obtainMessage(14, 0));
                    return;
                }
                if (action.equals("android.media.RINGER_MODE_CHANGED") && intent.getIntExtra("android.media.EXTRA_RINGER_MODE", 2) == 0) {
                    PhoneGlobals.this.notifier.silenceRinger();
                }
            }
        }
    }

    protected class MediaButtonBroadcastReceiver extends BroadcastReceiver {
        protected MediaButtonBroadcastReceiver() {
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            KeyEvent event = (KeyEvent) intent.getParcelableExtra("android.intent.extra.KEY_EVENT");
            Log.d("PhoneApp", "MediaButtonBroadcastReceiver.onReceive()...  event = " + event);
            if (event != null && event.getKeyCode() == 79) {
                Log.d("PhoneApp", "MediaButtonBroadcastReceiver: HEADSETHOOK");
                boolean consumed = PhoneUtils.handleHeadsetHook(PhoneGlobals.this.phone, event);
                Log.d("PhoneApp", "==> handleHeadsetHook(): consumed = " + consumed);
                if (consumed) {
                    abortBroadcast();
                    return;
                }
                return;
            }
            if (PhoneGlobals.this.mCM.getState() != PhoneConstants.State.IDLE) {
                Log.d("PhoneApp", "MediaButtonBroadcastReceiver: consumed");
                abortBroadcast();
            }
        }
    }

    public static class NotificationBroadcastReceiver extends BroadcastReceiver {
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d("PhoneApp", "Broadcast from Notification: " + action);
            if (action.equals("com.android.phone.ACTION_HANG_UP_ONGOING_CALL")) {
                PhoneUtils.hangup(PhoneGlobals.getInstance().mCM);
                return;
            }
            if (action.equals("com.android.phone.ACTION_CALL_BACK_FROM_NOTIFICATION")) {
                closeSystemDialogs(context);
                clearMissedCallNotification(context);
                Intent callIntent = new Intent("android.intent.action.CALL_PRIVILEGED", intent.getData());
                callIntent.setFlags(276824064);
                context.startActivity(callIntent);
                return;
            }
            if (action.equals("com.android.phone.ACTION_SEND_SMS_FROM_NOTIFICATION")) {
                closeSystemDialogs(context);
                clearMissedCallNotification(context);
                Intent smsIntent = new Intent("android.intent.action.SENDTO", intent.getData());
                smsIntent.addFlags(268435456);
                context.startActivity(smsIntent);
                return;
            }
            Log.w("PhoneApp", "Received hang-up request from notification, but there's no call the system can hang up.");
        }

        private void closeSystemDialogs(Context context) {
            Intent intent = new Intent("android.intent.action.CLOSE_SYSTEM_DIALOGS");
            context.sendBroadcastAsUser(intent, UserHandle.ALL);
        }

        private void clearMissedCallNotification(Context context) {
            Intent intent = new Intent(context, (Class<?>) ClearMissedCallsService.class);
            intent.setAction("com.android.phone.intent.CLEAR_MISSED_CALLS");
            context.startService(intent);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleServiceStateChanged(Intent intent) {
        ServiceState ss = ServiceState.newFromBundle(intent.getExtras());
        if (ss != null) {
            int state = ss.getState();
            this.notificationMgr.updateNetworkSelection(state, this.phone);
        }
    }

    public boolean isOtaCallInActiveState() {
        Log.d("PhoneApp", "- isOtaCallInActiveState false");
        return false;
    }

    public void clearOtaState() {
        if (DBG) {
            Log.d("PhoneApp", "- clearOtaState ...");
        }
        if (this.otaUtils != null) {
            this.otaUtils.cleanOtaScreen(true);
            if (DBG) {
                Log.d("PhoneApp", "  - clearOtaState clears OTA screen");
            }
        }
    }

    public void dismissOtaDialogs() {
        if (DBG) {
            Log.d("PhoneApp", "- dismissOtaDialogs ...");
        }
        if (this.otaUtils != null) {
            this.otaUtils.dismissAllOtaDialogs();
            if (DBG) {
                Log.d("PhoneApp", "  - dismissOtaDialogs clears OTA dialogs");
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleQueryTTYModeResponse(Message message) {
        String str;
        AsyncResult asyncResult = (AsyncResult) message.obj;
        if (asyncResult.exception != null) {
            if (DBG) {
                Log.d("PhoneApp", "handleQueryTTYModeResponse: Error getting TTY state.");
                return;
            }
            return;
        }
        if (DBG) {
            Log.d("PhoneApp", "handleQueryTTYModeResponse: TTY enable state successfully queried.");
        }
        int i = ((int[]) asyncResult.result)[0];
        if (DBG) {
            Log.d("PhoneApp", "handleQueryTTYModeResponse:ttymode=" + i);
        }
        Intent intent = new Intent("com.android.internal.telephony.cdma.intent.action.TTY_ENABLED_CHANGE");
        intent.putExtra("ttyEnabled", i != 0);
        sendBroadcastAsUser(intent, UserHandle.ALL);
        switch (i) {
            case 1:
                str = "tty_full";
                break;
            case 2:
                str = "tty_hco";
                break;
            case 3:
                str = "tty_vco";
                break;
            default:
                str = "tty_off";
                break;
        }
        ((AudioManager) getSystemService("audio")).setParameters("tty_mode=" + str);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleSetTTYModeResponse(Message message) {
        AsyncResult asyncResult = (AsyncResult) message.obj;
        if (asyncResult.exception != null && DBG) {
            Log.d("PhoneApp", "handleSetTTYModeResponse: Error setting TTY mode, ar.exception" + asyncResult.exception);
        }
        this.phone.queryTTYMode(this.mHandler.obtainMessage(15));
    }

    public int getDefaultSubscription() {
        return 0;
    }

    public int getVoiceSubscription() {
        return 0;
    }

    public int getVoiceSubscriptionInService() {
        return 0;
    }

    public int getDataSubscription() {
        return 0;
    }

    public void showCallDuration(long j) {
        if (this.mCallDurationDialog != null) {
            this.mCallDurationDialog.dismiss();
            this.mCallDurationDialog = null;
        }
        long j2 = j / 1000;
        long j3 = 0;
        if (j2 >= 60) {
            j3 = j2 / 60;
            j2 -= j3 * 60;
        }
        this.mCallDurationDialog = new AlertDialog.Builder(this).setTitle(R.string.title_dialog_duration).setMessage(getString(R.string.duration_format, new Object[]{Long.valueOf(j3), Long.valueOf(j2)})).create();
        this.mCallDurationDialog.getWindow().setType(2008);
        this.mCallDurationDialog.show();
        new Handler().postDelayed(new Runnable() { // from class: com.android.phone.PhoneGlobals.9
            @Override // java.lang.Runnable
            public void run() {
                if (PhoneGlobals.this.mCallDurationDialog != null) {
                    PhoneGlobals.this.mCallDurationDialog.dismiss();
                    PhoneGlobals.this.mCallDurationDialog = null;
                }
            }
        }, 1000L);
    }

    public int getDefaultDataSubscription() {
        return 0;
    }
}
