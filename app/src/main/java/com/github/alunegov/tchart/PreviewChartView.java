package com.github.alunegov.tchart;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.*;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import org.jetbrains.annotations.NotNull;

public class PreviewChartView extends AbsChartView {
    private static final int FADED_COLOR = Color.parseColor("#C8F5F8F9");

    private static final int FRAME_COLOR = Color.parseColor("#DBE7F0");

    private static final float LINE_WIDTH_DP = 1.0f;

    private static final float BORDER_HORIZONTAL_WIDTH_DP = 4f;
    private static final float BORDER_VERTICAL_HEIGHT_DP = 1.5f;

    private static final int TOUCH_SLOP1_DP = 40;
    private static final int TOUCH_SLOP2_DP = 10;

    // Cache the touch slop from the context that created the view.
    private int mTouchSlop;

    private float lineWidth;
    private float borderHorizontalWidth, borderVerticalHeight;
    private float touchSlop1, touchSlop2;

    private OnChangeListener onChangeListener;
    private MoveMode moveMode = MoveMode.NOP;
    private float moveStart;
    private boolean isMoving = false;
    private float zoneLeftValue, zoneRightValue;
    private RectF zoneLeftBorder, zoneRightBorder;

    // настройки отрисовки скрывающего слоя для зон слева и справа от выбранного диапазона по X
    private Paint fadedPaint;
    // настройки отрисовки рамки выбранного диапазона по X
    private Paint framePaint;

    public PreviewChartView(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context);
    }

    private void init(Context context) {
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        final DisplayMetrics dm = context.getResources().getDisplayMetrics();

        lineWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, LINE_WIDTH_DP, dm);
        borderHorizontalWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, BORDER_HORIZONTAL_WIDTH_DP, dm);
        borderVerticalHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, BORDER_VERTICAL_HEIGHT_DP, dm);
        touchSlop1 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TOUCH_SLOP1_DP, dm);
        touchSlop2 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TOUCH_SLOP2_DP, dm);

        int fadedColor;
        try {
            fadedColor = ContextCompat.getColor(context, R.color.tchart_preview_faded);
        } catch (Resources.NotFoundException e) {
            fadedColor = FADED_COLOR;
        }

        int frameColor;
        try {
            frameColor = ContextCompat.getColor(context, R.color.tchart_preview_frame);
        } catch (Resources.NotFoundException e) {
            frameColor = FRAME_COLOR;
        }

        // filled on first onSizeChanged
        zoneLeftBorder = new RectF();
        zoneRightBorder = new RectF();

        fadedPaint = new Paint();
        fadedPaint.setColor(fadedColor);
        fadedPaint.setStyle(Paint.Style.FILL);

        framePaint = new Paint();
        framePaint.setColor(frameColor);
        framePaint.setStyle(Paint.Style.FILL);
    }

    public void setOnChangeListener(@NotNull OnChangeListener onChangeListener) {
        this.onChangeListener = onChangeListener;
    }

    public void setInputData(@NotNull ChartInputData inputData) {
        drawData = new ChartDrawData(inputData);
        drawData.setXRange(0, inputData.XValues.length - 1);

        zoneLeftValue = inputData.XValues[inputData.XValues.length * 4 / 6];  // TODO: starting zoneLeft?
        zoneRightValue = inputData.XValues[inputData.XValues.length - 1];
        if (onChangeListener != null) {
            onChangeListener.onZoneChanged(zoneLeftValue, zoneRightValue);
        }

        linesPaints = ChartUtils.makeLinesPaints(inputData.LinesColors, lineWidth);

        //invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (drawData == null) {
            return;
        }

        drawData.setArea(new RectF(0, 0, w, h));

        float px = drawData.xToPixel(zoneLeftValue);
        zoneLeftBorder.set(px, 0, px + borderHorizontalWidth, h);
        px = drawData.xToPixel(zoneRightValue);
        zoneRightBorder.set(px - borderHorizontalWidth, 0, px, h);

        //invalidate();
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
                if (inTouchZone(zoneLeftBorder.right, zoneRightBorder.left, x, -touchSlop2, -touchSlop2)) {
                    moveMode = MoveMode.LEFT_AND_RIGHT;
                } else if (inTouchZone(zoneLeftBorder.left, zoneLeftBorder.right, x, touchSlop1, touchSlop2)) {
                    moveMode = MoveMode.LEFT;
                } else if (inTouchZone(zoneRightBorder.left, zoneRightBorder.right, x, touchSlop2, touchSlop1)) {
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
        zoneLeftBorder.left = drawData.xToPixel(zoneLeftValue);
        zoneLeftBorder.right = zoneLeftBorder.left + borderHorizontalWidth;
    }

    private void updateZoneRightBorder() {
        zoneRightBorder.right = drawData.xToPixel(zoneRightValue);
        zoneRightBorder.left = zoneRightBorder.right - borderHorizontalWidth;
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

    private void drawLinesFade(@NotNull Canvas canvas) {
        final int w = getWidth();
        final int h = getHeight();

        // left
        canvas.drawRect(0, 0, zoneLeftBorder.left, h, fadedPaint);
        // right
        canvas.drawRect(zoneRightBorder.right, 0, w, h, fadedPaint);
    }

    private void drawFrame(@NotNull Canvas canvas) {
        final int h = getHeight();

        // left, hor
        canvas.drawRect(zoneLeftBorder, framePaint);
        // right, hor
        canvas.drawRect(zoneRightBorder, framePaint);
        // top, vert
        canvas.drawRect(zoneLeftBorder.right, 0, zoneRightBorder.left, borderVerticalHeight, framePaint);
        // bottom, vert
        canvas.drawRect(zoneLeftBorder.right, h - borderVerticalHeight, zoneRightBorder.left, h, framePaint);
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
