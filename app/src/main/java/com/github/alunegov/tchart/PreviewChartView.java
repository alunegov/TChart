package com.github.alunegov.tchart;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.Set;

import org.jetbrains.annotations.NotNull;

public class PreviewChartView extends View {
    private static final int LINE_WIDTH = 2;

    private static final int FADED_COLOR = Color.argb(200, 245, 248, 249);
    private static final int FRAME_COLOR = Color.rgb(219, 231, 240);

    private static final int BORDER_HEIGHT = 3;
    private static final int BORDER_WIDTH = 9;
    private static final int TOUCH_SLOP1 = 50;
    private static final int TOUCH_SLOP2 = 10;

    // Cache the touch slop from the context that created the view.
    private int mTouchSlop;
    private OnChangeListener onChangeListener;
    private ChartDrawData drawData;
    private MoveMode moveMode = MoveMode.NOP;
    private float moveStart;
    private boolean isMoving = false;
    private float zoneLeftValue, zoneRightValue;
    private RectF zoneLeftBorder, zoneRightBorder;
    // настройки отрисовки линий
    private Paint[] linesPaints;
    // настройки отрисовки скрывающего слоя для зон слева и справа от выбранного диапазона по X
    private Paint fadedPaint;
    // настройки отрисовки рамки выбранного диапазона по X
    private Paint framePaint;

    public PreviewChartView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setOnChangeListener(@NotNull OnChangeListener onChangeListener) {
        this.onChangeListener = onChangeListener;
    }

    public void setInputData(@NotNull ChartInputData inputData) {
        drawData = new ChartDrawData(inputData);
        drawData.setXRange(0, inputData.XValues.length - 1);

        zoneLeftValue = inputData.XValues[inputData.XValues.length / 2];  // TODO: starting xLeft?
        zoneRightValue = inputData.XValues[inputData.XValues.length - 1];

        if (onChangeListener != null) {
            onChangeListener.onZoneChanged(zoneLeftValue, zoneRightValue);
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
                if (inTouchZone(zoneLeftBorder.right, zoneRightBorder.left, x, -TOUCH_SLOP2, -TOUCH_SLOP2)) {
                    moveMode = MoveMode.LEFT_AND_RIGHT;
                } else if (inTouchZone(zoneLeftBorder.left, zoneLeftBorder.right, x, TOUCH_SLOP1, TOUCH_SLOP2)) {
                    moveMode = MoveMode.LEFT;
                } else if (inTouchZone(zoneRightBorder.left, zoneRightBorder.right, x, TOUCH_SLOP2, TOUCH_SLOP1)) {
                    moveMode = MoveMode.RIGHT;
                } else {
                    moveMode = MoveMode.NOP;
                }

                if (moveMode != MoveMode.NOP) {
                    moveStart = x;
                }

                break;

            case MotionEvent.ACTION_UP:
                if (moveMode != MoveMode.NOP) {
                    moveMode = MoveMode.NOP;
                    isMoving = false;

                    // allow parent to intercept touch events
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                }

                break;

            case MotionEvent.ACTION_MOVE:
                if (moveMode != MoveMode.NOP) {
                    moveDelta = x - moveStart;

                    if (!isMoving) {
                        if (Math.abs(moveDelta) > mTouchSlop) {
                            moveStart = x;
                            isMoving = true;

                            // disallow parent (ScrollView) to intercept touch events while we're moving selection zone
                            if (getParent() != null) {
                                getParent().requestDisallowInterceptTouchEvent(true);
                            }
                        }
                        break;
                    }

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

                    moveStart = x;

                    invalidate();

                    if (onChangeListener != null) {
                        onChangeListener.onZoneChanged(zoneLeftValue, zoneRightValue);
                    }
                }

                break;
        }

        return true;
    }

    private boolean inTouchZone(float left, float right, float x, float leftSlop, float rightSlop) {
        return ((left - leftSlop) <= x) && (x <= (right + rightSlop));
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

        drawFrame(canvas);
        drawLines(canvas);
        drawLinesFade(canvas);
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

    private void drawLinesFade(@NotNull Canvas canvas) {
        final int w = getWidth();
        final int h = getHeight();

        canvas.drawRect(0, 0, zoneLeftBorder.left, h, fadedPaint);  // left
        canvas.drawRect(zoneRightBorder.right, 0, w, h, fadedPaint);  // right
    }

    private void drawFrame(@NotNull Canvas canvas) {
        final int h = getHeight();

        canvas.drawRect(zoneLeftBorder, framePaint);  // left, hor
        canvas.drawRect(zoneRightBorder, framePaint);  // right, hor
        canvas.drawRect(zoneLeftBorder.right, 0, zoneRightBorder.left, BORDER_HEIGHT, framePaint);  // top, vert
        canvas.drawRect(zoneLeftBorder.right, h - BORDER_HEIGHT, zoneRightBorder.left, h, framePaint);  // bottom, vert
    }

    public interface OnChangeListener {
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
