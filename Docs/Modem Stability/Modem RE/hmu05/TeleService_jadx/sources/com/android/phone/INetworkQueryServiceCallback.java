package com.android.phone;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import com.android.internal.telephony.OperatorInfo;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public interface INetworkQueryServiceCallback extends IInterface {
    void onQueryComplete(List<OperatorInfo> list, int i) throws RemoteException;

    public static abstract class Stub extends Binder implements INetworkQueryServiceCallback {
        public Stub() {
            attachInterface(this, "com.android.phone.INetworkQueryServiceCallback");
        }

        public static INetworkQueryServiceCallback asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface("com.android.phone.INetworkQueryServiceCallback");
            if (iin != null && (iin instanceof INetworkQueryServiceCallback)) {
                return (INetworkQueryServiceCallback) iin;
            }
            return new Proxy(obj);
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        @Override // android.os.Binder
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            switch (code) {
                case 1:
                    data.enforceInterface("com.android.phone.INetworkQueryServiceCallback");
                    List<OperatorInfo> _arg0 = data.createTypedArrayList(OperatorInfo.CREATOR);
                    int _arg1 = data.readInt();
                    onQueryComplete(_arg0, _arg1);
                    return true;
                case 1598968902:
                    reply.writeString("com.android.phone.INetworkQueryServiceCallback");
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static class Proxy implements INetworkQueryServiceCallback {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            @Override // android.os.IInterface
            public IBinder asBinder() {
                return this.mRemote;
            }

            @Override // com.android.phone.INetworkQueryServiceCallback
            public void onQueryComplete(List<OperatorInfo> networkInfoArray, int status) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.phone.INetworkQueryServiceCallback");
                    _data.writeTypedList(networkInfoArray);
                    _data.writeInt(status);
                    this.mRemote.transact(1, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }
        }
    }
}
