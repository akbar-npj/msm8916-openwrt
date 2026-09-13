package com.android.phone;

import android.app.ActionBar;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

/* JADX INFO: loaded from: classes.dex */
public class FdnList extends ADNList {
    @Override // com.android.phone.ADNList, android.app.Activity
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override // com.android.phone.ADNList
    protected Uri resolveIntent() {
        Intent intent = getIntent();
        intent.setData(Uri.parse("content://icc/fdn"));
        return intent.getData();
    }

    @Override // android.app.Activity
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        Resources r = getResources();
        menu.add(0, 1, 0, r.getString(R.string.menu_add)).setIcon(android.R.drawable.ic_menu_add);
        menu.add(0, 2, 0, r.getString(R.string.menu_edit)).setIcon(android.R.drawable.ic_menu_edit);
        menu.add(0, 3, 0, r.getString(R.string.menu_delete)).setIcon(android.R.drawable.ic_menu_delete);
        return true;
    }

    @Override // android.app.Activity
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        boolean hasSelection = getSelectedItemPosition() >= 0;
        menu.findItem(1).setVisible(true);
        menu.findItem(2).setVisible(hasSelection);
        menu.findItem(3).setVisible(hasSelection);
        return true;
    }

    @Override // android.app.Activity
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case 1:
                addContact();
                return true;
            case 2:
                editSelected();
                return true;
            case 3:
                deleteSelected();
                return true;
            case android.R.id.home:
                Intent intent = new Intent(this, (Class<?>) FdnSetting.class);
                intent.setAction("android.intent.action.MAIN");
                intent.addFlags(67108864);
                startActivity(intent);
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(menuItem);
        }
    }

    @Override // android.app.ListActivity
    public void onListItemClick(ListView l, View v, int position, long id) {
        editSelected(position);
    }

    protected void addContact() {
        Intent intent = new Intent();
        intent.setClass(this, EditFdnContactScreen.class);
        startActivity(intent);
    }

    private void editSelected() {
        editSelected(getSelectedItemPosition());
    }

    protected void editSelected(int i) {
        if (this.mCursor.moveToPosition(i)) {
            String string = this.mCursor.getString(0);
            String string2 = this.mCursor.getString(1);
            Intent intent = new Intent();
            intent.setClass(this, EditFdnContactScreen.class);
            intent.putExtra("name", string);
            intent.putExtra("number", string2);
            startActivity(intent);
        }
    }

    protected void deleteSelected() {
        if (this.mCursor.moveToPosition(getSelectedItemPosition())) {
            String string = this.mCursor.getString(0);
            String string2 = this.mCursor.getString(1);
            Intent intent = new Intent();
            intent.setClass(this, DeleteFdnContactScreen.class);
            intent.putExtra("name", string);
            intent.putExtra("number", string2);
            startActivity(intent);
        }
    }
}
