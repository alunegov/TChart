package com.github.alunegov.tchart;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

public class MainChartView extends View {
    private static final int NO_CURSOR = -1;

    private static final int AXIS_LINES_COUNT = 5;

    private static final int LINE_WIDTH = 4;

    private static final int AXIS_TEXT_COLOR = Color.rgb(152, 162, 170);
    private static final int Y_AXIS_LINE_COLOR = Color.rgb(241, 241, 242);
    private static final int CURSOR_LINE_COLOR = Color.rgb(229, 235, 239);
    private static final int CURSOR_LINE_WIDTH = 2;

    private static final int MARKER_RADIUS = 9;
    private static final int MARKER_FILL_RADIUS = MARKER_RADIUS - LINE_WIDTH / 2;

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
    }

    public void setInputData(@NotNull ChartInputData inputData) {
        this.inputData = inputData;

        drawData = new ChartDrawData(inputData);
        drawData.setXRange(0, inputData.XValues.length - 1);

        linesPaints = ChartUtils.makeLinesPaints(inputData.LinesColors, LINE_WIDTH);

        linesMarkerFillPaint = new Paint();
        linesMarkerFillPaint.setAntiAlias(true);
        linesMarkerFillPaint.setColor(Color.WHITE);  // set in onDraw
        linesMarkerFillPaint.setStyle(Paint.Style.FILL);

        axisTextPaint = new Paint();
        axisTextPaint.setAntiAlias(true);
        axisTextPaint.setColor(AXIS_TEXT_COLOR);
        axisTextPaint.setStyle(Paint.Style.FILL);
        axisTextPaint.setTextSize(30);

        yAxisLinePaint = ChartUtils.makeLinePaint(Y_AXIS_LINE_COLOR, 1);

        helperLinePaint = ChartUtils.makeLinePaint(CURSOR_LINE_COLOR, CURSOR_LINE_WIDTH);
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

        long stepValue = (long) xSwing / 4;
        //stepValue = 50;

        final int stepPixel =  (int) drawData.xToPixel(stepValue);
        if (stepPixel == 0) {
            return;
        }

        int i = 0;
        for (int x = 0; x < getWidth(); x = x + stepPixel) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd");
            final String s = sdf.format(new Date(i * stepValue * 1000L));
            canvas.drawText(s, x, getHeight(), axisTextPaint);

            i++;
        }
    }

    private void drawYAxis(@NotNull Canvas canvas) {
        final float ySwing = Math.abs(drawData.getYMax() - drawData.getYMin());

        int stepValue = (int) ySwing / AXIS_LINES_COUNT;
        //stepValue = 50;

        final int stepPixel =  (int) drawData.pixelToY(stepValue);
        if (stepPixel == 0) {
            return;
        }

        int i = 0;
        for (int y = 0; y < getHeight(); y = y + stepPixel) {
            canvas.drawLine(0, getHeight() - y, getWidth(), getHeight() - y, yAxisLinePaint);

            final String s = String.valueOf(i * stepValue);
            canvas.drawText(s, 0, getHeight() - y - 20, axisTextPaint);

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

        linesMarkerFillPaint.setColor(Color.WHITE);  // TODO: use canvas color
        for (int i = 0; i < inputData.LinesValues.length; i++) {
            final float cursorY = drawData.yToPixel(inputData.LinesValues[i][cursorIndex] - drawData.getYMin());

            // граница маркера цветом графика
            canvas.drawCircle(cursorX, cursorY, MARKER_RADIUS, linesPaints[i]);
            // заполнение маркера цветом фона
            canvas.drawCircle(cursorX, cursorY, MARKER_FILL_RADIUS, linesMarkerFillPaint);
        }
    }
}
