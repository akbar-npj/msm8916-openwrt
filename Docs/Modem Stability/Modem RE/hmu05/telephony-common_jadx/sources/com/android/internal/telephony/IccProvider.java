package com.android.internal.telephony;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Telephony;
import android.telephony.Rlog;
import android.text.TextUtils;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.IccConstants;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class IccProvider extends ContentProvider {
    private static final int ADN = 1;
    private static final boolean DBG = false;
    private static final int FDN = 2;
    private static final int SDN = 3;
    public static final String STR_NEW_ANRS = "newAnrs";
    public static final String STR_NEW_EMAILS = "newEmails";
    public static final String STR_NEW_NUMBER = "newNumber";
    public static final String STR_NEW_TAG = "newTag";
    public static final String STR_NUMBER = "number";
    public static final String STR_PIN2 = "pin2";
    public static final String STR_TAG = "tag";
    private static final String TAG = "IccProvider";
    public static final String STR_EMAILS = "emails";
    public static final String STR_ANRS = "anrs";
    protected static final String[] ADDRESS_BOOK_COLUMN_NAMES = {"name", "number", STR_EMAILS, STR_ANRS, Telephony.MmsSms.WordsTable.ID};
    private static final UriMatcher URL_MATCHER = new UriMatcher(-1);

    static {
        URL_MATCHER.addURI("icc", "adn", 1);
        URL_MATCHER.addURI("icc", "fdn", 2);
        URL_MATCHER.addURI("icc", "sdn", 3);
    }

    @Override // android.content.ContentProvider
    public boolean onCreate() {
        return true;
    }

    @Override // android.content.ContentProvider
    public Cursor query(Uri url, String[] projection, String selection, String[] selectionArgs, String sort) {
        switch (URL_MATCHER.match(url)) {
            case 1:
                return loadFromEf(28474);
            case 2:
                return loadFromEf(IccConstants.EF_FDN);
            case 3:
                return loadFromEf(IccConstants.EF_SDN);
            default:
                throw new IllegalArgumentException("Unknown URL " + url);
        }
    }

    @Override // android.content.ContentProvider
    public String getType(Uri url) {
        switch (URL_MATCHER.match(url)) {
            case 1:
            case 2:
            case 3:
                return "vnd.android.cursor.dir/sim-contact";
            default:
                throw new IllegalArgumentException("Unknown URL " + url);
        }
    }

    @Override // android.content.ContentProvider
    public Uri insert(Uri url, ContentValues initialValues) {
        int efType;
        String pin2 = null;
        int match = URL_MATCHER.match(url);
        switch (match) {
            case 1:
                efType = 28474;
                break;
            case 2:
                efType = IccConstants.EF_FDN;
                pin2 = initialValues.getAsString(STR_PIN2);
                break;
            default:
                throw new UnsupportedOperationException("Cannot insert into URL: " + url);
        }
        String tag = initialValues.getAsString(STR_TAG);
        String number = initialValues.getAsString("number");
        String emails = initialValues.getAsString(STR_EMAILS);
        String anrs = initialValues.getAsString(STR_ANRS);
        ContentValues mValues = new ContentValues();
        mValues.put(STR_TAG, "");
        mValues.put("number", "");
        mValues.put(STR_EMAILS, "");
        mValues.put(STR_ANRS, "");
        mValues.put(STR_NEW_TAG, tag);
        mValues.put(STR_NEW_NUMBER, number);
        mValues.put(STR_NEW_EMAILS, emails);
        mValues.put(STR_NEW_ANRS, anrs);
        boolean success = updateIccRecordInEf(efType, mValues, pin2);
        if (!success) {
            return null;
        }
        StringBuilder buf = new StringBuilder("content://icc/");
        switch (match) {
            case 1:
                buf.append("adn/");
                break;
            case 2:
                buf.append("fdn/");
                break;
        }
        buf.append(0);
        Uri uri = Uri.parse(buf.toString());
        getContext().getContentResolver().notifyChange(url, null);
        return uri;
    }

    protected String normalizeValue(String inVal) {
        int len = inVal.length();
        if (len != 0) {
            String retVal = inVal;
            if (inVal.charAt(0) == '\'' && inVal.charAt(len - 1) == '\'') {
                retVal = inVal.substring(1, len - 1);
            }
            return retVal;
        }
        return inVal;
    }

    @Override // android.content.ContentProvider
    public int delete(Uri url, String where, String[] whereArgs) {
        int efType;
        int match = URL_MATCHER.match(url);
        switch (match) {
            case 1:
                efType = 28474;
                break;
            case 2:
                efType = IccConstants.EF_FDN;
                break;
            default:
                throw new UnsupportedOperationException("Cannot insert into URL: " + url);
        }
        String tag = null;
        String number = null;
        String emails = null;
        String anrs = null;
        String pin2 = null;
        String[] tokens = where.split("AND");
        int n = tokens.length;
        while (true) {
            n--;
            if (n >= 0) {
                String param = tokens[n];
                String[] pair = param.split("=", 2);
                if (pair.length != 2) {
                    Rlog.e(TAG, "resolve: bad whereClause parameter: " + param);
                } else {
                    String key = pair[0].trim();
                    String val = pair[1].trim();
                    if (STR_TAG.equals(key)) {
                        tag = normalizeValue(val);
                    } else if ("number".equals(key)) {
                        number = normalizeValue(val);
                    } else if (STR_EMAILS.equals(key)) {
                        emails = normalizeValue(val);
                    } else if (STR_ANRS.equals(key)) {
                        anrs = normalizeValue(val);
                    } else if (STR_PIN2.equals(key)) {
                        pin2 = normalizeValue(val);
                    }
                }
            } else {
                ContentValues mValues = new ContentValues();
                mValues.put(STR_TAG, tag);
                mValues.put("number", number);
                mValues.put(STR_EMAILS, emails);
                mValues.put(STR_ANRS, anrs);
                mValues.put(STR_NEW_TAG, "");
                mValues.put(STR_NEW_NUMBER, "");
                mValues.put(STR_NEW_EMAILS, "");
                mValues.put(STR_NEW_ANRS, "");
                if (efType == 2 && TextUtils.isEmpty(pin2)) {
                    return 0;
                }
                boolean success = updateIccRecordInEf(efType, mValues, pin2);
                if (!success) {
                    return 0;
                }
                getContext().getContentResolver().notifyChange(url, null);
                return 1;
            }
        }
    }

    @Override // android.content.ContentProvider
    public int update(Uri url, ContentValues values, String where, String[] whereArgs) {
        int efType;
        String pin2 = null;
        int match = URL_MATCHER.match(url);
        switch (match) {
            case 1:
                efType = 28474;
                break;
            case 2:
                efType = IccConstants.EF_FDN;
                pin2 = values.getAsString(STR_PIN2);
                break;
            default:
                throw new UnsupportedOperationException("Cannot insert into URL: " + url);
        }
        values.getAsString(STR_TAG);
        values.getAsString("number");
        values.getAsString(STR_NEW_TAG);
        values.getAsString(STR_NEW_NUMBER);
        boolean success = updateIccRecordInEf(efType, values, pin2);
        if (!success) {
            return 0;
        }
        getContext().getContentResolver().notifyChange(url, null);
        return 1;
    }

    private MatrixCursor loadFromEf(int efType) {
        List<AdnRecord> adnRecords = null;
        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                adnRecords = iccIpb.getAdnRecordsInEf(efType);
            }
        } catch (RemoteException e) {
        } catch (SecurityException e2) {
        }
        if (adnRecords != null) {
            int N = adnRecords.size();
            MatrixCursor cursor = new MatrixCursor(ADDRESS_BOOK_COLUMN_NAMES, N);
            for (int i = 0; i < N; i++) {
                loadRecord(adnRecords.get(i), cursor, i);
            }
            return cursor;
        }
        Rlog.w(TAG, "Cannot load ADN records");
        return new MatrixCursor(ADDRESS_BOOK_COLUMN_NAMES);
    }

    private boolean updateIccRecordInEf(int efType, ContentValues values, String pin2) {
        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(ServiceManager.getService("simphonebook"));
            if (iccIpb == null) {
                return false;
            }
            boolean success = iccIpb.updateAdnRecordsWithContentValuesInEfBySearch(efType, values, pin2);
            return success;
        } catch (RemoteException e) {
            return false;
        } catch (SecurityException e2) {
            return false;
        }
    }

    protected void loadRecord(AdnRecord record, MatrixCursor cursor, int id) {
        if (!record.isEmpty()) {
            Object[] contact = new Object[5];
            String alphaTag = record.getAlphaTag();
            String number = record.getNumber();
            String[] anrs = record.getAdditionalNumbers();
            contact[0] = alphaTag;
            contact[1] = number;
            String[] emails = record.getEmails();
            if (emails != null) {
                StringBuilder emailString = new StringBuilder();
                for (String email : emails) {
                    emailString.append(email);
                    emailString.append(",");
                }
                contact[2] = emailString.toString();
            }
            if (anrs != null) {
                StringBuilder anrString = new StringBuilder();
                for (String anr : anrs) {
                    anrString.append(anr);
                    anrString.append("&");
                }
                contact[3] = anrString.toString();
            }
            contact[4] = Integer.valueOf(id);
            cursor.addRow(contact);
        }
    }

    protected void log(String msg) {
        Rlog.d(TAG, "[IccProvider] " + msg);
    }
}
