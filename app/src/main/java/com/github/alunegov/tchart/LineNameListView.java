package com.github.alunegov.tchart;

import android.content.Context;
import android.content.res.ColorStateList;
import android.support.v4.widget.CompoundButtonCompat;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LineNameListView extends LinearLayout {
    private static final int TEXT_SIZE_SP = 17;

    private float textSize;

    private @Nullable OnCheckedChangeListener onCheckedChangeListener;

    public LineNameListView(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);

        final DisplayMetrics dm = context.getResources().getDisplayMetrics();

        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP, dm);
    }

    public void setTextSize(float px) {
        textSize = px;

        for (int i = 0; i < getChildCount(); i++) {
            final View view = getChildAt(i);
            final CheckBox cb = (CheckBox) view.findViewById(R.id.line_checkbox);
            cb.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        }

        //invalidate();
    }

    public void setLineNames(@NotNull LineName[] lineNames) {
        final LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        assert inflater != null;

        removeAllViews();

        for (int i = 0; i < lineNames.length; i++) {
            final View view = inflater.inflate(R.layout.view_line_name_list_item, this, false);

            final CheckBox cb = (CheckBox) view.findViewById(R.id.line_checkbox);

            cb.setChecked(true);
            cb.setText(lineNames[i].getName());
            cb.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
            // https://stackoverflow.com/a/41752859/2968990
            CompoundButtonCompat.setButtonTintList(cb, ColorStateList.valueOf(lineNames[i].getColor()));
            // index in lineNames as tag for lineVisibilityOnCheckedChangeListener
            cb.setTag(i);
            cb.setOnCheckedChangeListener(lineVisibilityOnCheckedChangeListener);

            if (i == (lineNames.length - 1)) {
                final View dividerView = view.findViewById(R.id.lines_divider);

                dividerView.setVisibility(GONE);
            }

            addView(view);
        }

        //invalidate();
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
