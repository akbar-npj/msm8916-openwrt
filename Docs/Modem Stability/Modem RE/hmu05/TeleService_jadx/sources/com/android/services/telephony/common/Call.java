package com.android.services.telephony.common;

import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.PhoneNumberUtils;
import com.android.internal.telephony.PhoneConstants;
import com.google.android.collect.Sets;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.primitives.Ints;
import java.util.Collection;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/* JADX INFO: loaded from: classes.dex */
public final class Call implements Parcelable {
    private CallDetails mCallDetails;
    private int mCallId;
    private CallDetails mCallModifyDetails;
    private int mCapabilities;
    private SortedSet<Integer> mChildCallIds;
    private long mConnectTime;
    private DisconnectCause mDisconnectCause;
    private String mGatewayNumber;
    private String mGatewayPackage;
    private CallIdentification mIdentification;
    private SsNotification mSsNotification;
    private int mState;
    private int mSubscription;
    private static final Map<Integer, String> STATE_MAP = ImmutableMap.builder().put(2, "ACTIVE").put(4, "CALL_WAITING").put(5, "DIALING").put(6, "REDIALING").put(1, "IDLE").put(3, "INCOMING").put(7, "ONHOLD").put(0, "INVALID").put(8, "DISCONNECTING").put(9, "DISCONNECTED").put(10, "CONFERENCED").build();
    public static int PRESENTATION_ALLOWED = PhoneConstants.PRESENTATION_ALLOWED;
    public static int PRESENTATION_RESTRICTED = PhoneConstants.PRESENTATION_RESTRICTED;
    public static int PRESENTATION_UNKNOWN = PhoneConstants.PRESENTATION_UNKNOWN;
    public static int PRESENTATION_PAYPHONE = PhoneConstants.PRESENTATION_PAYPHONE;
    public static final Parcelable.Creator<Call> CREATOR = new Parcelable.Creator<Call>() { // from class: com.android.services.telephony.common.Call.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public Call createFromParcel(Parcel in) {
            return new Call(in);
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public Call[] newArray(int size) {
            return new Call[size];
        }
    };

    public enum DisconnectCause {
        NOT_DISCONNECTED,
        INCOMING_MISSED,
        NORMAL,
        LOCAL,
        BUSY,
        CONGESTION,
        MMI,
        INVALID_NUMBER,
        NUMBER_UNREACHABLE,
        SERVER_UNREACHABLE,
        INVALID_CREDENTIALS,
        OUT_OF_NETWORK,
        SERVER_ERROR,
        TIMED_OUT,
        LOST_SIGNAL,
        LIMIT_EXCEEDED,
        INCOMING_REJECTED,
        POWER_OFF,
        OUT_OF_SERVICE,
        ICC_ERROR,
        CALL_BARRED,
        FDN_BLOCKED,
        CS_RESTRICTED,
        CS_RESTRICTED_NORMAL,
        CS_RESTRICTED_EMERGENCY,
        UNOBTAINABLE_NUMBER,
        CDMA_LOCKED_UNTIL_POWER_CYCLE,
        CDMA_DROP,
        CDMA_INTERCEPT,
        CDMA_REORDER,
        CDMA_SO_REJECT,
        CDMA_RETRY_ORDER,
        CDMA_ACCESS_FAILURE,
        CDMA_PREEMPTED,
        CDMA_NOT_EMERGENCY,
        CDMA_ACCESS_BLOCKED,
        DIAL_MODIFIED_TO_USSD,
        DIAL_MODIFIED_TO_SS,
        DIAL_MODIFIED_TO_DIAL,
        ERROR_UNSPECIFIED,
        UNKNOWN,
        SRVCC_CALL_DROP,
        CALL_FAIL_MISC
    }

    public static class State {
        public static boolean isConnected(int state) {
            switch (state) {
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                case 10:
                    return true;
                case 8:
                case 9:
                default:
                    return false;
            }
        }

        public static boolean isDialing(int state) {
            return state == 5 || state == 6;
        }
    }

    public static class SsNotification {
        public int code;
        public int index;
        public int notificationType;
        public String number;
        public int type;

