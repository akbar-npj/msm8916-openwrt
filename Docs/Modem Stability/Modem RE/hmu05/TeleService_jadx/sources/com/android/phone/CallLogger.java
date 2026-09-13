package com.android.phone;

import android.net.Uri;
import android.os.SystemProperties;
import android.telephony.MSimTelephonyManager;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyCapabilities;
import com.android.internal.telephony.cdma.CdmaConnection;
import com.android.phone.common.CallLogAsync;

/* JADX INFO: loaded from: classes.dex */
class CallLogger {
    private static final String LOG_TAG = CallLogger.class.getSimpleName();
    private PhoneGlobals mApplication;
    private CallLogAsync mCallLog;

    public CallLogger(PhoneGlobals application, CallLogAsync callLogAsync) {
        this.mApplication = application;
        this.mCallLog = callLogAsync;
    }

    public void logCall(Connection c, int callLogType) {
        String number = c.getAddress();
        long date = c.getCreateTime();
        long duration = c.getDurationMillis();
        Phone phone = c.getCall().getPhone();
        log("- logCall(Connection c, int callLogType), phone = " + phone);
        CallerInfo ci = getCallerInfoFromConnection(c);
        String logNumber = getLogNumber(c, ci);
        log("- onDisconnect(): logNumber set to:" + PhoneUtils.toLogSafePhoneNumber(logNumber) + ", number set to: " + PhoneUtils.toLogSafePhoneNumber(number));
        int presentation = getPresentation(c, ci);
        boolean isOtaspNumber = TelephonyCapabilities.supportsOtasp(phone) && phone.isOtaSpNumber(number);
        int durationType = 0;
        if (2 == callLogType && (c instanceof CdmaConnection) && !((CdmaConnection) c).isConnectionTimerReset()) {
            durationType = 1;
        }
        if (!isOtaspNumber) {
            log("CallLogger: logCall: callLogType in =" + callLogType);
            log("CallLogger: logCall: phoneType =" + phone.getPhoneType());
            log("CallLogger: logCall: callType =" + c.getCallDetails().call_type);
            int callType = c.getCallDetails().call_type;
            String videoCallDuration = c.getCallDetails().getValueForKeyFromExtras(c.getCallDetails().extras, "video_call_duration_key");
            log("CallLogger: logCall, call modified, conn testlog = " + videoCallDuration);
            if (SystemProperties.getBoolean("net.lte.VT_LOOPBACK_ENABLE", false) || phone.getPhoneType() == 4) {
                switch (callLogType) {
                    case 1:
                        if (callType == 3) {
                            callLogType = 31;
                        } else if (callType == 0) {
                            callLogType = 21;
                        }
                        break;
                    case 2:
                        if (callType == 3) {
                            callLogType = 32;
                        } else if (callType == 0) {
                            callLogType = 22;
                        }
                        break;
                    case 3:
                        if (callType == 3) {
                            callLogType = 33;
                        } else if (callType == 0) {
                            callLogType = 23;
                        }
                        break;
                }
            }
            log("CallLogger: logCall: callLogType out =" + callLogType);
            if (MSimTelephonyManager.getDefault().getSimState(c.getCall().getPhone().getSubscription()) == 1) {
                logCall(ci, logNumber, presentation, callLogType, date, duration, -1, durationType, videoCallDuration);
            } else {
                logCall(ci, logNumber, presentation, callLogType, date, duration, c.getCall().getPhone().getSubscription(), durationType, videoCallDuration);
            }
        }
    }

    public void logCall(Connection c) {
        int callLogType;
        Connection.DisconnectCause cause = c.getDisconnectCause();
        if (c.isIncoming()) {
            callLogType = (cause == Connection.DisconnectCause.INCOMING_MISSED || (cause == Connection.DisconnectCause.INCOMING_REJECTED && this.mApplication.getResources().getBoolean(R.bool.reject_call_as_missed_call))) ? 3 : 1;
        } else {
            callLogType = 2;
        }
        log("- callLogType: " + callLogType + ", UserData: " + c.getUserData());
        logCall(c, callLogType);
    }

    public void logCall(CallerInfo ci, String number, int presentation, int callType, long start, long duration, int subscription, int durationType) {
        logCall(ci, number, presentation, callType, start, duration, subscription, durationType, null);
    }

    public void logCall(CallerInfo ci, String number, int presentation, int callType, long start, long duration, int subscription, int durationType, String videoCallDuration) {
        boolean isEmergencyNumber = PhoneNumberUtils.isLocalEmergencyNumber(number, this.mApplication);
        boolean okToLogEmergencyNumber = this.mApplication.getResources().getBoolean(R.bool.allow_emergency_numbers_in_call_log);
        boolean isOkToLogThisCall = !isEmergencyNumber || okToLogEmergencyNumber;
        if (isOkToLogThisCall) {
            log("sending Calllog entry: " + ci + ", " + PhoneUtils.toLogSafePhoneNumber(number) + "," + presentation + ", " + callType + ", " + start + ", " + duration);
            log("- videocallduration: " + videoCallDuration);
            CallLogAsync.AddCallArgs args = new CallLogAsync.AddCallArgs(this.mApplication, ci, number, presentation, callType, start, duration, subscription, durationType, videoCallDuration);
            this.mCallLog.addCall(args);
        }
    }

    private CallerInfo getCallerInfoFromConnection(Connection conn) {
        Object o = conn.getUserData();
        if (o == null || (o instanceof CallerInfo)) {
            CallerInfo ci = (CallerInfo) o;
            return ci;
        }
        if (o instanceof Uri) {
            CallerInfo ci2 = CallerInfo.getCallerInfo(this.mApplication.getApplicationContext(), (Uri) o);
            return ci2;
        }
        CallerInfo ci3 = ((PhoneUtils.CallerInfoToken) o).currentInfo;
        return ci3;
    }

    private String getLogNumber(Connection conn, CallerInfo callerInfo) {
        String number;
        if (conn.isIncoming()) {
            number = conn.getAddress();
        } else if (callerInfo == null || TextUtils.isEmpty(callerInfo.phoneNumber) || callerInfo.isEmergencyNumber() || callerInfo.isVoiceMailNumber()) {
            if (conn.getCall().getPhone().getPhoneType() == 2) {
                number = conn.getOrigDialString();
            } else {
                number = conn.getAddress();
            }
        } else {
            number = callerInfo.phoneNumber;
        }
        if (number == null) {
            return null;
        }
        int presentation = conn.getNumberPresentation();
        PhoneUtils.modifyForSpecialCnapCases(this.mApplication, callerInfo, number, presentation);
        if (!PhoneNumberUtils.isUriNumber(number)) {
            number = PhoneNumberUtils.stripSeparators(number);
        }
        log("getLogNumber: " + number);
        return number;
    }

    private int getPresentation(Connection conn, CallerInfo callerInfo) {
        int presentation;
        if (callerInfo == null) {
            presentation = conn.getNumberPresentation();
        } else {
            presentation = callerInfo.numberPresentation;
            log("- getPresentation(): ignoring connection's presentation: " + conn.getNumberPresentation());
        }
        log("- getPresentation: presentation: " + presentation);
        return presentation;
    }

    private void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
