package com.github.alunegov.tchart;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.*;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

public class MainChartView extends View {
    private static final int NO_CURSOR = -1;

    private static final int AXIS_LINES_COUNT = 5;

    private static final int LINE_WIDTH_DP = 3;

    private static final int AXIS_TEXT_SIZE_SP = 17;
    private static final int AXIS_TEXT_COLOR = Color.parseColor("#96A2AA");

    private static final int AXIS_LINE_COLOR = Color.parseColor("#F1F1F2");
    private static final int AXIS_LINE_WIDTH_DP = 1;

    private static final int CURSOR_LINE_COLOR = Color.parseColor("#E5EBEF");
    private static final int CURSOR_LINE_WIDTH_DP = 2;

    private static final int MARKER_RADIUS_DP = 6;

    private float lineWidth;
    private float axisTextSize;
    private float markerRadius, markerFillRadius;

    private ChartInputData inputData;
    private ChartDrawData drawData;
    // индекс точки с курсором
    private int cursorIndex = NO_CURSOR;

    // настройки отрисовки линий
    private Paint[] linesPaints;
    // настройки отрисовки заполнения маркера курсора (чтобы получить заливку маркера цветом фона)
    private Paint linesMarkerFillPaint;
    // настройки отрисовки надписей осей
    private Paint axisTextPaint;
    // настройки отрисовки линий оси Y
    private Paint yAxisLinePaint;
    // настройки отрисовки вспомогательных линий (оси, курсор)
    private Paint helperLinePaint;

