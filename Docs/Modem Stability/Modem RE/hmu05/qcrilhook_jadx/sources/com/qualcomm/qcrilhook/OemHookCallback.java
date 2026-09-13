package com.qualcomm.qcrilhook;

import android.os.Message;
import android.os.RemoteException;

/* JADX INFO: loaded from: classes.dex */
public class OemHookCallback extends IOemHookCallback.Stub {
    Message mAppMessage;

    public OemHookCallback(Message msg) {
        this.mAppMessage = msg;
    }

    @Override // com.qualcomm.qcrilhook.IOemHookCallback
    public void onOemHookResponse(byte[] response) throws RemoteException {
        QmiOemHook.receive(response, this.mAppMessage, QmiOemHookConstants.ResponseType.IS_ASYNC_RESPONSE);
    }
}
