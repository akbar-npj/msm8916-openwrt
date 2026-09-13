package org.codeaurora.ims;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/* JADX INFO: loaded from: classes.dex */
public interface IImsServiceListener extends IInterface {
    void imsRegStateChangeReqFailed() throws RemoteException;

    void imsRegStateChanged(int i) throws RemoteException;

    void imsUpdateServiceStatus(int i, int i2) throws RemoteException;

    public static abstract class Stub extends Binder implements IImsServiceListener {
        public Stub() {
            attachInterface(this, "org.codeaurora.ims.IImsServiceListener");
        }

        public static IImsServiceListener asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface("org.codeaurora.ims.IImsServiceListener");
            if (iin != null && (iin instanceof IImsServiceListener)) {
                return (IImsServiceListener) iin;
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
                    data.enforceInterface("org.codeaurora.ims.IImsServiceListener");
                    int _arg0 = data.readInt();
                    imsRegStateChanged(_arg0);
                    reply.writeNoException();
                    return true;
                case 2:
                    data.enforceInterface("org.codeaurora.ims.IImsServiceListener");
                    imsRegStateChangeReqFailed();
                    reply.writeNoException();
                    return true;
                case 3:
                    data.enforceInterface("org.codeaurora.ims.IImsServiceListener");
                    int _arg1 = data.readInt();
                    int _arg2 = data.readInt();
                    imsUpdateServiceStatus(_arg1, _arg2);
                    reply.writeNoException();
                    return true;
                case 1598968902:
                    reply.writeString("org.codeaurora.ims.IImsServiceListener");
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static class Proxy implements IImsServiceListener {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            @Override // android.os.IInterface
            public IBinder asBinder() {
                return this.mRemote;
            }

            @Override // org.codeaurora.ims.IImsServiceListener
            public void imsRegStateChanged(int regstate) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("org.codeaurora.ims.IImsServiceListener");
                    _data.writeInt(regstate);
                    this.mRemote.transact(1, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.IImsServiceListener
            public void imsRegStateChangeReqFailed() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("org.codeaurora.ims.IImsServiceListener");
                    this.mRemote.transact(2, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.IImsServiceListener
            public void imsUpdateServiceStatus(int service, int status) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("org.codeaurora.ims.IImsServiceListener");
                    _data.writeInt(service);
                    _data.writeInt(status);
                    this.mRemote.transact(3, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }
    }
}
