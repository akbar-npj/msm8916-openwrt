package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.format.Time;
import com.android.internal.telephony.cdma.CallFailCause;
import com.android.internal.telephony.uicc.IccUtils;
import java.util.Arrays;

/* JADX INFO: loaded from: classes.dex */
public class SmsCbEtwsInfo implements Parcelable {
    public static final Parcelable.Creator<SmsCbEtwsInfo> CREATOR = new Parcelable.Creator<SmsCbEtwsInfo>() { // from class: android.telephony.SmsCbEtwsInfo.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public SmsCbEtwsInfo createFromParcel(Parcel in) {
            return new SmsCbEtwsInfo(in);
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public SmsCbEtwsInfo[] newArray(int size) {
            return new SmsCbEtwsInfo[size];
        }
    };
    public static final int ETWS_WARNING_TYPE_EARTHQUAKE = 0;
    public static final int ETWS_WARNING_TYPE_EARTHQUAKE_AND_TSUNAMI = 2;
    public static final int ETWS_WARNING_TYPE_OTHER_EMERGENCY = 4;
    public static final int ETWS_WARNING_TYPE_TEST_MESSAGE = 3;
    public static final int ETWS_WARNING_TYPE_TSUNAMI = 1;
    public static final int ETWS_WARNING_TYPE_UNKNOWN = -1;
    private final boolean mActivatePopup;
    private final boolean mEmergencyUserAlert;
    private final byte[] mWarningSecurityInformation;
    private final int mWarningType;

    public SmsCbEtwsInfo(int warningType, boolean emergencyUserAlert, boolean activatePopup, byte[] warningSecurityInformation) {
        this.mWarningType = warningType;
        this.mEmergencyUserAlert = emergencyUserAlert;
        this.mActivatePopup = activatePopup;
        this.mWarningSecurityInformation = warningSecurityInformation;
    }

    SmsCbEtwsInfo(Parcel in) {
        this.mWarningType = in.readInt();
        this.mEmergencyUserAlert = in.readInt() != 0;
        this.mActivatePopup = in.readInt() != 0;
        this.mWarningSecurityInformation = in.createByteArray();
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.mWarningType);
        dest.writeInt(this.mEmergencyUserAlert ? 1 : 0);
        dest.writeInt(this.mActivatePopup ? 1 : 0);
        dest.writeByteArray(this.mWarningSecurityInformation);
    }

    public int getWarningType() {
        return this.mWarningType;
    }

    public boolean isEmergencyUserAlert() {
        return this.mEmergencyUserAlert;
    }

    public boolean isPopupAlert() {
        return this.mActivatePopup;
    }

    public long getPrimaryNotificationTimestamp() {
        if (this.mWarningSecurityInformation == null || this.mWarningSecurityInformation.length < 7) {
            return 0L;
        }
        int year = IccUtils.gsmBcdByteToInt(this.mWarningSecurityInformation[0]);
        int month = IccUtils.gsmBcdByteToInt(this.mWarningSecurityInformation[1]);
        int day = IccUtils.gsmBcdByteToInt(this.mWarningSecurityInformation[2]);
        int hour = IccUtils.gsmBcdByteToInt(this.mWarningSecurityInformation[3]);
        int minute = IccUtils.gsmBcdByteToInt(this.mWarningSecurityInformation[4]);
        int second = IccUtils.gsmBcdByteToInt(this.mWarningSecurityInformation[5]);
        byte tzByte = this.mWarningSecurityInformation[6];
        int timezoneOffset = IccUtils.gsmBcdByteToInt((byte) (tzByte & (-9)));
        if ((tzByte & 8) != 0) {
            timezoneOffset = -timezoneOffset;
        }
        Time time = new Time("UTC");
        time.year = year + 2000;
        time.month = month - 1;
        time.monthDay = day;
        time.hour = hour;
        time.minute = minute;
        time.second = second;
        return time.toMillis(true) - ((long) (((timezoneOffset * 15) * 60) * CallFailCause.CDMA_LOCKED_UNTIL_POWER_CYCLE));
    }

    public byte[] getPrimaryNotificationSignature() {
        if (this.mWarningSecurityInformation == null || this.mWarningSecurityInformation.length < 50) {
            return null;
        }
        return Arrays.copyOfRange(this.mWarningSecurityInformation, 7, 50);
    }

    public String toString() {
        return "SmsCbEtwsInfo{warningType=" + this.mWarningType + ", emergencyUserAlert=" + this.mEmergencyUserAlert + ", activatePopup=" + this.mActivatePopup + '}';
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }
}
