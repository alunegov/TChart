package com.github.alunegov.tchart;

import android.content.Context;
import android.graphics.*;
import android.support.v7.widget.ViewUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.*;

import java.text.SimpleDateFormat;
import java.util.*;

import org.jetbrains.annotations.NotNull;

public class MainChartView extends AbsChartView {
    private static final String NO_DATA = "NO VISIBLE LINES";

    private static final int AXIS_LINES_COUNT = 5;

    private static final float LINE_WIDTH_DP = 2.0f;

    // X/Y Axis Text
    //                                     Light             Dark
    // Followers, Interactions, Growth   8E8E93        x/y - A3B1C2, 60%
    // Messages, Apps                    252529, 50%    x - A3B1C2, 60% / y - ECF2F8, 50%
    private static final int AXIS_TEXT_COLOR = Color.parseColor("#8E8E93");
    private static final int AXIS_TEXT_SIZE_SP = 12;

    // Grid Lines
    // Light - 182D3B, 10%  Dark - FFFFFF, 10%
    private static final int AXIS_LINE_COLOR = Color.parseColor("#19182D3B");
    private static final float AXIS_LINE_WIDTH_DP = 1.0f;

    private static final int CURSOR_POPUP_START_MARGIN_DP = 10;

    private static final int MARKER_RADIUS_DP = 4;

    private static final int Y_AXIS_TEXT_VERTICAL_MARGIN_DP = 5;
    private static final int X_AXIS_TEXT_VERTICAL_MARGIN_DP = 3;

    private GestureDetector gestureDetector;

    private float cursorPopupStartMargin;
    private float markerRadius, markerFillRadius;
    private float yAxisTextVerticalMargin;
    private float xAxisTextVerticalMargin;

    // высота области графика (высота вида минус текст x-оси)
    private float graphAreaHeight;

    // popup для отображения значений курсора
    private CursorPopupView cursorPopupView;
    // Преобразователь значения в текст для x-значений курсора
    private XAxisConverter cursorDateCnv;

