package com.qualcomm.qcrilhook;

import android.util.Log;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/* JADX INFO: loaded from: classes.dex */
public class PresenceMsgParser {
    private static String LOG_TAG = "PresenceMsgParser";

    enum MediaCapabilities {
        FULL_DUPLEX,
        HALF_RECEIVE_ONLY,
        HALF_SEND_ONLY
    }

    public static class ContactInfo {
        public ListHeaderInfo listHeaderInfo;
        public String mAudioCapabilities;
        public String mContactUri;
        public String mDescription;
        public boolean mIsAudioSupported;
        public boolean mIsVideoSupported;
        public boolean mIsVolteContact;
        public int mPublishStatus;
        public String mResourceCid;
        public String mResourceId;
        public String mResourceReason;
        public String mResourceState;
        public String mResourceUri;
        public String mServiceId;
        public String mTimeStamp;
        public String mVersion;
        public String mVideoCapabilities;

        public String toString() {
            return "ContactInfo [listHeaderInfo=" + this.listHeaderInfo + ", mResourceUri=" + this.mResourceUri + ", mResourceId=" + this.mResourceId + ", mResourceState=" + this.mResourceState + ", mResourceReason=" + this.mResourceReason + ", mResourceCid=" + this.mResourceCid + ", mDescription=" + this.mDescription + ", mVersion=" + this.mVersion + ", mServiceId=" + this.mServiceId + ", mContactUri=" + this.mContactUri + ", mIsVolteContact=" + this.mIsVolteContact + ", mPublishStatus=" + this.mPublishStatus + ", mIsAudioSupported=" + this.mIsAudioSupported + ", mIsVideoSupported=" + this.mIsVideoSupported + ", mAudioCapabilities=" + this.mAudioCapabilities + ", mVideoCapabilities=" + this.mVideoCapabilities + ", mTimeStamp=" + this.mTimeStamp + "]";
        }
    }

    public static class ListHeaderInfo {
        public String mListContactUri;
        public String mListFullState;
        public String mListName;
        public String mListVersion;

        public String toString() {
            return "ListHeaderInfo [mListContactUri=" + this.mListContactUri + ", mListName=" + this.mListName + ", mListVersion=" + this.mListVersion + ", mListFullState=" + this.mListFullState + "]";
        }
    }

    static class PresenceRichNotifyParser {
        private ContactInfo c;
        private ListHeaderInfo listHeaderInfo;
        private ArrayList<ContactInfo> parsedContactList;
        private ByteBuffer respByteBuf;
        private int totalBytes;

        public PresenceRichNotifyParser(ByteBuffer respByteBuf, int n) {
            this.respByteBuf = respByteBuf;
            this.totalBytes = n;
        }

        private String parseString(int n) {
            if (this.respByteBuf.remaining() < n) {
                new Exception().printStackTrace();
                return "";
            }
            byte[] data = new byte[n];
            for (int i = 0; i < n; i++) {
                data[i] = this.respByteBuf.get();
            }
            return new String(data);
        }

        private int parseInteger() {
            if (this.respByteBuf.remaining() < 4) {
                new Exception().printStackTrace();
                return 0;
            }
            byte[] data = new byte[4];
            for (int i = 0; i < 4; i++) {
                data[i] = this.respByteBuf.get();
            }
            return PrimitiveParser.toUnsigned(data[0]);
        }

        private int parseShort() {
            if (this.respByteBuf.remaining() < 2) {
                new Exception().printStackTrace();
                return 0;
            }
            byte[] data = new byte[2];
            for (int i = 0; i < 2; i++) {
                data[i] = this.respByteBuf.get();
            }
            return PrimitiveParser.toUnsigned(data[0]);
        }

        private int parseByte() {
            if (this.respByteBuf.remaining() < 1) {
                new Exception().printStackTrace();
                return 0;
            }
            byte[] data = {this.respByteBuf.get()};
            return PrimitiveParser.toUnsigned(data[0]);
        }

        private void parseListContactUri() {
            int len = parseByte();
            String s = parseString(len);
            this.listHeaderInfo.mListContactUri = s;
            Log.d(PresenceMsgParser.LOG_TAG, "Parsing ListContactUri = " + s);
        }

        private void parseListName() {
            int len = parseByte();
            String s = parseString(len);
            this.listHeaderInfo.mListName = s;
            Log.d(PresenceMsgParser.LOG_TAG, "Parsing ListName = " + s);
        }

