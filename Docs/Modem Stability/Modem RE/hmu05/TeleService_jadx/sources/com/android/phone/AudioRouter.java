package com.android.phone;

import android.content.Context;
import android.os.SystemProperties;
import android.util.Log;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.PhoneConstants;
import com.android.services.telephony.common.AudioMode;
import com.google.common.collect.Lists;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
class AudioRouter implements BluetoothManager.BluetoothIndicatorListener, WiredHeadsetManager.WiredHeadsetListener {
    private static final boolean DBG;
    private static String LOG_TAG = AudioRouter.class.getSimpleName();
    private final BluetoothManager mBluetoothManager;
    private final CallManager mCallManager;
    private final Context mContext;
    private final WiredHeadsetManager mWiredHeadsetManager;
    private final List<AudioModeListener> mListeners = Lists.newArrayList();
    private int mAudioMode = AudioMode.EARPIECE;
    private int mPreviousMode = AudioMode.EARPIECE;
    private int mSupportedModes = AudioMode.ALL_MODES;

    public interface AudioModeListener {
        void onAudioModeChange(int i, boolean z);

        void onSupportedAudioModeChange(int i);
    }

    static {
        DBG = SystemProperties.getInt("ro.debuggable", 0) == 1;
    }

    public AudioRouter(Context context, BluetoothManager bluetoothManager, WiredHeadsetManager wiredHeadsetManager, CallManager callManager) {
        this.mContext = context;
        this.mBluetoothManager = bluetoothManager;
        this.mWiredHeadsetManager = wiredHeadsetManager;
        this.mCallManager = callManager;
        init();
    }

    public int getAudioMode() {
        return this.mAudioMode;
    }

    public int getSupportedAudioModes() {
        return this.mSupportedModes;
    }

    public boolean getMute() {
        return PhoneUtils.getMute();
    }

    public void addAudioModeListener(AudioModeListener listener) {
        if (!this.mListeners.contains(listener)) {
            this.mListeners.add(listener);
            listener.onAudioModeChange(this.mAudioMode, getMute());
            listener.onSupportedAudioModeChange(this.mSupportedModes);
        }
    }

    public void setAudioMode(int mode) {
        logD("setAudioMode " + AudioMode.toString(mode));
        boolean error = false;
        int mode2 = selectWiredOrEarpiece(mode);
        if ((calculateSupportedModes() | mode2) == 0) {
            Log.wtf(LOG_TAG, "Asking to set to a mode that is unsupported: " + mode2);
            return;
        }
        if (AudioMode.SPEAKER == mode2) {
            turnOnOffBluetooth(false);
            turnOnOffSpeaker(true);
        } else if (AudioMode.BLUETOOTH == mode2) {
            if (this.mBluetoothManager.isBluetoothAvailable()) {
                turnOnOffSpeaker(false);
                if (!turnOnOffBluetooth(true)) {
                    error = true;
                }
            } else {
                Log.e(LOG_TAG, "Asking to turn on bluetooth when no bluetooth available. supportedModes: " + AudioMode.toString(calculateSupportedModes()));
                error = true;
            }
        } else if (AudioMode.EARPIECE == mode2 || AudioMode.WIRED_HEADSET == mode2) {
            turnOnOffBluetooth(false);
            turnOnOffSpeaker(false);
        } else {
            error = true;
        }
        if (error) {
            mode2 = calculateModeFromCurrentState();
            Log.e(LOG_TAG, "There was an error in setting new audio mode. Resetting mode to " + AudioMode.toString(mode2) + ".");
        }
        updateAudioModeTo(mode2);
    }

    public void setSpeaker(boolean on) {
        logD("setSpeaker " + on);
        if (on) {
            setAudioMode(AudioMode.SPEAKER);
        } else {
            setAudioMode(AudioMode.WIRED_OR_EARPIECE);
        }
    }

    public void onMuteChange(boolean muted) {
        logD("onMuteChange: " + muted);
        notifyListeners();
    }

    @Override // com.android.phone.BluetoothManager.BluetoothIndicatorListener
    public void onBluetoothIndicationChange(boolean isConnected, BluetoothManager btManager) {
        logD("onBluetoothIndicationChange " + isConnected);
        updateAudioModeTo(calculateModeFromCurrentState());
    }

