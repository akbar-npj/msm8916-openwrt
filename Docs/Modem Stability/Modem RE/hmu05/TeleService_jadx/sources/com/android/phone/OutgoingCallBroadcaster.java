package com.android.phone;

import android.app.Activity;
import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.telephony.MSimTelephonyManager;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ProgressBar;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyCapabilities;
import com.codeaurora.telephony.msim.MSimPhoneFactory;
import com.codeaurora.telephony.msim.SubscriptionManager;
import com.google.common.collect.Maps;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public class OutgoingCallBroadcaster extends Activity implements DialogInterface.OnCancelListener, DialogInterface.OnClickListener {
    private static final boolean DBG;
    private final Handler mHandler = new Handler() { // from class: com.android.phone.OutgoingCallBroadcaster.1
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            if (msg.what == 101) {
                Log.i("OutgoingCallBroadcaster", "Outgoing call takes too long. Showing the spinner.");
                OutgoingCallBroadcaster.this.mWaitingSpinner.setVisibility(0);
            } else if (msg.what == 102) {
                OutgoingCallBroadcaster.this.finish();
            } else {
                Log.wtf("OutgoingCallBroadcaster", "Unknown message id: " + msg.what);
            }
        }
    };
    private boolean mIPCall;
    private int mSubscription;
    private ProgressBar mWaitingSpinner;

    static {
        DBG = SystemProperties.getInt("ro.debuggable", 0) == 1;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void startDelayedFinish() {
        this.mHandler.sendEmptyMessageDelayed(102, 2000L);
    }

    public class OutgoingCallReceiver extends BroadcastReceiver {
        public OutgoingCallReceiver() {
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            OutgoingCallBroadcaster.this.mHandler.removeMessages(101);
            boolean isAttemptingCall = doReceive(context, intent);
            if (OutgoingCallBroadcaster.DBG) {
                Log.v("OutgoingCallReceiver", "OutgoingCallReceiver is going to finish the Activity itself.");
            }
            if (isAttemptingCall) {
                OutgoingCallBroadcaster.this.startDelayedFinish();
            } else {
                OutgoingCallBroadcaster.this.finish();
            }
        }

        public boolean doReceive(Context context, Intent intent) {
            if (OutgoingCallBroadcaster.DBG) {
                Log.v("OutgoingCallReceiver", "doReceive: " + intent);
            }
            boolean isConferenceUri = intent.getBooleanExtra("org.codeaurora.extra.DIAL_CONFERENCE_URI", false);
            boolean isIMSVTCall = intent.getBooleanExtra("ims_videocall", false);
            if (OutgoingCallBroadcaster.DBG) {
                Log.v("OutgoingCallReceiver", "doReceive: isIMSVTCall= " + isIMSVTCall);
            }
            boolean alreadyCalled = intent.getBooleanExtra("android.phone.extra.ALREADY_CALLED", false);
            if (alreadyCalled) {
                if (OutgoingCallBroadcaster.DBG) {
                    Log.v("OutgoingCallReceiver", "CALL already placed -- returning.");
                }
                return false;
            }
            String number = getResultData();
            PhoneGlobals app = PhoneGlobals.getInstance();
            if (TelephonyCapabilities.supportsOtasp(app.phone)) {
                boolean activateState = app.cdmaOtaScreenState.otaScreenState == OtaUtils.CdmaOtaScreenState.OtaScreenState.OTA_STATUS_ACTIVATION;
                boolean dialogState = app.cdmaOtaScreenState.otaScreenState == OtaUtils.CdmaOtaScreenState.OtaScreenState.OTA_STATUS_SUCCESS_FAILURE_DLG;
                boolean isOtaCallActive = false;
                if (app.cdmaOtaScreenState.otaScreenState == OtaUtils.CdmaOtaScreenState.OtaScreenState.OTA_STATUS_PROGRESS || app.cdmaOtaScreenState.otaScreenState == OtaUtils.CdmaOtaScreenState.OtaScreenState.OTA_STATUS_LISTENING) {
                    isOtaCallActive = true;
                }
                if (activateState || dialogState) {
                    if (dialogState) {
                        app.dismissOtaDialogs();
                    }
                    app.clearOtaState();
                } else if (isOtaCallActive) {
                    Log.w("OutgoingCallReceiver", "OTASP call is active: disallowing a new outgoing call.");
                    return false;
                }
            }
            if (number == null) {
                if (OutgoingCallBroadcaster.DBG) {
                    Log.v("OutgoingCallReceiver", "CALL cancelled (null number), returning...");
                }
                return false;
            }
            if (TelephonyCapabilities.supportsOtasp(app.phone) && app.phone.getState() != PhoneConstants.State.IDLE && app.phone.isOtaSpNumber(number)) {
                if (OutgoingCallBroadcaster.DBG) {
                    Log.v("OutgoingCallReceiver", "Call is active, a 2nd OTA call cancelled -- returning.");
                }
                return false;
            }
            if (PhoneNumberUtils.isPotentialLocalEmergencyNumber(number, context)) {
                Log.w("OutgoingCallReceiver", "Cannot modify outgoing call to emergency number " + number + ".");
                return false;
            }
            String originalUri = intent.getStringExtra("android.phone.extra.ORIGINAL_URI");
            if (originalUri == null) {
                Log.e("OutgoingCallReceiver", "Intent is missing EXTRA_ORIGINAL_URI -- returning.");
                return false;
            }
            Uri uri = Uri.parse(originalUri);
            if (!isConferenceUri) {
                number = PhoneNumberUtils.stripSeparators(PhoneNumberUtils.convertKeypadLettersToDigits(number));
            }
            if (OutgoingCallBroadcaster.DBG) {
                Log.v("OutgoingCallReceiver", "doReceive: proceeding with call...");
            }
            OutgoingCallBroadcaster.this.startSipCallOptionHandler(context, intent, uri, number);
            return true;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void startSipCallOptionHandler(Context context, Intent intent, Uri uri, String str) {
        boolean booleanExtra = intent.getBooleanExtra("ims_videocall", false);
        Intent intent2 = new Intent("android.intent.action.CALL", uri);
        intent2.putExtra("android.phone.extra.ACTUAL_NUMBER_TO_DIAL", str);
        intent2.putExtra("subscription", this.mSubscription);
        intent2.putExtra("ip_call", this.mIPCall);
        CallGatewayManager.checkAndCopyPhoneProviderExtras(intent, intent2);
        PhoneUtils.copyImsExtras(intent, intent2);
        if (booleanExtra) {
            intent2.putExtra("ims_videocall", booleanExtra);
        }
        Intent intent3 = new Intent("com.android.phone.SIP_SELECT_PHONE", uri);
        intent3.setClass(context, SipCallOptionHandler.class);
        intent3.putExtra("android.phone.extra.NEW_CALL_INTENT", intent2);
        intent3.addFlags(268435456);
        if (DBG) {
            Log.v("OutgoingCallBroadcaster", "startSipCallOptionHandler(): calling startActivity: " + intent3);
        }
        context.startActivity(intent3);
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.outgoing_call_broadcaster);
        this.mWaitingSpinner = (ProgressBar) findViewById(R.id.spinner);
        Intent intent = getIntent();
        if (DBG) {
            Configuration configuration = getResources().getConfiguration();
            Log.v("OutgoingCallBroadcaster", "onCreate: this = " + this + ", icicle = " + icicle);
            Log.v("OutgoingCallBroadcaster", " - getIntent() = " + intent);
            Log.v("OutgoingCallBroadcaster", " - extras = " + intent.getExtras());
            Log.v("OutgoingCallBroadcaster", " - configuration = " + configuration);
        }
        boolean isIMSVTCall = intent.getBooleanExtra("ims_videocall", false);
        Log.v("OutgoingCallBroadcaster", "onCreate: isIMSVTCall= " + isIMSVTCall);
        if (icicle != null) {
            Log.i("OutgoingCallBroadcaster", "onCreate: non-null icicle!  Bailing out, not sending NEW_OUTGOING_CALL broadcast...");
            return;
        }
        processIntent(intent);
        if (DBG) {
            Log.v("OutgoingCallBroadcaster", "At the end of onCreate(). isFinishing(): " + isFinishing());
        }
    }

    private void processIntent(Intent intent) {
        if (DBG) {
            Log.v("OutgoingCallBroadcaster", "processIntent() = " + intent + ", thread: " + Thread.currentThread());
        }
        getResources().getConfiguration();
        if (!PhoneGlobals.sVoiceCapable) {
            Log.i("OutgoingCallBroadcaster", "This device is detected as non-voice-capable device.");
            handleNonVoiceCapable(intent);
            return;
        }
        this.mIPCall = intent.getBooleanExtra("ip_call", false);
        boolean zIsPromptEnabled = intent.getIntExtra("subscription", -1) == -1 ? MSimPhoneFactory.isPromptEnabled() : false;
        String numberFromIntent = PhoneNumberUtils.getNumberFromIntent(intent, this);
        boolean zIsEmergencyNumber = PhoneNumberUtils.isEmergencyNumber(numberFromIntent);
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled() && zIsPromptEnabled && activeSubCount() > 1 && !isIntentFromBluetooth(intent) && !isSIPCall(numberFromIntent, intent) && !zIsEmergencyNumber) {
            Log.d("OutgoingCallBroadcaster", "Start multisimdialer activity and get the sub selected by user");
            Intent intent2 = new Intent(this, (Class<?>) MSimDialerActivity.class);
            intent2.setData(intent.getData());
            intent2.setAction(intent.getAction());
            startActivityForResult(intent2, 1);
            return;
        }
        this.mSubscription = intent.getIntExtra("subscription", PhoneGlobals.getInstance().getVoiceSubscription());
        Log.d("OutgoingCallBroadcaster", "subscription when there is (from Extra):" + this.mSubscription);
        processMSimIntent(intent);
    }

    private void processMSimIntent(Intent intent) {
        int launchedFromUid;
        String launchedFromPackage;
        boolean callNow;
        String action = intent.getAction();
        intent.putExtra("ip_call", this.mIPCall);
        String number = PhoneNumberUtils.getNumberFromIntent(intent, this);
        boolean isConferenceUri = intent.getBooleanExtra("org.codeaurora.extra.DIAL_CONFERENCE_URI", false);
        Log.d("OutgoingCallBroadcaster", "outGoingcallBroadCaster action is " + action + " number = " + number);
        if (number != null) {
            if (!PhoneNumberUtils.isUriNumber(number) && !isConferenceUri) {
                number = PhoneNumberUtils.stripSeparators(PhoneNumberUtils.convertKeypadLettersToDigits(number));
            }
        } else {
            Log.w("OutgoingCallBroadcaster", "The number obtained from Intent is null.");
        }
        AppOpsManager appOps = (AppOpsManager) getSystemService("appops");
        try {
            launchedFromUid = ActivityManagerNative.getDefault().getLaunchedFromUid(getActivityToken());
            launchedFromPackage = ActivityManagerNative.getDefault().getLaunchedFromPackage(getActivityToken());
        } catch (RemoteException e) {
            launchedFromUid = -1;
            launchedFromPackage = null;
        }
        if (appOps.noteOpNoThrow(13, launchedFromUid, launchedFromPackage) != 0) {
            Log.w("OutgoingCallBroadcaster", "Rejecting call from uid " + launchedFromUid + " package " + launchedFromPackage);
            finish();
            return;
        }
        boolean emergencyOnIms = false;
        if (getClass().getName().equals(intent.getComponent().getClassName()) && !"android.intent.action.CALL".equals(intent.getAction())) {
            Log.w("OutgoingCallBroadcaster", "Attempt to deliver non-CALL action; forcing to CALL");
            intent.setAction("android.intent.action.CALL");
        }
        if (number == null || PhoneNumberUtils.isLocalEmergencyNumber(number, this)) {
        }
        boolean isPotentialEmergencyNumber = number != null && PhoneNumberUtils.isPotentialLocalEmergencyNumber(number, this);
        if ("android.intent.action.CALL_PRIVILEGED".equals(action)) {
            if (isPotentialEmergencyNumber) {
                Log.i("OutgoingCallBroadcaster", "ACTION_CALL_PRIVILEGED is used while the number is a potential emergency number. Use ACTION_CALL_EMERGENCY as an action instead.");
                action = "android.intent.action.CALL_EMERGENCY";
            } else {
                action = "android.intent.action.CALL";
            }
            if (DBG) {
                Log.v("OutgoingCallBroadcaster", " - updating action from CALL_PRIVILEGED to " + action);
            }
            intent.setAction(action);
        }
        if ("android.intent.action.CALL".equals(action)) {
            if (isPotentialEmergencyNumber) {
                Log.w("OutgoingCallBroadcaster", "Cannot call potential emergency number '" + number + "' with CALL Intent " + intent + ".");
                Log.i("OutgoingCallBroadcaster", "Launching default dialer instead...");
                Intent invokeFrameworkDialer = new Intent();
                Resources resources = getResources();
                invokeFrameworkDialer.setClassName(resources.getString(R.string.ui_default_package), resources.getString(R.string.dialer_default_class));
                invokeFrameworkDialer.setAction("android.intent.action.DIAL");
                invokeFrameworkDialer.setData(intent.getData());
                if (DBG) {
                    Log.v("OutgoingCallBroadcaster", "onCreate(): calling startActivity for Dialer: " + invokeFrameworkDialer);
                }
                startActivity(invokeFrameworkDialer);
                finish();
                return;
            }
            if (PhoneUtils.isAnyOtherSubActive(this.mSubscription) && MSimTelephonyManager.getDefault().getMultiSimConfiguration() != MSimTelephonyManager.MultiSimVariants.DSDA) {
                Log.d("OutgoingCallBroadcaster", "Call not allowed, as other sub is already active" + this.mSubscription);
                handleNonVoiceCapable(intent);
                return;
            } else {
                intent.putExtra("subscription", this.mSubscription);
                Log.d("OutgoingCallBroadcaster", "for non emergency call,sub is  :" + this.mSubscription);
                callNow = false;
            }
        } else if ("android.intent.action.CALL_EMERGENCY".equals(action)) {
            if (!isPotentialEmergencyNumber) {
                Log.w("OutgoingCallBroadcaster", "Cannot call non-potential-emergency number " + number + " with EMERGENCY_CALL Intent " + intent + ". Finish the Activity immediately.");
                finish();
                return;
            }
            callNow = true;
        } else {
            Log.e("OutgoingCallBroadcaster", "Unhandled Intent " + intent + ". Finish the Activity immediately.");
            finish();
            return;
        }
        PhoneGlobals.getInstance().wakeUpScreen();
        if (TextUtils.isEmpty(number)) {
            if (intent.getBooleanExtra("com.android.phone.extra.SEND_EMPTY_FLASH", false)) {
                Log.i("OutgoingCallBroadcaster", "onCreate: SEND_EMPTY_FLASH...");
                PhoneUtils.sendEmptyFlash(PhoneGlobals.getPhone());
                finish();
                return;
            } else {
                Log.i("OutgoingCallBroadcaster", "onCreate: null or empty number, setting callNow=true...");
                callNow = true;
                intent.putExtra("subscription", this.mSubscription);
            }
        }
        if (callNow) {
            Log.i("OutgoingCallBroadcaster", "onCreate(): callNow case! Calling placeCall(): " + intent);
            if (PhoneUtils.isCallOnImsEnabled()) {
                if (!MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                    Log.d("OutgoingCallBroadcaster", "IMS is enabled on SS, place IMS emergency call");
                    PhoneUtils.convertCallToIms(intent, 0);
                } else {
                    Log.d("OutgoingCallBroadcaster", "IMS is enabled in Multisim, taken care later");
                }
                emergencyOnIms = true;
            }
            PhoneGlobals.getInstance().callController.placeCall(intent);
        }
        Uri uri = intent.getData();
        String scheme = uri.getScheme();
        if (("sip".equals(scheme) || PhoneNumberUtils.isUriNumber(number)) && !emergencyOnIms) {
            Log.i("OutgoingCallBroadcaster", "The requested number was detected as SIP call.");
            startSipCallOptionHandler(this, intent, uri, number);
            finish();
            return;
        }
        if (!processAddParticipant(intent, number)) {
            Intent broadcastIntent = new Intent("android.intent.action.NEW_OUTGOING_CALL");
            if (number != null) {
                broadcastIntent.putExtra("android.intent.extra.PHONE_NUMBER", number);
            }
            CallGatewayManager.checkAndCopyPhoneProviderExtras(intent, broadcastIntent);
            broadcastIntent.putExtra("android.phone.extra.ALREADY_CALLED", callNow);
            broadcastIntent.putExtra("android.phone.extra.ORIGINAL_URI", uri.toString());
            broadcastIntent.putExtra("org.codeaurora.extra.DIAL_CONFERENCE_URI", intent.getBooleanExtra("org.codeaurora.extra.DIAL_CONFERENCE_URI", false));
            broadcastIntent.putExtra("ims_videocall", intent.getBooleanExtra("ims_videocall", false));
            broadcastIntent.addFlags(268435456);
            if (DBG) {
                Log.v("OutgoingCallBroadcaster", " - Broadcasting intent: " + broadcastIntent + ".");
            }
            boolean isIMSVTCall = intent.getBooleanExtra("ims_videocall", false);
            if (DBG) {
                Log.v("OutgoingCallBroadcaster", "processMSimIntent: isIMSVTCall= " + isIMSVTCall);
            }
            this.mHandler.sendEmptyMessageDelayed(101, 2000L);
            sendOrderedBroadcastAsUser(broadcastIntent, UserHandle.OWNER, "android.permission.PROCESS_OUTGOING_CALLS", new OutgoingCallReceiver(), null, -1, number, null);
        }
    }

    private boolean processAddParticipant(Intent intent, String number) {
        String[] extras = null;
        boolean ret = false;
        if (intent.getBooleanExtra("add_participant", false)) {
            boolean isConferenceUri = intent.getBooleanExtra("org.codeaurora.extra.DIAL_CONFERENCE_URI", false);
            if (isConferenceUri) {
                Map<String, String> extrasMap = Maps.newHashMap();
                extrasMap.put("isConferenceUri", Boolean.toString(isConferenceUri));
                extras = PhoneUtils.getExtrasFromMap(extrasMap);
            }
            PhoneUtils.addParticipant(number, 0, 10, extras);
            ret = true;
            finish();
        }
        Log.d("OutgoingCallBroadcaster", "processAddParticipant return = " + ret);
        return ret;
    }

    @Override // android.app.Activity
    protected void onStop() {
        removeDialog(1);
        super.onStop();
    }

    private void handleNonVoiceCapable(Intent intent) {
        if (DBG) {
            Log.v("OutgoingCallBroadcaster", "handleNonVoiceCapable: handling " + intent + " on non-voice-capable device...");
        }
        showDialog(1);
    }

    @Override // android.app.Activity
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case 1:
                Dialog dialog = new AlertDialog.Builder(this).setTitle(R.string.not_voice_capable).setIconAttribute(android.R.attr.alertDialogIcon).setPositiveButton(android.R.string.ok, this).setOnCancelListener(this).create();
                return dialog;
            default:
                Log.w("OutgoingCallBroadcaster", "onCreateDialog: unexpected ID " + id);
                return null;
        }
    }

    @Override // android.content.DialogInterface.OnClickListener
    public void onClick(DialogInterface dialog, int id) {
        finish();
    }

    @Override // android.content.DialogInterface.OnCancelListener
    public void onCancel(DialogInterface dialog) {
        finish();
    }

    @Override // android.app.Activity
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == 0) {
            Log.d("OutgoingCallBroadcaster", "activity cancelled or backkey pressed ");
            finish();
        } else if (resultCode == -1) {
            Bundle extras = data.getExtras();
            this.mSubscription = extras.getInt("subscription");
            Log.d("OutgoingCallBroadcaster", "subscription selected from multiSimDialer" + this.mSubscription);
            processMSimIntent(data);
        }
    }

    private int activeSubCount() {
        SubscriptionManager subManager = SubscriptionManager.getInstance();
        int count = subManager.getActiveSubscriptionsCount();
        if (DBG) {
            Log.v("OutgoingCallBroadcaster", "count of subs activated " + count);
        }
        return count;
    }

    private boolean isIntentFromBluetooth(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null || extras.getString("Bluetooth") == null || !extras.getString("Bluetooth").equals("true")) {
            return false;
        }
        Log.d("OutgoingCallBroadcaster", "isIntentFromBluetooth trueintent :" + extras.getString("Bluetooth"));
        return true;
    }

    private boolean isSIPCall(String number, Intent intent) {
        String scheme;
        boolean sipCall = false;
        if (intent.getData() != null && (scheme = intent.getData().getScheme()) != null && ("sip".equals(scheme) || PhoneNumberUtils.isUriNumber(number))) {
            sipCall = true;
        }
        Log.d("OutgoingCallBroadcaster", "isSIPCall : " + sipCall);
        return sipCall;
    }

    @Override // android.app.Activity, android.content.ComponentCallbacks
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (DBG) {
            Log.v("OutgoingCallBroadcaster", "onConfigurationChanged: newConfig = " + newConfig);
        }
    }
}
