package com.android.internal.telephony.cat;

import android.os.Parcel;
import android.os.Parcelable;

/* JADX INFO: loaded from: classes.dex */
public class CatCmdMessage implements Parcelable {
    public static final Parcelable.Creator<CatCmdMessage> CREATOR = new Parcelable.Creator<CatCmdMessage>() { // from class: com.android.internal.telephony.cat.CatCmdMessage.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public CatCmdMessage createFromParcel(Parcel in) {
            return new CatCmdMessage(in);
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public CatCmdMessage[] newArray(int size) {
            return new CatCmdMessage[size];
        }
    };
    static final int REFRESH_NAA_INIT = 3;
    static final int REFRESH_NAA_INIT_AND_FILE_CHANGE = 2;
    static final int REFRESH_NAA_INIT_AND_FULL_FILE_CHANGE = 0;
    static final int REFRESH_UICC_RESET = 4;
    private BrowserSettings mBrowserSettings;
    private CallSettings mCallSettings;
    CommandDetails mCmdDet;
    private Input mInput;
    private boolean mLoadIconFailed;
    private Menu mMenu;
    private SetupEventListSettings mSetupEventListSettings;
    private TextMessage mTextMsg;
    private ToneSettings mToneSettings;

    public class BrowserSettings {
        public LaunchBrowserMode mode;
        public String url;

        public BrowserSettings() {
        }
    }

    public class CallSettings {
        public TextMessage callMsg;
        public TextMessage confirmMsg;

        public CallSettings() {
        }
    }

    public class SetupEventListSettings {
        public int[] eventList;

        public SetupEventListSettings() {
        }
    }

    public final class SetupEventListConstants {
        public static final int BROWSER_TERMINATION_EVENT = 8;
        public static final int BROWSING_STATUS_EVENT = 15;
        public static final int HCI_CONNECTIVITY_EVENT = 19;
        public static final int IDLE_SCREEN_AVAILABLE_EVENT = 5;
        public static final int LANGUAGE_SELECTION_EVENT = 7;
        public static final int USER_ACTIVITY_EVENT = 4;

        public SetupEventListConstants() {
        }
    }

    public final class BrowserTerminationCauses {
        public static final int ERROR_TERMINATION = 1;
        public static final int USER_TERMINATION = 0;

        public BrowserTerminationCauses() {
        }
    }

    CatCmdMessage(CommandParams cmdParams) {
        this.mBrowserSettings = null;
        this.mToneSettings = null;
        this.mCallSettings = null;
        this.mSetupEventListSettings = null;
        this.mLoadIconFailed = false;
        this.mCmdDet = cmdParams.mCmdDet;
        this.mLoadIconFailed = cmdParams.mLoadIconFailed;
        switch (getCmdType()) {
            case SET_UP_MENU:
            case SELECT_ITEM:
                this.mMenu = ((SelectItemParams) cmdParams).mMenu;
                break;
            case DISPLAY_TEXT:
            case SET_UP_IDLE_MODE_TEXT:
            case SEND_DTMF:
            case SEND_SMS:
            case REFRESH:
            case SEND_SS:
            case SEND_USSD:
                this.mTextMsg = ((DisplayTextParams) cmdParams).mTextMsg;
                break;
            case GET_INPUT:
            case GET_INKEY:
                this.mInput = ((GetInputParams) cmdParams).mInput;
                break;
            case LAUNCH_BROWSER:
                this.mTextMsg = ((LaunchBrowserParams) cmdParams).mConfirmMsg;
                this.mBrowserSettings = new BrowserSettings();
                this.mBrowserSettings.url = ((LaunchBrowserParams) cmdParams).mUrl;
                this.mBrowserSettings.mode = ((LaunchBrowserParams) cmdParams).mMode;
                break;
            case PLAY_TONE:
                PlayToneParams params = (PlayToneParams) cmdParams;
                this.mToneSettings = params.mSettings;
                this.mTextMsg = params.mTextMsg;
                break;
            case GET_CHANNEL_STATUS:
                this.mTextMsg = ((CallSetupParams) cmdParams).mConfirmMsg;
                break;
            case SET_UP_CALL:
                this.mCallSettings = new CallSettings();
                this.mCallSettings.confirmMsg = ((CallSetupParams) cmdParams).mConfirmMsg;
                this.mCallSettings.callMsg = ((CallSetupParams) cmdParams).mCallMsg;
                break;
            case OPEN_CHANNEL:
            case CLOSE_CHANNEL:
            case RECEIVE_DATA:
            case SEND_DATA:
                BIPClientParams param = (BIPClientParams) cmdParams;
                this.mTextMsg = param.mTextMsg;
                break;
            case SET_UP_EVENT_LIST:
                this.mSetupEventListSettings = new SetupEventListSettings();
                this.mSetupEventListSettings.eventList = ((SetEventListParams) cmdParams).mEventInfo;
                break;
        }
    }

