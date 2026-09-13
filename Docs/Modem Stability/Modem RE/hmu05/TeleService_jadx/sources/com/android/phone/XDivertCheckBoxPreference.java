package com.android.phone;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.telephony.MSimTelephonyManager;
import android.telephony.PhoneNumberUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Toast;
import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.Phone;

/* JADX INFO: loaded from: classes.dex */
public class XDivertCheckBoxPreference extends CheckBoxPreference {
    private final boolean DBG;
    int mAction;
    String[] mCFLine1Number;
    private MSimCallNotifier mCallNotif;
    private final Handler mGetOptionComplete;
    String[] mLine1Number;
    int mNumPhones;
    Phone[] mPhoneObj;
    int mReason;
    private final Handler mRevertOptionComplete;
    private final Handler mSetOptionComplete;
    boolean mSub1CallWaiting;
    boolean mSub2CallWaiting;
    TimeConsumingPreferenceListener mTcpListener;
    private XDivertUtility mXDivertUtility;
    boolean mXdivertStatus;

    public XDivertCheckBoxPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.DBG = true;
        this.mGetOptionComplete = new Handler() { // from class: com.android.phone.XDivertCheckBoxPreference.4
            @Override // android.os.Handler
            public void handleMessage(Message msg) {
                AsyncResult result = (AsyncResult) msg.obj;
                switch (msg.what) {
                    case 2:
                        XDivertCheckBoxPreference.this.handleGetCFNRCResponse(result, msg.arg1);
                        break;
                    case 3:
                        XDivertCheckBoxPreference.this.handleGetCallWaitingResponse(result, msg.arg1, msg.arg2);
                        break;
                }
            }
        };
        this.mSetOptionComplete = new Handler() { // from class: com.android.phone.XDivertCheckBoxPreference.5
            @Override // android.os.Handler
            public void handleMessage(Message msg) {
                AsyncResult result = (AsyncResult) msg.obj;
                switch (msg.what) {
                    case 4:
                        XDivertCheckBoxPreference.this.handleSetCFNRCResponse(result, msg.arg1);
                        break;
                    case 5:
                        XDivertCheckBoxPreference.this.handleSetCallWaitingResponse(result, msg.arg1);
                        break;
                }
            }
        };
        this.mRevertOptionComplete = new Handler() { // from class: com.android.phone.XDivertCheckBoxPreference.6
            @Override // android.os.Handler
            public void handleMessage(Message msg) {
                AsyncResult result = (AsyncResult) msg.obj;
                switch (msg.what) {
                    case 6:
                        XDivertCheckBoxPreference.this.handleRevertSetCFNRC(result, msg.arg2);
                        break;
                }
            }
        };
    }

    public XDivertCheckBoxPreference(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.checkBoxPreferenceStyle);
    }

    public XDivertCheckBoxPreference(Context context) {
        this(context, null);
    }

    void init(TimeConsumingPreferenceListener listener, boolean skipReading, String[] line1Number) {
        this.mTcpListener = listener;
        PhoneGlobals app = PhoneGlobals.getInstance();
        this.mCallNotif = (MSimCallNotifier) app.notifier;
        this.mXDivertUtility = XDivertUtility.getInstance();
        this.mNumPhones = MSimTelephonyManager.getDefault().getPhoneCount();
        for (int i = 0; i < this.mNumPhones; i++) {
            Log.d("XDivertCheckBoxPreference", "init sub" + i + " = " + line1Number[i]);
            this.mXDivertUtility.storeNumber(line1Number[i], i);
        }
        processStartDialog(8, true);
        if (!skipReading) {
            this.mPhoneObj = new Phone[this.mNumPhones];
            this.mLine1Number = new String[this.mNumPhones];
            this.mCFLine1Number = new String[this.mNumPhones];
            for (int i2 = 0; i2 < this.mNumPhones; i2++) {
                this.mPhoneObj[i2] = MSimPhoneGlobals.getInstance().getPhone(i2);
                this.mLine1Number[i2] = line1Number[i2];
            }
            this.mPhoneObj[0].getCallForwardingOption(3, this.mGetOptionComplete.obtainMessage(2, 0, 0));
        }
    }

    @Override // android.preference.TwoStatePreference, android.preference.Preference
    protected void onClick() {
        super.onClick();
        processStartDialog(8, false);
        Log.d("XDivertCheckBoxPreference", "onClick mXdivertStatus = " + this.mXdivertStatus);
        this.mSub1CallWaiting = this.mXdivertStatus;
        this.mSub2CallWaiting = this.mXdivertStatus;
        this.mAction = this.mXdivertStatus ? 0 : 3;
        this.mReason = 3;
        int time = this.mReason != 2 ? 0 : 20;
        synchronized (this) {
            try {
                wait(5000L);
            } catch (InterruptedException e) {
            }
        }
        boolean requestForSub1 = PhoneNumberUtils.compare(this.mCFLine1Number[0], this.mLine1Number[1]);
        if (requestForSub1 && requestForSub1 == this.mSub1CallWaiting && this.mAction == 3) {
            this.mPhoneObj[1].setCallForwardingOption(this.mAction, this.mReason, this.mLine1Number[0], time, this.mSetOptionComplete.obtainMessage(4, 1, 0));
        } else {
            this.mPhoneObj[0].setCallForwardingOption(this.mAction, this.mReason, this.mLine1Number[1], time, this.mSetOptionComplete.obtainMessage(4, 0, 0));
        }
    }

    void queryCallWaiting(int arg) {
        this.mPhoneObj[arg].getCallWaiting(this.mGetOptionComplete.obtainMessage(3, arg, 3));
    }

    private boolean validateXDivert() {
        boolean check1 = PhoneNumberUtils.compare(this.mCFLine1Number[0], this.mLine1Number[1]);
        boolean check2 = PhoneNumberUtils.compare(this.mCFLine1Number[1], this.mLine1Number[0]);
        Log.d("XDivertCheckBoxPreference", " CFNR sub1 = " + check1 + " CFNR sub2 = " + check2 + " mSub1CallWaiting = " + this.mSub1CallWaiting + " mSub2CallWaiting = " + this.mSub2CallWaiting);
        displayAlertMessage(check1, check2, this.mSub1CallWaiting, this.mSub2CallWaiting);
        if (this.mCFLine1Number[0] == null || this.mCFLine1Number[1] == null) {
            return false;
        }
        if (check1 && check1 == check2) {
            return this.mSub1CallWaiting && this.mSub1CallWaiting == this.mSub2CallWaiting;
        }
        return false;
    }

    public void displayAlertMessage(boolean sub1Cfnrc, boolean sub2Cfnrc, boolean sub1CW, boolean sub2CW) {
        int[] subStatus = {R.string.xdivert_not_active, R.string.xdivert_not_active};
        int[] resSubId = {R.string.set_sub_1, R.string.set_sub_2};
        String dispMsg = "";
        for (int i = 0; i < this.mNumPhones; i++) {
            if (sub1Cfnrc && sub1Cfnrc == sub1CW && i == 0) {
                subStatus[i] = R.string.xdivert_active;
            }
            if (sub2Cfnrc && sub2Cfnrc == sub2CW && i == 1) {
                subStatus[i] = R.string.xdivert_active;
            }
            dispMsg = dispMsg + getContext().getString(resSubId[i]) + " " + getContext().getString(subStatus[i]) + "\n";
        }
        Log.d("XDivertCheckBoxPreference", "displayAlertMessage:  dispMsg = " + dispMsg);
        new AlertDialog.Builder(getContext()).setTitle(R.string.xdivert_status).setMessage(dispMsg).setIcon(android.R.drawable.ic_dialog_alert).setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() { // from class: com.android.phone.XDivertCheckBoxPreference.2
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int whichButton) {
                Log.d("XDivertCheckBoxPreference", "displayAlertMessage:  onClick");
            }
        }).show().setOnDismissListener(new DialogInterface.OnDismissListener() { // from class: com.android.phone.XDivertCheckBoxPreference.1
            @Override // android.content.DialogInterface.OnDismissListener
            public void onDismiss(DialogInterface dialog) {
                Log.d("XDivertCheckBoxPreference", "displayAlertMessage:  onDismiss");
            }
        });
    }

    private void processStopDialog(int state, boolean read) {
        if (this.mTcpListener != null) {
            Log.d("XDivertCheckBoxPreference", "stop");
            this.mTcpListener.onFinished(this, read);
        }
    }

    private void processStartDialog(final int state, final boolean read) {
        new Thread(new Runnable() { // from class: com.android.phone.XDivertCheckBoxPreference.3
            @Override // java.lang.Runnable
            public void run() {
                Looper.prepare();
                int mode = state;
                if (mode == 8 && XDivertCheckBoxPreference.this.mTcpListener != null) {
                    Log.d("XDivertCheckBoxPreference", "start");
                    XDivertCheckBoxPreference.this.mTcpListener.onStarted(XDivertCheckBoxPreference.this, read);
                }
                Looper.loop();
            }
        }).start();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleGetCFNRCResponse(AsyncResult ar, int arg) {
        Log.d("XDivertCheckBoxPreference", "handleGetCFResponse: done arg = " + arg);
        if (ar.exception != null) {
            Log.d("XDivertCheckBoxPreference", "handleGetCFResponse: ar.exception = " + ar.exception);
            this.mTcpListener.onException(this, (CommandException) ar.exception);
            processStopDialog(9, true);
            return;
        }
        if (ar.userObj instanceof Throwable) {
            this.mTcpListener.onError(this, 400);
            processStopDialog(9, true);
            return;
        }
        CallForwardInfo[] cfInfoArray = (CallForwardInfo[]) ar.result;
        if (cfInfoArray == null) {
            Log.d("XDivertCheckBoxPreference", "handleGetCFResponse: cfInfoArray.length==0");
            this.mTcpListener.onError(this, 400);
            return;
        }
        int length = cfInfoArray.length;
        for (int i = 0; i < length; i++) {
            Log.d("XDivertCheckBoxPreference", "handleGetCFResponse, cfInfoArray[" + i + "]=" + cfInfoArray[i]);
            if ((cfInfoArray[i].serviceClass & 1) != 0 && arg == 0) {
                CallForwardInfo info = cfInfoArray[i];
                this.mCFLine1Number[0] = info.number;
                queryCallWaiting(0);
            } else if ((cfInfoArray[i].serviceClass & 1) != 0 && arg == 1) {
                CallForwardInfo info2 = cfInfoArray[i];
                this.mCFLine1Number[1] = info2.number;
                queryCallWaiting(1);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleSetCFNRCResponse(AsyncResult ar, int arg) {
        Log.d("XDivertCheckBoxPreference", "handleSetCFResponse: done on Sub = " + arg);
        if (ar.exception != null) {
            Log.d("XDivertCheckBoxPreference", "handleSetCFResponse: ar.exception = " + ar.exception);
            this.mTcpListener.onException(this, (CommandException) ar.exception);
            handleRevertOperation(arg, 6);
        } else if (ar.userObj instanceof Throwable) {
            if (this.mTcpListener != null) {
                this.mTcpListener.onError(this, 400);
            }
            handleRevertOperation(arg, 6);
        } else {
            if (arg == 0) {
                this.mCFLine1Number[arg] = this.mLine1Number[1];
            } else {
                this.mCFLine1Number[arg] = this.mLine1Number[0];
            }
            this.mPhoneObj[arg].setCallWaiting(true, this.mSetOptionComplete.obtainMessage(5, arg, 0));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleGetCallWaitingResponse(AsyncResult ar, int arg1, int arg2) {
        boolean z = false;
        if (ar.exception != null) {
            Log.d("XDivertCheckBoxPreference", "handleGetCallWaitingResponse: ar.exception = " + ar.exception);
            if (this.mTcpListener != null) {
                this.mTcpListener.onException(this, (CommandException) ar.exception);
            }
            processStopDialog(9, true);
            return;
        }
        if (ar.userObj instanceof Throwable) {
            if (this.mTcpListener != null) {
                this.mTcpListener.onError(this, 400);
            }
            processStopDialog(9, true);
            return;
        }
        Log.d("XDivertCheckBoxPreference", "handleGetCallWaitingResponse: CW state successfully queried.");
        int[] cwArray = (int[]) ar.result;
        if (arg1 == 0) {
            this.mSub1CallWaiting = cwArray[0] == 1 && (cwArray[1] & 1) == 1;
            Log.d("XDivertCheckBoxPreference", "CW for Sub0 = " + this.mSub1CallWaiting);
            synchronized (this) {
                try {
                    wait(5000L);
                } catch (InterruptedException e) {
                }
            }
            this.mPhoneObj[1].getCallForwardingOption(3, this.mGetOptionComplete.obtainMessage(2, 1, 0));
            return;
        }
        if (arg1 == 1) {
            if (cwArray[0] == 1 && (cwArray[1] & 1) == 1) {
                z = true;
            }
            this.mSub2CallWaiting = z;
            Log.d("XDivertCheckBoxPreference", "CW for Sub1 = " + this.mSub2CallWaiting);
            processStopDialog(9, true);
            this.mXdivertStatus = validateXDivert();
            setChecked(this.mXdivertStatus);
            this.mCallNotif.onXDivertChanged(this.mXdivertStatus);
            this.mCallNotif.setXDivertStatus(this.mXdivertStatus);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleSetCallWaitingResponse(AsyncResult ar, int arg) {
        if (ar.exception != null) {
            Log.d("XDivertCheckBoxPreference", "handleSetCallWaitingResponse: ar.exception = " + ar.exception);
            handleRevertOperation(arg, 7);
            return;
        }
        Log.d("XDivertCheckBoxPreference", "handleSetCallWaitingResponse success arg = " + arg);
        int time = this.mReason != 2 ? 0 : 20;
        if (arg == 0) {
            synchronized (this) {
                try {
                    wait(5000L);
                } catch (InterruptedException e) {
                }
            }
            this.mSub1CallWaiting = !this.mSub1CallWaiting;
            this.mPhoneObj[1].setCallForwardingOption(this.mAction, this.mReason, this.mLine1Number[0], time, this.mSetOptionComplete.obtainMessage(4, 1, 0));
            return;
        }
        if (arg == 1) {
            this.mSub2CallWaiting = this.mSub2CallWaiting ? false : true;
            if (this.mTcpListener != null) {
                this.mTcpListener.onFinished(this, false);
            }
            this.mXdivertStatus = validateXDivert();
            setChecked(this.mXdivertStatus);
            this.mCallNotif.onXDivertChanged(this.mXdivertStatus);
            this.mCallNotif.setXDivertStatus(this.mXdivertStatus);
        }
    }

    private void handleRevertOperation(int subscription, int event) {
        Log.d("XDivertCheckBoxPreference", "handleRevertOperation sub = " + subscription + "Event = " + event);
        if (subscription == 0) {
            switch (event) {
                case 6:
                    if (this.mTcpListener != null) {
                        this.mTcpListener.onFinished(this, false);
                    }
                    break;
                case 7:
                    revertCFNRC(0);
                    break;
            }
        }
        if (subscription == 1) {
            switch (event) {
                case 6:
                    if (this.mTcpListener != null) {
                        this.mTcpListener.onFinished(this, false);
                    }
                    Toast toast = Toast.makeText(getContext(), R.string.xdivert_partial_set, 1);
                    toast.show();
                    break;
                case 7:
                    revertCFNRC(1);
                    break;
            }
        }
    }

    private void revertCFNRC(int arg) {
        int action = this.mXdivertStatus ? 3 : 0;
        int time = 3 != 2 ? 0 : 20;
        Log.d("XDivertCheckBoxPreference", "revertCFNRc arg = " + arg);
        if (arg == 0) {
            this.mPhoneObj[0].setCallForwardingOption(action, 3, this.mLine1Number[1], time, this.mRevertOptionComplete.obtainMessage(6, action, 0));
        } else if (arg == 1) {
            this.mPhoneObj[1].setCallForwardingOption(action, 3, this.mLine1Number[0], time, this.mRevertOptionComplete.obtainMessage(6, action, 1));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleRevertSetCFNRC(AsyncResult ar, int arg) {
        Log.d("XDivertCheckBoxPreference", "handleRevertSetCFNRC: done arg = " + arg + "res = " + ar);
        processStopDialog(9, false);
        if (ar.exception != null) {
            Log.d("XDivertCheckBoxPreference", "handleRevertSetCFNRC: ar.exception = " + ar.exception);
            this.mTcpListener.onException(this, (CommandException) ar.exception);
        } else if ((ar.userObj instanceof Throwable) && this.mTcpListener != null) {
            this.mTcpListener.onError(this, 400);
        }
        Toast toast = Toast.makeText(getContext(), R.string.xdivert_partial_set, 1);
        toast.show();
    }
}
