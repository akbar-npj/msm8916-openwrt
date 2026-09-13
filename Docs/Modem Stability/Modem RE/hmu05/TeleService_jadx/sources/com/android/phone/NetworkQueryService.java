package com.android.phone;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import java.util.ArrayList;

/* JADX INFO: loaded from: classes.dex */
public class NetworkQueryService extends Service {
    private Phone mPhone;
    private int mState;
    private final IBinder mLocalBinder = new LocalBinder();
    Handler mHandler = new Handler() { // from class: com.android.phone.NetworkQueryService.1
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 100:
                    NetworkQueryService.this.broadcastQueryResults((AsyncResult) msg.obj);
                    break;
            }
        }
    };
    final RemoteCallbackList<INetworkQueryServiceCallback> mCallbacks = new RemoteCallbackList<>();
    private final INetworkQueryService.Stub mBinder = new INetworkQueryService.Stub() { // from class: com.android.phone.NetworkQueryService.2
        @Override // com.android.phone.INetworkQueryService
        public void startNetworkQuery(INetworkQueryServiceCallback cb) {
            if (cb != null) {
                synchronized (NetworkQueryService.this.mCallbacks) {
                    NetworkQueryService.this.mCallbacks.register(cb);
                    switch (NetworkQueryService.this.mState) {
                        case -1:
                            NetworkQueryService.this.mPhone.getAvailableNetworks(NetworkQueryService.this.mHandler.obtainMessage(100));
                            NetworkQueryService.this.mState = -2;
                            break;
                    }
                }
            }
        }

        @Override // com.android.phone.INetworkQueryService
        public void stopNetworkQuery(INetworkQueryServiceCallback cb) {
            if (cb != null) {
                synchronized (NetworkQueryService.this.mCallbacks) {
                    NetworkQueryService.this.mCallbacks.unregister(cb);
                }
            }
        }
    };

    public class LocalBinder extends Binder {
        public LocalBinder() {
        }

        INetworkQueryService getService() {
            return NetworkQueryService.this.mBinder;
        }
    }

    @Override // android.app.Service
    public void onCreate() {
        this.mState = -1;
        this.mPhone = PhoneFactory.getDefaultPhone();
    }

    @Override // android.app.Service
    public void onStart(Intent intent, int startId) {
    }

    @Override // android.app.Service
    public int onStartCommand(Intent intent, int flags, int startId) {
        int subscription = intent.getIntExtra("subscription", PhoneGlobals.getInstance().getDefaultSubscription());
        log("onStart subscription :" + subscription);
        this.mPhone = MSimPhoneGlobals.getInstance().getPhone(subscription);
        return 3;
    }

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return this.mLocalBinder;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void broadcastQueryResults(AsyncResult ar) {
        synchronized (this.mCallbacks) {
            this.mState = -1;
            if (ar != null) {
                int exception = ar.exception == null ? 0 : 1;
                for (int i = this.mCallbacks.beginBroadcast() - 1; i >= 0; i--) {
                    INetworkQueryServiceCallback cb = (INetworkQueryServiceCallback) this.mCallbacks.getBroadcastItem(i);
                    try {
                        cb.onQueryComplete((ArrayList) ar.result, exception);
                    } catch (RemoteException e) {
                    }
                }
                this.mCallbacks.finishBroadcast();
            }
        }
    }

    private static void log(String msg) {
        Log.d("NetworkQuery", msg);
    }
}
