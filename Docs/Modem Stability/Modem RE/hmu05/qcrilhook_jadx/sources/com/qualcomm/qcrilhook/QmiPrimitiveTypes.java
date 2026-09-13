package com.qualcomm.qcrilhook;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.InvalidParameterException;

/* JADX INFO: loaded from: classes.dex */
public class QmiPrimitiveTypes {
    private static final String LOG_TAG = "QmiPrimitiveTypes";
    public static final int SIZE_OF_BYTE = 1;
    public static final int SIZE_OF_INT = 4;
    public static final int SIZE_OF_LONG = 8;
    public static final int SIZE_OF_SHORT = 2;

    public static class QmiNull extends BaseQmiTypes.BaseQmiItemType {
        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiItemType
        public int getSize() {
            return 0;
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiItemType
        public byte[] toByteArray() {
            return new byte[0];
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.QmiBase
        public String toString() {
            return "val=null";
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiItemType
        public byte[] toTlv(short type) {
            return new byte[0];
        }
    }

    public static class QmiByte extends BaseQmiTypes.BaseQmiItemType {
        private byte mVal;

        public QmiByte() {
            this.mVal = (byte) 0;
        }

        public QmiByte(byte val) {
            this.mVal = val;
        }

        public QmiByte(short val) throws InvalidParameterException {
            try {
                this.mVal = PrimitiveParser.parseByte(val);
            } catch (NumberFormatException e) {
                throw new InvalidParameterException(e.toString());
            }
        }

        public QmiByte(int val) throws InvalidParameterException {
            try {
                this.mVal = PrimitiveParser.parseByte(val);
            } catch (NumberFormatException e) {
                throw new InvalidParameterException(e.toString());
            }
        }

        public QmiByte(char val) throws InvalidParameterException {
            try {
                this.mVal = PrimitiveParser.parseByte(val);
            } catch (NumberFormatException e) {
                throw new InvalidParameterException(e.toString());
            }
        }

        public QmiByte(byte[] bArray) throws InvalidParameterException {
            if (bArray.length < 1) {
                throw new InvalidParameterException();
            }
            ByteBuffer buf = createByteBuffer(bArray);
            this.mVal = buf.get();
        }

        public short toShort() {
            return PrimitiveParser.toUnsigned(this.mVal);
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiItemType
        public int getSize() {
            return 1;
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.QmiBase
        public String toString() {
            return String.format("val=%d", Byte.valueOf(this.mVal));
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiItemType
        public byte[] toByteArray() {
            ByteBuffer buf = createByteBuffer(getSize());
            buf.put(this.mVal);
            return buf.array();
        }
    }

    public static class QmiShort extends BaseQmiTypes.BaseQmiItemType {
        private short mVal;

        public QmiShort() {
            this.mVal = (short) 0;
        }

        public QmiShort(int val) throws InvalidParameterException {
            try {
                this.mVal = PrimitiveParser.parseShort(val);
            } catch (NumberFormatException e) {
                throw new InvalidParameterException(e.toString());
            }
        }

        public QmiShort(byte[] bArray) throws InvalidParameterException {
            if (bArray.length < 2) {
                throw new InvalidParameterException();
            }
            ByteBuffer buf = createByteBuffer(bArray);
            this.mVal = buf.getShort();
        }

        public int toInt() {
            return PrimitiveParser.toUnsigned(this.mVal);
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiItemType
        public int getSize() {
            return 2;
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.QmiBase
        public String toString() {
            return String.format("val=%d", Short.valueOf(this.mVal));
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiItemType
        public byte[] toByteArray() {
            ByteBuffer buf = createByteBuffer(getSize());
            buf.putShort(this.mVal);
            return buf.array();
        }
    }

    public static class QmiArray<T extends BaseQmiTypes.BaseQmiItemType> extends BaseQmiTypes.BaseQmiItemType {
        private short mArrayLength;
        private T[] mVal;
        private short vLenSize;

        public QmiArray(T[] arr, short maxArraySize, Class<T> c) throws InvalidParameterException {
            try {
                this.mVal = arr;
                this.mArrayLength = (short) arr.length;
                if (maxArraySize > 255) {
                    this.vLenSize = (short) 2;
                } else {
                    this.vLenSize = (short) 1;
                }
            } catch (NumberFormatException e) {
                throw new InvalidParameterException(e.toString());
            }
        }

        public QmiArray(T[] arr, Class<T> c, short valueSize) throws InvalidParameterException {
            try {
                this.mVal = arr;
                this.mArrayLength = (short) arr.length;
                this.vLenSize = valueSize;
            } catch (NumberFormatException e) {
                throw new InvalidParameterException(e.toString());
            }
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiItemType
        public int getSize() {
            int actualArrayBytesSize = 0;
            for (int i = 0; i < this.mArrayLength; i++) {
                actualArrayBytesSize += this.mVal[i].getSize();
            }
            return this.vLenSize + actualArrayBytesSize;
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.QmiBase
        public String toString() {
            StringBuffer s = new StringBuffer();
            for (int i = 0; i < this.mArrayLength; i++) {
                s.append(this.mVal[i].toString());
            }
            return s.toString();
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiItemType
        public byte[] toByteArray() {
            ByteBuffer buf = createByteBuffer(getSize());
            if (this.vLenSize == 2) {
                buf.putShort(this.mArrayLength);
            } else {
                buf.put(PrimitiveParser.parseByte(this.mArrayLength));
            }
            for (int i = 0; i < this.mArrayLength; i++) {
                buf.put(this.mVal[i].toByteArray());
            }
            return buf.array();
        }
    }

    public static class QmiEnum extends BaseQmiTypes.BaseQmiItemType {
        private short mVal;

        public QmiEnum(int val, int[] allowedValues) throws InvalidParameterException {
            try {
                this.mVal = PrimitiveParser.parseShort(val);
            } catch (NumberFormatException e) {
                throw new InvalidParameterException(e.toString());
            }
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiItemType
        public int getSize() {
            return 1;
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiItemType
        public byte[] toByteArray() {
            ByteBuffer buf = createByteBuffer(getSize());
            buf.putShort(this.mVal);
            return buf.array();
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.QmiBase
        public String toString() {
            return String.format("val=%d", Short.valueOf(this.mVal));
        }
    }

    public static class QmiInteger extends BaseQmiTypes.BaseQmiItemType {
        private int mVal;

        public QmiInteger() {
            this.mVal = 0;
        }

        public QmiInteger(long val) throws InvalidParameterException {
            try {
                this.mVal = PrimitiveParser.parseInt(val);
            } catch (NumberFormatException e) {
                throw new InvalidParameterException(e.toString());
            }
        }

        public QmiInteger(byte[] bArray) throws InvalidParameterException {
            if (bArray.length < 4) {
                throw new InvalidParameterException();
            }
            ByteBuffer buf = createByteBuffer(bArray);
            this.mVal = buf.getInt();
        }

        public long toLong() {
            return PrimitiveParser.toUnsigned(this.mVal);
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiItemType
        public int getSize() {
            return 4;
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.QmiBase
        public String toString() {
            return String.format("val=%d", Integer.valueOf(this.mVal));
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiItemType
        public byte[] toByteArray() {
            ByteBuffer buf = createByteBuffer(getSize());
            buf.putInt(this.mVal);
            return buf.array();
        }
    }

    public static class QmiLong extends BaseQmiTypes.BaseQmiItemType {
        private long mVal;

        public QmiLong() {
            this.mVal = 0L;
        }

        public QmiLong(long mVal) {
            this.mVal = mVal;
        }

        public QmiLong(String mVal) throws InvalidParameterException {
            try {
                this.mVal = PrimitiveParser.parseLong(mVal);
            } catch (NumberFormatException e) {
                throw new InvalidParameterException(e.toString());
            }
        }

        public QmiLong(byte[] bArray) throws InvalidParameterException {
            if (bArray.length < 8) {
                throw new InvalidParameterException();
            }
            ByteBuffer buf = createByteBuffer(bArray);
            this.mVal = buf.getLong();
        }

        public String toStringValue() {
            ByteBuffer buf = createByteBuffer(getSize());
            buf.order(ByteOrder.BIG_ENDIAN);
            buf.putLong(this.mVal);
            return new BigInteger(1, buf.array()).toString();
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiItemType
        public int getSize() {
            return 8;
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiItemType
        public byte[] toByteArray() {
            ByteBuffer buf = createByteBuffer(getSize());
            buf.putLong(this.mVal);
            return buf.array();
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.QmiBase
        public String toString() {
            return "val=" + this.mVal;
        }
    }

    public static class QmiString extends BaseQmiTypes.BaseQmiItemType {
        public static final int LENGTH_SIZE = 1;
        private String mVal;

        public QmiString() {
            this.mVal = new String();
        }

        public QmiString(String mVal) throws InvalidParameterException {
            if (mVal.length() > 65536) {
                throw new InvalidParameterException();
            }
            this.mVal = mVal;
        }

        public QmiString(byte[] bArray) throws InvalidParameterException {
            try {
                this.mVal = new String(bArray, BaseQmiTypes.QmiBase.QMI_CHARSET);
            } catch (UnsupportedEncodingException e) {
                throw new InvalidParameterException(e.toString());
            }
        }

        public String toStringValue() {
            return this.mVal;
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiItemType
        public int getSize() {
            return this.mVal.length();
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiItemType
        public byte[] toByteArray() {
            ByteBuffer buf = createByteBuffer(getSize());
            for (int i = 0; i < this.mVal.length(); i++) {
                buf.put((byte) this.mVal.charAt(i));
            }
            return buf.array();
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.QmiBase
        public String toString() {
            return "val=" + this.mVal;
        }
    }
}
