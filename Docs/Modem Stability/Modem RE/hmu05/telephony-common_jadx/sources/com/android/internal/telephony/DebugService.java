package com.android.internal.telephony;

import android.telephony.Rlog;
import com.android.internal.telephony.uicc.IccCardProxy;
import com.android.internal.telephony.uicc.UiccController;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/* JADX INFO: loaded from: classes.dex */
public class DebugService {
    private static String TAG = "DebugService";

    public DebugService() {
        log("DebugService:");
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        log("dump: +");
        try {
            PhoneProxy phoneProxy = (PhoneProxy) PhoneFactory.getDefaultPhone();
            try {
                PhoneBase phoneBase = (PhoneBase) phoneProxy.getActivePhone();
                pw.println();
                pw.println("++++++++++++++++++++++++++++++++");
                pw.flush();
                try {
                    phoneBase.dump(fd, pw, args);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                pw.flush();
                pw.println("++++++++++++++++++++++++++++++++");
                try {
                    phoneBase.mDcTracker.dump(fd, pw, args);
                } catch (Exception e2) {
                    e2.printStackTrace();
                }
                pw.flush();
                pw.println("++++++++++++++++++++++++++++++++");
                try {
                    phoneBase.getServiceStateTracker().dump(fd, pw, args);
                } catch (Exception e3) {
                    e3.printStackTrace();
                }
                pw.flush();
                pw.println("++++++++++++++++++++++++++++++++");
                try {
                    phoneBase.getCallTracker().dump(fd, pw, args);
                } catch (Exception e4) {
                    e4.printStackTrace();
                }
                pw.flush();
                pw.println("++++++++++++++++++++++++++++++++");
                try {
                    ((RIL) phoneBase.mCi).dump(fd, pw, args);
                } catch (Exception e5) {
                    e5.printStackTrace();
                }
                pw.flush();
                pw.println("++++++++++++++++++++++++++++++++");
                try {
                    UiccController.getInstance().dump(fd, pw, args);
                } catch (Exception e6) {
                    e6.printStackTrace();
                }
                pw.flush();
                pw.println("++++++++++++++++++++++++++++++++");
                try {
                    ((IccCardProxy) phoneProxy.getIccCard()).dump(fd, pw, args);
                } catch (Exception e7) {
                    e7.printStackTrace();
                }
                pw.flush();
                pw.println("++++++++++++++++++++++++++++++++");
                log("dump: -");
            } catch (Exception e8) {
                pw.println("Telephony DebugService: Could not PhoneBase e=" + e8);
            }
        } catch (Exception e9) {
            pw.println("Telephony DebugService: Could not getDefaultPhone e=" + e9);
        }
    }

    private static void log(String s) {
        Rlog.d(TAG, "DebugService " + s);
    }
}
