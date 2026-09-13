package com.android.internal.telephony.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

/* JADX INFO: compiled from: ModelInterpreter.java */
/* JADX INFO: loaded from: classes.dex */
class LineReader {
    static final int BUFFER_SIZE = 4096;
    byte[] mBuffer = new byte[4096];
    InputStream mInStream;

    LineReader(InputStream s) {
        this.mInStream = s;
    }

    String getNextLine() {
        return getNextLine(false);
    }

    String getNextLineCtrlZ() {
        return getNextLine(true);
    }

    String getNextLine(boolean ctrlZ) {
        int i;
        int i2 = 0;
        while (true) {
            try {
                int result = this.mInStream.read();
                if (result >= 0) {
                    if (ctrlZ && result == 26) {
                        break;
                    }
                    if (result == 13 || result == 10) {
                        if (i2 != 0) {
                            break;
                        }
                    } else {
                        i = i2 + 1;
                        try {
                            this.mBuffer[i2] = (byte) result;
                            i2 = i;
                        } catch (IOException e) {
                            return null;
                        } catch (IndexOutOfBoundsException e2) {
                            System.err.println("ATChannel: buffer overflow");
                            return new String(this.mBuffer, 0, i, "US-ASCII");
                        }
                    }
                } else {
                    return null;
                }
            } catch (IOException e3) {
                return null;
            } catch (IndexOutOfBoundsException e4) {
                i = i2;
            }
        }
        i = i2;
        try {
            return new String(this.mBuffer, 0, i, "US-ASCII");
        } catch (UnsupportedEncodingException e5) {
            System.err.println("ATChannel: implausable UnsupportedEncodingException");
            return null;
        }
    }
}
