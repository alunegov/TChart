package com.github.alunegov.tchart;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;

import org.jetbrains.annotations.NotNull;

public class PreviewChartView extends AbsChartView {
    // Scroll Background
    // Light - E2EEF9, 60%  Dark - 304259, 60%
    private static final int FADED_COLOR = Color.parseColor("#99E2EEF9");

    // Scroll Selector (overlays the value above)
    // Light - 86A9C4, 50%  Dark - 6F899E, 50%
    private static final int FRAME_COLOR = Color.parseColor("#7F86A9C4");

    private static final float LINE_WIDTH_DP = 1.0f;

    private static final float BORDER_VERTICAL_WIDTH_DP = 10f;
    private static final float BORDER_HORIZONTAL_HEIGHT_DP = 1.5f;

    private static final int TOUCH_SLOP1_DP = 40;
    private static final float TOUCH_SLOP2_PERCENT = 0.1f;

    private static final float FRAME_CORNER_RADIUS = 6f;

    private static final float TICK_CORNER_RADIUS = 2f;

    private float borderVerticalWidth;
    private int borderHorizontalHeight;
    private float touchSlop1;
    // радиус скругления всего вида (faded) и зоны выбранного диапазона (frame)
    private float frameCornerRadius;
    // полу-ширина засечки на границах диапазона (зависит от borderVerticalWidth)
    private float tickHalfWidth;
    // полу-высота засечки на границах диапазона (зависит от высоты вида)
    private float tickHalfHeight;
    // радиус скругления засечки
    private float tickCornerRadius;

    private OnChangeListener onChangeListener;
    private MoveMode moveMode = MoveMode.NOP;
    private float moveStart;
    private float zoneLeftValue, zoneRightValue;
    private RectF zoneLeftBorder, zoneRightBorder;
    private Bitmap cachedLines = null;
    private boolean useCachedLines = true;

    // настройки отрисовки скрывающего слоя для зон слева и справа от выбранного диапазона по X
    private Paint fadedPaint;
    // настройки отрисовки рамки выбранного диапазона по X
    private Paint framePaint;
    //
    private Paint tickPaint;

    public PreviewChartView(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context);
    }

    private void init(Context context) {
        final DisplayMetrics dm = context.getResources().getDisplayMetrics();

        lineWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, LINE_WIDTH_DP, dm);
        borderVerticalWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, BORDER_VERTICAL_WIDTH_DP, dm);
        final float tmpHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, BORDER_HORIZONTAL_HEIGHT_DP, dm);
        borderHorizontalHeight = Math.round(tmpHeight);
        if (borderHorizontalHeight <= 0) {
            borderHorizontalHeight = 1;
        }
        touchSlop1 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TOUCH_SLOP1_DP, dm);
        frameCornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, FRAME_CORNER_RADIUS, dm);
        tickHalfWidth = borderVerticalWidth / 10f;
        tickHalfHeight = 8f;  // обновляется в onSizeChanged
        tickCornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TICK_CORNER_RADIUS, dm);

        final int fadedColor = ChartUtils.getThemedColor(context, R.attr.tchart_preview_faded_color, FADED_COLOR);
        final int frameColor = ChartUtils.getThemedColor(context, R.attr.tchart_preview_frame_color, FRAME_COLOR);

        // filled on first onSizeChanged
        zoneLeftBorder = new RectF();
        zoneRightBorder = new RectF();

        fadedPaint = new Paint();
        fadedPaint.setAntiAlias(true);
        fadedPaint.setColor(fadedColor);
        fadedPaint.setStyle(Paint.Style.FILL);

        framePaint = new Paint(fadedPaint);
        framePaint.setColor(frameColor);

        tickPaint = new Paint(fadedPaint);
        tickPaint.setColor(Color.WHITE);
    }

    public void setOnChangeListener(@NotNull OnChangeListener onChangeListener) {
        this.onChangeListener = onChangeListener;
    }

    @Override
    public void setInputData(@NotNull ChartInputData inputData, @NotNull ChartInputDataStats inputDataStats) {
        super.setInputData(inputData, inputDataStats);

        zoneLeftValue = inputData.XValues[0];//inputData.XValues[inputData.XValues.length * 4 / 6];  // TODO: starting zoneLeft?
        zoneRightValue = inputData.XValues[inputData.XValues.length - 1];

        updateZoneLeftBorder(false);
        updateZoneRightBorder(false);

        useCachedLines(true);

        // оповещение через onChangeListener. если нужно получить зону, то есть getZone

        //invalidate();
    }

