package com.android.phone;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyCapabilities;

/* JADX INFO: loaded from: classes.dex */
public class InCallScreenShowActivation extends Activity {
    private static final boolean DBG;

    static {
        DBG = SystemProperties.getInt("ro.debuggable", 0) == 1;
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Intent intent = getIntent();
        if (DBG) {
            Log.d("InCallScreenShowActivation", "onCreate: intent = " + intent);
        }
        Bundle extras = intent.getExtras();
        if (DBG && extras != null) {
            Log.d("InCallScreenShowActivation", "      - has extras: size = " + extras.size());
            Log.d("InCallScreenShowActivation", "      - extras = " + extras);
        }
        PhoneGlobals app = PhoneGlobals.getInstance();
        Phone phone = PhoneGlobals.getPhone();
        if (!TelephonyCapabilities.supportsOtasp(phone)) {
            Log.w("InCallScreenShowActivation", "CDMA Provisioning not supported on this device");
            setResult(0);
            finish();
            return;
        }
        if (intent.getAction().equals("com.android.phone.PERFORM_CDMA_PROVISIONING")) {
            boolean usesHfa = getResources().getBoolean(R.bool.config_use_hfa_for_provisioning);
            if (usesHfa) {
                Log.d("InCallScreenShowActivation", "Starting Hfa from ACTION_PERFORM_CDMA_PROVISIONING");
                startHfa();
                finish();
                return;
            }
            boolean interactiveMode = false;
            Log.d("InCallScreenShowActivation", "ACTION_PERFORM_CDMA_PROVISIONING (interactiveMode = false)...");
            if (intent.hasExtra("ota_override_interactive_mode") && SystemProperties.getInt("ro.debuggable", 0) == 1) {
                interactiveMode = intent.getBooleanExtra("ota_override_interactive_mode", false);
                Log.d("InCallScreenShowActivation", "===> MANUALLY OVERRIDING interactiveMode to " + interactiveMode);
            }
            app.cdmaOtaScreenState.otaspResultCodePendingIntent = (PendingIntent) intent.getParcelableExtra("otasp_result_code_pending_intent");
            if (interactiveMode) {
                if (DBG) {
                    Log.d("InCallScreenShowActivation", "==> Starting interactive CDMA provisioning...");
                }
                OtaUtils.startInteractiveOtasp(this);
                setResult(1);
            } else {
                if (DBG) {
                    Log.d("InCallScreenShowActivation", "==> Starting non-interactive CDMA provisioning...");
                }
                int callStatus = OtaUtils.startNonInteractiveOtasp(this);
                if (callStatus == 0) {
                    if (DBG) {
                        Log.d("InCallScreenShowActivation", "  ==> successful result from startNonInteractiveOtasp(): " + callStatus);
                    }
                    setResult(2);
                } else {
                    Log.w("InCallScreenShowActivation", "Failure code from startNonInteractiveOtasp(): " + callStatus);
                    setResult(3);
                }
            }
        } else {
            Log.e("InCallScreenShowActivation", "Unexpected intent action: " + intent);
            setResult(0);
        }
        finish();
    }

    private boolean isWizardRunning(Context context) {
        Intent intent = new Intent("android.intent.action.DEVICE_INITIALIZATION_WIZARD");
        ResolveInfo resolveInfo = context.getPackageManager().resolveActivity(intent, 65536);
        boolean provisioned = Settings.Global.getInt(context.getContentResolver(), "device_provisioned", 0) != 0;
        String mode = SystemProperties.get("ro.setupwizard.mode", "REQUIRED");
        boolean runningSetupWizard = "REQUIRED".equals(mode) || "OPTIONAL".equals(mode);
        if (DBG) {
            Log.v("InCallScreenShowActivation", "resolvInfo = " + resolveInfo + ", provisioned = " + provisioned + ", runningSetupWizard = " + runningSetupWizard);
        }
        return (resolveInfo == null || provisioned || !runningSetupWizard) ? false : true;
    }

    private void startHfa() {
        Intent intent = new Intent();
        PendingIntent pendingIntent = (PendingIntent) getIntent().getParcelableExtra("otasp_result_code_pending_intent");
        boolean z = !isWizardRunning(this);
        intent.setFlags(268435456);
        if (pendingIntent != null) {
            intent.putExtra("otasp_result_code_pending_intent", pendingIntent);
        }
        Log.v("InCallScreenShowActivation", "Starting hfa activation activity");
        if (z) {
            intent.setClassName(this, HfaActivity.class.getName());
            startActivity(intent);
        } else {
            intent.setClassName(this, HfaService.class.getName());
            startService(intent);
        }
        setResult(-1);
    }
}
