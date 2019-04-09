package com.github.alunegov.tchart;

import android.content.Context;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import com.google.android.flexbox.FlexboxLayout;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LineNameListView extends FlexboxLayout {
    private static final int TEXT_SIZE_SP = 17;

    private boolean mBroadcasting = false;

    private float textSize;

    private @Nullable OnChangeListener onChangeListener;

    public LineNameListView(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context);
    }

    private void init(Context context) {
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
        if (inflater == null) throw new AssertionError();

        removeAllViews();

        for (int i = 0; i < lineNames.length; i++) {
            final View view = inflater.inflate(R.layout.view_line_name_list_item, this, false);

            final CheckBox cb = (CheckBox) view.findViewById(R.id.line_checkbox);

            cb.setChecked(true);
            cb.setText(lineNames[i].getName());
            cb.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
            // https://stackoverflow.com/a/40252537/2968990
            cb.getBackground().setColorFilter(lineNames[i].getColor(), PorterDuff.Mode.SRC_ATOP);
            // index in lineNames as tag for lineVisibilityOnCheckedChangeListener
            cb.setTag(i);
            cb.setOnCheckedChangeListener(lineOnCheckedChangeListener);
            cb.setOnLongClickListener(lineOnLongClickListener);

            addView(view);
        }

        //invalidate();
    }

    private final CompoundButton.OnCheckedChangeListener lineOnCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            // avoid per-line disabling then disabling all via long tap
            if (mBroadcasting) {
                return;
            }

            final int lineIndex = (int) buttonView.getTag();

            if (onChangeListener != null) {
                onChangeListener.onCheckedChange(lineIndex, isChecked);
            }
        }
    };

    private final CompoundButton.OnLongClickListener lineOnLongClickListener = new OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            final int lineIndex = (int) v.getTag();

            mBroadcasting = true;
            for (int i = 0; i < getChildCount(); i++) {
                final View view = getChildAt(i);
                final CheckBox cb = (CheckBox) view.findViewById(R.id.line_checkbox);
                cb.setChecked(i == lineIndex);
            }

            mBroadcasting = false;

            if (onChangeListener != null) {
                onChangeListener.onLongClick(lineIndex);
            }

            return true;
        }
    };

    public void setOnChangeListener(@Nullable OnChangeListener onChangeListener) {
        this.onChangeListener = onChangeListener;
    }

    public interface OnChangeListener {
        void onCheckedChange(int lineIndex, boolean isChecked);

        void onLongClick(int lineIndex);
    }
}
