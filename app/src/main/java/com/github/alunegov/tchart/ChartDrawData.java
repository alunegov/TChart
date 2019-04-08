package com.github.alunegov.tchart;

import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// Данные графика, используемые при отрисовке
public class ChartDrawData {
    public static final int VISIBILITY_STATE_ON = 255;

    // исходные данные графика
    private ChartInputData inputData;
    // Кол-во линий оцифровки осей
    private int axisLineCount;
    // Преобразователь значения в текст для оцифровки оси X
    private AxisTextConverter xAxisTextConv;
    // режим нижней границы Y
    private YMinMode yMinMode = YMinMode.RANGE;
    // область отображения графика
    private RectF area = new RectF();
    // флаг: область отображения графика задана
    private boolean areaSet;
    // границы отображаемого диапазона по X, значение
    private float xLeftValue, xRightValue;
    // границы отображаемого диапазона по X, индекс в inputData.XValues
    private int xLeftIndex, xRightIndex;
    // флаг: границы отображаемого диапазона по X заданы
    private boolean xLeftSet, xRightSet;
    private int[] linesVisibilityState;
    private int visibleLinesCount;
    // минимальное и максимальное значения Y по отображаемому диапазону X по всем сигналам (с учётом yMinMode)
    private int yMin, yMax;
    // коэффициент пересчета значений в пиксели
    private float scaleX, scaleY;
    // отображаемые данные линий (сигналов) в виде Path
    private Path[] linesPaths;
    private float[][] linesLines;
    // Метки для оцифровки осей
    private List<AxisMark> xAxisMarks, yAxisMarks;

    private final @NotNull Matrix matrix = new Matrix();
    private float[] pts;

    public ChartDrawData(@NotNull ChartInputData inputData) {
        this.inputData = inputData;

        linesVisibilityState = new int[inputData.LinesValues.length];
        for (int i = 0; i < linesVisibilityState.length; i++) {
            linesVisibilityState[i] = VISIBILITY_STATE_ON;
        }
        visibleLinesCount = inputData.LinesValues.length;

        linesPaths = new Path[inputData.LinesValues.length];
        for (int i = 0; i < linesPaths.length; i++) {
            linesPaths[i] = new Path();
        }

        linesLines = new float[inputData.LinesValues.length][];
        for (int i = 0; i < linesLines.length; i++) {
            if (BuildConfig.DEBUG && (inputData.XValues.length != inputData.LinesValues[i].length))
                throw new AssertionError();
            linesLines[i] = new float[(inputData.XValues.length - 1) * 4];
        }

        pts = new float[(inputData.XValues.length - 1) * 4];
    }

    public void enableMarksUpdating(int axisLineCount, @NotNull AxisTextConverter xAxisTextConv) {
        this.axisLineCount = axisLineCount;
        this.xAxisTextConv = xAxisTextConv;

        xAxisMarks = new ArrayList<>();
        yAxisMarks = new ArrayList<>();
    }

    public @NotNull RectF getArea() {
        return area;
    }

    public void setArea(@NotNull RectF area) {
        this.area.set(area);
        this.areaSet = true;

        //updateYRange();
        updateScales();
        updateLinesAndAxis();
    }

    // отображаемый диапазон по X, фактические значения. М.б. не из XValues
    public void getXRange(@NotNull float[] range) {
        if (BuildConfig.DEBUG && (range.length != 2)) throw new AssertionError();

        range[0] = xLeftValue;
        range[1] = xRightValue;
    }

    // отображаемый диапазон по Х, индексы в XValues. М.б. -1/+1, чтобы "охватить" фактические значения
    // (см. findXLeftIndex и findXRightIndex)
    public void getXRange(@NotNull int[] range) {
        if (BuildConfig.DEBUG && (range.length != 2)) throw new AssertionError();

        range[0] = xLeftIndex;
        range[1] = xRightIndex;
    }

    // отображаемый диапазон по Х, значения в XValues
    public void getXRange(@NotNull long[] range) {
        if (BuildConfig.DEBUG && (range.length != 2)) throw new AssertionError();

        range[0] = inputData.XValues[xLeftIndex] < xLeftValue ? inputData.XValues[xLeftIndex + 1] : inputData.XValues[xLeftIndex];
        range[1] = inputData.XValues[xRightIndex] > xRightValue ? inputData.XValues[xRightIndex - 1] : inputData.XValues[xRightIndex];
    }