        public String toString() {
            return super.toString() + " mobile" + (this.notificationType == 0 ? " originated " : " terminated ") + " code: " + this.code + " index: " + this.index + " \"" + PhoneNumberUtils.stringFromStringAndTOA(this.number, this.type) + "\" ";
        }
    }

    public Call(int callId) {
        this.mState = 0;
        this.mDisconnectCause = DisconnectCause.UNKNOWN;
        this.mConnectTime = 0L;
        this.mChildCallIds = Sets.newSortedSet();
        this.mSubscription = -1;
        this.mCallId = callId;
        this.mIdentification = new CallIdentification(this.mCallId);
        this.mCallDetails = new CallDetails();
        this.mCallModifyDetails = new CallDetails();
    }

    public Call(Call call) {
        this.mState = 0;
        this.mDisconnectCause = DisconnectCause.UNKNOWN;
        this.mConnectTime = 0L;
        this.mChildCallIds = Sets.newSortedSet();
        this.mSubscription = -1;
        this.mCallId = call.mCallId;
        this.mIdentification = new CallIdentification(call.mIdentification);
        this.mState = call.mState;
        this.mDisconnectCause = call.mDisconnectCause;
        this.mCapabilities = call.mCapabilities;
        this.mConnectTime = call.mConnectTime;
        this.mChildCallIds = new TreeSet((SortedSet) call.mChildCallIds);
        this.mGatewayNumber = call.mGatewayNumber;
        this.mGatewayPackage = call.mGatewayPackage;
        this.mCallDetails = new CallDetails();
        this.mCallModifyDetails = new CallDetails();
        copyDetails(call.mCallDetails, this.mCallDetails);
        copyDetails(call.mCallModifyDetails, this.mCallModifyDetails);
        this.mSubscription = call.mSubscription;
    }

    private void copyDetails(CallDetails src, CallDetails dest) {
        dest.setCallType(src.getCallType());
        dest.setCallDomain(src.getCallDomain());
        dest.setExtras(src.getExtras());
        dest.setErrorInfo(src.getErrorInfo());
        dest.setConfUriList(src.getConfParticipantList());
        dest.setMpty(src.isMpty());
    }

    public int getCallId() {
        return this.mCallId;
    }

    public String getNumber() {
        return this.mIdentification.getNumber();
    }

    public void setNumber(String number) {
        this.mIdentification.setNumber(number);
    }

    public int getState() {
        return this.mState;
    }

    public void setState(int state) {
        this.mState = state;
    }

    public CallDetails getCallDetails() {
        return this.mCallDetails;
    }

    public CallDetails getCallModifyDetails() {
        return this.mCallModifyDetails;
    }

    public int getNumberPresentation() {
        return this.mIdentification.getNumberPresentation();
    }

    public void setNumberPresentation(int presentation) {
        this.mIdentification.setNumberPresentation(presentation);
    }

    public int getCnapNamePresentation() {
        return this.mIdentification.getCnapNamePresentation();
    }

    public void setCnapNamePresentation(int presentation) {
        this.mIdentification.setCnapNamePresentation(presentation);
    }

    public String getCnapName() {
        return this.mIdentification.getCnapName();
    }

    public void setCnapName(String cnapName) {
        this.mIdentification.setCnapName(cnapName);
    }

    public DisconnectCause getDisconnectCause() {
        return (this.mState == 9 || this.mState == 1) ? this.mDisconnectCause : DisconnectCause.NOT_DISCONNECTED;
    }

    public void setDisconnectCause(DisconnectCause cause) {
        this.mDisconnectCause = cause;
    }

    public int getCapabilities() {
        return this.mCapabilities;
    }

    public void setCapabilities(int capabilities) {
        this.mCapabilities = capabilities & 1023;
    }

    public void setConnectTime(long connectTime) {
        this.mConnectTime = connectTime;
    }

    public long getConnectTime() {
        return this.mConnectTime;
    }

    public void removeAllChildren() {
        this.mChildCallIds.clear();
    }

