package com.android.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.MSimTelephonyManager;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import com.android.internal.telephony.Phone;
import com.codeaurora.telephony.msim.MSimPhoneFactory;
import com.codeaurora.telephony.msim.SubscriptionManager;

/* JADX INFO: loaded from: classes.dex */
public class MSimDialerActivity extends Activity {
    public static final String[] MULTI_SIM_NAME = {"perferred_name_sub1", "perferred_name_sub2"};
    private String mCallNumber;
    private Context mContext;
    private Intent mIntent;
    private String mNumber;
    private TextView mTextNumber;
    private AlertDialog mAlertDialog = null;
    private int mPhoneCount = 0;

    @Override // android.app.Activity
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        this.mContext = getApplicationContext();
        this.mCallNumber = getResources().getString(R.string.call_number);
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        this.mPhoneCount = MSimTelephonyManager.getDefault().getPhoneCount();
        this.mIntent = getIntent();
        Log.v("MSimDialerActivity", "Intent = " + this.mIntent);
        this.mNumber = PhoneNumberUtils.getNumberFromIntent(this.mIntent, this);
        Log.v("MSimDialerActivity", "mNumber " + this.mNumber);
        if (this.mNumber != null) {
            this.mNumber = PhoneNumberUtils.convertKeypadLettersToDigits(this.mNumber);
            this.mNumber = PhoneNumberUtils.stripSeparators(this.mNumber);
        }
        Phone phone = null;
        boolean phoneInCall = false;
        for (int i = 0; i < this.mPhoneCount; i++) {
            phone = MSimPhoneFactory.getPhone(i);
            boolean inCall = isInCall(phone);
            if (phone != null && inCall) {
                phoneInCall = true;
                break;
            }
        }
        if (phoneInCall && MSimTelephonyManager.getDefault().getMultiSimConfiguration() != MSimTelephonyManager.MultiSimVariants.DSDA) {
            Log.v("MSimDialerActivity", "subs [" + phone.getSubscription() + "] is in call");
            startOutgoingCall(phone.getSubscription());
        } else {
            Log.v("MSimDialerActivity", "launch dsdsdialer");
            launchMSDialer();
        }
        Log.d("MSimDialerActivity", "end of onResume()");
    }

    @Override // android.app.Activity
    protected void onPause() {
        super.onPause();
        Log.v("MSimDialerActivity", "onPause : " + this.mIntent);
        if (this.mAlertDialog != null) {
            this.mAlertDialog.dismiss();
            this.mAlertDialog = null;
        }
    }

    private int getSubscriptionForEmergencyCall() {
        Log.d("MSimDialerActivity", "emergency call, getVoiceSubscriptionInService");
        int sub = PhoneGlobals.getInstance().getVoiceSubscriptionInService();
        return sub;
    }

    private void launchMSDialer() {
        boolean isEmergency = PhoneNumberUtils.isEmergencyNumber(this.mNumber);
        if (isEmergency) {
            Log.d("MSimDialerActivity", "emergency call");
            startOutgoingCall(getSubscriptionForEmergencyCall());
            return;
        }
        LayoutInflater inflater = (LayoutInflater) this.mContext.getSystemService("layout_inflater");
        View layout = inflater.inflate(R.layout.dialer_ms, (ViewGroup) findViewById(R.id.layout_root));
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(layout);
        builder.setOnKeyListener(new DialogInterface.OnKeyListener() { // from class: com.android.phone.MSimDialerActivity.1
            @Override // android.content.DialogInterface.OnKeyListener
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                Log.d("MSimDialerActivity", "key code is :" + keyCode);
                switch (keyCode) {
                    case 4:
                        MSimDialerActivity.this.mAlertDialog.dismiss();
                        MSimDialerActivity.this.startOutgoingCall(99);
                        return true;
                    case 5:
                        Log.d("MSimDialerActivity", "event is" + event.getAction());
                        if (event.getAction() == 1) {
                            return true;
                        }
                        MSimDialerActivity.this.mAlertDialog.dismiss();
                        MSimDialerActivity.this.startOutgoingCall(MSimPhoneFactory.getVoiceSubscription());
                        return true;
                    case 84:
                        return true;
                    default:
                        return false;
                }
            }
        });
        this.mAlertDialog = builder.create();
        this.mAlertDialog.setCanceledOnTouchOutside(false);
        this.mTextNumber = (TextView) layout.findViewById(R.id.CallNumber);
        String vm = "";
        if (this.mIntent.getData() != null) {
            vm = this.mIntent.getData().getScheme();
        }
        if (vm != null && vm.equals("voicemail")) {
            this.mTextNumber.setText(this.mCallNumber + "VoiceMail");
            Log.d("MSimDialerActivity", "its voicemail!!!");
        } else {
            this.mTextNumber.setText(this.mCallNumber + this.mNumber);
        }
        Button callCancel = (Button) layout.findViewById(R.id.callcancel);
        callCancel.setOnClickListener(new View.OnClickListener() { // from class: com.android.phone.MSimDialerActivity.2
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                MSimDialerActivity.this.mAlertDialog.dismiss();
                MSimDialerActivity.this.startOutgoingCall(99);
            }
        });
        Button[] callButton = new Button[this.mPhoneCount];
        int[] callMark = {R.id.callmark1, R.id.callmark2, R.id.callmark3};
        SubscriptionManager subManager = SubscriptionManager.getInstance();
        for (int index = 0; index < this.mPhoneCount; index++) {
            if (subManager.isSubActive(index)) {
                Button button = (Button) layout.findViewById(callMark[index]);
                button.setVisibility(0);
            }
        }
        for (int index2 = 0; index2 < this.mPhoneCount; index2++) {
            callButton[index2] = (Button) layout.findViewById(callMark[index2]);
            String simName = Settings.System.getString(this.mContext.getContentResolver(), MULTI_SIM_NAME[index2]);
            callButton[index2].setText(simName);
            callButton[index2].setOnClickListener(new View.OnClickListener() { // from class: com.android.phone.MSimDialerActivity.3
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    MSimDialerActivity.this.mAlertDialog.dismiss();
                    switch (v.getId()) {
                        case R.id.callmark1 /* 2131165209 */:
                            MSimDialerActivity.this.startOutgoingCall(0);
                            break;
                        case R.id.callmark2 /* 2131165210 */:
                            MSimDialerActivity.this.startOutgoingCall(1);
                            break;
                        case R.id.callmark3 /* 2131165211 */:
                            MSimDialerActivity.this.startOutgoingCall(2);
                            break;
                    }
                }
            });
        }
        int index3 = MSimPhoneFactory.getVoiceSubscription();
        if (index3 < this.mPhoneCount) {
            callButton[index3].setBackgroundResource(R.drawable.highlight_btn_call);
        }
        this.mAlertDialog.show();
    }

    boolean isInCall(Phone phone) {
        return phone != null && (phone.getForegroundCall().getState().isAlive() || phone.getBackgroundCall().getState().isAlive() || phone.getRingingCall().getState().isAlive());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void startOutgoingCall(int i) {
        this.mIntent.putExtra("subscription", i);
        this.mIntent.setClass(this, OutgoingCallBroadcaster.class);
        Log.v("MSimDialerActivity", "startOutgoingCall for sub " + i + " from intent: " + this.mIntent);
        if (i < this.mPhoneCount) {
            setResult(-1, this.mIntent);
        } else {
            setResult(0, this.mIntent);
            Log.d("MSimDialerActivity", "call cancelled");
        }
        finish();
    }
}