    public void setXRange(float xLeftValue, float xRightValue, boolean doUpdate) {
        this.xLeftValue = xLeftValue;
        xLeftIndex = findXLeftIndex(xLeftValue);
        xLeftSet = true;

        this.xRightValue = xRightValue;
        xRightIndex = findXRightIndex(xRightValue, xLeftIndex);
        xRightSet = true;

        if (doUpdate) {
            updateYRange();
            updateScales();
            updateLinesAndAxis();
        }
    }

    public @NotNull int[] getLinesVisibilityState() {
        return linesVisibilityState;
    }

    public int getVisibleLinesCount() {
        return visibleLinesCount;
    }

    public void updateLineVisibility(int lineIndex, int state, boolean doUpdate) {
        if ((lineIndex < 0) || (inputData.LinesValues.length <= lineIndex)) {
            return;
        }

        linesVisibilityState[lineIndex] = state;

        if (doUpdate) {
            updateYRange();
            updateScales();
            updateLinesAndAxis();
        }
    }

    public void getYRange(@NotNull int[] range) {
        if (BuildConfig.DEBUG && (range.length != 2)) throw new AssertionError();

        range[0] = yMin;
        range[1] = yMax;
    }

    public void setYRange(int yMin, int yMax) {
        this.yMin = yMin;
        this.yMax = yMax;

        updateScales();
        updateLinesAndAxis();
    }

    public void calcYRangeAt(int xLeftIndex, int xRightIndex, @NotNull int[] linesVisibilityState, @NotNull int[] range) {
        if (BuildConfig.DEBUG && (range.length != 2)) throw new AssertionError();

        final @NotNull int[] yMinMax = inputData.findYMinMax(xLeftIndex, xRightIndex, linesVisibilityState);

        int yMinAt;
        switch (yMinMode) {
            case RANGE:
                yMinAt = yMinMax[0];
                break;
            case ZERO:
                if (BuildConfig.DEBUG && (yMinMax[0] < 0)) throw new AssertionError();
                yMinAt = 0;
                break;
            default:
                yMinAt = 0;
        }

        int yMaxAt = yMinMax[1];

        // добавляем к минимуму/максимуму часть размаха, чтобы снизу/сверху было немного места (так на ref, была видна пометка точки)
        // TODO: добавлять к минимуму не просто "часть размаха", а так, чтобы первая линия оцифровки была в "нулевом пикселе"
        final int yDelta = (int) (0.05 * (yMaxAt - yMinAt));
        if (yMinAt != 0) {
            yMinAt -= yDelta;
        }
        yMaxAt += yDelta;

        range[0] = yMinAt;
        range[1] = yMaxAt;
    }

    public void calcYRangeAt(float xLeftValue, float xRightValue, @NotNull int[] linesVisibilityState, @NotNull int[] range) {
        final int xLeftIndexAt = findXLeftIndex(xLeftValue);
        final int xRightIndexAt = findXRightIndex(xRightValue, xLeftIndexAt);

        calcYRangeAt(xLeftIndexAt, xRightIndexAt, linesVisibilityState, range);
    }

    public float getXScale() {
        return scaleX;
    }

    public float getYScale() {
        return scaleY;
    }

    public @NotNull Path[] getLinesPaths() {
        return linesPaths;
    }

    public @NotNull float[][] getLinesLines() {
        return linesLines;
    }

    public @Nullable List<AxisMark> getXAxisMarks() {
        return xAxisMarks;
    }

    public @Nullable List<AxisMark> getYAxisMarks() {
        return yAxisMarks;
    }

    private final @NotNull int[] yRange = new int[2];

    private void updateYRange() {
        calcYRangeAt(xLeftIndex, xRightIndex, linesVisibilityState, yRange);

        yMin = yRange[0];
        yMax = yRange[1];
    }

    private void updateScales() {
        scaleX = area.width() / Math.abs(xRightValue - xLeftValue);
        scaleY = area.height() / (float) Math.abs(yMax - yMin);
    }

    boolean b1 = false;
    boolean b2 = false;

    private void updateLinesAndAxis() {
        if (!areaSet) {
            return;
        }
        if (!xLeftSet || !xRightSet) {
            return;
        }

        updateVisibleLinesCount();

        updateMatrix();

        //if (!b1) {
            //b1 = true;

            updateLines();
        //}

        //if (!b2) {
            //b2 = true;

            updateAxisMarks();
        //}
    }

