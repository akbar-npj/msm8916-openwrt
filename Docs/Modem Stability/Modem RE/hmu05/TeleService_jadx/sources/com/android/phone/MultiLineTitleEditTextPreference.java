package com.android.phone;

import android.content.Context;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

/* JADX INFO: loaded from: classes.dex */
public class MultiLineTitleEditTextPreference extends EditTextPreference {
    public MultiLineTitleEditTextPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public MultiLineTitleEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MultiLineTitleEditTextPreference(Context context) {
        super(context);
    }

    @Override // android.preference.Preference
    protected void onBindView(View view) {
        super.onBindView(view);
        TextView textView = (TextView) view.findViewById(android.R.id.title);
        if (textView != null) {
            textView.setSingleLine(false);
        }
    }
}