    // настройки отрисовки заполнения маркера курсора (чтобы получить заливку маркера цветом фона)
    private Paint linesMarkerFillPaint;
    // настройки отрисовки надписей осей
    private Paint xAxisTextPaint;
    // в спец. случае это отдельные настройки (см. FLAG_Y_SCALED или AXIS_TEXT_DARK_BARS_Y_COLOR)
    private Paint yLeftAxisTextPaint, yRightAxisTextPaint;
    // настройки отрисовки линий оцифровки (и курсора)
    private Paint axisLinePaint;

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
        markerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, MARKER_RADIUS_DP, dm);
        markerFillRadius = markerRadius - lineWidth / 2;
        xAxisTextVerticalMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, X_AXIS_TEXT_VERTICAL_MARGIN_DP, dm);
        yAxisTextVerticalMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, Y_AXIS_TEXT_VERTICAL_MARGIN_DP, dm);

        final String cursorDateFormatTemplate = ChartUtils.getMarkerDateFormatTemplate(context);
        cursorDateCnv = new XAxisConverter(cursorDateFormatTemplate);

        final int axisTextColor = ChartUtils.getThemedColor(context, R.attr.tchart_axis_text_color, AXIS_TEXT_COLOR);
        final int axisLineColor = ChartUtils.getThemedColor(context, R.attr.tchart_axis_line_color, AXIS_LINE_COLOR);
        final int backgroundColor = ChartUtils.getThemedColor(context, R.attr.app_background_color, Color.WHITE);

        linesMarkerFillPaint = new Paint();
        linesMarkerFillPaint.setAntiAlias(true);
        linesMarkerFillPaint.setColor(backgroundColor);
        linesMarkerFillPaint.setStyle(Paint.Style.FILL);

        xAxisTextPaint = new Paint();
        xAxisTextPaint.setAntiAlias(true);
        xAxisTextPaint.setColor(axisTextColor);
        xAxisTextPaint.setStyle(Paint.Style.FILL);
        xAxisTextPaint.setTextSize(axisTextSize);

        yLeftAxisTextPaint = new Paint(xAxisTextPaint);

        yRightAxisTextPaint = new Paint(xAxisTextPaint);

        axisLinePaint = ChartUtils.makeLinePaint(axisLineColor, axisLineWidth, true);

        updateGraphAreaHeight();
    }

    public void setAxisTextSize(float px) {
        xAxisTextPaint.setTextSize(px);
        yLeftAxisTextPaint.setTextSize(px);
        yRightAxisTextPaint.setTextSize(px);

        updateGraphAreaHeight();

        //invalidate();
    }

    @Override
    public void setInputData(@NotNull final ChartInputData inputData, @NotNull final ChartInputDataStats inputDataStats) {
        super.setInputData(inputData, inputDataStats);

        drawData.enableMarksUpdating(AXIS_LINES_COUNT, new XAxisConverter(getContext()));
        drawData.enableYRangeEnlarging();

        if (inputData.flags.get(ChartInputData.FLAG_Y_SCALED)) {
            final boolean[] linesRightAlign = inputDataStats.getLinesRightAlign();

            for (int j = 0; j < inputData.LinesValues.length; j++) {
                if (!linesRightAlign[j]) {
                    yLeftAxisTextPaint.setColor(inputData.LinesColors[j]);
                    break;
                }
            }

            for (int j = 0; j < inputData.LinesValues.length; j++) {
                if (linesRightAlign[j]) {
                    yRightAxisTextPaint.setColor(inputData.LinesColors[j]);
                    break;
                }
            }
        } else if (inputData.linesType == ChartInputData.LineType.BAR || inputData.linesType == ChartInputData.LineType.AREA) {
            final int barsXAxisTextColor = ChartUtils.getThemedColor(getContext(), R.attr.tchart_bars_x_axis_text_color, AXIS_TEXT_COLOR);
            final int barsYAxisTextColor = ChartUtils.getThemedColor(getContext(), R.attr.tchart_bars_y_axis_text_color, AXIS_TEXT_COLOR);

            xAxisTextPaint.setColor(barsXAxisTextColor);
            yLeftAxisTextPaint.setColor(barsYAxisTextColor);
            yRightAxisTextPaint.setColor(barsYAxisTextColor);
        }

        cursorPopupView = new CursorPopupView(getContext(), inputData);
        cursorPopupView.top = 0;
        cursorPopupView.onChangeListener = new CursorPopupView.OnChangeListener() {
            @Override
            public void OnClick() {
                cursorIndex = NO_CURSOR;

                drawData.updateCursorPaths(cursorIndex);
                updateCursorPopup();

                invalidate();
            }

            @Override
            public void OnCursorNextClick() {
                if (cursorIndex < (inputData.XValues.length - 1)) {
                    cursorIndex++;

                    drawData.updateCursorPaths(cursorIndex);
                    updateCursorPopup();

                    invalidate();
                }
            }
        };

        //invalidate();
    }

    public void setXRange(float xLeftValue, float xRightValue) {
        if (drawData == null) {
            return;
        }

        drawData.setXRange(xLeftValue, xRightValue, true);

        invalidate();
        //postInvalidateDelayed(12);
    }

    public void setXYRange(float xLeftValue, float xRightValue, int yLeftMin, int yLeftMax, int yRightMin, int yRightMax) {
        if (drawData == null) {
            return;
        }

        drawData.setXRange(xLeftValue, xRightValue, false);
        drawData.setYRange(yLeftMin, yLeftMax, yRightMin, yRightMax);

        invalidate();
        //postInvalidateDelayed(12);
    }

    @Override
    public void updateLineVisibility(int lineIndex, boolean exceptLine, int state, boolean doUpdate, boolean doInvalidate) {
        super.updateLineVisibility(lineIndex, exceptLine, state, doUpdate, doInvalidate);

        drawData.updateCursorPaths(cursorIndex);
        updateCursorPopup();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (drawData == null) {
            return;
        }

        if (BuildConfig.DEBUG && ((getWidth() != w) || (getHeight() != h))) throw new AssertionError();
        updateGraphAreaHeight();

        //cursorPopupView.top = 0;

        //invalidate();
    }

    // пересчёт высоты области для вывода сигналов (немного уменьшаем высоту, чтобы выводить в нижней части вида x-значения)
    // вызывается при изменении размеров вида, размера текста надписей оси
    private void updateGraphAreaHeight() {
        Paint.FontMetrics fm = xAxisTextPaint.getFontMetrics();

        final float axisTextHeight = fm.descent - fm.ascent;

        graphAreaHeight = getHeight() - 2 * xAxisTextVerticalMargin - axisTextHeight;

        if (drawData != null) {
            drawData.setArea(new RectF(getPaddingLeft(), 0, getWidth() - getPaddingRight(), graphAreaHeight));
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

        drawData.updateCursorPaths(cursorIndex);
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
            return;
        }

        final int[] linesVisibilityState = inputDataStats.getLinesVisibilityState();
        final boolean showAll = (inputData.linesType == ChartInputData.LineType.BAR) && (inputData.LinesValues.length > 1);
        final boolean showPercentage = inputData.linesType == ChartInputData.LineType.AREA;

        cursorPopupView.date = cursorDateCnv.toText(inputData.XValues[cursorIndex]);

        cursorPopupView.linesValuesCount = 0;
        for (int j = 0; j < inputData.LinesValues.length; j++) {
            if (linesVisibilityState[j] == ChartInputDataStats.VISIBILITY_STATE_OFF) {
                continue;
            }

            final String percent;
            if (showPercentage) {
                final int[] stackedSum = inputDataStats.getStackedSum();
                assert stackedSum != null;

                final float percentValue = (float) inputData.LinesValues[j][cursorIndex] / stackedSum[cursorIndex] * 100f;

                percent = String.format(Locale.getDefault(), "%d%%", Math.round(percentValue));
            } else {
                percent = "";
            }

            CursorPopupView.LineValues lineValues = cursorPopupView.linesValues[cursorPopupView.linesValuesCount];
            lineValues.name = inputData.LinesNames[j];
            lineValues.value = String.format(Locale.getDefault(), "%,d", inputData.LinesValues[j][cursorIndex]);
            lineValues.percent = percent;
            lineValues.color = inputData.LinesColors[j];
            lineValues.boldName = false;
            lineValues.state = linesVisibilityState[j];

            cursorPopupView.linesValuesCount++;
        }
        // сумма всех значений
        if (showAll) {
            int sum = 0;
            for (int i = 0; i < inputData.LinesValues.length; i++) {
                if (linesVisibilityState[i] == ChartInputDataStats.VISIBILITY_STATE_OFF) {
                    continue;
                }

                sum += inputData.LinesValues[i][cursorIndex] * linesVisibilityState[i] / ChartInputDataStats.VISIBILITY_STATE_ON;
            }

            final Context context = getContext();

            CursorPopupView.LineValues lineValues = cursorPopupView.linesValues[cursorPopupView.linesValuesCount];
            lineValues.name = context.getString(R.string.sum_line_name);
            lineValues.value = String.format(Locale.getDefault(), "%,d", sum);
            //lineValues.percent = ;
            lineValues.color = ChartUtils.getThemedColor(context, R.attr.tchart_cursor_popup_text_color, Color.BLACK);
            lineValues.boldName = true;
            lineValues.state = ChartInputDataStats.VISIBILITY_STATE_ON;

            cursorPopupView.linesValuesCount++;
        }

        cursorPopupView.onLayout();
    }

    @Override
    protected void onDraw(Canvas canvas) {
//        synchronized (lock) {
            if (drawData == null) {
                return;
            }

            // если нет видимых сигналов, оставляем xAxis и выводим текст NO_DATA по центру области графика
            if (inputDataStats.getVisibleLinesCount() == 0) {
                final float x = getWidth() / 2f - xAxisTextPaint.measureText(NO_DATA) / 2f;
                final float y = graphAreaHeight / 2f;
                canvas.drawText(NO_DATA, x, y, xAxisTextPaint);

                drawXAxis(canvas);

                return;
            }

            drawLines(canvas);
            drawXAxis(canvas);
            drawYAxis(canvas);
            drawCursor(canvas);
//        }
    }

    private void drawXAxis(@NotNull Canvas canvas) {
        final List<ChartDrawData.AxisMark> marks = drawData.getXAxisMarks();
        if (marks == null) throw new AssertionError();

        final float y = getHeight() - xAxisTextVerticalMargin;

        for (ChartDrawData.AxisMark mark: marks) {
            // центруем текст относительно точки
            final float x = mark.getPosition() - xAxisTextPaint.measureText(mark.getText()) / 2f;

            canvas.drawText(mark.getText(), x, y, xAxisTextPaint);
        }
    }

    private void drawYAxis(@NotNull Canvas canvas) {
        final List<ChartDrawData.AxisMark> marks = drawData.getYAxisMarks();
        if (marks == null) throw new AssertionError();

        final boolean isLayoutRtl = ViewUtils.isLayoutRtl(this);
        final int startX = getPaddingLeft();
        final int w = getWidth() - getPaddingRight();

        for (ChartDrawData.AxisMark mark: marks) {
            final float y = mark.getPosition();

            canvas.drawLine(startX, y, w, y, axisLinePaint);

            float x;

            if (mark.getText() != null) {
                if (isLayoutRtl) {
                    x = w - yLeftAxisTextPaint.measureText(mark.getText());
                } else {
                    x = startX;
                }
                canvas.drawText(mark.getText(), x, y - yAxisTextVerticalMargin, yLeftAxisTextPaint);
            }

            if (mark.getTextRight() != null) {
                if (isLayoutRtl) {
                    x = startX;
                } else {
                    x = w - yRightAxisTextPaint.measureText(mark.getTextRight());
                }
                canvas.drawText(mark.getTextRight(), x, y - yAxisTextVerticalMargin, yRightAxisTextPaint);
            }
        }
    }

    private void drawCursor(@NotNull Canvas canvas) {
        if (cursorIndex == NO_CURSOR) {
            return;
        }

        final float cursorX = drawData.xToPixel(inputData.XValues[cursorIndex]);

        // в BAR не нужны ни линия, ни отметки точек, только tooltip
        if (inputData.linesType == ChartInputData.LineType.BAR) {
            drawCursorPopup(canvas, cursorX);
            return;
        }

        canvas.drawLine(cursorX, 0, cursorX, graphAreaHeight, axisLinePaint);

        // в AREA нужна только линия и tooltip, без отметок точек
        if (inputData.linesType == ChartInputData.LineType.AREA) {
            drawCursorPopup(canvas, cursorX);
            return;
        }

        final int[] linesVisibilityState = inputDataStats.getLinesVisibilityState();
        final boolean[] linesRightAlign = inputDataStats.getLinesRightAlign();

        for (int i = 0; i < inputData.LinesValues.length; i++) {
            if (linesVisibilityState[i] == ChartInputDataStats.VISIBILITY_STATE_OFF) {
                continue;
            }

            final float cursorY;
            if (linesRightAlign[i]) {
                cursorY = drawData.yRightToPixel(inputData.LinesValues[i][cursorIndex]);
            } else {
                cursorY = drawData.yLeftToPixel(inputData.LinesValues[i][cursorIndex]);
            }

            // граница маркера цветом графика
            canvas.drawCircle(cursorX, cursorY, markerRadius, linesPaints[i]);
            // заполнение маркера цветом фона
            canvas.drawCircle(cursorX, cursorY, markerFillRadius, linesMarkerFillPaint);
        }

        // рисуем последним, что ничего его не перекрывало - ни графики, ни линия курсора, ни отметки точек
        drawCursorPopup(canvas, cursorX);
    }

    private void drawCursorPopup(@NotNull Canvas canvas, float cursorX) {
        if (BuildConfig.DEBUG && (cursorPopupView == null)) throw new AssertionError();

        final int viewWidth = getWidth();

        if (0 > cursorX || cursorX > viewWidth) {
            return;
        }

        // TODO: rtl
        float cursorPopupLeft = cursorX + cursorPopupStartMargin;

        if (cursorPopupLeft + cursorPopupView.width > viewWidth) {
            // выходит за правую границу экрана - размещаем слева от курсора
            cursorPopupLeft = cursorX - cursorPopupStartMargin - cursorPopupView.width;

            if (cursorPopupLeft < 0) {
                // выходит за левую границу экрана - размещаем начиная от левой границы
                cursorPopupLeft = 0;
            }
        }

        cursorPopupView.left = cursorPopupLeft;
        cursorPopupView.onDraw(canvas);
    }

    private class OnGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            if (inputDataStats.getVisibleLinesCount() > 0) {
                if (cursorPopupView.onSingleTapUp(e)) {
                    return true;
                }

                onCursorChanged(e.getX(), true);
            }

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

            if (horizontalMovement && (inputDataStats.getVisibleLinesCount() > 0)) {
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
