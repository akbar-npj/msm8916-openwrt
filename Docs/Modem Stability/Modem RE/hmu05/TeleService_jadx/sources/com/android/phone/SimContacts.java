package com.android.phone;

import android.accounts.Account;
import android.app.ActionBar;
import android.app.ProgressDialog;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import java.util.ArrayList;

/* JADX INFO: loaded from: classes.dex */
public class SimContacts extends ADNList {
    static final ContentValues sEmptyContentValues = new ContentValues();
    private Account mAccount;
    private ProgressDialog mProgressDialog;
    protected boolean mIsForeground = false;
    private Handler mHandler = new Handler() { // from class: com.android.phone.SimContacts.1
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 9:
                    SimContacts.this.reQuery();
                    int result = ((Integer) msg.obj).intValue();
                    if (result == 1) {
                        SimContacts.this.showAlertDialog(SimContacts.this.getString(R.string.allContactdeleteSuccess));
                    } else {
                        SimContacts.this.showAlertDialog(SimContacts.this.getString(R.string.allContactdeleteFailed));
                    }
                    break;
            }
        }
    };

    private static class NamePhoneTypePair {
        final String name;
        final int phoneType;

        public NamePhoneTypePair(String nameWithPhoneType) {
            int nameLen = nameWithPhoneType.length();
            if (nameLen - 2 >= 0 && nameWithPhoneType.charAt(nameLen - 2) == '/') {
                char c = Character.toUpperCase(nameWithPhoneType.charAt(nameLen - 1));
                if (c == 'W') {
                    this.phoneType = 3;
                } else if (c == 'M' || c == 'O') {
                    this.phoneType = 2;
                } else if (c == 'H') {
                    this.phoneType = 1;
                } else {
                    this.phoneType = 7;
                }
                this.name = nameWithPhoneType.substring(0, nameLen - 2);
                return;
            }
            this.phoneType = 7;
            this.name = nameWithPhoneType;
        }
    }

    private class ImportAllSimContactsThread extends Thread implements DialogInterface.OnCancelListener, DialogInterface.OnClickListener {
        boolean mCanceled;

        public ImportAllSimContactsThread() {
            super("ImportAllSimContactsThread");
            this.mCanceled = false;
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            new ContentValues();
            ContentResolver resolver = SimContacts.this.getContentResolver();
            SimContacts.this.mCursor.moveToPosition(-1);
            while (!this.mCanceled && SimContacts.this.mCursor.moveToNext()) {
                SimContacts.actuallyImportOneSimContact(SimContacts.this.mCursor, resolver, SimContacts.this.mAccount);
                SimContacts.this.mProgressDialog.incrementProgressBy(1);
            }
            if (SimContacts.this.mIsForeground) {
                SimContacts.this.mProgressDialog.dismiss();
            }
            SimContacts.this.finish();
        }

        @Override // android.content.DialogInterface.OnCancelListener
        public void onCancel(DialogInterface dialog) {
            this.mCanceled = true;
        }

        @Override // android.content.DialogInterface.OnClickListener
        public void onClick(DialogInterface dialog, int which) {
            if (which == -2) {
                this.mCanceled = true;
                SimContacts.this.mProgressDialog.dismiss();
            } else {
                Log.e("SimContacts", "Unknown button event has come: " + dialog.toString());
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void actuallyImportOneSimContact(Cursor cursor, ContentResolver resolver, Account account) {
        String[] emailAddressArray;
        NamePhoneTypePair namePhoneTypePair = new NamePhoneTypePair(cursor.getString(0));
        String name = namePhoneTypePair.name;
        int phoneType = namePhoneTypePair.phoneType;
        String phoneNumber = cursor.getString(1);
        String emailAddresses = cursor.getString(2);
        if (!TextUtils.isEmpty(emailAddresses)) {
            emailAddressArray = emailAddresses.split(",");
        } else {
            emailAddressArray = null;
        }
        ArrayList<ContentProviderOperation> operationList = new ArrayList<>();
        ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI);
        if (account != null) {
            builder.withValue("account_name", account.name);
            builder.withValue("account_type", account.type);
        } else {
            builder.withValues(sEmptyContentValues);
        }
        operationList.add(builder.build());
        ContentProviderOperation.Builder builder2 = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
        builder2.withValueBackReference("raw_contact_id", 0);
        builder2.withValue("mimetype", "vnd.android.cursor.item/name");
        builder2.withValue("data1", name);
        operationList.add(builder2.build());
        ContentProviderOperation.Builder builder3 = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
        builder3.withValueBackReference("raw_contact_id", 0);
        builder3.withValue("mimetype", "vnd.android.cursor.item/phone_v2");
        builder3.withValue("data2", Integer.valueOf(phoneType));
        builder3.withValue("data1", phoneNumber);
        builder3.withValue("is_primary", 1);
        operationList.add(builder3.build());
        if (emailAddresses != null) {
            String[] arr$ = emailAddressArray;
            for (String emailAddress : arr$) {
                ContentProviderOperation.Builder builder4 = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
                builder4.withValueBackReference("raw_contact_id", 0);
                builder4.withValue("mimetype", "vnd.android.cursor.item/email_v2");
                builder4.withValue("data2", 4);
                builder4.withValue("data1", emailAddress);
                operationList.add(builder4.build());
            }
        }
        if (0 != 0) {
            ContentProviderOperation.Builder builder5 = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
            builder5.withValueBackReference("raw_contact_id", 0);
            builder5.withValue("mimetype", "vnd.android.cursor.item/group_membership");
            builder5.withValue("group_sourceid", null);
            operationList.add(builder5.build());
        }
        try {
            resolver.applyBatch("com.android.contacts", operationList);
        } catch (OperationApplicationException e) {
            Log.e("SimContacts", String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (RemoteException e2) {
            Log.e("SimContacts", String.format("%s: %s", e2.toString(), e2.getMessage()));
        }
    }

    private void importOneSimContact(int position) {
        ContentResolver resolver = getContentResolver();
        if (this.mCursor.moveToPosition(position)) {
            actuallyImportOneSimContact(this.mCursor, resolver, this.mAccount);
        } else {
            Log.e("SimContacts", "Failed to move the cursor to the position \"" + position + "\"");
        }
    }

    @Override // com.android.phone.ADNList, android.app.Activity
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Intent intent = getIntent();
        if (intent != null) {
            String accountName = intent.getStringExtra("account_name");
            String accountType = intent.getStringExtra("account_type");
            if (!TextUtils.isEmpty(accountName) && !TextUtils.isEmpty(accountType)) {
                this.mAccount = new Account(accountName, accountType);
            }
        }
        registerForContextMenu(getListView());
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override // com.android.phone.ADNList, android.app.Activity
    protected void onResume() {
        super.onResume();
        this.mIsForeground = true;
    }

    @Override // android.app.Activity
    protected void onPause() {
        super.onPause();
        this.mIsForeground = false;
    }

    @Override // com.android.phone.ADNList
    protected CursorAdapter newAdapter() {
        return new SimpleCursorAdapter(this, R.layout.sim_import_list_entry, this.mCursor, new String[]{"name"}, new int[]{android.R.id.text1});
    }

    @Override // com.android.phone.ADNList
    protected Uri resolveIntent() {
        Intent intent = getIntent();
        intent.setData(Uri.parse("content://icc/adn"));
        if ("android.intent.action.PICK".equals(intent.getAction())) {
            this.mInitialSelection = intent.getIntExtra("index", 0) - 1;
        }
        return intent.getData();
    }

    @Override // android.app.Activity
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, 2, 0, R.string.importAllSimEntries);
        menu.add(0, 3, 0, R.string.deleteAllSimEntries);
        menu.add(0, 4, 0, R.string.addSimEntries);
        return true;
    }

    @Override // android.app.Activity
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(2);
        if (item != null) {
            item.setVisible(this.mCursor != null && this.mCursor.getCount() > 0);
        }
        MenuItem item2 = menu.findItem(3);
        if (item2 != null) {
            item2.setVisible(this.mCursor != null && this.mCursor.getCount() > 0);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override // android.app.Activity
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 2:
                CharSequence title = getString(R.string.importAllSimEntries);
                CharSequence message = getString(R.string.importingSimContacts);
                ImportAllSimContactsThread thread = new ImportAllSimContactsThread();
                if (this.mCursor == null) {
                    Log.e("SimContacts", "cursor is null. Ignore silently.");
                } else {
                    prepareProgressDialog(title, message);
                    this.mProgressDialog.setButton(-2, getString(R.string.cancel), thread);
                    this.mProgressDialog.show();
                    thread.start();
                    return true;
                }
                break;
            case 3:
                CharSequence title2 = getString(R.string.deleteAllSimEntries);
                CharSequence message2 = getString(R.string.deleteSimContacts);
                DeleteAllSimContactsThread deleteThread = new DeleteAllSimContactsThread();
                if (this.mCursor == null) {
                    showAlertDialog(getString(R.string.cursorError));
                } else {
                    prepareProgressDialog(title2, message2);
                    this.mProgressDialog.setButton(-2, getString(R.string.cancel), deleteThread);
                    this.mProgressDialog.show();
                    deleteThread.start();
                    return true;
                }
                break;
            case 4:
                showContactScreen(null, null, 1);
                return true;
            case android.R.id.home:
                Intent intent = new Intent();
                intent.setClassName("com.android.contacts", "com.android.contacts.activities.PeopleActivity");
                intent.addFlags(67108864);
                startActivity(intent);
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    void prepareProgressDialog(CharSequence title, CharSequence message) {
        this.mProgressDialog = new ProgressDialog(this);
        this.mProgressDialog.setTitle(title);
        this.mProgressDialog.setMessage(message);
        this.mProgressDialog.setProgressStyle(1);
        this.mProgressDialog.setProgress(0);
        this.mProgressDialog.setMax(this.mCursor.getCount());
    }

    @Override // android.app.Activity
    public boolean onContextItemSelected(MenuItem item) {
        ContextMenu.ContextMenuInfo menuInfo = item.getMenuInfo();
        if (menuInfo instanceof AdapterView.AdapterContextMenuInfo) {
            int position = ((AdapterView.AdapterContextMenuInfo) menuInfo).position;
            switch (item.getItemId()) {
                case 1:
                    importOneSimContact(position);
                    return true;
                case 2:
                case 3:
                case 4:
                default:
                    return super.onContextItemSelected(item);
                case 5:
                    editOneSimContact(position);
                    return true;
                case 6:
                    smsToNumber(position);
                    return true;
                case 7:
                    dialNumber(position);
                    return true;
                case 8:
                    deleteOneSimContact(position);
                    return true;
            }
        }
        return super.onContextItemSelected(item);
    }

    private void smsToNumber(int position) {
        if (this.mCursor.moveToPosition(position)) {
            String phoneNumber = this.mCursor.getString(1);
            Intent intent = new Intent("android.intent.action.SENDTO", Uri.fromParts("smsto", PhoneNumberUtils.formatNumber(phoneNumber), null));
            startActivity(intent);
            finish();
            return;
        }
        showAlertDialog(getString(R.string.cursorError));
    }

    private void dialNumber(int position) {
        if (this.mCursor.moveToPosition(position)) {
            String phoneNumber = this.mCursor.getString(1);
            if (phoneNumber == null || !TextUtils.isGraphic(phoneNumber)) {
                Log.e("SimContacts", " There is no number in contact ...");
            }
            Intent intent = new Intent("android.intent.action.CALL_PRIVILEGED", Uri.fromParts("tel", phoneNumber, null));
            intent.setFlags(276824064);
            startActivity(intent);
            finish();
            return;
        }
        showAlertDialog(getString(R.string.cursorError));
    }

    private class DeleteAllSimContactsThread extends Thread implements DialogInterface.OnCancelListener, DialogInterface.OnClickListener {
        boolean mCanceled;

        public DeleteAllSimContactsThread() {
            super("deleteAllSimContactsThread");
            this.mCanceled = false;
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            int result = 1;
            SimContacts.this.mCursor.moveToPosition(-1);
            while (!this.mCanceled && SimContacts.this.mCursor.moveToNext()) {
                result &= SimContacts.this.actuallyDeleteOneSimContact(SimContacts.this.mCursor);
                SimContacts.this.mProgressDialog.incrementProgressBy(1);
            }
            SimContacts.this.mProgressDialog.dismiss();
            Message message = Message.obtain(SimContacts.this.mHandler, 9, Integer.valueOf(result));
            SimContacts.this.mHandler.sendMessage(message);
        }

        @Override // android.content.DialogInterface.OnCancelListener
        public void onCancel(DialogInterface dialog) {
            this.mCanceled = true;
        }

        @Override // android.content.DialogInterface.OnClickListener
        public void onClick(DialogInterface dialog, int which) {
            if (which == -2) {
                this.mCanceled = true;
                SimContacts.this.mProgressDialog.dismiss();
            } else {
                Log.e("SimContacts", "Unknown button event has come: " + dialog.toString());
            }
        }
    }

    private void deleteOneSimContact(int position) {
        if (this.mCursor.moveToPosition(position)) {
            NamePhoneTypePair namePhoneTypePair = new NamePhoneTypePair(this.mCursor.getString(0));
            String name = namePhoneTypePair.name;
            int i = namePhoneTypePair.phoneType;
            String phoneNumber = this.mCursor.getString(1);
            Uri uri = getUri();
            if (uri == null) {
                Log.e("SimContacts", "deleteOneSimContact: uri is null, return!!!");
                return;
            } else {
                this.mQueryHandler.startDelete(3, null, uri, "tag=" + name + " AND number=" + phoneNumber, null);
                displayProgress(true);
                return;
            }
        }
        showAlertDialog(getString(R.string.cursorError));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int actuallyDeleteOneSimContact(Cursor cursor) {
        NamePhoneTypePair namePhoneTypePair = new NamePhoneTypePair(cursor.getString(0));
        String name = namePhoneTypePair.name;
        int i = namePhoneTypePair.phoneType;
        String phoneNumber = cursor.getString(1);
        Uri uri = getUri();
        if (uri != null) {
            int result = getContentResolver().delete(uri, "tag=" + name + " AND number=" + phoneNumber, null);
            return result;
        }
        Log.e("SimContacts", "actuallyDeleteOneSimContact: uri is null!!!");
        return -1;
    }

    private void editOneSimContact(int position) {
        if (this.mCursor.moveToPosition(position)) {
            NamePhoneTypePair namePhoneTypePair = new NamePhoneTypePair(this.mCursor.getString(0));
            String name = namePhoneTypePair.name;
            int i = namePhoneTypePair.phoneType;
            String phoneNumber = this.mCursor.getString(1);
            showContactScreen(name, phoneNumber, 2);
            return;
        }
        showAlertDialog(getString(R.string.cursorError));
    }

    private void showContactScreen(String name, String phoneNumber, int requestCode) {
        Intent intent = new Intent();
        intent.setClassName("com.android.phone", "com.android.phone.ContactScreenActivity");
        intent.putExtra("NAME", name);
        intent.putExtra("PHONE", phoneNumber);
        startActivityForResult(intent, requestCode);
    }

    @Override // android.app.Activity
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        Uri uri = getUri();
        if (uri == null) {
            Log.e("SimContacts", "onActivityResult: uri is null, return!!!");
            return;
        }
        ContentValues values = new ContentValues();
        if (resultCode == -1 && requestCode == 1) {
            String name = intent.getStringExtra("NEWNAME");
            String number = intent.getStringExtra("NEWPHONE");
            values.put("tag", name);
            values.put("number", number);
            this.mQueryHandler.startInsert(1, null, uri, values);
            displayProgress(true);
            return;
        }
        if (resultCode == -1 && requestCode == 2) {
            String oldName = intent.getStringExtra("NAME");
            String oldNumber = intent.getStringExtra("PHONE");
            String newName = intent.getStringExtra("NEWNAME");
            String newNumber = intent.getStringExtra("NEWPHONE");
            values.put("tag", oldName);
            values.put("number", oldNumber);
            values.put("newTag", newName);
            values.put("newNumber", newNumber);
            this.mQueryHandler.startUpdate(2, null, uri, values, null, null);
            displayProgress(true);
        }
    }

    @Override // android.app.Activity, android.view.View.OnCreateContextMenuListener
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (menuInfo instanceof AdapterView.AdapterContextMenuInfo) {
            AdapterView.AdapterContextMenuInfo itemInfo = (AdapterView.AdapterContextMenuInfo) menuInfo;
            TextView textView = (TextView) itemInfo.targetView.findViewById(android.R.id.text1);
            if (textView != null) {
                menu.setHeaderTitle(textView.getText());
            }
            menu.add(0, 1, 0, R.string.importSimEntry);
            menu.add(0, 5, 0, R.string.editContact);
            menu.add(0, 6, 0, R.string.sendSms);
            menu.add(0, 7, 0, R.string.dial);
            menu.add(0, 8, 0, R.string.delete);
        }
    }

    @Override // android.app.ListActivity
    public void onListItemClick(ListView l, View v, int position, long id) {
        importOneSimContact(position);
    }

    @Override // android.app.Activity, android.view.KeyEvent.Callback
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case 5:
                if (this.mCursor != null && this.mCursor.moveToPosition(getSelectedItemPosition())) {
                    String phoneNumber = this.mCursor.getString(1);
                    if (phoneNumber == null || !TextUtils.isGraphic(phoneNumber)) {
                        return true;
                    }
                    Intent intent = new Intent("android.intent.action.CALL_PRIVILEGED", Uri.fromParts("tel", phoneNumber, null));
                    intent.setFlags(276824064);
                    startActivity(intent);
                    finish();
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    protected Uri getUri() {
        return Uri.parse("content://icc/adn");
    }
}
