package com.android.internal.telephony.dataconnection;

import android.R;
import android.app.PendingIntent;
import android.content.Context;
import android.net.NetworkConfig;
import android.telephony.Rlog;
import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.Phone;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/* JADX INFO: loaded from: classes.dex */
public class ApnContext {
    protected static final boolean DBG = false;
    public final String LOG_TAG;
    private final Context mContext;
    AtomicBoolean mDataEnabled;
    private DataProfile mDataProfile;
    private final String mDataProfileType;
    DcAsyncChannel mDcAc;
    AtomicBoolean mDependencyMet;
    private final int mPriority;
    String mReason;
    PendingIntent mReconnectAlarmIntent;
    private AtomicInteger mWaitingApnsPermanentFailureCountDown;
    public final int priority;
    private ArrayList<DataProfile> mWaitingDataProfiles = null;
    private DctConstants.State mState = DctConstants.State.IDLE;

    public ApnContext(Context context, String dataProfileType, String logTag, NetworkConfig config) {
        this.mContext = context;
        this.mDataProfileType = dataProfileType;
        this.mPriority = DcTrackerBase.mApnPriorities.get(this.mDataProfileType).intValue();
        setReason(Phone.REASON_DATA_ENABLED);
        this.mDataEnabled = new AtomicBoolean(false);
        this.mDependencyMet = new AtomicBoolean(config.dependencyMet);
        this.mWaitingApnsPermanentFailureCountDown = new AtomicInteger(0);
        this.priority = config.priority;
        this.LOG_TAG = logTag;
    }

    public String getDataProfileType() {
        return this.mDataProfileType;
    }

    public synchronized DcAsyncChannel getDcAc() {
        return this.mDcAc;
    }

    public synchronized void setDataConnectionAc(DcAsyncChannel dcac) {
        this.mDcAc = dcac;
    }

    public synchronized PendingIntent getReconnectIntent() {
        return this.mReconnectAlarmIntent;
    }

    public synchronized void setReconnectIntent(PendingIntent intent) {
        this.mReconnectAlarmIntent = intent;
    }

    public synchronized DataProfile getDataProfile() {
        log("getDataProfile: mDataProfile=" + this.mDataProfile);
        return this.mDataProfile;
    }

    public synchronized void setDataProfile(DataProfile dataProfile) {
        log("setDataProfile: mDataProfile=" + dataProfile);
        this.mDataProfile = dataProfile;
    }

    public synchronized void setWaitingDataProfiles(ArrayList<DataProfile> waitingDataProfiles) {
        this.mWaitingDataProfiles = waitingDataProfiles;
        this.mWaitingApnsPermanentFailureCountDown.set(this.mWaitingDataProfiles.size());
    }

    public int getWaitingApnsPermFailCount() {
        return this.mWaitingApnsPermanentFailureCountDown.get();
    }

    public void decWaitingApnsPermFailCount() {
        this.mWaitingApnsPermanentFailureCountDown.decrementAndGet();
    }

    public synchronized DataProfile getNextWaitingApn() {
        DataProfile apn;
        ArrayList<DataProfile> list = this.mWaitingDataProfiles;
        apn = null;
        if (list != null && !list.isEmpty()) {
            apn = list.get(0);
        }
        return apn;
    }

    public synchronized void removeWaitingApn(DataProfile apn) {
        if (this.mWaitingDataProfiles != null) {
            this.mWaitingDataProfiles.remove(apn);
        }
    }

    public synchronized ArrayList<DataProfile> getWaitingApns() {
        return this.mWaitingDataProfiles;
    }

    public synchronized int getPriority() {
        return this.mPriority;
    }

    public synchronized boolean isHigherPriority(ApnContext context) {
        return this.mPriority > context.getPriority();
    }

    public synchronized boolean isLowerPriority(ApnContext context) {
        return this.mPriority < context.getPriority();
    }

    public synchronized boolean isEqualPriority(ApnContext context) {
        return this.mPriority == context.getPriority();
    }

    public synchronized void setState(DctConstants.State s) {
        this.mState = s;
        if (this.mState == DctConstants.State.FAILED && this.mWaitingDataProfiles != null) {
            this.mWaitingDataProfiles.clear();
        }
    }

    public synchronized DctConstants.State getState() {
        return this.mState;
    }

    public boolean isDisconnected() {
        DctConstants.State currentState = getState();
        return currentState == DctConstants.State.IDLE || currentState == DctConstants.State.FAILED;
    }

    public synchronized void setReason(String reason) {
        this.mReason = reason;
    }

    public synchronized String getReason() {
        return this.mReason;
    }

    public boolean isReady() {
        return this.mDataEnabled.get() && this.mDependencyMet.get();
    }

    public boolean isConnectable() {
        return isReady() && (this.mState == DctConstants.State.IDLE || this.mState == DctConstants.State.SCANNING || this.mState == DctConstants.State.RETRYING || this.mState == DctConstants.State.FAILED);
    }

    public boolean isConnectedOrConnecting() {
        return isReady() && (this.mState == DctConstants.State.CONNECTED || this.mState == DctConstants.State.CONNECTING || this.mState == DctConstants.State.SCANNING || this.mState == DctConstants.State.RETRYING);
    }

    public void setEnabled(boolean enabled) {
        this.mDataEnabled.set(enabled);
    }

    public boolean isEnabled() {
        return this.mDataEnabled.get();
    }

    public void setDependencyMet(boolean met) {
        this.mDependencyMet.set(met);
    }

    public boolean getDependencyMet() {
        return this.mDependencyMet.get();
    }

    public boolean isProvisioningApn() {
        String provisioningApn = this.mContext.getResources().getString(R.string.config_defaultCallRedirection);
        if (this.mDataProfile == null || this.mDataProfile.apn == null) {
            return false;
        }
        return this.mDataProfile.apn.equals(provisioningApn);
    }

    public synchronized String toString() {
        return "{mApnType=" + this.mDataProfileType + " mState=" + getState() + " mWaitingDataProfiles={" + this.mWaitingDataProfiles + "} mWaitingApnsPermanentFailureCountDown=" + this.mWaitingApnsPermanentFailureCountDown + " mDataProfile={" + this.mDataProfile + "} mReason=" + this.mReason + " mDataEnabled=" + this.mDataEnabled + " mDependencyMet=" + this.mDependencyMet + "}";
    }

    protected void log(String s) {
        Rlog.d(this.LOG_TAG, "[ApnContext:" + this.mDataProfileType + "] " + s);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("ApnContext: " + toString());
    }
}
