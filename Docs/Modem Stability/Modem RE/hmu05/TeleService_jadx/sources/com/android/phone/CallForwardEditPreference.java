package com.android.phone;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;

/* JADX INFO: loaded from: classes.dex */
public class CallForwardEditPreference extends EditPhoneNumberPreference {
    private static final String[] SRC_TAGS = {"{0}"};
    CallForwardInfo callForwardInfo;
    private int mButtonClicked;
    private MyHandler mHandler;
    private int mServiceClass;
    private CharSequence mSummaryOnTemplate;
    Phone phone;
    int reason;
    TimeConsumingPreferenceListener tcpListener;

    public CallForwardEditPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mHandler = new MyHandler();
        this.phone = PhoneGlobals.getPhone();
        this.mSummaryOnTemplate = getSummaryOn();
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CallForwardEditPreference, 0, R.style.EditPhoneNumberPreference);
        this.mServiceClass = a.getInt(0, 1);
        this.reason = a.getInt(1, 0);
        a.recycle();
        Log.d("CallForwardEditPreference", "mServiceClass=" + this.mServiceClass + ", reason=" + this.reason);
    }

    public CallForwardEditPreference(Context context) {
        this(context, null);
    }

    void init(TimeConsumingPreferenceListener listener, boolean skipReading, int subscription) {
        Log.d("CallForwardEditPreference", "Getting CallForwardEditPreference subscription =" + subscription);
        this.phone = PhoneGlobals.getInstance().getPhone(subscription);
        this.tcpListener = listener;
        if (!skipReading) {
            Log.d("CallForwardEditPreference", "getCallForwardingOption for reason " + this.reason);
            if (PhoneGlobals.isIMSRegisterd()) {
                Log.d("CallForwardEditPreference", "UT interface, getCallForwardingOption for reason " + this.reason);
                PhoneBase pb = PhoneUtils.getImsPhone(PhoneGlobals.getInstance().mCM);
                pb.getCallForwardingOption(this.reason, this.mHandler.obtainMessage(0, 0, 0, null));
            } else {
                if (this.reason == 6) {
                    Log.d("CallForwardEditPreference", "IMS is not registered, can not query for CFUT");
                    return;
                }
                this.phone.getCallForwardingOption(this.reason, this.mHandler.obtainMessage(0, 0, 0, null));
            }
            if (this.tcpListener != null) {
                this.tcpListener.onStarted(this, true);
            }
        }
    }

    @Override // com.android.phone.EditPhoneNumberPreference, android.preference.EditTextPreference, android.preference.DialogPreference
    protected void onBindDialogView(View view) {
        this.mButtonClicked = -2;
        super.onBindDialogView(view);
    }

    @Override // com.android.phone.EditPhoneNumberPreference, android.preference.DialogPreference, android.content.DialogInterface.OnClickListener
    public void onClick(DialogInterface dialog, int which) {
        super.onClick(dialog, which);
        this.mButtonClicked = which;
    }

    @Override // com.android.phone.EditPhoneNumberPreference, android.preference.EditTextPreference, android.preference.DialogPreference
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        Log.d("CallForwardEditPreference", "mButtonClicked=" + this.mButtonClicked + ", positiveResult=" + positiveResult);
        if (this.mButtonClicked != -2) {
            int action = (isToggled() || this.mButtonClicked == -1) ? 3 : 0;
            int time = this.reason != 2 ? 0 : 20;
            String number = getPhoneNumber();
            int StartHour = getStartTimeHour();
            int StartMinute = getStartTimeMinute();
            int EndHour = getEndTimeHour();
            int EndMinute = getEndTimeMinute();
            Log.d("CallForwardEditPreference", "callForwardInfo=" + this.callForwardInfo);
            boolean isCFSettingChanged = true;
            if (action == 3 && this.callForwardInfo != null && this.callForwardInfo.status == 1 && number.equals(this.callForwardInfo.number)) {
                if (this.reason == 6 || this.reason == 0) {
                    isCFSettingChanged = (this.callForwardInfo.startHour == StartHour && this.callForwardInfo.startHour == StartMinute && this.callForwardInfo.endHour == EndHour && this.callForwardInfo.endMinute == EndMinute) ? false : true;
                } else {
                    Log.d("CallForwardEditPreference", "no change, do nothing");
                    isCFSettingChanged = false;
                }
            }
            Log.d("CallForwardEditPreference", "onDialogClosed: , reason=" + this.reason + ", action=" + action + ", number=" + number + ", isCFSettingChanged" + isCFSettingChanged);
            if (isCFSettingChanged) {
                Log.d("CallForwardEditPreference", "reason=" + this.reason + ", action=" + action + ", number=" + number);
                setSummaryOn("");
                if (PhoneGlobals.isIMSRegisterd()) {
                    Log.d("CallForwardEditPreference", "onDialogClosed, set CallForwarding on UT");
                    PhoneBase pb = PhoneUtils.getImsPhone(PhoneGlobals.getInstance().mCM);
                    if (this.reason == 6) {
                        Log.d("CallForwardEditPreference", "onDialogClosed, setCallForwardingTimerOption, StartHour=" + StartHour + ", StartMinute=" + StartMinute + ", EndHour=" + EndHour + ", EndMinute=" + EndMinute);
                        pb.setCallForwardingTimerOption(StartHour, StartMinute, EndHour, EndMinute, action, this.reason, number, time, this.mHandler.obtainMessage(1, action, 1));
                    } else {
                        pb.setCallForwardingOption(action, this.reason, number, time, this.mHandler.obtainMessage(1, action, 1));
                    }
                } else {
                    this.phone.setCallForwardingOption(action, this.reason, number, time, this.mHandler.obtainMessage(1, action, 1));
                }
                if (this.tcpListener != null) {
                    this.tcpListener.onStarted(this, false);
                }
            }
        }
    }

    void handleCallForwardResult(CallForwardInfo cf) {
        this.callForwardInfo = cf;
        Log.d("CallForwardEditPreference", "handleGetCFResponse done, callForwardInfo=" + this.callForwardInfo);
        setToggled(this.callForwardInfo.status == 1);
        setPhoneNumber(this.callForwardInfo.number);
        if (this.callForwardInfo.reason == 6) {
            Log.e("CallForwardEditPreference", "handleCallForwardResult, reason " + this.callForwardInfo.reason + ", number " + this.callForwardInfo.number + ", startHour " + this.callForwardInfo.startHour + ", startMinute " + this.callForwardInfo.startMinute + ", endHour " + this.callForwardInfo.endHour + ", endMinute " + this.callForwardInfo.endMinute);
            setPhoneNumberWithTimePeriod(this.callForwardInfo.number, this.callForwardInfo.startHour, this.callForwardInfo.startMinute, this.callForwardInfo.endHour, this.callForwardInfo.endMinute);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateSummaryText() {
        CharSequence summaryOn;
        Log.e("CallForwardEditPreference", "updateSummaryText, complete fetching for reason " + this.reason);
        if (isToggled()) {
            String number = getRawPhoneNumber();
            if (this.reason == 6) {
                number = getRawPhoneNumberWithTime();
            }
            if (number != null && number.length() > 0) {
                String[] values = {number};
                summaryOn = TextUtils.replace(this.mSummaryOnTemplate, SRC_TAGS, values);
            } else {
                summaryOn = getContext().getString(R.string.sum_cfu_enabled_no_number);
            }
            setSummaryOn(summaryOn);
        }
    }

    private class MyHandler extends Handler {
        private MyHandler() {
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    handleGetCFResponse(msg);
                    break;
                case 1:
                    handleSetCFResponse(msg);
                    break;
            }
        }

        private void handleGetCFResponse(Message msg) {
            CharSequence s;
            Log.d("CallForwardEditPreference", "handleGetCFResponse: done");
            if (msg.arg2 == 1) {
                CallForwardEditPreference.this.tcpListener.onFinished(CallForwardEditPreference.this, false);
            } else {
                CallForwardEditPreference.this.tcpListener.onFinished(CallForwardEditPreference.this, true);
            }
            AsyncResult ar = (AsyncResult) msg.obj;
            CallForwardEditPreference.this.callForwardInfo = null;
            if (ar.exception != null) {
                Log.d("CallForwardEditPreference", "handleGetCFResponse: ar.exception=" + ar.exception);
                if (ar.exception instanceof RuntimeException) {
                    CallForwardEditPreference.this.tcpListener.onException(CallForwardEditPreference.this, CommandException.fromRilErrno(2));
                } else {
                    CallForwardEditPreference.this.tcpListener.onException(CallForwardEditPreference.this, (CommandException) ar.exception);
                }
            } else {
                if (ar.userObj instanceof Throwable) {
                    CallForwardEditPreference.this.tcpListener.onError(CallForwardEditPreference.this, 400);
                }
                CallForwardInfo[] cfInfoArray = (CallForwardInfo[]) ar.result;
                if (cfInfoArray.length == 0) {
                    Log.d("CallForwardEditPreference", "handleGetCFResponse: cfInfoArray.length==0");
                    CallForwardEditPreference.this.setEnabled(false);
                    CallForwardEditPreference.this.tcpListener.onError(CallForwardEditPreference.this, 400);
                } else {
                    int length = cfInfoArray.length;
                    for (int i = 0; i < length; i++) {
                        Log.d("CallForwardEditPreference", "handleGetCFResponse, cfInfoArray[" + i + "]=" + cfInfoArray[i]);
                        if ((CallForwardEditPreference.this.mServiceClass & cfInfoArray[i].serviceClass) != 0) {
                            CallForwardInfo info = cfInfoArray[i];
                            CallForwardEditPreference.this.handleCallForwardResult(info);
                            if (msg.arg2 == 1 && msg.arg1 == 0 && info.status == 1) {
                                switch (CallForwardEditPreference.this.reason) {
                                    case 1:
                                        s = CallForwardEditPreference.this.getContext().getText(R.string.disable_cfb_forbidden);
                                        break;
                                    case 2:
                                        s = CallForwardEditPreference.this.getContext().getText(R.string.disable_cfnry_forbidden);
                                        break;
                                    default:
                                        s = CallForwardEditPreference.this.getContext().getText(R.string.disable_cfnrc_forbidden);
                                        break;
                                }
                                AlertDialog.Builder builder = new AlertDialog.Builder(CallForwardEditPreference.this.getContext());
                                builder.setNeutralButton(R.string.close_dialog, (DialogInterface.OnClickListener) null);
                                builder.setTitle(CallForwardEditPreference.this.getContext().getText(R.string.error_updating_title));
                                builder.setMessage(s);
                                builder.setCancelable(true);
                                builder.create().show();
                            }
                        }
                    }
                }
            }
            CallForwardEditPreference.this.updateSummaryText();
        }

        private void handleSetCFResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            if (ar.exception != null) {
                Log.d("CallForwardEditPreference", "handleSetCFResponse: ar.exception=" + ar.exception);
            }
            Log.d("CallForwardEditPreference", "handleSetCFResponse: re get, reason = " + CallForwardEditPreference.this.reason);
            if (CallForwardEditPreference.this.reason == 6) {
                PhoneBase pb = PhoneUtils.getImsPhone(PhoneGlobals.getInstance().mCM);
                pb.getCallForwardingOption(CallForwardEditPreference.this.reason, obtainMessage(0, msg.arg1, 1, ar.exception));
            } else {
                CallForwardEditPreference.this.phone.getCallForwardingOption(CallForwardEditPreference.this.reason, obtainMessage(0, msg.arg1, 1, ar.exception));
            }
        }
    }
}
