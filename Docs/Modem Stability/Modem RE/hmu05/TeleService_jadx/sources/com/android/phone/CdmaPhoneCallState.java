package com.android.phone;

/* JADX INFO: loaded from: classes.dex */
public class CdmaPhoneCallState {
    private boolean mAddCallMenuStateAfterCW;
    private PhoneCallState mCurrentCallState;
    private PhoneCallState mPreviousCallState;
    private boolean mThreeWayCallOrigStateDialing;

    public enum PhoneCallState {
        IDLE,
        SINGLE_ACTIVE,
        THRWAY_ACTIVE,
        CONF_CALL
    }

    public void CdmaPhoneCallStateInit() {
        this.mCurrentCallState = PhoneCallState.IDLE;
        this.mPreviousCallState = PhoneCallState.IDLE;
        this.mThreeWayCallOrigStateDialing = false;
        this.mAddCallMenuStateAfterCW = true;
    }

    public PhoneCallState getCurrentCallState() {
        return this.mCurrentCallState;
    }

    public void setCurrentCallState(PhoneCallState newState) {
        this.mPreviousCallState = this.mCurrentCallState;
        this.mCurrentCallState = newState;
        this.mThreeWayCallOrigStateDialing = false;
        if (this.mCurrentCallState == PhoneCallState.SINGLE_ACTIVE && this.mPreviousCallState == PhoneCallState.IDLE) {
            this.mAddCallMenuStateAfterCW = true;
        }
    }

    public boolean IsThreeWayCallOrigStateDialing() {
        return this.mThreeWayCallOrigStateDialing;
    }

    public void setThreeWayCallOrigState(boolean newState) {
        this.mThreeWayCallOrigStateDialing = newState;
    }

    public boolean getAddCallMenuStateAfterCallWaiting() {
        return this.mAddCallMenuStateAfterCW;
    }

    public void setAddCallMenuStateAfterCallWaiting(boolean newState) {
        this.mAddCallMenuStateAfterCW = newState;
    }

    public PhoneCallState getPreviousCallState() {
        return this.mPreviousCallState;
    }

    public void resetCdmaPhoneCallState() {
        this.mCurrentCallState = PhoneCallState.IDLE;
        this.mPreviousCallState = PhoneCallState.IDLE;
        this.mThreeWayCallOrigStateDialing = false;
        this.mAddCallMenuStateAfterCW = true;
    }
}
