package com.android.phone;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.CursorAdapter;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import com.android.internal.telephony.PhoneFactory;

/* JADX INFO: loaded from: classes.dex */
public class ADNList extends ListActivity {
    private static final String[] COLUMN_NAMES = {"name", "number", "emails"};
    private static final int[] VIEW_NAMES = {android.R.id.text1, android.R.id.text2};
    protected CursorAdapter mCursorAdapter;
    private TextView mEmptyText;
    protected QueryHandler mQueryHandler;
    protected Cursor mCursor = null;
    protected int mInitialSelection = -1;

    @Override // android.app.Activity
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        getWindow().requestFeature(5);
        setContentView(R.layout.adn_list);
        this.mEmptyText = (TextView) findViewById(android.R.id.empty);
        this.mQueryHandler = new QueryHandler(getContentResolver());
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        query();
    }

    @Override // android.app.Activity
    protected void onStop() {
        super.onStop();
        if (this.mCursor != null) {
            this.mCursor.deactivate();
        }
    }

    protected Uri resolveIntent() {
        Intent intent = getIntent();
        if (intent.getData() == null) {
            intent.setData(Uri.parse("content://icc/adn"));
        }
        return intent.getData();
    }

    private void query() {
        Uri uri = resolveIntent();
        this.mQueryHandler.startQuery(0, null, uri, COLUMN_NAMES, null, null, null);
        displayProgress(true);
    }

    protected void reQuery() {
        query();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setAdapter() {
        if (this.mCursorAdapter == null) {
            this.mCursorAdapter = newAdapter();
            setListAdapter(this.mCursorAdapter);
        } else {
            this.mCursorAdapter.changeCursor(this.mCursor);
        }
        if (this.mInitialSelection >= 0 && this.mInitialSelection < this.mCursorAdapter.getCount()) {
            setSelection(this.mInitialSelection);
            getListView().setFocusableInTouchMode(true);
            getListView().requestFocus();
        }
    }

    protected CursorAdapter newAdapter() {
        return new SimpleCursorAdapter(this, android.R.layout.simple_list_item_2, this.mCursor, COLUMN_NAMES, VIEW_NAMES);
    }

    protected void displayProgress(boolean loading) {
        int i;
        TextView textView = this.mEmptyText;
        if (loading) {
            i = R.string.simContacts_emptyLoading;
        } else {
            i = (!isAirplaneModeOn(this) || isSimPresent()) ? R.string.simContacts_empty : R.string.simContacts_airplaneMode;
        }
        textView.setText(i);
        getWindow().setFeatureInt(5, loading ? -1 : -2);
    }

    private static boolean isAirplaneModeOn(Context context) {
        return Settings.System.getInt(context.getContentResolver(), "airplane_mode_on", 0) != 0;
    }

    protected class QueryHandler extends AsyncQueryHandler {
        public QueryHandler(ContentResolver cr) {
            super(cr);
        }

        @Override // android.content.AsyncQueryHandler
        protected void onQueryComplete(int token, Object cookie, Cursor c) {
            ADNList.this.mCursor = c;
            ADNList.this.setAdapter();
            ADNList.this.displayProgress(false);
            ADNList.this.invalidateOptionsMenu();
        }

        @Override // android.content.AsyncQueryHandler
        protected void onInsertComplete(int token, Object cookie, Uri uri) {
            ADNList.this.displayProgress(false);
            if (uri != null) {
                ADNList.this.showAlertDialog(ADNList.this.getString(R.string.contactAddSuccess));
            } else {
                ADNList.this.showAlertDialog(ADNList.this.getString(R.string.contactAddFailed));
            }
            ADNList.this.reQuery();
        }

        @Override // android.content.AsyncQueryHandler
        protected void onUpdateComplete(int token, Object cookie, int result) {
            ADNList.this.displayProgress(false);
            if (result == 1) {
                ADNList.this.showAlertDialog(ADNList.this.getString(R.string.contactUpdateSuccess));
            } else {
                ADNList.this.showAlertDialog(ADNList.this.getString(R.string.contactUpdateFailed));
            }
            ADNList.this.reQuery();
        }

        @Override // android.content.AsyncQueryHandler
        protected void onDeleteComplete(int token, Object cookie, int result) {
            ADNList.this.displayProgress(false);
            if (result == 1) {
                ADNList.this.showAlertDialog(ADNList.this.getString(R.string.contactdeleteSuccess));
            } else {
                ADNList.this.showAlertDialog(ADNList.this.getString(R.string.contactdeleteFailed));
            }
            ADNList.this.reQuery();
        }
    }

    protected void showAlertDialog(String value) {
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle("Result...");
        alertDialog.setMessage(value);
        alertDialog.setButton("OK", new DialogInterface.OnClickListener() { // from class: com.android.phone.ADNList.1
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        alertDialog.show();
    }

    protected boolean isSimPresent() {
        return PhoneFactory.getDefaultPhone().getIccCard().hasIccCard();
    }
}
