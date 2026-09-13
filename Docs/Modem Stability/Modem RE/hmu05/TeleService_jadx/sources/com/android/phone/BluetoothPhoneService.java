package com.android.phone;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.IBluetoothHeadsetPhone;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.telephony.MSimTelephonyManager;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import java.util.LinkedList;
import java.util.List;
import org.codeaurora.btmultisim.IBluetoothDsdaService;

/* JADX INFO: loaded from: classes.dex */
public class BluetoothPhoneService extends Service {
    private static final boolean DBG;
    private BluetoothAdapter mAdapter;
    private BluetoothHeadset mBluetoothHeadset;
    private CallManager mCM;
    private CallGatewayManager mCallGatewayManager;
    private long[] mClccTimestamps;
    private boolean[] mClccUsed;
    private Call.State mForegroundCallState;
    int mNumActive;
    int mNumHeld;
    private PowerManager mPowerManager;
    private CallNumber mRingNumber;
    private Call.State mRingingCallState;
    private PowerManager.WakeLock mStartCallWakeLock;
    private PhoneConstants.State mPhoneState = PhoneConstants.State.IDLE;
    CdmaPhoneCallState.PhoneCallState mCdmaThreeWayCallState = CdmaPhoneCallState.PhoneCallState.IDLE;
    private IBluetoothDsdaService mBluetoothDsda = null;
    long mBgndEarliestConnectionTime = 0;
    private boolean mCdmaIsSecondCallActive = false;
    private boolean mCdmaCallsSwapped = false;
    private ServiceConnection btMultiSimServiceConnection = new ServiceConnection() { // from class: com.android.phone.BluetoothPhoneService.1
        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothPhoneService.this.mBluetoothDsda = IBluetoothDsdaService.Stub.asInterface(service);
            Log.d("BluetoothPhoneService", "Dsda Service Connected" + BluetoothPhoneService.this.mBluetoothDsda);
            if (BluetoothPhoneService.this.mBluetoothDsda != null) {
                Log.e("BluetoothPhoneService", "IBluetoothDsdaService created");
                if (BluetoothPhoneService.this.isDsdaEnabled()) {
                    BluetoothPhoneService.this.handlePreciseCallStateChange(null);
                    return;
                }
                return;
            }
            Log.e("BluetoothPhoneService", "IBluetoothDsdaService Error");
        }