    public CatCmdMessage(Parcel in) {
        this.mBrowserSettings = null;
        this.mToneSettings = null;
        this.mCallSettings = null;
        this.mSetupEventListSettings = null;
        this.mLoadIconFailed = false;
        this.mCmdDet = (CommandDetails) in.readParcelable(null);
        this.mTextMsg = (TextMessage) in.readParcelable(null);
        this.mMenu = (Menu) in.readParcelable(null);
        this.mInput = (Input) in.readParcelable(null);
        this.mLoadIconFailed = in.readByte() == 1;
        switch (getCmdType()) {
            case LAUNCH_BROWSER:
                this.mBrowserSettings = new BrowserSettings();
                this.mBrowserSettings.url = in.readString();
                this.mBrowserSettings.mode = LaunchBrowserMode.values()[in.readInt()];
                break;
            case PLAY_TONE:
                this.mToneSettings = (ToneSettings) in.readParcelable(null);
                break;
            case SET_UP_CALL:
                this.mCallSettings = new CallSettings();
                this.mCallSettings.confirmMsg = (TextMessage) in.readParcelable(null);
                this.mCallSettings.callMsg = (TextMessage) in.readParcelable(null);
                break;
            case SET_UP_EVENT_LIST:
                this.mSetupEventListSettings = new SetupEventListSettings();
                int length = in.readInt();
                this.mSetupEventListSettings.eventList = new int[length];
                for (int i = 0; i < length; i++) {
                    this.mSetupEventListSettings.eventList[i] = in.readInt();
                }
                break;
        }
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(this.mCmdDet, 0);
        dest.writeParcelable(this.mTextMsg, 0);
        dest.writeParcelable(this.mMenu, 0);
        dest.writeParcelable(this.mInput, 0);
        dest.writeByte((byte) (this.mLoadIconFailed ? 1 : 0));
        switch (getCmdType()) {
            case LAUNCH_BROWSER:
                dest.writeString(this.mBrowserSettings.url);
                dest.writeInt(this.mBrowserSettings.mode.ordinal());
                break;
            case PLAY_TONE:
                dest.writeParcelable(this.mToneSettings, 0);
                break;
            case SET_UP_CALL:
                dest.writeParcelable(this.mCallSettings.confirmMsg, 0);
                dest.writeParcelable(this.mCallSettings.callMsg, 0);
                break;
            case SET_UP_EVENT_LIST:
                dest.writeIntArray(this.mSetupEventListSettings.eventList);
                break;
        }
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    public AppInterface.CommandType getCmdType() {
        return AppInterface.CommandType.fromInt(this.mCmdDet.typeOfCommand);
    }

    public Menu getMenu() {
        return this.mMenu;
    }

    public Input geInput() {
        return this.mInput;
    }

    public TextMessage geTextMessage() {
        return this.mTextMsg;
    }

    public BrowserSettings getBrowserSettings() {
        return this.mBrowserSettings;
    }

    public ToneSettings getToneSettings() {
        return this.mToneSettings;
    }

    public CallSettings getCallSettings() {
        return this.mCallSettings;
    }

    public SetupEventListSettings getSetEventList() {
        return this.mSetupEventListSettings;
    }

    public boolean isRefreshResetOrInit() {
        return this.mCmdDet.commandQualifier == 0 || this.mCmdDet.commandQualifier == 2 || this.mCmdDet.commandQualifier == 3 || this.mCmdDet.commandQualifier == 4;
    }

    public boolean hasIconLoadFailed() {
        return this.mLoadIconFailed;
    }
}
