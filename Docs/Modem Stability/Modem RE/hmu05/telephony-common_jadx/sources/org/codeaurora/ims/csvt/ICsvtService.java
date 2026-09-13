package org.codeaurora.ims.csvt;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Message;
import android.os.Parcel;
import android.os.RemoteException;

/* JADX INFO: loaded from: classes.dex */
public interface ICsvtService extends IInterface {
    void acceptCall() throws RemoteException;

    void dial(String str) throws RemoteException;

    void fallBack() throws RemoteException;

    void getCallForwardingOption(int i, Message message) throws RemoteException;

    void getCallWaiting(Message message) throws RemoteException;

    void hangup() throws RemoteException;

    boolean isActive() throws RemoteException;

    boolean isIdle() throws RemoteException;

    boolean isNonCsvtIdle() throws RemoteException;

    void registerListener(ICsvtServiceListener iCsvtServiceListener) throws RemoteException;

    void rejectCall() throws RemoteException;

    void setCallForwardingOption(int i, int i2, String str, int i3, Message message) throws RemoteException;

    void setCallWaiting(boolean z, Message message) throws RemoteException;

    void unregisterListener(ICsvtServiceListener iCsvtServiceListener) throws RemoteException;

    public static abstract class Stub extends Binder implements ICsvtService {
        private static final String DESCRIPTOR = "org.codeaurora.ims.csvt.ICsvtService";
        static final int TRANSACTION_acceptCall = 3;
        static final int TRANSACTION_dial = 1;
        static final int TRANSACTION_fallBack = 5;
        static final int TRANSACTION_getCallForwardingOption = 9;
        static final int TRANSACTION_getCallWaiting = 11;
        static final int TRANSACTION_hangup = 2;
        static final int TRANSACTION_isActive = 7;
        static final int TRANSACTION_isIdle = 6;
        static final int TRANSACTION_isNonCsvtIdle = 8;
        static final int TRANSACTION_registerListener = 13;
        static final int TRANSACTION_rejectCall = 4;
        static final int TRANSACTION_setCallForwardingOption = 10;
        static final int TRANSACTION_setCallWaiting = 12;
        static final int TRANSACTION_unregisterListener = 14;

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static ICsvtService asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin != null && (iin instanceof ICsvtService)) {
                return (ICsvtService) iin;
            }
            return new Proxy(obj);
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        @Override // android.os.Binder
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            Message _arg1;
            Message _arg0;
            Message _arg4;
            Message _arg2;
            switch (code) {
                case 1:
                    data.enforceInterface(DESCRIPTOR);
                    String _arg3 = data.readString();
                    dial(_arg3);
                    reply.writeNoException();
                    return true;
                case 2:
                    data.enforceInterface(DESCRIPTOR);
                    hangup();
                    reply.writeNoException();
                    return true;
                case 3:
                    data.enforceInterface(DESCRIPTOR);
                    acceptCall();
                    reply.writeNoException();
                    return true;
                case 4:
                    data.enforceInterface(DESCRIPTOR);
                    rejectCall();
                    reply.writeNoException();
                    return true;
                case 5:
                    data.enforceInterface(DESCRIPTOR);
                    fallBack();
                    reply.writeNoException();
                    return true;
                case 6:
                    data.enforceInterface(DESCRIPTOR);
                    boolean _result = isIdle();
                    reply.writeNoException();
                    reply.writeInt(_result ? 1 : 0);
                    return true;
                case 7:
                    data.enforceInterface(DESCRIPTOR);
                    boolean _result2 = isActive();
                    reply.writeNoException();
                    reply.writeInt(_result2 ? 1 : 0);
                    return true;
                case 8:
                    data.enforceInterface(DESCRIPTOR);
                    boolean _result3 = isNonCsvtIdle();
                    reply.writeNoException();
                    reply.writeInt(_result3 ? 1 : 0);
                    return true;
                case 9:
                    data.enforceInterface(DESCRIPTOR);
                    int _arg5 = data.readInt();
                    if (data.readInt() != 0) {
                        _arg2 = (Message) Message.CREATOR.createFromParcel(data);
                    } else {
                        _arg2 = null;
                    }
                    getCallForwardingOption(_arg5, _arg2);
                    reply.writeNoException();
                    return true;
                case 10:
                    data.enforceInterface(DESCRIPTOR);
                    int _arg6 = data.readInt();
                    int _arg7 = data.readInt();
                    String _arg8 = data.readString();
                    int _arg9 = data.readInt();
                    if (data.readInt() != 0) {
                        _arg4 = (Message) Message.CREATOR.createFromParcel(data);
                    } else {
                        _arg4 = null;
                    }
                    setCallForwardingOption(_arg6, _arg7, _arg8, _arg9, _arg4);
                    reply.writeNoException();
                    return true;
                case 11:
                    data.enforceInterface(DESCRIPTOR);
                    if (data.readInt() != 0) {
                        _arg0 = (Message) Message.CREATOR.createFromParcel(data);
                    } else {
                        _arg0 = null;
                    }
                    getCallWaiting(_arg0);
                    reply.writeNoException();
                    return true;
                case 12:
                    data.enforceInterface(DESCRIPTOR);
                    boolean _arg10 = data.readInt() != 0;
                    if (data.readInt() != 0) {
                        _arg1 = (Message) Message.CREATOR.createFromParcel(data);
                    } else {
                        _arg1 = null;
                    }
                    setCallWaiting(_arg10, _arg1);
                    reply.writeNoException();
                    return true;
                case 13:
                    data.enforceInterface(DESCRIPTOR);
                    ICsvtServiceListener _arg11 = ICsvtServiceListener.Stub.asInterface(data.readStrongBinder());
                    registerListener(_arg11);
                    reply.writeNoException();
                    return true;
                case 14:
                    data.enforceInterface(DESCRIPTOR);
                    ICsvtServiceListener _arg12 = ICsvtServiceListener.Stub.asInterface(data.readStrongBinder());
                    unregisterListener(_arg12);
                    reply.writeNoException();
                    return true;
                case 1598968902:
                    reply.writeString(DESCRIPTOR);
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static class Proxy implements ICsvtService {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            @Override // android.os.IInterface
            public IBinder asBinder() {
                return this.mRemote;
            }

            public String getInterfaceDescriptor() {
                return Stub.DESCRIPTOR;
            }

            @Override // org.codeaurora.ims.csvt.ICsvtService
            public void dial(String number) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(number);
                    this.mRemote.transact(1, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.csvt.ICsvtService
            public void hangup() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(2, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.csvt.ICsvtService
            public void acceptCall() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(3, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.csvt.ICsvtService
            public void rejectCall() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(4, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.csvt.ICsvtService
            public void fallBack() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(5, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.csvt.ICsvtService
            public boolean isIdle() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(6, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.csvt.ICsvtService
            public boolean isActive() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(7, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.csvt.ICsvtService
            public boolean isNonCsvtIdle() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(8, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.csvt.ICsvtService
            public void getCallForwardingOption(int commandInterfaceCFReason, Message onComplete) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(commandInterfaceCFReason);
                    if (onComplete != null) {
                        _data.writeInt(1);
                        onComplete.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    this.mRemote.transact(9, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.csvt.ICsvtService
            public void setCallForwardingOption(int commandInterfaceCFReason, int commandInterfaceCFAction, String dialingNumber, int timerSeconds, Message onComplete) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(commandInterfaceCFReason);
                    _data.writeInt(commandInterfaceCFAction);
                    _data.writeString(dialingNumber);
                    _data.writeInt(timerSeconds);
                    if (onComplete != null) {
                        _data.writeInt(1);
                        onComplete.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    this.mRemote.transact(10, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.csvt.ICsvtService
            public void getCallWaiting(Message onComplete) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (onComplete != null) {
                        _data.writeInt(1);
                        onComplete.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    this.mRemote.transact(11, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.csvt.ICsvtService
            public void setCallWaiting(boolean enable, Message onComplete) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(enable ? 1 : 0);
                    if (onComplete != null) {
                        _data.writeInt(1);
                        onComplete.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    this.mRemote.transact(12, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.csvt.ICsvtService
            public void registerListener(ICsvtServiceListener l) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeStrongBinder(l != null ? l.asBinder() : null);
                    this.mRemote.transact(13, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.csvt.ICsvtService
            public void unregisterListener(ICsvtServiceListener l) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeStrongBinder(l != null ? l.asBinder() : null);
                    this.mRemote.transact(14, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }
    }
}