        @Override // android.content.ServiceConnection
        public void onServiceDisconnected(ComponentName arg0) {
            Log.w("BluetoothPhoneService", "DSDA Service onServiceDisconnected");
            BluetoothPhoneService.this.mBluetoothDsda = null;
        }
    };
    private Handler mHandler = new Handler() { // from class: com.android.phone.BluetoothPhoneService.2
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            Log.d("BluetoothPhoneService", "handleMessage: " + msg.what);
            switch (msg.what) {
                case 1:
                    Connection connection = null;
                    if (((AsyncResult) msg.obj).result instanceof Connection) {
                        connection = (Connection) ((AsyncResult) msg.obj).result;
                    }
                    if (BluetoothPhoneService.this.isDsdaEnabled() && BluetoothPhoneService.this.mBluetoothDsda != null) {
                        if (!(((AsyncResult) msg.obj).result instanceof PhoneBase)) {
                            BluetoothPhoneService.log("No PhoneBase object found");
                        } else {
                            PhoneBase pb = (PhoneBase) ((AsyncResult) msg.obj).result;
                            int subscription = pb.getSubscription();
                            BluetoothPhoneService.log("SUB on which it happned: " + subscription);
                            try {
                                BluetoothPhoneService.this.mBluetoothDsda.setCurrentSub(subscription);
                                for (Phone phone : BluetoothPhoneService.this.mCM.getAllPhones()) {
                                    if (phone != null && phone.getSubscription() == subscription) {
                                        int mPhonetype = phone.getPhoneType();
                                        if (mPhonetype == 2) {
                                            Log.d("BluetoothPhoneService", "CDMA.Update held calls on this SUB");
                                            BluetoothPhoneService.this.mBluetoothDsda.updateCdmaHeldCall(BluetoothPhoneService.this.getNumHeldCdma());
                                        } else {
                                            continue;
                                        }
                                    }
                                }
                            } catch (RemoteException e) {
                                Log.w("BluetoothPhoneService", "mBluetoothDsda class not found exception " + e);
                                return;
                            }
                        }
                    }
                    BluetoothPhoneService.this.handlePreciseCallStateChange(connection);
                    break;
                case 2:
                    Connection conn = null;
                    if (BluetoothPhoneService.this.isDsdaEnabled() && BluetoothPhoneService.this.mBluetoothDsda != null) {
                        AsyncResult ar = (AsyncResult) msg.obj;
                        int subscription2 = ((Integer) ar.userObj).intValue();
                        BluetoothPhoneService.log("CDMA call waiting on sub: " + subscription2);
                        conn = BluetoothPhoneService.this.mCM.getFirstActiveRingingCall(subscription2).getLatestConnection();
                        try {
                            BluetoothPhoneService.this.mBluetoothDsda.setCurrentSub(subscription2);
                            BluetoothPhoneService.this.mBluetoothDsda.updateCdmaHeldCall(BluetoothPhoneService.this.getNumHeldCdma());
                        } catch (RemoteException e2) {
                            Log.w("BluetoothPhoneService", "Class not found exception " + e2);
                            return;
                        }
                    } else if (((AsyncResult) msg.obj).result instanceof Connection) {
                        conn = (Connection) ((AsyncResult) msg.obj).result;
                    }
                    BluetoothPhoneService.this.handlePreciseCallStateChange(conn);
                    break;
                case 3:
                    BluetoothPhoneService.this.handleListCurrentCalls();
                    break;
                case 4:
                    BluetoothPhoneService.this.handleQueryPhoneState();
                    break;
                case 5:
                    BluetoothPhoneService.this.handleCdmaSwapSecondCallState();
                    break;
                case 6:
                    BluetoothPhoneService.this.handleCdmaSetSecondCallState(((Boolean) msg.obj).booleanValue());
                    break;
                case 7:
                    Connection connection2 = null;
                    if (((AsyncResult) msg.obj).result instanceof Connection) {
                        connection2 = (Connection) ((AsyncResult) msg.obj).result;
                    }
                    if (BluetoothPhoneService.this.isDsdaEnabled() && BluetoothPhoneService.this.mBluetoothDsda != null) {
                        if (!(((AsyncResult) msg.obj).result instanceof PhoneBase)) {
                            BluetoothPhoneService.log("No PhoneBase object found");
                        } else {
                            PhoneBase pb2 = (PhoneBase) ((AsyncResult) msg.obj).result;
                            int subscription3 = pb2.getSubscription();
                            BluetoothPhoneService.log("SUB on which it happned: " + subscription3);
                            try {
                                BluetoothPhoneService.this.mBluetoothDsda.setCurrentSub(subscription3);
                                for (Phone phone2 : BluetoothPhoneService.this.mCM.getAllPhones()) {
                                    if (phone2 != null && phone2.getSubscription() == subscription3) {
                                        int mPhonetype2 = phone2.getPhoneType();
                                        if (mPhonetype2 == 2) {
                                            Log.d("BluetoothPhoneService", "CDMA. Update held calls on this SUB");
                                            BluetoothPhoneService.this.mBluetoothDsda.updateCdmaHeldCall(BluetoothPhoneService.this.getNumHeldCdma());
                                        } else {
                                            continue;
                                        }
                                    }
                                }
                            } catch (RemoteException e3) {
                                Log.w("BluetoothPhoneService", " mBluetoothDsda class not found exception " + e3);
                                return;
                            }
                        }
                    }
                    BluetoothPhoneService.this.handlePreciseCallStateChange(connection2);
                    break;
                case 8:
                    if (BluetoothPhoneService.this.isDsdaEnabled() && BluetoothPhoneService.this.mBluetoothDsda != null) {
                        try {
                            BluetoothPhoneService.this.mBluetoothDsda.phoneSubChanged();
                        } catch (RemoteException e4) {
                            Log.w("BluetoothPhoneService", "DSDA class not found exception " + e4);
                            return;
                        }
                        break;
                    }
                    break;
            }
        }
    };
    private BluetoothProfile.ServiceListener mProfileListener = new BluetoothProfile.ServiceListener() { // from class: com.android.phone.BluetoothPhoneService.3
        @Override // android.bluetooth.BluetoothProfile.ServiceListener
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            BluetoothPhoneService.this.mBluetoothHeadset = (BluetoothHeadset) proxy;
        }

        @Override // android.bluetooth.BluetoothProfile.ServiceListener
        public void onServiceDisconnected(int profile) {
            BluetoothPhoneService.this.mBluetoothHeadset = null;
        }
    };
    private final BroadcastReceiver mCsvtCallStateReceiver = new BroadcastReceiver() { // from class: com.android.phone.BluetoothPhoneService.4
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("intent.action.CSVT_PRECISE_CALL_STATE_CHANGED".equals(action)) {
                BluetoothPhoneService.this.handlePreciseCallStateChange(null);
            }
        }
    };
    private final IBluetoothHeadsetPhone.Stub mBinder = new IBluetoothHeadsetPhone.Stub() { // from class: com.android.phone.BluetoothPhoneService.5
        public boolean answerCall() {
            BluetoothPhoneService.this.enforceCallingOrSelfPermission("android.permission.MODIFY_PHONE_STATE", null);
            return PhoneUtils.isImsVideoCall(BluetoothPhoneService.this.mCM.getFirstActiveRingingCall()) ? BluetoothPhoneService.this.answerCsvtCall() : PhoneUtils.answerCall(BluetoothPhoneService.this.mCM.getFirstActiveRingingCall());
        }

        public boolean hangupCall() {
            BluetoothPhoneService.this.enforceCallingOrSelfPermission("android.permission.MODIFY_PHONE_STATE", null);
            if (BluetoothPhoneService.this.mCM.hasActiveFgCall()) {
                return PhoneUtils.isImsVideoCall(BluetoothPhoneService.this.mCM.getActiveFgCall()) ? BluetoothPhoneService.this.hangupCsvtCall() : PhoneUtils.hangupActiveCall(BluetoothPhoneService.this.mCM.getActiveFgCall());
            }
            if (BluetoothPhoneService.this.mCM.hasActiveRingingCall()) {
                return PhoneUtils.isImsVideoCall(BluetoothPhoneService.this.mCM.getFirstActiveRingingCall()) ? BluetoothPhoneService.this.hangupCsvtCall() : PhoneUtils.hangupRingingCall(BluetoothPhoneService.this.mCM.getFirstActiveRingingCall());
            }
            if (BluetoothPhoneService.this.mCM.hasActiveBgCall()) {
                return PhoneUtils.hangupHoldingCall(BluetoothPhoneService.this.mCM.getFirstActiveBgCall());
            }
            return false;
        }

        public boolean sendDtmf(int dtmf) {
            BluetoothPhoneService.this.enforceCallingOrSelfPermission("android.permission.MODIFY_PHONE_STATE", null);
            return BluetoothPhoneService.this.mCM.sendDtmf((char) dtmf);
        }

        public boolean processChld(int chld) {
            BluetoothPhoneService.this.enforceCallingOrSelfPermission("android.permission.MODIFY_PHONE_STATE", null);
            if (BluetoothPhoneService.this.isDsdaEnabled() && BluetoothPhoneService.this.mBluetoothDsda != null) {
                try {
                    return BluetoothPhoneService.this.processDsdaChld(chld);
                } catch (RemoteException e) {
                    Log.e("BluetoothPhoneService", " BluetoothDsdaService class not found exception " + e);
                }
            }
            Phone phone = BluetoothPhoneService.this.mCM.getPhoneInCall();
            int phoneType = phone.getPhoneType();
            BluetoothPhoneService.log("processChld: " + chld + " for Phone type: " + phoneType);
            Call ringingCall = BluetoothPhoneService.this.mCM.getFirstActiveRingingCall();
            Call backgroundCall = BluetoothPhoneService.this.mCM.getFirstActiveBgCall();
            if (chld == 0) {
                if (ringingCall.isRinging()) {
                    return PhoneUtils.hangupRingingCall(ringingCall);
                }
                return PhoneUtils.hangupHoldingCall(backgroundCall);
            }
            if (chld == 1) {
                if (phoneType == 2) {
                    if (ringingCall.isRinging()) {
                        BluetoothPhoneService.log("CHLD:1 Callwaiting Answer call");
                        PhoneUtils.hangupRingingAndActive(phone);
                        return true;
                    }
                    BluetoothPhoneService.log("CHLD:1 Hangup Call");
                    PhoneUtils.hangup(PhoneGlobals.getInstance().mCM);
                    return true;
                }
                if (phoneType == 1) {
                    if (ringingCall.isRinging() && BluetoothPhoneService.this.mNumHeld > 0 && BluetoothPhoneService.this.mNumActive == 0) {
                        BluetoothPhoneService.log("CHLD:1 Answer the Call");
                        return PhoneUtils.answerCall(ringingCall);
                    }
                    return PhoneUtils.answerAndEndActive(PhoneGlobals.getInstance().mCM, ringingCall);
                }
                Log.e("BluetoothPhoneService", "bad phone type: " + phoneType);
                return false;
            }
            if (chld == 2) {
                if (phoneType == 2) {
                    if (ringingCall.isRinging()) {
                        BluetoothPhoneService.log("CHLD:2 Callwaiting Answer call");
                        PhoneUtils.answerCall(ringingCall);
                        PhoneUtils.setMute(false);
                        cdmaSetSecondCallState(true);
                        return true;
                    }
                    if (PhoneGlobals.getInstance().cdmaPhoneCallState.getCurrentCallState() == CdmaPhoneCallState.PhoneCallState.CONF_CALL) {
                        BluetoothPhoneService.log("CHLD:2 Swap Calls");
                        PhoneUtils.switchHoldingAndActive(backgroundCall);
                        cdmaSwapSecondCallState();
                        return true;
                    }
                    Log.e("BluetoothPhoneService", "CDMA fail to do hold active and accept held");
                    return false;
                }
                if (phoneType == 1) {
                    if (ringingCall.isRinging() && BluetoothPhoneService.this.mNumHeld > 0 && BluetoothPhoneService.this.mNumActive == 0) {
                        PhoneUtils.answerCall(ringingCall);
                        return true;
                    }
                    PhoneUtils.switchHoldingAndActive(backgroundCall);
                    return true;
                }
                Log.e("BluetoothPhoneService", "Unexpected phone type: " + phoneType);
                return false;
            }
            if (chld == 3) {
                if (phoneType == 2) {
                    CdmaPhoneCallState.PhoneCallState state = PhoneGlobals.getInstance().cdmaPhoneCallState.getCurrentCallState();
                    if (state == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE) {
                        BluetoothPhoneService.log("CHLD:3 Merge Calls");
                        PhoneUtils.mergeCalls();
                        return true;
                    }
                    if (state == CdmaPhoneCallState.PhoneCallState.CONF_CALL) {
                        return false;
                    }
                    Log.e("BluetoothPhoneService", "GSG no call to add conference");
                    return false;
                }
                if (phoneType == 1) {
                    BluetoothPhoneService.log("processChld fr CHLD = 3 for GSM, operate only on single sub");
                    if (BluetoothPhoneService.this.mCM.hasActiveFgCall() && BluetoothPhoneService.this.mCM.hasActiveBgCall()) {
                        PhoneUtils.mergeCalls();
                        return true;
                    }
                    Log.e("BluetoothPhoneService", "GSG no call to merge");
                    return false;
                }
                Log.e("BluetoothPhoneService", "Unexpected phone type: " + phoneType);
                return false;
            }
            Log.e("BluetoothPhoneService", "bad CHLD value: " + chld);
            return false;
        }

        public String getNetworkOperator() {
            BluetoothPhoneService.this.enforceCallingOrSelfPermission("android.permission.MODIFY_PHONE_STATE", null);
            if (BluetoothPhoneService.this.isDsdaEnabled()) {
                BluetoothPhoneService.log("getNetworkOperator for DSDA");
                int activeSub = BluetoothPhoneService.this.mCM.getActiveSubscription();
                return BluetoothPhoneService.this.mCM.getFgPhone(activeSub).getServiceState().getOperatorAlphaLong();
            }
            return BluetoothPhoneService.this.mCM.getDefaultPhone().getServiceState().getOperatorAlphaLong();
        }

        public String getSubscriberNumber() {
            BluetoothPhoneService.this.enforceCallingOrSelfPermission("android.permission.MODIFY_PHONE_STATE", null);
            if (BluetoothPhoneService.this.isDsdaEnabled()) {
                BluetoothPhoneService.log("getSubscriberNumber for DSDA");
                int activeSub = BluetoothPhoneService.this.mCM.getActiveSubscription();
                Phone phone = MSimPhoneGlobals.getInstance().getPhone(activeSub);
                return phone.getLine1Number();
            }
            return BluetoothPhoneService.this.mCM.getDefaultPhone().getLine1Number();
        }

        public boolean listCurrentCalls() {
            BluetoothPhoneService.this.enforceCallingOrSelfPermission("android.permission.MODIFY_PHONE_STATE", null);
            Message msg = Message.obtain(BluetoothPhoneService.this.mHandler, 3);
            BluetoothPhoneService.this.mHandler.sendMessage(msg);
            return true;
        }

        public boolean queryPhoneState() {
            BluetoothPhoneService.this.enforceCallingOrSelfPermission("android.permission.MODIFY_PHONE_STATE", null);
            Message msg = Message.obtain(BluetoothPhoneService.this.mHandler, 4);
            BluetoothPhoneService.this.mHandler.sendMessage(msg);
            return true;
        }

        public void updateBtHandsfreeAfterRadioTechnologyChange() {
            BluetoothPhoneService.this.enforceCallingOrSelfPermission("android.permission.MODIFY_PHONE_STATE", null);
            Log.d("BluetoothPhoneService", "updateBtHandsfreeAfterRadioTechnologyChange...");
            BluetoothPhoneService.this.updateBtPhoneStateAfterRadioTechnologyChange();
        }

        public void cdmaSwapSecondCallState() {
            BluetoothPhoneService.this.enforceCallingOrSelfPermission("android.permission.MODIFY_PHONE_STATE", null);
            Message msg = Message.obtain(BluetoothPhoneService.this.mHandler, 5);
            BluetoothPhoneService.this.mHandler.sendMessage(msg);
        }

        public void cdmaSetSecondCallState(boolean state) {
            BluetoothPhoneService.this.enforceCallingOrSelfPermission("android.permission.MODIFY_PHONE_STATE", null);
            Message msg = BluetoothPhoneService.this.mHandler.obtainMessage(6, Boolean.valueOf(state));
            BluetoothPhoneService.this.mHandler.sendMessage(msg);
        }
    };

    static {
        DBG = SystemProperties.getInt("ro.debuggable", 0) == 1;
    }

    @Override // android.app.Service
    public void onCreate() {
        int i = 0;
        super.onCreate();
        this.mCM = CallManager.getInstance();
        this.mAdapter = BluetoothAdapter.getDefaultAdapter();
        if (this.mAdapter == null) {
            Log.d("BluetoothPhoneService", "mAdapter null");
            return;
        }
        this.mCallGatewayManager = CallGatewayManager.getInstance();
        this.mPowerManager = (PowerManager) getSystemService("power");
        this.mStartCallWakeLock = this.mPowerManager.newWakeLock(1, "BluetoothPhoneService:StartCall");
        this.mStartCallWakeLock.setReferenceCounted(false);
        this.mAdapter.getProfileProxy(this, this.mProfileListener, 1);
        this.mForegroundCallState = Call.State.IDLE;
        this.mRingingCallState = Call.State.IDLE;
        this.mNumActive = 0;
        this.mNumHeld = 0;
        this.mRingNumber = new CallNumber("", i);
        handlePreciseCallStateChange(null);
        Log.d("BluetoothPhoneService", "registerForServiceStateChanged");
        Log.d("BluetoothPhoneService", "registerForPreciseCallStateChanged start");
        this.mCM.registerForPreciseCallStateChanged(this.mHandler, 1, (Object) null);
        CallManager callManager = this.mCM;
        if (CallManager.isCallOnCsvtEnabled()) {
            IntentFilter filter = new IntentFilter();
            filter.addAction("intent.action.CSVT_PRECISE_CALL_STATE_CHANGED");
            PhoneGlobals.getInstance().registerReceiver(this.mCsvtCallStateReceiver, filter);
        }
        for (Phone phone : this.mCM.getAllPhones()) {
            if (phone.getPhoneType() == 2) {
                log("register for cdma call waiting " + phone.getSubscription());
                this.mCM.registerForCallWaiting(this.mHandler, 2, Integer.valueOf(phone.getSubscription()));
                break;
            }
        }
        if (isDsdaEnabled()) {
            this.mCM.registerForSubscriptionChange(this.mHandler, 8, (Object) null);
        }
        this.mCM.registerForDisconnect(this.mHandler, 7, (Object) null);
        this.mClccTimestamps = new long[6];
        this.mClccUsed = new boolean[6];
        for (int i2 = 0; i2 < 6; i2++) {
            this.mClccUsed[i2] = false;
        }
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            Log.d("BluetoothPhoneService", "DSDA is enabled, Bind to DSDA service");
            createBTMultiSimService();
        }
    }

    @Override // android.app.Service
    public void onStart(Intent intent, int startId) {
        if (this.mAdapter == null) {
            Log.w("BluetoothPhoneService", "Stopping Bluetooth BluetoothPhoneService Service: device does not have BT");
            stopSelf();
        }
        Log.d("BluetoothPhoneService", "BluetoothPhoneService started");
    }

    @Override // android.app.Service
    public void onDestroy() {
        super.onDestroy();
        CallManager callManager = this.mCM;
        if (CallManager.isCallOnCsvtEnabled()) {
            PhoneGlobals.getInstance().unregisterReceiver(this.mCsvtCallStateReceiver);
        }
        if (DBG) {
            log("Stopping Bluetooth BluetoothPhoneService Service");
        }
    }

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return this.mBinder;
    }

    private void createBTMultiSimService() {
        try {
            boolean bound = bindService(new Intent("org.codeaurora.btmultisim.IBluetoothDsdaService"), this.btMultiSimServiceConnection, 1);
            Log.d("BluetoothPhoneService", "IBluetoothDsdaService bound request : " + bound);
        } catch (NoClassDefFoundError e) {
            Log.w("BluetoothPhoneService", "Ignoring IBluetoothDsdaService class not found exception " + e);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isDsdaEnabled() {
        if (MSimTelephonyManager.getDefault().getMultiSimConfiguration() != MSimTelephonyManager.MultiSimVariants.DSDA) {
            return false;
        }
        Log.d("BluetoothPhoneService", "DSDA is enabled");
        return true;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateBtPhoneStateAfterRadioTechnologyChange() {
        Log.d("BluetoothPhoneService", "updateBtPhoneStateAfterRadioTechnologyChange...");
        this.mCM.unregisterForPreciseCallStateChanged(this.mHandler);
        this.mCM.unregisterForCallWaiting(this.mHandler);
        if (isDsdaEnabled()) {
            this.mCM.unregisterForSubscriptionChange(this.mHandler);
        }
        this.mCM.registerForPreciseCallStateChanged(this.mHandler, 1, (Object) null);
        for (Phone phone : this.mCM.getAllPhones()) {
            if (phone.getPhoneType() == 2) {
                log("register for cdma call waiting " + phone.getSubscription());
                this.mCM.registerForCallWaiting(this.mHandler, 2, Integer.valueOf(phone.getSubscription()));
                break;
            }
        }
        if (isDsdaEnabled()) {
            this.mCM.registerForSubscriptionChange(this.mHandler, 8, (Object) null);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handlePreciseCallStateChange(Connection connection) {
        boolean callsSwitched;
        if (isDsdaEnabled()) {
            Log.d("BluetoothPhoneService", "DSDA call operation, handle it separately");
            if (this.mBluetoothDsda != null) {
                try {
                    updateCdmaCallStates();
                    this.mBluetoothDsda.handleMultiSimPreciseCallStateChange();
                    return;
                } catch (RemoteException e) {
                    Log.w("BluetoothPhoneService", "Ignoring DSDA class not found exception " + e);
                    return;
                }
            }
            return;
        }
        int oldNumActive = this.mNumActive;
        int oldNumHeld = this.mNumHeld;
        Call.State oldRingingCallState = this.mRingingCallState;
        Call.State oldForegroundCallState = this.mForegroundCallState;
        CallNumber oldRingNumber = this.mRingNumber;
        Call foregroundCall = this.mCM.getActiveFgCall();
        Log.d("BluetoothPhoneService", " handlePreciseCallStateChange: foreground: " + foregroundCall + " background: " + this.mCM.getFirstActiveBgCall() + " ringing: " + this.mCM.getFirstActiveRingingCall());
        this.mForegroundCallState = foregroundCall.getState();
        if (this.mForegroundCallState == Call.State.DISCONNECTING) {
            Log.d("BluetoothPhoneService", "handlePreciseCallStateChange. Call disconnecting, wait before update");
            return;
        }
        this.mNumActive = this.mForegroundCallState == Call.State.ACTIVE ? 1 : 0;
        Call ringingCall = this.mCM.getFirstActiveRingingCall();
        this.mRingingCallState = ringingCall.getState();
        this.mRingNumber = getCallNumber(connection, ringingCall);
        if (this.mCM.getPhoneInCall().getPhoneType() == 2) {
            this.mNumHeld = getNumHeldCdma();
            PhoneGlobals app = PhoneGlobals.getInstance();
            if (app.cdmaPhoneCallState != null) {
                CdmaPhoneCallState.PhoneCallState currCdmaThreeWayCallState = app.cdmaPhoneCallState.getCurrentCallState();
                CdmaPhoneCallState.PhoneCallState prevCdmaThreeWayCallState = app.cdmaPhoneCallState.getPreviousCallState();
                log("CDMA call state: " + currCdmaThreeWayCallState + " prev state:" + prevCdmaThreeWayCallState);
                if (this.mBluetoothHeadset != null && this.mCdmaThreeWayCallState != currCdmaThreeWayCallState) {
                    log("CDMA 3way call state change. mNumActive: " + this.mNumActive + " mNumHeld: " + this.mNumHeld + " IsThreeWayCallOrigStateDialing: " + app.cdmaPhoneCallState.IsThreeWayCallOrigStateDialing());
                    if (currCdmaThreeWayCallState == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE && app.cdmaPhoneCallState.IsThreeWayCallOrigStateDialing()) {
                        this.mBluetoothHeadset.phoneStateChanged(0, this.mNumHeld, convertCallState(Call.State.IDLE, Call.State.DIALING), this.mRingNumber.mNumber, this.mRingNumber.mType);
                        this.mBluetoothHeadset.phoneStateChanged(0, this.mNumHeld, convertCallState(Call.State.IDLE, Call.State.ALERTING), this.mRingNumber.mNumber, this.mRingNumber.mType);
                    }
                    if (currCdmaThreeWayCallState == CdmaPhoneCallState.PhoneCallState.CONF_CALL && prevCdmaThreeWayCallState == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE) {
                        log("CDMA 3way conf call. mNumActive: " + this.mNumActive + " mNumHeld: " + this.mNumHeld);
                        this.mBluetoothHeadset.phoneStateChanged(this.mNumActive, this.mNumHeld, convertCallState(Call.State.IDLE, this.mForegroundCallState), this.mRingNumber.mNumber, this.mRingNumber.mType);
                    }
                }
                this.mCdmaThreeWayCallState = currCdmaThreeWayCallState;
            }
        } else {
            this.mNumHeld = getNumHeldUmts();
        }
        if (this.mCM.getPhoneInCall().getPhoneType() == 2 && this.mCdmaThreeWayCallState == CdmaPhoneCallState.PhoneCallState.CONF_CALL) {
            callsSwitched = this.mCdmaCallsSwapped;
        } else {
            Call backgroundCall = this.mCM.getFirstActiveBgCall();
            callsSwitched = this.mNumHeld == 1 && backgroundCall.getEarliestConnectTime() != this.mBgndEarliestConnectionTime;
            this.mBgndEarliestConnectionTime = backgroundCall.getEarliestConnectTime();
        }
        log("update the call states, active: " + this.mNumActive + "held" + this.mNumHeld);
        if ((this.mNumActive != oldNumActive || this.mNumHeld != oldNumHeld || this.mRingingCallState != oldRingingCallState || this.mForegroundCallState != oldForegroundCallState || !this.mRingNumber.equalTo(oldRingNumber) || callsSwitched) && this.mBluetoothHeadset != null) {
            log("update the headset");
            this.mBluetoothHeadset.phoneStateChanged(this.mNumActive, this.mNumHeld, convertCallState(this.mRingingCallState, this.mForegroundCallState), this.mRingNumber.mNumber, this.mRingNumber.mType);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleListCurrentCalls() {
        if (isDsdaEnabled()) {
            if (this.mBluetoothDsda != null) {
                try {
                    updateCdmaCallStates();
                    this.mBluetoothDsda.handleListCurrentCalls();
                    return;
                } catch (RemoteException e) {
                    Log.w("BluetoothPhoneService", "Ignoring DSDA class not found exception " + e);
                    return;
                }
            }
            return;
        }
        Phone phone = this.mCM.getPhoneInCall();
        int phoneType = phone.getPhoneType();
        if (phoneType == 2) {
            listCurrentCallsCdma();
        } else if (phoneType == 1) {
            listCurrentCallsGsm();
        } else {
            Log.e("BluetoothPhoneService", "Unexpected phone type: " + phoneType);
        }
        if (this.mBluetoothHeadset != null) {
            this.mBluetoothHeadset.clccResponse(0, 0, 0, 0, false, "", 0);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleQueryPhoneState() {
        if (isDsdaEnabled()) {
            if (this.mBluetoothDsda != null) {
                try {
                    this.mBluetoothDsda.processQueryPhoneState();
                    return;
                } catch (RemoteException e) {
                    Log.e("BluetoothPhoneService", "DSDA Service not found exception " + e);
                    return;
                }
            }
            return;
        }
        if (this.mBluetoothHeadset == null) {
            return;
        }
        this.mBluetoothHeadset.phoneStateChanged(this.mNumActive, this.mNumHeld, convertCallState(this.mRingingCallState, this.mForegroundCallState), this.mRingNumber.mNumber, this.mRingNumber.mType);
    }

    private int getNumHeldUmts() {
        int countHeld = 0;
        List<Call> heldCalls = this.mCM.getBackgroundCalls();
        for (Call call : heldCalls) {
            if (call.getState() == Call.State.HOLDING) {
                countHeld++;
            }
        }
        return countHeld;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int getNumHeldCdma() {
        PhoneGlobals app = PhoneGlobals.getInstance();
        if (app.cdmaPhoneCallState == null) {
            return 0;
        }
        CdmaPhoneCallState.PhoneCallState curr3WayCallState = app.cdmaPhoneCallState.getCurrentCallState();
        CdmaPhoneCallState.PhoneCallState prev3WayCallState = app.cdmaPhoneCallState.getPreviousCallState();
        log("CDMA call state: " + curr3WayCallState + " prev state:" + prev3WayCallState);
        if (curr3WayCallState == CdmaPhoneCallState.PhoneCallState.CONF_CALL) {
            if (prev3WayCallState == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE) {
                return 0;
            }
            return 1;
        }
        if (curr3WayCallState == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE) {
            return 1;
        }
        return 0;
    }

    private void updateCdmaCallStates() throws RemoteException {
        PhoneGlobals app = PhoneGlobals.getInstance();
        int currCallState = 0;
        int prevCallState = 0;
        if (app.cdmaPhoneCallState != null) {
            CdmaPhoneCallState.PhoneCallState curr3WayCallState = app.cdmaPhoneCallState.getCurrentCallState();
            CdmaPhoneCallState.PhoneCallState prev3WayCallState = app.cdmaPhoneCallState.getPreviousCallState();
            log("CDMA call state: " + curr3WayCallState + " prev state:" + prev3WayCallState);
            switch (curr3WayCallState) {
                case IDLE:
                    currCallState = 0;
                    break;
                case SINGLE_ACTIVE:
                    currCallState = 1;
                    break;
                case THRWAY_ACTIVE:
                    currCallState = 2;
                    break;
                case CONF_CALL:
                    currCallState = 3;
                    break;
            }
            switch (prev3WayCallState) {
                case IDLE:
                    prevCallState = 0;
                    break;
                case SINGLE_ACTIVE:
                    prevCallState = 1;
                    break;
                case THRWAY_ACTIVE:
                    prevCallState = 2;
                    break;
                case CONF_CALL:
                    prevCallState = 3;
                    break;
            }
            this.mBluetoothDsda.setCurrentCallState(currCallState, prevCallState, app.cdmaPhoneCallState.IsThreeWayCallOrigStateDialing());
        }
    }

    private CallNumber getCallNumber(Connection connection, Call call) {
        String number = null;
        int type = 128;
        if (connection == null && (connection = call.getEarliestConnection()) == null) {
            Log.e("BluetoothPhoneService", "Could not get a handle on Connection object for the call");
        }
        if (connection != null && (number = connection.getAddress()) != null) {
            type = PhoneNumberUtils.toaFromString(number);
        }
        if (number == null) {
            number = "";
        }
        return new CallNumber(number, type);
    }

    private class CallNumber {
        private String mNumber;
        private int mType;

        private CallNumber(String number, int type) {
            this.mNumber = null;
            this.mType = 0;
            this.mNumber = number;
            this.mType = type;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public boolean equalTo(CallNumber callNumber) {
            return this.mType == callNumber.mType && this.mNumber != null && this.mNumber.compareTo(callNumber.mNumber) == 0;
        }
    }

    private void listCurrentCallsGsm() {
        Connection[] clccConnections = new Connection[6];
        LinkedList<Connection> newConnections = new LinkedList<>();
        LinkedList<Connection> connections = new LinkedList<>();
        Call foregroundCall = this.mCM.getActiveFgCall();
        Call backgroundCall = this.mCM.getFirstActiveBgCall();
        Call ringingCall = this.mCM.getFirstActiveRingingCall();
        if (ringingCall.getState().isAlive()) {
            connections.addAll(ringingCall.getConnections());
        }
        if (foregroundCall.getState().isAlive()) {
            connections.addAll(foregroundCall.getConnections());
        }
        if (backgroundCall.getState().isAlive()) {
            connections.addAll(backgroundCall.getConnections());
        }
        boolean[] clccUsed = new boolean[6];
        for (int i = 0; i < 6; i++) {
            clccUsed[i] = this.mClccUsed[i];
            this.mClccUsed[i] = false;
        }
        for (Connection c : connections) {
            boolean found = false;
            long timestamp = c.getCreateTime();
            for (int i2 = 0; i2 < 6; i2++) {
                if (clccUsed[i2] && timestamp == this.mClccTimestamps[i2]) {
                    this.mClccUsed[i2] = true;
                    found = true;
                    clccConnections[i2] = c;
                    break;
                }
            }
            if (!found) {
                newConnections.add(c);
            }
        }
        while (!newConnections.isEmpty()) {
            int i3 = 0;
            while (this.mClccUsed[i3]) {
                i3++;
            }
            long earliestTimestamp = newConnections.get(0).getCreateTime();
            Connection earliestConnection = newConnections.get(0);
            for (int j = 0; j < newConnections.size(); j++) {
                long timestamp2 = newConnections.get(j).getCreateTime();
                if (timestamp2 < earliestTimestamp) {
                    earliestTimestamp = timestamp2;
                    Connection earliestConnection2 = newConnections.get(j);
                    earliestConnection = earliestConnection2;
                }
            }
            this.mClccUsed[i3] = true;
            this.mClccTimestamps[i3] = earliestTimestamp;
            clccConnections[i3] = earliestConnection;
            newConnections.remove(earliestConnection);
        }
        for (int i4 = 0; i4 < clccConnections.length; i4++) {
            if (this.mClccUsed[i4]) {
                sendClccResponseGsm(i4, clccConnections[i4]);
            }
        }
    }

    private void sendClccResponseGsm(int index, Connection connection) {
        int state = convertCallState(connection.getState());
        boolean mpty = false;
        Call call = connection.getCall();
        if (call != null) {
            mpty = call.isMultiparty();
        }
        boolean isIncoming = connection.isIncoming();
        String number = connection.getAddress();
        if (!isIncoming) {
            CallGatewayManager.RawGatewayInfo rawInfo = this.mCallGatewayManager.getGatewayInfo(connection);
            if (!rawInfo.isEmpty()) {
                number = rawInfo.trueNumber;
            }
        }
        int type = -1;
        if (number != null) {
            type = PhoneNumberUtils.toaFromString(number);
        } else {
            number = "";
        }
        if (this.mBluetoothHeadset != null) {
            this.mBluetoothHeadset.clccResponse(index + 1, isIncoming ? 1 : 0, state, 0, mpty, number, type);
        }
    }

    private synchronized void listCurrentCallsCdma() {
        Connection[] clccConnections = new Connection[2];
        Call foregroundCall = this.mCM.getActiveFgCall();
        Call ringingCall = this.mCM.getFirstActiveRingingCall();
        Call.State ringingCallState = ringingCall.getState();
        if (ringingCallState == Call.State.INCOMING) {
            log("Filling clccConnections[0] for INCOMING state");
            clccConnections[0] = ringingCall.getLatestConnection();
        } else if (foregroundCall.getState().isAlive()) {
            if (ringingCall.isRinging()) {
                log("Filling clccConnections[0] & [1] for CALL WAITING state");
                clccConnections[0] = foregroundCall.getEarliestConnection();
                clccConnections[1] = ringingCall.getLatestConnection();
            } else if (foregroundCall.getConnections().size() <= 1) {
                log("Filling clccConnections[0] with ForgroundCall latest connection");
                clccConnections[0] = foregroundCall.getLatestConnection();
            } else {
                log("Filling clccConnections[0] & [1] with ForgroundCall connections");
                clccConnections[0] = foregroundCall.getEarliestConnection();
                clccConnections[1] = foregroundCall.getLatestConnection();
            }
        }
        if (PhoneGlobals.getInstance().cdmaPhoneCallState.getCurrentCallState() == CdmaPhoneCallState.PhoneCallState.SINGLE_ACTIVE) {
            Message msg = this.mHandler.obtainMessage(6, false);
            this.mHandler.sendMessage(msg);
        } else if (PhoneGlobals.getInstance().cdmaPhoneCallState.getCurrentCallState() == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE) {
            Message msg2 = this.mHandler.obtainMessage(6, true);
            this.mHandler.sendMessage(msg2);
        }
        for (int i = 0; i < clccConnections.length && clccConnections[i] != null; i++) {
            sendClccResponseCdma(i, clccConnections[i]);
        }
    }

    private void sendClccResponseCdma(int index, Connection connection) {
        int state;
        PhoneGlobals app = PhoneGlobals.getInstance();
        CdmaPhoneCallState.PhoneCallState currCdmaCallState = app.cdmaPhoneCallState.getCurrentCallState();
        CdmaPhoneCallState.PhoneCallState prevCdmaCallState = app.cdmaPhoneCallState.getPreviousCallState();
        if (prevCdmaCallState == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE && currCdmaCallState == CdmaPhoneCallState.PhoneCallState.CONF_CALL) {
            state = 0;
        } else {
            Call.State callState = connection.getState();
            switch (AnonymousClass6.$SwitchMap$com$android$internal$telephony$Call$State[callState.ordinal()]) {
                case 1:
                    if (index == 0) {
                        state = !this.mCdmaIsSecondCallActive ? 0 : 1;
                    } else if (!this.mCdmaIsSecondCallActive) {
                        state = 1;
                    } else {
                        state = 0;
                    }
                    break;
                case 2:
                    state = 1;
                    break;
                case 3:
                    state = 2;
                    break;
                case 4:
                    state = 3;
                    break;
                case 5:
                    state = 4;
                    break;
                case 6:
                    state = 5;
                    break;
                default:
                    Log.e("BluetoothPhoneService", "bad call state: " + callState);
                    return;
            }
        }
        boolean mpty = false;
        if (currCdmaCallState == CdmaPhoneCallState.PhoneCallState.CONF_CALL && prevCdmaCallState == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE) {
            mpty = true;
        }
        boolean isIncoming = connection.isIncoming();
        String number = connection.getAddress();
        if (!isIncoming) {
            CallGatewayManager.RawGatewayInfo rawInfo = this.mCallGatewayManager.getGatewayInfo(connection);
            if (!rawInfo.isEmpty()) {
                number = rawInfo.trueNumber;
            }
        }
        int type = -1;
        if (number != null) {
            type = PhoneNumberUtils.toaFromString(number);
        } else {
            number = "";
        }
        if (this.mBluetoothHeadset != null) {
            this.mBluetoothHeadset.clccResponse(index + 1, isIncoming ? 1 : 0, state, 0, mpty, number, type);
        }
    }

    /* JADX INFO: renamed from: com.android.phone.BluetoothPhoneService$6, reason: invalid class name */
    static /* synthetic */ class AnonymousClass6 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$Call$State = new int[Call.State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[Call.State.ACTIVE.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[Call.State.HOLDING.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[Call.State.DIALING.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[Call.State.ALERTING.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[Call.State.INCOMING.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[Call.State.WAITING.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[Call.State.IDLE.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[Call.State.DISCONNECTED.ordinal()] = 8;
            } catch (NoSuchFieldError e8) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[Call.State.DISCONNECTING.ordinal()] = 9;
            } catch (NoSuchFieldError e9) {
            }
            $SwitchMap$com$android$phone$CdmaPhoneCallState$PhoneCallState = new int[CdmaPhoneCallState.PhoneCallState.values().length];
            try {
                $SwitchMap$com$android$phone$CdmaPhoneCallState$PhoneCallState[CdmaPhoneCallState.PhoneCallState.IDLE.ordinal()] = 1;
            } catch (NoSuchFieldError e10) {
            }
            try {
                $SwitchMap$com$android$phone$CdmaPhoneCallState$PhoneCallState[CdmaPhoneCallState.PhoneCallState.SINGLE_ACTIVE.ordinal()] = 2;
            } catch (NoSuchFieldError e11) {
            }
            try {
                $SwitchMap$com$android$phone$CdmaPhoneCallState$PhoneCallState[CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE.ordinal()] = 3;
            } catch (NoSuchFieldError e12) {
            }
            try {
                $SwitchMap$com$android$phone$CdmaPhoneCallState$PhoneCallState[CdmaPhoneCallState.PhoneCallState.CONF_CALL.ordinal()] = 4;
            } catch (NoSuchFieldError e13) {
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleCdmaSwapSecondCallState() {
        log("cdmaSwapSecondCallState: Toggling mCdmaIsSecondCallActive");
        if (isDsdaEnabled() && this.mBluetoothDsda != null) {
            log("DSDA.handleCdmaSwapSecondCallState");
            try {
                this.mBluetoothDsda.handleCdmaSwapSecondCallState();
                return;
            } catch (RemoteException e) {
                Log.w("BluetoothPhoneService", "DSDA class not found exception " + e);
                return;
            }
        }
        this.mCdmaIsSecondCallActive = !this.mCdmaIsSecondCallActive;
        this.mCdmaCallsSwapped = true;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleCdmaSetSecondCallState(boolean state) {
        log("cdmaSetSecondCallState: Setting mCdmaIsSecondCallActive to " + state);
        if (isDsdaEnabled() && this.mBluetoothDsda != null) {
            log("DSDA.handleCdmaSetSecondCallState");
            try {
                this.mBluetoothDsda.handleCdmaSetSecondCallState(state);
                return;
            } catch (RemoteException e) {
                Log.w("BluetoothPhoneService", "DSDA class not found exception " + e);
                return;
            }
        }
        this.mCdmaIsSecondCallActive = state;
        if (!this.mCdmaIsSecondCallActive) {
            this.mCdmaCallsSwapped = false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean answerCsvtCall() {
        log("answerCsvtCall");
        Intent intent = new Intent("com.borqs.videocall.action.answerCall");
        PhoneGlobals.getInstance().sendBroadcast(intent);
        return true;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean hangupCsvtCall() {
        log("hangupCsvtCall");
        Intent intent = new Intent("com.borqs.videocall.action.StopVTCall");
        PhoneGlobals.getInstance().sendBroadcast(intent);
        return true;
    }

    private Call getCallOnOtherSub() throws RemoteException {
        log("getCallOnOtherSub");
        int activeSub = this.mCM.getActiveSubscription();
        int bgSub = PhoneUtils.getOtherActiveSub(activeSub);
        if (bgSub == -1 || this.mBluetoothDsda.getTotalCallsOnSub(bgSub) != 1) {
            return null;
        }
        if (this.mCM.hasActiveFgCall(bgSub)) {
            Call call = this.mCM.getActiveFgCall(bgSub);
            return call;
        }
        if (!this.mCM.hasActiveBgCall(bgSub)) {
            return null;
        }
        Call call2 = this.mCM.getFirstActiveBgCall(bgSub);
        return call2;
    }

    private Call getCallOnActiveSub() throws RemoteException {
        log("getCallOnActiveSub");
        int activeSub = this.mCM.getActiveSubscription();
        Call call = null;
        int bgSub = PhoneUtils.getOtherActiveSub(activeSub);
        if (bgSub == -1) {
            return null;
        }
        if (this.mBluetoothDsda.getTotalCallsOnSub(bgSub) < 2) {
            if (this.mCM.hasActiveFgCall(activeSub)) {
                call = this.mCM.getActiveFgCall(activeSub);
            } else if (this.mCM.hasActiveBgCall(activeSub)) {
                call = this.mCM.getFirstActiveBgCall(activeSub);
            }
        }
        return call;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean processDsdaChld(int i) throws RemoteException {
        boolean z = true;
        int activeSubscription = this.mCM.getActiveSubscription();
        Phone phone = MSimPhoneGlobals.getInstance().getPhone(activeSubscription);
        int phoneType = phone.getPhoneType();
        log("processChld: " + i + " for Phone type: " + phoneType);
        Call firstActiveRingingCall = this.mCM.getFirstActiveRingingCall(activeSubscription);
        Call firstActiveBgCall = this.mCM.getFirstActiveBgCall(activeSubscription);
        switch (i) {
            case 0:
                if (firstActiveRingingCall.isRinging()) {
                    return PhoneUtils.hangupRingingCall(firstActiveRingingCall);
                }
                Call callOnOtherSub = getCallOnOtherSub();
                if (callOnOtherSub != null) {
                    PhoneUtils.hangup(callOnOtherSub);
                    return true;
                }
                return PhoneUtils.hangupHoldingCall(firstActiveBgCall);
            case 1:
                if (phoneType == 2) {
                    Call callOnOtherSub2 = getCallOnOtherSub();
                    if (firstActiveRingingCall.isRinging() && callOnOtherSub2 != null) {
                        PhoneUtils.answerCall(this.mCM.getFirstActiveRingingCall(activeSubscription));
                        PhoneUtils.hangup(callOnOtherSub2);
                    } else if (this.mBluetoothDsda.isSwitchSubAllowed()) {
                        log("Drop the call on Active sub, move LCH to active");
                        Call callOnActiveSub = getCallOnActiveSub();
                        if (callOnActiveSub != null) {
                            PhoneUtils.hangup(callOnActiveSub);
                        }
                    } else if (firstActiveRingingCall.isRinging()) {
                        log("CHLD:1 Callwaiting Answer call");
                        PhoneUtils.hangupRingingAndActive(phone);
                    } else {
                        log("CHLD:1 Hangup Call");
                        PhoneUtils.hangup(PhoneGlobals.getInstance().mCM);
                    }
                    return true;
                }
                if (phoneType == 1) {
                    Call callOnOtherSub3 = getCallOnOtherSub();
                    if (firstActiveRingingCall.isRinging() && callOnOtherSub3 != null) {
                        PhoneUtils.answerCall(this.mCM.getFirstActiveRingingCall(activeSubscription));
                        PhoneUtils.hangup(callOnOtherSub3);
                    } else if (this.mBluetoothDsda.isSwitchSubAllowed()) {
                        log("processChld drop the call on Active sub, move LCH to active");
                        log("Drop call on active sub");
                        Call callOnActiveSub2 = getCallOnActiveSub();
                        if (callOnActiveSub2 != null) {
                            PhoneUtils.hangup(callOnActiveSub2);
                        }
                    } else {
                        PhoneUtils.answerAndEndActive(PhoneGlobals.getInstance().mCM, firstActiveRingingCall);
                    }
                    return true;
                }
                Log.e("BluetoothPhoneService", "bad phone type: " + phoneType);
                return false;
            case 2:
                if (phoneType == 2) {
                    if (this.mBluetoothDsda.canDoCallSwap()) {
                        log("Try to do call swap on same sub");
                        if (PhoneGlobals.getInstance().cdmaPhoneCallState.getCurrentCallState() == CdmaPhoneCallState.PhoneCallState.CONF_CALL) {
                            log("CHLD:2 Swap Calls");
                            PhoneUtils.switchHoldingAndActive(firstActiveBgCall);
                            handleCdmaSwapSecondCallState();
                        } else {
                            Log.e("BluetoothPhoneService", "CDMA fail to do hold active and accept held");
                        }
                    } else if (this.mBluetoothDsda.isSwitchSubAllowed()) {
                        log("CHLD = 2 Switch sub");
                        ((MSimCallNotifier) PhoneGlobals.getInstance().notifier).manageMSimInCallTones(true);
                        this.mBluetoothDsda.SwitchSub();
                    } else if (this.mBluetoothDsda.answerOnThisSubAllowed()) {
                        log("Can we answer the call on other SUB?");
                        if (firstActiveRingingCall.isRinging()) {
                            PhoneUtils.answerCall(firstActiveRingingCall);
                        }
                    } else {
                        if (firstActiveRingingCall.isRinging()) {
                            log("CHLD:2 Callwaiting Answer call");
                            PhoneUtils.answerCall(firstActiveRingingCall);
                            PhoneUtils.setMute(false);
                            handleCdmaSetSecondCallState(true);
                        } else if (PhoneGlobals.getInstance().cdmaPhoneCallState.getCurrentCallState() == CdmaPhoneCallState.PhoneCallState.CONF_CALL) {
                            log("CHLD:2 Swap Calls");
                            PhoneUtils.switchHoldingAndActive(firstActiveBgCall);
                            handleCdmaSwapSecondCallState();
                        }
                        Log.e("BluetoothPhoneService", "CDMA fail to do hold active and accept held");
                    }
                    return true;
                }
                if (phoneType == 1) {
                    if (this.mBluetoothDsda.canDoCallSwap()) {
                        log("Try to do call swap on same sub");
                        PhoneUtils.switchHoldingAndActive(firstActiveBgCall);
                    } else if (this.mBluetoothDsda.isSwitchSubAllowed()) {
                        log("Switch sub");
                        ((MSimCallNotifier) PhoneGlobals.getInstance().notifier).manageMSimInCallTones(true);
                        this.mBluetoothDsda.SwitchSub();
                    } else if (this.mBluetoothDsda.answerOnThisSubAllowed()) {
                        log("Can we answer the call on other SUB?");
                        if (firstActiveRingingCall.isRinging()) {
                            PhoneUtils.answerCall(firstActiveRingingCall);
                        }
                    } else {
                        log("CHLD=2, Answer the call on same sub");
                        if (firstActiveBgCall.mState == Call.State.HOLDING && firstActiveRingingCall.isRinging()) {
                            log("Background is on hold when incoming call came");
                            PhoneUtils.answerCall(firstActiveRingingCall);
                        } else {
                            PhoneUtils.switchHoldingAndActive(firstActiveBgCall);
                        }
                    }
                    return true;
                }
                Log.e("BluetoothPhoneService", "Unexpected phone type: " + phoneType);
                return false;
            case 3:
                if (phoneType == 2) {
                    CdmaPhoneCallState.PhoneCallState currentCallState = PhoneGlobals.getInstance().cdmaPhoneCallState.getCurrentCallState();
                    if (currentCallState == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE) {
                        log("CHLD:3 Merge Calls");
                        PhoneUtils.mergeCalls();
                    } else if (currentCallState == CdmaPhoneCallState.PhoneCallState.CONF_CALL) {
                        z = false;
                    } else {
                        Log.e("BluetoothPhoneService", "GSG no call to add conference");
                        z = false;
                    }
                    return z;
                }
                if (phoneType == 1) {
                    if (this.mCM.hasActiveFgCall() && this.mCM.hasActiveBgCall()) {
                        PhoneUtils.mergeCalls();
                        return true;
                    }
                    Log.e("BluetoothPhoneService", "GSG no call to merge");
                    return false;
                }
                Log.e("BluetoothPhoneService", "Unexpected phone type: " + phoneType);
                return false;
            default:
                Log.e("BluetoothPhoneService", "bad CHLD value: " + i);
                return false;
        }
    }

    static int convertCallState(Call.State state, Call.State state2) {
        int i;
        if (state == Call.State.INCOMING || state == Call.State.WAITING) {
            i = 4;
        } else if (state2 == Call.State.DIALING) {
            i = 2;
        } else if (state2 == Call.State.ALERTING) {
            i = 3;
        } else {
            i = 6;
        }
        Log.v("BluetoothPhoneService", "Call state Converted2: " + state + "/" + state2 + " -> " + i);
        return i;
    }

    static int convertCallState(Call.State state) {
        int i = 6;
        switch (AnonymousClass6.$SwitchMap$com$android$internal$telephony$Call$State[state.ordinal()]) {
            case 1:
                i = 0;
                break;
            case 2:
                i = 1;
                break;
            case 3:
                i = 2;
                break;
            case 4:
                i = 3;
                break;
            case 5:
                i = 4;
                break;
            case 6:
                i = 5;
                break;
            case 7:
            case 8:
            case 9:
                break;
            default:
                Log.e("BluetoothPhoneService", "bad call state: " + state);
                break;
        }
        Log.v("BluetoothPhoneService", "Call state Converted2: " + state + " -> " + i);
        return i;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void log(String msg) {
        Log.d("BluetoothPhoneService", msg);
    }
}
