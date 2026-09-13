package com.android.phone;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import com.android.internal.telephony.DebugService;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/* JADX INFO: loaded from: classes.dex */
public class TelephonyDebugService extends Service {
    private static String TAG = "TelephonyDebugService";
    private DebugService mDebugService = new DebugService();

    public TelephonyDebugService() {
        Log.d(TAG, "TelephonyDebugService()");
    }

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override // android.app.Service
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        this.mDebugService.dump(fd, pw, args);
    }
}
