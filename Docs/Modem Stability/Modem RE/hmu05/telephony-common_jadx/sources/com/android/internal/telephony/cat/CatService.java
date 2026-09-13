package com.android.internal.telephony.cat;

import android.R;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemProperties;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccRefreshResponse;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;

/* JADX INFO: loaded from: classes.dex */
public class CatService extends Handler implements AppInterface {
    private static final boolean DBG = false;
    private static final int DEV_ID_DISPLAY = 2;
    private static final int DEV_ID_KEYPAD = 1;
    private static final int DEV_ID_NETWORK = 131;
    private static final int DEV_ID_TERMINAL = 130;
    private static final int DEV_ID_UICC = 129;
    protected static final int MSG_ID_ALPHA_NOTIFY = 8;
    protected static final int MSG_ID_CALL_SETUP = 4;
    protected static final int MSG_ID_EVENT_NOTIFY = 3;
    protected static final int MSG_ID_ICC_CHANGED = 7;
    protected static final int MSG_ID_ICC_RECORDS_LOADED = 20;
    protected static final int MSG_ID_ICC_REFRESH = 30;
    protected static final int MSG_ID_PROACTIVE_COMMAND = 2;
    static final int MSG_ID_REFRESH = 5;
    static final int MSG_ID_RESPONSE = 6;
    static final int MSG_ID_RIL_MSG_DECODED = 10;
    protected static final int MSG_ID_SESSION_END = 1;
    static final String STK_DEFAULT = "Default Message";
    protected static IccRecords mIccRecords;
    protected static UiccCardApplication mUiccApplication;
    protected static HandlerThread mhandlerThread;
    private static CatService sInstance;
    protected static final Object sInstanceLock = new Object();
    protected IccCardStatus.CardState mCardState;
    protected CommandsInterface mCmdIf;
    protected Context mContext;
    protected CatCmdMessage mCurrntCmd;
    protected CatCmdMessage mMenuCmd;
    protected RilMessageDecoder mMsgDecoder;
    protected boolean mStkAppInstalled;
    protected UiccController mUiccController;

    private CatService(CommandsInterface ci, UiccCardApplication ca, IccRecords ir, Context context, IccFileHandler fh, UiccCard ic) {
        this.mCurrntCmd = null;
        this.mMenuCmd = null;
        this.mMsgDecoder = null;
        this.mStkAppInstalled = false;
        this.mCardState = IccCardStatus.CardState.CARDSTATE_ABSENT;
        if (ci == null || ca == null || ir == null || context == null || fh == null || ic == null) {
            throw new NullPointerException("Service: Input parameters must not be null");
        }
        this.mCmdIf = ci;
        this.mContext = context;
        this.mMsgDecoder = RilMessageDecoder.getInstance(this, fh);
        this.mCmdIf.setOnCatSessionEnd(this, 1, null);
        this.mCmdIf.setOnCatProactiveCmd(this, 2, null);
        this.mCmdIf.setOnCatEvent(this, 3, null);
        this.mCmdIf.setOnCatCallSetUp(this, 4, null);
        this.mCmdIf.registerForIccRefresh(this, 30, null);
        this.mCmdIf.setOnCatCcAlphaNotify(this, 8, null);
        mIccRecords = ir;
        mUiccApplication = ca;
        mIccRecords.registerForRecordsLoaded(this, 20, null);
        this.mUiccController = UiccController.getInstance();
        if (this.mUiccController != null) {
            this.mUiccController.registerForIccChanged(this, 7, null);
        } else {
            CatLog.e(this, "UiccController instance is null");
        }
        this.mStkAppInstalled = isStkAppInstalled();
        CatLog.d(this, "Running CAT service. STK app installed:" + this.mStkAppInstalled);
    }

    protected CatService() {
        this.mCurrntCmd = null;
        this.mMenuCmd = null;
        this.mMsgDecoder = null;
        this.mStkAppInstalled = false;
        this.mCardState = IccCardStatus.CardState.CARDSTATE_ABSENT;
    }

