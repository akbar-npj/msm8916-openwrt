package com.android.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.preference.EditTextPreference;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.method.ArrowKeyMovementMethod;
import android.text.method.DialerKeyListener;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import java.util.Calendar;
import java.util.Date;

/* JADX INFO: loaded from: classes.dex */
public class EditPhoneNumberPreference extends EditTextPreference implements AdapterView.OnItemSelectedListener {
    private String TAG;
    private int mButtonClicked;
    private CharSequence mChangeNumberText;
    private boolean mChecked;
    private int mConfirmationMode;
    private Intent mContactListIntent;
    private ImageButton mContactPickButton;
    private View.OnFocusChangeListener mDialogFocusChangeListener;
    private OnDialogClosedListener mDialogOnClosedListener;
    private CharSequence mDisableText;
    private CharSequence mEnableText;
    private String mEncodedText;
    private Calendar mEndDate;
    private int mEndTimeHour;
    private int mEndTimeMinute;
    private View mEndTimeSetting;
    private GetDefaultNumberListener mGetDefaultNumberListener;
    private Activity mParentActivity;
    private String mPhoneNumber;
    private int mPrefId;
    private Calendar mStartDate;
    private int mStartTimeHour;
    private int mStartTimeMinute;
    private View mStartTimeSetting;
    private CharSequence mSummaryOff;
    private CharSequence mSummaryOn;
    private Spinner mTimeEndFormate;
    private Spinner mTimeEndHourSpinner;
    private Spinner mTimeEndMinuteSpinner;
    private TextView mTimeEndTextView;
    private CheckBox mTimePeriodAllDay;
    private String mTimePeriodString;
    private Spinner mTimeStartFormate;
    private Spinner mTimeStartHourSpinner;
    private Spinner mTimeStartMinuteSpinner;
    private TextView mTimeStartTextView;
    private ArrayAdapter time12hour;
    private ArrayAdapter time24hour;
    private int timeformateAMPM;

    interface GetDefaultNumberListener {
        String onGetDefaultNumber(EditPhoneNumberPreference editPhoneNumberPreference);
    }

    interface OnDialogClosedListener {
        void onDialogClosed(EditPhoneNumberPreference editPhoneNumberPreference, int i);
    }

