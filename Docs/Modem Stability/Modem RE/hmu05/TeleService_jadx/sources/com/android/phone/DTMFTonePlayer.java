package com.android.phone;

import android.content.Context;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.services.telephony.common.Call;
import com.google.common.collect.ImmutableMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/* JADX INFO: loaded from: classes.dex */
public class DTMFTonePlayer implements CallModeler.Listener {
    private static final String LOG_TAG = DTMFTonePlayer.class.getSimpleName();
    private static final Map<Character, Integer> mToneMap = ImmutableMap.builder().put('1', 1).put('2', 2).put('3', 3).put('4', 4).put('5', 5).put('6', 6).put('7', 7).put('8', 8).put('9', 9).put('0', 0).put('#', 11).put('*', 10).build();
    private final CallManager mCallManager;
    private final CallModeler mCallModeler;
    private boolean mLocalToneEnabled;
    boolean mShortTone;
    private ToneGenerator mToneGenerator;
    private final Object mToneGeneratorLock = new Object();
    private boolean mDTMFBurstCnfPending = false;
    private Queue<Character> mDTMFQueue = new LinkedList();
    private final Handler mHandler = new Handler() { // from class: com.android.phone.DTMFTonePlayer.1
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 100:
                    DTMFTonePlayer.logD("dtmf confirmation received from FW.");
                    DTMFTonePlayer.this.handleBurstDtmfConfirmation();
                    break;
                case 101:
                    DTMFTonePlayer.logD("dtmf stop received");
                    DTMFTonePlayer.this.stopDtmfTone();
                    break;
            }
        }
    };

    public DTMFTonePlayer(CallManager callManager, CallModeler callModeler) {
        this.mCallManager = callManager;
        this.mCallModeler = callModeler;
        this.mCallModeler.addListener(this);
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onDisconnect(Call call) {
        logD("Call disconnected");
        checkCallState();
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onSuppServiceFailed(int service) {
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onIncoming(Call call) {
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onUpdate(List<Call> calls) {
        logD("Call updated");
        checkCallState();
    }

    /* JADX INFO: renamed from: com.android.phone.DTMFTonePlayer$2, reason: invalid class name */
    static /* synthetic */ class AnonymousClass2 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$Connection$PostDialState = new int[Connection.PostDialState.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$Connection$PostDialState[Connection.PostDialState.STARTED.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Connection$PostDialState[Connection.PostDialState.PAUSE.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Connection$PostDialState[Connection.PostDialState.WAIT.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Connection$PostDialState[Connection.PostDialState.WILD.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Connection$PostDialState[Connection.PostDialState.COMPLETE.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
        }
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onPostDialAction(Connection.PostDialState state, int callId, String remainingChars, char currentChar) {
        switch (AnonymousClass2.$SwitchMap$com$android$internal$telephony$Connection$PostDialState[state.ordinal()]) {
            case 1:
                stopLocalToneIfNeeded();
                if (mToneMap.containsKey(Character.valueOf(currentChar))) {
                    startLocalToneIfNeeded(currentChar);
                }
                break;
            case 2:
            case 3:
            case 4:
            case 5:
                stopLocalToneIfNeeded();
                break;
        }
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onModifyCall(Call call) {
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onActiveSubChanged(int activeSub) {
    }

    public void startDialerSession() {
        logD("startDialerSession()... this = " + this);
        if (PhoneGlobals.getInstance().getResources().getBoolean(R.bool.allow_local_dtmf_tones)) {
            this.mLocalToneEnabled = Settings.System.getInt(PhoneGlobals.getInstance().getContentResolver(), "dtmf_tone", 1) == 1;
        } else {
            this.mLocalToneEnabled = false;
        }
        logD("- startDialerSession: mLocalToneEnabled = " + this.mLocalToneEnabled);
        if (this.mLocalToneEnabled) {
            synchronized (this.mToneGeneratorLock) {
                if (this.mToneGenerator == null) {
                    try {
                        this.mToneGenerator = new ToneGenerator(8, 80);
                    } catch (RuntimeException e) {
                        Log.e(LOG_TAG, "Exception caught while creating local tone generator", e);
                        this.mToneGenerator = null;
                    }
                }
            }
        }
    }

    public void stopDialerSession() {
        synchronized (this.mToneGeneratorLock) {
            if (this.mToneGenerator != null) {
                this.mToneGenerator.release();
                this.mToneGenerator = null;
            }
        }
        this.mHandler.removeMessages(100);
        synchronized (this.mDTMFQueue) {
            this.mDTMFBurstCnfPending = false;
            this.mDTMFQueue.clear();
        }
    }

    public void playDtmfTone(char c, boolean timedShortTone) {
        if (mToneMap.containsKey(Character.valueOf(c)) && okToDialDtmfTones()) {
            PhoneGlobals.getInstance().pokeUserActivity();
            Phone phone = this.mCallManager.getFgPhone();
            if (this.mHandler.hasMessages(101)) {
                this.mHandler.removeMessages(101);
                stopDtmfTone();
            }
            this.mShortTone = useShortDtmfTones(phone, phone.getContext());
            logD("startDtmfTone()...");
            if (this.mShortTone) {
                sendShortDtmfToNetwork(c);
            } else {
                logD("send long dtmf for " + c);
                this.mCallManager.startDtmf(c);
                if (timedShortTone) {
                    this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(101), 120L);
                }
            }
            startLocalToneIfNeeded(c);
        }
    }

    private void sendShortDtmfToNetwork(char dtmfDigit) {
        synchronized (this.mDTMFQueue) {
            if (this.mDTMFBurstCnfPending) {
                this.mDTMFQueue.add(new Character(dtmfDigit));
            } else {
                String dtmfStr = Character.toString(dtmfDigit);
                this.mCallManager.sendBurstDtmf(dtmfStr, 0, 0, this.mHandler.obtainMessage(100));
                this.mDTMFBurstCnfPending = true;
            }
        }
    }

    void handleBurstDtmfConfirmation() {
        Character dtmfChar = null;
        synchronized (this.mDTMFQueue) {
            this.mDTMFBurstCnfPending = false;
            if (!this.mDTMFQueue.isEmpty()) {
                dtmfChar = this.mDTMFQueue.remove();
                Log.i(LOG_TAG, "The dtmf character removed from queue" + dtmfChar);
            }
        }
        if (dtmfChar != null) {
            sendShortDtmfToNetwork(dtmfChar.charValue());
        }
    }

    public void stopDtmfTone() {
        if (!this.mShortTone) {
            this.mCallManager.stopDtmf();
            stopLocalToneIfNeeded();
        }
    }

    private void startLocalToneIfNeeded(char c) {
        if (this.mLocalToneEnabled) {
            synchronized (this.mToneGeneratorLock) {
                if (this.mToneGenerator == null) {
                    logD("startDtmfTone: mToneGenerator == null, tone: " + c);
                } else {
                    logD("starting local tone " + c);
                    int toneDuration = -1;
                    if (this.mShortTone) {
                        toneDuration = 120;
                    }
                    this.mToneGenerator.startTone(mToneMap.get(Character.valueOf(c)).intValue(), toneDuration);
                }
            }
        }
    }

    public void stopLocalToneIfNeeded() {
        if (!this.mShortTone) {
            logD("trying to stop local tone...");
            if (this.mLocalToneEnabled) {
                synchronized (this.mToneGeneratorLock) {
                    if (this.mToneGenerator == null) {
                        logD("stopLocalTone: mToneGenerator == null");
                    } else {
                        logD("stopping local tone.");
                        this.mToneGenerator.stopTone();
                    }
                }
            }
        }
    }

    private boolean okToDialDtmfTones() {
        boolean hasActiveCall = false;
        boolean hasIncomingCall = false;
        List<Call> calls = this.mCallModeler.getFullList();
        int len = calls.size();
        for (int i = 0; i < len; i++) {
            hasActiveCall |= calls.get(i).getState() == 2 || calls.get(i).getState() == 5;
            hasIncomingCall |= calls.get(i).getState() == 3;
        }
        return hasActiveCall && !hasIncomingCall;
    }

    private static boolean useShortDtmfTones(Phone phone, Context context) {
        int phoneType = phone.getPhoneType();
        if (phoneType == 1 || phoneType == 4) {
            return false;
        }
        if (phoneType == 2) {
            int toneType = Settings.System.getInt(context.getContentResolver(), "dtmf_tone_type", 0);
            return toneType == 0;
        }
        if (phoneType == 3) {
            return false;
        }
        throw new IllegalStateException("Unexpected phone type: " + phoneType);
    }

    private void checkCallState() {
        logD("checkCallState");
        if (this.mCallModeler.hasOutstandingActiveOrDialingCall()) {
            startDialerSession();
        } else {
            stopDialerSession();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void logD(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