/*    @Override
    public void updateLineVisibility(int lineIndex, boolean exceptLine, int state, boolean doUpdate, boolean doInvalidate) {
        super.updateLineVisibility(lineIndex, exceptLine, state, doUpdate, false);

        // TODO: обновляем кэш-картинку в конце анимации (0 или 255 в зависимости от направления)
        /*if (state == ChartDrawData.VISIBILITY_STATE_OFF || state == ChartDrawData.VISIBILITY_STATE_ON) {
            useCachedLines(false);
        }*//*

        if (doInvalidate) {
            invalidate();
        }
    }*/

    @Override
    public void setYRange(int yLeftMin, int yLeftMax, int yRightMin, int yRightMax, boolean doUpdateAndInvalidate) {
        useCachedLines = false;
        super.setYRange(yLeftMin, yLeftMax, yRightMin, yRightMax, doUpdateAndInvalidate);
    }

    @Override
    protected void drawLines(@NotNull Canvas canvas) {
        if (useCachedLines) {
            if (BuildConfig.DEBUG && (cachedLines == null)) throw new AssertionError();
            canvas.drawBitmap(cachedLines, 0, 0, null);
        } else {
            super.drawLines(canvas);
        }
    }

/*    @Override
    protected void drawLines2(@NotNull Canvas canvas) {
        if (useCachedLines) {
            if (BuildConfig.DEBUG && (cachedLines == null)) throw new AssertionError();
            canvas.drawBitmap(cachedLines, 0, 0, null);
        } else {
            super.drawLines2(canvas);
        }
    }*/

    public void getZone(@NotNull float[] zone) {
        if (BuildConfig.DEBUG && (zone.length != 2)) throw new AssertionError();

        zone[0] = zoneLeftValue;
        zone[1] = zoneRightValue;
    }

    private void useCachedLines(boolean force) {
        if (!useCachedLines || force) {
            useCachedLines = updateCachedLines();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (drawData == null) {
            return;
        }

        if (BuildConfig.DEBUG && ((getWidth() != w) || (getHeight() != h))) throw new AssertionError();
        drawData.setArea(new RectF(0, borderHorizontalHeight, w, h - borderHorizontalHeight));

        updateZoneLeftBorder(true);
        updateZoneRightBorder(true);

        tickHalfHeight = zoneLeftBorder.height() / 7f;

        cachedLines = null;  // чтобы пересоздать кэш-картинку с новыми размерами
        useCachedLines(true);

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

                        useCachedLines(false);

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
                            } else if ((newX + borderVerticalWidth) > zoneRightBorder.left) {
                                newX = zoneRightBorder.left - borderVerticalWidth;
                            }

                            zoneLeftValue = drawData.pixelToX(newX);

                            updateZoneLeftBorder(false);

                            break;

                        case RIGHT:
                            newX = zoneRightBorder.right + moveDelta;
                            if ((newX - borderVerticalWidth) < zoneLeftBorder.right) {
                                newX = zoneLeftBorder.right + borderVerticalWidth;
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
        zoneLeftBorder.right = zoneLeftBorder.left + borderVerticalWidth;
        if (doVertical) {
            zoneLeftBorder.top = 0;
            zoneLeftBorder.bottom = getHeight();
        }
    }

    private void updateZoneRightBorder(boolean doVertical) {
        zoneRightBorder.right = drawData.xToPixel(zoneRightValue);
        zoneRightBorder.left = zoneRightBorder.right - borderVerticalWidth;
        if (doVertical) {
            zoneRightBorder.top = 0;
            zoneRightBorder.bottom = getHeight();
        }
    }

    private boolean updateCachedLines() {
        //Log.d("PCV", "updateCachedLines");

        if (getWidth() == 0 || getHeight() == 0) {
            cachedLines = null;
            return false;
        }

        if (cachedLines == null) {
            cachedLines = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.RGB_565);
        }

        final Canvas canvas = new Canvas(cachedLines);

        final int backColor = ChartUtils.getThemedColor(getContext(), R.attr.app_background_color, Color.WHITE);
        canvas.drawColor(backColor);

        super.drawLines(canvas);

        return true;
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

    private final @NotNull RectF tmpRect = new RectF();

    private void drawLinesFade(@NotNull Canvas canvas) {
        final int w = getWidth();
        final int h = getHeight() - borderHorizontalHeight;

        //fadedPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_ATOP));

        // left
        tmpRect.set(0, borderHorizontalHeight, zoneLeftBorder.right, h);
        //drawLeftRoundedRect(canvas, tmpRect, frameCornerRadius, fadedPaint);
        canvas.drawRect(tmpRect, fadedPaint);
        // right
        tmpRect.set(zoneRightBorder.left, borderHorizontalHeight, w, h);
        //drawRightRoundedRect(canvas, tmpRect, frameCornerRadius, fadedPaint);
        canvas.drawRect(tmpRect, fadedPaint);
    }

    private final Path tmpPath = new Path();

    private void drawFrame(@NotNull Canvas canvas) {
        final int h = getHeight();

        // left, vert
        drawLeftRoundedRect(canvas, zoneLeftBorder, frameCornerRadius, framePaint);
        // right, vert
        drawRightRoundedRect(canvas, zoneRightBorder, frameCornerRadius, framePaint);
        // top, hor
        canvas.drawRect(zoneLeftBorder.right, 0, zoneRightBorder.left, borderHorizontalHeight, framePaint);
        // bottom, hor
        canvas.drawRect(zoneLeftBorder.right, h - borderHorizontalHeight, zoneRightBorder.left, h, framePaint);

        // tick, left
        tmpRect.set(zoneLeftBorder.centerX() - tickHalfWidth, zoneLeftBorder.centerY() - tickHalfHeight,
                zoneLeftBorder.centerX() + tickHalfWidth, zoneLeftBorder.centerY() + tickHalfHeight);
        canvas.drawRoundRect(tmpRect, tickCornerRadius, tickCornerRadius, tickPaint);
        // tick, right
        tmpRect.left = zoneRightBorder.centerX() - tickHalfWidth;
        tmpRect.right = zoneRightBorder.centerX() + tickHalfWidth;
        canvas.drawRoundRect(tmpRect, tickCornerRadius, tickCornerRadius, tickPaint);
    }

    private final @NotNull RectF drawRoundedRect = new RectF();

    private void drawLeftRoundedRect(Canvas canvas, RectF rect, float r, Paint paint) {
        tmpPath.reset();
        tmpPath.moveTo(rect.right, rect.bottom);
        tmpPath.lineTo(rect.left - r, rect.bottom);
        drawRoundedRect.set(rect.left, rect.bottom - 2 * r, rect.left + 2 * r, rect.bottom);
        tmpPath.arcTo(drawRoundedRect, 90, 90);
        tmpPath.lineTo(rect.left, rect.top - r);
        drawRoundedRect.set(rect.left, rect.top, rect.left + 2 * r, rect.top + 2 * r);
        tmpPath.arcTo(drawRoundedRect, 180, 90);
        tmpPath.lineTo(rect.right, rect.top);
        tmpPath.close();
        canvas.drawPath(tmpPath, paint);
    }

    private void drawRightRoundedRect(Canvas canvas, RectF rect, float r, Paint paint) {
        tmpPath.reset();
        tmpPath.moveTo(rect.left, rect.top);
        tmpPath.lineTo(rect.right - r, rect.top);
        drawRoundedRect.set(rect.right - 2 * r, rect.top, rect.right, rect.top + 2 * r);
        tmpPath.arcTo(drawRoundedRect, 270, 90);
        tmpPath.lineTo(rect.right, rect.bottom - r);
        drawRoundedRect.set(rect.right - 2 * r, rect.bottom - 2 * r, rect.right, rect.bottom);
        tmpPath.arcTo(drawRoundedRect, 0, 90);
        tmpPath.lineTo(rect.left, rect.bottom);
        tmpPath.close();
        canvas.drawPath(tmpPath, paint);
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
