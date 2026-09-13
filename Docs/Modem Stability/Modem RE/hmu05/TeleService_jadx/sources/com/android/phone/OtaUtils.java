package com.android.phone;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ToggleButton;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyCapabilities;

/* JADX INFO: loaded from: classes.dex */
public class OtaUtils {
    private static boolean sIsWizardMode = true;
    private static int sOtaCallLteRetries = 0;
    private final BluetoothManager mBluetoothManager;
    private Context mContext;
    private boolean mInteractive;
    private OtaWidgetData mOtaWidgetData;
    public final int OTA_SPC_TIMEOUT = 60;
    public final int OTA_FAILURE_DIALOG_TIMEOUT = 2;
    private PhoneGlobals mApplication = PhoneGlobals.getInstance();

    public static class CdmaOtaConfigData {
        public int otaShowActivationScreen = 0;
        public int otaShowListeningScreen = 0;
        public int otaShowActivateFailTimes = 0;
        public int otaPlaySuccessFailureTone = 0;
    }

    public static class CdmaOtaInCallScreenUiState {
        public State state = State.UNDEFINED;

        public enum State {
            UNDEFINED,
            NORMAL,
            ENDED
        }
    }

    public static class CdmaOtaProvisionData {
        public int activationCount;
        public boolean inOtaSpcState;
        public boolean isOtaCallCommitted;
        public boolean isOtaCallIntentProcessed;
        public long otaSpcUptime;
    }

    public static class CdmaOtaScreenState {
        public OtaScreenState otaScreenState = OtaScreenState.OTA_STATUS_UNDEFINED;
        public PendingIntent otaspResultCodePendingIntent;

        public enum OtaScreenState {
            OTA_STATUS_UNDEFINED,
            OTA_STATUS_ACTIVATION,
            OTA_STATUS_LISTENING,
            OTA_STATUS_PROGRESS,
            OTA_STATUS_SUCCESS_FAILURE_DLG
        }
    }

    private class OtaWidgetData {
        public View callCardOtaButtonsActivate;
        public View callCardOtaButtonsFailSuccess;
        public View callCardOtaButtonsListenProgress;
        public AlertDialog otaFailureDialog;
        public Button otaNextButton;
        public Button otaSkipButton;
        public ToggleButton otaSpeakerButton;
        public TextView otaTextActivate;
        public TextView otaTextListenProgress;
        public ProgressBar otaTextProgressBar;
        public TextView otaTextSuccessFail;
        public TextView otaTitle;
        public Button otaTryAgainButton;
        public ViewGroup otaUpperWidgets;
        public AlertDialog spcErrorDialog;
    }

    public OtaUtils(Context context, boolean interactive, BluetoothManager bluetoothManager) {
        this.mInteractive = true;
        this.mContext = context;
        this.mInteractive = interactive;
        this.mBluetoothManager = bluetoothManager;
    }

    public static boolean maybeDoOtaCall(Context context, Handler handler, int request) {
        PhoneGlobals app = PhoneGlobals.getInstance();
        Phone phone = app.phone;
        if (ActivityManager.isRunningInTestHarness()) {
            Log.i("OtaUtils", "Don't run provisioning when in test harness");
            return true;
        }
        if (!TelephonyCapabilities.supportsOtasp(phone)) {
            return true;
        }
        if (!phone.isMinInfoReady()) {
            phone.registerForSubscriptionInfoReady(handler, request, (Object) null);
            return false;
        }
        phone.unregisterForSubscriptionInfoReady(handler);
        if (getLteOnCdmaMode(context) == -1) {
            if (sOtaCallLteRetries < 5) {
                handler.sendEmptyMessageDelayed(request, 3000L);
                sOtaCallLteRetries++;
                return false;
            }
            Log.w("OtaUtils", "maybeDoOtaCall: LTE state still unknown: giving up");
            return true;
        }
        boolean phoneNeedsActivation = phone.needsOtaServiceProvisioning();
        int otaShowActivationScreen = context.getResources().getInteger(R.integer.OtaShowActivationScreen);
        if (PhoneGlobals.sVoiceCapable && getLteOnCdmaMode(context) == 0) {
            if (!phoneNeedsActivation || otaShowActivationScreen != 1) {
                return true;
            }
            app.cdmaOtaProvisionData.isOtaCallIntentProcessed = false;
            sIsWizardMode = false;
            startInteractiveOtasp(context);
            return true;
        }
        if (!phoneNeedsActivation) {
            return true;
        }
        app.cdmaOtaProvisionData.isOtaCallIntentProcessed = false;
        Intent newIntent = new Intent("com.android.phone.PERFORM_VOICELESS_CDMA_PROVISIONING");
        newIntent.setFlags(268435456);
        newIntent.putExtra("com.android.phone.VOICELESS_PROVISIONING_OFFER_DONTSHOW", true);
        try {
            context.startActivity(newIntent);
            return true;
        } catch (ActivityNotFoundException e) {
            loge("No activity Handling PERFORM_VOICELESS_CDMA_PROVISIONING!");
            return false;
        }
    }

