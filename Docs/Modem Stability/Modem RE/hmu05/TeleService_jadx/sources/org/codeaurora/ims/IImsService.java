package org.codeaurora.ims;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Messenger;
import android.os.Parcel;
import android.os.RemoteException;

/* JADX INFO: loaded from: classes.dex */
public interface IImsService extends IInterface {
    int deregisterCallback(IImsServiceListener iImsServiceListener) throws RemoteException;

    int getRegistrationState() throws RemoteException;

    void queryImsServiceStatus(int i, Messenger messenger) throws RemoteException;

    int registerCallback(IImsServiceListener iImsServiceListener) throws RemoteException;

    void setRegistrationState(int i) throws RemoteException;

    void setServiceStatus(int i, int i2, int i3, int i4, int i5, Messenger messenger) throws RemoteException;

    public static abstract class Stub extends Binder implements IImsService {
        public Stub() {
            attachInterface(this, "org.codeaurora.ims.IImsService");
        }

        public static IImsService asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface("org.codeaurora.ims.IImsService");
            if (iin != null && (iin instanceof IImsService)) {
                return (IImsService) iin;
            }
            return new Proxy(obj);
        }

        @Override // android.os.Binder
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            Messenger _arg5;
            Messenger _arg1;
            switch (code) {
                case 1:
                    data.enforceInterface("org.codeaurora.ims.IImsService");
                    IImsServiceListener _arg0 = IImsServiceListener.Stub.asInterface(data.readStrongBinder());
                    int _result = registerCallback(_arg0);
                    reply.writeNoException();
                    reply.writeInt(_result);
                    return true;
                case 2:
                    data.enforceInterface("org.codeaurora.ims.IImsService");
                    IImsServiceListener _arg2 = IImsServiceListener.Stub.asInterface(data.readStrongBinder());
                    int _result2 = deregisterCallback(_arg2);
                    reply.writeNoException();
                    reply.writeInt(_result2);
                    return true;
                case 3:
                    data.enforceInterface("org.codeaurora.ims.IImsService");
                    int _arg3 = data.readInt();
                    setRegistrationState(_arg3);
                    reply.writeNoException();
                    return true;
                case 4:
                    data.enforceInterface("org.codeaurora.ims.IImsService");
                    int _result3 = getRegistrationState();
                    reply.writeNoException();
                    reply.writeInt(_result3);
                    return true;
                case 5:
                    data.enforceInterface("org.codeaurora.ims.IImsService");
                    int _arg4 = data.readInt();
                    if (data.readInt() != 0) {
                        _arg1 = (Messenger) Messenger.CREATOR.createFromParcel(data);
                    } else {
                        _arg1 = null;
                    }
                    queryImsServiceStatus(_arg4, _arg1);
                    reply.writeNoException();
                    return true;
                case 6:
                    data.enforceInterface("org.codeaurora.ims.IImsService");
                    int _arg6 = data.readInt();
                    int _arg7 = data.readInt();
                    int _arg8 = data.readInt();
                    int _arg9 = data.readInt();
                    int _arg10 = data.readInt();
                    if (data.readInt() != 0) {
                        _arg5 = (Messenger) Messenger.CREATOR.createFromParcel(data);
                    } else {
                        _arg5 = null;
                    }
                    setServiceStatus(_arg6, _arg7, _arg8, _arg9, _arg10, _arg5);
                    reply.writeNoException();
                    return true;
                case 1598968902:
                    reply.writeString("org.codeaurora.ims.IImsService");
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static class Proxy implements IImsService {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            @Override // android.os.IInterface
            public IBinder asBinder() {
                return this.mRemote;
            }

            @Override // org.codeaurora.ims.IImsService
            public int registerCallback(IImsServiceListener imsServListener) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("org.codeaurora.ims.IImsService");
                    _data.writeStrongBinder(imsServListener != null ? imsServListener.asBinder() : null);
                    this.mRemote.transact(1, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.IImsService
            public int deregisterCallback(IImsServiceListener imsServListener) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("org.codeaurora.ims.IImsService");
                    _data.writeStrongBinder(imsServListener != null ? imsServListener.asBinder() : null);
                    this.mRemote.transact(2, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.IImsService
            public void setRegistrationState(int imsRegState) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("org.codeaurora.ims.IImsService");
                    _data.writeInt(imsRegState);
                    this.mRemote.transact(3, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.IImsService
            public int getRegistrationState() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("org.codeaurora.ims.IImsService");
                    this.mRemote.transact(4, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.IImsService
            public void queryImsServiceStatus(int event, Messenger msgr) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("org.codeaurora.ims.IImsService");
                    _data.writeInt(event);
                    if (msgr != null) {
                        _data.writeInt(1);
                        msgr.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    this.mRemote.transact(5, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.IImsService
            public void setServiceStatus(int service, int networkType, int enabled, int restrictCause, int event, Messenger msgr) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("org.codeaurora.ims.IImsService");
                    _data.writeInt(service);
                    _data.writeInt(networkType);
                    _data.writeInt(enabled);
                    _data.writeInt(restrictCause);
                    _data.writeInt(event);
                    if (msgr != null) {
                        _data.writeInt(1);
                        msgr.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    this.mRemote.transact(6, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }
    }
}