    @Override // com.android.phone.WiredHeadsetManager.WiredHeadsetListener
    public void onWiredHeadsetConnection(boolean pluggedIn) {
        logD("onWireHeadsetConnection " + pluggedIn);
        boolean isOffhook = this.mCallManager.getState() == PhoneConstants.State.OFFHOOK;
        int newMode = this.mAudioMode;
        if (!this.mBluetoothManager.isBluetoothHeadsetAudioOn()) {
            if (isOffhook) {
                if (!pluggedIn) {
                    PhoneUtils.restoreSpeakerMode(this.mContext);
                    if (PhoneUtils.isSpeakerOn(this.mContext)) {
                        newMode = AudioMode.SPEAKER;
                    } else {
                        newMode = AudioMode.EARPIECE;
                    }
                } else {
                    PhoneUtils.turnOnSpeaker(this.mContext, false, false);
                    newMode = AudioMode.WIRED_HEADSET;
                }
            } else {
                newMode = pluggedIn ? AudioMode.WIRED_HEADSET : AudioMode.EARPIECE;
            }
        }
        updateAudioModeTo(newMode);
    }

    private int selectWiredOrEarpiece(int mode) {
        if (mode == AudioMode.WIRED_OR_EARPIECE) {
            int mode2 = AudioMode.WIRED_OR_EARPIECE & this.mSupportedModes;
            if (mode2 == 0) {
                Log.wtf(LOG_TAG, "One of wired headset or earpiece should always be valid.");
                return AudioMode.EARPIECE;
            }
            return mode2;
        }
        return mode;
    }

    private boolean turnOnOffBluetooth(boolean onOff) {
        if (this.mBluetoothManager.isBluetoothAvailable()) {
            boolean isAlreadyOn = this.mBluetoothManager.isBluetoothAudioConnected();
            if (onOff && !isAlreadyOn) {
                this.mBluetoothManager.connectBluetoothAudio();
            } else if (!onOff && isAlreadyOn) {
                this.mBluetoothManager.disconnectBluetoothAudio();
            }
        } else if (onOff) {
            Log.e(LOG_TAG, "Asking to turn on bluetooth, but there is no bluetooth availabled.");
            return false;
        }
        return true;
    }

    private void turnOnOffSpeaker(boolean onOff) {
        if (PhoneUtils.isSpeakerOn(this.mContext) != onOff) {
            PhoneUtils.turnOnSpeaker(this.mContext, onOff, true);
        }
    }

    private void init() {
        this.mBluetoothManager.addBluetoothIndicatorListener(this);
        this.mWiredHeadsetManager.addWiredHeadsetListener(this);
    }

    private int calculateModeFromCurrentState() {
        int mode = AudioMode.EARPIECE;
        if (this.mBluetoothManager.showBluetoothIndication()) {
            mode = AudioMode.BLUETOOTH;
        } else if (PhoneUtils.isSpeakerOn(this.mContext)) {
            mode = AudioMode.SPEAKER;
        } else if (this.mWiredHeadsetManager.isHeadsetPlugged()) {
            mode = AudioMode.WIRED_HEADSET;
        }
        logD("calculateModeFromCurrentState " + AudioMode.toString(mode));
        return mode;
    }

    private void updateAudioModeTo(int mode) {
        int oldSupportedModes = this.mSupportedModes;
        this.mSupportedModes = calculateSupportedModes();
        if ((this.mSupportedModes & mode) == 0) {
            Log.e(LOG_TAG, "Setting audio mode to an unsupported mode: " + AudioMode.toString(mode) + ", supported (" + AudioMode.toString(this.mSupportedModes) + ")");
        }
        boolean doNotify = oldSupportedModes != this.mSupportedModes;
        if (this.mAudioMode != mode) {
            Log.i(LOG_TAG, "Audio mode changing to " + AudioMode.toString(mode));
            doNotify = true;
        }
        this.mPreviousMode = this.mAudioMode;
        this.mAudioMode = mode;
        if (doNotify) {
            notifyListeners();
        }
    }

    private int calculateSupportedModes() {
        int supportedModes;
        int supportedModes2 = AudioMode.SPEAKER;
        if (this.mWiredHeadsetManager.isHeadsetPlugged()) {
            supportedModes = supportedModes2 | AudioMode.WIRED_HEADSET;
        } else {
            supportedModes = supportedModes2 | AudioMode.EARPIECE;
        }
        if (this.mBluetoothManager.isBluetoothAvailable()) {
            return supportedModes | AudioMode.BLUETOOTH;
        }
        return supportedModes;
    }

    private void notifyListeners() {
        logD("AudioMode: " + AudioMode.toString(this.mAudioMode));
        logD("Supported AudioMode: " + AudioMode.toString(this.mSupportedModes));
        for (int i = 0; i < this.mListeners.size(); i++) {
            this.mListeners.get(i).onAudioModeChange(this.mAudioMode, getMute());
            this.mListeners.get(i).onSupportedAudioModeChange(this.mSupportedModes);
        }
    }

    private void logD(String msg) {
        if (DBG) {
            Log.d(LOG_TAG, msg);
        }
    }
}