    public void addChildId(int id) {
        this.mChildCallIds.add(Integer.valueOf(id));
    }

    public ImmutableSortedSet<Integer> getChildCallIds() {
        return ImmutableSortedSet.copyOf((Collection) this.mChildCallIds);
    }

    public String getGatewayNumber() {
        return this.mGatewayNumber;
    }

    public void setGatewayNumber(String number) {
        this.mGatewayNumber = number;
    }

    public String getGatewayPackage() {
        return this.mGatewayPackage;
    }

    public void setGatewayPackage(String packageName) {
        this.mGatewayPackage = packageName;
    }

    public void setSuppServNotification(SsNotification notification) {
        this.mSsNotification = notification;
    }

    public int getSubscription() {
        return this.mSubscription;
    }

    public void setSubscription(int subscription) {
        this.mSubscription = subscription;
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.mCallId);
        dest.writeInt(this.mState);
        dest.writeString(getDisconnectCause().toString());
        dest.writeInt(getCapabilities());
        dest.writeLong(getConnectTime());
        dest.writeIntArray(Ints.toArray(this.mChildCallIds));
        dest.writeString(getGatewayNumber());
        dest.writeString(getGatewayPackage());
        dest.writeParcelable(this.mIdentification, 0);
        int hasSuppServNotification = this.mSsNotification != null ? 1 : 0;
        dest.writeInt(hasSuppServNotification);
        if (hasSuppServNotification == 1) {
            dest.writeInt(this.mSsNotification.notificationType);
            dest.writeInt(this.mSsNotification.code);
            dest.writeInt(this.mSsNotification.index);
            dest.writeInt(this.mSsNotification.type);
            dest.writeString(this.mSsNotification.number);
        }
        dest.writeParcelable(this.mCallDetails, 1);
        dest.writeParcelable(this.mCallModifyDetails, 2);
        dest.writeInt(this.mSubscription);
    }

    private Call(Parcel parcel) {
        this.mState = 0;
        this.mDisconnectCause = DisconnectCause.UNKNOWN;
        this.mConnectTime = 0L;
        this.mChildCallIds = Sets.newSortedSet();
        this.mSubscription = -1;
        this.mCallId = parcel.readInt();
        this.mState = parcel.readInt();
        this.mDisconnectCause = DisconnectCause.valueOf(parcel.readString());
        this.mCapabilities = parcel.readInt();
        this.mConnectTime = parcel.readLong();
        this.mChildCallIds.addAll(Ints.asList(parcel.createIntArray()));
        this.mGatewayNumber = parcel.readString();
        this.mGatewayPackage = parcel.readString();
        this.mIdentification = (CallIdentification) parcel.readParcelable(CallIdentification.class.getClassLoader());
        if (parcel.readInt() == 1) {
            this.mSsNotification = new SsNotification();
            this.mSsNotification.notificationType = parcel.readInt();
            this.mSsNotification.code = parcel.readInt();
            this.mSsNotification.index = parcel.readInt();
            this.mSsNotification.type = parcel.readInt();
            this.mSsNotification.number = parcel.readString();
        }
        this.mCallDetails = (CallDetails) parcel.readParcelable(CallDetails.class.getClassLoader());
        this.mCallModifyDetails = (CallDetails) parcel.readParcelable(CallDetails.class.getClassLoader());
        this.mSubscription = parcel.readInt();
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    public String toString() {
        return Objects.toStringHelper(this).add("mCallId", this.mCallId).add("mState", STATE_MAP.get(Integer.valueOf(this.mState))).add("mDisconnectCause", this.mDisconnectCause).add("mCapabilities", this.mCapabilities).add("mConnectTime", this.mConnectTime).add("mChildCallIds", this.mChildCallIds).add("mGatewayNumber", MoreStrings.toSafeString(this.mGatewayNumber)).add("mGatewayPackage", this.mGatewayPackage).add("mIdentification", this.mIdentification).add("mSsNotification", this.mSsNotification).add("mCallDetails", this.mCallDetails).add("mCallModifyDetails", this.mCallModifyDetails).add("mSubscription", this.mSubscription).toString();
    }
}
