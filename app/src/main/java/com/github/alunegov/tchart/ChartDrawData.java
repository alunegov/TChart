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

        update();
    }

    public float getXLeftValue() {
        return xLeftValue;
    }

    public float getXRightValue() {
        return xRightValue;
    }

    public void setXRange(float xLeftValue, float xRightValue) {
        this.xLeftValue = xLeftValue;
        xLeftIndex = findXLeftIndex(xLeftValue);
        xLeftSet = true;

        this.xRightValue = xRightValue;
        xRightIndex = findXRightIndex(xRightValue, xLeftIndex);
        xRightSet = true;

        update();
    }

    public void setXRange(int xLeftIndex, int xRightIndex) {
        this.xLeftIndex = xLeftIndex;
        xLeftValue = inputData.XValues[xLeftIndex];
        xLeftSet = true;

        this.xRightIndex = xRightIndex;
        xRightValue = inputData.XValues[xRightIndex];
        xRightSet = true;

        update();
    }

    public Set<Integer> getInvisibleLinesIndexes() {
        return invisibleLinesIndexes;
    }

    public boolean getIsAllLinesInvisible() {
        return isAllLinesInvisible;
    }

    public void updateLineVisibility(int lineIndex, boolean visible) {
        if ((lineIndex < 0) || (inputData.LinesValues.length <= lineIndex)) {
            return;
        }

        if (visible) {
            invisibleLinesIndexes.remove(lineIndex);
        } else {
            invisibleLinesIndexes.add(lineIndex);
        }

        update();
    }

    public int getYMin() {
        return yMin;
    }

    public int getYMax() {
        return yMax;
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

    public @Nullable List<AxisMark> getXAxisMarks() {
        return xAxisMarks;
    }

    public @Nullable List<AxisMark> getYAxisMarks() {
        return yAxisMarks;
    }

    private void update() {
        if (!areaSet) {
            return;
        }
        if (!xLeftSet || !xRightSet) {
            return;
        }

        final int[] yMinMax = inputData.findYMinMax(xLeftIndex, xRightIndex, invisibleLinesIndexes);

        switch (yMinMode) {
            case RANGE:
                yMin = yMinMax[0];
                break;
            case ZERO:
                assert yMinMax[0] >= 0;
                yMin = 0;
                break;
            default:
                yMin = 0;
        }

        // добавляем к максимуму часть размаха, чтобы сверху было немного места (так на ref, была видна пометка точки)
        yMax = yMinMax[1] + (int) (0.05 * (yMinMax[1] - yMin));

        scaleX = area.width() / Math.abs(xRightValue - xLeftValue);
        scaleY = area.height() / (float) Math.abs(yMax - yMin);

        int visibleLinesCount = 0;

        for (int j = 0; j < linesPaths.length; j++) {
            linesPaths[j].reset();

            // don't calc invisible lines
            if (invisibleLinesIndexes.contains(j)) {
                continue;
            }

            linesPaths[j].moveTo(
                    xToPixel(inputData.XValues[xLeftIndex]),
                    yToPixel(inputData.LinesValues[j][xLeftIndex])
            );
            for (int i = xLeftIndex + 1; i <= xRightIndex; i++) {
                linesPaths[j].lineTo(
                        xToPixel(inputData.XValues[i]),
                        yToPixel(inputData.LinesValues[j][i])
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

    private void updateXAxisMarks() {
        assert xAxisMarks != null;
        assert axisLineCount > 0;
        assert xAxisTextConv != null;

        xAxisMarks.clear();

        final float xSwing = Math.abs(xRightValue - xLeftValue);

        final long stepValue = (long) (xSwing / axisLineCount);
        // TODO: beautify step?

        final float stepPixel = stepValue * scaleX;
        if (stepPixel <= 0) {
            return;
        }

        // начало отсчёта
        final long startXValue = (long) (xLeftValue / stepValue) * stepValue;
        final float startXPixel = xToPixel(startXValue);

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
