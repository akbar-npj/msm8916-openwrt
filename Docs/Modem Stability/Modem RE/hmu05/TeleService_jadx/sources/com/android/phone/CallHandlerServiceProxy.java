package com.android.phone;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;
import com.android.internal.telephony.Connection;
import com.android.services.telephony.common.AudioMode;
import com.android.services.telephony.common.Call;
import com.android.services.telephony.common.ICallHandlerService;
import com.google.common.collect.Lists;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class CallHandlerServiceProxy extends Handler implements AudioRouter.AudioModeListener, CallModeler.Listener {
    private static final boolean DBG;
    private static final String TAG = CallHandlerServiceProxy.class.getSimpleName();
    private AudioRouter mAudioRouter;
    private CallCommandService mCallCommandService;
    private ICallHandlerService mCallHandlerServiceGuarded;
    private CallModeler mCallModeler;
    private Context mContext;
    private boolean mFullUpdateOnConnect;
    private List<QueueParams> mQueue;
    private final Object mServiceAndQueueLock = new Object();
    private int mBindRetryCount = 0;
    private ServiceConnection mConnection = null;

    static {
        DBG = SystemProperties.getInt("ro.debuggable", 0) == 1;
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        super.handleMessage(msg);
        switch (msg.what) {
            case 1:
                removeMessages(1);
                handleConnectRetry();
                return;
            case 2:
                removeMessages(2);
                synchronized (this.mServiceAndQueueLock) {
                    if (this.mCallHandlerServiceGuarded == null) {
                        Log.w(TAG, "Binding time out. InCallUI did not respond in time.");
                        try {
                            this.mContext.unbindService(this.mConnection);
                        } catch (Exception e) {
                            Log.w(TAG, "unbindservice exception", e);
                        }
                        this.mConnection = null;
                        handleConnectRetry();
                    }
                    break;
                }
                return;
            default:
                return;
        }
    }

    public CallHandlerServiceProxy(Context context, CallModeler callModeler, CallCommandService callCommandService, AudioRouter audioRouter) {
        if (DBG) {
            Log.d(TAG, "init CallHandlerServiceProxy");
        }
        this.mContext = context;
        this.mCallCommandService = callCommandService;
        this.mCallModeler = callModeler;
        this.mAudioRouter = audioRouter;
        this.mAudioRouter.addAudioModeListener(this);
        this.mCallModeler.addListener(this);
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onDisconnect(Call call) {
        wakeUpScreen();
        synchronized (this.mServiceAndQueueLock) {
            if (this.mCallHandlerServiceGuarded == null) {
                if (DBG) {
                    Log.d(TAG, "CallHandlerService not connected.  Enqueue disconnect");
                }
                enqueueDisconnect(call);
                setupServiceConnection();
                return;
            }
            processDisconnect(call);
        }
    }

    private void wakeUpScreen() {
        Log.d(TAG, "wakeUpScreen()");
        PowerManager pm = (PowerManager) this.mContext.getSystemService("power");
        pm.wakeUp(SystemClock.uptimeMillis());
    }

    private void processDisconnect(Call call) {
        try {
            if (DBG) {
                Log.d(TAG, "onDisconnect: " + call);
            }
            synchronized (this.mServiceAndQueueLock) {
                if (this.mCallHandlerServiceGuarded != null) {
                    this.mCallHandlerServiceGuarded.onDisconnect(call);
                }
            }
            if (!this.mCallModeler.hasLiveCall()) {
                unbind();
            }
        } catch (Exception e) {
            Log.e(TAG, "Remote exception handling onDisconnect ", e);
        }
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onIncoming(Call call) {
        resetConnectRetryCount();
        synchronized (this.mServiceAndQueueLock) {
            if (this.mCallHandlerServiceGuarded == null) {
                if (DBG) {
                    Log.d(TAG, "CallHandlerService not connected.  Enqueue incoming.");
                }
                enqueueIncoming(call);
                setupServiceConnection();
                return;
            }
            processIncoming(call);
        }
    }

    private void processIncoming(Call call) {
        if (DBG) {
            Log.d(TAG, "onIncoming: " + call);
        }
        try {
            synchronized (this.mServiceAndQueueLock) {
                try {
                    if (this.mCallHandlerServiceGuarded != null) {
                        this.mCallHandlerServiceGuarded.onIncoming(call, RejectWithTextMessageManager.loadCannedResponses());
                    }
                } catch (Throwable th) {
                    throw th;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Remote exception handling onUpdate", e);
        }
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onUpdate(List<Call> calls) {
        synchronized (this.mServiceAndQueueLock) {
            if (this.mCallHandlerServiceGuarded == null) {
                if (DBG) {
                    Log.d(TAG, "CallHandlerService not connected.  Enqueue update.");
                }
                enqueueUpdate(calls);
                setupServiceConnection();
                return;
            }
            processUpdate(calls);
        }
    }

    private void processUpdate(List<Call> calls) {
        if (DBG) {
            Log.d(TAG, "onUpdate: " + calls.toString());
        }
        try {
            synchronized (this.mServiceAndQueueLock) {
                if (this.mCallHandlerServiceGuarded != null) {
                    this.mCallHandlerServiceGuarded.onUpdate(calls);
                }
            }
            if (!this.mCallModeler.hasLiveCall()) {
                unbind();
            }
        } catch (Exception e) {
            Log.e(TAG, "Remote exception handling onUpdate", e);
        }
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onPostDialAction(Connection.PostDialState state, int callId, String remainingChars, char currentChar) {
        if (state == Connection.PostDialState.WAIT) {
            try {
                synchronized (this.mServiceAndQueueLock) {
                    if (this.mCallHandlerServiceGuarded == null) {
                        if (DBG) {
                            Log.d(TAG, "CallHandlerService not conneccted. Skipping onPostDialWait().");
                        }
                    } else {
                        this.mCallHandlerServiceGuarded.onPostDialWait(callId, remainingChars);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Remote exception handling onUpdate", e);
            }
        }
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onSuppServiceFailed(int service) {
        try {
            synchronized (this.mServiceAndQueueLock) {
                if (this.mCallHandlerServiceGuarded == null) {
                    if (DBG) {
                        Log.d(TAG, "CallHandlerService not connected. Skipping onSuppServiceFailed().");
                    }
                } else {
                    this.mCallHandlerServiceGuarded.onSuppServiceFailed(service);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Remote exception handling onSuppServiceFailed", e);
        }
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onActiveSubChanged(int activeSub) {
        try {
            synchronized (this.mServiceAndQueueLock) {
                if (this.mCallHandlerServiceGuarded == null) {
                    if (DBG) {
                        Log.d(TAG, "CallHandlerService not conneccted. Skipping onActiveSubChanged().");
                    }
                } else {
                    this.mCallHandlerServiceGuarded.onActiveSubChanged(activeSub);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Remote exception handling onUpdate", e);
        }
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onModifyCall(Call call) {
        try {
            synchronized (this.mServiceAndQueueLock) {
                if (this.mCallHandlerServiceGuarded == null) {
                    if (DBG) {
                        Log.d(TAG, "CallHandlerService not conneccted. Skipping onModifyCallResponse().");
                    }
                } else {
                    this.mCallHandlerServiceGuarded.onModifyCall(call);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Remote exception handling onAudioModeChange", e);
        }
    }

    @Override // com.android.phone.AudioRouter.AudioModeListener
    public void onAudioModeChange(int newMode, boolean muted) {
        try {
            synchronized (this.mServiceAndQueueLock) {
                if (this.mCallHandlerServiceGuarded == null) {
                    if (DBG) {
                        Log.d(TAG, "CallHandlerService not conneccted. Skipping onAudioModeChange().");
                    }
                } else {
                    Log.i(TAG, "Updating with new audio mode: " + AudioMode.toString(newMode) + " with mute " + muted);
                    this.mCallHandlerServiceGuarded.onAudioModeChange(newMode, muted);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Remote exception handling onAudioModeChange", e);
        }
    }

    @Override // com.android.phone.AudioRouter.AudioModeListener
    public void onSupportedAudioModeChange(int modeMask) {
        try {
            synchronized (this.mServiceAndQueueLock) {
                if (this.mCallHandlerServiceGuarded == null) {
                    if (DBG) {
                        Log.d(TAG, "CallHandlerService not conneccted. SkippingonSupportedAudioModeChange().");
                    }
                } else {
                    if (DBG) {
                        Log.d(TAG, "onSupportAudioModeChange: " + AudioMode.toString(modeMask));
                    }
                    this.mCallHandlerServiceGuarded.onSupportedAudioModeChange(modeMask);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Remote exception handling onAudioModeChange", e);
        }
    }

    private class InCallServiceConnection implements ServiceConnection {
        private InCallServiceConnection() {
        }

        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName className, IBinder service) {
            if (CallHandlerServiceProxy.DBG) {
                Log.d(CallHandlerServiceProxy.TAG, "Service Connected");
            }
            CallHandlerServiceProxy.this.onCallHandlerServiceConnected(ICallHandlerService.Stub.asInterface(service));
            CallHandlerServiceProxy.this.removeMessages(2);
            if (CallHandlerServiceProxy.DBG) {
                Log.d(CallHandlerServiceProxy.TAG, "Service Connected. Cancel timer");
            }
            CallHandlerServiceProxy.this.resetConnectRetryCount();
        }

        @Override // android.content.ServiceConnection
        public void onServiceDisconnected(ComponentName className) {
            Log.i(CallHandlerServiceProxy.TAG, "Disconnected from UI service.");
            synchronized (CallHandlerServiceProxy.this.mServiceAndQueueLock) {
                CallHandlerServiceProxy.this.unbind();
                CallHandlerServiceProxy.this.reconnectOnRemainingCalls();
            }
        }
    }

    public void bringToForeground(boolean showDialpad) {
        synchronized (this.mServiceAndQueueLock) {
            if (this.mCallHandlerServiceGuarded != null && this.mCallModeler.hasLiveCall()) {
                try {
                    if (DBG) {
                        Log.d(TAG, "bringToForeground: " + showDialpad);
                    }
                    this.mCallHandlerServiceGuarded.bringToForeground(showDialpad);
                } catch (RemoteException e) {
                    Log.e(TAG, "Exception handling bringToForeground", e);
                }
            }
        }
    }

    private static Intent getInCallServiceIntent(Context context) {
        Intent intent = new Intent(ICallHandlerService.class.getName());
        intent.setComponent(new ComponentName(context.getResources().getString(R.string.ui_default_package), context.getResources().getString(R.string.incall_default_class)));
        return intent;
    }

    private void setupServiceConnection() {
        if (PhoneGlobals.sVoiceCapable) {
            Intent serviceIntent = getInCallServiceIntent(this.mContext);
            if (DBG) {
                Log.d(TAG, "binding to service " + serviceIntent);
            }
            synchronized (this.mServiceAndQueueLock) {
                if (this.mConnection == null) {
                    this.mConnection = new InCallServiceConnection();
                    boolean failedConnection = false;
                    PackageManager packageManger = this.mContext.getPackageManager();
                    List<ResolveInfo> services = packageManger.queryIntentServices(serviceIntent, 0);
                    ServiceInfo serviceInfo = null;
                    for (int i = 0; i < services.size(); i++) {
                        ResolveInfo info = services.get(i);
                        if (info.serviceInfo != null && "android.permission.BIND_CALL_SERVICE".equals(info.serviceInfo.permission)) {
                            serviceInfo = info.serviceInfo;
                            break;
                        }
                    }
                    if (serviceInfo == null) {
                        Log.w(TAG, "Default call handler service not found.");
                        failedConnection = true;
                    } else {
                        serviceIntent.setComponent(new ComponentName(serviceInfo.packageName, serviceInfo.name));
                        if (DBG) {
                            Log.d(TAG, "binding to service " + serviceIntent);
                        }
                        if (!this.mContext.bindService(serviceIntent, this.mConnection, 1)) {
                            Log.w(TAG, "Could not bind to default call handler service: " + serviceIntent.getComponent());
                            failedConnection = true;
                        }
                    }
                    if (failedConnection) {
                        this.mConnection = null;
                        enqueueConnectRetry(1);
                    } else {
                        enqueueConnectRetry(2);
                    }
                } else {
                    Log.d(TAG, "Service connection to in call service already started.");
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void resetConnectRetryCount() {
        this.mBindRetryCount = 0;
    }

    private void incrementRetryCount() {
        if (Integer.MAX_VALUE == this.mBindRetryCount) {
            this.mBindRetryCount = 5;
        }
        this.mBindRetryCount++;
    }

    private void handleConnectRetry() {
        if (this.mConnection != null) {
            Log.i(TAG, "Retry: already connected.");
            return;
        }
        if (this.mCallModeler.hasLiveCall()) {
            incrementRetryCount();
            Log.i(TAG, "Retrying connection: " + this.mBindRetryCount);
            setupServiceConnection();
        } else {
            Log.i(TAG, "Canceling connection retry since there are no calls.");
            synchronized (this.mServiceAndQueueLock) {
                if (this.mQueue != null) {
                    this.mQueue.clear();
                }
            }
            resetConnectRetryCount();
        }
    }

    private void enqueueConnectRetry(int msg) {
        boolean isLongDelay = this.mBindRetryCount > 5;
        int delay = isLongDelay ? 30000 : 2000;
        Log.w(TAG, "InCallUI Connection failed. Enqueuing delayed retry for " + delay + " ms. retries(" + this.mBindRetryCount + ")");
        sendEmptyMessageDelayed(msg, delay);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void unbind() {
        synchronized (this.mServiceAndQueueLock) {
            NotificationMgr.StatusBarHelper statusBarHelper = PhoneGlobals.getInstance().notificationMgr.statusBarHelper;
            statusBarHelper.enableSystemBarNavigation(true);
            statusBarHelper.enableExpandedView(true);
            if (this.mCallHandlerServiceGuarded != null) {
                Log.d(TAG, "Unbinding service.");
                this.mCallHandlerServiceGuarded = null;
                this.mContext.unbindService(this.mConnection);
            }
            this.mConnection = null;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onCallHandlerServiceConnected(ICallHandlerService callHandlerService) {
        synchronized (this.mServiceAndQueueLock) {
            this.mCallHandlerServiceGuarded = callHandlerService;
            makeInitialServiceCalls();
            processQueue();
            if (this.mFullUpdateOnConnect) {
                this.mFullUpdateOnConnect = false;
                onUpdate(this.mCallModeler.getFullList());
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void reconnectOnRemainingCalls() {
        if (this.mCallModeler.hasLiveCall()) {
            this.mFullUpdateOnConnect = true;
            setupServiceConnection();
        }
    }

    private void makeInitialServiceCalls() {
        try {
            this.mCallHandlerServiceGuarded.startCallService(this.mCallCommandService);
            onSupportedAudioModeChange(this.mAudioRouter.getSupportedAudioModes());
            onAudioModeChange(this.mAudioRouter.getAudioMode(), this.mAudioRouter.getMute());
        } catch (RemoteException e) {
            Log.e(TAG, "Remote exception calling CallHandlerService::setCallCommandService", e);
        }
    }

    private List<QueueParams> getQueue() {
        if (this.mQueue == null) {
            this.mQueue = Lists.newArrayList();
        }
        return this.mQueue;
    }

    private void enqueueDisconnect(Call call) {
        getQueue().add(new QueueParams(3, new Call(call)));
    }

    private void enqueueIncoming(Call call) {
        getQueue().add(new QueueParams(1, new Call(call)));
    }

    private void enqueueUpdate(List<Call> calls) {
        List<Call> copy = Lists.newArrayList();
        for (Call call : calls) {
            copy.add(new Call(call));
        }
        getQueue().add(new QueueParams(2, copy));
    }

    private void processQueue() {
        synchronized (this.mServiceAndQueueLock) {
            if (this.mQueue != null) {
                for (QueueParams params : this.mQueue) {
                    switch (params.mMethod) {
                        case 1:
                            processIncoming((Call) params.mArg);
                            break;
                        case 2:
                            processUpdate((List) params.mArg);
                            break;
                        case 3:
                            processDisconnect((Call) params.mArg);
                            break;
                        default:
                            throw new IllegalArgumentException("Method type " + params.mMethod + " not recognized.");
                    }
                }
                this.mQueue.clear();
                this.mQueue = null;
            }
        }
    }

    private static class QueueParams {
        private final Object mArg;
        private final int mMethod;

        private QueueParams(int method, Object arg) {
            this.mMethod = method;
            this.mArg = arg;
        }
    }
}