    public void dispose() {
        synchronized (sInstanceLock) {
            CatLog.d(this, "Disposing CatService object");
            mIccRecords.unregisterForRecordsLoaded(this);
            broadcastCardStateAndIccRefreshResp(IccCardStatus.CardState.CARDSTATE_ABSENT, null);
            this.mCmdIf.unSetOnCatSessionEnd(this);
            this.mCmdIf.unSetOnCatProactiveCmd(this);
            this.mCmdIf.unSetOnCatEvent(this);
            this.mCmdIf.unSetOnCatCallSetUp(this);
            this.mCmdIf.unregisterForIccRefresh(this);
            if (this.mUiccController != null) {
                this.mUiccController.unregisterForIccChanged(this);
                this.mUiccController = null;
            }
            if (mUiccApplication != null) {
                mUiccApplication.unregisterForReady(this);
            }
            this.mMsgDecoder.dispose();
            this.mMsgDecoder = null;
            this.mCmdIf.unSetOnCatCcAlphaNotify(this);
            disposeHandlerThread();
            sInstance = null;
            removeCallbacksAndMessages(null);
        }
    }

    protected void disposeHandlerThread() {
        mhandlerThread.quit();
        mhandlerThread = null;
    }

    protected void finalize() {
        CatLog.d(this, "Service finalized");
    }

