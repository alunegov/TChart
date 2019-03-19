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

public class MainChartView extends AbsChartView {
    private static final int NO_CURSOR = -1;

    private static final String NO_DATA = "NO DATA";

    private static final int AXIS_LINES_COUNT = 5;

    private static final float LINE_WIDTH_DP = 2.0f;

    private static final int AXIS_TEXT_SIZE_SP = 12;
    private static final int AXIS_TEXT_COLOR = Color.parseColor("#96A2AA");

    private static final int AXIS_LINE_COLOR = Color.parseColor("#F1F1F2");
    private static final float AXIS_LINE_WIDTH_DP = 1.0f;

    private static final int CURSOR_LINE_COLOR = Color.parseColor("#E5EBEF");
    private static final float CURSOR_LINE_WIDTH_DP = 1.0f;

    private static final int MARKER_RADIUS_DP = 4;

    private static final int Y_AXIS_TEXT_VERTICAL_MARGIN_DP = 7;
    private static final int X_AXIS_TEXT_VERTICAL_MARGIN_DP = 3;

    private float lineWidth;
    private float markerRadius, markerFillRadius;
    private float yAxisTextVerticalMargin;
    private float xAxisTextVerticalMargin;

    // индекс точки с курсором
    private int cursorIndex = NO_CURSOR;
    // высота области графика (высота вида минус текст x-оси)
    private float graphAreaHeight;

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
        final float axisTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, AXIS_TEXT_SIZE_SP, dm);
        final float axisLineWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, AXIS_LINE_WIDTH_DP, dm);
        final float cursorLineWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, CURSOR_LINE_WIDTH_DP, dm);
        markerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, MARKER_RADIUS_DP, dm);
        markerFillRadius = markerRadius - lineWidth / 2;
        xAxisTextVerticalMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, X_AXIS_TEXT_VERTICAL_MARGIN_DP, dm);
        yAxisTextVerticalMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, Y_AXIS_TEXT_VERTICAL_MARGIN_DP, dm);

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

        updateGraphAreaHeight();
    }

    public void setAxisTextSize(float px) {
        axisTextPaint.setTextSize(px);

        updateGraphAreaHeight();

        //invalidate();
    }

    public void setInputData(@NotNull ChartInputData inputData) {
        this.inputData = inputData;

        drawData = new ChartDrawData(inputData);
        drawData.setXRange(0, inputData.XValues.length - 1);

        linesPaints = ChartUtils.makeLinesPaints(inputData.LinesColors, lineWidth);

        //invalidate();
    }

    public void setXRange(float xLeftValue, float xRightValue) {
        if (drawData == null) {
            return;
        }

        drawData.setXRange(xLeftValue, xRightValue);

        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (drawData == null) {
            return;
        }

        assert getWidth() == w;
        assert getHeight() == h;
        updateGraphAreaHeight();

        //invalidate();
    }

    // пересчёт высоты области для вывода сигналов (немного уменьшаем высоту, чтобы выводить в нижней части вида x-значения)
    // вызывается при изменении размеров вида, размера текста надписей оси
    private void updateGraphAreaHeight() {
        Paint.FontMetrics fm = axisTextPaint.getFontMetrics();

        final float axisTextHeight = fm.descent - fm.ascent;

        graphAreaHeight = getHeight() - 2 * xAxisTextVerticalMargin - axisTextHeight;

        if (drawData != null) {
            drawData.setArea(new RectF(0, 0, getWidth(), graphAreaHeight));
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (drawData == null) {
            return true;
        }
        // ничего не делаем, если нет видимых сигналов
        if (drawData.getIsAllLinesInvisible()) {
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

    private int findCursorIndex(float cursorXValue) {
        // TODO: find cursor near selected point
        return drawData.findXLeftIndex(cursorXValue);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (drawData == null) {
            return;
        }

        drawXAxis(canvas);

        // если нет видимых сигналов, оставляем xAxis и выводим текст NO_DATA по центру области графика
        if (drawData.getIsAllLinesInvisible()) {
            final float x = getWidth() / 2f - axisTextPaint.measureText(NO_DATA) / 2f;
            final float y = graphAreaHeight / 2f;

            canvas.drawText(NO_DATA, x, y, axisTextPaint);

            return;
        }

        drawYAxis(canvas);
        drawLines(canvas);
        drawCursor(canvas);
    }

    private void drawXAxis(@NotNull Canvas canvas) {
        final float xSwing = Math.abs(drawData.getXRightValue() - drawData.getXLeftValue());

        final long stepValue = (long) (xSwing / AXIS_LINES_COUNT);
        // TODO: beautify step?

        final float stepPixel = stepValue * drawData.getXScale();
        if (stepPixel <= 0) {
            return;
        }

        final long startXValue = (long) (drawData.getXLeftValue() / stepValue) * stepValue;
        final float startXPixel = drawData.xToPixel(startXValue);

        final float textY = getHeight() - xAxisTextVerticalMargin;
        final int w = getWidth();

        final SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());

        long i = startXValue;
        for (float x = startXPixel; x < w; x += stepPixel) {
            final String s = sdf.format(new Date(i * 1000L));
            canvas.drawText(s, x, textY, axisTextPaint);

            i += stepValue;
        }
    }

    private void drawYAxis(@NotNull Canvas canvas) {
        final float ySwing = Math.abs(drawData.getYMax() - drawData.getYMin());

        int stepValue = (int) (ySwing / AXIS_LINES_COUNT);
        // TODO: beautify step
//        stepValue = stepValue / 10 * 10;

        final float stepPixel = stepValue * drawData.getYScale();
        if (stepPixel <= 0) {
            return;
        }

        int startYValue = drawData.getYMin() / stepValue * stepValue;
        if (startYValue < drawData.getYMin()) {
            startYValue += stepValue;
        }
        final float startYPixel = drawData.yToPixel(startYValue);

        final int w = getWidth();

        int i = startYValue;
        for (float y = startYPixel; y >= 0; y -= stepPixel) {
            canvas.drawLine(0, y, w, y, yAxisLinePaint);

            final String s = String.valueOf(i);
            canvas.drawText(s, 0, y - yAxisTextVerticalMargin, axisTextPaint);

            i += stepValue;
        }
    }

    private void drawCursor(@NotNull Canvas canvas) {
        if (cursorIndex == NO_CURSOR) {
            return;
        }

        final float cursorX = drawData.xToPixel(inputData.XValues[cursorIndex]);

        canvas.drawLine(cursorX, 0, cursorX, graphAreaHeight, helperLinePaint);

        final Set<Integer> invisibleLinesIndexes = drawData.getInvisibleLinesIndexes();

        //linesMarkerFillPaint.setColor(Color.WHITE);  // TODO: use canvas background color

        for (int i = 0; i < inputData.LinesValues.length; i++) {
            if (invisibleLinesIndexes.contains(i)) {
                continue;
            }

            final float cursorY = drawData.yToPixel(inputData.LinesValues[i][cursorIndex]);

            // граница маркера цветом графика
            canvas.drawCircle(cursorX, cursorY, markerRadius, linesPaints[i]);
            // заполнение маркера цветом фона
            canvas.drawCircle(cursorX, cursorY, markerFillRadius, linesMarkerFillPaint);
        }
    }
}