    public static void startInteractiveOtasp(Context context) {
        Intent action = new Intent().setClass(context, InCallScreen.class).setAction("com.android.phone.DISPLAY_ACTIVATION_SCREEN");
        action.setFlags(268435456);
        setupOtaspCall(action);
        Log.i("OtaUtils", "startInteractiveOtasp: launching InCallScreen in 'activate' state: " + action);
        context.startActivity(action);
    }

    public static int startNonInteractiveOtasp(Context context) {
        PhoneGlobals app = PhoneGlobals.getInstance();
        if (app.otaUtils != null) {
            Log.i("OtaUtils", "startNonInteractiveOtasp: OtaUtils already exists; nuking the old one and starting again...");
        }
        app.otaUtils = new OtaUtils(context, false, app.getBluetoothManager());
        Phone phone = PhoneGlobals.getPhone();
        Log.i("OtaUtils", "startNonInteractiveOtasp: placing call to '*22899'...");
        int callStatus = PhoneUtils.placeCall(context, phone, "*22899", null, false);
        if (callStatus != 0) {
            Log.w("OtaUtils", "Failure from placeCall() for OTA number '*22899': code " + callStatus);
        }
        return callStatus;
    }

    public static boolean isOtaspCallIntent(Intent intent) {
        String action;
        PhoneGlobals app = PhoneGlobals.getInstance();
        Phone phone = app.mCM.getDefaultPhone();
        if (intent == null || !TelephonyCapabilities.supportsOtasp(phone) || (action = intent.getAction()) == null || !action.equals("android.intent.action.CALL")) {
            return false;
        }
        if (app.cdmaOtaScreenState == null || app.cdmaOtaProvisionData == null) {
            throw new IllegalStateException("isOtaspCallIntent: app.cdmaOta* objects(s) not initialized");
        }
        try {
            String number = PhoneUtils.getInitialNumber(intent);
            return phone.isOtaSpNumber(number);
        } catch (PhoneUtils.VoiceMailNumberMissingException e) {
            return false;
        }
    }

    public static void setupOtaspCall(Intent intent) {
        PhoneGlobals app = PhoneGlobals.getInstance();
        if (app.otaUtils != null) {
            Log.i("OtaUtils", "setupOtaspCall: OtaUtils already exists; replacing with new instance...");
        }
        app.otaUtils = new OtaUtils(app.getApplicationContext(), true, app.getBluetoothManager());
        app.otaUtils.setCdmaOtaInCallScreenUiState(CdmaOtaInCallScreenUiState.State.NORMAL);
        if (app.cdmaOtaProvisionData != null) {
            app.cdmaOtaProvisionData.isOtaCallCommitted = false;
        }
    }

    private void setSpeaker(boolean state) {
        if (this.mInteractive && state != PhoneUtils.isSpeakerOn(this.mContext)) {
            if (state && this.mBluetoothManager.isBluetoothAvailable() && this.mBluetoothManager.isBluetoothAudioConnected()) {
                this.mBluetoothManager.disconnectBluetoothAudio();
            }
            PhoneUtils.turnOnSpeaker(this.mContext, state, true);
        }
    }

