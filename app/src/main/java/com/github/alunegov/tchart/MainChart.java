package com.github.alunegov.tchart;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import org.jetbrains.annotations.NotNull;

public class MainChart extends View {
    private static final int NO_CURSOR = -1;

    private static final int LINE_WIDTH = 4;

    private static final int HELPER_LINE_COLOR = Color.rgb(229, 235, 239);
    private static final int HELPER_LINE_WIDTH = 2;

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
    // настройки отрисовки вспомогательных линий (оси, курсор)
    private Paint helperLinePaint;

    public MainChart(Context context, AttributeSet attrs) {
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

        helperLinePaint = ChartUtils.makeLinePaint(HELPER_LINE_COLOR, HELPER_LINE_WIDTH);
    }

    public void setXRange(float xLeftValue, float xRightValue) {
        if (drawData == null) {
            return;
        }

        drawData.setXRange(xLeftValue, xRightValue);

        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                getDefaultSize(600, heightMeasureSpec));
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

        drawXAxis(canvas, helperLinePaint);
        drawYAxis(canvas, helperLinePaint);
        drawLines(canvas, drawData.getLinesPaths(), linesPaints);
        drawCursor(canvas);
    }

    private void drawXAxis(@NotNull Canvas canvas, Paint paint) {

    }

    private void drawYAxis(@NotNull Canvas canvas, Paint paint) {

        for (int i = 0; i < 5; i++) {
            final float y = getHeight() - 100 * i;

            canvas.drawLine(0, y, getWidth(), y, paint);

            final String s = String.valueOf(i);
            canvas.drawText(s, 0, y - 20, paint);
        }
    }

    private void drawLines(@NotNull Canvas canvas, @NotNull Path[] paths, @NotNull Paint[] paints) {
        for (int i = 0; i < paths.length; i++) {
            canvas.drawPath(paths[i], paints[i]);
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
