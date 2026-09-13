package com.android.phone;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ServiceManager;
import android.telephony.CellInfo;
import android.telephony.MSimTelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.DefaultPhoneNotifier;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.msim.ITelephonyMSim;
import com.android.internal.telephony.uicc.IccIoResult;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccUtils;
import com.codeaurora.telephony.msim.MSimUiccController;
import com.codeaurora.telephony.msim.SubscriptionManager;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class MSimPhoneInterfaceManager extends ITelephonyMSim.Stub {
    private static MSimPhoneInterfaceManager sInstance;
    PhoneGlobals mApp;
    CallHandlerServiceProxy mCallHandlerService;
    Phone mPhone;
    private int mPhoneCount = MSimTelephonyManager.getDefault().getPhoneCount();
    private int[] mLastError = new int[this.mPhoneCount];
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
        public Object argument2;
        public Object result;

        public MainThreadRequest(Object argument, Object argument2) {
            this.argument = argument;
            this.argument2 = argument2;
        }
    }

    private final class MainThreadHandler extends Handler {
        private MainThreadHandler() {
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            boolean hungUp;
            MSimPhoneInterfaceManager.this.getDefaultSubscription();
            switch (msg.what) {
                case 1:
                    MainThreadRequest request = (MainThreadRequest) msg.obj;
                    Phone phone = PhoneGlobals.getInstance().getPhone(((Integer) request.argument2).intValue());
                    Log.i("MSimPhoneInterfaceManager", "CMD_HANDLE_PIN_MMI: sub :" + phone.getSubscription());
                    request.result = Boolean.valueOf(phone.handlePinMmi((String) request.argument));
                    synchronized (request) {
                        request.notifyAll();
                        break;
                    }
                    return;
                case 2:
                    Message onCompleted = obtainMessage(3, (MainThreadRequest) msg.obj);
                    MSimPhoneInterfaceManager.this.mPhone.getNeighboringCids(onCompleted);
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
                    MSimPhoneInterfaceManager.this.answerRingingCallInternal();
                    return;
                case 5:
                    MainThreadRequest request3 = (MainThreadRequest) msg.obj;
                    int sub = ((Integer) request3.argument).intValue();
                    MSimPhoneInterfaceManager.this.log("Ending call on subscription =" + sub);
                    Phone phone2 = MSimPhoneInterfaceManager.this.mApp.getPhone(sub);
                    int phoneType = phone2.getPhoneType();
                    if (phoneType == 2) {
                        hungUp = PhoneUtils.hangupRingingAndActive(phone2);
                    } else if (phoneType == 1) {
                        hungUp = PhoneUtils.hangup(MSimPhoneInterfaceManager.this.mCM);
                    } else {
                        throw new IllegalStateException("Unexpected phone type: " + phoneType);
                    }
                    MSimPhoneInterfaceManager.this.log("CMD_END_CALL: " + (hungUp ? "hung up!" : "no call to hang up"));
                    request3.result = Boolean.valueOf(hungUp);
                    synchronized (request3) {
                        request3.notifyAll();
                        break;
                    }
                    return;
                case 6:
                    MSimPhoneInterfaceManager.this.silenceRingerInternal();
                    return;
                case 7:
                case 8:
                case 9:
                case 10:
                case 11:
                case 12:
                case 13:
                default:
                    Log.w("MSimPhoneInterfaceManager", "MainThreadHandler: unexpected message code: " + msg.what);
                    return;
                case 14:
                    MainThreadRequest request4 = (MainThreadRequest) msg.obj;
                    int subscription = ((Integer) request4.argument).intValue();
                    boolean isTempSwitch = ((Boolean) request4.argument2).booleanValue();
                    Message onCompleted2 = obtainMessage(15, request4);
                    SubscriptionManager subManager = SubscriptionManager.getInstance();
                    if (subManager != null) {
                        subManager.setDataSubscription(subscription, isTempSwitch, onCompleted2);
                        return;
                    }
                    request4.result = false;
                    synchronized (request4) {
                        request4.notifyAll();
                        break;
                    }
                    return;
                case 15:
                    boolean retStatus = false;
                    AsyncResult ar2 = (AsyncResult) msg.obj;
                    MainThreadRequest request5 = (MainThreadRequest) ar2.userObj;
                    if (ar2.exception == null && ar2.result != null) {
                        boolean result = ((Boolean) ar2.result).booleanValue();
                        if (result) {
                            retStatus = true;
                        }
                    }
                    request5.result = Boolean.valueOf(retStatus);
                    synchronized (request5) {
                        request5.notifyAll();
                        break;
                    }
                    return;
                case 16:
                    MainThreadRequest request6 = (MainThreadRequest) msg.obj;
                    IccApduArgument argument = (IccApduArgument) request6.argument;
                    int sub2 = ((Integer) request6.argument2).intValue();
                    Message onCompleted3 = obtainMessage(17, request6);
                    MSimPhoneInterfaceManager.this.getPhone(sub2).getIccCard().exchangeApdu(argument.cla, argument.command, argument.channel, argument.p1, argument.p2, argument.p3, argument.data, onCompleted3);
                    return;
                case 17:
                    AsyncResult ar3 = (AsyncResult) msg.obj;
                    MainThreadRequest request7 = (MainThreadRequest) ar3.userObj;
                    int sub3 = ((Integer) request7.argument2).intValue();
                    if (ar3.exception == null && ar3.result != null) {
                        request7.result = ar3.result;
                        MSimPhoneInterfaceManager.this.mLastError[sub3] = 0;
                    } else {
                        request7.result = new IccIoResult(111, 0, (byte[]) null);
                        MSimPhoneInterfaceManager.this.mLastError[sub3] = 1;
                        if (ar3.exception != null && (ar3.exception instanceof CommandException) && ar3.exception.getCommandError() == CommandException.Error.INVALID_PARAMETER) {
                            MSimPhoneInterfaceManager.this.mLastError[sub3] = 5;
                        }
                    }
                    synchronized (request7) {
                        request7.notifyAll();
                        break;
                    }
                    return;
                case 18:
                    MainThreadRequest request8 = (MainThreadRequest) msg.obj;
                    int sub4 = ((Integer) request8.argument2).intValue();
                    Message onCompleted4 = obtainMessage(19, request8);
                    MSimPhoneInterfaceManager.this.getPhone(sub4).getIccCard().openLogicalChannel((String) request8.argument, onCompleted4);
                    return;
                case 19:
                    AsyncResult ar4 = (AsyncResult) msg.obj;
                    MainThreadRequest request9 = (MainThreadRequest) ar4.userObj;
                    int sub5 = ((Integer) request9.argument2).intValue();
                    if (ar4.exception == null && ar4.result != null) {
                        int[] resultArray = (int[]) ar4.result;
                        request9.result = new Integer(resultArray[0]);
                        MSimPhoneInterfaceManager.this.mLastError[sub5] = 0;
                    } else {
                        request9.result = new Integer(0);
                        MSimPhoneInterfaceManager.this.mLastError[sub5] = 1;
                        if (ar4.exception != null && (ar4.exception instanceof CommandException)) {
                            if (ar4.exception.getCommandError() == CommandException.Error.MISSING_RESOURCE) {
                                MSimPhoneInterfaceManager.this.mLastError[sub5] = 2;
                            } else if (ar4.exception.getCommandError() == CommandException.Error.NO_SUCH_ELEMENT) {
                                MSimPhoneInterfaceManager.this.mLastError[sub5] = 3;
                            }
                        }
                    }
                    synchronized (request9) {
                        request9.notifyAll();
                        break;
                    }
                    return;
                case 20:
                    MainThreadRequest request10 = (MainThreadRequest) msg.obj;
                    int sub6 = ((Integer) request10.argument2).intValue();
                    Message onCompleted5 = obtainMessage(21, request10);
                    MSimPhoneInterfaceManager.this.getPhone(sub6).getIccCard().closeLogicalChannel(((Integer) request10.argument).intValue(), onCompleted5);
                    return;
                case 21:
                    AsyncResult ar5 = (AsyncResult) msg.obj;
                    MainThreadRequest request11 = (MainThreadRequest) ar5.userObj;
                    int sub7 = ((Integer) request11.argument2).intValue();
                    if (ar5.exception == null) {
                        request11.result = new Integer(0);
                        MSimPhoneInterfaceManager.this.mLastError[sub7] = 0;
                    } else {
                        request11.result = new Integer(-1);
                        MSimPhoneInterfaceManager.this.mLastError[sub7] = 1;
                        if (ar5.exception != null && (ar5.exception instanceof CommandException) && ar5.exception.getCommandError() == CommandException.Error.INVALID_PARAMETER) {
                            MSimPhoneInterfaceManager.this.mLastError[sub7] = 5;
                        }
                    }
                    synchronized (request11) {
                        request11.notifyAll();
                        break;
                    }
                    return;
                case 22:
                    MainThreadRequest request12 = (MainThreadRequest) msg.obj;
                    IccApduArgument parameters = (IccApduArgument) request12.argument;
                    int sub8 = ((Integer) request12.argument2).intValue();
                    Message onCompleted6 = obtainMessage(23, request12);
                    MSimPhoneInterfaceManager.this.getPhone(sub8).getIccCard().exchangeIccIo(parameters.cla, parameters.command, parameters.p1, parameters.p2, parameters.p3, parameters.data, onCompleted6);
                    return;
                case 23:
                    AsyncResult ar6 = (AsyncResult) msg.obj;
                    MainThreadRequest request13 = (MainThreadRequest) ar6.userObj;
                    int sub9 = ((Integer) request13.argument2).intValue();
                    if (ar6.exception == null && ar6.result != null) {
                        request13.result = ar6.result;
                        MSimPhoneInterfaceManager.this.mLastError[sub9] = 0;
                    } else {
                        request13.result = new IccIoResult(111, 0, (byte[]) null);
                        MSimPhoneInterfaceManager.this.mLastError[sub9] = 1;
                        if (ar6.exception != null && (ar6.exception instanceof CommandException) && ar6.exception.getCommandError() == CommandException.Error.INVALID_PARAMETER) {
                            MSimPhoneInterfaceManager.this.mLastError[sub9] = 5;
                        }
                    }
                    synchronized (request13) {
                        request13.notifyAll();
                        break;
                    }
                    return;
                case 24:
                    MainThreadRequest request14 = (MainThreadRequest) msg.obj;
                    int sub10 = ((Integer) request14.argument2).intValue();
                    Message onCompleted7 = obtainMessage(25, request14);
                    MSimPhoneInterfaceManager.this.getPhone(sub10).getIccCard().getAtr(onCompleted7);
                    return;
                case 25:
                    AsyncResult ar7 = (AsyncResult) msg.obj;
                    MainThreadRequest request15 = (MainThreadRequest) ar7.userObj;
                    int sub11 = ((Integer) request15.argument2).intValue();
                    if (ar7.exception == null) {
                        request15.result = ar7.result;
                        MSimPhoneInterfaceManager.this.mLastError[sub11] = 0;
                    } else {
                        request15.result = "";
                        if (ar7.exception != null && (ar7.exception instanceof CommandException)) {
                            if (ar7.exception.getCommandError() == CommandException.Error.RADIO_NOT_AVAILABLE) {
                                MSimPhoneInterfaceManager.this.mLastError[sub11] = 1;
                            } else if (ar7.exception.getCommandError() == CommandException.Error.GENERIC_FAILURE) {
                                MSimPhoneInterfaceManager.this.mLastError[sub11] = 2;
                            }
                        }
                    }
                    synchronized (request15) {
                        request15.notifyAll();
                        break;
                    }
                    return;
            }
        }
    }

    private Object sendRequest(int command, Object argument, Object argument2) {
        if (Looper.myLooper() == this.mMainThreadHandler.getLooper()) {
            throw new RuntimeException("This method will deadlock if called from the main thread.");
        }
        MainThreadRequest request = new MainThreadRequest(argument, argument2);
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

    static MSimPhoneInterfaceManager init(PhoneGlobals phoneGlobals, Phone phone, CallHandlerServiceProxy callHandlerServiceProxy) {
        MSimPhoneInterfaceManager mSimPhoneInterfaceManager;
        synchronized (MSimPhoneInterfaceManager.class) {
            if (sInstance == null) {
                sInstance = new MSimPhoneInterfaceManager(phoneGlobals, phone, callHandlerServiceProxy);
            } else {
                Log.wtf("MSimPhoneInterfaceManager", "init() called multiple times!  sInstance = " + sInstance);
            }
            mSimPhoneInterfaceManager = sInstance;
        }
        return mSimPhoneInterfaceManager;
    }

    private MSimPhoneInterfaceManager(PhoneGlobals app, Phone phone, CallHandlerServiceProxy callHandlerService) {
        this.mApp = app;
        this.mPhone = phone;
        this.mCallHandlerService = callHandlerService;
        publish();
    }

    /* JADX WARN: Multi-variable type inference failed */
    private void publish() {
        log("publish: " + this);
        ServiceManager.addService("phone_msim", this);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public Phone getPhone(int subscription) {
        return MSimPhoneGlobals.getInstance().getPhone(subscription);
    }

    public void dial(String number, int subscription) {
        PhoneConstants.State state;
        log("dial: " + number);
        String url = createTelUrl(number);
        if (url != null && (state = this.mCM.getState(subscription)) != PhoneConstants.State.OFFHOOK && state != PhoneConstants.State.RINGING) {
            Intent intent = new Intent("android.intent.action.DIAL", Uri.parse(url));
            intent.addFlags(268435456);
            intent.putExtra("subscription", subscription);
            this.mApp.startActivity(intent);
        }
    }

    public void call(String callingPackage, String number, int subscription) {
        log("call: " + number);
        enforceCallPermission();
        String url = createTelUrl(number);
        if (url != null) {
            Intent intent = new Intent("android.intent.action.CALL", Uri.parse(url));
            intent.putExtra("subscription", subscription);
            intent.addFlags(268435456);
            this.mApp.startActivity(intent);
        }
    }

    private boolean showCallScreenInternal(boolean specifyInitialDialpadState, boolean showDialpad) {
        if (MSimPhoneGlobals.getInstance().isCsvtActive()) {
            Log.d("MSimPhoneInterfaceManager", "showCallScreenInternal: csvt is active");
            Intent mIntent = new Intent("restore_video_call");
            this.mApp.sendBroadcast(mIntent);
            return false;
        }
        if (!PhoneGlobals.sVoiceCapable) {
            return false;
        }
        int sub = this.mCM.getPhoneInCall().getSubscription();
        if (isIdle(sub)) {
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

    public boolean endCall(int subscription) {
        enforceCallPermission();
        return ((Boolean) sendRequest(5, Integer.valueOf(subscription), null)).booleanValue();
    }

    public void answerRingingCall(int subscription) {
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
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void silenceRingerInternal() {
        if (this.mCM.getState() == PhoneConstants.State.RINGING && this.mApp.notifier.isRinging()) {
            log("silenceRingerInternal: silencing...");
            this.mApp.notifier.silenceRinger();
        }
    }

    public boolean isOffhook(int subscription) {
        return getPhone(subscription).getState() == PhoneConstants.State.OFFHOOK;
    }

    public boolean isRinging(int subscription) {
        return getPhone(subscription).getState() == PhoneConstants.State.RINGING;
    }

    public boolean isIdle(int subscription) {
        return getPhone(subscription).getState() == PhoneConstants.State.IDLE;
    }

    public boolean isSimPinEnabled(int subscription) {
        enforceReadPermission();
        return ((MSimPhoneGlobals) this.mApp).isSimPinEnabled(subscription);
    }

    public boolean supplyPin(String pin, int subscription) {
        int[] resultArray = supplyPinReportResult(pin, subscription);
        return resultArray[0] == 0;
    }

    public boolean supplyPuk(String puk, String pin, int subscription) {
        int[] resultArray = supplyPukReportResult(puk, pin, subscription);
        return resultArray[0] == 0;
    }

    public int[] supplyPinReportResult(String pin, int subscription) {
        enforceModifyPermission();
        UnlockSim checkSimPin = new UnlockSim(getPhone(subscription).getIccCard());
        checkSimPin.start();
        return checkSimPin.unlockSim(null, pin);
    }

    public int[] supplyPukReportResult(String puk, String pin, int subscription) {
        enforceModifyPermission();
        UnlockSim checkSimPuk = new UnlockSim(getPhone(subscription).getIccCard());
        checkSimPuk.start();
        return checkSimPuk.unlockSim(puk, pin);
    }

    public int getIccPin1RetryCount(int subscription) {
        return getPhone(subscription).getIccCard().getIccPin1RetryCount();
    }

    public String getIccOperatorNumeric(int subId) {
        IccRecords iccRecords;
        int netType = getDataNetworkType(subId);
        int family = MSimUiccController.getFamilyFromRadioTechnology(netType);
        if (-1 == family) {
            int phoneType = getActivePhoneType(subId);
            switch (phoneType) {
                case 1:
                    family = 1;
                    break;
                case 2:
                    family = 2;
                    break;
            }
        }
        if (-1 == family || (iccRecords = MSimUiccController.getInstance().getIccRecords(subId, family)) == null) {
            return null;
        }
        String iccOperatorNumeric = iccRecords.getOperatorNumeric();
        return iccOperatorNumeric;
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
                this.mHandler = new Handler() { // from class: com.android.phone.MSimPhoneInterfaceManager.UnlockSim.1
                    @Override // android.os.Handler
                    public void handleMessage(Message msg) {
                        AsyncResult ar = (AsyncResult) msg.obj;
                        switch (msg.what) {
                            case 100:
                                Log.d("MSimPhoneInterfaceManager", "SUPPLY_PIN_COMPLETE");
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
                    Log.d("MSimPhoneInterfaceManager", "wait for done");
                    wait();
                } catch (InterruptedException e2) {
                    Thread.currentThread().interrupt();
                }
            }
            Log.d("MSimPhoneInterfaceManager", "done");
            resultArray = new int[]{this.mResult, this.mRetryCount};
            return resultArray;
        }
    }

    public void updateServiceLocation(int subscription) {
        getPhone(subscription).updateServiceLocation();
    }

    public boolean isRadioOn(int subscription) {
        return getPhone(subscription).getServiceState().getState() != 3;
    }

    public void toggleRadioOnOff(int subscription) {
        enforceModifyPermission();
        getPhone(subscription).setRadioPower(!isRadioOn(subscription));
    }

    public boolean setRadio(boolean turnOn, int subscription) {
        enforceModifyPermission();
        if ((getPhone(subscription).getServiceState().getState() != 3) != turnOn) {
            toggleRadioOnOff(subscription);
        }
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
        int defaultData = ((MSimPhoneGlobals) this.mApp).getDefaultDataSubscription();
        int ret = getPhone(defaultData).enableApnType(type);
        Log.d("MSimPhoneInterfaceManager", "enableApnType result is " + ret);
        return ret;
    }

    public int enableApnTypeOnSubscription(String type, int subId) {
        enforceModifyPermission();
        Log.d("MSimPhoneInterfaceManager", "enableApnTypeOnSubscription: type=" + type + "subid=" + subId);
        MSimTelephonyManager.getDefault().getPhoneCount();
        Log.d("MSimPhoneInterfaceManager", "phone = " + getPhone(subId));
        int ret = getPhone(subId).enableApnType(type);
        Log.d("MSimPhoneInterfaceManager", "enableApnType result is " + ret);
        return ret;
    }

    public int disableApnType(String type) {
        enforceModifyPermission();
        int defaultData = ((MSimPhoneGlobals) this.mApp).getDefaultDataSubscription();
        int ret = getPhone(defaultData).disableApnType(type);
        Log.d("MSimPhoneInterfaceManager", "disableApnType result is " + ret);
        return ret;
    }

    public int disableApnTypeOnSubscription(String type, int subId) {
        enforceModifyPermission();
        Log.d("MSimPhoneInterfaceManager", "disableApnTypeOnSubscription: type=" + type + "subid=" + subId);
        int ret = getPhone(subId).disableApnType(type);
        Log.d("MSimPhoneInterfaceManager", "disableApnType result is " + ret);
        return ret;
    }

    public boolean disableDataConnectivity() {
        enforceModifyPermission();
        ConnectivityManager cm = (ConnectivityManager) this.mApp.getSystemService("connectivity");
        cm.setMobileDataEnabled(false);
        return true;
    }

    public boolean isDataConnectivityPossible() {
        return getPhone(((MSimPhoneGlobals) this.mApp).getDataSubscription()).isDataConnectivityPossible();
    }

    public boolean handlePinMmi(String dialString, int subscription) {
        enforceModifyPermission();
        return ((Boolean) sendRequest(1, dialString, Integer.valueOf(subscription))).booleanValue();
    }

    public void cancelMissedCallsNotification(int subscription) {
        enforceModifyPermission();
        ((MSimNotificationMgr) this.mApp.notificationMgr).cancelMissedCallNotification();
    }

    public int getCallState(int subscription) {
        return DefaultPhoneNotifier.convertCallState(getPhone(subscription).getState());
    }

    public int getDataState() {
        return DefaultPhoneNotifier.convertDataState(getPhone(((MSimPhoneGlobals) this.mApp).getDataSubscription()).getDataConnectionState());
    }

    public int getDataActivity() {
        return DefaultPhoneNotifier.convertDataActivityState(getPhone(((MSimPhoneGlobals) this.mApp).getDataSubscription()).getDataActivityState());
    }

    public List<CellInfo> getAllCellInfo(int subscription) {
        try {
            this.mApp.enforceCallingOrSelfPermission("android.permission.ACCESS_FINE_LOCATION", null);
        } catch (SecurityException e) {
            this.mApp.enforceCallingOrSelfPermission("android.permission.ACCESS_COARSE_LOCATION", null);
        }
        return getPhone(subscription).getAllCellInfo();
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

    private String createTelUrl(String number) {
        if (TextUtils.isEmpty(number)) {
            return null;
        }
        return "tel:" + number;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void log(String msg) {
        Log.d("MSimPhoneInterfaceManager", "[PhoneIntfMgr] " + msg);
    }

    public int getActivePhoneType(int subscription) {
        return getPhone(subscription).getPhoneType();
    }

    public int getCdmaEriIconIndex(int subscription) {
        return getPhone(subscription).getCdmaEriIconIndex();
    }

    public int getCdmaEriIconMode(int subscription) {
        return getPhone(subscription).getCdmaEriIconMode();
    }

    public String getCdmaEriText(int subscription) {
        return getPhone(subscription).getCdmaEriText();
    }

    public boolean needsOtaServiceProvisioning() {
        return this.mPhone.needsOtaServiceProvisioning();
    }

    public int getVoiceMessageCount(int subscription) {
        return getPhone(subscription).getVoiceMessageCount();
    }

    public int getNetworkType(int subscription) {
        return getDataNetworkType(subscription);
    }

    public int getDataNetworkType(int subscription) {
        return getPhone(subscription).getServiceState().getDataNetworkType();
    }

    public int getVoiceNetworkType(int subscription) {
        return getPhone(subscription).getServiceState().getVoiceNetworkType();
    }

    public boolean hasIccCard(int subscription) {
        return getPhone(subscription).getIccCard().hasIccCard();
    }

    public int getDefaultSubscription() {
        return this.mApp.getDefaultSubscription();
    }

    public int getPreferredVoiceSubscription() {
        return this.mApp.getVoiceSubscription();
    }

    public int getPreferredDataSubscription() {
        return ((MSimPhoneGlobals) this.mApp).getDataSubscription();
    }

    public int getDefaultDataSubscription() {
        return ((MSimPhoneGlobals) this.mApp).getDefaultDataSubscription();
    }

    public boolean setPreferredDataSubscription(int subscription) {
        return ((Boolean) sendRequest(14, Integer.valueOf(subscription), true)).booleanValue();
    }

    public boolean setDefaultDataSubscription(int subscription) {
        return ((Boolean) sendRequest(14, Integer.valueOf(subscription), false)).booleanValue();
    }

    public int getLteOnCdmaMode(int subscription) {
        return getPhone(subscription).getLteOnCdmaMode();
    }

    private String exchangeIccApdu(int cla, int command, int channel, int p1, int p2, int p3, String data, int subscription) {
        if (Binder.getCallingUid() != 1027) {
            throw new SecurityException("Only Smartcard API may access UICC");
        }
        Log.d("MSimPhoneInterfaceManager", "> exchangeAPDU " + channel + " " + cla + " " + command + " " + p1 + " " + p2 + " " + p3 + " " + data + "subscription = " + subscription);
        IccIoResult response = (IccIoResult) sendRequest(16, new IccApduArgument(cla, command, channel, p1, p2, p3, data), Integer.valueOf(subscription));
        Log.d("MSimPhoneInterfaceManager", "< exchangeAPDU " + response);
        String s = Integer.toHexString((response.sw1 << 8) + response.sw2 + 65536).substring(1);
        if (response.payload != null) {
            return IccUtils.bytesToHexString(response.payload) + s;
        }
        return s;
    }

    public String transmitIccBasicChannel(int cla, int command, int p1, int p2, int p3, String data, int subscription) {
        return exchangeIccApdu(cla, command, 0, p1, p2, p3, data, subscription);
    }

    public String transmitIccLogicalChannel(int cla, int command, int channel, int p1, int p2, int p3, String data, int subscription) {
        return exchangeIccApdu(cla, command, channel, p1, p2, p3, data, subscription);
    }

    public int openIccLogicalChannel(String aid, int subscription) {
        if (Binder.getCallingUid() != 1027) {
            throw new SecurityException("Only Smartcard API may access UICC");
        }
        Log.d("MSimPhoneInterfaceManager", "> openIccLogicalChannel " + aid + " subscription = " + subscription);
        Integer channel = (Integer) sendRequest(18, aid, Integer.valueOf(subscription));
        Log.d("MSimPhoneInterfaceManager", "< openIccLogicalChannel " + channel);
        return channel.intValue();
    }

    public boolean closeIccLogicalChannel(int channel, int subscription) {
        if (Binder.getCallingUid() != 1027) {
            throw new SecurityException("Only Smartcard API may access UICC");
        }
        Log.d("MSimPhoneInterfaceManager", "> closeIccLogicalChannel " + channel + " subscription = " + subscription);
        Integer err = (Integer) sendRequest(20, new Integer(channel), Integer.valueOf(subscription));
        Log.d("MSimPhoneInterfaceManager", "< closeIccLogicalChannel " + err);
        return err.intValue() == 0;
    }

    public byte[] transmitIccSimIO(int fileId, int command, int p1, int p2, int p3, String filePath, int subscription) {
        byte[] result;
        if (Binder.getCallingUid() != 1027) {
            throw new SecurityException("Only Smartcard API may access UICC");
        }
        Log.d("MSimPhoneInterfaceManager", "Exchange SIM_IO " + fileId + ":" + command + " " + p1 + " " + p2 + " " + p3 + ":" + filePath + " subscription = " + subscription);
        IccIoResult response = (IccIoResult) sendRequest(22, new IccApduArgument(fileId, command, -1, p1, p2, p3, filePath), Integer.valueOf(subscription));
        Log.d("MSimPhoneInterfaceManager", "Exchange SIM_IO [R]" + response);
        int length = 2;
        if (response.payload != null) {
            length = response.payload.length + 2;
            result = new byte[length];
            System.arraycopy(response.payload, 0, result, 0, response.payload.length);
        } else {
            result = new byte[2];
        }
        Log.d("MSimPhoneInterfaceManager", "Exchange SIM_IO [L] " + length);
        result[length - 1] = (byte) response.sw2;
        result[length - 2] = (byte) response.sw1;
        return result;
    }

    public byte[] getATR(int subscription) {
        if (Binder.getCallingUid() != 1027) {
            throw new SecurityException("Only Smartcard API may access UICC");
        }
        Log.d("MSimPhoneInterfaceManager", "> getATR  subscription = " + subscription);
        String response = (String) sendRequest(24, null, Integer.valueOf(subscription));
        Log.d("MSimPhoneInterfaceManager", "< getATR  response = " + response);
        if (response == null || response.length() == 0) {
            return null;
        }
        try {
            byte[] result = IccUtils.hexStringToBytes(response);
            return result;
        } catch (RuntimeException e) {
            Log.e("MSimPhoneInterfaceManager", "Invalid format of the response string");
            return null;
        }
    }

    public int getLastError(int subscription) {
        return this.mLastError[subscription];
    }
}
