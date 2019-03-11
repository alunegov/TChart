package com.github.alunegov.tchart;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import org.jetbrains.annotations.NotNull;

public class PreviewChart extends View {
    private static final int LINE_WIDTH = 2;

    private static final int FADED_COLOR = Color.argb(200, 245, 248, 249);
    private static final int FRAME_COLOR = Color.rgb(219, 231, 240);

    private static final int BORDER_HEIGHT = 3;
    private static final int BORDER_WIDTH = 9;
    private static final int TOUCH_SLOPE1 = 50;
    private static final int TOUCH_SLOPE2 = 10;

    private ChangeListener changeListener;
    private ChartDrawData drawData;
    private MoveMode moveMode = MoveMode.NOP;
    private float moveStart;
    private float zoneLeftValue, zoneRightValue;
    private RectF zoneLeftBorder, zoneRightBorder;
    // настройки отрисовки линий
    private Paint[] linesPaints;
    // настройки отрисовки скрывающего слоя для зон слева и справа от выбранного диапазона по X
    private Paint fadedPaint;
    // настройки отрисовки рамки выбранного диапазона по X
    private Paint framePaint;

    public PreviewChart(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
    }

    public void setChangeListener(@NotNull ChangeListener changeListener) {
        this.changeListener = changeListener;
    }

    public void setInputData(@NotNull ChartInputData inputData) {
        drawData = new ChartDrawData(inputData);
        drawData.setXRange(0, inputData.XValues.length - 1);

        zoneLeftValue = inputData.XValues[inputData.XValues.length / 2];  // TODO: starting xLeft?
        zoneRightValue = inputData.XValues[inputData.XValues.length - 1];

        if (changeListener != null) {
            changeListener.onZoneChanged(zoneLeftValue, zoneRightValue);
        }

        // filled on first onSizeChanged
        zoneLeftBorder = new RectF();
        zoneRightBorder = new RectF();

        linesPaints = ChartUtils.makeLinesPaints(inputData.LinesColors, LINE_WIDTH);

        fadedPaint = new Paint();
        fadedPaint.setColor(FADED_COLOR);
        fadedPaint.setStyle(Paint.Style.FILL);

        framePaint = new Paint();
        framePaint.setColor(FRAME_COLOR);
        framePaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                getDefaultSize(100, heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (drawData == null) {
            return;
        }

        drawData.setArea(new RectF(0, 0, w, h));

        float px = drawData.xToPixel(zoneLeftValue - drawData.getXLeftValue());
        zoneLeftBorder.set(px, 0, px + BORDER_WIDTH, h);
        px = drawData.xToPixel(zoneRightValue - drawData.getXLeftValue());
        zoneRightBorder.set(px - BORDER_WIDTH, 0, px, h);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (drawData == null) {
            return true;
        }

        final float x = event.getX();
        float newX, moveDelta, zoneWidth;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (inTouchZone(zoneLeftBorder.left, zoneLeftBorder.right, x, TOUCH_SLOPE1, TOUCH_SLOPE2)) {
                    moveMode = MoveMode.LEFT;
                } else if (inTouchZone(zoneRightBorder.left, zoneRightBorder.right, x, TOUCH_SLOPE2, TOUCH_SLOPE1)) {
                    moveMode = MoveMode.RIGHT;
                } else if (inTouchZone(zoneLeftBorder.right, zoneRightBorder.left, x, 0, 0)) {
                    moveMode = MoveMode.LEFT_AND_RIGHT;
                } else {
                    moveMode = MoveMode.NOP;
                }

                if (moveMode !=  MoveMode.NOP) {
                    moveStart = x;

                    // disallow parent (ScrollView) to intercept touch events while we're moving selection zone
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }

                break;

            case MotionEvent.ACTION_UP:
                moveMode = MoveMode.NOP;

                // allow parent to intercept touch events
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }

                break;

            case MotionEvent.ACTION_MOVE:
                moveDelta = x - moveStart;

                switch (moveMode) {
                    case LEFT:
                        newX = zoneLeftBorder.left + moveDelta;
                        if (newX < 0) {
                            newX = 0;
                        } else if (newX > zoneRightBorder.left) {
                            newX = zoneRightBorder.left;
                        }

                        zoneLeftValue = drawData.pixelToX(newX);

                        updateZoneLeftBorder();

                        break;

                    case RIGHT:
                        newX = zoneRightBorder.right + moveDelta;
                        if (newX < zoneLeftBorder.right) {
                            newX = zoneLeftBorder.right;
                        } else if (newX > getWidth()) {
                            newX = getWidth();
                        }

                        zoneRightValue = drawData.pixelToX(newX);

                        updateZoneRightBorder();

                        break;

                    case LEFT_AND_RIGHT:
                        zoneWidth = zoneRightBorder.right - zoneLeftBorder.left;

                        newX = zoneLeftBorder.left + moveDelta;
                        if (newX < 0) {
                            newX = 0;
                        } else if ((newX + zoneWidth) > getWidth()) {
                            newX = getWidth() - zoneWidth;
                        }

                        zoneLeftValue = drawData.pixelToX(newX);
                        zoneRightValue = drawData.pixelToX(newX + zoneWidth);

                        updateZoneLeftBorder();
                        updateZoneRightBorder();

                        break;
                }

                if (moveMode !=  MoveMode.NOP) {
                    moveStart = x;

                    invalidate();

                    if (changeListener != null) {
                        changeListener.onZoneChanged(zoneLeftValue, zoneRightValue);
                    }
                }

                break;
        }