    public MainChartView(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context);
    }

    private void init(Context context) {
        final DisplayMetrics dm = context.getResources().getDisplayMetrics();

        lineWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, LINE_WIDTH_DP, dm);
        axisTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, AXIS_TEXT_SIZE_SP, dm);
        final float axisLineWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, AXIS_LINE_WIDTH_DP, dm);
        final float cursorLineWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, CURSOR_LINE_WIDTH_DP, dm);
        markerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, MARKER_RADIUS_DP, dm);
        markerFillRadius = markerRadius - lineWidth / 2;

        int axisTextColor;
        try {
            axisTextColor = ContextCompat.getColor(context, R.color.tchart_axis_text);
        } catch (Resources.NotFoundException e) {
            axisTextColor = AXIS_TEXT_COLOR;
        }

        int axisLineColor;
        try {
            axisLineColor = ContextCompat.getColor(context, R.color.tchart_axis_line);
        } catch (Resources.NotFoundException e) {
            axisLineColor = AXIS_LINE_COLOR;
        }

        int cursorLineColor;
        try {
            cursorLineColor = ContextCompat.getColor(context, R.color.tchart_cursor_line);
        } catch (Resources.NotFoundException e) {
            cursorLineColor = CURSOR_LINE_COLOR;
        }

        linesMarkerFillPaint = new Paint();
        linesMarkerFillPaint.setAntiAlias(true);
        linesMarkerFillPaint.setColor(Color.WHITE);  // sets in onDraw
        linesMarkerFillPaint.setStyle(Paint.Style.FILL);

        axisTextPaint = new Paint();
        axisTextPaint.setAntiAlias(true);
        axisTextPaint.setColor(axisTextColor);
        axisTextPaint.setStyle(Paint.Style.FILL);
        axisTextPaint.setTextSize(axisTextSize);

        yAxisLinePaint = ChartUtils.makeLinePaint(axisLineColor, axisLineWidth);

        helperLinePaint = ChartUtils.makeLinePaint(cursorLineColor, cursorLineWidth);
    }

    public void setAxisTextSize(float px) {
        axisTextSize = px;

        axisTextPaint.setTextSize(axisTextSize);

        invalidate();
    }

    public void setInputData(@NotNull ChartInputData inputData) {
        this.inputData = inputData;

        drawData = new ChartDrawData(inputData);
        drawData.setXRange(0, inputData.XValues.length - 1);

        linesPaints = ChartUtils.makeLinesPaints(inputData.LinesColors, lineWidth);
    }

    public void setXRange(float xLeftValue, float xRightValue) {
        if (drawData == null) {
            return;
        }

        drawData.setXRange(xLeftValue, xRightValue);

        invalidate();
    }

    public void updateLineVisibility(int lineIndex, boolean visible) {
        if (drawData == null) {
            return;
        }

        drawData.updateLineVisibility(lineIndex, visible);

        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (drawData == null) {
            return;
        }

        drawData.setArea(new RectF(0, 0, w, h));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (drawData == null) {
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_UP) {
            final float x = event.getX();

            final float xValue = drawData.pixelToX(x);

            final int newCursorIndex = findCursorIndex(xValue);

            // reset cursor if it set in same point
            cursorIndex = (newCursorIndex != cursorIndex) ? newCursorIndex : NO_CURSOR;

            invalidate();

            // TODO: dialog
        }

        return true;
    }

    // TODO: find cursor near selected point
    private int findCursorIndex(float cursorXValue) {
        return drawData.findXLeftIndex(cursorXValue);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (drawData == null) {
            return;
        }

        drawXAxis(canvas);
        drawYAxis(canvas);
        drawLines(canvas);
        drawCursor(canvas);
    }

    private void drawXAxis(@NotNull Canvas canvas) {
        final float xSwing = Math.abs(drawData.getXRightValue() - drawData.getXLeftValue());

        long stepValue = (long) xSwing / AXIS_LINES_COUNT;

        final int stepPixel =  (int) drawData.xToPixel(stepValue);
        if (stepPixel == 0) {
            return;
        }

        final SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());

        int i = 0;
        for (int x = 0; x < getWidth(); x = x + stepPixel) {
            final String s = sdf.format(new Date(i * stepValue * 1000L));
            canvas.drawText(s, x, getHeight(), axisTextPaint);

            i++;
        }
    }

    private void drawYAxis(@NotNull Canvas canvas) {
        final float ySwing = Math.abs(drawData.getYMax() - drawData.getYMin());

        int stepValue = (int) ySwing / AXIS_LINES_COUNT;

        final int stepPixel =  (int) drawData.pixelToY(stepValue);
        if (stepPixel == 0) {
            return;
        }

        int i = 0;
        for (int y = 0; y < getHeight(); y = y + stepPixel) {
            canvas.drawLine(0, getHeight() - y, getWidth(), getHeight() - y, yAxisLinePaint);

            final String s = String.valueOf(i * stepValue);
            canvas.drawText(s, 0, getHeight() - y - 20, axisTextPaint);  // TODO: scale dp->px

            i++;
        }
    }

    private void drawLines(@NotNull Canvas canvas) {
        final Path[] paths = drawData.getLinesPaths();
        final Set<Integer> invisibleLinesIndexes = drawData.getInvisibleLinesIndexes();

        for (int i = 0; i < paths.length; i++) {
            if (!invisibleLinesIndexes.contains(i)) {
                canvas.drawPath(paths[i], linesPaints[i]);
            }
        }
    }

    private void drawCursor(@NotNull Canvas canvas) {
        if (cursorIndex == NO_CURSOR) {
            return;
        }

        final float cursorX = drawData.xToPixel(inputData.XValues[cursorIndex] - drawData.getXLeftValue());

        canvas.drawLine(cursorX, 0, cursorX, getHeight(), helperLinePaint);

        final Set<Integer> invisibleLinesIndexes = drawData.getInvisibleLinesIndexes();

        linesMarkerFillPaint.setColor(Color.WHITE);  // TODO: use canvas background color

        for (int i = 0; i < inputData.LinesValues.length; i++) {
            if (invisibleLinesIndexes.contains(i)) {
                continue;
            }

            final float cursorY = drawData.yToPixel(inputData.LinesValues[i][cursorIndex] - drawData.getYMin());

            // граница маркера цветом графика
            canvas.drawCircle(cursorX, cursorY, markerRadius, linesPaints[i]);
            // заполнение маркера цветом фона
            canvas.drawCircle(cursorX, cursorY, markerFillRadius, linesMarkerFillPaint);
        }
    }
}
