package org.codeaurora.ims.csvt;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public interface ICsvtServiceListener extends IInterface {
    void onCallForwardingOptions(List<CallForwardInfoP> list) throws RemoteException;

    void onCallStatus(int i) throws RemoteException;

    void onCallWaiting(boolean z) throws RemoteException;

    void onPhoneStateChanged(int i) throws RemoteException;

    void onRingbackTone(boolean z) throws RemoteException;

    public static abstract class Stub extends Binder implements ICsvtServiceListener {
        private static final String DESCRIPTOR = "org.codeaurora.ims.csvt.ICsvtServiceListener";
        static final int TRANSACTION_onCallForwardingOptions = 4;
        static final int TRANSACTION_onCallStatus = 2;
        static final int TRANSACTION_onCallWaiting = 3;
        static final int TRANSACTION_onPhoneStateChanged = 1;
        static final int TRANSACTION_onRingbackTone = 5;

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static ICsvtServiceListener asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin != null && (iin instanceof ICsvtServiceListener)) {
                return (ICsvtServiceListener) iin;
            }
            return new Proxy(obj);
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        @Override // android.os.Binder
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            boolean _arg0;
            switch (code) {
                case 1:
                    data.enforceInterface(DESCRIPTOR);
                    onPhoneStateChanged(data.readInt());
                    return true;
                case 2:
                    data.enforceInterface(DESCRIPTOR);
                    onCallStatus(data.readInt());
                    return true;
                case 3:
                    data.enforceInterface(DESCRIPTOR);
                    _arg0 = data.readInt() != 0;
                    onCallWaiting(_arg0);
                    return true;
                case 4:
                    data.enforceInterface(DESCRIPTOR);
                    onCallForwardingOptions(data.createTypedArrayList(CallForwardInfoP.CREATOR));
                    return true;
                case 5:
                    data.enforceInterface(DESCRIPTOR);
                    _arg0 = data.readInt() != 0;
                    onRingbackTone(_arg0);
                    return true;
                case 1598968902:
                    reply.writeString(DESCRIPTOR);
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static class Proxy implements ICsvtServiceListener {
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

            @Override // org.codeaurora.ims.csvt.ICsvtServiceListener
            public void onPhoneStateChanged(int state) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(state);
                    this.mRemote.transact(1, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.csvt.ICsvtServiceListener
            public void onCallStatus(int status) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(status);
                    this.mRemote.transact(2, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.csvt.ICsvtServiceListener
            public void onCallWaiting(boolean enabled) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(enabled ? 1 : 0);
                    this.mRemote.transact(3, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.csvt.ICsvtServiceListener
            public void onCallForwardingOptions(List<CallForwardInfoP> fi) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeTypedList(fi);
                    this.mRemote.transact(4, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            @Override // org.codeaurora.ims.csvt.ICsvtServiceListener
            public void onRingbackTone(boolean playTone) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(playTone ? 1 : 0);
                    this.mRemote.transact(5, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }
        }
    }
}
