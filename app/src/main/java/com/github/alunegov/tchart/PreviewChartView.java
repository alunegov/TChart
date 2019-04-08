package com.github.alunegov.tchart;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;

import org.jetbrains.annotations.NotNull;

public class PreviewChartView extends AbsChartView {
    private static final int FADED_COLOR = Color.parseColor("#C8EDF5FB");

    private static final int FRAME_COLOR = Color.parseColor("#C8DBE7F0");

    private static final float LINE_WIDTH_DP = 1.0f;

    private static final float BORDER_HORIZONTAL_WIDTH_DP = 10f;
    private static final float BORDER_VERTICAL_HEIGHT_DP = 1.5f;

    private static final int TOUCH_SLOP1_DP = 40;
    private static final float TOUCH_SLOP2_PERCENT = 0.1f;

    private float borderHorizontalWidth, borderVerticalHeight;
    private float touchSlop1;

    private OnChangeListener onChangeListener;
    private MoveMode moveMode = MoveMode.NOP;
    private float moveStart;
    private float zoneLeftValue, zoneRightValue;
    private RectF zoneLeftBorder, zoneRightBorder;
    private Bitmap cachedLines;
    private boolean useCachedLines = true;

    // настройки отрисовки скрывающего слоя для зон слева и справа от выбранного диапазона по X
    private Paint fadedPaint;
    // настройки отрисовки рамки выбранного диапазона по X
    private Paint framePaint;