    public void onOtaProvisionStatusChanged(AsyncResult r) {
        int[] OtaStatus = (int[]) r.result;
        switch (OtaStatus[0]) {
            case 0:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 9:
            case 10:
            case 11:
                if (getCdmaOtaInCallScreenUiState() == CdmaOtaInCallScreenUiState.State.NORMAL) {
                    updateOtaspProgress();
                }
                break;
            case 1:
                updateOtaspProgress();
                this.mApplication.cdmaOtaProvisionData.otaSpcUptime = SystemClock.elapsedRealtime();
                if (this.mInteractive) {
                    otaShowSpcErrorNotice(60);
                } else {
                    sendOtaspResult(4);
                }
                break;
            case 8:
                this.mApplication.cdmaOtaProvisionData.isOtaCallCommitted = true;
                if (this.mApplication.cdmaOtaScreenState.otaScreenState != CdmaOtaScreenState.OtaScreenState.OTA_STATUS_UNDEFINED) {
                    updateOtaspProgress();
                }
                break;
        }
    }

    public void onOtaspDisconnect() {
        if (!this.mInteractive) {
            updateNonInteractiveOtaSuccessFailure();
        }
    }

    private void updateOtaspProgress() {
        if (this.mInteractive) {
            otaShowInProgressScreen();
        }
    }

    private void updateNonInteractiveOtaSuccessFailure() {
        int resultCode = this.mApplication.cdmaOtaProvisionData.isOtaCallCommitted ? 2 : 3;
        sendOtaspResult(resultCode);
    }

    private void sendOtaspResult(int resultCode) {
        Intent extraStuff = new Intent();
        extraStuff.putExtra("otasp_result_code", resultCode);
        if (this.mApplication.cdmaOtaScreenState == null) {
            Log.e("OtaUtils", "updateNonInteractiveOtaSuccessFailure: no cdmaOtaScreenState object!");
            return;
        }
        if (this.mApplication.cdmaOtaScreenState.otaspResultCodePendingIntent == null) {
            Log.w("OtaUtils", "updateNonInteractiveOtaSuccessFailure: null otaspResultCodePendingIntent!");
            return;
        }
        try {
            this.mApplication.cdmaOtaScreenState.otaspResultCodePendingIntent.send(this.mContext, 0, extraStuff);
        } catch (PendingIntent.CanceledException e) {
            Log.e("OtaUtils", "PendingIntent send() failed: " + e);
        }
    }

    private void otaShowInProgressScreen() {
        if (!this.mInteractive) {
            Log.w("OtaUtils", "otaShowInProgressScreen: not interactive!");
            return;
        }
        this.mApplication.cdmaOtaScreenState.otaScreenState = CdmaOtaScreenState.OtaScreenState.OTA_STATUS_PROGRESS;
        if (this.mOtaWidgetData == null) {
            Log.w("OtaUtils", "otaShowInProgressScreen: UI widgets not set up yet!");
            return;
        }
        if (!isDialerOpened()) {
            otaScreenInitialize();
            this.mOtaWidgetData.otaTextListenProgress.setVisibility(0);
            this.mOtaWidgetData.otaTextListenProgress.setText(R.string.ota_progress);
            this.mOtaWidgetData.otaTextProgressBar.setVisibility(0);
            this.mOtaWidgetData.callCardOtaButtonsListenProgress.setVisibility(0);
            this.mOtaWidgetData.otaSpeakerButton.setVisibility(0);
            boolean speakerOn = PhoneUtils.isSpeakerOn(this.mContext);
            this.mOtaWidgetData.otaSpeakerButton.setChecked(speakerOn);
        }
    }

