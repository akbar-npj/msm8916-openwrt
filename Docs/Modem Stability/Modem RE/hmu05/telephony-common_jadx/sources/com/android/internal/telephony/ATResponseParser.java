package com.android.internal.telephony;

/* JADX INFO: loaded from: classes.dex */
public class ATResponseParser {
    private String mLine;
    private int mNext = 0;
    private int mTokEnd;
    private int mTokStart;

    public ATResponseParser(String line) {
        this.mLine = line;
    }

    public boolean nextBoolean() {
        nextTok();
        if (this.mTokEnd - this.mTokStart > 1) {
            throw new ATParseEx();
        }
        char c = this.mLine.charAt(this.mTokStart);
        if (c == '0') {
            return false;
        }
        if (c != '1') {
            throw new ATParseEx();
        }
        return true;
    }

    public int nextInt() {
        int ret = 0;
        nextTok();
        for (int i = this.mTokStart; i < this.mTokEnd; i++) {
            char c = this.mLine.charAt(i);
            if (c < '0' || c > '9') {
                throw new ATParseEx();
            }
            ret = (ret * 10) + (c - '0');
        }
        return ret;
    }

    public String nextString() {
        nextTok();
        return this.mLine.substring(this.mTokStart, this.mTokEnd);
    }

    public boolean hasMore() {
        return this.mNext < this.mLine.length();
    }

    private void nextTok() {
        int len = this.mLine.length();
        if (this.mNext == 0) {
            skipPrefix();
        }
        if (this.mNext >= len) {
            throw new ATParseEx();
        }
        try {
            String str = this.mLine;
            int i = this.mNext;
            this.mNext = i + 1;
            char c = skipWhiteSpace(str.charAt(i));
            if (c == '\"') {
                if (this.mNext >= len) {
                    throw new ATParseEx();
                }
                String str2 = this.mLine;
                int i2 = this.mNext;
                this.mNext = i2 + 1;
                char c2 = str2.charAt(i2);
                this.mTokStart = this.mNext - 1;
                while (c2 != '\"' && this.mNext < len) {
                    String str3 = this.mLine;
                    int i3 = this.mNext;
                    this.mNext = i3 + 1;
                    c2 = str3.charAt(i3);
                }
                if (c2 != '\"') {
                    throw new ATParseEx();
                }
                this.mTokEnd = this.mNext - 1;
                if (this.mNext < len) {
                    String str4 = this.mLine;
                    int i4 = this.mNext;
                    this.mNext = i4 + 1;
                    if (str4.charAt(i4) != ',') {
                        throw new ATParseEx();
                    }
                    return;
                }
                return;
            }
            this.mTokStart = this.mNext - 1;
            this.mTokEnd = this.mTokStart;
            while (c != ',') {
                if (!Character.isWhitespace(c)) {
                    this.mTokEnd = this.mNext;
                }
                if (this.mNext != len) {
                    String str5 = this.mLine;
                    int i5 = this.mNext;
                    this.mNext = i5 + 1;
                    c = str5.charAt(i5);
                } else {
                    return;
                }
            }
        } catch (StringIndexOutOfBoundsException e) {
            throw new ATParseEx();
        }
    }

    private char skipWhiteSpace(char c) {
        int len = this.mLine.length();
        while (this.mNext < len && Character.isWhitespace(c)) {
            String str = this.mLine;
            int i = this.mNext;
            this.mNext = i + 1;
            c = str.charAt(i);
        }
        if (Character.isWhitespace(c)) {
            throw new ATParseEx();
        }
        return c;
    }

    private void skipPrefix() {
        this.mNext = 0;
        int s = this.mLine.length();
        while (this.mNext < s) {
            String str = this.mLine;
            int i = this.mNext;
            this.mNext = i + 1;
            char c = str.charAt(i);
            if (c == ':') {
                return;
            }
        }
        throw new ATParseEx("missing prefix");
    }
}
