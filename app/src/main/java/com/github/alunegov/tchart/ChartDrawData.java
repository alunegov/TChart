package com.github.alunegov.tchart;

import android.graphics.Path;
import android.graphics.RectF;

import java.util.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// Данные графика, используемые при отрисовке
public class ChartDrawData {
    // исходные данные графика
    private ChartInputData inputData;
    // Кол-во линий оцифровки осей
    private int axisLineCount;
    // Преобразователь значения в текст для оцифровки оси X
    private AxisTextConverter xAxisTextConv;
    // режим нижней границы Y
    private YMinMode yMinMode = YMinMode.ZERO;
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
    private Set<Integer> invisibleLinesIndexes;
    private boolean isAllLinesInvisible;
    // минимальное и максимальное значения Y по отображаемому диапазону X по всем сигналам (с учётом yMinMode)
    private int yMin, yMax;
    // коэффициент пересчета значений в пиксели
    private float scaleX, scaleY;
    // отображаемые данные линий (сигналов) в виде Path
    private Path[] linesPaths;
    // Метки для оцифровки осей
    private List<AxisMark> xAxisMarks, yAxisMarks;

    public ChartDrawData(@NotNull ChartInputData inputData) {
        this.inputData = inputData;

        invisibleLinesIndexes = new HashSet<>(inputData.LinesValues.length);
        isAllLinesInvisible = false;

        linesPaths = new Path[inputData.LinesValues.length];
        for (int i = 0; i < linesPaths.length; i++) {
            linesPaths[i] = new Path();
        }
    }

    public void enableMarksUpdating(int axisLineCount, @NotNull AxisTextConverter xAxisTextConv) {
        this.axisLineCount = axisLineCount;
        this.xAxisTextConv = xAxisTextConv;

        xAxisMarks = new ArrayList<>();
        yAxisMarks = new ArrayList<>();
    }

    public void setArea(@NotNull RectF area) {
        this.area.set(area);
        this.areaSet = true;

        //updateYRange();
        updateScales();
        updateLinesAndAxis();
    }

    public void getXRange(@NotNull float[] range) {
        assert (range != null) && (range.length == 2);

        range[0] = xLeftValue;
        range[1] = xRightValue;
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

    public @NotNull Set<Integer> getInvisibleLinesIndexes() {
        return invisibleLinesIndexes;
    }

    public boolean getIsAllLinesInvisible() {
        return isAllLinesInvisible;
    }

    public static void updateLineVisibility(@NotNull Set<Integer> invisibleLinesIndexes, int lineIndex, boolean visible) {
        if (visible) {
            invisibleLinesIndexes.remove(lineIndex);
        } else {
            invisibleLinesIndexes.add(lineIndex);
        }
    }

    public void updateLineVisibility(int lineIndex, boolean visible, boolean doUpdate) {
        if ((lineIndex < 0) || (inputData.LinesValues.length <= lineIndex)) {
            return;
        }

        updateLineVisibility(invisibleLinesIndexes, lineIndex, visible);

        if (doUpdate) {
            updateYRange();
            updateScales();
            updateLinesAndAxis();
        }
    }

    public void getYRange(@NotNull int[] range) {
        assert (range != null) && (range.length == 2);

        range[0] = yMin;
        range[1] = yMax;
    }

    public void setYRange(int yMin, int yMax) {
        this.yMin = yMin;
        this.yMax = yMax;

        updateScales();
        updateLinesAndAxis();
    }

    public void calcYRangeAt(int xLeftIndex, int xRightIndex, @NotNull Set<Integer> invisibleLinesIndexes, @NotNull int[] range) {
        assert (range != null) && (range.length == 2);

        final @NotNull int[] yMinMax = inputData.findYMinMax(xLeftIndex, xRightIndex, invisibleLinesIndexes);

        int yMinAt;
        switch (yMinMode) {
            case RANGE:
                yMinAt = yMinMax[0];
                break;
            case ZERO:
                assert yMinMax[0] >= 0;
                yMinAt = 0;
                break;
            default:
                yMinAt = 0;
        }

        // добавляем к максимуму часть размаха, чтобы сверху было немного места (так на ref, была видна пометка точки)
        final int yMaxAt = yMinMax[1] + (int) (0.05 * (yMinMax[1] - yMin));

        range[0] = yMinAt;
        range[1] = yMaxAt;
    }

    public void calcYRangeAt(float xLeftValue, float xRightValue, @NotNull Set<Integer> invisibleLinesIndexes, @NotNull int[] range) {
        final int xLeftIndexAt = findXLeftIndex(xLeftValue);
        final int xRightIndexAt = findXRightIndex(xRightValue, xLeftIndexAt);

        calcYRangeAt(xLeftIndexAt, xRightIndexAt, invisibleLinesIndexes, range);
    }

    /*public float getXScale() {
        return scaleX;
    }*/

    /*public float getYScale() {
        return scaleY;
    }*/

    public @NotNull Path[] getLinesPaths() {
        return linesPaths;
    }

    public @Nullable List<AxisMark> getXAxisMarks() {
        return xAxisMarks;
    }

    public @Nullable List<AxisMark> getYAxisMarks() {
        return yAxisMarks;
    }

    private void updateYRange() {
        final @NotNull int[] yRange = new int[2];
        calcYRangeAt(xLeftIndex, xRightIndex, invisibleLinesIndexes, yRange);

        yMin = yRange[0];
        yMax = yRange[1];
    }

    private void updateScales() {
        scaleX = area.width() / Math.abs(xRightValue - xLeftValue);
        scaleY = area.height() / (float) Math.abs(yMax - yMin);
    }

    private void updateLinesAndAxis() {
        if (!areaSet) {
            return;
        }
        if (!xLeftSet || !xRightSet) {
            return;
        }

        int visibleLinesCount = 0;

        final float xToPixelHelper = area.left/* + x * scaleX*/ - xLeftValue * scaleX;
        final float yToPixelHelper = area.bottom/* - y * scaleY*/ + yMin * scaleY;

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

            visibleLinesCount++;
        }

        isAllLinesInvisible = visibleLinesCount == 0;

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
        assert xAxisMarks != null;
        assert axisLineCount > 0;
        assert xAxisTextConv != null;

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
        assert yAxisMarks != null;
        assert axisLineCount > 0;

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
