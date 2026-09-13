package com.android.phone;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.DialerKeyListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;

/* JADX INFO: loaded from: classes.dex */
public class IccDepersonalizationPanel extends IccPanel {
    private final int ENTRY;
    private final int ERROR;
    private final int IN_PROGRESS;
    private final int SUCCESS;
    private Button mDismissButton;
    View.OnClickListener mDismissListener;
    private LinearLayout mEntryPanel;
    private Handler mHandler;
    private int mPersoSubtype;
    private final int[][] mPersoSubtypeLabels;
    private TextView mPersoSubtypeText;
    private Phone mPhone;
    private EditText mPinEntry;
    private TextWatcher mPinEntryWatcher;
    private LinearLayout mStatusPanel;
    private TextView mStatusText;
    private Button mUnlockButton;
    View.OnClickListener mUnlockListener;

    public IccDepersonalizationPanel(Context context) {
        super(context);
        this.ENTRY = 0;
        this.IN_PROGRESS = 1;
        this.ERROR = 2;
        this.SUCCESS = 3;
        this.mPersoSubtypeLabels = new int[][]{new int[]{0, 0, 0, 0}, new int[]{0, 0, 0, 0}, new int[]{0, 0, 0, 0}, new int[]{R.string.label_ndp, R.string.requesting_unlock, R.string.unlock_failed, R.string.unlock_success}, new int[]{R.string.label_nsdp, R.string.requesting_nw_subset_unlock, R.string.nw_subset_unlock_failed, R.string.nw_subset_unlock_success}, new int[]{R.string.label_cdp, R.string.requesting_corporate_unlock, R.string.corporate_unlock_failed, R.string.corporate_unlock_success}, new int[]{R.string.label_spdp, R.string.requesting_sp_unlock, R.string.sp_unlock_failed, R.string.sp_unlock_success}, new int[]{R.string.label_sdp, R.string.requesting_sim_unlock, R.string.sim_unlock_failed, R.string.sim_unlock_success}, new int[]{R.string.label_puk, R.string.requesting_puk_unlock, R.string.puk_unlock_failed, R.string.puk_unlock_success}, new int[]{R.string.label_puk, R.string.requesting_puk_unlock, R.string.puk_unlock_failed, R.string.puk_unlock_success}, new int[]{R.string.label_puk, R.string.requesting_puk_unlock, R.string.puk_unlock_failed, R.string.puk_unlock_success}, new int[]{R.string.label_puk, R.string.requesting_puk_unlock, R.string.puk_unlock_failed, R.string.puk_unlock_success}, new int[]{R.string.label_puk, R.string.requesting_puk_unlock, R.string.puk_unlock_failed, R.string.puk_unlock_success}, new int[]{R.string.label_rn1dp, R.string.requesting_rnw1_unlock, R.string.rnw1_unlock_failed, R.string.rnw1_unlock_success}, new int[]{R.string.label_rn2dp, R.string.requesting_rnw2_unlock, R.string.rnw2_unlock_failed, R.string.rnw2_unlock_success}, new int[]{R.string.label_rhrpd, R.string.requesting_rhrpd_unlock, R.string.rhrpd_unlock_failed, R.string.rhrpd_unlock_success}, new int[]{R.string.label_rcdp, R.string.requesting_rc_unlock, R.string.rc_unlock_failed, R.string.rc_unlock_success}, new int[]{R.string.label_rspdp, R.string.requesting_rsp_unlock, R.string.rsp_unlock_failed, R.string.rsp_unlock_success}, new int[]{R.string.label_rdp, R.string.requesting_ruim_unlock, R.string.ruim_unlock_failed, R.string.ruim_unlock_success}, new int[]{R.string.label_puk, R.string.requesting_puk_unlock, R.string.puk_unlock_failed, R.string.puk_unlock_success}, new int[]{R.string.label_puk, R.string.requesting_puk_unlock, R.string.puk_unlock_failed, R.string.puk_unlock_success}, new int[]{R.string.label_puk, R.string.requesting_puk_unlock, R.string.puk_unlock_failed, R.string.puk_unlock_success}, new int[]{R.string.label_puk, R.string.requesting_puk_unlock, R.string.puk_unlock_failed, R.string.puk_unlock_success}, new int[]{R.string.label_puk, R.string.requesting_puk_unlock, R.string.puk_unlock_failed, R.string.puk_unlock_success}, new int[]{R.string.label_puk, R.string.requesting_puk_unlock, R.string.puk_unlock_failed, R.string.puk_unlock_success}};
        this.mPinEntryWatcher = new TextWatcher() { // from class: com.android.phone.IccDepersonalizationPanel.1
            @Override // android.text.TextWatcher
            public void beforeTextChanged(CharSequence buffer, int start, int olen, int nlen) {
            }

            @Override // android.text.TextWatcher
            public void onTextChanged(CharSequence buffer, int start, int olen, int nlen) {
            }

            @Override // android.text.TextWatcher
            public void afterTextChanged(Editable buffer) {
                if (SpecialCharSequenceMgr.handleChars(IccDepersonalizationPanel.this.getContext(), buffer.toString())) {
                    IccDepersonalizationPanel.this.mPinEntry.getText().clear();
                }
            }
        };
        this.mHandler = new Handler() { // from class: com.android.phone.IccDepersonalizationPanel.2
            @Override // android.os.Handler
            public void handleMessage(Message msg) {
                if (msg.what == 100) {
                    AsyncResult res = (AsyncResult) msg.obj;
                    if (res.exception != null) {
                        IccDepersonalizationPanel.this.log("De-Personalization request failure.");
                        IccDepersonalizationPanel.this.displayStatus(2);
                        postDelayed(new Runnable() { // from class: com.android.phone.IccDepersonalizationPanel.2.1
                            @Override // java.lang.Runnable
                            public void run() {
                                IccDepersonalizationPanel.this.hideAlert();
                                IccDepersonalizationPanel.this.mPinEntry.getText().clear();
                                IccDepersonalizationPanel.this.mPinEntry.requestFocus();
                            }
                        }, 3000L);
                    } else {
                        IccDepersonalizationPanel.this.log("De-Personalization success.");
                        IccDepersonalizationPanel.this.displayStatus(3);
                        postDelayed(new Runnable() { // from class: com.android.phone.IccDepersonalizationPanel.2.2
                            @Override // java.lang.Runnable
                            public void run() {
                                IccDepersonalizationPanel.this.dismiss();
                            }
                        }, 3000L);
                    }
                }
            }
        };
        this.mUnlockListener = new View.OnClickListener() { // from class: com.android.phone.IccDepersonalizationPanel.3
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                String pin = IccDepersonalizationPanel.this.mPinEntry.getText().toString();
                if (!TextUtils.isEmpty(pin)) {
                    IccDepersonalizationPanel.this.log("Requesting De-Personalization for subtype " + IccDepersonalizationPanel.this.mPersoSubtype);
                    IccDepersonalizationPanel.this.mPhone.getIccCard().supplyDepersonalization(pin, Integer.toString(IccDepersonalizationPanel.this.mPersoSubtype), Message.obtain(IccDepersonalizationPanel.this.mHandler, 100));
                    IccDepersonalizationPanel.this.displayStatus(1);
                }
            }
        };
        this.mDismissListener = new View.OnClickListener() { // from class: com.android.phone.IccDepersonalizationPanel.4
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                IccDepersonalizationPanel.this.log("mDismissListener: skipping depersonalization...");
                IccDepersonalizationPanel.this.dismiss();
            }
        };
        this.mPersoSubtype = IccCardApplicationStatus.PersoSubState.PERSOSUBSTATE_SIM_NETWORK.ordinal();
    }

    public IccDepersonalizationPanel(Context context, int subtype) {
        super(context);
        this.ENTRY = 0;
        this.IN_PROGRESS = 1;
        this.ERROR = 2;
        this.SUCCESS = 3;
        this.mPersoSubtypeLabels = new int[][]{new int[]{0, 0, 0, 0}, new int[]{0, 0, 0, 0}, new int[]{0, 0, 0, 0}, new int[]{R.string.label_ndp, R.string.requesting_unlock, R.string.unlock_failed, R.string.unlock_success}, new int[]{R.string.label_nsdp, R.string.requesting_nw_subset_unlock, R.string.nw_subset_unlock_failed, R.string.nw_subset_unlock_success}, new int[]{R.string.label_cdp, R.string.requesting_corporate_unlock, R.string.corporate_unlock_failed, R.string.corporate_unlock_success}, new int[]{R.string.label_spdp, R.string.requesting_sp_unlock, R.string.sp_unlock_failed, R.string.sp_unlock_success}, new int[]{R.string.label_sdp, R.string.requesting_sim_unlock, R.string.sim_unlock_failed, R.string.sim_unlock_success}, new int[]{R.string.label_puk, R.string.requesting_puk_unlock, R.string.puk_unlock_failed, R.string.puk_unlock_success}, new int[]{R.string.label_puk, R.string.requesting_puk_unlock, R.string.puk_unlock_failed, R.string.puk_unlock_success}, new int[]{R.string.label_puk, R.string.requesting_puk_unlock, R.string.puk_unlock_failed, R.string.puk_unlock_success}, new int[]{R.string.label_puk, R.string.requesting_puk_unlock, R.string.puk_unlock_failed, R.string.puk_unlock_success}, new int[]{R.string.label_puk, R.string.requesting_puk_unlock, R.string.puk_unlock_failed, R.string.puk_unlock_success}, new int[]{R.string.label_rn1dp, R.string.requesting_rnw1_unlock, R.string.rnw1_unlock_failed, R.string.rnw1_unlock_success}, new int[]{R.string.label_rn2dp, R.string.requesting_rnw2_unlock, R.string.rnw2_unlock_failed, R.string.rnw2_unlock_success}, new int[]{R.string.label_rhrpd, R.string.requesting_rhrpd_unlock, R.string.rhrpd_unlock_failed, R.string.rhrpd_unlock_success}, new int[]{R.string.label_rcdp, R.string.requesting_rc_unlock, R.string.rc_unlock_failed, R.string.rc_unlock_success}, new int[]{R.string.label_rspdp, R.string.requesting_rsp_unlock, R.string.rsp_unlock_failed, R.string.rsp_unlock_success}, new int[]{R.string.label_rdp, R.string.requesting_ruim_unlock, R.string.ruim_unlock_failed, R.string.ruim_unlock_success}, new int[]{R.string.label_puk, R.string.requesting_puk_unlock, R.string.puk_unlock_failed, R.string.puk_unlock_success}, new int[]{R.string.label_puk, R.string.requesting_puk_unlock, R.string.puk_unlock_failed, R.string.puk_unlock_success}, new int[]{R.string.label_puk, R.string.requesting_puk_unlock, R.string.puk_unlock_failed, R.string.puk_unlock_success}, new int[]{R.string.label_puk, R.string.requesting_puk_unlock, R.string.puk_unlock_failed, R.string.puk_unlock_success}, new int[]{R.string.label_puk, R.string.requesting_puk_unlock, R.string.puk_unlock_failed, R.string.puk_unlock_success}, new int[]{R.string.label_puk, R.string.requesting_puk_unlock, R.string.puk_unlock_failed, R.string.puk_unlock_success}};
        this.mPinEntryWatcher = new TextWatcher() { // from class: com.android.phone.IccDepersonalizationPanel.1
            @Override // android.text.TextWatcher
            public void beforeTextChanged(CharSequence buffer, int start, int olen, int nlen) {
            }

            @Override // android.text.TextWatcher
            public void onTextChanged(CharSequence buffer, int start, int olen, int nlen) {
            }

            @Override // android.text.TextWatcher
            public void afterTextChanged(Editable buffer) {
                if (SpecialCharSequenceMgr.handleChars(IccDepersonalizationPanel.this.getContext(), buffer.toString())) {
                    IccDepersonalizationPanel.this.mPinEntry.getText().clear();
                }
            }
        };
        this.mHandler = new Handler() { // from class: com.android.phone.IccDepersonalizationPanel.2
            @Override // android.os.Handler
            public void handleMessage(Message msg) {
                if (msg.what == 100) {
                    AsyncResult res = (AsyncResult) msg.obj;
                    if (res.exception != null) {
                        IccDepersonalizationPanel.this.log("De-Personalization request failure.");
                        IccDepersonalizationPanel.this.displayStatus(2);
                        postDelayed(new Runnable() { // from class: com.android.phone.IccDepersonalizationPanel.2.1
                            @Override // java.lang.Runnable
                            public void run() {
                                IccDepersonalizationPanel.this.hideAlert();
                                IccDepersonalizationPanel.this.mPinEntry.getText().clear();
                                IccDepersonalizationPanel.this.mPinEntry.requestFocus();
                            }
                        }, 3000L);
                    } else {
                        IccDepersonalizationPanel.this.log("De-Personalization success.");
                        IccDepersonalizationPanel.this.displayStatus(3);
                        postDelayed(new Runnable() { // from class: com.android.phone.IccDepersonalizationPanel.2.2
                            @Override // java.lang.Runnable
                            public void run() {
                                IccDepersonalizationPanel.this.dismiss();
                            }
                        }, 3000L);
                    }
                }
            }
        };
        this.mUnlockListener = new View.OnClickListener() { // from class: com.android.phone.IccDepersonalizationPanel.3
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                String pin = IccDepersonalizationPanel.this.mPinEntry.getText().toString();
                if (!TextUtils.isEmpty(pin)) {
                    IccDepersonalizationPanel.this.log("Requesting De-Personalization for subtype " + IccDepersonalizationPanel.this.mPersoSubtype);
                    IccDepersonalizationPanel.this.mPhone.getIccCard().supplyDepersonalization(pin, Integer.toString(IccDepersonalizationPanel.this.mPersoSubtype), Message.obtain(IccDepersonalizationPanel.this.mHandler, 100));
                    IccDepersonalizationPanel.this.displayStatus(1);
                }
            }
        };
        this.mDismissListener = new View.OnClickListener() { // from class: com.android.phone.IccDepersonalizationPanel.4
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                IccDepersonalizationPanel.this.log("mDismissListener: skipping depersonalization...");
                IccDepersonalizationPanel.this.dismiss();
            }
        };
        this.mPersoSubtype = subtype;
    }

    @Override // com.android.phone.IccPanel, android.app.Dialog
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.sim_ndp);
        this.mPinEntry = (EditText) findViewById(R.id.pin_entry);
        this.mPinEntry.setKeyListener(DialerKeyListener.getInstance());
        this.mPinEntry.setOnClickListener(this.mUnlockListener);
        Spannable text = this.mPinEntry.getText();
        Spannable span = text;
        span.setSpan(this.mPinEntryWatcher, 0, text.length(), 18);
        this.mEntryPanel = (LinearLayout) findViewById(R.id.entry_panel);
        this.mPersoSubtypeText = (TextView) findViewById(R.id.perso_subtype_text);
        displayStatus(0);
        this.mUnlockButton = (Button) findViewById(R.id.ndp_unlock);
        this.mUnlockButton.setOnClickListener(this.mUnlockListener);
        this.mDismissButton = (Button) findViewById(R.id.ndp_dismiss);
        if (getContext().getResources().getBoolean(R.bool.icc_perso_unlock_allow_dismiss)) {
            log("Enabling 'Dismiss' button...");
            this.mDismissButton.setVisibility(0);
            this.mDismissButton.setOnClickListener(this.mDismissListener);
        } else {
            log("Removing 'Dismiss' button...");
            this.mDismissButton.setVisibility(8);
        }
        this.mStatusPanel = (LinearLayout) findViewById(R.id.status_panel);
        this.mStatusText = (TextView) findViewById(R.id.status_text);
        this.mPhone = PhoneGlobals.getPhone();
    }

    @Override // com.android.phone.IccPanel, android.app.Dialog
    protected void onStart() {
        super.onStart();
    }

    @Override // com.android.phone.IccPanel, android.app.Dialog, android.view.KeyEvent.Callback
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == 4) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void displayStatus(int type) {
        int label = this.mPersoSubtypeLabels[this.mPersoSubtype][type];
        if (label == 0) {
            log("Unsupported Perso Subtype :" + this.mPersoSubtype);
            return;
        }
        if (type == 0) {
            String displayText = getContext().getString(label);
            this.mPersoSubtypeText.setText(displayText);
        } else {
            this.mStatusText.setText(label);
            this.mEntryPanel.setVisibility(8);
            this.mStatusPanel.setVisibility(0);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void hideAlert() {
        this.mEntryPanel.setVisibility(0);
        this.mStatusPanel.setVisibility(8);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void log(String msg) {
        Log.d("PhoneApp", "[IccDepersonalizationPanel] " + msg);
    }
}
