package com.android.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.sip.SipException;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.MSimTelephonyManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.sip.SipPhone;
import com.android.phone.sip.SipProfileDb;
import com.android.phone.sip.SipSettings;
import com.android.phone.sip.SipSharedPreferences;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class SipCallOptionHandler extends Activity implements DialogInterface.OnCancelListener, DialogInterface.OnClickListener, CompoundButton.OnCheckedChangeListener {
    private static final boolean DBG;
    private String mCallOption;
    private int mImsCallType;
    private Intent mIntent;
    private String mNumber;
    private SipProfile mOutgoingSipProfile;
    private List<SipProfile> mProfileList;
    private SipProfileDb mSipProfileDb;
    private SipSharedPreferences mSipSharedPreferences;
    private TextView mUnsetPriamryHint;
    private Dialog[] mDialogs = new Dialog[8];
    private boolean mUseSipPhone = false;
    private boolean mMakePrimary = false;
    private int NoSimOnLTE = 10;
    private final Handler mHandler = new Handler() { // from class: com.android.phone.SipCallOptionHandler.1
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            if (msg.what == 1) {
                SipCallOptionHandler.this.finish();
            } else {
                Log.wtf("SipCallOptionHandler", "Unknown message id: " + msg.what);
            }
        }
    };

    static {
        DBG = SystemProperties.getInt("ro.debuggable", 0) == 1;
    }

    @Override // android.app.Activity
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String action = intent.getAction();
        if (!"com.android.phone.SIP_SELECT_PHONE".equals(action)) {
            Log.wtf("SipCallOptionHandler", "onCreate: got intent action '" + action + "', expected com.android.phone.SIP_SELECT_PHONE");
            finish();
            return;
        }
        this.mIntent = (Intent) intent.getParcelableExtra("android.phone.extra.NEW_CALL_INTENT");
        if (this.mIntent == null) {
            finish();
            return;
        }
        getWindow().addFlags(524288);
        boolean voipSupported = PhoneUtils.isVoipSupported(this);
        if (DBG) {
            Log.v("SipCallOptionHandler", "voipSupported: " + voipSupported);
        }
        this.mSipProfileDb = new SipProfileDb(this);
        this.mSipSharedPreferences = new SipSharedPreferences(this);
        this.mCallOption = this.mSipSharedPreferences.getSipCallOption();
        if (DBG) {
            Log.v("SipCallOptionHandler", "Call option: " + this.mCallOption);
        }
        boolean isIMSVTCall = this.mIntent.getBooleanExtra("ims_videocall", false);
        if (DBG) {
            Log.e("SipCallOptionHandler", "Call intent: " + this.mIntent + "extras" + this.mIntent.getExtras());
            Log.e("SipCallOptionHandler", "isIMSVTCall = " + isIMSVTCall);
            Log.e("SipCallOptionHandler", "isMultiSimEnabled() = " + MSimTelephonyManager.getDefault().isMultiSimEnabled());
            Log.e("SipCallOptionHandler", "currentSubscription =  " + this.mIntent.getIntExtra("subscription", -1));
            Log.e("SipCallOptionHandler", "isIMSRegisterd = " + PhoneGlobals.isIMSRegisterd());
        }
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            if (PhoneGlobals.isIMSRegisterd()) {
                int SubscriptionOnLTE = getSubscriptionOnLTE();
                if (SubscriptionOnLTE == this.NoSimOnLTE) {
                    Log.e("SipCallOptionHandler", "ims registered and not on LTE, wrong!!!!!");
                    return;
                } else if (isIMSVTCall) {
                    this.mIntent.putExtra("subscription", SubscriptionOnLTE);
                    this.mImsCallType = 3;
                } else {
                    int currentSubscription = this.mIntent.getIntExtra("subscription", -1);
                    if (currentSubscription == SubscriptionOnLTE) {
                        this.mImsCallType = 0;
                    }
                }
            } else if (isIMSVTCall) {
                showDialog(7);
                return;
            }
        } else if (PhoneGlobals.isIMSRegisterd()) {
            if (isIMSVTCall) {
                this.mImsCallType = 3;
            } else {
                this.mImsCallType = 0;
            }
        } else if (isIMSVTCall) {
            showDialog(7);
            return;
        }
        Phone phone = PhoneUtils.getImsPhone(PhoneGlobals.getInstance().mCM);
        if (phone != null && phone.getSubscription() != this.mIntent.getIntExtra("subscription", 0)) {
            this.mImsCallType = 10;
        }
        if (DBG) {
            Log.v("SipCallOptionHandler", "IMS call type: " + this.mImsCallType);
        }
        Uri uri = this.mIntent.getData();
        String scheme = uri.getScheme();
        this.mNumber = PhoneNumberUtils.getNumberFromIntent(this.mIntent, this);
        boolean isInCellNetwork = PhoneGlobals.getInstance().phoneMgr.isRadioOn();
        boolean isKnownCallScheme = "tel".equals(scheme) || "sip".equals(scheme);
        boolean isRegularCall = ("tel".equals(scheme) && !PhoneNumberUtils.isUriNumber(this.mNumber)) || PhoneUtils.isImsCallIntent(scheme, this.mIntent) || this.mIntent.getBooleanExtra("org.codeaurora.extra.DIAL_CONFERENCE_URI", false);
        if (!isKnownCallScheme) {
            setResultAndFinish();
            return;
        }
        if (!voipSupported) {
            if (!isRegularCall) {
                showDialog(4);
                return;
            } else {
                setResultAndFinish();
                return;
            }
        }
        if (!CallGatewayManager.hasPhoneProviderExtras(this.mIntent)) {
            if (!isNetworkConnected()) {
                if (!isRegularCall) {
                    showDialog(3);
                    return;
                }
            } else if (this.mCallOption.equals("SIP_ASK_ME_EACH_TIME") && isRegularCall && isInCellNetwork) {
                showDialog(0);
                return;
            } else if (!this.mCallOption.equals("SIP_ADDRESS_ONLY") || !isRegularCall) {
                this.mUseSipPhone = true;
            }
        }
        if (this.mUseSipPhone) {
            if (this.mSipProfileDb.getProfilesCount() > 0 || !isRegularCall) {
                startGetPrimarySipPhoneThread();
                return;
            }
            this.mUseSipPhone = false;
        }
        setResultAndFinish();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void startDelayedFinish() {
        this.mHandler.sendEmptyMessageDelayed(1, 2000L);
    }

    @Override // android.app.Activity
    public void onPause() {
        super.onPause();
        if (!isFinishing()) {
            Dialog[] arr$ = this.mDialogs;
            for (Dialog dialog : arr$) {
                if (dialog != null) {
                    dialog.dismiss();
                }
            }
            finish();
        }
    }

    @Override // android.app.Activity
    protected Dialog onCreateDialog(int id) {
        Dialog dialog;
        switch (id) {
            case 0:
                dialog = new AlertDialog.Builder(this).setTitle(R.string.pick_outgoing_call_phone_type).setIconAttribute(android.R.attr.alertDialogIcon).setSingleChoiceItems(R.array.phone_type_values, -1, this).setNegativeButton(android.R.string.cancel, this).setOnCancelListener(this).create();
                break;
            case 1:
                dialog = new AlertDialog.Builder(this).setTitle(R.string.pick_outgoing_sip_phone).setIconAttribute(android.R.attr.alertDialogIcon).setSingleChoiceItems(getProfileNameArray(), -1, this).setNegativeButton(android.R.string.cancel, this).setOnCancelListener(this).create();
                addMakeDefaultCheckBox(dialog);
                break;
            case 2:
                dialog = new AlertDialog.Builder(this).setTitle(R.string.no_sip_account_found_title).setMessage(R.string.no_sip_account_found).setIconAttribute(android.R.attr.alertDialogIcon).setPositiveButton(R.string.sip_menu_add, this).setNegativeButton(android.R.string.cancel, this).setOnCancelListener(this).create();
                break;
            case 3:
                boolean wifiOnly = SipManager.isSipWifiOnly(this);
                dialog = new AlertDialog.Builder(this).setTitle(wifiOnly ? R.string.no_wifi_available_title : R.string.no_internet_available_title).setMessage(wifiOnly ? R.string.no_wifi_available : R.string.no_internet_available).setIconAttribute(android.R.attr.alertDialogIcon).setPositiveButton(android.R.string.ok, this).setOnCancelListener(this).create();
                break;
            case 4:
                dialog = new AlertDialog.Builder(this).setTitle(R.string.no_voip).setIconAttribute(android.R.attr.alertDialogIcon).setPositiveButton(android.R.string.ok, this).setOnCancelListener(this).create();
                break;
            case 5:
                dialog = new AlertDialog.Builder(this).setTitle(R.string.no_volte).setIconAttribute(android.R.attr.alertDialogIcon).setPositiveButton(android.R.string.ok, this).setOnCancelListener(this).create();
                break;
            case 6:
                dialog = new AlertDialog.Builder(this).setTitle(R.string.no_vt_allowed).setIconAttribute(android.R.attr.alertDialogIcon).setPositiveButton(android.R.string.ok, this).setOnCancelListener(this).create();
                break;
            case 7:
                dialog = new AlertDialog.Builder(this).setTitle(R.string.no_vt_support_in_current_network_title).setMessage(R.string.no_vt_support_in_current_network).setPositiveButton(android.R.string.ok, this).setNegativeButton(android.R.string.cancel, this).setOnCancelListener(this).create();
                break;
            default:
                dialog = null;
                break;
        }
        if (dialog != null) {
            this.mDialogs[id] = dialog;
        }
        return dialog;
    }

    private void addMakeDefaultCheckBox(Dialog dialog) {
        LayoutInflater inflater = (LayoutInflater) getSystemService("layout_inflater");
        View view = inflater.inflate(android.R.layout.activity_chooser_view, (ViewGroup) null);
        CheckBox makePrimaryCheckBox = (CheckBox) view.findViewById(android.R.id.chooser_row_text_option);
        makePrimaryCheckBox.setText(R.string.remember_my_choice);
        makePrimaryCheckBox.setOnCheckedChangeListener(this);
        this.mUnsetPriamryHint = (TextView) view.findViewById(android.R.id.chronometer);
        this.mUnsetPriamryHint.setText(R.string.reset_my_choice_hint);
        this.mUnsetPriamryHint.setVisibility(8);
        ((AlertDialog) dialog).setView(view);
    }

    private CharSequence[] getProfileNameArray() {
        CharSequence[] entries = new CharSequence[this.mProfileList.size()];
        int i = 0;
        for (SipProfile p : this.mProfileList) {
            entries[i] = p.getProfileName();
            i++;
        }
        return entries;
    }

    @Override // android.content.DialogInterface.OnClickListener
    public void onClick(DialogInterface dialogInterface, int i) {
        if (i == -2) {
            finish();
            return;
        }
        if (dialogInterface == this.mDialogs[0]) {
            String str = getResources().getStringArray(R.array.phone_type_values)[i];
            if (DBG) {
                Log.v("SipCallOptionHandler", "User pick phone " + str);
            }
            if (str.equals(getString(R.string.internet_phone))) {
                this.mUseSipPhone = true;
                startGetPrimarySipPhoneThread();
                return;
            }
        } else if (dialogInterface == this.mDialogs[1]) {
            this.mOutgoingSipProfile = this.mProfileList.get(i);
        } else {
            if (dialogInterface == this.mDialogs[3] || dialogInterface == this.mDialogs[4] || dialogInterface == this.mDialogs[6] || dialogInterface == this.mDialogs[5]) {
                finish();
                return;
            }
            if (dialogInterface == this.mDialogs[7]) {
                if (i == -1) {
                    Log.e("SipCallOptionHandler", "IMS Vt not supported, place cs voice call");
                }
            } else {
                if (i == -1) {
                    Intent intent = new Intent(this, (Class<?>) SipSettings.class);
                    intent.addFlags(268435456);
                    startActivity(intent);
                }
                finish();
                return;
            }
        }
        setResultAndFinish();
    }

    @Override // android.content.DialogInterface.OnCancelListener
    public void onCancel(DialogInterface dialog) {
        finish();
    }

    @Override // android.widget.CompoundButton.OnCheckedChangeListener
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        this.mMakePrimary = isChecked;
        if (isChecked) {
            this.mUnsetPriamryHint.setVisibility(0);
        } else {
            this.mUnsetPriamryHint.setVisibility(4);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void createSipPhoneIfNeeded(SipProfile p) {
        CallManager cm = PhoneGlobals.getInstance().mCM;
        if (PhoneUtils.getSipPhoneFromUri(cm, p.getUriString()) == null) {
            try {
                SipManager.newInstance(this).open(p);
                SipPhone sipPhoneMakeSipPhone = PhoneFactory.makeSipPhone(p.getUriString());
                if (sipPhoneMakeSipPhone != null) {
                    cm.registerPhone(sipPhoneMakeSipPhone);
                } else {
                    Log.e("SipCallOptionHandler", "cannot make sipphone profile" + p);
                }
            } catch (SipException e) {
                Log.e("SipCallOptionHandler", "cannot open sip profile" + p, e);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean useImsPhone() {
        boolean useIms = false;
        if (!this.mUseSipPhone && this.mImsCallType != 10 && PhoneGlobals.isIMSRegisterd()) {
            CallManager cm = PhoneGlobals.getInstance().mCM;
            if ((cm.getPhoneInCall().getPhoneType() != 2 || cm.getPhoneInCall().getState() == PhoneConstants.State.IDLE) && cm.getDefaultPhone().getServiceState().getState() != 3) {
                useIms = true;
            }
        }
        Log.d("SipCallOptionHandler", "useImsPhone returns " + useIms);
        return useIms;
    }

    private void setResultAndFinish() {
        runOnUiThread(new Runnable() { // from class: com.android.phone.SipCallOptionHandler.2
            @Override // java.lang.Runnable
            public void run() {
                if (SipCallOptionHandler.this.mOutgoingSipProfile != null) {
                    if (SipCallOptionHandler.this.isNetworkConnected()) {
                        if (SipCallOptionHandler.DBG) {
                            Log.v("SipCallOptionHandler", "primary SIP URI is " + SipCallOptionHandler.this.mOutgoingSipProfile.getUriString());
                        }
                        SipCallOptionHandler.this.createSipPhoneIfNeeded(SipCallOptionHandler.this.mOutgoingSipProfile);
                        SipCallOptionHandler.this.mIntent.putExtra("android.phone.extra.SIP_PHONE_URI", SipCallOptionHandler.this.mOutgoingSipProfile.getUriString());
                        if (SipCallOptionHandler.this.mMakePrimary) {
                            SipCallOptionHandler.this.mSipSharedPreferences.setPrimaryAccount(SipCallOptionHandler.this.mOutgoingSipProfile.getUriString());
                        }
                    } else {
                        SipCallOptionHandler.this.showDialog(3);
                        return;
                    }
                }
                if (!SipCallOptionHandler.this.mUseSipPhone || SipCallOptionHandler.this.mOutgoingSipProfile != null) {
                    SipCallOptionHandler.this.mIntent.getBooleanExtra("org.codeaurora.extra.DIAL_CONFERENCE_URI", false);
                    if (!SipCallOptionHandler.this.useImsPhone()) {
                        int sub = SipCallOptionHandler.this.mIntent.getIntExtra("subscription", 0);
                        if (PhoneUtils.isTDDDataOnly(SipCallOptionHandler.this, sub)) {
                            SipCallOptionHandler.this.notifyTDDDataOnly(sub);
                            SipCallOptionHandler.this.finish();
                            return;
                        }
                    } else {
                        Phone phone = PhoneUtils.getImsPhone(PhoneGlobals.getInstance().mCM);
                        if (phone != null && phone.getServiceState().getState() == 0) {
                            if (!PhoneUtils.isImsVtCallNotAllowed(SipCallOptionHandler.this.mImsCallType)) {
                                PhoneUtils.convertCallToIms(SipCallOptionHandler.this.mIntent, SipCallOptionHandler.this.mImsCallType);
                            } else {
                                SipCallOptionHandler.this.showDialog(6);
                                return;
                            }
                        } else {
                            if (SystemProperties.getBoolean("persist.radio.domain.ps", true)) {
                                SipCallOptionHandler.this.showDialog(5);
                                return;
                            }
                            Log.d("SipCallOptionHandler", "IMS phone is unavailable , place CS call");
                        }
                    }
                    PhoneGlobals.getInstance().callController.placeCall(SipCallOptionHandler.this.mIntent);
                    SipCallOptionHandler.this.startDelayedFinish();
                    return;
                }
                SipCallOptionHandler.this.showDialog(2);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void notifyTDDDataOnly(int sub) {
        try {
            Intent intent = new Intent("com.qualcomm.qti.phonefeature.DISABLE_TDD_DATA_ONLY");
            intent.putExtra("subscription", sub);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.d("SipCallOptionHandler", "notifyTDDDataOnly catch e = " + e);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isNetworkConnected() {
        NetworkInfo ni;
        ConnectivityManager cm = (ConnectivityManager) getSystemService("connectivity");
        if (cm == null || (ni = cm.getActiveNetworkInfo()) == null || !ni.isConnected()) {
            return false;
        }
        return ni.getType() == 1 || !SipManager.isSipWifiOnly(this);
    }

    private void startGetPrimarySipPhoneThread() {
        new Thread(new Runnable() { // from class: com.android.phone.SipCallOptionHandler.3
            @Override // java.lang.Runnable
            public void run() {
                SipCallOptionHandler.this.getPrimarySipPhone();
            }
        }).start();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void getPrimarySipPhone() {
        String primarySipUri = this.mSipSharedPreferences.getPrimaryAccount();
        this.mOutgoingSipProfile = getPrimaryFromExistingProfiles(primarySipUri);
        if (this.mOutgoingSipProfile == null && this.mProfileList != null && this.mProfileList.size() > 0) {
            runOnUiThread(new Runnable() { // from class: com.android.phone.SipCallOptionHandler.4
                @Override // java.lang.Runnable
                public void run() {
                    SipCallOptionHandler.this.showDialog(1);
                }
            });
        } else {
            setResultAndFinish();
        }
    }

    private SipProfile getPrimaryFromExistingProfiles(String primarySipUri) {
        this.mProfileList = this.mSipProfileDb.retrieveSipProfileList();
        if (this.mProfileList == null) {
            return null;
        }
        for (SipProfile p : this.mProfileList) {
            if (p.getUriString().equals(primarySipUri)) {
                return p;
            }
        }
        return null;
    }

    public int getSubscriptionOnLTE() {
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            int NetWorkType = MSimTelephonyManager.getDefault().getNetworkType(0);
            if (NetWorkType == 13) {
                return 0;
            }
            int NetWorkType2 = MSimTelephonyManager.getDefault().getNetworkType(1);
            if (NetWorkType2 == 13) {
                return 1;
            }
        } else {
            int NetWorkType3 = TelephonyManager.getDefault().getNetworkType();
            if (NetWorkType3 == 13) {
                return 0;
            }
        }
        return this.NoSimOnLTE;
    }
}
