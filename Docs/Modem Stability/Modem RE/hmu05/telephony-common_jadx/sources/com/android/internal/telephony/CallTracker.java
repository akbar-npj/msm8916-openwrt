package com.android.internal.telephony;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/* JADX INFO: loaded from: classes.dex */
public abstract class CallTracker extends Handler {
    private static final boolean DBG_POLL = false;
    protected static final int EVENT_CALL_STATE_CHANGE = 2;
    protected static final int EVENT_CALL_WAITING_INFO_CDMA = 15;
    protected static final int EVENT_CDMA_INFO_REC = 17;
    protected static final int EVENT_CONFERENCE_RESULT = 11;
    protected static final int EVENT_ECT_RESULT = 13;
    protected static final int EVENT_EXIT_ECM_RESPONSE_CDMA = 14;
    protected static final int EVENT_GET_LAST_CALL_FAIL_CAUSE = 5;
    protected static final int EVENT_OPERATION_COMPLETE = 4;
    protected static final int EVENT_POLL_CALLS_RESULT = 1;
    protected static final int EVENT_RADIO_AVAILABLE = 9;
    protected static final int EVENT_RADIO_NOT_AVAILABLE = 10;
    protected static final int EVENT_REPOLL_AFTER_DELAY = 3;
    protected static final int EVENT_SEPARATE_RESULT = 12;
    protected static final int EVENT_SWITCH_RESULT = 8;
    protected static final int EVENT_THREE_WAY_DIAL_L2_RESULT_CDMA = 16;
    static final int POLL_DELAY_MSEC = 250;
    public CommandsInterface mCi;
    protected Message mLastRelevantPoll;
    protected boolean mNeedsPoll;
    protected int mPendingOperations;

    @Override // android.os.Handler
    public abstract void handleMessage(Message message);

    protected abstract void handlePollCalls(AsyncResult asyncResult);

    protected abstract void log(String str);

    public abstract void registerForVoiceCallEnded(Handler handler, int i, Object obj);

    public abstract void registerForVoiceCallStarted(Handler handler, int i, Object obj);

    public abstract void unregisterForVoiceCallEnded(Handler handler);

    public abstract void unregisterForVoiceCallStarted(Handler handler);

    protected void pollCallsWhenSafe() {
        this.mNeedsPoll = true;
        if (checkNoOperationsPending()) {
            this.mLastRelevantPoll = obtainMessage(1);
            this.mCi.getCurrentCalls(this.mLastRelevantPoll);
        }
    }

    protected void pollCallsAfterDelay() {
        Message msg = obtainMessage();
        msg.what = 3;
        sendMessageDelayed(msg, 250L);
    }

    protected boolean isCommandExceptionRadioNotAvailable(Throwable e) {
        return e != null && (e instanceof CommandException) && ((CommandException) e).getCommandError() == CommandException.Error.RADIO_NOT_AVAILABLE;
    }

    protected void handleRadioAvailable() {
        pollCallsWhenSafe();
    }

    protected Message obtainNoPollCompleteMessage(int what) {
        this.mPendingOperations++;
        this.mLastRelevantPoll = null;
        return obtainMessage(what);
    }

    private boolean checkNoOperationsPending() {
        return this.mPendingOperations == 0;
    }

    protected String checkForTestEmergencyNumber(String dialString) {
        String testEn = SystemProperties.get("ril.test.emergencynumber");
        if (!TextUtils.isEmpty(testEn)) {
            String[] values = testEn.split(":");
            log("checkForTestEmergencyNumber: values.length=" + values.length);
            if (values.length == 2 && values[0].equals(PhoneNumberUtils.stripSeparators(dialString))) {
                this.mCi.testingEmergencyCall();
                log("checkForTestEmergencyNumber: remap " + dialString + " to " + values[1]);
                return values[1];
            }
            return dialString;
        }
        return dialString;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("CallTracker:");
        pw.println(" mPendingOperations=" + this.mPendingOperations);
        pw.println(" mNeedsPoll=" + this.mNeedsPoll);
        pw.println(" mLastRelevantPoll=" + this.mLastRelevantPoll);
    }
}
