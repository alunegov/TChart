package com.github.alunegov.tchart;

import android.content.Context;
import android.graphics.*;
import android.support.v7.widget.ViewUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.*;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.*;

import org.jetbrains.annotations.NotNull;

public class MainChartView extends AbsChartView {
    private static final int NO_CURSOR = -1;

    private static final String NO_DATA = "NO VISIBLE LINES";

    private static final int AXIS_LINES_COUNT = 5;

    private static final float LINE_WIDTH_DP = 2.0f;

    private static final int AXIS_TEXT_SIZE_SP = 12;
    private static final int AXIS_TEXT_COLOR = Color.parseColor("#8E8E93");

    private static final int AXIS_LINE_COLOR = Color.parseColor("#E7E9EB");
    private static final float AXIS_LINE_WIDTH_DP = 1.0f;

    private static final int CURSOR_LINE_COLOR = Color.parseColor("#E7E9EB");
    private static final float CURSOR_LINE_WIDTH_DP = 1.0f;

    private static final int CURSOR_POPUP_START_MARGIN_DP = 15;

    private static final int MARKER_RADIUS_DP = 4;

    private static final int Y_AXIS_TEXT_VERTICAL_MARGIN_DP = 7;
    private static final int X_AXIS_TEXT_VERTICAL_MARGIN_DP = 3;

    private GestureDetector gestureDetector;

    private float cursorPopupStartMargin;
    private float markerRadius, markerFillRadius;
    private float yAxisTextVerticalMargin;
    private float xAxisTextVerticalMargin;

    // индекс точки с курсором
    private int cursorIndex = NO_CURSOR;
    // высота области графика (высота вида минус текст x-оси)
    private float graphAreaHeight;

    // popup для отображения значений курсора
    private View cursorPopupView;
    private TextView cursorDateTextView;
    private LinearLayout cursorValuesLayout;
    // Преобразователь значения в текст для x-значений курсора
    private XAxisConverter cursorDateCnv;

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
        gestureDetector = new GestureDetector(getContext(), new OnGestureListener());
        gestureDetector.setIsLongpressEnabled(false);

        final DisplayMetrics dm = context.getResources().getDisplayMetrics();

        lineWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, LINE_WIDTH_DP, dm);
        cursorPopupStartMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, CURSOR_POPUP_START_MARGIN_DP, dm);
        final float axisTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, AXIS_TEXT_SIZE_SP, dm);
        final float axisLineWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, AXIS_LINE_WIDTH_DP, dm);
        final float cursorLineWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, CURSOR_LINE_WIDTH_DP, dm);
        markerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, MARKER_RADIUS_DP, dm);
        markerFillRadius = markerRadius - lineWidth / 2;
        xAxisTextVerticalMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, X_AXIS_TEXT_VERTICAL_MARGIN_DP, dm);
        yAxisTextVerticalMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, Y_AXIS_TEXT_VERTICAL_MARGIN_DP, dm);

        final String cursorDateFormatTemplate = ChartUtils.getMarkerDateFormatTemplate(context);
        cursorDateCnv = new XAxisConverter(cursorDateFormatTemplate);

        final int axisTextColor = ChartUtils.getThemedColor(context, R.attr.tchart_axis_text_color, AXIS_TEXT_COLOR);
        final int axisLineColor = ChartUtils.getThemedColor(context, R.attr.tchart_axis_line_color, AXIS_LINE_COLOR);
        final int cursorLineColor = ChartUtils.getThemedColor(context, R.attr.tchart_cursor_line_color, CURSOR_LINE_COLOR);
        final int backColor = ChartUtils.getThemedColor(context, R.attr.app_background_color, Color.WHITE);

        linesMarkerFillPaint = new Paint();
        linesMarkerFillPaint.setAntiAlias(true);
        // activity background as fill color
        linesMarkerFillPaint.setColor(backColor);
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

    @Override
    public void setInputData(@NotNull ChartInputData inputData) {
        super.setInputData(inputData);

        drawData.enableMarksUpdating(AXIS_LINES_COUNT, new XAxisConverter(getContext()));

        //invalidate();
    }

    public void setCursorPopupView(View cursorPopupView) {
        if (BuildConfig.DEBUG && (cursorPopupView == null)) throw new AssertionError();

        this.cursorPopupView = cursorPopupView;

        cursorDateTextView = (TextView) cursorPopupView.findViewById(R.id.cursor_date);
        cursorValuesLayout = (LinearLayout) cursorPopupView.findViewById(R.id.cursor_values);

        this.cursorPopupView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                cursorIndex = NO_CURSOR;

                updateCursorPopup();

                invalidate();
            }
        });
    }

    public void setXRange(float xLeftValue, float xRightValue) {
        if (drawData == null) {
            return;
        }

        drawData.setXRange(xLeftValue, xRightValue, true);

        updateCursorPopupPosition();

        invalidate();
        //postInvalidateDelayed(12);
    }

    public void setXYRange(float xLeftValue, float xRightValue, int yMin, int yMax) {
        if (drawData == null) {
            return;
        }

        drawData.setXRange(xLeftValue, xRightValue, false);
        drawData.setYRange(yMin, yMax);

        updateCursorPopupPosition();

        invalidate();
        //postInvalidateDelayed(12);
    }

    @Override
    public void updateLineVisibility(int lineIndex, boolean exceptLine, int state, boolean doUpdate) {
        super.updateLineVisibility(lineIndex, exceptLine, state, doUpdate);

        updateCursorPopup();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (drawData == null) {
            return;
        }

        if (BuildConfig.DEBUG && ((getWidth() != w) || (getHeight() != h))) throw new AssertionError();
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
    public boolean dispatchTouchEvent(MotionEvent event) {
        final int action = event.getAction();
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            horizontalMovement = false;

            // allow parent to intercept touch events
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
        }

        return gestureDetector.onTouchEvent(event);
    }

    private void onCursorChanged(float xPixel, boolean tapping) {
        final float xValue = drawData.pixelToX(xPixel);
        final int newCursorIndex = findCursorIndex(xValue);

        if (tapping) {
            // reset cursor if it set in same point
            cursorIndex = (newCursorIndex != cursorIndex) ? newCursorIndex : NO_CURSOR;
        } else {
            cursorIndex = newCursorIndex;
        }
        updateCursorPopup();

        invalidate();
    }

    private int findCursorIndex(float cursorXValue) {
        // use the closer point to cursorXValue
        final int cursorIndex = drawData.findXLeftIndex(cursorXValue);
        if ((cursorIndex + 1) < inputData.XValues.length) {
            final float prevPointDelta = Math.abs(inputData.XValues[cursorIndex] - cursorXValue);
            final float nextPointDelta = Math.abs(inputData.XValues[cursorIndex + 1] - cursorXValue);
            return (prevPointDelta < nextPointDelta) ? cursorIndex : cursorIndex + 1;
        } else {
            return cursorIndex;
        }
    }

    private void updateCursorPopup() {
        if (BuildConfig.DEBUG && (cursorPopupView == null)) throw new AssertionError();

        if (cursorIndex == NO_CURSOR) {
            cursorPopupView.setVisibility(GONE);
            return;
        }

        updateCursorPopupValues();
        updateCursorPopupPosition();
    }

    // создание списка видов в cursorPopupWindow со значениями курсора на видимых сигналах
    private void updateCursorPopupValues() {
        if (BuildConfig.DEBUG && (cursorPopupView == null)) throw new AssertionError();
        if (BuildConfig.DEBUG && (cursorIndex == NO_CURSOR)) throw new AssertionError();

        final int[] linesVisibilityState = drawData.getLinesVisibilityState();
        final boolean simpleStacked = simpleStacked();

        int visibleLinesCount = drawData.getVisibleLinesCount();
        // с учётом суммы всех значений
        if (simpleStacked) {
            visibleLinesCount++;
        }
        final boolean recreate = visibleLinesCount != cursorValuesLayout.getChildCount();

        if (recreate) {
            //Log.d("MCV", "cursorValues recreated");

            final LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            if (inflater == null) throw new AssertionError();

            cursorValuesLayout.removeAllViews();

            for (int i = 0; i < inputData.LinesValues.length; i++) {
                if (linesVisibilityState[i] == ChartDrawData.VISIBILITY_STATE_OFF) {
                    continue;
                }

                inflater.inflate(R.layout.view_cursor_value_list_item, cursorValuesLayout, true);
            }

            // для суммы всех значений
            if (simpleStacked) {
                inflater.inflate(R.layout.view_cursor_value_list_item, cursorValuesLayout, true);
            }
        }

        // дата
        cursorDateTextView.setText(cursorDateCnv.toText(inputData.XValues[cursorIndex]));

        // значения линий
        int k = 0;
        for (int i = 0; i < inputData.LinesValues.length; i++) {
            if (linesVisibilityState[i] == ChartDrawData.VISIBILITY_STATE_OFF) {
                continue;
            }

            final View view = cursorValuesLayout.getChildAt(k++);

            updateCursorPopupValueText(view, inputData.LinesNames[i], String.valueOf(inputData.LinesValues[i][cursorIndex]),
                    inputData.LinesColors[i], linesVisibilityState[i], recreate);
        }
        // сумма всех значений
        if (simpleStacked) {
            if (BuildConfig.DEBUG && (k != (cursorValuesLayout.getChildCount() - 1))) throw new AssertionError();

            int sum = 0;
            for (int i = 0; i < inputData.LinesValues.length; i++) {
                if (linesVisibilityState[i] == ChartDrawData.VISIBILITY_STATE_OFF) {
                    continue;
                }

                sum += inputData.LinesValues[i][cursorIndex];
            }

            final View view = cursorValuesLayout.getChildAt(k);

            // TODO: theme color
            updateCursorPopupValueText(view, "All", String.valueOf(sum), Color.BLACK, ChartDrawData.VISIBILITY_STATE_ON, recreate);
        }
    }

    private boolean simpleStacked() {
        return inputData.flags.get(ChartInputData.FLAG_STACKED) && !inputData.flags.get(ChartInputData.FLAG_PERCENTAGE);
    }

    private boolean percentageStacked() {
        return inputData.flags.get(ChartInputData.FLAG_PERCENTAGE);
    }

    private void updateCursorPopupValueText(@NotNull View view, @NotNull String name, @NotNull String value, int color,
                                            int state, boolean refill) {
        final float scale = state / 255f;
        //@ColorInt int c = (color & 0x00ffffff) | (state << 24);

        final TextView lineNameTextBox = (TextView) view.findViewById(R.id.cursor_line_name);
        lineNameTextBox.setScaleY(scale);
        if (refill) {
            lineNameTextBox.setText(name);
            //lineNameTextBox.setTextColor(lineColor);
        }

        final TextView valueTextBox = (TextView) view.findViewById(R.id.cursor_value);
        valueTextBox.setText(value);
        valueTextBox.setScaleY(scale);
        if (refill) {
            valueTextBox.setTextColor(color);
        }
    }

    private void updateCursorPopupPosition() {
        if (BuildConfig.DEBUG && (cursorPopupView == null)) throw new AssertionError();

        if (cursorIndex == NO_CURSOR) {
            cursorPopupView.setVisibility(GONE);
            return;
        }

        int cursorX = (int) drawData.xToPixel(inputData.XValues[cursorIndex]);

        final int viewRight = getRight();

        if (cursorX < getLeft() || viewRight < cursorX) {
            cursorPopupView.setVisibility(GONE);
            return;
        }

        // TODO: rtl
        cursorX += cursorPopupStartMargin;
        if (cursorX + cursorPopupView.getWidth() > viewRight) {
            cursorX = viewRight - cursorPopupView.getWidth();
        }

        cursorPopupView.setX(cursorX);

        cursorPopupView.setVisibility(VISIBLE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
//        synchronized (lock) {
            if (drawData == null) {
                return;
            }

            drawXAxis(canvas);

            // если нет видимых сигналов, оставляем xAxis и выводим текст NO_DATA по центру области графика
            if (drawData.getVisibleLinesCount() == ChartDrawData.VISIBILITY_STATE_OFF) {
                final float x = getWidth() / 2f - axisTextPaint.measureText(NO_DATA) / 2f;
                final float y = graphAreaHeight / 2f;

                canvas.drawText(NO_DATA, x, y, axisTextPaint);

                return;
            }

            drawYAxis(canvas);
            drawLines(canvas);
            drawCursor(canvas);
//        }
    }

    private void drawXAxis(@NotNull Canvas canvas) {
        final List<ChartDrawData.AxisMark> marks = drawData.getXAxisMarks();
        if (BuildConfig.DEBUG && (marks == null)) throw new AssertionError();

        final float y = getHeight() - xAxisTextVerticalMargin;

        for (ChartDrawData.AxisMark mark: marks) {
            // центруем текст относительно точки
            final float x = mark.getPosition() - axisTextPaint.measureText(mark.getText()) / 2f;

            canvas.drawText(mark.getText(), x, y, axisTextPaint);
        }
    }

    private void drawYAxis(@NotNull Canvas canvas) {
        final List<ChartDrawData.AxisMark> marks = drawData.getYAxisMarks();
        if (BuildConfig.DEBUG && (marks == null)) throw new AssertionError();

        boolean isLayoutRtl = ViewUtils.isLayoutRtl(this);
        final int w = getWidth();

        for (ChartDrawData.AxisMark mark: marks) {
            final float y = mark.getPosition();

            canvas.drawLine(0, y, w, y, yAxisLinePaint);

            float x;
            if (isLayoutRtl) {
                x = w - axisTextPaint.measureText(mark.getText());
            } else {
                x = 0;
            }
            canvas.drawText(mark.getText(), x, y - yAxisTextVerticalMargin, axisTextPaint);
        }
    }

    private void drawCursor(@NotNull Canvas canvas) {
        if (cursorIndex == NO_CURSOR) {
            return;
        }

        final float cursorX = drawData.xToPixel(inputData.XValues[cursorIndex]);

        canvas.drawLine(cursorX, 0, cursorX, graphAreaHeight, helperLinePaint);

        final int[] linesVisibilityState = drawData.getLinesVisibilityState();

        for (int i = 0; i < inputData.LinesValues.length; i++) {
            if (linesVisibilityState[i] == ChartDrawData.VISIBILITY_STATE_OFF) {
                continue;
            }

            final float cursorY = drawData.yToPixel(inputData.LinesValues[i][cursorIndex]);

            // граница маркера цветом графика
            canvas.drawCircle(cursorX, cursorY, markerRadius, linesPaints[i]);
            // заполнение маркера цветом фона
            canvas.drawCircle(cursorX, cursorY, markerFillRadius, linesMarkerFillPaint);
        }
    }

    private class OnGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            onCursorChanged(e.getX(), true);

            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (!horizontalMovement) {
                if (Math.abs(e2.getX() - e1.getX()) > mTouchSlop) {
                    horizontalMovement = true;

                    // disallow parent (ScrollView) to intercept touch events while we're moving selection zone
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
            }

            if (horizontalMovement) {
                onCursorChanged(e2.getX(), false);
            }

            return true;
        }
    }

    public static class XAxisConverter implements ChartDrawData.AxisTextConverter {
        private SimpleDateFormat dateFormat;
        private final @NotNull Date tmpDate = new Date();

        public XAxisConverter(@NotNull Context context) {
            this(ChartUtils.getAxisDateFormatTemplate(context));
        }

        public XAxisConverter(@NotNull String template) {
            dateFormat = new SimpleDateFormat(template, Locale.getDefault());
        }

        @Override
        public @NotNull String toText(long value) {
            tmpDate.setTime(value);
            return dateFormat.format(tmpDate);
        }
    }
}