    private void handleRilMsg(RilMessage rilMsg) {
        CommandParams cmdParams;
        if (rilMsg != null) {
            switch (rilMsg.mId) {
                case 1:
                    handleSessionEnd();
                    break;
                case 2:
                    try {
                        CommandParams cmdParams2 = (CommandParams) rilMsg.mData;
                        if (cmdParams2 != null) {
                            if (rilMsg.mResCode == ResultCode.OK) {
                                handleCommand(cmdParams2, true);
                            } else {
                                sendTerminalResponse(cmdParams2.mCmdDet, rilMsg.mResCode, false, 0, null);
                            }
                        }
                    } catch (ClassCastException e) {
                        CatLog.d(this, "Fail to parse proactive command");
                        if (this.mCurrntCmd != null) {
                            sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.CMD_DATA_NOT_UNDERSTOOD, false, 0, null);
                            return;
                        }
                        return;
                    }
                    break;
                case 3:
                    if (rilMsg.mResCode == ResultCode.OK && (cmdParams = (CommandParams) rilMsg.mData) != null) {
                        handleCommand(cmdParams, false);
                        break;
                    }
                    break;
                case 5:
                    CommandParams cmdParams3 = (CommandParams) rilMsg.mData;
                    if (cmdParams3 != null) {
                        handleCommand(cmdParams3, false);
                    }
                    break;
            }
        }
    }

    private boolean isSupportedSetupEventCommand(CatCmdMessage cmdMsg) {
        boolean flag = true;
        int[] arr$ = cmdMsg.getSetEventList().eventList;
        for (int eventVal : arr$) {
            CatLog.d(this, "Event: " + eventVal);
            switch (eventVal) {
                case 5:
                case 7:
                case 19:
                    break;
                default:
                    flag = false;
                    break;
            }
        }
        return flag;
    }

    private void handleCommand(CommandParams cmdParams, boolean isProactiveCmd) {
        boolean noAlphaUsrCnf;
        CatLog.d(this, cmdParams.getCommandType().name());
        CatCmdMessage cmdMsg = new CatCmdMessage(cmdParams);
        switch (cmdParams.getCommandType()) {
            case SET_UP_MENU:
                if (removeMenu(cmdMsg.getMenu())) {
                    this.mMenuCmd = null;
                } else {
                    this.mMenuCmd = cmdMsg;
                }
                ResultCode resultCode = cmdParams.mLoadIconFailed ? ResultCode.PRFRMD_ICON_NOT_DISPLAYED : ResultCode.OK;
                sendTerminalResponse(cmdParams.mCmdDet, resultCode, false, 0, null);
                break;
            case DISPLAY_TEXT:
            case SELECT_ITEM:
            case GET_INPUT:
            case GET_INKEY:
            case PLAY_TONE:
                break;
            case REFRESH:
                CatLog.d(this, "Pass Refresh to Stk app");
                break;
            case SET_UP_IDLE_MODE_TEXT:
                ResultCode resultCode2 = cmdParams.mLoadIconFailed ? ResultCode.PRFRMD_ICON_NOT_DISPLAYED : ResultCode.OK;
                sendTerminalResponse(cmdParams.mCmdDet, resultCode2, false, 0, null);
                break;
            case SET_UP_EVENT_LIST:
                if (isSupportedSetupEventCommand(cmdMsg)) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false, 0, null);
                } else {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                }
                break;
            case PROVIDE_LOCAL_INFORMATION:
                switch (cmdParams.mCmdDet.commandQualifier) {
                    case 3:
                        ResponseData resp = new DTTZResponseData(null);
                        sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false, 0, resp);
                        break;
                    case 4:
                        ResponseData resp2 = new LanguageResponseData(Locale.getDefault().getLanguage());
                        sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false, 0, resp2);
                        break;
                    default:
                        sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false, 0, null);
                        break;
                }
                return;
            case LAUNCH_BROWSER:
                if (((LaunchBrowserParams) cmdParams).mConfirmMsg.text != null && ((LaunchBrowserParams) cmdParams).mConfirmMsg.text.equals(STK_DEFAULT)) {
                    CharSequence message = this.mContext.getText(R.string.keyguard_accessibility_pattern_unlock);
                    ((LaunchBrowserParams) cmdParams).mConfirmMsg.text = message.toString();
                }
                break;
            case SEND_DTMF:
            case SEND_SMS:
            case SEND_SS:
            case SEND_USSD:
                if (((DisplayTextParams) cmdParams).mTextMsg.text != null && ((DisplayTextParams) cmdParams).mTextMsg.text.equals(STK_DEFAULT)) {
                    CharSequence message2 = this.mContext.getText(R.string.keyguard_accessibility_pattern_area);
                    ((DisplayTextParams) cmdParams).mTextMsg.text = message2.toString();
                }
                break;
            case SET_UP_CALL:
                if (((CallSetupParams) cmdParams).mConfirmMsg.text != null && ((CallSetupParams) cmdParams).mConfirmMsg.text.equals(STK_DEFAULT)) {
                    CharSequence message3 = this.mContext.getText(R.string.keyguard_accessibility_pin_unlock);
                    ((CallSetupParams) cmdParams).mConfirmMsg.text = message3.toString();
                }
                break;
            case OPEN_CHANNEL:
            case CLOSE_CHANNEL:
            case RECEIVE_DATA:
            case SEND_DATA:
                BIPClientParams cmd = (BIPClientParams) cmdParams;
                try {
                    noAlphaUsrCnf = this.mContext.getResources().getBoolean(R.bool.config_bg_current_drain_event_duration_based_threshold_enabled);
                } catch (Resources.NotFoundException e) {
                    noAlphaUsrCnf = false;
                }
                if (cmd.mTextMsg.text == null && (cmd.mHasAlphaId || noAlphaUsrCnf)) {
                    CatLog.d(this, "cmd " + cmdParams.getCommandType() + " with null alpha id");
                    if (isProactiveCmd) {
                        sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false, 0, null);
                        return;
                    } else {
                        if (cmdParams.getCommandType() == AppInterface.CommandType.OPEN_CHANNEL) {
                            this.mCmdIf.handleCallSetupRequestFromSim(true, null);
                            return;
                        }
                        return;
                    }
                }
                if (!this.mStkAppInstalled) {
                    CatLog.d(this, "No STK application found.");
                    if (isProactiveCmd) {
                        sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                        return;
                    }
                }
                if (isProactiveCmd && (cmdParams.getCommandType() == AppInterface.CommandType.CLOSE_CHANNEL || cmdParams.getCommandType() == AppInterface.CommandType.RECEIVE_DATA || cmdParams.getCommandType() == AppInterface.CommandType.SEND_DATA)) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false, 0, null);
                }
                break;
                break;
            case ACTIVATE:
                ResultCode resultCode3 = ResultCode.OK;
                sendTerminalResponse(cmdParams.mCmdDet, resultCode3, false, 0, null);
                break;
            default:
                CatLog.d(this, "Unsupported command");
                return;
        }
        this.mCurrntCmd = cmdMsg;
        broadcastCatCmdIntent(cmdMsg);
    }

    protected void broadcastCatCmdIntent(CatCmdMessage cmdMsg) {
        Intent intent = new Intent(AppInterface.CAT_CMD_ACTION);
        intent.addFlags(268435456);
        intent.putExtra("STK CMD", cmdMsg);
        this.mContext.sendBroadcast(intent);
    }

    protected void handleSessionEnd() {
        CatLog.d(this, "SESSION END");
        this.mCurrntCmd = this.mMenuCmd;
        Intent intent = new Intent(AppInterface.CAT_SESSION_END_ACTION);
        intent.addFlags(268435456);
        this.mContext.sendBroadcast(intent);
    }

    private void sendTerminalResponse(CommandDetails cmdDet, ResultCode resultCode, boolean includeAdditionalInfo, int additionalInfo, ResponseData resp) {
        if (cmdDet != null) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            Input cmdInput = null;
            if (this.mCurrntCmd != null) {
                cmdInput = this.mCurrntCmd.geInput();
            }
            int tag = ComprehensionTlvTag.COMMAND_DETAILS.value();
            if (cmdDet.compRequired) {
                tag |= 128;
            }
            buf.write(tag);
            buf.write(3);
            buf.write(cmdDet.commandNumber);
            buf.write(cmdDet.typeOfCommand);
            buf.write(cmdDet.commandQualifier);
            buf.write(ComprehensionTlvTag.DEVICE_IDENTITIES.value());
            buf.write(2);
            buf.write(130);
            buf.write(DEV_ID_UICC);
            int tag2 = ComprehensionTlvTag.RESULT.value();
            if (cmdDet.compRequired) {
                tag2 |= 128;
            }
            buf.write(tag2);
            int length = includeAdditionalInfo ? 2 : 1;
            buf.write(length);
            buf.write(resultCode.value());
            if (includeAdditionalInfo) {
                buf.write(additionalInfo);
            }
            if (resp != null) {
                resp.format(buf);
            } else {
                encodeOptionalTags(cmdDet, resultCode, cmdInput, buf);
            }
            byte[] rawData = buf.toByteArray();
            String hexString = IccUtils.bytesToHexString(rawData);
            this.mCmdIf.sendTerminalResponse(hexString, null);
        }
    }

    private void encodeOptionalTags(CommandDetails cmdDet, ResultCode resultCode, Input cmdInput, ByteArrayOutputStream buf) {
        AppInterface.CommandType cmdType = AppInterface.CommandType.fromInt(cmdDet.typeOfCommand);
        if (cmdType != null) {
            switch (cmdType) {
                case PROVIDE_LOCAL_INFORMATION:
                    if (cmdDet.commandQualifier == 4 && resultCode.value() == ResultCode.OK.value()) {
                        getPliResponse(buf);
                        break;
                    }
                    break;
                case GET_INKEY:
                    if (resultCode.value() == ResultCode.NO_RESPONSE_FROM_USER.value() && cmdInput != null && cmdInput.duration != null) {
                        getInKeyResponse(buf, cmdInput);
                        break;
                    }
                    break;
                default:
                    CatLog.d(this, "encodeOptionalTags() Unsupported Cmd details=" + cmdDet);
                    break;
            }
            return;
        }
        CatLog.d(this, "encodeOptionalTags() bad Cmd details=" + cmdDet);
    }

    private void getInKeyResponse(ByteArrayOutputStream buf, Input cmdInput) {
        int tag = ComprehensionTlvTag.DURATION.value();
        buf.write(tag);
        buf.write(2);
        Duration.TimeUnit timeUnit = cmdInput.duration.timeUnit;
        buf.write(Duration.TimeUnit.SECOND.value());
        buf.write(cmdInput.duration.timeInterval);
    }

    private void getPliResponse(ByteArrayOutputStream buf) {
        String lang = SystemProperties.get("persist.sys.language");
        if (lang != null) {
            int tag = ComprehensionTlvTag.LANGUAGE.value();
            buf.write(tag);
            ResponseData.writeLength(buf, lang.length());
            buf.write(lang.getBytes(), 0, lang.length());
        }
    }

    private void sendMenuSelection(int menuId, boolean helpRequired) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        buf.write(211);
        buf.write(0);
        int tag = ComprehensionTlvTag.DEVICE_IDENTITIES.value() | 128;
        buf.write(tag);
        buf.write(2);
        buf.write(1);
        buf.write(DEV_ID_UICC);
        int tag2 = ComprehensionTlvTag.ITEM_ID.value() | 128;
        buf.write(tag2);
        buf.write(1);
        buf.write(menuId);
        if (helpRequired) {
            int tag3 = ComprehensionTlvTag.HELP_REQUEST.value();
            buf.write(tag3);
            buf.write(0);
        }
        byte[] rawData = buf.toByteArray();
        int len = rawData.length - 2;
        rawData[1] = (byte) len;
        String hexString = IccUtils.bytesToHexString(rawData);
        this.mCmdIf.sendEnvelope(hexString, null);
    }

    private void eventDownload(int event, int sourceId, int destinationId, byte[] additionalInfo, boolean oneShot) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        buf.write(BerTlv.BER_EVENT_DOWNLOAD_TAG);
        buf.write(0);
        int tag = ComprehensionTlvTag.EVENT_LIST.value() | 128;
        buf.write(tag);
        buf.write(1);
        buf.write(event);
        int tag2 = ComprehensionTlvTag.DEVICE_IDENTITIES.value() | 128;
        buf.write(tag2);
        buf.write(2);
        buf.write(sourceId);
        buf.write(destinationId);
        switch (event) {
            case 5:
                CatLog.d(this, " Sending Idle Screen Available event download to ICC");
                break;
            case 7:
                CatLog.d(this, " Sending Language Selection event download to ICC");
                int tag3 = ComprehensionTlvTag.LANGUAGE.value() | 128;
                buf.write(tag3);
                buf.write(2);
                break;
            case 19:
                CatLog.d(this, " Sending HCI Connectivity event download to ICC");
                break;
        }
        if (additionalInfo != null) {
            for (byte b : additionalInfo) {
                buf.write(b);
            }
        }
        byte[] rawData = buf.toByteArray();
        int len = rawData.length - 2;
        rawData[1] = (byte) len;
        String hexString = IccUtils.bytesToHexString(rawData);
        CatLog.d(this, "ENVELOPE COMMAND: " + hexString);
        this.mCmdIf.sendEnvelope(hexString, null);
    }

    public static CatService getInstance(CommandsInterface ci, Context context, UiccCard ic) {
        CatService catService = null;
        UiccCardApplication ca = null;
        IccFileHandler fh = null;
        IccRecords ir = null;
        if (ic != null) {
            for (int i = 0; i < ic.getNumApplications(); i++) {
                ca = ic.getApplicationIndex(i);
                if (ca != null && ca.getType() != IccCardApplicationStatus.AppType.APPTYPE_UNKNOWN) {
                    fh = ca.getIccFileHandler();
                    ir = ca.getIccRecords();
                    break;
                }
            }
        }
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                if (ci != null && ca != null && ir != null && context != null && fh != null && ic != null) {
                    mhandlerThread = new HandlerThread("Cat Telephony service");
                    mhandlerThread.start();
                    sInstance = new CatService(ci, ca, ir, context, fh, ic);
                    CatLog.d(sInstance, "NEW sInstance");
                }
            } else if (ir != null && mIccRecords != ir) {
                if (mIccRecords != null) {
                    mIccRecords.unregisterForRecordsLoaded(sInstance);
                }
                if (mUiccApplication != null) {
                    mUiccApplication.unregisterForReady(sInstance);
                }
                CatLog.d(sInstance, "Reinitialize the Service with SIMRecords and UiccCardApplication");
                mIccRecords = ir;
                mUiccApplication = ca;
                mIccRecords.registerForRecordsLoaded(sInstance, 20, null);
                CatLog.d(sInstance, "sr changed reinitialize and return current sInstance");
            } else {
                CatLog.d(sInstance, "Return current sInstance");
            }
            catService = sInstance;
        }
        return catService;
    }

    public static AppInterface getInstance() {
        return getInstance(null, null, null);
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        AsyncResult ar;
        switch (msg.what) {
            case 1:
            case 2:
            case 3:
            case 5:
                CatLog.d(this, "ril message arrived");
                String data = null;
                if (msg.obj != null && (ar = (AsyncResult) msg.obj) != null && ar.result != null) {
                    try {
                        data = (String) ar.result;
                    } catch (ClassCastException e) {
                        return;
                    }
                    break;
                }
                this.mMsgDecoder.sendStartDecodingMessageParams(new RilMessage(msg.what, data));
                return;
            case 4:
                this.mMsgDecoder.sendStartDecodingMessageParams(new RilMessage(msg.what, null));
                return;
            case 6:
                handleCmdResponse((CatResponseMessage) msg.obj);
                return;
            case 7:
                CatLog.d(this, "MSG_ID_ICC_CHANGED");
                updateIccAvailability();
                return;
            case 8:
                CatLog.d(this, "Received CAT CC Alpha message from card");
                if (msg.obj != null) {
                    AsyncResult ar2 = (AsyncResult) msg.obj;
                    if (ar2 != null && ar2.result != null) {
                        broadcastAlphaMessage((String) ar2.result);
                        return;
                    } else {
                        CatLog.d(this, "CAT Alpha message: ar.result is null");
                        return;
                    }
                }
                CatLog.d(this, "CAT Alpha message: msg.obj is null");
                return;
            case 10:
                handleRilMsg((RilMessage) msg.obj);
                return;
            case 20:
                return;
            case 30:
                if (msg.obj != null) {
                    AsyncResult ar3 = (AsyncResult) msg.obj;
                    if (ar3 != null && ar3.result != null) {
                        broadcastCardStateAndIccRefreshResp(IccCardStatus.CardState.CARDSTATE_PRESENT, (IccRefreshResponse) ar3.result);
                        return;
                    } else {
                        CatLog.e(this, "Icc REFRESH with exception: " + ar3.exception);
                        return;
                    }
                }
                CatLog.e(this, "IccRefresh Message is null");
                return;
            default:
                throw new AssertionError("Unrecognized CAT command: " + msg.what);
        }
    }

    protected void broadcastAlphaMessage(String alphaString) {
        CatLog.d(this, "Broadcasting CAT Alpha message from card: " + alphaString);
        Intent intent = new Intent(AppInterface.CAT_ALPHA_NOTIFY_ACTION);
        intent.putExtra(AppInterface.ALPHA_STRING, alphaString);
        intent.addFlags(268435456);
        this.mContext.sendBroadcast(intent);
    }

    protected void broadcastCardStateAndIccRefreshResp(IccCardStatus.CardState cardState, IccRefreshResponse iccRefreshState) {
        Intent intent = new Intent(AppInterface.CAT_ICC_STATUS_CHANGE);
        intent.addFlags(268435456);
        boolean cardPresent = cardState == IccCardStatus.CardState.CARDSTATE_PRESENT;
        if (iccRefreshState != null) {
            intent.putExtra(AppInterface.REFRESH_RESULT, iccRefreshState.refreshResult);
            CatLog.d(this, "Sending IccResult with Result: " + iccRefreshState.refreshResult);
        }
        intent.putExtra(AppInterface.CARD_STATUS, cardPresent);
        CatLog.d(this, "Sending Card Status: " + cardState + " cardPresent: " + cardPresent);
        this.mContext.sendBroadcast(intent);
    }

    @Override // com.android.internal.telephony.cat.AppInterface
    public synchronized void onCmdResponse(CatResponseMessage resMsg) {
        if (resMsg != null) {
            Message msg = obtainMessage(6, resMsg);
            msg.sendToTarget();
        }
    }

    private boolean validateResponse(CatResponseMessage resMsg) {
        if (resMsg.mCmdDet.typeOfCommand == AppInterface.CommandType.SET_UP_EVENT_LIST.value() || resMsg.mCmdDet.typeOfCommand == AppInterface.CommandType.SET_UP_MENU.value()) {
            CatLog.d(this, "CmdType: " + resMsg.mCmdDet.typeOfCommand);
            return true;
        }
        if (this.mCurrntCmd == null) {
            return false;
        }
        boolean validResponse = resMsg.mCmdDet.compareTo(this.mCurrntCmd.mCmdDet);
        CatLog.d(this, "isResponse for last valid cmd: " + validResponse);
        return validResponse;
    }

    private boolean removeMenu(Menu menu) {
        try {
            return menu.items.size() == 1 && menu.items.get(0) == null;
        } catch (NullPointerException e) {
            CatLog.d(this, "Unable to get Menu's items size");
            return true;
        }
    }

    /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
    /* JADX WARN: Code duplicated, block: B:11:0x0032  */
    private void handleCmdResponse(CatResponseMessage resMsg) {
        ResponseData resp;
        if (validateResponse(resMsg)) {
            boolean helpRequired = false;
            CommandDetails cmdDet = resMsg.getCmdDetails();
            AppInterface.CommandType type = AppInterface.CommandType.fromInt(cmdDet.typeOfCommand);
            switch (resMsg.mResCode) {
                case HELP_INFO_REQUIRED:
                    helpRequired = true;
                case OK:
                case PRFRMD_WITH_PARTIAL_COMPREHENSION:
                case PRFRMD_WITH_MISSING_INFO:
                case PRFRMD_WITH_ADDITIONAL_EFS_READ:
                case PRFRMD_ICON_NOT_DISPLAYED:
                case PRFRMD_MODIFIED_BY_NAA:
                case PRFRMD_LIMITED_SERVICE:
                case PRFRMD_WITH_MODIFICATION:
                case PRFRMD_NAA_NOT_ACTIVE:
                case PRFRMD_TONE_NOT_PLAYED:
                case LAUNCH_BROWSER_ERROR:
                case TERMINAL_CRNTLY_UNABLE_TO_PROCESS:
                    switch (type) {
                        case SET_UP_MENU:
                            boolean helpRequired2 = resMsg.mResCode == ResultCode.HELP_INFO_REQUIRED;
                            sendMenuSelection(resMsg.mUsersMenuSelection, helpRequired2);
                            break;
                        case DISPLAY_TEXT:
                            if (resMsg.mResCode == ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS) {
                                resMsg.setAdditionalInfo(1);
                                resp = null;
                            } else {
                                resMsg.mIncludeAdditionalInfo = false;
                                resMsg.mAdditionalInfo = 0;
                                resp = null;
                            }
                            sendTerminalResponse(cmdDet, resMsg.mResCode, resMsg.mIncludeAdditionalInfo, resMsg.mAdditionalInfo, resp);
                            this.mCurrntCmd = null;
                            break;
                        case REFRESH:
                        case SET_UP_IDLE_MODE_TEXT:
                        case PROVIDE_LOCAL_INFORMATION:
                        case SEND_DTMF:
                        case SEND_SMS:
                        case SEND_SS:
                        case SEND_USSD:
                        case PLAY_TONE:
                        default:
                            resp = null;
                            sendTerminalResponse(cmdDet, resMsg.mResCode, resMsg.mIncludeAdditionalInfo, resMsg.mAdditionalInfo, resp);
                            this.mCurrntCmd = null;
                            break;
                        case SET_UP_EVENT_LIST:
                            if (5 == resMsg.mEventValue) {
                                eventDownload(resMsg.mEventValue, 2, DEV_ID_UICC, resMsg.mAddedInfo, false);
                            } else {
                                eventDownload(resMsg.mEventValue, 130, DEV_ID_UICC, resMsg.mAddedInfo, false);
                            }
                            break;
                        case LAUNCH_BROWSER:
                            resp = null;
                            sendTerminalResponse(cmdDet, resMsg.mResCode, resMsg.mIncludeAdditionalInfo, resMsg.mAdditionalInfo, resp);
                            this.mCurrntCmd = null;
                            break;
                        case SELECT_ITEM:
                            resp = new SelectItemResponseData(resMsg.mUsersMenuSelection);
                            sendTerminalResponse(cmdDet, resMsg.mResCode, resMsg.mIncludeAdditionalInfo, resMsg.mAdditionalInfo, resp);
                            this.mCurrntCmd = null;
                            break;
                        case GET_INPUT:
                        case GET_INKEY:
                            Input input = this.mCurrntCmd.geInput();
                            if (!input.yesNo) {
                                if (helpRequired) {
                                    resp = null;
                                } else {
                                    resp = new GetInkeyInputResponseData(resMsg.mUsersInput, input.ucs2, input.packed);
                                }
                            } else {
                                resp = new GetInkeyInputResponseData(resMsg.mUsersYesNoSelection);
                            }
                            sendTerminalResponse(cmdDet, resMsg.mResCode, resMsg.mIncludeAdditionalInfo, resMsg.mAdditionalInfo, resp);
                            this.mCurrntCmd = null;
                            break;
                        case SET_UP_CALL:
                        case OPEN_CHANNEL:
                            this.mCmdIf.handleCallSetupRequestFromSim(resMsg.mUsersConfirm, null);
                            this.mCurrntCmd = null;
                            break;
                    }
                    break;
                case BACKWARD_MOVE_BY_USER:
                case USER_NOT_ACCEPT:
                    if (type == AppInterface.CommandType.SET_UP_CALL || type == AppInterface.CommandType.OPEN_CHANNEL) {
                        this.mCmdIf.handleCallSetupRequestFromSim(false, null);
                        this.mCurrntCmd = null;
                    } else {
                        resp = null;
                        sendTerminalResponse(cmdDet, resMsg.mResCode, resMsg.mIncludeAdditionalInfo, resMsg.mAdditionalInfo, resp);
                        this.mCurrntCmd = null;
                    }
                    break;
                case NO_RESPONSE_FROM_USER:
                    if (type == AppInterface.CommandType.SET_UP_CALL) {
                        this.mCurrntCmd = null;
                        break;
                    }
                case UICC_SESSION_TERM_BY_USER:
                    resp = null;
                    sendTerminalResponse(cmdDet, resMsg.mResCode, resMsg.mIncludeAdditionalInfo, resMsg.mAdditionalInfo, resp);
                    this.mCurrntCmd = null;
                    break;
            }
        }
    }

    protected boolean isStkAppInstalled() {
        Intent intent = new Intent(AppInterface.CAT_CMD_ACTION);
        PackageManager pm = this.mContext.getPackageManager();
        List<ResolveInfo> broadcastReceivers = pm.queryBroadcastReceivers(intent, 128);
        int numReceiver = broadcastReceivers == null ? 0 : broadcastReceivers.size();
        return numReceiver > 0;
    }

    protected void updateIccAvailability() {
        IccCardStatus.CardState newState = IccCardStatus.CardState.CARDSTATE_ABSENT;
        if (this.mUiccController != null) {
            UiccCard newCard = this.mUiccController.getUiccCard();
            if (newCard != null) {
                newState = newCard.getCardState();
            }
            IccCardStatus.CardState oldState = this.mCardState;
            this.mCardState = newState;
            CatLog.d(this, "New Card State = " + newState + " Old Card State = " + oldState);
            if (oldState == IccCardStatus.CardState.CARDSTATE_PRESENT && newState != IccCardStatus.CardState.CARDSTATE_PRESENT) {
                broadcastCardStateAndIccRefreshResp(newState, null);
            } else if (oldState != IccCardStatus.CardState.CARDSTATE_PRESENT && newState == IccCardStatus.CardState.CARDSTATE_PRESENT) {
                this.mCmdIf.reportStkServiceIsRunning(null);
            }
        }
    }
}
