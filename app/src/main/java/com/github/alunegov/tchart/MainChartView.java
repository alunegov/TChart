package com.github.alunegov.tchart;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

public class MainChartView extends AbsChartView {
    private static final int NO_CURSOR = -1;

    private static final String NO_DATA = "NO VISIBLE LINES";

    private static final int AXIS_LINES_COUNT = 5;

    private static final float LINE_WIDTH_DP = 2.0f;

    private static final int AXIS_TEXT_SIZE_SP = 12;
    private static final int AXIS_TEXT_COLOR = Color.parseColor("#96A2AA");

    private static final int AXIS_LINE_COLOR = Color.parseColor("#F1F1F2");
    private static final float AXIS_LINE_WIDTH_DP = 1.0f;

    private static final int CURSOR_LINE_COLOR = Color.parseColor("#E5EBEF");
    private static final float CURSOR_LINE_WIDTH_DP = 1.0f;

    private static final int CURSOR_POPUP_TOP_SHIFT_DP = 30;
    private static final int CURSOR_POPUP_TOP_MARGIN_DP = 30;

    private static final int MARKER_RADIUS_DP = 4;

    private static final int Y_AXIS_TEXT_VERTICAL_MARGIN_DP = 7;
    private static final int X_AXIS_TEXT_VERTICAL_MARGIN_DP = 3;

    private float lineWidth;
    private int cursorPopupTopShift, cursorPopupTopMargin;
    private float markerRadius, markerFillRadius;
    private float yAxisTextVerticalMargin;
    private float xAxisTextVerticalMargin;

    // индекс точки с курсором
    private int cursorIndex = NO_CURSOR;
    // высота области графика (высота вида минус текст x-оси)
    private float graphAreaHeight;

