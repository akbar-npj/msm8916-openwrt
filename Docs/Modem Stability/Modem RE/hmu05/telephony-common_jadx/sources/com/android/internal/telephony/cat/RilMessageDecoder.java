package com.android.internal.telephony.cat;

import android.os.Handler;
import android.os.Message;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

/* JADX INFO: loaded from: classes.dex */
public class RilMessageDecoder extends StateMachine {
    private static final int CMD_PARAMS_READY = 2;
    private static final int CMD_START = 1;
    private static RilMessageDecoder sInstance = null;
    protected Handler mCaller;
    protected CommandParamsFactory mCmdParamsFactory;
    private RilMessage mCurrentRilMessage;
    protected StateCmdParamsReady mStateCmdParamsReady;
    protected StateStart mStateStart;

    public static synchronized RilMessageDecoder getInstance(Handler caller, IccFileHandler fh) {
        if (sInstance == null) {
            sInstance = new RilMessageDecoder(caller, fh);
            sInstance.start();
        }
        return sInstance;
    }

    public void sendStartDecodingMessageParams(RilMessage rilMsg) {
        Message msg = obtainMessage(1);
        msg.obj = rilMsg;
        sendMessage(msg);
    }

    public void sendMsgParamsDecoded(ResultCode resCode, CommandParams cmdParams) {
        Message msg = obtainMessage(2);
        msg.arg1 = resCode.value();
        msg.obj = cmdParams;
        sendMessage(msg);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sendCmdForExecution(RilMessage rilMsg) {
        Message msg = this.mCaller.obtainMessage(10, new RilMessage(rilMsg));
        msg.sendToTarget();
    }

    /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
    private RilMessageDecoder(Handler caller, IccFileHandler fh) {
        super("RilMessageDecoder");
        this.mCmdParamsFactory = null;
        this.mCurrentRilMessage = null;
        this.mCaller = null;
        this.mStateStart = new StateStart();
        this.mStateCmdParamsReady = new StateCmdParamsReady();
        addState(this.mStateStart);
        addState(this.mStateCmdParamsReady);
        setInitialState(this.mStateStart);
        this.mCaller = caller;
        this.mCmdParamsFactory = CommandParamsFactory.getInstance(this, fh);
    }

    /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
    protected RilMessageDecoder() {
        super("RilMessageDecoder");
        this.mCmdParamsFactory = null;
        this.mCurrentRilMessage = null;
        this.mCaller = null;
        this.mStateStart = new StateStart();
        this.mStateCmdParamsReady = new StateCmdParamsReady();
    }

    private class StateStart extends State {
        private StateStart() {
        }

        public boolean processMessage(Message msg) {
            if (msg.what == 1) {
                if (RilMessageDecoder.this.decodeMessageParams((RilMessage) msg.obj)) {
                    RilMessageDecoder.this.transitionTo(RilMessageDecoder.this.mStateCmdParamsReady);
                }
            } else {
                CatLog.d(this, "StateStart unexpected expecting START=1 got " + msg.what);
            }
            return true;
        }
    }

    private class StateCmdParamsReady extends State {
        private StateCmdParamsReady() {
        }

        public boolean processMessage(Message msg) {
            if (msg.what == 2) {
                RilMessageDecoder.this.mCurrentRilMessage.mResCode = ResultCode.fromInt(msg.arg1);
                RilMessageDecoder.this.mCurrentRilMessage.mData = msg.obj;
                RilMessageDecoder.this.sendCmdForExecution(RilMessageDecoder.this.mCurrentRilMessage);
                RilMessageDecoder.this.transitionTo(RilMessageDecoder.this.mStateStart);
                return true;
            }
            CatLog.d(this, "StateCmdParamsReady expecting CMD_PARAMS_READY=2 got " + msg.what);
            RilMessageDecoder.this.deferMessage(msg);
            return true;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean decodeMessageParams(RilMessage rilMsg) {
        this.mCurrentRilMessage = rilMsg;
        switch (rilMsg.mId) {
            case 1:
            case 4:
                this.mCurrentRilMessage.mResCode = ResultCode.OK;
                sendCmdForExecution(this.mCurrentRilMessage);
                return false;
            case 2:
            case 3:
            case 5:
                try {
                    byte[] rawData = IccUtils.hexStringToBytes((String) rilMsg.mData);
                    try {
                        this.mCmdParamsFactory.make(BerTlv.decode(rawData));
                        return true;
                    } catch (ResultException e) {
                        CatLog.d(this, "decodeMessageParams: caught ResultException e=" + e);
                        this.mCurrentRilMessage.mResCode = e.result();
                        sendCmdForExecution(this.mCurrentRilMessage);
                        return false;
                    }
                } catch (Exception e2) {
                    CatLog.d(this, "decodeMessageParams dropping zombie messages");
                    return false;
                }
            default:
                return false;
        }
    }

    public void dispose() {
        this.mStateStart = null;
        this.mStateCmdParamsReady = null;
        this.mCmdParamsFactory.dispose();
        this.mCmdParamsFactory = null;
        this.mCurrentRilMessage = null;
        this.mCaller = null;
        sInstance = null;
    }
}
