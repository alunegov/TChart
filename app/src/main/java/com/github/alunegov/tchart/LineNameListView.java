package com.github.alunegov.tchart;

import android.content.Context;
import android.content.res.ColorStateList;
import android.support.v4.widget.CompoundButtonCompat;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LineNameListView extends LinearLayout {
    private @Nullable OnCheckedChangeListener onCheckedChangeListener;

    public LineNameListView(Context context, AttributeSet attrs) {
        super(context, attrs);

        init();
    }

    private void init() {
        setOrientation(VERTICAL);
    }

    public void setLineNames(@NotNull LineName[] lineNames) {
        final LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        removeAllViews();
        for (int i = 0; i < lineNames.length; i++) {
            final View view = inflater.inflate(R.layout.view_line_name_list_item, this, false);

            final CheckBox cb = (CheckBox) view.findViewById(R.id.line_visibility);
            // https://stackoverflow.com/a/41752859/2968990
            CompoundButtonCompat.setButtonTintList(cb, ColorStateList.valueOf(lineNames[i].getColor()));
            cb.setTag(i);
            cb.setOnCheckedChangeListener(lineVisibilityOnCheckedChangeListener);

            final TextView tv = (TextView) view.findViewById(R.id.line_name);
            tv.setText(lineNames[i].getName());

            addView(view);
        }
    }

    private final CompoundButton.OnCheckedChangeListener lineVisibilityOnCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            final int lineIndex = (int) buttonView.getTag();

            if (onCheckedChangeListener != null) {
                onCheckedChangeListener.onCheckedChange(lineIndex, isChecked);
            }
        }
    };

    public void setOnCheckedChangeListener(@Nullable OnCheckedChangeListener onCheckedChangeListener) {
        this.onCheckedChangeListener = onCheckedChangeListener;
    }

    public interface OnCheckedChangeListener {
        void onCheckedChange(int lineIndex, boolean isChecked);
    }
}
