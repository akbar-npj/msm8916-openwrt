package com.android.phone;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemProperties;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/* JADX INFO: loaded from: classes.dex */
public class EmergencyCallList extends ListActivity {
    private SimpleAdapter mAdapter;
    private StringBuilder mAddNumbers;
    private int mDefaultLength;
    private String mDefaultNumbers;
    private AlertDialog mDialog;
    private ArrayList<HashMap<String, Object>> mNumberList = new ArrayList<>();

    @Override // android.app.Activity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.emergency_call_list);
        getDefaultNumberList();
        this.mAdapter = new SimpleAdapter(this, this.mNumberList, android.R.layout.simple_list_item_1, new String[]{"ItemText"}, new int[]{android.R.id.text1});
        setListAdapter(this.mAdapter);
    }

    private void getDefaultNumberList() {
        this.mDefaultNumbers = SystemProperties.get("ril.ecclist");
        this.mDefaultLength = this.mDefaultNumbers.split(",").length;
        String[] arr$ = this.mDefaultNumbers.split(",");
        for (String eccNum : arr$) {
            HashMap<String, Object> map = new HashMap<>();
            map.put("ItemText", eccNum);
            if (this.mNumberList.contains(map)) {
                this.mDefaultLength--;
            } else {
                this.mNumberList.add(map);
            }
        }
        Log.d("EmergencyCallList", "default ecc Numbers " + this.mDefaultNumbers);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void refreshNumberList() {
        this.mNumberList.subList(this.mDefaultLength, this.mNumberList.size()).clear();
        String addNumbers = SystemProperties.get("persist.radio.user.add.eccList");
        if (addNumbers != null && addNumbers.length() > 0) {
            Log.d("EmergencyCallList", "add  ecc Numbers " + addNumbers);
            String[] arr$ = addNumbers.split(",");
            for (String eccNumber : arr$) {
                HashMap<String, Object> map = new HashMap<>();
                map.put("ItemText", eccNumber);
                if (!this.mNumberList.contains(map)) {
                    this.mNumberList.add(map);
                }
            }
        }
        this.mAdapter.notifyDataSetChanged();
    }

    @Override // android.app.Activity
    public void onResume() {
        super.onResume();
        refreshNumberList();
    }

    @Override // android.app.ListActivity
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final String number = (String) this.mNumberList.get(position).get("ItemText");
        builder.setTitle(R.string.emergency_call_number);
        builder.setMessage(number);
        builder.setPositiveButton(R.string.emergency_call, new DialogInterface.OnClickListener() { // from class: com.android.phone.EmergencyCallList.1
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                if (number != null) {
                    Intent intent = new Intent("android.intent.action.CALL_EMERGENCY");
                    intent.setData(Uri.fromParts("tel", number, null));
                    intent.setFlags(268435456);
                    EmergencyCallList.this.startActivity(intent);
                    EmergencyCallList.this.finish();
                }
            }
        });
        if (this.mDefaultLength > position) {
            builder.setNegativeButton(R.string.cancel, (DialogInterface.OnClickListener) null);
        } else {
            builder.setNegativeButton(R.string.delete, new DialogInterface.OnClickListener() { // from class: com.android.phone.EmergencyCallList.2
                @Override // android.content.DialogInterface.OnClickListener
                public void onClick(DialogInterface dialog, int which) {
                    if (number != null) {
                        EmergencyCallList.this.deleteEmergencyNumber(number);
                    }
                }
            });
        }
        builder.create().show();
    }

    @Override // android.app.Activity
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.emergency_call_list_menu, menu);
        return true;
    }

    @Override // android.app.Activity
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.add_emergency_number /* 2131165287 */:
                showDialog(0);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override // android.app.Activity
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case 0:
                LayoutInflater inflater = LayoutInflater.from(this);
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                View view = inflater.inflate(R.layout.new_emergency_call_number, (ViewGroup) null);
                final EditText edit = (EditText) view.findViewById(R.id.edit);
                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() { // from class: com.android.phone.EmergencyCallList.3
                    @Override // android.content.DialogInterface.OnClickListener
                    public void onClick(DialogInterface dialog, int which) {
                        String number = edit.getText().toString();
                        if (number.trim().length() > 0) {
                            EmergencyCallList.this.addEmergencyNumber(number.trim());
                        }
                        EmergencyCallList.this.refreshNumberList();
                        edit.setText("");
                        EmergencyCallList.this.mDialog.cancel();
                    }
                });
                builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() { // from class: com.android.phone.EmergencyCallList.4
                    @Override // android.content.DialogInterface.OnClickListener
                    public void onClick(DialogInterface dialog, int which) {
                        edit.setText("");
                        EmergencyCallList.this.mDialog.cancel();
                    }
                });
                this.mDialog = builder.setView(view).setTitle(R.string.new_emergency_number).create();
                return this.mDialog;
            default:
                return null;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void addEmergencyNumber(String number) {
        Log.d("EmergencyCallList", "addEmergencyNumber.number=" + number);
        if (30 < number.length()) {
            Toast.makeText(this, R.string.emergency_number_save_error, 1).show();
            return;
        }
        HashMap<String, Object> map = new HashMap<>();
        map.put("ItemText", number);
        if (!this.mNumberList.contains(map)) {
            String addNumbers = SystemProperties.get("persist.radio.user.add.eccList");
            if (addNumbers == null || addNumbers.length() <= 0) {
                this.mAddNumbers = new StringBuilder(number);
            } else {
                this.mAddNumbers = new StringBuilder(addNumbers);
                this.mAddNumbers.append(",");
                this.mAddNumbers.append(number);
            }
            Log.d("EmergencyCallList", "addEmergencyNumber.PROPERTY_ADDED_ECC_LIST=" + this.mAddNumbers.toString());
            SystemProperties.set("persist.radio.user.add.eccList", this.mAddNumbers.toString());
            return;
        }
        Toast toast = Toast.makeText(this, R.string.emergency_number_exist, 1);
        toast.show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void deleteEmergencyNumber(String numberDelete) {
        ArrayList<String> addEccNumber = new ArrayList<>();
        String addNumbers = SystemProperties.get("persist.radio.user.add.eccList");
        String[] arr$ = addNumbers.split(",");
        for (String eccNum : arr$) {
            if (!addEccNumber.contains(eccNum)) {
                addEccNumber.add(eccNum);
            }
        }
        addEccNumber.remove(numberDelete);
        Log.d("EmergencyCallList", "added emergency number is " + addEccNumListToString(addEccNumber));
        SystemProperties.set("persist.radio.user.add.eccList", addEccNumListToString(addEccNumber));
        refreshNumberList();
    }

    private String addEccNumListToString(ArrayList<String> addEccNumList) {
        String str = null;
        String string = Arrays.toString(addEccNumList.toArray());
        String[] stringArray = string.replace("[", "").replace("]", "").trim().split(", ");
        for (int i = 0; i < stringArray.length; i++) {
            if (i == 0) {
                str = stringArray[i];
            } else {
                str = str + "," + stringArray[i];
            }
        }
        return str;
    }
}
