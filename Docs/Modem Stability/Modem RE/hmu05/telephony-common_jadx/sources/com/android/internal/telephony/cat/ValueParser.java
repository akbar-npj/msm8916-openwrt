package com.android.internal.telephony.cat;

import android.R;
import android.content.res.Resources;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.uicc.IccUtils;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
abstract class ValueParser {
    ValueParser() {
    }

    static CommandDetails retrieveCommandDetails(ComprehensionTlv ctlv) throws ResultException {
        CommandDetails cmdDet = new CommandDetails();
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        try {
            cmdDet.compRequired = ctlv.isComprehensionRequired();
            cmdDet.commandNumber = rawValue[valueIndex] & 255;
            cmdDet.typeOfCommand = rawValue[valueIndex + 1] & 255;
            cmdDet.commandQualifier = rawValue[valueIndex + 2] & 255;
            return cmdDet;
        } catch (IndexOutOfBoundsException e) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }
    }

    static DeviceIdentities retrieveDeviceIdentities(ComprehensionTlv ctlv) throws ResultException {
        DeviceIdentities devIds = new DeviceIdentities();
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        try {
            devIds.sourceId = rawValue[valueIndex] & 255;
            devIds.destinationId = rawValue[valueIndex + 1] & 255;
            return devIds;
        } catch (IndexOutOfBoundsException e) {
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        }
    }

    static Duration retrieveDuration(ComprehensionTlv ctlv) throws ResultException {
        Duration.TimeUnit timeUnit = Duration.TimeUnit.SECOND;
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        try {
            Duration.TimeUnit timeUnit2 = Duration.TimeUnit.values()[rawValue[valueIndex] & 255];
            int timeInterval = rawValue[valueIndex + 1] & 255;
            return new Duration(timeInterval, timeUnit2);
        } catch (IndexOutOfBoundsException e) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }
    }

    static Item retrieveItem(ComprehensionTlv ctlv) throws ResultException {
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        int length = ctlv.getLength();
        if (length == 0) {
            return null;
        }
        int textLen = length - 1;
        try {
            int id = rawValue[valueIndex] & 255;
            String text = IccUtils.adnStringFieldToString(rawValue, valueIndex + 1, textLen);
            Item item = new Item(id, text);
            return item;
        } catch (IndexOutOfBoundsException e) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }
    }

    static int retrieveItemId(ComprehensionTlv ctlv) throws ResultException {
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        try {
            int id = rawValue[valueIndex] & 255;
            return id;
        } catch (IndexOutOfBoundsException e) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }
    }

    static IconId retrieveIconId(ComprehensionTlv ctlv) throws ResultException {
        IconId id = new IconId();
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        int valueIndex2 = valueIndex + 1;
        try {
            id.selfExplanatory = (rawValue[valueIndex] & 255) == 0;
            id.recordNumber = rawValue[valueIndex2] & 255;
            return id;
        } catch (IndexOutOfBoundsException e) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }
    }

    static ItemsIconId retrieveItemsIconId(ComprehensionTlv ctlv) throws ResultException {
        CatLog.d("ValueParser", "retrieveItemsIconId:");
        ItemsIconId id = new ItemsIconId();
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        int numOfItems = ctlv.getLength() - 1;
        id.recordNumbers = new int[numOfItems];
        int valueIndex2 = valueIndex + 1;
        try {
            id.selfExplanatory = (rawValue[valueIndex] & 255) == 0;
            int index = 0;
            while (index < numOfItems) {
                int index2 = index + 1;
                int valueIndex3 = valueIndex2 + 1;
                try {
                    id.recordNumbers[index] = rawValue[valueIndex2];
                    index = index2;
                    valueIndex2 = valueIndex3;
                } catch (IndexOutOfBoundsException e) {
                    throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
                }
            }
            return id;
        } catch (IndexOutOfBoundsException e2) {
        }
    }

    static List<TextAttribute> retrieveTextAttribute(ComprehensionTlv ctlv) throws ResultException {
        ArrayList<TextAttribute> lst = new ArrayList<>();
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        int length = ctlv.getLength();
        if (length != 0) {
            int itemCount = length / 4;
            int i = 0;
            while (i < itemCount) {
                try {
                    int start = rawValue[valueIndex] & 255;
                    int textLength = rawValue[valueIndex + 1] & 255;
                    int format = rawValue[valueIndex + 2] & 255;
                    int colorValue = rawValue[valueIndex + 3] & 255;
                    int alignValue = format & 3;
                    TextAlignment align = TextAlignment.fromInt(alignValue);
                    int sizeValue = (format >> 2) & 3;
                    FontSize size = FontSize.fromInt(sizeValue);
                    if (size == null) {
                        size = FontSize.NORMAL;
                    }
                    boolean bold = (format & 16) != 0;
                    boolean italic = (format & 32) != 0;
                    boolean underlined = (format & 64) != 0;
                    boolean strikeThrough = (format & 128) != 0;
                    TextColor color = TextColor.fromInt(colorValue);
                    TextAttribute attr = new TextAttribute(start, textLength, align, size, bold, italic, underlined, strikeThrough, color);
                    lst.add(attr);
                    i++;
                    valueIndex += 4;
                } catch (IndexOutOfBoundsException e) {
                    throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
                }
            }
            return lst;
        }
        return null;
    }

    static String retrieveAlphaId(ComprehensionTlv ctlv) throws ResultException {
        boolean noAlphaUsrCnf;
        if (ctlv != null) {
            byte[] rawValue = ctlv.getRawValue();
            int valueIndex = ctlv.getValueIndex();
            int length = ctlv.getLength();
            if (length != 0) {
                try {
                    return IccUtils.adnStringFieldToString(rawValue, valueIndex, length);
                } catch (IndexOutOfBoundsException e) {
                    throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
                }
            }
            CatLog.d("ValueParser", "Alpha Id length=" + length);
            return null;
        }
        Resources resource = Resources.getSystem();
        try {
            noAlphaUsrCnf = resource.getBoolean(R.bool.config_bg_current_drain_event_duration_based_threshold_enabled);
        } catch (Resources.NotFoundException e2) {
            noAlphaUsrCnf = false;
        }
        if (noAlphaUsrCnf) {
            return null;
        }
        return "Default Message";
    }

    static String retrieveTextString(ComprehensionTlv ctlv) throws ResultException {
        String text;
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        int textLen = ctlv.getLength();
        if (textLen == 0) {
            return null;
        }
        int textLen2 = textLen - 1;
        try {
            byte codingScheme = (byte) (rawValue[valueIndex] & 12);
            if (codingScheme == 0) {
                text = GsmAlphabet.gsm7BitPackedToString(rawValue, valueIndex + 1, (textLen2 * 8) / 7);
            } else if (codingScheme == 4) {
                text = GsmAlphabet.gsm8BitUnpackedToString(rawValue, valueIndex + 1, textLen2);
            } else if (codingScheme == 8) {
                text = new String(rawValue, valueIndex + 1, textLen2, "UTF-16");
            } else {
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
            }
            return text;
        } catch (UnsupportedEncodingException e) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        } catch (IndexOutOfBoundsException e2) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD);
        }
    }

    static int retrieveTarget(ComprehensionTlv ctlv) throws ResultException {
        ActivateDescriptor activateDesc = new ActivateDescriptor();
        byte[] rawValue = ctlv.getRawValue();
        int valueIndex = ctlv.getValueIndex();
        try {
            activateDesc.target = rawValue[valueIndex] & 255;
            return activateDesc.target;
        } catch (IndexOutOfBoundsException e) {
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING);
        }
    }
}
