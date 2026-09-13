package com.android.internal.telephony.cat;

/* JADX INFO: loaded from: classes.dex */
public interface AppInterface {
    public static final String ALPHA_STRING = "alpha_string";
    public static final String CARD_STATUS = "card_status";
    public static final String CAT_ACTIVATE_NOTIFY_ACTION = "org.codeaurora.intent.action.stk.activate_notify";
    public static final String CAT_ALPHA_NOTIFY_ACTION = "org.codeaurora.intent.action.stk.alpha_notify";
    public static final String CAT_CMD_ACTION = "android.intent.action.stk.command";
    public static final String CAT_HCI_CONNECTIVITY_ACTION = "org.codeaurora.intent.action.stk.hci_connectivity";
    public static final String CAT_ICC_STATUS_CHANGE = "org.codeaurora.intent.action.stk.icc_status_change";
    public static final String CAT_IDLE_SCREEN_ACTION = "org.codeaurora.intent.action.stk.idle_screen";
    public static final String CAT_SESSION_END_ACTION = "android.intent.action.stk.session_end";
    public static final String CHECK_SCREEN_IDLE_ACTION = "org.codeaurora.intent.action.stk.check_screen_idle";
    public static final String REFRESH_RESULT = "refresh_result";

    void onCmdResponse(CatResponseMessage catResponseMessage);

    public enum CommandType {
        DISPLAY_TEXT(33),
        GET_INKEY(34),
        GET_INPUT(35),
        LAUNCH_BROWSER(21),
        PLAY_TONE(32),
        REFRESH(1),
        SELECT_ITEM(36),
        SEND_SS(17),
        SEND_USSD(18),
        SEND_SMS(19),
        SEND_DTMF(20),
        SET_UP_EVENT_LIST(5),
        SET_UP_IDLE_MODE_TEXT(40),
        SET_UP_MENU(37),
        SET_UP_CALL(16),
        PROVIDE_LOCAL_INFORMATION(38),
        OPEN_CHANNEL(64),
        CLOSE_CHANNEL(65),
        RECEIVE_DATA(66),
        SEND_DATA(67),
        GET_CHANNEL_STATUS(68),
        ACTIVATE(112);

        private int mValue;

        CommandType(int value) {
            this.mValue = value;
        }

        public int value() {
            return this.mValue;
        }

        public static CommandType fromInt(int value) {
            CommandType[] arr$ = values();
            for (CommandType e : arr$) {
                if (e.mValue == value) {
                    return e;
                }
            }
            return null;
        }
    }
}
