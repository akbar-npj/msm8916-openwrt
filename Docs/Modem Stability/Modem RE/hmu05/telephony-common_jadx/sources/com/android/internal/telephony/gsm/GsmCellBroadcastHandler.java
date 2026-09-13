package com.android.internal.telephony.gsm;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.CellLocation;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;
import android.telephony.gsm.GsmCellLocation;
import com.android.internal.telephony.CellBroadcastHandler;
import com.android.internal.telephony.PhoneBase;
import java.util.HashMap;
import java.util.Iterator;

/* JADX INFO: loaded from: classes.dex */
public class GsmCellBroadcastHandler extends CellBroadcastHandler {
    private static final boolean VDBG = false;
    private final HashMap<SmsCbConcatInfo, byte[][]> mSmsCbPageMap;

    protected GsmCellBroadcastHandler(Context context, PhoneBase phone) {
        super("GsmCellBroadcastHandler", context, phone);
        this.mSmsCbPageMap = new HashMap<>(4);
        phone.mCi.setOnNewGsmBroadcastSms(getHandler(), 1, null);
    }

    @Override // com.android.internal.telephony.WakeLockStateMachine
    protected void onQuitting() {
        this.mPhone.mCi.unSetOnNewGsmBroadcastSms(getHandler());
        super.onQuitting();
    }

    public static GsmCellBroadcastHandler makeGsmCellBroadcastHandler(Context context, PhoneBase phone) {
        GsmCellBroadcastHandler handler = new GsmCellBroadcastHandler(context, phone);
        handler.start();
        return handler;
    }

    @Override // com.android.internal.telephony.WakeLockStateMachine
    public void updatePhoneObject(PhoneBase phone) {
        this.mPhone = phone;
    }

    @Override // com.android.internal.telephony.CellBroadcastHandler, com.android.internal.telephony.WakeLockStateMachine
    protected boolean handleSmsMessage(Message message) {
        SmsCbMessage cbMessage;
        if (!(message.obj instanceof AsyncResult) || (cbMessage = handleGsmBroadcastSms((AsyncResult) message.obj)) == null) {
            return super.handleSmsMessage(message);
        }
        handleBroadcastSms(cbMessage);
        return true;
    }

    private SmsCbMessage handleGsmBroadcastSms(AsyncResult ar) {
        SmsCbLocation location;
        byte[][] pdus;
        try {
            byte[] receivedPdu = (byte[]) ar.result;
            SmsCbHeader header = new SmsCbHeader(receivedPdu);
            String plmn = SystemProperties.get("gsm.operator.numeric");
            int lac = -1;
            int cid = -1;
            CellLocation cl = this.mPhone.getCellLocation();
            if (cl instanceof GsmCellLocation) {
                GsmCellLocation cellLocation = (GsmCellLocation) cl;
                lac = cellLocation.getLac();
                cid = cellLocation.getCid();
            }
            switch (header.getGeographicalScope()) {
                case 0:
                case 3:
                    location = new SmsCbLocation(plmn, lac, cid);
                    break;
                case 1:
                default:
                    location = new SmsCbLocation(plmn);
                    break;
                case 2:
                    location = new SmsCbLocation(plmn, lac, -1);
                    break;
            }
            int pageCount = header.getNumberOfPages();
            if (pageCount > 1) {
                SmsCbConcatInfo concatInfo = new SmsCbConcatInfo(header, location);
                pdus = this.mSmsCbPageMap.get(concatInfo);
                if (pdus == null) {
                    pdus = new byte[pageCount][];
                    this.mSmsCbPageMap.put(concatInfo, pdus);
                }
                pdus[header.getPageIndex() - 1] = receivedPdu;
                byte[][] arr$ = pdus;
                for (byte[] pdu : arr$) {
                    if (pdu == null) {
                        return null;
                    }
                }
                this.mSmsCbPageMap.remove(concatInfo);
            } else {
                pdus = new byte[][]{receivedPdu};
            }
            Iterator<SmsCbConcatInfo> iter = this.mSmsCbPageMap.keySet().iterator();
            while (iter.hasNext()) {
                SmsCbConcatInfo info = iter.next();
                if (!info.matchesLocation(plmn, lac, cid)) {
                    iter.remove();
                }
            }
            return GsmSmsCbMessage.createSmsCbMessage(header, location, pdus);
        } catch (RuntimeException e) {
            loge("Error in decoding SMS CB pdu", e);
            return null;
        }
    }

    private static final class SmsCbConcatInfo {
        private final SmsCbHeader mHeader;
        private final SmsCbLocation mLocation;

        SmsCbConcatInfo(SmsCbHeader header, SmsCbLocation location) {
            this.mHeader = header;
            this.mLocation = location;
        }

        public int hashCode() {
            return (this.mHeader.getSerialNumber() * 31) + this.mLocation.hashCode();
        }

        public boolean equals(Object obj) {
            if (!(obj instanceof SmsCbConcatInfo)) {
                return false;
            }
            SmsCbConcatInfo other = (SmsCbConcatInfo) obj;
            return this.mHeader.getSerialNumber() == other.mHeader.getSerialNumber() && this.mLocation.equals(other.mLocation);
        }

        public boolean matchesLocation(String plmn, int lac, int cid) {
            return this.mLocation.isInLocationArea(plmn, lac, cid);
        }
    }
}
