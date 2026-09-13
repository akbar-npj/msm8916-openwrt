package com.android.phone;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.telephony.MSimTelephonyManager;
import android.util.Log;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.PhoneConstants;
import com.android.services.telephony.common.Call;
import com.google.android.collect.Lists;
import com.google.common.base.Preconditions;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class BluetoothManager implements CallModeler.Listener {
    private static final boolean DBG;
    private static final String LOG_TAG = BluetoothManager.class.getSimpleName();
    private long mBluetoothConnectionRequestTime;
    private BluetoothHeadset mBluetoothHeadset;
    private final CallManager mCallManager;
    private final CallModeler mCallModeler;
    private final Context mContext;
    private int mBluetoothHeadsetState = 0;
    private int mBluetoothHeadsetAudioState = 10;
    private boolean mShowBluetoothIndication = false;
    private boolean mBluetoothConnectionPending = false;
    private final BroadcastReceiver mReceiver = new BluetoothBroadcastReceiver();
    private final List<BluetoothIndicatorListener> mListeners = Lists.newArrayList();
    private BluetoothProfile.ServiceListener mBluetoothProfileServiceListener = new BluetoothProfile.ServiceListener() { // from class: com.android.phone.BluetoothManager.1
        @Override // android.bluetooth.BluetoothProfile.ServiceListener
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            BluetoothManager.this.mBluetoothHeadset = (BluetoothHeadset) proxy;
            BluetoothManager.this.log("- Got BluetoothHeadset: " + BluetoothManager.this.mBluetoothHeadset);
        }

        @Override // android.bluetooth.BluetoothProfile.ServiceListener
        public void onServiceDisconnected(int profile) {
            BluetoothManager.this.mBluetoothHeadset = null;
        }
    };
    private final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    interface BluetoothIndicatorListener {
        void onBluetoothIndicationChange(boolean z, BluetoothManager bluetoothManager);
    }

    static {
        DBG = SystemProperties.getInt("ro.debuggable", 0) == 1;
    }

    public BluetoothManager(Context context, CallManager callManager, CallModeler callModeler) {
        this.mContext = context;
        this.mCallManager = callManager;
        this.mCallModeler = callModeler;
        init(this.mContext);
    }

    boolean isBluetoothHeadsetAudioOn() {
        return this.mBluetoothHeadsetAudioState != 10;
    }

    boolean isBluetoothAvailable() {
        log("isBluetoothAvailable()...");
        boolean isConnected = false;
        if (this.mBluetoothHeadset != null) {
            List<BluetoothDevice> deviceList = this.mBluetoothHeadset.getConnectedDevices();
            if (deviceList.size() > 0) {
                isConnected = true;
                for (int i = 0; i < deviceList.size(); i++) {
                    BluetoothDevice device = deviceList.get(i);
                    log("state = " + this.mBluetoothHeadset.getConnectionState(device) + "for headset: " + device);
                }
            }
        }
        log("  ==> " + isConnected);
        return isConnected;
    }

    boolean isBluetoothAudioConnected() {
        if (this.mBluetoothHeadset == null) {
            log("isBluetoothAudioConnected: ==> FALSE (null mBluetoothHeadset)");
            return false;
        }
        List<BluetoothDevice> deviceList = this.mBluetoothHeadset.getConnectedDevices();
        if (deviceList.isEmpty()) {
            return false;
        }
        for (int i = 0; i < deviceList.size(); i++) {
            BluetoothDevice device = deviceList.get(i);
            boolean isAudioOn = this.mBluetoothHeadset.isAudioConnected(device);
            log("isBluetoothAudioConnected: ==> isAudioOn = " + isAudioOn + "for headset: " + device);
            if (isAudioOn) {
                return true;
            }
        }
        return false;
    }

    boolean showBluetoothIndication() {
        return this.mShowBluetoothIndication;
    }

    void updateBluetoothIndication() {
        this.mShowBluetoothIndication = shouldShowBluetoothIndication(this.mBluetoothHeadsetState, this.mBluetoothHeadsetAudioState, this.mCallManager);
        notifyListeners(this.mShowBluetoothIndication);
    }

    public void addBluetoothIndicatorListener(BluetoothIndicatorListener listener) {
        if (!this.mListeners.contains(listener)) {
            this.mListeners.add(listener);
        }
    }

    private void notifyListeners(boolean showBluetoothOn) {
        for (int i = 0; i < this.mListeners.size(); i++) {
            this.mListeners.get(i).onBluetoothIndicationChange(showBluetoothOn, this);
        }
    }

    private void init(Context context) {
        Preconditions.checkNotNull(context);
        if (this.mBluetoothAdapter != null) {
            this.mBluetoothAdapter.getProfileProxy(context, this.mBluetoothProfileServiceListener, 1);
        }
        IntentFilter intentFilter = new IntentFilter("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED");
        intentFilter.addAction("android.bluetooth.headset.profile.action.AUDIO_STATE_CHANGED");
        context.registerReceiver(this.mReceiver, intentFilter);
        this.mCallModeler.addListener(this);
    }

    private static boolean shouldShowBluetoothIndication(int bluetoothState, int bluetoothAudioState, CallManager cm) {
        PhoneConstants.State state = cm.getState();
        if (MSimTelephonyManager.getDefault().getMultiSimConfiguration() == MSimTelephonyManager.MultiSimVariants.DSDA && state == PhoneConstants.State.IDLE) {
            int sub = cm.getActiveSubscription() ^ 1;
            state = cm.getState(sub);
        }
        switch (AnonymousClass2.$SwitchMap$com$android$internal$telephony$PhoneConstants$State[state.ordinal()]) {
            case 1:
                return bluetoothState == 2 && bluetoothAudioState == 12;
            case 2:
                return bluetoothState == 2;
            default:
                return false;
        }
    }

    /* JADX INFO: renamed from: com.android.phone.BluetoothManager$2, reason: invalid class name */
    static /* synthetic */ class AnonymousClass2 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$PhoneConstants$State = new int[PhoneConstants.State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$PhoneConstants$State[PhoneConstants.State.OFFHOOK.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$PhoneConstants$State[PhoneConstants.State.RINGING.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
        }
    }

    void connectBluetoothAudio() {
        log("connectBluetoothAudio()...");
        if (this.mBluetoothHeadset != null) {
            this.mBluetoothHeadset.connectAudio();
        }
        this.mBluetoothConnectionPending = true;
        this.mBluetoothConnectionRequestTime = SystemClock.elapsedRealtime();
    }

    void disconnectBluetoothAudio() {
        log("disconnectBluetoothAudio()...");
        if (this.mBluetoothHeadset != null) {
            this.mBluetoothHeadset.disconnectAudio();
        }
        this.mBluetoothConnectionPending = false;
    }

    private class BluetoothBroadcastReceiver extends BroadcastReceiver {
        private BluetoothBroadcastReceiver() {
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED")) {
                BluetoothManager.this.mBluetoothHeadsetState = intent.getIntExtra("android.bluetooth.profile.extra.STATE", 0);
                Log.d(BluetoothManager.LOG_TAG, "mReceiver: HEADSET_STATE_CHANGED_ACTION");
                Log.d(BluetoothManager.LOG_TAG, "==> new state: " + BluetoothManager.this.mBluetoothHeadsetState);
                BluetoothManager.this.updateBluetoothIndication();
                return;
            }
            if (action.equals("android.bluetooth.headset.profile.action.AUDIO_STATE_CHANGED")) {
                BluetoothManager.this.mBluetoothHeadsetAudioState = intent.getIntExtra("android.bluetooth.profile.extra.STATE", 10);
                Log.d(BluetoothManager.LOG_TAG, "mReceiver: HEADSET_AUDIO_STATE_CHANGED_ACTION");
                Log.d(BluetoothManager.LOG_TAG, "==> new state: " + BluetoothManager.this.mBluetoothHeadsetAudioState);
                BluetoothManager.this.updateBluetoothIndication();
            }
        }
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onDisconnect(Call call) {
        updateBluetoothIndication();
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onIncoming(Call call) {
        updateBluetoothIndication();
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onUpdate(List<Call> calls) {
        updateBluetoothIndication();
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onPostDialAction(Connection.PostDialState state, int callId, String chars, char c) {
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onModifyCall(Call call) {
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onSuppServiceFailed(int service) {
    }

    @Override // com.android.phone.CallModeler.Listener
    public void onActiveSubChanged(int activeSub) {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