        return true;
    }

    private boolean inTouchZone(float left, float right, float x, float leftSlope, float rightSlope) {
        return ((left - leftSlope) <= x) && (x <= (right + rightSlope));
    }

    private void updateZoneLeftBorder() {
        zoneLeftBorder.left = drawData.xToPixel(zoneLeftValue - drawData.getXLeftValue());
        zoneLeftBorder.right = zoneLeftBorder.left + BORDER_WIDTH;
    }

    private void updateZoneRightBorder() {
        zoneRightBorder.right = drawData.xToPixel(zoneRightValue - drawData.getXLeftValue());
        zoneRightBorder.left = zoneRightBorder.right - BORDER_WIDTH;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (drawData == null) {
            return;
        }

        drawFrame(canvas, framePaint);
        drawLines(canvas, drawData.getLinesPaths(), linesPaints);
        drawLinesFade(canvas, fadedPaint);
    }

    private void drawLines(@NotNull Canvas canvas, @NotNull Path[] paths, @NotNull Paint[] paints) {
        for (int j = 0; j < paths.length; j++) {
            canvas.drawPath(paths[j], paints[j]);
        }
    }

    private void drawLinesFade(@NotNull Canvas canvas, @NotNull Paint paint) {
        canvas.drawRect(0, 0, zoneLeftBorder.left, getHeight(), paint);  // left
        canvas.drawRect(zoneRightBorder.right, 0, getWidth(), getHeight(), paint);  // right
    }

    private void drawFrame(@NotNull Canvas canvas, @NotNull Paint paint) {
        canvas.drawRect(zoneLeftBorder, paint);  // left, hor
        canvas.drawRect(zoneRightBorder, paint);  // right, hor
        canvas.drawRect(zoneLeftBorder.right, 0, zoneRightBorder.left, BORDER_HEIGHT, paint);  // top, vert
        canvas.drawRect(zoneLeftBorder.right, getHeight() - BORDER_HEIGHT, zoneRightBorder.left, getHeight(), paint);  // bottom, vert
    }

    public interface ChangeListener {
        void onZoneChanged(float zoneLeftValue, float zoneRightValue);
    }

    // Режим перемещения выделения (диапазона по X)
    private enum MoveMode {
        // nop
        NOP,
        // левая граница
        LEFT,
        // правая граница
        RIGHT,
        // левая и правая границы
        LEFT_AND_RIGHT,
    }
}