    private void updateVisibleLinesCount() {
        visibleLinesCount = 0;
        for (int j = 0; j < inputData.LinesValues.length; j++) {
            if (linesVisibilityState[j] != 0) {
                visibleLinesCount++;
            }
        }
    }

    private void updateMatrix() {
        final float xToPixelHelper = area.left/* + x * scaleX*/ - xLeftValue * scaleX;
        final float yToPixelHelper = area.bottom/* - y * scaleY*/ + yMin * scaleY;

        matrix.setScale(scaleX, -scaleY);
        matrix.postTranslate(xToPixelHelper, yToPixelHelper);
    }

    private void updateLines() {
/*        //
        for (int j = 0; j < linesPaths.length; j++) {
            linesPaths[j].reset();

            // don't calc invisible lines
            if (invisibleLinesIndexes.contains(j)) {
                continue;
            }

            linesPaths[j].moveTo(
                    //xToPixel(inputData.XValues[xLeftIndex]),
                    xToPixelHelper + inputData.XValues[xLeftIndex] * scaleX,
                    //yToPixel(inputData.LinesValues[j][xLeftIndex])
                    yToPixelHelper - inputData.LinesValues[j][xLeftIndex] * scaleY
            );
            for (int i = xLeftIndex + 1; i <= xRightIndex; i++) {
                linesPaths[j].lineTo(
                        //xToPixel(inputData.XValues[i]),
                        xToPixelHelper + inputData.XValues[i] * scaleX,
                        //yToPixel(inputData.LinesValues[j][i])
                        yToPixelHelper - inputData.LinesValues[j][i] * scaleY
                );
            }
        }*/

/*        for (int j = 0; j < linesLines.length; j++) {
            // don't calc invisible lines
            if (invisibleLinesIndexes.contains(j)) {
                continue;
            }

            final float[] lineLines = linesLines[j];

            int k = 0;
            lineLines[k] = xToPixelHelper + inputData.XValues[xLeftIndex] * scaleX;
            lineLines[k + 1] = yToPixelHelper - inputData.LinesValues[j][xLeftIndex] * scaleY;
            k += 2;
            for (int i = xLeftIndex + 1; i < xRightIndex; i++) {
                lineLines[k] = xToPixelHelper + inputData.XValues[i] * scaleX;
                lineLines[k + 1] = yToPixelHelper - inputData.LinesValues[j][i] * scaleY;
                lineLines[k + 2] = lineLines[k];
                lineLines[k + 3] = lineLines[k + 1];
                k += 4;
            }
            lineLines[k] = xToPixelHelper + inputData.XValues[xRightIndex] * scaleX;
            lineLines[k + 1] = yToPixelHelper - inputData.LinesValues[j][xRightIndex] * scaleY;

//            if (BuildConfig.DEBUG && ((k + 2) != (xRightIndex - xLeftIndex + 1 - 1) * 4))
//                throw new AssertionError();
        }*/

        final int linePtsCount = xRightIndex - xLeftIndex + 1;

        for (int j = 0; j < linesLines.length; j++) {
            // don't calc invisible lines
            if (linesVisibilityState[j] == 0) {
                continue;
            }

            int k = 0;
            pts[k] = inputData.XValues[xLeftIndex];
            pts[k + 1] = inputData.LinesValues[j][xLeftIndex];
            k += 2;
            for (int i = xLeftIndex + 1; i < xRightIndex; i++) {
                pts[k] = inputData.XValues[i];
                pts[k + 1] = inputData.LinesValues[j][i];
                pts[k + 2] = pts[k];
                pts[k + 3] = pts[k + 1];
                k += 4;
            }
            pts[k] = inputData.XValues[xRightIndex];
            pts[k + 1] = inputData.LinesValues[j][xRightIndex];

            matrix.mapPoints(linesLines[j], 0, pts, 0, (linePtsCount - 1) << 1);
        }
    }

    public @NotNull Matrix getMatrix() {
        return matrix;
    }

    private void updateAxisMarks() {
        if (getIsMarksUpdating()) {
            updateXAxisMarks();
            updateYAxisMarks();
        }
    }

    // TODO: метод половинного деления (для findXRightIndex тоже)?
    public int findXLeftIndex(float xValue) {
        for (int i = 0; i < inputData.XValues.length; i++) {
            if (inputData.XValues[i] == xValue) {
                return i;
            } else if (inputData.XValues[i] > xValue) {
                return i > 0 ? i - 1 : i;
            }
        }
        return 0;
    }

