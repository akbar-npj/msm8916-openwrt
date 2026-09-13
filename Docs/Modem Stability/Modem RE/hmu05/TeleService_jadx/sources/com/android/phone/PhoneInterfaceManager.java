package com.android.phone;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.telephony.CellInfo;
import android.telephony.NeighboringCellInfo;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.DefaultPhoneNotifier;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.ITelephonyListener;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.uicc.IccIoResult;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.UiccController;
import com.android.services.telephony.common.Call;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public class PhoneInterfaceManager extends ITelephony.Stub implements CallModeler.Listener {
    private static PhoneInterfaceManager sInstance;
    PhoneGlobals mApp;
    AppOpsManager mAppOps;
    CallHandlerServiceProxy mCallHandlerService;
    CallModeler mCallModeler;
    Runnable mDtmfStopRunnable;
    DTMFTonePlayer mDtmfTonePlayer;
    private int mLastError;
    Phone mPhone;
    Handler mDtmfStopHandler = new Handler();
    private final List<ITelephonyListener> mListeners = new ArrayList();
    private final Map<IBinder, TelephonyListenerDeathRecipient> mDeathRecipients = new HashMap();
    CallManager mCM = PhoneGlobals.getInstance().mCM;
    MainThreadHandler mMainThreadHandler = new MainThreadHandler();

    private static final class IccApduArgument {
        public int channel;
        public int cla;
        public int command;
        public String data;
        public int p1;
        public int p2;
        public int p3;

        public IccApduArgument(int cla, int command, int channel, int p1, int p2, int p3, String data) {
            this.channel = channel;
            this.cla = cla;
            this.command = command;
            this.p1 = p1;
            this.p2 = p2;
            this.p3 = p3;
            this.data = data;
        }
    }

    private static final class MainThreadRequest {
        public Object argument;
        public Object result;

        public MainThreadRequest(Object argument) {
            this.argument = argument;
        }
    }

    private final class MainThreadHandler extends Handler {
        private MainThreadHandler() {
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            boolean hungUp;
            switch (msg.what) {
                case 1:
                    MainThreadRequest request = (MainThreadRequest) msg.obj;
                    request.result = Boolean.valueOf(PhoneInterfaceManager.this.mPhone.handlePinMmi((String) request.argument));
                    synchronized (request) {
                        request.notifyAll();
                        break;
                    }
                    return;
                case 2:
                    Message onCompleted = obtainMessage(3, (MainThreadRequest) msg.obj);
                    PhoneInterfaceManager.this.mPhone.getNeighboringCids(onCompleted);
                    return;
                case 3:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    MainThreadRequest request2 = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null && ar.result != null) {
                        request2.result = ar.result;
                    } else {
                        request2.result = new ArrayList();
                    }
                    synchronized (request2) {
                        request2.notifyAll();
                        break;
                    }
                    return;
                case 4:
                    PhoneInterfaceManager.this.answerRingingCallInternal();
                    return;
                case 5:
                    MainThreadRequest request3 = (MainThreadRequest) msg.obj;
                    int phoneType = PhoneInterfaceManager.this.mPhone.getPhoneType();
                    if (phoneType == 2) {
                        hungUp = PhoneUtils.hangupRingingAndActive(PhoneInterfaceManager.this.mPhone);
                    } else if (phoneType == 1 || phoneType == 4) {
                        hungUp = PhoneUtils.hangup(PhoneInterfaceManager.this.mCM);
                    } else {
                        throw new IllegalStateException("Unexpected phone type: " + phoneType);
                    }
                    PhoneInterfaceManager.this.log("CMD_END_CALL: " + (hungUp ? "hung up!" : "no call to hang up"));
                    request3.result = Boolean.valueOf(hungUp);
                    synchronized (request3) {
                        request3.notifyAll();
                        break;
                    }
                    return;
                case 6:
                    PhoneInterfaceManager.this.silenceRingerInternal();
                    return;
                case 7:
                    MainThreadRequest request4 = (MainThreadRequest) msg.obj;
                    IccApduArgument argument = (IccApduArgument) request4.argument;
                    Message onCompleted2 = obtainMessage(8, request4);
                    PhoneInterfaceManager.this.mPhone.getIccCard().exchangeApdu(argument.cla, argument.command, argument.channel, argument.p1, argument.p2, argument.p3, argument.data, onCompleted2);
                    return;
                case 8:
                    AsyncResult ar2 = (AsyncResult) msg.obj;
                    MainThreadRequest request5 = (MainThreadRequest) ar2.userObj;
                    if (ar2.exception == null && ar2.result != null) {
                        request5.result = ar2.result;
                        PhoneInterfaceManager.this.mLastError = 0;
                    } else {
                        request5.result = new IccIoResult(111, 0, (byte[]) null);
                        PhoneInterfaceManager.this.mLastError = 1;
                        if (ar2.exception != null && (ar2.exception instanceof CommandException) && ar2.exception.getCommandError() == CommandException.Error.INVALID_PARAMETER) {
                            PhoneInterfaceManager.this.mLastError = 5;
                        }
                    }
                    synchronized (request5) {
                        request5.notifyAll();
                        break;
                    }
                    return;
                case 9:
                    MainThreadRequest request6 = (MainThreadRequest) msg.obj;
                    Message onCompleted3 = obtainMessage(10, request6);
                    PhoneInterfaceManager.this.mPhone.getIccCard().openLogicalChannel((String) request6.argument, onCompleted3);
                    return;
                case 10:
                    AsyncResult ar3 = (AsyncResult) msg.obj;
                    MainThreadRequest request7 = (MainThreadRequest) ar3.userObj;
                    if (ar3.exception == null && ar3.result != null) {
                        int[] resultArray = (int[]) ar3.result;
                        request7.result = new Integer(resultArray[0]);
                        PhoneInterfaceManager.this.mLastError = 0;
                    } else {
                        request7.result = new Integer(0);
                        PhoneInterfaceManager.this.mLastError = 1;
                        if (ar3.exception != null && (ar3.exception instanceof CommandException)) {
                            if (ar3.exception.getCommandError() == CommandException.Error.MISSING_RESOURCE) {
                                PhoneInterfaceManager.this.mLastError = 2;
                            } else if (ar3.exception.getCommandError() == CommandException.Error.NO_SUCH_ELEMENT) {
                                PhoneInterfaceManager.this.mLastError = 3;
                            }
                        }
                    }
                    synchronized (request7) {
                        request7.notifyAll();
                        break;
                    }
                    return;
                case 11:
                    MainThreadRequest request8 = (MainThreadRequest) msg.obj;
                    Message onCompleted4 = obtainMessage(12, request8);
                    PhoneInterfaceManager.this.mPhone.getIccCard().closeLogicalChannel(((Integer) request8.argument).intValue(), onCompleted4);
                    return;
                case 12:
                    AsyncResult ar4 = (AsyncResult) msg.obj;
                    MainThreadRequest request9 = (MainThreadRequest) ar4.userObj;
                    if (ar4.exception == null) {
                        request9.result = new Integer(0);
                        PhoneInterfaceManager.this.mLastError = 0;
                    } else {
                        request9.result = new Integer(-1);
                        PhoneInterfaceManager.this.mLastError = 1;
                        if (ar4.exception != null && (ar4.exception instanceof CommandException) && ar4.exception.getCommandError() == CommandException.Error.INVALID_PARAMETER) {
                            PhoneInterfaceManager.this.mLastError = 5;
                        }
                    }
                    synchronized (request9) {
                        request9.notifyAll();
                        break;
                    }
                    return;
                case 13:
                    MainThreadRequest request10 = (MainThreadRequest) msg.obj;
                    IccApduArgument parameters = (IccApduArgument) request10.argument;
                    Message onCompleted5 = obtainMessage(14, request10);
                    PhoneInterfaceManager.this.mPhone.getIccCard().exchangeIccIo(parameters.cla, parameters.command, parameters.p1, parameters.p2, parameters.p3, parameters.data, onCompleted5);
                    return;
                case 14:
                    AsyncResult ar5 = (AsyncResult) msg.obj;
                    MainThreadRequest request11 = (MainThreadRequest) ar5.userObj;
                    if (ar5.exception == null && ar5.result != null) {
                        request11.result = ar5.result;
                        PhoneInterfaceManager.this.mLastError = 0;
                    } else {
                        request11.result = new IccIoResult(111, 0, (byte[]) null);
                        PhoneInterfaceManager.this.mLastError = 1;
                        if (ar5.exception != null && (ar5.exception instanceof CommandException) && ar5.exception.getCommandError() == CommandException.Error.INVALID_PARAMETER) {
                            PhoneInterfaceManager.this.mLastError = 5;
                        }
                    }
                    synchronized (request11) {
                        request11.notifyAll();
                        break;
                    }
                    return;
                case 15:
                    Message onCompleted6 = obtainMessage(16, (MainThreadRequest) msg.obj);
                    PhoneInterfaceManager.this.mPhone.getIccCard().getAtr(onCompleted6);
                    return;
                case 16:
                    AsyncResult ar6 = (AsyncResult) msg.obj;
                    MainThreadRequest request12 = (MainThreadRequest) ar6.userObj;
                    if (ar6.exception == null) {
                        request12.result = ar6.result;
                        PhoneInterfaceManager.this.mLastError = 0;
                    } else {
                        request12.result = "";
                        if (ar6.exception != null && (ar6.exception instanceof CommandException)) {
                            if (ar6.exception.getCommandError() == CommandException.Error.RADIO_NOT_AVAILABLE) {
                                PhoneInterfaceManager.this.mLastError = 1;
                            } else if (ar6.exception.getCommandError() == CommandException.Error.GENERIC_FAILURE) {
                                PhoneInterfaceManager.this.mLastError = 2;
                            }
                        }
                    }
                    synchronized (request12) {
                        request12.notifyAll();
                        break;
                    }
                    return;
                default:
                    Log.w("PhoneInterfaceManager", "MainThreadHandler: unexpected message code: " + msg.what);
                    return;
            }
        }
    }

    private Object sendRequest(int command, Object argument) {
        if (Looper.myLooper() == this.mMainThreadHandler.getLooper()) {
            throw new RuntimeException("This method will deadlock if called from the main thread.");
        }
        MainThreadRequest request = new MainThreadRequest(argument);
        Message msg = this.mMainThreadHandler.obtainMessage(command, request);
        msg.sendToTarget();
        synchronized (request) {
            while (request.result == null) {
                try {
                    request.wait();
                } catch (InterruptedException e) {
                }
            }
        }
        return request.result;
    }

    private void sendRequestAsync(int command) {
        this.mMainThreadHandler.sendEmptyMessage(command);
    }

    static PhoneInterfaceManager init(PhoneGlobals phoneGlobals, Phone phone, CallHandlerServiceProxy callHandlerServiceProxy, CallModeler callModeler, DTMFTonePlayer dTMFTonePlayer) {
        PhoneInterfaceManager phoneInterfaceManager;
        synchronized (PhoneInterfaceManager.class) {
            if (sInstance == null) {
                sInstance = new PhoneInterfaceManager(phoneGlobals, phone, callHandlerServiceProxy, callModeler, dTMFTonePlayer);
            } else {
                Log.wtf("PhoneInterfaceManager", "init() called multiple times!  sInstance = " + sInstance);
            }
            phoneInterfaceManager = sInstance;
        }
        return phoneInterfaceManager;
    }

    private PhoneInterfaceManager(PhoneGlobals app, Phone phone, CallHandlerServiceProxy callHandlerService, CallModeler callModeler, DTMFTonePlayer dtmfTonePlayer) {
        this.mApp = app;
        this.mPhone = phone;
        this.mAppOps = (AppOpsManager) app.getSystemService("appops");
        this.mCallHandlerService = callHandlerService;
        this.mCallModeler = callModeler;
        this.mCallModeler.addListener(this);
        this.mDtmfTonePlayer = dtmfTonePlayer;
        publish();
    }

    /* JADX WARN: Multi-variable type inference failed */
    private void publish() {
        log("publish: " + this);
        ServiceManager.addService("phone", this);
    }

    public void dial(String number) {
        PhoneConstants.State state;
        log("dial: " + number);
        String url = createTelUrl(number);
        if (url != null && (state = this.mCM.getState()) != PhoneConstants.State.OFFHOOK && state != PhoneConstants.State.RINGING) {
            Intent intent = new Intent("android.intent.action.DIAL", Uri.parse(url));
            intent.addFlags(268435456);
            this.mApp.startActivity(intent);
        }
    }

    public void call(String callingPackage, String number) {
        String url;
        log("call: " + number);
        enforceCallPermission();
        if (this.mAppOps.noteOp(13, Binder.getCallingUid(), callingPackage) == 0 && (url = createTelUrl(number)) != null) {
            Intent intent = new Intent("android.intent.action.CALL", Uri.parse(url));
            intent.addFlags(268435456);
            this.mApp.startActivity(intent);
        }
    }

    private boolean showCallScreenInternal(boolean specifyInitialDialpadState, boolean showDialpad) {
        if (!PhoneGlobals.sVoiceCapable) {
            return false;
        }
        if (PhoneGlobals.getInstance().isCsvtActive()) {
            Log.d("PhoneInterfaceManager", "showCallScreenInternal: csvt is active");
            Intent mIntent = new Intent("restore_video_call");
            this.mApp.sendBroadcast(mIntent);
            return false;
        }
        if (isIdle()) {
            return false;
        }
        long callingId = Binder.clearCallingIdentity();
        this.mCallHandlerService.bringToForeground(showDialpad);
        Binder.restoreCallingIdentity(callingId);
        return true;
    }

    public boolean showCallScreen() {
        return showCallScreenInternal(false, false);
    }

    public boolean showCallScreenWithDialpad(boolean showDialpad) {
        return showCallScreenInternal(true, showDialpad);
    }

    public boolean endCall() {
        enforceCallPermission();
        return ((Boolean) sendRequest(5, null)).booleanValue();
    }

    public void answerRingingCall() {
        log("answerRingingCall...");
        enforceModifyPermission();
        sendRequestAsync(4);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void answerRingingCallInternal() {
        boolean hasRingingCall = !this.mPhone.getRingingCall().isIdle();
        if (hasRingingCall) {
            boolean hasActiveCall = !this.mPhone.getForegroundCall().isIdle();
            boolean hasHoldingCall = !this.mPhone.getBackgroundCall().isIdle();
            if (hasActiveCall && hasHoldingCall) {
                PhoneUtils.answerAndEndActive(this.mCM, this.mCM.getFirstActiveRingingCall());
            } else {
                PhoneUtils.answerCall(this.mCM.getFirstActiveRingingCall());
            }
        }
    }

    public void silenceRinger() {
        log("silenceRinger...");
        enforceModifyPermission();
        sendRequestAsync(6);
        silenceCsvtRinger();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void silenceRingerInternal() {
        if (this.mCM.getState() == PhoneConstants.State.RINGING && this.mApp.notifier.isRinging()) {
            log("silenceRingerInternal: silencing...");
            this.mApp.notifier.silenceRinger();
        }
    }

    private void silenceCsvtRinger() {
        Intent intent = new Intent("com.borqs.videocall.action.silencering");
        this.mApp.sendBroadcast(intent);
    }

    public boolean isOffhook() {
        return this.mCM.getState() == PhoneConstants.State.OFFHOOK;
    }

    public boolean isRinging() {
        return this.mCM.getState() == PhoneConstants.State.RINGING;
    }

    public boolean isIdle() {
        return this.mCM.getState() == PhoneConstants.State.IDLE;
    }

    public boolean isSimPinEnabled() {
        enforceReadPermission();
        return PhoneGlobals.getInstance().isSimPinEnabled();
    }

    public boolean supplyPin(String pin) {
        int[] resultArray = supplyPinReportResult(pin);
        return resultArray[0] == 0;
    }

    public boolean supplyPuk(String puk, String pin) {
        int[] resultArray = supplyPukReportResult(puk, pin);
        return resultArray[0] == 0;
    }

    public int[] supplyPinReportResult(String pin) {
        enforceModifyPermission();
        UnlockSim checkSimPin = new UnlockSim(this.mPhone.getIccCard());
        checkSimPin.start();
        return checkSimPin.unlockSim(null, pin);
    }

    public int[] supplyPukReportResult(String puk, String pin) {
        enforceModifyPermission();
        UnlockSim checkSimPuk = new UnlockSim(this.mPhone.getIccCard());
        checkSimPuk.start();
        return checkSimPuk.unlockSim(puk, pin);
    }

    private static class UnlockSim extends Thread {
        private Handler mHandler;
        private final IccCard mSimCard;
        private boolean mDone = false;
        private int mResult = 2;
        private int mRetryCount = -1;

        public UnlockSim(IccCard simCard) {
            this.mSimCard = simCard;
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            Looper.prepare();
            synchronized (this) {
                this.mHandler = new Handler() { // from class: com.android.phone.PhoneInterfaceManager.UnlockSim.1
                    @Override // android.os.Handler
                    public void handleMessage(Message msg) {
                        AsyncResult ar = (AsyncResult) msg.obj;
                        switch (msg.what) {
                            case 100:
                                Log.d("PhoneInterfaceManager", "SUPPLY_PIN_COMPLETE");
                                synchronized (UnlockSim.this) {
                                    UnlockSim.this.mRetryCount = msg.arg1;
                                    if (ar.exception == null) {
                                        UnlockSim.this.mResult = 0;
                                    } else if (!(ar.exception instanceof CommandException) || ar.exception.getCommandError() != CommandException.Error.PASSWORD_INCORRECT) {
                                        UnlockSim.this.mResult = 2;
                                    } else {
                                        UnlockSim.this.mResult = 1;
                                    }
                                    UnlockSim.this.mDone = true;
                                    UnlockSim.this.notifyAll();
                                    break;
                                }
                                return;
                            default:
                                return;
                        }
                    }
                };
                notifyAll();
            }
            Looper.loop();
        }

        synchronized int[] unlockSim(String puk, String pin) {
            int[] resultArray;
            while (this.mHandler == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            Message callback = Message.obtain(this.mHandler, 100);
            if (puk == null) {
                this.mSimCard.supplyPin(pin, callback);
            } else {
                this.mSimCard.supplyPuk(puk, pin, callback);
            }
            while (!this.mDone) {
                try {
                    Log.d("PhoneInterfaceManager", "wait for done");
                    wait();
                } catch (InterruptedException e2) {
                    Thread.currentThread().interrupt();
                }
            }
            Log.d("PhoneInterfaceManager", "done");
            resultArray = new int[]{this.mResult, this.mRetryCount};
            return resultArray;
        }
    }

    public void updateServiceLocation() {
        this.mPhone.updateServiceLocation();
    }

    public boolean isRadioOn() {
        return this.mPhone.isRadioOn();
    }

    public void toggleRadioOnOff() {
        enforceModifyPermission();
        this.mPhone.setRadioPower(!isRadioOn());
    }

    public boolean setRadio(boolean turnOn) {
        enforceModifyPermission();
        if (this.mPhone.isRadioOn() != turnOn) {
            toggleRadioOnOff();
            return true;
        }
        return true;
    }

    public boolean setRadioPower(boolean turnOn) {
        enforceModifyPermission();
        this.mPhone.setRadioPower(turnOn);
        return true;
    }

    public boolean enableDataConnectivity() {
        enforceModifyPermission();
        ConnectivityManager cm = (ConnectivityManager) this.mApp.getSystemService("connectivity");
        cm.setMobileDataEnabled(true);
        return true;
    }

    public int enableApnType(String type) {
        enforceModifyPermission();
        return this.mPhone.enableApnType(type);
    }

    public int disableApnType(String type) {
        enforceModifyPermission();
        return this.mPhone.disableApnType(type);
    }

    public boolean disableDataConnectivity() {
        enforceModifyPermission();
        ConnectivityManager cm = (ConnectivityManager) this.mApp.getSystemService("connectivity");
        cm.setMobileDataEnabled(false);
        return true;
    }

    public boolean isDataConnectivityPossible() {
        return this.mPhone.isDataConnectivityPossible();
    }

    public boolean handlePinMmi(String dialString) {
        enforceModifyPermission();
        return ((Boolean) sendRequest(1, dialString)).booleanValue();
    }

    public void cancelMissedCallsNotification() {
        enforceModifyPermission();
        this.mApp.notificationMgr.cancelMissedCallNotification();
    }

    public int getCallState() {
        return DefaultPhoneNotifier.convertCallState(this.mCM.getState());
    }

    public int getDataState() {
        Phone phone = this.mApp.getPhone(this.mApp.getDataSubscription());
        return DefaultPhoneNotifier.convertDataState(phone.getDataConnectionState());
    }

    public int getDataActivity() {
        Phone phone = this.mApp.getPhone(this.mApp.getDataSubscription());
        return DefaultPhoneNotifier.convertDataActivityState(phone.getDataActivityState());
    }

    public Bundle getCellLocation() {
        try {
            this.mApp.enforceCallingOrSelfPermission("android.permission.ACCESS_FINE_LOCATION", null);
        } catch (SecurityException e) {
            this.mApp.enforceCallingOrSelfPermission("android.permission.ACCESS_COARSE_LOCATION", null);
        }
        if (!checkIfCallerIsSelfOrForegoundUser()) {
            return null;
        }
        Bundle data = new Bundle();
        this.mPhone.getCellLocation().fillInNotifierBundle(data);
        return data;
    }

    public void enableLocationUpdates() {
        this.mApp.enforceCallingOrSelfPermission("android.permission.CONTROL_LOCATION_UPDATES", null);
        this.mPhone.enableLocationUpdates();
    }

    public void disableLocationUpdates() {
        this.mApp.enforceCallingOrSelfPermission("android.permission.CONTROL_LOCATION_UPDATES", null);
        this.mPhone.disableLocationUpdates();
    }

    public List<NeighboringCellInfo> getNeighboringCellInfo(String callingPackage) {
        try {
            this.mApp.enforceCallingOrSelfPermission("android.permission.ACCESS_FINE_LOCATION", null);
        } catch (SecurityException e) {
            this.mApp.enforceCallingOrSelfPermission("android.permission.ACCESS_COARSE_LOCATION", null);
        }
        if (this.mAppOps.noteOp(12, Binder.getCallingUid(), callingPackage) != 0 || !checkIfCallerIsSelfOrForegoundUser()) {
            return null;
        }
        try {
            return (ArrayList) sendRequest(2, null);
        } catch (RuntimeException e2) {
            Log.e("PhoneInterfaceManager", "getNeighboringCellInfo " + e2);
            return null;
        }
    }

    public List<CellInfo> getAllCellInfo() {
        try {
            this.mApp.enforceCallingOrSelfPermission("android.permission.ACCESS_FINE_LOCATION", null);
        } catch (SecurityException e) {
            this.mApp.enforceCallingOrSelfPermission("android.permission.ACCESS_COARSE_LOCATION", null);
        }
        if (checkIfCallerIsSelfOrForegoundUser()) {
            return this.mPhone.getAllCellInfo();
        }
        return null;
    }

    public void setCellInfoListRate(int rateInMillis) {
        this.mPhone.setCellInfoListRate(rateInMillis);
    }

    private boolean checkIfCallerIsSelfOrForegoundUser() {
        boolean self = Binder.getCallingUid() == Process.myUid();
        if (!self) {
            int callingUser = UserHandle.getCallingUserId();
            long ident = Binder.clearCallingIdentity();
            try {
                int foregroundUser = ActivityManager.getCurrentUser();
                boolean ok = foregroundUser == callingUser;
                return ok;
            } catch (Exception e) {
                return false;
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
        return true;
    }

    private void enforceReadPermission() {
        this.mApp.enforceCallingOrSelfPermission("android.permission.READ_PHONE_STATE", null);
    }

    private void enforceModifyPermission() {
        this.mApp.enforceCallingOrSelfPermission("android.permission.MODIFY_PHONE_STATE", null);
    }

    private void enforceCallPermission() {
        this.mApp.enforceCallingOrSelfPermission("android.permission.CALL_PHONE", null);
    }

    private void enforcePrivilegedPhoneStatePermission() {
        this.mApp.enforceCallingOrSelfPermission("android.permission.READ_PRIVILEGED_PHONE_STATE", null);
    }

    private String createTelUrl(String number) {
        if (TextUtils.isEmpty(number)) {
            return null;
        }
        return "tel:" + number;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void log(String msg) {
        Log.d("PhoneInterfaceManager", "[PhoneIntfMgr] " + msg);
    }

    public int getActivePhoneType() {
        return this.mPhone.getPhoneType();
    }

    public int getCdmaEriIconIndex() {
        return this.mPhone.getCdmaEriIconIndex();
    }

    public int getCdmaEriIconMode() {
        return this.mPhone.getCdmaEriIconMode();
    }

    public String getCdmaEriText() {
        return this.mPhone.getCdmaEriText();
    }

    public boolean needsOtaServiceProvisioning() {
        return this.mPhone.needsOtaServiceProvisioning();
    }

    public int getVoiceMessageCount() {
        return this.mPhone.getVoiceMessageCount();
    }

    public int getNetworkType() {
        return this.mPhone.getServiceState().getDataNetworkType();
    }

    public int getDataNetworkType() {
        Phone phone = this.mApp.getPhone(this.mApp.getDataSubscription());
        return phone.getServiceState().getDataNetworkType();
    }

    public int getVoiceNetworkType() {
        return this.mPhone.getServiceState().getVoiceNetworkType();
    }

    public boolean hasIccCard() {
        return this.mPhone.getIccCard().hasIccCard();
    }

    public int getLteOnCdmaMode() {
        return this.mPhone.getLteOnCdmaMode();
    }

    public int getIccPin1RetryCount() {
        return this.mPhone.getIccCard().getIccPin1RetryCount();
    }

    public void setPhone(Phone phone) {
        this.mPhone = phone;
    }

    private String exchangeIccApdu(int cla, int command, int channel, int p1, int p2, int p3, String data) {
        if (Binder.getCallingUid() != 1027) {
            throw new SecurityException("Only Smartcard API may access UICC");
        }
        Log.d("PhoneInterfaceManager", "> exchangeAPDU " + channel + " " + cla + " " + command + " " + p1 + " " + p2 + " " + p3 + " " + data);
        IccIoResult response = (IccIoResult) sendRequest(7, new IccApduArgument(cla, command, channel, p1, p2, p3, data));
        Log.d("PhoneInterfaceManager", "< exchangeAPDU " + response);
        String s = Integer.toHexString((response.sw1 << 8) + response.sw2 + 65536).substring(1);
        if (response.payload != null) {
            return IccUtils.bytesToHexString(response.payload) + s;
        }
        return s;
    }

    public String transmitIccBasicChannel(int cla, int command, int p1, int p2, int p3, String data) {
        return exchangeIccApdu(cla, command, 0, p1, p2, p3, data);
    }

    public String transmitIccLogicalChannel(int cla, int command, int channel, int p1, int p2, int p3, String data) {
        return exchangeIccApdu(cla, command, channel, p1, p2, p3, data);
    }

    public int openIccLogicalChannel(String aid) {
        if (Binder.getCallingUid() != 1027) {
            throw new SecurityException("Only Smartcard API may access UICC");
        }
        Log.d("PhoneInterfaceManager", "> openIccLogicalChannel " + aid);
        Integer channel = (Integer) sendRequest(9, aid);
        Log.d("PhoneInterfaceManager", "< openIccLogicalChannel " + channel);
        return channel.intValue();
    }

    public boolean closeIccLogicalChannel(int channel) {
        if (Binder.getCallingUid() != 1027) {
            throw new SecurityException("Only Smartcard API may access UICC");
        }
        Log.d("PhoneInterfaceManager", "> closeIccLogicalChannel " + channel);
        Integer err = (Integer) sendRequest(11, new Integer(channel));
        Log.d("PhoneInterfaceManager", "< closeIccLogicalChannel " + err);
        return err.intValue() == 0;
    }

    public int getLastError() {
        return this.mLastError;
    }

    public byte[] transmitIccSimIO(int fileId, int command, int p1, int p2, int p3, String filePath) {
        byte[] result;
        if (Binder.getCallingUid() != 1027) {
            throw new SecurityException("Only Smartcard API may access UICC");
        }
        Log.d("PhoneInterfaceManager", "Exchange SIM_IO " + fileId + ":" + command + " " + p1 + " " + p2 + " " + p3 + ":" + filePath);
        IccIoResult response = (IccIoResult) sendRequest(13, new IccApduArgument(fileId, command, -1, p1, p2, p3, filePath));
        Log.d("PhoneInterfaceManager", "Exchange SIM_IO [R]" + response);
        int length = 2;
        if (response.payload != null) {
            length = response.payload.length + 2;
            result = new byte[length];
            System.arraycopy(response.payload, 0, result, 0, response.payload.length);
        } else {
            result = new byte[2];
        }
        Log.d("PhoneInterfaceManager", "Exchange SIM_IO [L] " + length);
        result[length - 1] = (byte) response.sw2;
        result[length - 2] = (byte) response.sw1;
        return result;
    }

    public byte[] getATR() {
        if (Binder.getCallingUid() != 1027) {
            throw new SecurityException("Only Smartcard API may access UICC");
        }
        Log.d("PhoneInterfaceManager", "SIM_GET_ATR ");
        String response = (String) sendRequest(15, null);
        if (response == null || response.length() == 0) {
            return null;
        }
        try {
            byte[] result = IccUtils.hexStringToBytes(response);
            return result;
        } catch (RuntimeException e) {
            Log.e("PhoneInterfaceManager", "Invalid format of the response string");
            return null;
        }
    }

    public void toggleHold() {
        enforceModifyPermission();
        try {
            PhoneUtils.switchHoldingAndActive(this.mCM.getFirstActiveBgCall());
        } catch (Exception e) {
            Log.e("PhoneInterfaceManager", "Error during toggleHold().", e);
        }
    }

    public String getIccOperatorNumeric() {
        IccRecords iccRecords;
        int netType = getDataNetworkType();
        int family = UiccController.getFamilyFromRadioTechnology(netType);
        if (-1 == family) {
            int phoneType = getActivePhoneType();
            switch (phoneType) {
                case 1:
                    family = 1;
                    break;
                case 2:
                    family = 2;
                    break;
            }
        }
        if (-1 == family || (iccRecords = UiccController.getInstance().getIccRecords(family)) == null) {
            return null;
        }
        String iccOperatorNumeric = iccRecords.getOperatorNumeric();
        return iccOperatorNumeric;
    }

    public void merge() {
        enforceModifyPermission();
        try {
            if (PhoneUtils.okToMergeCalls(this.mCM)) {
                PhoneUtils.mergeCalls(this.mCM);
            }
        } catch (Exception e) {
            Log.e("PhoneInterfaceManager", "Error during merge().", e);
        }
    }

    public void swap() {
        enforceModifyPermission();
        try {
            PhoneUtils.swap();
        } catch (Exception e) {
            Log.e("PhoneInterfaceManager", "Error during swap().", e);
        }
    }

    public void mute(boolean onOff) {
        enforceModifyPermission();
        try {
            PhoneUtils.setMute(onOff);
        } catch (Exception e) {
            Log.e("PhoneInterfaceManager", "Error during mute().", e);
        }
    }

    public void playDtmfTone(char digit, boolean timedShortTone) {
        enforceModifyPermission();
        synchronized (this.mDtmfStopHandler) {
            try {
                this.mDtmfTonePlayer.playDtmfTone(digit, timedShortTone);
            } catch (Exception e) {
                Log.e("PhoneInterfaceManager", "Error playing DTMF tone.", e);
            }
            if (this.mDtmfStopRunnable != null) {
                this.mDtmfStopHandler.removeCallbacks(this.mDtmfStopRunnable);
            }
            this.mDtmfStopRunnable = new Runnable() { // from class: com.android.phone.PhoneInterfaceManager.1
                @Override // java.lang.Runnable
                public void run() {
                    synchronized (PhoneInterfaceManager.this.mDtmfStopHandler) {
                        if (PhoneInterfaceManager.this.mDtmfStopRunnable == this) {
                            PhoneInterfaceManager.this.mDtmfTonePlayer.stopDtmfTone();
                            PhoneInterfaceManager.this.mDtmfStopRunnable = null;
                        }
                    }
                }
            };
            this.mDtmfStopHandler.postDelayed(this.mDtmfStopRunnable, 5000L);
        }
    }

    public void stopDtmfTone() {
        enforceModifyPermission();
        synchronized (this.mDtmfStopHandler) {
            try {
                this.mDtmfTonePlayer.stopDtmfTone();
            } catch (Exception e) {
                Log.e("PhoneInterfaceManager", "Error stopping DTMF tone.", e);
            }
            if (this.mDtmfStopRunnable != null) {
                this.mDtmfStopHandler.removeCallbacks(this.mDtmfStopRunnable);
                this.mDtmfStopRunnable = null;
            }
        }
    }

    public void addListener(ITelephonyListener iTelephonyListener) {
        enforcePrivilegedPhoneStatePermission();
        if (iTelephonyListener == null) {
            throw new IllegalArgumentException("Listener must not be null.");
        }
        synchronized (this.mListeners) {
            try {
                IBinder iBinderAsBinder = iTelephonyListener.asBinder();
                Iterator<ITelephonyListener> it = this.mListeners.iterator();
                while (it.hasNext()) {
                    if (it.next().asBinder().equals(iBinderAsBinder)) {
                        Log.w("PhoneInterfaceManager", "Listener already registered. Ignoring.");
                        return;
                    }
                }
                this.mListeners.add(iTelephonyListener);
                this.mDeathRecipients.put(iTelephonyListener.asBinder(), new TelephonyListenerDeathRecipient(iTelephonyListener.asBinder()));
                Iterator<Call> it2 = this.mCallModeler.getFullList().iterator();
                while (it2.hasNext()) {
                    try {
                        notifyListenerOfCallLocked(it2.next(), iTelephonyListener);
                    } catch (RemoteException e) {
                        Log.e("PhoneInterfaceManager", "Error updating new listener. Ignoring.");
                        removeListenerInternal(iTelephonyListener);
                    }
                }
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public void removeListener(ITelephonyListener listener) {
        enforcePrivilegedPhoneStatePermission();
        if (listener == null) {
            throw new IllegalArgumentException("Listener must not be null.");
        }
        removeListenerInternal(listener);
    }

    private void removeListenerInternal(ITelephonyListener listener) {
        IBinder listenerBinder = listener.asBinder();
        synchronized (this.mListeners) {
            Iterator<ITelephonyListener> it = this.mListeners.iterator();
            while (it.hasNext()) {
                ITelephonyListener nextListener = it.next();
                if (nextListener.asBinder().equals(listenerBinder)) {
                    TelephonyListenerDeathRecipient dr = this.mDeathRecipients.get(listener.asBinder());
                    if (dr != null) {
                        dr.unlinkDeathRecipient();
                    }
                    it.remove();
                }
            }
        }
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onDisconnect(Call call) {
        notifyListenersOfCall(call);
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onIncoming(Call call) {
        notifyListenersOfCall(call);
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onUpdate(List<Call> calls) {
        for (Call call : calls) {
            notifyListenersOfCall(call);
        }
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onPostDialAction(Connection.PostDialState state, int callId, String remainingChars, char c) {
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onSuppServiceFailed(int service) {
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onModifyCall(Call call) {
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onActiveSubChanged(int activeSub) {
    }

    private void notifyListenersOfCall(Call call) {
        synchronized (this.mListeners) {
            Iterator<ITelephonyListener> it = this.mListeners.iterator();
            while (it.hasNext()) {
                ITelephonyListener listener = it.next();
                try {
                    notifyListenerOfCallLocked(call, listener);
                } catch (RemoteException e) {
                    TelephonyListenerDeathRecipient deathRecipient = this.mDeathRecipients.get(listener.asBinder());
                    if (deathRecipient != null) {
                        deathRecipient.unlinkDeathRecipient();
                    }
                    it.remove();
                }
            }
        }
    }

    private void notifyListenerOfCallLocked(final Call call, final ITelephonyListener listener) throws RemoteException {
        if (Binder.isProxy(listener)) {
            listener.onUpdate(call.getCallId(), call.getState(), call.getNumber());
        } else {
            this.mMainThreadHandler.post(new Runnable() { // from class: com.android.phone.PhoneInterfaceManager.2
                @Override // java.lang.Runnable
                public void run() {
                    try {
                        listener.onUpdate(call.getCallId(), call.getState(), call.getNumber());
                    } catch (RemoteException e) {
                        Log.wtf("PhoneInterfaceManager", "Local binder call failed with RemoteException.", e);
                    }
                }
            });
        }
    }

    private class TelephonyListenerDeathRecipient implements IBinder.DeathRecipient {
        private final IBinder mBinder;

        public TelephonyListenerDeathRecipient(IBinder listener) {
            this.mBinder = listener;
            try {
                this.mBinder.linkToDeath(this, 0);
            } catch (RemoteException e) {
                unlinkDeathRecipient();
            }
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            synchronized (PhoneInterfaceManager.this.mListeners) {
                if (PhoneInterfaceManager.this.mListeners.contains(this.mBinder)) {
                    PhoneInterfaceManager.this.mListeners.remove(this.mBinder);
                    Log.w("PhoneInterfaceManager", "ITelephonyListener died. Removing.");
                } else {
                    Log.w("PhoneInterfaceManager", "TelephonyListener binder died but the listener is not registered.");
                }
            }
        }

        public void unlinkDeathRecipient() {
            this.mBinder.unlinkToDeath(this, 0);
        }
    }
}
