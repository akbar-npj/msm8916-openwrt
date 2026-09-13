package com.android.phone;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/* JADX INFO: loaded from: classes.dex */
public class HfaService extends Service {
    private static final String TAG = HfaService.class.getSimpleName();
    private HfaLogic mHfaLogic;

    @Override // android.app.Service
    public void onCreate() {
        Log.i(TAG, "service started");
    }

    @Override // android.app.Service
    public int onStartCommand(Intent intent, int flags, int startId) {
        PendingIntent otaResponseIntent = (PendingIntent) intent.getParcelableExtra("otasp_result_code_pending_intent");
        this.mHfaLogic = new HfaLogic(this, new HfaLogic.HfaLogicCallback() { // from class: com.android.phone.HfaService.1
            @Override // com.android.phone.HfaLogic.HfaLogicCallback
            public void onSuccess() {
                Log.i(HfaService.TAG, "onSuccess");
                HfaService.this.onComplete();
            }

            @Override // com.android.phone.HfaLogic.HfaLogicCallback
            public void onError(String msg) {
                Log.i(HfaService.TAG, "onError: " + msg);
                HfaService.this.onComplete();
            }
        }, otaResponseIntent);
        this.mHfaLogic.start();
        return 1;
    }

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onComplete() {
        stopSelf();
    }
}
