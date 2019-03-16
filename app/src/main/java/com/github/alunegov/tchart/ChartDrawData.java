package com.github.alunegov.tchart;

import android.graphics.Path;
import android.graphics.RectF;

import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

// Данные графика, используемые при отрисовке
public class ChartDrawData {
    // исходные данные графика
    private ChartInputData inputData;
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
    // минимальное и максимальное значения Y по отображаемому диапазону X по всем сигналам (с учётом yMinMode)
    private int yMin, yMax;
    // коэффициент пересчета значений в пиксели
    private float scaleX, scaleY;
    // отображаемые данные линий (сигналов) в виде Path
    private Path[] linesPaths;

    public ChartDrawData(@NotNull ChartInputData inputData) {
        this.inputData = inputData;

        invisibleLinesIndexes = new HashSet<Integer>(inputData.LinesValues.length);

        linesPaths = new Path[inputData.LinesValues.length];
        for (int i = 0; i < linesPaths.length; i++) {
            linesPaths[i] = new Path();
        }
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

    public @NotNull Path[] getLinesPaths() {
        return linesPaths;
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

        yMax = yMinMax[1];

        scaleX = area.width() / (xRightValue - xLeftValue);
        scaleY = area.height() / (float) Math.abs(yMax - yMin);

        for (int j = 0; j < linesPaths.length; j++) {
            linesPaths[j].reset();

            // don't calc invisible lines
            if (invisibleLinesIndexes.contains(j)) {
                continue;
            }

            linesPaths[j].moveTo(
                    xToPixel(inputData.XValues[xLeftIndex] - xLeftValue),
                    yToPixel(inputData.LinesValues[j][xLeftIndex] - yMin)
            );
            for (int i = xLeftIndex + 1; i <= xRightIndex; i++) {
                linesPaths[j].lineTo(
                        xToPixel(inputData.XValues[i] - xLeftValue),
                        yToPixel(inputData.LinesValues[j][i] - yMin)
                );
            }
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
        return area.left + x * scaleX;
    }

    public float pixelToX(float px) {
        return px / scaleX + xLeftValue;
    }

    public float yToPixel(int y) {
        return area.bottom - y * scaleY;
    }

    public float pixelToY(float py) {
        return py * scaleY + yMin;
    }

    // Режим нижней границы Y - какое значение используется для минимума по Y на графике
    public enum YMinMode {
        // использование минимума в отображаемом диапазоне X
        RANGE,
        // нулевое значение
        ZERO,
    }
}