        private void parseListVersion() {
            int listVersion = parseInteger();
            this.listHeaderInfo.mListVersion = "" + listVersion;
            Log.d(PresenceMsgParser.LOG_TAG, "Parsing ListVersion = " + listVersion);
        }

        private void parseListFullState() {
            int b = parseByte();
            this.listHeaderInfo.mListFullState = "" + b;
            Log.d(PresenceMsgParser.LOG_TAG, "Parsing ListFullState = " + b);
        }

        private void parseListInfo() {
            parseListContactUri();
            parseListName();
            parseListVersion();
            parseListFullState();
        }

        private void parseResourceUri() {
            int len = parseByte();
            String s = parseString(len);
            this.c.mResourceUri = s;
            Log.d(PresenceMsgParser.LOG_TAG, "Parsing ResourceUri = " + s);
        }

        private void parseIsVolteContact() {
            int val = parseByte();
            this.c.mIsVolteContact = val == 1;
            Log.d(PresenceMsgParser.LOG_TAG, "Parsing IsVolteContact = " + this.c.mIsVolteContact);
        }

        private void parsePublishStatus() {
            int val = parseInteger();
            this.c.mPublishStatus = val;
            Log.d(PresenceMsgParser.LOG_TAG, "Parsing PublishStatus = " + val);
        }

        private void parseResourceId() {
            int len = parseByte();
            String s = parseString(len);
            this.c.mResourceId = s;
            Log.d(PresenceMsgParser.LOG_TAG, "Parsing ResourceId = " + s);
        }

        private void parseResourceState() {
            int len = parseByte();
            String s = parseString(len);
            this.c.mResourceState = s;
            Log.d(PresenceMsgParser.LOG_TAG, "Parsing ResourceState = " + s);
        }

        private void parseResourceReason() {
            int len = parseByte();
            String s = parseString(len);
            this.c.mResourceReason = s;
            Log.d(PresenceMsgParser.LOG_TAG, "Parsing ResourceReason = " + s);
        }

        private void parseResourceCid() {
            int len = parseShort();
            String s = parseString(len);
            this.c.mResourceCid = s;
            Log.d(PresenceMsgParser.LOG_TAG, "Parsing ResourceCid = " + s);
        }

        private void parseResouceInstance() {
            parseResourceId();
            parseResourceState();
            parseResourceReason();
            parseResourceCid();
        }

        private void parseContactUri() {
            int len = parseByte();
            String s = parseString(len);
            this.c.mContactUri = s;
            Log.d(PresenceMsgParser.LOG_TAG, "Parsing Contact Uri = " + s);
        }

        private void parseDescription() {
            int len = parseByte();
            String s = parseString(len);
            this.c.mDescription = s;
            Log.d(PresenceMsgParser.LOG_TAG, "Parsing Description = " + s);
        }

        private void parseVersion() {
            int len = parseByte();
            String s = parseString(len);
            this.c.mVersion = s;
            Log.d(PresenceMsgParser.LOG_TAG, "Parsing Version = " + s);
        }

        private void parseServiceid() {
            int len = parseByte();
            String s = parseString(len);
            this.c.mServiceId = s;
            Log.d(PresenceMsgParser.LOG_TAG, "Parsing ServiceId = " + s);
        }

        private void parseServiceDescriptions() {
            parseDescription();
            parseVersion();
            parseServiceid();
        }

        private void parseIsAudioSupported() {
            int val = parseByte();
            this.c.mIsAudioSupported = val == 1;
            Log.d(PresenceMsgParser.LOG_TAG, "Parsing isAudioSupported=" + this.c.mIsAudioSupported);
        }

        private void parseAudioCapability() {
            int val = parseInteger();
            this.c.mAudioCapabilities = MediaCapabilities.values()[val].toString();
            Log.d(PresenceMsgParser.LOG_TAG, "Parsing AudioCapabilities=" + this.c.mAudioCapabilities);
        }

        private void parseVideoCapability() {
            int val = parseInteger();
            this.c.mVideoCapabilities = MediaCapabilities.values()[val].toString();
            Log.d(PresenceMsgParser.LOG_TAG, "Parsing VideoCapabilities=" + this.c.mVideoCapabilities);
        }

