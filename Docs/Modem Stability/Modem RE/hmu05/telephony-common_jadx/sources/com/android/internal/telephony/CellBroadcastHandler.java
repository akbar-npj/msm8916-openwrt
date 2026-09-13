package com.android.internal.telephony;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Message;
import android.provider.Telephony;
import android.telephony.SmsCbMessage;

/* JADX INFO: loaded from: classes.dex */
public class CellBroadcastHandler extends WakeLockStateMachine {
    private CellBroadcastHandler(Context context) {
        this("CellBroadcastHandler", context, null);
    }

    protected CellBroadcastHandler(String debugTag, Context context, PhoneBase phone) {
        super(debugTag, context, phone);
    }

    public static CellBroadcastHandler makeCellBroadcastHandler(Context context) {
        CellBroadcastHandler handler = new CellBroadcastHandler(context);
        handler.start();
        return handler;
    }

    @Override // com.android.internal.telephony.WakeLockStateMachine
    protected boolean handleSmsMessage(Message message) {
        if (message.obj instanceof SmsCbMessage) {
            handleBroadcastSms((SmsCbMessage) message.obj);
            return true;
        }
        loge("handleMessage got object of type: " + message.obj.getClass().getName());
        return false;
    }

    protected void handleBroadcastSms(SmsCbMessage message) {
        Intent intent;
        String receiverPermission;
        int appOp;
        if (message.isEmergencyMessage()) {
            log("Dispatching emergency SMS CB");
            intent = new Intent(Telephony.Sms.Intents.SMS_EMERGENCY_CB_RECEIVED_ACTION);
            receiverPermission = "android.permission.RECEIVE_EMERGENCY_BROADCAST";
            appOp = 17;
            intent.addFlags(268435456);
        } else {
            log("Dispatching SMS CB");
            intent = new Intent(Telephony.Sms.Intents.SMS_CB_RECEIVED_ACTION);
            receiverPermission = "android.permission.RECEIVE_SMS";
            appOp = 16;
        }
        intent.putExtra("message", message);
        this.mContext.sendOrderedBroadcast(intent, receiverPermission, appOp, this.mReceiver, getHandler(), -1, (String) null, (Bundle) null);
    }
}