    public PreviewChartView(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context);
    }

    private void init(Context context) {
        final DisplayMetrics dm = context.getResources().getDisplayMetrics();

        lineWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, LINE_WIDTH_DP, dm);
        borderHorizontalWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, BORDER_HORIZONTAL_WIDTH_DP, dm);
        borderVerticalHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, BORDER_VERTICAL_HEIGHT_DP, dm);
        touchSlop1 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TOUCH_SLOP1_DP, dm);

        final int fadedColor = ChartUtils.getThemedColor(context, R.attr.tchart_preview_faded_color, FADED_COLOR);
        final int frameColor = ChartUtils.getThemedColor(context, R.attr.tchart_preview_frame_color, FRAME_COLOR);

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

    @Override
    public void setInputData(@NotNull ChartInputData inputData) {
        super.setInputData(inputData);

        zoneLeftValue = inputData.XValues[0];//inputData.XValues[inputData.XValues.length * 4 / 6];  // TODO: starting zoneLeft?
        zoneRightValue = inputData.XValues[inputData.XValues.length - 1];

        updateZoneLeftBorder(false);
        updateZoneRightBorder(false);

        updateCachedLines();
        useCachedLines = true;

        // оповещение через onChangeListener. если нужно получить зону, то есть getZone

        //invalidate();
    }

    @Override
    public void updateLineVisibility(int lineIndex, int state, boolean doUpdate) {
        super.updateLineVisibility(lineIndex, state, false);

        if (doUpdate) {
            if (state == 0 || state == ChartDrawData.VISIBILITY_STATE_ON) {
                updateCachedLines();
                useCachedLines = true;
            }

            invalidate();
        }
    }

    @Override
    public void setYRange(int yMin, int yMax, boolean doUpdate) {
        useCachedLines = false;
        super.setYRange(yMin, yMax, doUpdate);
    }

    @Override
    protected void drawLines(@NotNull Canvas canvas) {
        if (useCachedLines) {
            assert cachedLines != null;
            canvas.drawBitmap(cachedLines, 0, 0, null);
        } else {
            super.drawLines(canvas);
        }
    }

    @Override
    protected void drawLines2(@NotNull Canvas canvas) {
        if (useCachedLines) {
            assert cachedLines != null;
            canvas.drawBitmap(cachedLines, 0, 0, null);
        } else {
            super.drawLines2(canvas);
        }
    }

    public void getZone(@NotNull float[] zone) {
        if (BuildConfig.DEBUG && (zone.length != 2)) throw new AssertionError();

        zone[0] = zoneLeftValue;
        zone[1] = zoneRightValue;
    }

    private void useCachedLines() {
        if (!useCachedLines) {
            //Log.d("PCV", "useCachedLines");
            updateCachedLines();
            useCachedLines = true;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (drawData == null) {
            return;
        }

        if (BuildConfig.DEBUG && ((getWidth() != w) || (getHeight() != h))) throw new AssertionError();
        drawData.setArea(new RectF(0, 0, w, h));

        updateZoneLeftBorder(true);
        updateZoneRightBorder(true);

        updateCachedLines();
        useCachedLines = true;

        //invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (drawData == null) {
            return true;
        }

        final float x = event.getX();
        float newX, moveDelta, zoneWidth, touchSlop2;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // расширение области для перетаскивания внутри зоны - 10% от ширины зоны.
                touchSlop2 = (zoneRightBorder.right - zoneLeftBorder.left) * TOUCH_SLOP2_PERCENT;

                if (inTouchZone(zoneLeftBorder.right, zoneRightBorder.left, x, -touchSlop2, -touchSlop2)) {
                    moveMode = MoveMode.LEFT_AND_RIGHT;
                } else if (inTouchZone(zoneLeftBorder.left, zoneLeftBorder.right, x, touchSlop1, touchSlop2) && (x < zoneRightBorder.left)) {
                    moveMode = MoveMode.LEFT;
                } else if (inTouchZone(zoneRightBorder.left, zoneRightBorder.right, x, touchSlop2, touchSlop1) && (x > zoneLeftBorder.right)) {
                    moveMode = MoveMode.RIGHT;
                } else {
                    moveMode = MoveMode.NOP;
                }

                if (moveMode != MoveMode.NOP) {
                    moveStart = x;
                }

                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (moveMode != MoveMode.NOP) {
                    moveMode = MoveMode.NOP;
                    horizontalMovement = false;

                    // allow parent to intercept touch events
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                }

                break;

            case MotionEvent.ACTION_MOVE:
                if (moveMode != MoveMode.NOP) {
                    moveDelta = x - moveStart;

                    if (!horizontalMovement) {
                        if (Math.abs(moveDelta) > mTouchSlop) {
                            moveStart = x;
                            horizontalMovement = true;

                            // disallow parent (ScrollView) to intercept touch events while we're moving selection zone
                            if (getParent() != null) {
                                getParent().requestDisallowInterceptTouchEvent(true);
                            }
                        }

                        useCachedLines();

                        break;
                    }

                    if (moveDelta == 0f) {
                        moveStart = x;
                        break;
                    }

                    switch (moveMode) {
                        case LEFT:
                            newX = zoneLeftBorder.left + moveDelta;
                            if (newX < 0) {
                                newX = 0;
                            } else if ((newX + borderHorizontalWidth) > zoneRightBorder.left) {
                                newX = zoneRightBorder.left - borderHorizontalWidth;
                            }

                            zoneLeftValue = drawData.pixelToX(newX);

                            updateZoneLeftBorder(false);

                            break;

                        case RIGHT:
                            newX = zoneRightBorder.right + moveDelta;
                            if ((newX - borderHorizontalWidth) < zoneLeftBorder.right) {
                                newX = zoneLeftBorder.right + borderHorizontalWidth;
                            } else if (newX > getWidth()) {
                                newX = getWidth();
                            }

                            zoneRightValue = drawData.pixelToX(newX);

                            updateZoneRightBorder(false);

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

                            updateZoneLeftBorder(false);
                            updateZoneRightBorder(false);

                            break;
                    }

                    moveStart = x;

                    invalidate();

                    //Log.d("PCV", String.format("action = %d, moveMode = %s", event.getAction(), moveMode.toString()));

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

    private void updateZoneLeftBorder(boolean doVertical) {
        zoneLeftBorder.left = drawData.xToPixel(zoneLeftValue);
        zoneLeftBorder.right = zoneLeftBorder.left + borderHorizontalWidth;
        if (doVertical) {
            zoneLeftBorder.top = 0;
            zoneLeftBorder.bottom = getHeight();
        }
    }

    private void updateZoneRightBorder(boolean doVertical) {
        zoneRightBorder.right = drawData.xToPixel(zoneRightValue);
        zoneRightBorder.left = zoneRightBorder.right - borderHorizontalWidth;
        if (doVertical) {
            zoneRightBorder.top = 0;
            zoneRightBorder.bottom = getHeight();
        }
    }

    private void updateCachedLines() {
        if (getWidth() == 0 || getHeight() == 0) {
            cachedLines = null;
            return;
        }

        cachedLines = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.RGB_565);

        final Canvas canvas = new Canvas(cachedLines);

        final int backColor = ChartUtils.getThemedColor(getContext(), R.attr.app_background_color, Color.WHITE);
        canvas.drawColor(backColor);

        super.drawLines(canvas);
    }

    @Override
    protected void onDraw(Canvas canvas) {
//        synchronized (lock) {
            if (drawData == null) {
                return;
            }

            drawLines(canvas);
            drawLinesFade(canvas);
            drawFrame(canvas);
//        }
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

        // left, vert
        canvas.drawRect(zoneLeftBorder, framePaint);
        // right, vert
        canvas.drawRect(zoneRightBorder, framePaint);
        // top, hor
        canvas.drawRect(zoneLeftBorder.right, 0, zoneRightBorder.left, borderVerticalHeight, framePaint);
        // bottom, hor
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