        private void parseIsVideoSupported() {
            int val = parseByte();
            this.c.mIsVideoSupported = val == 1;
            Log.d(PresenceMsgParser.LOG_TAG, "Parsing isVideoSupported=" + this.c.mIsVideoSupported);
        }

        private void parseServiceCapabilities() {
            parseIsAudioSupported();
            parseAudioCapability();
            parseIsVideoSupported();
            parseVideoCapability();
        }

        private void parsePresenceInfo() {
            parseContactUri();
            parseServiceDescriptions();
            parseServiceCapabilities();
        }

        private void parseTimeStamp() {
            int len = parseByte();
            String s = parseString(len);
            this.c.mTimeStamp = s;
            Log.d(PresenceMsgParser.LOG_TAG, "Parsing timeStamp=" + this.c.mTimeStamp);
        }

        private void parsePresenceUserInfoWithTs() {
            parsePresenceInfo();
            parseTimeStamp();
        }

        public int parseUserListInfoLen() {
            return parseByte();
        }

        private void parseUserListInfo() {
            int numOfContacts = parseUserListInfoLen();
            Log.d(PresenceMsgParser.LOG_TAG, "Parsing numOfContacts = " + numOfContacts);
            for (int i = 0; i < numOfContacts; i++) {
                this.c = new ContactInfo();
                this.c.listHeaderInfo = this.listHeaderInfo;
                parseResourceUri();
                parseIsVolteContact();
                parsePublishStatus();
                parseResouceInstance();
                parsePresenceUserInfoWithTs();
                this.parsedContactList.add(this.c);
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public ArrayList<ContactInfo> parseRichInfo() {
            this.parsedContactList = new ArrayList<>();
            this.listHeaderInfo = new ListHeaderInfo();
            parseListInfo();
            parseUserListInfo();
            return this.parsedContactList;
        }
    }

    static ArrayList<ContactInfo> parseNotifyUpdate(ByteBuffer respByteBuf, int responseSize, int successStatus) {
        Log.d(LOG_TAG, "notifyUpdate(), Thread=" + Thread.currentThread().getName());
        while (responseSize > 0) {
            short type = PrimitiveParser.toUnsigned(respByteBuf.get());
            int length = PrimitiveParser.toUnsigned(respByteBuf.getShort());
            switch (type) {
                case 1:
                    Log.v(LOG_TAG, "NOTIFY_DETAIL_TYPE");
                    PresenceRichNotifyParser parser = new PresenceRichNotifyParser(respByteBuf, length);
                    ArrayList<ContactInfo> parsedContactList = parser.parseRichInfo();
                    Log.d(LOG_TAG, "parsed contact info " + parsedContactList);
                    return parsedContactList;
                case 16:
                    byte[] data = new byte[length];
                    for (int i = 0; i < length; i++) {
                        data[i] = respByteBuf.get();
                    }
                    int callId = PrimitiveParser.toUnsigned(data[0]);
                    Log.v(LOG_TAG, "callId = " + callId);
                    break;
            }
            responseSize -= length + 3;
        }
        return null;
    }

    static int parseEnablerState(ByteBuffer respByteBuf) {
        int val = (int) PrimitiveParser.toUnsigned(respByteBuf.getInt());
        return val;
    }

    static String parseNotifyUpdateXML(ByteBuffer respByteBuf) {
        int unsigned = (short) PrimitiveParser.toUnsigned(respByteBuf.getShort());
        byte[] data = new byte[unsigned];
        for (int i = 0; i < unsigned; i++) {
            data[i] = respByteBuf.get();
        }
        return new String(data);
    }

    static int parseGetNotifyReq(ByteBuffer respByteBuf) {
        byte optionalVal = (byte) PrimitiveParser.toUnsigned(respByteBuf.get());
        return optionalVal;
    }

    static int parseGetEventReport(ByteBuffer respByteBuf) {
        byte optionalVal = (byte) PrimitiveParser.toUnsigned(respByteBuf.get());
        return optionalVal;
    }

    static int parsePublishTrigger(ByteBuffer respByteBuf) {
        int val = (int) PrimitiveParser.toUnsigned(respByteBuf.getInt());
        return val;
    }

    static int parseEnablerStateInd(ByteBuffer respByteBuf) {
        int val = (int) PrimitiveParser.toUnsigned(respByteBuf.getInt());
        return val;
    }
}