    public EditPhoneNumberPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.TAG = "EditPhoneNumberPreference";
        this.mStartDate = Calendar.getInstance();
        this.mEndDate = Calendar.getInstance();
        this.mStartTimeHour = 1;
        this.mStartTimeMinute = 10;
        this.mEndTimeHour = 2;
        this.mEndTimeMinute = 20;
        this.timeformateAMPM = 0;
        this.mEncodedText = null;
        setDialogLayoutResource(R.layout.pref_dialog_editphonenumber);
        this.mContactListIntent = new Intent("android.intent.action.GET_CONTENT");
        this.mContactListIntent.setType("vnd.android.cursor.item/phone_v2");
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.EditPhoneNumberPreference, 0, R.style.EditPhoneNumberPreference);
        this.mEnableText = a.getString(0);
        this.mDisableText = a.getString(1);
        this.mChangeNumberText = a.getString(2);
        this.mConfirmationMode = a.getInt(3, 0);
        a.recycle();
        TypedArray a2 = context.obtainStyledAttributes(attrs, android.R.styleable.CheckBoxPreference, 0, 0);
        this.mSummaryOn = a2.getString(0);
        this.mSummaryOff = a2.getString(1);
        a2.recycle();
    }

    public EditPhoneNumberPreference(Context context) {
        this(context, null);
    }

    @Override // android.preference.Preference
    protected void onBindView(View view) {
        CharSequence sum;
        int vis;
        super.onBindView(view);
        TextView summaryView = (TextView) view.findViewById(android.R.id.summary);
        if (summaryView != null) {
            if (this.mConfirmationMode == 1) {
                if (this.mChecked) {
                    sum = this.mSummaryOn == null ? getSummary() : this.mSummaryOn;
                } else {
                    sum = this.mSummaryOff == null ? getSummary() : this.mSummaryOff;
                }
            } else {
                sum = getSummary();
            }
            if (sum != null) {
                summaryView.setText(sum);
                vis = 0;
            } else {
                vis = 8;
            }
            if (vis != summaryView.getVisibility()) {
                summaryView.setVisibility(vis);
            }
        }
    }

    @Override // android.preference.EditTextPreference, android.preference.DialogPreference
    protected void onBindDialogView(View view) {
        String defaultNumber;
        Log.e(this.TAG, "onBindDialogView, mPrefId = " + this.mPrefId);
        this.mButtonClicked = -2;
        super.onBindDialogView(view);
        EditText editText = getEditText();
        this.mContactPickButton = (ImageButton) view.findViewById(R.id.select_contact);
        if (editText != null) {
            if (this.mGetDefaultNumberListener != null && (defaultNumber = this.mGetDefaultNumberListener.onGetDefaultNumber(this)) != null) {
                this.mPhoneNumber = defaultNumber;
            }
            editText.setText(this.mPhoneNumber);
            editText.setMovementMethod(ArrowKeyMovementMethod.getInstance());
            editText.setKeyListener(DialerKeyListener.getInstance());
            editText.setOnFocusChangeListener(this.mDialogFocusChangeListener);
        }
        if (this.mContactPickButton != null) {
            this.mContactPickButton.setOnClickListener(new View.OnClickListener() { // from class: com.android.phone.EditPhoneNumberPreference.1
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    if (EditPhoneNumberPreference.this.mParentActivity != null) {
                        EditPhoneNumberPreference.this.mParentActivity.startActivityForResult(EditPhoneNumberPreference.this.mContactListIntent, EditPhoneNumberPreference.this.mPrefId);
                    }
                }
            });
        }
        initTimeSettingsView(view);
    }

    @Override // android.preference.EditTextPreference
    protected void onAddEditTextToDialogView(View dialogView, EditText editText) {
        ViewGroup container = (ViewGroup) dialogView.findViewById(R.id.edit_container);
        if (container != null) {
            container.addView(editText, -1, -2);
        }
    }

    @Override // android.preference.DialogPreference
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        if (this.mConfirmationMode == 1) {
            if (this.mChecked) {
                builder.setPositiveButton(this.mChangeNumberText, this);
                builder.setNeutralButton(this.mDisableText, this);
            } else {
                builder.setPositiveButton((CharSequence) null, (DialogInterface.OnClickListener) null);
                builder.setNeutralButton(this.mEnableText, this);
            }
        }
        builder.setIcon(R.mipmap.ic_launcher_phone);
    }

    public void setDialogOnClosedListener(OnDialogClosedListener l) {
        this.mDialogOnClosedListener = l;
    }

    public void setParentActivity(Activity parent, int identifier) {
        this.mParentActivity = parent;
        this.mPrefId = identifier;
        this.mGetDefaultNumberListener = null;
    }

    public void setParentActivity(Activity parent, int identifier, GetDefaultNumberListener l) {
        this.mParentActivity = parent;
        this.mPrefId = identifier;
        this.mGetDefaultNumberListener = l;
    }

    public void onPickActivityResult(String pickedValue) {
        EditText editText = getEditText();
        if (editText != null) {
            editText.setText(pickedValue);
        }
    }

    @Override // android.preference.DialogPreference, android.content.DialogInterface.OnClickListener
    public void onClick(DialogInterface dialog, int which) {
        if (this.mConfirmationMode == 1 && which == -3) {
            setToggled(isToggled() ? false : true);
        }
        this.mButtonClicked = which;
        super.onClick(dialog, which);
    }

    @Override // android.preference.EditTextPreference, android.preference.DialogPreference
    protected void onDialogClosed(boolean positiveResult) {
        if (this.mButtonClicked == -1 || this.mButtonClicked == -3) {
            String number = getEditText().getText().toString();
            if (this.mPrefId == 6) {
                setPhoneNumberWithTimePeriod(number, this.mStartTimeHour, this.mStartTimeMinute, this.mEndTimeHour, this.mEndTimeMinute);
                Log.e(this.TAG, "onDialogClosed, phonenumber = " + number + "timePeriodString = " + this.mTimePeriodString);
            } else {
                setPhoneNumber(number);
            }
            dumpTimePeriodInfo();
            super.onDialogClosed(positiveResult);
            setText(getStringValue());
        } else {
            super.onDialogClosed(positiveResult);
        }
        if (this.mDialogOnClosedListener != null) {
            this.mDialogOnClosedListener.onDialogClosed(this, this.mButtonClicked);
        }
    }

    public boolean isToggled() {
        return this.mChecked;
    }

    public EditPhoneNumberPreference setToggled(boolean checked) {
        this.mChecked = checked;
        setText(getStringValue());
        notifyChanged();
        return this;
    }

    public String getPhoneNumber() {
        return PhoneNumberUtils.stripSeparators(this.mPhoneNumber);
    }

    public int getStartTimeHour() {
        return this.mStartTimeHour;
    }

    public int getStartTimeMinute() {
        return this.mStartTimeMinute;
    }

    public int getEndTimeHour() {
        return this.mEndTimeHour;
    }

    public int getEndTimeMinute() {
        return this.mEndTimeMinute;
    }

    protected String getRawPhoneNumber() {
        return this.mPhoneNumber;
    }

    protected String getRawPhoneNumberWithTime() {
        return this.mPhoneNumber + this.mTimePeriodString;
    }

    public EditPhoneNumberPreference setPhoneNumber(String number) {
        Log.e("EditPhoneNumberPreference", "setPhoneNumber, phonenumber = " + number);
        this.mPhoneNumber = number;
        setText(getStringValue());
        notifyChanged();
        return this;
    }

    public EditPhoneNumberPreference setPhoneNumberWithTimePeriod(String number, int starthour, int startminute, int endhour, int endminute) {
        this.mPhoneNumber = number;
        setText(getStringValue());
        setTimePeriodInfo(starthour, startminute, endhour, endminute);
        notifyChanged();
        return this;
    }

    public void setTimePeriodInfo(int starthour, int startminute, int endhour, int endminute) {
        this.mStartTimeHour = starthour;
        this.mStartTimeMinute = startminute;
        this.mEndTimeHour = endhour;
        this.mEndTimeMinute = endminute;
        if (this.mStartTimeHour == 0 && this.mStartTimeMinute == 0 && this.mEndTimeHour == 23 && this.mEndTimeMinute == 59) {
            this.mTimePeriodString = " all day";
            return;
        }
        String fomatedStartTimeString = formateTime(this.mStartDate, this.mStartTimeHour, this.mStartTimeMinute);
        String fomatedEndTimeString = formateTime(this.mEndDate, this.mEndTimeHour, this.mEndTimeMinute);
        this.mTimePeriodString = " from" + fomatedStartTimeString + " to " + fomatedEndTimeString;
    }

    private String formateTime(Calendar mDate, int hour, int minute) {
        Calendar now = Calendar.getInstance();
        DateFormat.getDateFormat(getContext());
        mDate.set(now.get(1), now.get(2), now.get(5), hour, minute, 0);
        Date mDateTimeOnly = mDate.getTime();
        String fomatedTimeString = DateFormat.getTimeFormat(getContext()).format(mDateTimeOnly);
        Log.e(this.TAG, "setTimePeriodInfo, formatedTime =" + fomatedTimeString);
        return fomatedTimeString;
    }

    public EditPhoneNumberPreference setPhoneNumberWithTimePeriod(String number, String timeperiod) {
        this.mPhoneNumber = number;
        this.mTimePeriodString = timeperiod;
        setText(getStringValue());
        notifyChanged();
        return this;
    }

    @Override // android.preference.EditTextPreference, android.preference.Preference
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setValueFromString(restoreValue ? getPersistedString(getStringValue()) : (String) defaultValue);
    }

    @Override // android.preference.EditTextPreference, android.preference.Preference
    public boolean shouldDisableDependents() {
        if (this.mConfirmationMode != 1 || this.mEncodedText == null) {
            return TextUtils.isEmpty(this.mPhoneNumber) && this.mConfirmationMode == 0;
        }
        String[] inValues = this.mEncodedText.split(":", 2);
        boolean shouldDisable = inValues[0].equals("1");
        return shouldDisable;
    }

    @Override // android.preference.Preference
    protected boolean persistString(String value) {
        this.mEncodedText = value;
        return super.persistString(value);
    }

    public EditPhoneNumberPreference setSummaryOn(CharSequence summary) {
        this.mSummaryOn = summary;
        if (isToggled()) {
            notifyChanged();
        }
        return this;
    }

    public CharSequence getSummaryOn() {
        return this.mSummaryOn;
    }

    protected void setValueFromString(String value) {
        String[] inValues = value.split(":", 2);
        setToggled(inValues[0].equals("1"));
        if (inValues.length == 3) {
            setPhoneNumberWithTimePeriod(inValues[1], inValues[2]);
        } else {
            setPhoneNumber(inValues[1]);
        }
    }

    protected String getStringValue() {
        if (this.mPrefId == 6) {
            return (isToggled() ? "1" : "0") + ":" + getPhoneNumber() + this.mTimePeriodString;
        }
        return (isToggled() ? "1" : "0") + ":" + getPhoneNumber();
    }

    @Override // android.widget.AdapterView.OnItemSelectedListener
    public void onNothingSelected(AdapterView<?> parent) {
    }

    @Override // android.widget.AdapterView.OnItemSelectedListener
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        String selectedItem = parent.getItemAtPosition(position).toString();
        Log.d(this.TAG, "onItemSelected, selectedItem = " + selectedItem + ", position = " + position + ", parent" + parent);
        setSelectionStartTimePreiod();
        setSelectionEndTimePreiod();
        dumpTimePeriodInfo();
    }

    private void initTimeSettingsView(View view) {
        Log.e(this.TAG, "initTimeSettingsView, mPrefId = " + this.mPrefId);
        this.mTimePeriodAllDay = (CheckBox) view.findViewById(R.id.all_day);
        this.mTimePeriodAllDay.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.android.phone.EditPhoneNumberPreference.2
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                EditPhoneNumberPreference.this.onAllDayChecked(isChecked);
            }
        });
        this.mTimeStartTextView = (TextView) view.findViewById(R.id.time_start);
        this.mStartTimeSetting = view.findViewById(R.id.start_time_setting);
        this.mTimeStartHourSpinner = (Spinner) view.findViewById(R.id.time_start_hour);
        this.mTimeStartHourSpinner.setOnItemSelectedListener(this);
        this.mTimeStartMinuteSpinner = (Spinner) view.findViewById(R.id.time_start_minute);
        this.mTimeStartMinuteSpinner.setOnItemSelectedListener(this);
        this.mTimeEndTextView = (TextView) view.findViewById(R.id.time_end);
        this.mEndTimeSetting = view.findViewById(R.id.end_time_setting);
        this.mTimeEndHourSpinner = (Spinner) view.findViewById(R.id.time_end_hour);
        this.mTimeEndHourSpinner.setOnItemSelectedListener(this);
        this.mTimeEndMinuteSpinner = (Spinner) view.findViewById(R.id.time_end_minute);
        this.mTimeEndMinuteSpinner.setOnItemSelectedListener(this);
        this.mTimeStartFormate = (Spinner) view.findViewById(R.id.time_start_formate);
        this.mTimeEndFormate = (Spinner) view.findViewById(R.id.time_end_formate);
        this.time24hour = ArrayAdapter.createFromResource(getContext(), R.array.hour_24_items, android.R.layout.simple_spinner_item);
        this.time24hour.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.time12hour = ArrayAdapter.createFromResource(getContext(), R.array.hour_12_items, android.R.layout.simple_spinner_item);
        this.time12hour.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (this.mPrefId == 6) {
            Log.e(this.TAG, "show time setting field");
            this.mTimePeriodAllDay.setVisibility(0);
            this.mTimeStartTextView.setVisibility(0);
            this.mTimeEndTextView.setVisibility(0);
            this.mStartTimeSetting.setVisibility(0);
            this.mEndTimeSetting.setVisibility(0);
            this.mTimeStartMinuteSpinner.setSelection(this.mStartTimeMinute);
            this.mTimeEndMinuteSpinner.setSelection(this.mEndTimeMinute);
            if (is24Hour()) {
                this.mTimeStartFormate.setVisibility(8);
                this.mTimeEndFormate.setVisibility(8);
                this.mTimeStartHourSpinner.setAdapter((SpinnerAdapter) this.time24hour);
                this.mTimeEndHourSpinner.setAdapter((SpinnerAdapter) this.time24hour);
                this.mTimeStartHourSpinner.setSelection(this.mStartTimeHour);
                this.mTimeEndHourSpinner.setSelection(this.mEndTimeHour);
            } else {
                this.mTimeStartFormate.setVisibility(0);
                this.mTimeEndFormate.setVisibility(0);
                this.mTimeStartHourSpinner.setAdapter((SpinnerAdapter) this.time12hour);
                this.mTimeEndHourSpinner.setAdapter((SpinnerAdapter) this.time12hour);
                showTimeWith24Formate(this.mStartTimeHour, this.mTimeStartHourSpinner, this.mTimeStartFormate);
                showTimeWith24Formate(this.mEndTimeHour, this.mTimeEndHourSpinner, this.mTimeEndFormate);
            }
            onAllDayChecked(this.mTimePeriodAllDay.isChecked());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onAllDayChecked(boolean isChecked) {
        this.mTimeStartHourSpinner.setEnabled(!isChecked);
        this.mTimeStartHourSpinner.setClickable(!isChecked);
        this.mTimeStartMinuteSpinner.setEnabled(!isChecked);
        this.mTimeStartMinuteSpinner.setClickable(!isChecked);
        this.mTimeEndHourSpinner.setEnabled(!isChecked);
        this.mTimeEndHourSpinner.setClickable(!isChecked);
        this.mTimeEndMinuteSpinner.setEnabled(!isChecked);
        this.mTimeEndMinuteSpinner.setClickable(!isChecked);
        if (!is24Hour()) {
            this.mTimeStartFormate.setEnabled(!isChecked);
            this.mTimeStartFormate.setClickable(!isChecked);
            this.mTimeEndFormate.setEnabled(!isChecked);
            this.mTimeEndFormate.setClickable(isChecked ? false : true);
        }
        if (isChecked) {
            this.mTimeStartTextView.setTextColor(-7829368);
            this.mTimeEndTextView.setTextColor(-7829368);
        } else {
            this.mTimeStartTextView.setTextColor(-16777216);
            this.mTimeEndTextView.setTextColor(-16777216);
        }
    }

    private boolean is24Hour() {
        return DateFormat.is24HourFormat(getContext());
    }

    private void showTimeWith24Formate(int hour, Spinner hourSpinner, Spinner hourFormate) {
        if (hour < 12) {
            hourSpinner.setSelection(hour);
            hourFormate.setSelection(0);
        } else if (hour >= 12) {
            hourSpinner.setSelection(hour - 12);
            hourFormate.setSelection(1);
        }
    }

    private void setSelectionStartTimePreiod() {
        this.mStartTimeMinute = (int) this.mTimeStartMinuteSpinner.getSelectedItemId();
        Log.d(this.TAG, "setSelectionTimePreiod, minute = " + this.mStartTimeMinute);
        int hourposion = (int) this.mTimeStartHourSpinner.getSelectedItemId();
        Log.d(this.TAG, "setSelectionTimePreiod, hourposion = " + hourposion);
        if (is24Hour()) {
            this.mStartTimeHour = (int) this.mTimeStartHourSpinner.getSelectedItemId();
        } else {
            int selectedFormate = (int) this.mTimeStartFormate.getSelectedItemId();
            Log.d(this.TAG, "setSelectionTimePreiod, selectedFormate = " + selectedFormate);
            if (this.mTimeStartFormate.getSelectedItemId() == 0) {
                this.mStartTimeHour = hourposion;
            } else if (this.mTimeStartFormate.getSelectedItemId() == 1) {
                this.mStartTimeHour = hourposion + 12;
            }
        }
        Log.d(this.TAG, "setSelectionTimePreiod, mStartTimeHour = " + this.mStartTimeHour + ", mStartTimeMinute = " + this.mStartTimeMinute);
    }

    private void setSelectionEndTimePreiod() {
        this.mEndTimeMinute = (int) this.mTimeEndMinuteSpinner.getSelectedItemId();
        Log.d(this.TAG, "setSelectionTimePreiod, mEndTimeMinute = " + this.mEndTimeMinute);
        int hourposion = (int) this.mTimeEndHourSpinner.getSelectedItemId();
        Log.d(this.TAG, "setSelectionTimePreiod, endhourposion = " + hourposion);
        if (is24Hour()) {
            this.mEndTimeHour = (int) this.mTimeEndHourSpinner.getSelectedItemId();
        } else {
            int selectedFormate = (int) this.mTimeEndFormate.getSelectedItemId();
            Log.d(this.TAG, "setSelectionTimePreiod, selectedend time Formate = " + selectedFormate);
            if (this.mTimeEndFormate.getSelectedItemId() == 0) {
                this.mEndTimeHour = hourposion;
            } else if (this.mTimeEndFormate.getSelectedItemId() == 1) {
                this.mEndTimeHour = hourposion + 12;
            }
        }
        Log.d(this.TAG, "setSelectionTimePreiod, mEndTimeHour = " + this.mEndTimeHour + ", mEndTimeMinute = " + this.mEndTimeMinute);
    }

    private void dumpTimePeriodInfo() {
        Log.e(this.TAG, "dumpTimePeriodIfo: mStartTimeHour = " + this.mStartTimeHour + ", mStartTimeMinute = " + this.mStartTimeMinute + ", mEndTimeHour = " + this.mEndTimeHour + ", mEndTimeMinute = " + this.mEndTimeMinute + ", mTimePeriodString = " + this.mTimePeriodString);
    }
}
