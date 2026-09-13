package com.android.phone;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/* JADX INFO: loaded from: classes.dex */
public interface INetworkQueryService extends IInterface {
    void startNetworkQuery(INetworkQueryServiceCallback iNetworkQueryServiceCallback) throws RemoteException;

    void stopNetworkQuery(INetworkQueryServiceCallback iNetworkQueryServiceCallback) throws RemoteException;

    public static abstract class Stub extends Binder implements INetworkQueryService {
        public Stub() {
            attachInterface(this, "com.android.phone.INetworkQueryService");
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        @Override // android.os.Binder
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            switch (code) {
                case 1:
                    data.enforceInterface("com.android.phone.INetworkQueryService");
                    INetworkQueryServiceCallback _arg0 = INetworkQueryServiceCallback.Stub.asInterface(data.readStrongBinder());
                    startNetworkQuery(_arg0);
                    return true;
                case 2:
                    data.enforceInterface("com.android.phone.INetworkQueryService");
                    INetworkQueryServiceCallback _arg1 = INetworkQueryServiceCallback.Stub.asInterface(data.readStrongBinder());
                    stopNetworkQuery(_arg1);
                    return true;
                case 1598968902:
                    reply.writeString("com.android.phone.INetworkQueryService");
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }
    }
}