    // popup для отображения значений курсора
    private PopupWindow cursorPopupWindow;
    // Преобразователь значения в текст для x-значений курсора
    private XAxisConverter cursorDateCnv;
    // Положение и размеры вида в кординатах экрана. Пересчитываются при каждом отображении значений курсора
    private Rect rectOnScreen;

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
        cursorPopupTopShift = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, CURSOR_POPUP_TOP_SHIFT_DP, dm);
        cursorPopupTopMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, CURSOR_POPUP_TOP_MARGIN_DP, dm);
        markerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, MARKER_RADIUS_DP, dm);
        markerFillRadius = markerRadius - lineWidth / 2;
        xAxisTextVerticalMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, X_AXIS_TEXT_VERTICAL_MARGIN_DP, dm);
        yAxisTextVerticalMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, Y_AXIS_TEXT_VERTICAL_MARGIN_DP, dm);

        final String cursorDateFormatTemplate = ChartUtils.getMarkerDateFormatTemplate(context);
        cursorDateCnv = new XAxisConverter(context, cursorDateFormatTemplate);

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

    public void setInputData(@NotNull ChartInputData inputData) {
        this.inputData = inputData;

        drawData = new ChartDrawData(inputData);
        drawData.enableMarksUpdating(AXIS_LINES_COUNT, new XAxisConverter(getContext()));
        drawData.setXRange(0, inputData.XValues.length - 1);

        linesPaints = ChartUtils.makeLinesPaints(inputData.LinesColors, lineWidth);

        //invalidate();
    }

    public void setXRange(float xLeftValue, float xRightValue) {
        if (drawData == null) {
            return;
        }

        drawData.setXRange(xLeftValue, xRightValue);

        if (cursorIndex != NO_CURSOR) {
            // should never got here with cursorPopupWindow.setOutsideTouchable(true)
            cursorIndex = NO_CURSOR;

            assert cursorPopupWindow != null;
            cursorPopupWindow.dismiss();
        }

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

        switch (event.getAction()) {
            case MotionEvent.ACTION_CANCEL:
                cursorIndex = NO_CURSOR;

                invalidate();

                updateCursorPopup();

                break;

            case MotionEvent.ACTION_UP:
                final float x = event.getX();
                final float xValue = drawData.pixelToX(x);
                final int newCursorIndex = findCursorIndex(xValue);

                // reset cursor if it set in same point
                cursorIndex = (newCursorIndex != cursorIndex) ? newCursorIndex : NO_CURSOR;

                invalidate();

                updateCursorPopup();

                break;
        }

        return true;
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
        if (cursorIndex == NO_CURSOR) {
            if (cursorPopupWindow != null) {
                cursorPopupWindow.dismiss();
            }
            return;
        }

        if (cursorPopupWindow == null) {
            createCursorPopupWindow();
        }
        assert cursorPopupWindow != null;

        updateCursorPopupValues();

        final int[] loc = new int[2];
        getLocationOnScreen(loc);

        rectOnScreen = new Rect(loc[0], loc[1], loc[0] + getWidth(), loc[1] + getHeight());

        final int cursorX = (int) drawData.xToPixel(inputData.XValues[cursorIndex]);
        int y = rectOnScreen.top - cursorPopupTopShift;
        if (y <= cursorPopupTopMargin) {
            y = cursorPopupTopMargin;
        }

        if (!cursorPopupWindow.isShowing()) {
            cursorPopupWindow.showAtLocation(this, Gravity.NO_GRAVITY, cursorX, y);
        }
        cursorPopupWindow.update(cursorX, y, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private void createCursorPopupWindow() {
        final LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        assert inflater != null;

        final View popupView = inflater.inflate(R.layout.cursor_popup, null);
        assert popupView != null;

        cursorPopupWindow = new PopupWindow(popupView, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        if (Build.VERSION.SDK_INT >= 21) {
            cursorPopupWindow.setElevation(5.0f);
        }

        cursorPopupWindow.setTouchable(true);
        cursorPopupWindow.setOutsideTouchable(true);
        // В v16 без задания setBackgroundDrawable не работает Touchable/OutsideTouchable. Задание TRANSPARENT и
        // использование @drawable/round_rect_shape_light в xml-разметке работают как нужно (в v17 и v27).
        cursorPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        cursorPopupWindow.setTouchInterceptor(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    // Don't reset cursor/dismiss popup when touching inside MainChartView - it's a cursor changing.
                    // If not, scroll or something else, when MotionEvent.ACTION_CANCEL fires.
                    final int[] popupLoc = new int[2];
                    v.getLocationOnScreen(popupLoc);

                    final int xOnScreen = popupLoc[0] + (int) event.getX();
                    final int yOnScreen = popupLoc[1] + (int) event.getY();

                    if (rectOnScreen.contains(xOnScreen, yOnScreen)) {
                        return true;
                    }
                }

                cursorIndex = NO_CURSOR;

                invalidate();

                cursorPopupWindow.dismiss();

                return true;
            }
        });
    }

    // создание списка видов в cursorPopupWindow со значениями курсора на видимых сигналах
    private void updateCursorPopupValues() {
        assert cursorPopupWindow != null;
        assert cursorIndex != NO_CURSOR;

        final View popupView = cursorPopupWindow.getContentView();
        assert popupView != null;

        // дата
        final TextView dateTextView = (TextView) popupView.findViewById(R.id.cursor_date);
        dateTextView.setText(cursorDateCnv.toText(inputData.XValues[cursorIndex]));

        // значения линий
        LinearLayout valuesLayout = (LinearLayout) popupView.findViewById(R.id.cursor_values);
        assert valuesLayout != null;

        final LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        assert inflater != null;

        final Set<Integer> invisibleLinesIndexes = drawData.getInvisibleLinesIndexes();

        valuesLayout.removeAllViews();

        for (int i = 0; i < inputData.LinesValues.length; i++) {
            if (invisibleLinesIndexes.contains(i)) {
                continue;
            }

            final View view = inflater.inflate(R.layout.view_cursor_value_list_item, valuesLayout, false);

            final TextView valueTextBox = (TextView) view.findViewById(R.id.cursor_value);
            valueTextBox.setText(String.valueOf(inputData.LinesValues[i][cursorIndex]));
            valueTextBox.setTextColor(inputData.LinesColors[i]);

            final TextView lineNameTextBox = (TextView) view.findViewById(R.id.cursor_line_name);
            lineNameTextBox.setText(inputData.LinesNames[i]);
            lineNameTextBox.setTextColor(inputData.LinesColors[i]);

            valuesLayout.addView(view);
        }
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
        final List<ChartDrawData.AxisMark> marks = drawData.getXAxisMarks();
        assert marks != null;

        final float y = getHeight() - xAxisTextVerticalMargin;

        for (ChartDrawData.AxisMark mark: marks) {
            // центруем текст относительно точки
            final float x = mark.getPosition() - axisTextPaint.measureText(mark.getText()) / 2f;

            canvas.drawText(mark.getText(), x, y, axisTextPaint);
        }
    }

    private void drawYAxis(@NotNull Canvas canvas) {
        final List<ChartDrawData.AxisMark> marks = drawData.getYAxisMarks();
        assert marks != null;

        final int w = getWidth();

        for (ChartDrawData.AxisMark mark: marks) {
            final float y = mark.getPosition();

            canvas.drawLine(0, y, w, y, yAxisLinePaint);

            canvas.drawText(mark.getText(), 0, y - yAxisTextVerticalMargin, axisTextPaint);
        }
    }

    private void drawCursor(@NotNull Canvas canvas) {
        if (cursorIndex == NO_CURSOR) {
            return;
        }

        final float cursorX = drawData.xToPixel(inputData.XValues[cursorIndex]);

        canvas.drawLine(cursorX, 0, cursorX, graphAreaHeight, helperLinePaint);

        final Set<Integer> invisibleLinesIndexes = drawData.getInvisibleLinesIndexes();

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

    private static class XAxisConverter implements ChartDrawData.AxisTextConverter {
        private SimpleDateFormat dateFormat;
        private final @NotNull Date tmpDate = new Date();

        public XAxisConverter(@NotNull Context context) {
            this(context, ChartUtils.getAxisDateFormatTemplate(context));
        }

        public XAxisConverter(@NotNull Context context, @NotNull String template) {
            dateFormat = new SimpleDateFormat(template, Locale.getDefault());
        }

        @Override
        public @NotNull String toText(long value) {
            tmpDate.setTime(value);
            return dateFormat.format(tmpDate);
        }
    }
}
