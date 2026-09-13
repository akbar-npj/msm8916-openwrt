package org.codeaurora.btmultisim;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/* JADX INFO: loaded from: classes.dex */
public interface IBluetoothDsdaService extends IInterface {
    void SwitchSub() throws RemoteException;

    boolean answerOnThisSubAllowed() throws RemoteException;

    boolean canDoCallSwap() throws RemoteException;

    int getTotalCallsOnSub(int i) throws RemoteException;

    void handleCdmaSetSecondCallState(boolean z) throws RemoteException;

    void handleCdmaSwapSecondCallState() throws RemoteException;

    void handleListCurrentCalls() throws RemoteException;

    void handleMultiSimPreciseCallStateChange() throws RemoteException;

    boolean isSwitchSubAllowed() throws RemoteException;

    void phoneSubChanged() throws RemoteException;

    void processQueryPhoneState() throws RemoteException;

    void setCurrentCallState(int i, int i2, boolean z) throws RemoteException;

    void setCurrentSub(int i) throws RemoteException;

    void updateCdmaHeldCall(int i) throws RemoteException;

    public static abstract class Stub extends Binder implements IBluetoothDsdaService {
        public Stub() {
            attachInterface(this, "org.codeaurora.btmultisim.IBluetoothDsdaService");
        }

        public static IBluetoothDsdaService asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface("org.codeaurora.btmultisim.IBluetoothDsdaService");
            if (iin != null && (iin instanceof IBluetoothDsdaService)) {
                return (IBluetoothDsdaService) iin;
            }
            return new Proxy(obj);
        }

        @Override // android.os.Binder
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            switch (code) {
                case 1:
                    data.enforceInterface("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    int _arg0 = data.readInt();
                    setCurrentSub(_arg0);
                    reply.writeNoException();
                    return true;
                case 2:
                    data.enforceInterface("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    phoneSubChanged();
                    reply.writeNoException();
                    return true;
                case 3:
                    data.enforceInterface("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    handleMultiSimPreciseCallStateChange();
                    reply.writeNoException();
                    return true;
                case 4:
                    data.enforceInterface("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    handleListCurrentCalls();
                    reply.writeNoException();
                    return true;
                case 5:
                    data.enforceInterface("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    processQueryPhoneState();
                    reply.writeNoException();
                    return true;
                case 6:
                    data.enforceInterface("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    handleCdmaSwapSecondCallState();
                    reply.writeNoException();
                    return true;
                case 7:
                    data.enforceInterface("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    boolean _arg1 = data.readInt() != 0;
                    handleCdmaSetSecondCallState(_arg1);
                    reply.writeNoException();
                    return true;
                case 8:
                    data.enforceInterface("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    int _arg2 = data.readInt();
                    int _arg3 = data.readInt();
                    boolean _arg4 = data.readInt() != 0;
                    setCurrentCallState(_arg2, _arg3, _arg4);
                    reply.writeNoException();
                    return true;
                case 9:
                    data.enforceInterface("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    int _arg5 = data.readInt();
                    int _result = getTotalCallsOnSub(_arg5);
                    reply.writeNoException();
                    reply.writeInt(_result);
                    return true;
                case 10:
                    data.enforceInterface("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    boolean _result2 = isSwitchSubAllowed();
                    reply.writeNoException();
                    reply.writeInt(_result2 ? 1 : 0);
                    return true;
                case 11:
                    data.enforceInterface("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    SwitchSub();
                    reply.writeNoException();
                    return true;
                case 12:
                    data.enforceInterface("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    boolean _result3 = canDoCallSwap();
                    reply.writeNoException();
                    reply.writeInt(_result3 ? 1 : 0);
                    return true;
                case 13:
                    data.enforceInterface("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    boolean _result4 = answerOnThisSubAllowed();
                    reply.writeNoException();
                    reply.writeInt(_result4 ? 1 : 0);
                    return true;
                case 14:
                    data.enforceInterface("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    int _arg6 = data.readInt();
                    updateCdmaHeldCall(_arg6);
                    reply.writeNoException();
                    return true;
                case 1598968902:
                    reply.writeString("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static class Proxy implements IBluetoothDsdaService {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            @Override // android.os.IInterface
            public IBinder asBinder() {
                return this.mRemote;
            }

            @Override // org.codeaurora.btmultisim.IBluetoothDsdaService
            public void setCurrentSub(int sub) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    _data.writeInt(sub);
                    this.mRemote.transact(1, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.btmultisim.IBluetoothDsdaService
            public void phoneSubChanged() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    this.mRemote.transact(2, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.btmultisim.IBluetoothDsdaService
            public void handleMultiSimPreciseCallStateChange() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    this.mRemote.transact(3, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.btmultisim.IBluetoothDsdaService
            public void handleListCurrentCalls() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    this.mRemote.transact(4, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.btmultisim.IBluetoothDsdaService
            public void processQueryPhoneState() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    this.mRemote.transact(5, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.btmultisim.IBluetoothDsdaService
            public void handleCdmaSwapSecondCallState() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    this.mRemote.transact(6, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.btmultisim.IBluetoothDsdaService
            public void handleCdmaSetSecondCallState(boolean state) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    _data.writeInt(state ? 1 : 0);
                    this.mRemote.transact(7, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.btmultisim.IBluetoothDsdaService
            public void setCurrentCallState(int currCallState, int prevCallState, boolean IsThreeWayCallOrigStateDialing) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    _data.writeInt(currCallState);
                    _data.writeInt(prevCallState);
                    _data.writeInt(IsThreeWayCallOrigStateDialing ? 1 : 0);
                    this.mRemote.transact(8, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.btmultisim.IBluetoothDsdaService
            public int getTotalCallsOnSub(int subId) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    _data.writeInt(subId);
                    this.mRemote.transact(9, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.btmultisim.IBluetoothDsdaService
            public boolean isSwitchSubAllowed() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    this.mRemote.transact(10, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.btmultisim.IBluetoothDsdaService
            public void SwitchSub() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    this.mRemote.transact(11, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.btmultisim.IBluetoothDsdaService
            public boolean canDoCallSwap() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    this.mRemote.transact(12, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.btmultisim.IBluetoothDsdaService
            public boolean answerOnThisSubAllowed() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    this.mRemote.transact(13, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.btmultisim.IBluetoothDsdaService
            public void updateCdmaHeldCall(int numheld) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("org.codeaurora.btmultisim.IBluetoothDsdaService");
                    _data.writeInt(numheld);
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