    private void otaShowSpcErrorNotice(int length) {
        if (this.mOtaWidgetData.spcErrorDialog == null) {
            this.mApplication.cdmaOtaProvisionData.inOtaSpcState = true;
            DialogInterface.OnKeyListener keyListener = new DialogInterface.OnKeyListener() { // from class: com.android.phone.OtaUtils.1
                @Override // android.content.DialogInterface.OnKeyListener
                public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                    OtaUtils.log("Ignoring key events...");
                    return true;
                }
            };
            this.mOtaWidgetData.spcErrorDialog = new AlertDialog.Builder(null).setMessage(R.string.ota_spc_failure).setOnKeyListener(keyListener).create();
            this.mOtaWidgetData.spcErrorDialog.getWindow().addFlags(144);
            this.mOtaWidgetData.spcErrorDialog.show();
            long j = length * 1000;
        }
    }

    private void otaScreenInitialize() {
        if (!this.mInteractive) {
            Log.w("OtaUtils", "otaScreenInitialize: not interactive!");
            return;
        }
        this.mOtaWidgetData.otaTitle.setText(R.string.ota_title_activate);
        this.mOtaWidgetData.otaTextActivate.setVisibility(8);
        this.mOtaWidgetData.otaTextListenProgress.setVisibility(8);
        this.mOtaWidgetData.otaTextProgressBar.setVisibility(8);
        this.mOtaWidgetData.otaTextSuccessFail.setVisibility(8);
        this.mOtaWidgetData.callCardOtaButtonsActivate.setVisibility(8);
        this.mOtaWidgetData.callCardOtaButtonsListenProgress.setVisibility(8);
        this.mOtaWidgetData.callCardOtaButtonsFailSuccess.setVisibility(8);
        this.mOtaWidgetData.otaSpeakerButton.setVisibility(8);
        this.mOtaWidgetData.otaTryAgainButton.setVisibility(8);
        this.mOtaWidgetData.otaNextButton.setVisibility(8);
        this.mOtaWidgetData.otaUpperWidgets.setVisibility(0);
        this.mOtaWidgetData.otaSkipButton.setVisibility(0);
    }

    public boolean isDialerOpened() {
        return false;
    }

    public void dismissAllOtaDialogs() {
        if (this.mOtaWidgetData != null) {
            if (this.mOtaWidgetData.spcErrorDialog != null) {
                this.mOtaWidgetData.spcErrorDialog.dismiss();
                this.mOtaWidgetData.spcErrorDialog = null;
            }
            if (this.mOtaWidgetData.otaFailureDialog != null) {
                this.mOtaWidgetData.otaFailureDialog.dismiss();
                this.mOtaWidgetData.otaFailureDialog = null;
            }
        }
    }

    public void cleanOtaScreen(boolean disableSpeaker) {
        this.mApplication.cdmaOtaScreenState.otaScreenState = CdmaOtaScreenState.OtaScreenState.OTA_STATUS_UNDEFINED;
        this.mApplication.cdmaOtaProvisionData.isOtaCallCommitted = false;
        this.mApplication.cdmaOtaProvisionData.isOtaCallIntentProcessed = false;
        this.mApplication.cdmaOtaProvisionData.inOtaSpcState = false;
        this.mApplication.cdmaOtaProvisionData.activationCount = 0;
        this.mApplication.cdmaOtaProvisionData.otaSpcUptime = 0L;
        this.mApplication.cdmaOtaInCallScreenUiState.state = CdmaOtaInCallScreenUiState.State.UNDEFINED;
        if (this.mInteractive && this.mOtaWidgetData != null) {
            this.mOtaWidgetData.otaTextActivate.setVisibility(8);
            this.mOtaWidgetData.otaTextListenProgress.setVisibility(8);
            this.mOtaWidgetData.otaTextProgressBar.setVisibility(8);
            this.mOtaWidgetData.otaTextSuccessFail.setVisibility(8);
            this.mOtaWidgetData.callCardOtaButtonsActivate.setVisibility(8);
            this.mOtaWidgetData.callCardOtaButtonsListenProgress.setVisibility(8);
            this.mOtaWidgetData.callCardOtaButtonsFailSuccess.setVisibility(8);
            this.mOtaWidgetData.otaUpperWidgets.setVisibility(8);
            this.mOtaWidgetData.otaNextButton.setVisibility(8);
            this.mOtaWidgetData.otaTryAgainButton.setVisibility(8);
        }
        if (disableSpeaker) {
            setSpeaker(false);
        }
    }

    public void setCdmaOtaInCallScreenUiState(CdmaOtaInCallScreenUiState.State state) {
        this.mApplication.cdmaOtaInCallScreenUiState.state = state;
    }

    public CdmaOtaInCallScreenUiState.State getCdmaOtaInCallScreenUiState() {
        return this.mApplication.cdmaOtaInCallScreenUiState.state;
    }

    private static int getLteOnCdmaMode(Context context) {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService("phone");
        return (telephonyManager == null || telephonyManager.getLteOnCdmaMode() == -1) ? SystemProperties.getInt("telephony.lteOnCdmaDevice", -1) : telephonyManager.getLteOnCdmaMode();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void log(String msg) {
        Log.d("OtaUtils", msg);
    }

    private static void loge(String msg) {
        Log.e("OtaUtils", msg);
    }
}
