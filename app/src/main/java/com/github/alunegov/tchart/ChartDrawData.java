package com.github.alunegov.tchart;

import android.graphics.Path;
import android.graphics.RectF;

import org.jetbrains.annotations.NotNull;

// Данные графика, используемые при отрисовке
public class ChartDrawData {
    // исходные данные графика
    private ChartInputData inputData;
    // режим нижней границы Y
    private YMinMode yMinMode = YMinMode.RANGE;
    // минимальное значение Y по всему диапазону X по всем сигналам (используется в режиме YMinMode.FULL)
    private int yMinFull;
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
    // минимальное и максимальное значения Y по отображаемому диапазону X по всем сигналам (с учётом yMinMode)
    private int yMin, yMax;
    // коэффициент пересчета значений в пиксели
    private float scaleX, scaleY;
    // отображаемые данные линий (сигналов) в виде Path
    private Path[] linesPaths;

    public ChartDrawData(@NotNull ChartInputData inputData) {
        this.inputData = inputData;

        // calc yMinFull only in YMinMode.FULL mode
        if (yMinMode == YMinMode.FULL) {
            final int[] yMinMax = inputData.findYMinMax(0, inputData.XValues.length - 1);
            yMinFull = yMinMax[0];
        }
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

    public int getYMin() {
        return yMin;
    }

    public int getYMax() {
        return yMin;
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

        final int[] yMinMax = inputData.findYMinMax(xLeftIndex, xRightIndex);

        switch (yMinMode) {
            case RANGE:
                yMin = yMinMax[0];
                break;
            case FULL:
                yMin = yMinFull;
                break;
            case ZERO:
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

    // Режим нижней границы Y - какое значение используется для минимума по Y на графике
    public enum YMinMode {
        // использование минимума в отображаемом диапазоне X
        RANGE,
        // использование минимума во всём диапазоне X
        FULL,
        // нулевое значение
        ZERO,
    }
}
