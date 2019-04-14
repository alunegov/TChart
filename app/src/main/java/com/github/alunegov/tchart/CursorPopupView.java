package com.github.alunegov.tchart;

import android.content.Context;
import android.graphics.*;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;

import android.view.MotionEvent;
import org.jetbrains.annotations.NotNull;

public class CursorPopupView {
    // cursor_next - #D2D5D7

    private static final int MIN_WIDTH_DP = 150;

    private static final int PADDING_DP = 10;

    private static final int VERTICAL_MARGIN_DP = 6;

    private static final int HORIZONTAL_MARGIN_DP = 5;

    private static final int TEXT_SIZE_SP = 14;

    private static final int BORDER_RADIUS_SP = 8;

    private static final int BACKGROUND_COLOR = Color.parseColor("#000000");

    // #E2E5E7, #232E3D
    private static final int BORDER_COLOR = Color.parseColor("#E2E5E7");

    private static final int TEXT_COLOR = Color.parseColor("#FFFFFF");

    public String date;
    public LineValues[] linesValues;
    public int linesValuesCount;
    public OnChangeListener onChangeListener;

    public float left, top, width, height;

    private boolean showPercents;
    private float minWidth;
    private float padding;
    private float verticalMargin;
    private float horizontalMargin;
    private float borderRadius;

    private float textHeight;
    private float[] percentsTextWidth;
    private float[] valuesTextWidth;

    private Paint backgroundPaint;
    private Paint borderPaint;
    private Paint datePaint;
    private Paint lineNamePaint;
    private Paint lineValuePaint;
    private Paint linePercentPaint;

    private float maxPercentW, maxNameW, maxValueW;

    public CursorPopupView(Context context, @NotNull ChartInputData inputData) {
        linesValues = new LineValues[inputData.LinesValues.length + 1];
        for (int i = 0; i < linesValues.length; i++) {
            linesValues[i] = new LineValues();
        }

        linesValuesCount = 0;

        showPercents = inputData.linesType == ChartInputData.LineType.AREA;

        final DisplayMetrics dm = context.getResources().getDisplayMetrics();

        minWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, MIN_WIDTH_DP, dm);
        padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, PADDING_DP, dm);
        verticalMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, VERTICAL_MARGIN_DP, dm);
        horizontalMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, HORIZONTAL_MARGIN_DP, dm);
        final float textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP, dm);
        borderRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, BORDER_RADIUS_SP, dm);

        percentsTextWidth = new float[linesValues.length];
        valuesTextWidth = new float[linesValues.length];

        final int backgroundColor = ChartUtils.getThemedColor(context, R.attr.tchart_cursor_popup_background_color, BACKGROUND_COLOR);
        final int borderColor = ChartUtils.getThemedColor(context, R.attr.tchart_cursor_popup_border_color, BORDER_COLOR);
        final int textColor = ChartUtils.getThemedColor(context, R.attr.tchart_cursor_popup_text_color, TEXT_COLOR);

        backgroundPaint = new Paint();
        backgroundPaint.setAntiAlias(true);
        backgroundPaint.setColor(backgroundColor);

        borderPaint = new Paint();
        borderPaint.setAntiAlias(true);
        borderPaint.setColor(borderColor);
        borderPaint.setStyle(Paint.Style.STROKE);

        datePaint = new Paint();
        datePaint.setAntiAlias(true);
        datePaint.setColor(textColor);
        datePaint.setFakeBoldText(true);
        datePaint.setTextSize(textSize);

        lineNamePaint = new Paint(datePaint);
        lineNamePaint.setFakeBoldText(false);

        lineValuePaint = new Paint(datePaint);

        linePercentPaint = new Paint(datePaint);

        final Paint.FontMetrics fm = datePaint.getFontMetrics();
        textHeight = fm.descent - fm.ascent;
    }

    public void onLayout() {
        float minW = datePaint.measureText(date);

        //Log.d("CPV", String.format("onLayout: [0].state = %d, [1].state = %d", linesValues[0].state, linesValues[1].state));
        //Log.d("CPV", String.format("onLayout: [0].percent = %s, [1].percent = %s", linesValues[0].percent, linesValues[1].percent));
        //Log.d("CPV", String.format("onLayout: [0].name = %s, [1].name = %s", linesValues[0].name, linesValues[1].name));

        maxPercentW = maxNameW = maxValueW = 0;
        for (int i = 0; i < linesValuesCount; i++) {
            if (showPercents) {
                percentsTextWidth[i] = linePercentPaint.measureText(linesValues[i].percent);
                if (percentsTextWidth[i] > maxPercentW) {
                    maxPercentW = percentsTextWidth[i];
                }
            }

            // на boldName не смотрим - "жирное" имя это All и оно короткое
            final float w = lineNamePaint.measureText(linesValues[i].name);
            if (w > maxNameW) {
                maxNameW = w;
            }

            valuesTextWidth[i] = lineValuePaint.measureText(linesValues[i].value);
            if (valuesTextWidth[i] > maxValueW) {
                maxValueW = valuesTextWidth[i];
            }
        }

        width = 2 * padding + Math.max(minW, maxPercentW + maxNameW + maxValueW + 2 * verticalMargin);
        width = Math.max(width, minWidth);

        height = 2 * padding + textHeight * (1 + linesValuesCount) + horizontalMargin * (1 + linesValuesCount - 1);
    }

    private final RectF tmpRect = new RectF();

    public boolean onSingleTapUp(MotionEvent event) {
/*        final boolean inCursorNextArea = false;
        if (inCursorNextArea) {
            if (onChangeListener != null) {
                onChangeListener.OnCursorNextClick();
            }

            return true;
        }*/

        tmpRect.set(left, top, left + width, top + height);
        if (tmpRect.contains(event.getX(), event.getY())) {
            if (onChangeListener != null) {
                onChangeListener.OnClick();
            }

            return true;
        }

        return false;
    }

    public void onDraw(Canvas canvas) {
        tmpRect.set(left, top, left + width, top + height);
        canvas.drawRoundRect(tmpRect, borderRadius, borderRadius, backgroundPaint);
        canvas.drawRoundRect(tmpRect, borderRadius, borderRadius, borderPaint);

        float y = top + padding + textHeight;

        canvas.drawText(date, left + padding, y, datePaint);

        y += textHeight + horizontalMargin;

        for (int i = 0; i < linesValuesCount; i++) {
            final float scale = (float) linesValues[i].state / ChartInputDataStats.VISIBILITY_STATE_ON;

            canvas.save();
            canvas.scale(1f, scale, 0f, y);

            float x = left + padding;

            if (showPercents) {
                x += maxPercentW;
                canvas.drawText(linesValues[i].percent, x - percentsTextWidth[i], y, linePercentPaint);

                x += verticalMargin;
            }

            lineNamePaint.setFakeBoldText(linesValues[i].boldName);
            canvas.drawText(linesValues[i].name, x, y, lineNamePaint);

            x = left + width - padding;
            lineValuePaint.setColor(linesValues[i].color);
            canvas.drawText(linesValues[i].value, x - valuesTextWidth[i], y, lineValuePaint);

            y += textHeight + horizontalMargin;

            canvas.restore();
        }
    }

    public static class LineValues {
        String name;
        String value;
        String percent;
        int color;
        boolean boldName;
        int state;
    }

    public interface OnChangeListener {
        void OnClick();

        void OnCursorNextClick();
    }
}
