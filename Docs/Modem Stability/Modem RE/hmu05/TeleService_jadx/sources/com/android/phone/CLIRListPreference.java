package com.android.phone;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.util.Log;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.Phone;

/* JADX INFO: loaded from: classes.dex */
public class CLIRListPreference extends ListPreference {
    private final boolean DBG;
    int[] clirArray;
    private final MyHandler mHandler;
    private Phone mPhone;
    private TimeConsumingPreferenceListener mTcpListener;

    public CLIRListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.DBG = true;
        this.mHandler = new MyHandler();
        this.mPhone = PhoneGlobals.getPhone();
    }

    public CLIRListPreference(Context context) {
        this(context, null);
    }

    @Override // android.preference.ListPreference, android.preference.DialogPreference
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        this.mPhone.setOutgoingCallerIdDisplay(findIndexOfValue(getValue()), this.mHandler.obtainMessage(1));
        if (this.mTcpListener != null) {
            this.mTcpListener.onStarted(this, false);
        }
    }

    void init(TimeConsumingPreferenceListener listener, boolean skipReading, int subscription) {
        Log.d("CLIRListPreference", "CLIRListPreference init, subscription :" + subscription);
        this.mPhone = PhoneGlobals.getInstance().getPhone(subscription);
        this.mTcpListener = listener;
        if (!skipReading) {
            this.mPhone.getOutgoingCallerIdDisplay(this.mHandler.obtainMessage(0, 0, 0));
            if (this.mTcpListener != null) {
                this.mTcpListener.onStarted(this, true);
            }
        }
    }

    void handleGetCLIRResult(int[] tmpClirArray) {
        int value;
        this.clirArray = tmpClirArray;
        boolean enabled = tmpClirArray[1] == 1 || tmpClirArray[1] == 3 || tmpClirArray[1] == 4;
        setEnabled(enabled);
        switch (tmpClirArray[1]) {
            case 1:
            case 3:
            case 4:
                switch (tmpClirArray[0]) {
                    case 1:
                        value = 1;
                        break;
                    case 2:
                        value = 2;
                        break;
                    default:
                        value = 0;
                        break;
                }
                break;
            case 2:
            default:
                value = 0;
                break;
        }
        setValueIndex(value);
        int summary = R.string.sum_default_caller_id;
        switch (value) {
            case 0:
                summary = R.string.sum_default_caller_id;
                break;
            case 1:
                summary = R.string.sum_hide_caller_id;
                break;
            case 2:
                summary = R.string.sum_show_caller_id;
                break;
        }
        setSummary(summary);
    }

    private class MyHandler extends Handler {
        private MyHandler() {
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    handleGetCLIRResponse(msg);
                    break;
                case 1:
                    handleSetCLIRResponse(msg);
                    break;
            }
        }

        private void handleGetCLIRResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            if (msg.arg2 == 1) {
                CLIRListPreference.this.mTcpListener.onFinished(CLIRListPreference.this, false);
            } else {
                CLIRListPreference.this.mTcpListener.onFinished(CLIRListPreference.this, true);
            }
            CLIRListPreference.this.clirArray = null;
            if (ar.exception != null) {
                Log.d("CLIRListPreference", "handleGetCLIRResponse: ar.exception=" + ar.exception);
                CLIRListPreference.this.mTcpListener.onException(CLIRListPreference.this, (CommandException) ar.exception);
            } else {
                if (ar.userObj instanceof Throwable) {
                    CLIRListPreference.this.mTcpListener.onError(CLIRListPreference.this, 400);
                    return;
                }
                int[] clirArray = (int[]) ar.result;
                if (clirArray.length != 2) {
                    CLIRListPreference.this.mTcpListener.onError(CLIRListPreference.this, 400);
                } else {
                    Log.d("CLIRListPreference", "handleGetCLIRResponse: CLIR successfully queried, clirArray[0]=" + clirArray[0] + ", clirArray[1]=" + clirArray[1]);
                    CLIRListPreference.this.handleGetCLIRResult(clirArray);
                }
            }
        }

        private void handleSetCLIRResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            if (ar.exception != null) {
                Log.d("CLIRListPreference", "handleSetCallWaitingResponse: ar.exception=" + ar.exception);
            }
            Log.d("CLIRListPreference", "handleSetCallWaitingResponse: re get");
            CLIRListPreference.this.mPhone.getOutgoingCallerIdDisplay(obtainMessage(0, 1, 1, ar.exception));
        }
    }
}
