package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

/* JADX INFO: loaded from: classes.dex */
public class SmsCbMessage implements Parcelable {
    public static final Parcelable.Creator<SmsCbMessage> CREATOR = new Parcelable.Creator<SmsCbMessage>() { // from class: android.telephony.SmsCbMessage.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public SmsCbMessage createFromParcel(Parcel in) {
            return new SmsCbMessage(in);
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public SmsCbMessage[] newArray(int size) {
            return new SmsCbMessage[size];
        }
    };
    public static final int GEOGRAPHICAL_SCOPE_CELL_WIDE = 3;
    public static final int GEOGRAPHICAL_SCOPE_CELL_WIDE_IMMEDIATE = 0;
    public static final int GEOGRAPHICAL_SCOPE_LA_WIDE = 2;
    public static final int GEOGRAPHICAL_SCOPE_PLMN_WIDE = 1;
    protected static final String LOG_TAG = "SMSCB";
    public static final int MESSAGE_FORMAT_3GPP = 1;
    public static final int MESSAGE_FORMAT_3GPP2 = 2;
    public static final int MESSAGE_PRIORITY_EMERGENCY = 3;
    public static final int MESSAGE_PRIORITY_INTERACTIVE = 1;
    public static final int MESSAGE_PRIORITY_NORMAL = 0;
    public static final int MESSAGE_PRIORITY_URGENT = 2;
    private final String mBody;
    private final SmsCbCmasInfo mCmasWarningInfo;
    private final SmsCbEtwsInfo mEtwsWarningInfo;
    private final int mGeographicalScope;
    private final String mLanguage;
    private final SmsCbLocation mLocation;
    private final int mMessageFormat;
    private final int mPriority;
    private final int mSerialNumber;
    private final int mServiceCategory;

    public SmsCbMessage(int messageFormat, int geographicalScope, int serialNumber, SmsCbLocation location, int serviceCategory, String language, String body, int priority, SmsCbEtwsInfo etwsWarningInfo, SmsCbCmasInfo cmasWarningInfo) {
        this.mMessageFormat = messageFormat;
        this.mGeographicalScope = geographicalScope;
        this.mSerialNumber = serialNumber;
        this.mLocation = location;
        this.mServiceCategory = serviceCategory;
        this.mLanguage = language;
        this.mBody = body;
        this.mPriority = priority;
        this.mEtwsWarningInfo = etwsWarningInfo;
        this.mCmasWarningInfo = cmasWarningInfo;
    }

    public SmsCbMessage(Parcel in) {
        this.mMessageFormat = in.readInt();
        this.mGeographicalScope = in.readInt();
        this.mSerialNumber = in.readInt();
        this.mLocation = new SmsCbLocation(in);
        this.mServiceCategory = in.readInt();
        this.mLanguage = in.readString();
        this.mBody = in.readString();
        this.mPriority = in.readInt();
        int type = in.readInt();
        switch (type) {
            case 67:
                this.mEtwsWarningInfo = null;
                this.mCmasWarningInfo = new SmsCbCmasInfo(in);
                break;
            case 68:
            default:
                this.mEtwsWarningInfo = null;
                this.mCmasWarningInfo = null;
                break;
            case 69:
                this.mEtwsWarningInfo = new SmsCbEtwsInfo(in);
                this.mCmasWarningInfo = null;
                break;
        }
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.mMessageFormat);
        dest.writeInt(this.mGeographicalScope);
        dest.writeInt(this.mSerialNumber);
        this.mLocation.writeToParcel(dest, flags);
        dest.writeInt(this.mServiceCategory);
        dest.writeString(this.mLanguage);
        dest.writeString(this.mBody);
        dest.writeInt(this.mPriority);
        if (this.mEtwsWarningInfo != null) {
            dest.writeInt(69);
            this.mEtwsWarningInfo.writeToParcel(dest, flags);
        } else if (this.mCmasWarningInfo != null) {
            dest.writeInt(67);
            this.mCmasWarningInfo.writeToParcel(dest, flags);
        } else {
            dest.writeInt(48);
        }
    }

    public int getGeographicalScope() {
        return this.mGeographicalScope;
    }

    public int getSerialNumber() {
        return this.mSerialNumber;
    }

    public SmsCbLocation getLocation() {
        return this.mLocation;
    }

    public int getServiceCategory() {
        return this.mServiceCategory;
    }

    public String getLanguageCode() {
        return this.mLanguage;
    }

    public String getMessageBody() {
        return this.mBody;
    }

    public int getMessageFormat() {
        return this.mMessageFormat;
    }

    public int getMessagePriority() {
        return this.mPriority;
    }

    public SmsCbEtwsInfo getEtwsWarningInfo() {
        return this.mEtwsWarningInfo;
    }

    public SmsCbCmasInfo getCmasWarningInfo() {
        return this.mCmasWarningInfo;
    }

    public boolean isEmergencyMessage() {
        return this.mPriority == 3;
    }

    public boolean isEtwsMessage() {
        return this.mEtwsWarningInfo != null;
    }

    public boolean isCmasMessage() {
        return this.mCmasWarningInfo != null;
    }

    public String toString() {
        return "SmsCbMessage{geographicalScope=" + this.mGeographicalScope + ", serialNumber=" + this.mSerialNumber + ", location=" + this.mLocation + ", serviceCategory=" + this.mServiceCategory + ", language=" + this.mLanguage + ", body=" + this.mBody + ", priority=" + this.mPriority + (this.mEtwsWarningInfo != null ? ", " + this.mEtwsWarningInfo.toString() : "") + (this.mCmasWarningInfo != null ? ", " + this.mCmasWarningInfo.toString() : "") + '}';
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }
}
