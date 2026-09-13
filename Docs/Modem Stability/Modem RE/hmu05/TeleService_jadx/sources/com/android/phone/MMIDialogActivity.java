package com.android.phone;

import android.app.Activity;
import android.app.Dialog;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class MMIDialogActivity extends Activity {
    private static final String TAG = MMIDialogActivity.class.getSimpleName();
    private Handler mHandler;
    private Dialog mMMIDialog;
    private CallManager mCM = PhoneGlobals.getInstance().getCallManager();
    private Phone mPhone = PhoneGlobals.getPhone();

    @Override // android.app.Activity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mHandler = new Handler() { // from class: com.android.phone.MMIDialogActivity.1
            @Override // android.os.Handler
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case 52:
                        MMIDialogActivity.this.onMMIComplete((MmiCode) ((AsyncResult) msg.obj).result);
                        break;
                    case 53:
                        MMIDialogActivity.this.onMMICancel();
                        break;
                }
            }
        };
        this.mCM.registerForMmiComplete(this.mHandler, 52, (Object) null);
        if (this.mCM.getState() == PhoneConstants.State.OFFHOOK) {
            Toast.makeText(this, R.string.incall_status_dialed_mmi, 0).show();
        }
        showMMIDialog();
    }

    private void showMMIDialog() {
        List<? extends MmiCode> codes = this.mPhone.getPendingMmiCodes();
        if (codes.size() > 0) {
            MmiCode mmiCode = (MmiCode) codes.get(0);
            Message message = Message.obtain(this.mHandler, 53);
            this.mMMIDialog = PhoneUtils.displayMMIInitiate(this, mmiCode, message, this.mMMIDialog);
            return;
        }
        finish();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onMMIComplete(MmiCode mmiCode) {
        int phoneType = this.mPhone.getPhoneType();
        if (phoneType == 2) {
            PhoneUtils.displayMMIComplete(this.mPhone, this, mmiCode, null, null);
        } else if (phoneType == 1 && mmiCode.getState() != MmiCode.State.PENDING) {
            Log.d(TAG, "Got MMI_COMPLETE, finishing dialog activity...");
            dismissDialogsAndFinish();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onMMICancel() {
        Log.v(TAG, "onMMICancel()...");
        PhoneUtils.cancelMmiCode(this.mPhone);
        Log.d(TAG, "onMMICancel: finishing InCallScreen...");
        dismissDialogsAndFinish();
    }

    private void dismissDialogsAndFinish() {
        if (this.mMMIDialog != null) {
            this.mMMIDialog.dismiss();
        }
        if (this.mHandler != null) {
            this.mCM.unregisterForMmiComplete(this.mHandler);
        }
        finish();
    }
}