    private int findXRightIndex(float xValue, int startingXIndex) {
        for (int i = startingXIndex; i < inputData.XValues.length; i++) {
            if (inputData.XValues[i] >= xValue) {
                return i;
            }
        }
        return inputData.XValues.length - 1;
    }

    public float xToPixel(float x) {
        return area.left + (x - xLeftValue) * scaleX;
    }

    public float pixelToX(float px) {
        return (px - area.left) / scaleX + xLeftValue;
    }

    public float yToPixel(int y) {
        return area.bottom - (y - yMin) * scaleY;
    }

    /*public float pixelToY(float py) {
        return py / scaleY + yMin;
    }*/

    private boolean getIsMarksUpdating() {
        return (axisLineCount > 0) && (xAxisTextConv != null);
    }

    private static final long MSEC_PER_HOUR = 60 * 60 * 1000L;
    private static final long MSEC_PER_DAY = 24 * MSEC_PER_HOUR;

    private void updateXAxisMarks() {
        if (BuildConfig.DEBUG && (xAxisMarks == null)) throw new AssertionError();
        if (BuildConfig.DEBUG && (axisLineCount <= 0)) throw new AssertionError();
        if (BuildConfig.DEBUG && (xAxisTextConv == null)) throw new AssertionError();

        xAxisMarks.clear();

        final float xSwing = Math.abs(xRightValue - xLeftValue);

        long stepValue = (long) (xSwing / axisLineCount);

        // beautify step
        if (stepValue > MSEC_PER_DAY) {
            stepValue = stepValue / MSEC_PER_DAY * MSEC_PER_DAY;
        } else {
            stepValue = stepValue / MSEC_PER_HOUR * MSEC_PER_HOUR;
        }

        final float stepPixel = stepValue * scaleX;
        if (stepPixel <= 0) {
            return;
        }

        // начало отсчёта
        final long startXValue = (long) (xLeftValue / stepValue) * stepValue;
        final float startXPixel = xToPixel(startXValue);
        //DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault());
        //Log.v("CDD", String.format("left = %f, right = %f, swing = %f, start = %s, scaleX = %f", xLeftValue, xRightValue, xSwing, df.format(new Date(startXValue)), 1f / scaleX));

        final float w = area.width();

        long i = startXValue;
        for (float x = startXPixel; x < w; x += stepPixel) {
            final String text = xAxisTextConv.toText(i);

            xAxisMarks.add(new AxisMark(x, text));

            i += stepValue;
        }
    }

    private void updateYAxisMarks() {
        if (BuildConfig.DEBUG && (yAxisMarks == null)) throw new AssertionError();
        if (BuildConfig.DEBUG && (axisLineCount <= 0)) throw new AssertionError();

        yAxisMarks.clear();

        final float ySwing = Math.abs(yMax - yMin);

        int stepValue = (int) (ySwing / axisLineCount);

        // beautify step
        int k = 0;
        while (stepValue >= 20) {
            stepValue /= 10;
            k++;
        }
        // значения (10, 19] при делении на 10 "дадут" 1, и линий окажется слишком много - равномерно распределяем их
        // между значениями 10, 15 и 20.
        if (stepValue > 10) {
            if (stepValue >= 18) {
                stepValue = 20;
            } else if (stepValue >= 14) {
                stepValue = 15;
            } else {
                stepValue = 10;
            }
        }
        while (k > 0) {
            stepValue *= 10;
            k--;
        }

        final float stepPixel = stepValue * scaleY;
        if (stepPixel <= 0) {
            return;
        }

        // начало отсчёта
        int startYValue = yMin / stepValue * stepValue;
        if (startYValue < yMin) {
            startYValue += stepValue;
        }
        final float startYPixel = yToPixel(startYValue);

        int i = startYValue;
        for (float y = startYPixel; y >= 0; y -= stepPixel) {
            final String text = String.valueOf(i);

            yAxisMarks.add(new AxisMark(y, text));

            i += stepValue;
        }
    }

    // Режим нижней границы Y - какое значение используется для минимума по Y на графике
    private enum YMinMode {
        // использование минимума в отображаемом диапазоне X
        RANGE,
        // нулевое значение
        ZERO,
    }

    public interface AxisTextConverter {
        @NotNull String toText(long value);
    }

    public static class AxisMark {
        private float position;
        private @NotNull String text;

        public AxisMark(float position, @NotNull String text) {
            this.position = position;
            this.text = text;
        }

        public float getPosition() {
            return position;
        }

        public @NotNull String getText() {
            return text;
        }
    }
}
