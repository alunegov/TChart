package com.github.alunegov.tchart;

import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// Данные графика, используемые при отрисовке
public class ChartDrawData {
    // исходные данные графика
    private ChartInputData inputData;
    private ChartInputDataStats inputDataStats;
    // Кол-во линий оцифровки осей
    private int axisLineCount;
    // Преобразователь значения в текст для оцифровки оси X
    private AxisTextConverter xAxisTextCnv;
    //
    private boolean mYRangeEnlarging;
    // режим нижней границы Y
    private YMinMode yMinMode;
    private DrawLinesMode drawLinesMode;
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
    private float[][] linesLines;
    // Метки для оцифровки осей
    private List<AxisMark> xAxisMarks, yAxisMarks;

    private final @NotNull Matrix matrix = new Matrix();
    private float[] pts;

    private RectF[][] linesRects;

    private float[] mTmpStackedSum;

    public ChartDrawData(@NotNull ChartInputData inputData, @NotNull ChartInputDataStats inputDataStats) {
        this.inputData = inputData;
        this.inputDataStats = inputDataStats;

        switch (inputData.linesType) {
            case LINE:
                yMinMode = YMinMode.RANGE;
                break;
            case BAR:
            case AREA:
                yMinMode = YMinMode.ZERO;
                break;
        }

        switch (inputData.linesType) {
            case LINE:
                drawLinesMode = DrawLinesMode.LINES;
                break;
            case BAR:
                drawLinesMode = DrawLinesMode.PATH_REVERSE;
                break;
            case AREA:
                drawLinesMode = DrawLinesMode.PATH_REVERSE;
                break;
        }

        // TODO: выделять память только под нужный режим отрисовки drawLinesMode

        linesPaths = new Path[inputData.LinesValues.length];
        for (int i = 0; i < linesPaths.length; i++) {
            linesPaths[i] = new Path();
        }

        linesLines = new float[inputData.LinesValues.length][(inputData.XValues.length - 1) * 4];

        pts = new float[(inputData.XValues.length - 1) * 4];

        linesRects = new RectF[inputData.LinesValues.length][inputData.XValues.length];
        for (int i = 0; i < linesRects.length; i++) {
            for (int j = 0; j < linesRects[i].length; j++) {
                linesRects[i][j] = new RectF();
            }
        }

        mYRangeEnlarging = false;
    }

    public void enableMarksUpdating(int axisLineCount, @NotNull AxisTextConverter xAxisTextCnv) {
        this.axisLineCount = axisLineCount;
        this.xAxisTextCnv = xAxisTextCnv;

        xAxisMarks = new ArrayList<>();
        yAxisMarks = new ArrayList<>();
    }

    public void enableYRangeEnlarging() {
        mYRangeEnlarging = true;
    }

    public DrawLinesMode getDrawLinesMode() {
        return drawLinesMode;
    }

    public @NotNull RectF getArea() {
        return area;
    }

    public void setArea(@NotNull RectF area) {
        this.area.set(area);
        this.areaSet = true;

        //updateYRange();
        updateScalesAndMatrix();
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
            updateScalesAndMatrix();
            updateLinesAndAxis();
        }
    }

    public void updateLineVisibility(boolean doUpdate) {
        if (doUpdate) {
            updateYRange();
            updateScalesAndMatrix();
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

        updateScalesAndMatrix();
        updateLinesAndAxis();
    }

    public void calcYRangeAt(int xLeftIndex, int xRightIndex, @NotNull int[] linesVisibilityState, @NotNull int[] range) {
        if (BuildConfig.DEBUG && (range.length != 2)) throw new AssertionError();

        final @NotNull int[] yMinMax = inputDataStats.findYMinMax(xLeftIndex, xRightIndex, linesVisibilityState);

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
        if (mYRangeEnlarging) {
            final int yDelta = (int) (0.05 * (yMaxAt - yMinAt));
            if (yMinAt != 0) {
                yMinAt -= yDelta;
            }
            yMaxAt += yDelta;
        }

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

    public @NotNull RectF[][] getLinesRects() {
        return linesRects;
    }

    public @Nullable List<AxisMark> getXAxisMarks() {
        return xAxisMarks;
    }

    public @Nullable List<AxisMark> getYAxisMarks() {
        return yAxisMarks;
    }

    private final @NotNull int[] yRange = new int[2];

    private void updateYRange() {
        calcYRangeAt(xLeftIndex, xRightIndex, inputDataStats.getLinesVisibilityState(), yRange);

        yMin = yRange[0];
        yMax = yRange[1];
    }

    private void updateScalesAndMatrix() {
        scaleX = area.width() / Math.abs(xRightValue - xLeftValue);
        scaleY = area.height() / (float) Math.abs(yMax - yMin);

        final float xToPixelHelper = area.left/* + x * scaleX*/ - xLeftValue * scaleX;
        final float yToPixelHelper = area.bottom/* - y * scaleY*/ + yMin * scaleY;
        matrix.setScale(scaleX, -scaleY);
        matrix.postTranslate(xToPixelHelper, yToPixelHelper);
    }

//    private boolean b1 = false;
//    private boolean b2 = false;

    private void updateLinesAndAxis() {
        if (!areaSet) {
            return;
        }
        if (!xLeftSet || !xRightSet) {
            return;
        }

//        if (!b1) {
//            b1 = true;
            updateLines();
//        }

//        if (!b2) {
//            b2 = true;
            updateAxisMarks();
//        }
    }

    private void updateLines() {
        switch (inputData.linesType) {
            case LINE:
                //updateLines_LINE_Path();
                //updateLines_LINE_Lines();
                updateLines_LINE_Lines_Matrix();
                break;

            case BAR:
                //updateLines_BAR_Rect();
                updateLines_BAR_Path_Matrix();
                break;

            case AREA:
                //updateLines_AREA_Rect();
                //updateLines_AREA_Path();
                updateLines_AREA_Path_Matrix();
                break;
        }
    }

    private void updateLines_LINE_Path() {
        final int[] linesVisibilityState = inputDataStats.getLinesVisibilityState();

        final float xToPixelHelper = area.left/* + x * scaleX*/ - xLeftValue * scaleX;
        final float yToPixelHelper = area.bottom/* - y * scaleY*/ + yMin * scaleY;

        for (int j = 0; j < linesPaths.length; j++) {
            linesPaths[j].reset();

            if (linesVisibilityState[j] == ChartInputDataStats.VISIBILITY_STATE_OFF) {
                continue;
            }

            linesPaths[j].moveTo(
                    xToPixelHelper + inputData.XValues[xLeftIndex] * scaleX,
                    yToPixelHelper - inputData.LinesValues[j][xLeftIndex] * scaleY
            );
            for (int i = xLeftIndex + 1; i <= xRightIndex; i++) {
                linesPaths[j].lineTo(
                        xToPixelHelper + inputData.XValues[i] * scaleX,
                        yToPixelHelper - inputData.LinesValues[j][i] * scaleY
                );
            }
        }
    }

    private void updateLines_LINE_Lines() {
        final int[] linesVisibilityState = inputDataStats.getLinesVisibilityState();

        final float xToPixelHelper = area.left/* + x * scaleX*/ - xLeftValue * scaleX;
        final float yToPixelHelper = area.bottom/* - y * scaleY*/ + yMin * scaleY;

        for (int j = 0; j < linesLines.length; j++) {
            if (linesVisibilityState[j] == ChartInputDataStats.VISIBILITY_STATE_OFF) {
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

            if (BuildConfig.DEBUG && ((k + 2) != (xRightIndex - xLeftIndex + 1 - 1) * 4)) throw new AssertionError();
        }
    }

    private void updateLines_LINE_Lines_Matrix() {
        final int[] linesVisibilityState = inputDataStats.getLinesVisibilityState();

        int linePtsCount = xRightIndex - xLeftIndex + 1;

        linePtsCount = (linePtsCount - 1) << 1;

        for (int j = 0; j < linesLines.length; j++) {
            if (linesVisibilityState[j] == ChartInputDataStats.VISIBILITY_STATE_OFF) {
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

            matrix.mapPoints(linesLines[j], 0, pts, 0, linePtsCount);

            if (BuildConfig.DEBUG && ((k + 2) != linePtsCount * 2)) throw new AssertionError();
        }
    }

    private void updateLines_BAR_Rect() {
        final int[] linesVisibilityState = inputDataStats.getLinesVisibilityState();

        final int ptsCount = xRightIndex - xLeftIndex + 1;
        final float rectWidth = area.width() / ptsCount;
        //Log.d("CDD", String.format("ptsCount = %d, rectWidth = %f", ptsCount, rectWidth));

        int l = -1;
        for (int j = 0; j < linesLines.length; j++) {
            if (linesVisibilityState[j] == ChartInputDataStats.VISIBILITY_STATE_OFF) {
                continue;
            }

            final RectF[] lineRects = linesRects[j];

            final float lineK = (float) linesVisibilityState[j] / ChartInputDataStats.VISIBILITY_STATE_ON;

            if (l == -1) {
                for (int i = xLeftIndex; i <= xRightIndex; i++) {
                    lineRects[i].left = xToPixel(inputData.XValues[i]);
                    lineRects[i].top = yToPixel(inputData.LinesValues[j][i] * lineK);
                    lineRects[i].bottom = area.bottom;
                }
            } else {
                final RectF[] prevLineRects = linesRects[l];

                for (int i = xLeftIndex; i <= xRightIndex; i++) {
                    lineRects[i].left = prevLineRects[i].left;
                    lineRects[i].top = prevLineRects[i].top - (inputData.LinesValues[j][i] * lineK) * scaleY;
                    lineRects[i].bottom = prevLineRects[i].top;
                }
            }
            l = j;

            lineRects[xLeftIndex].right = lineRects[xLeftIndex].left + rectWidth;
            for (int i = xLeftIndex + 1; i <= xRightIndex; i++) {
                lineRects[i - 1].right = lineRects[i].left;
            }
            lineRects[xRightIndex].right = lineRects[xRightIndex].left + rectWidth;
        }
    }

    private void updateLines_BAR_Path_Matrix() {
        final int[] linesVisibilityState = inputDataStats.getLinesVisibilityState();

        final int[] stackedSum = inputDataStats.getStackedSum();
        assert stackedSum != null;

        final @NotNull float[] tmpStackedSum = getTmpStackedSum();
        for (int i = xLeftIndex; i <= xRightIndex; i++) {
            tmpStackedSum[i] = 0;
        }

        final int ptsCount = xRightIndex - xLeftIndex + 1;
        final float rectWidth = (xRightValue - xLeftValue) / ptsCount;
        //Log.d("CDD", String.format("ptsCount = %d, rectWidth = %f", ptsCount, rectWidth));

        float prevMin = yMin;
        float currMin;

        for (int j = 0; j < linesLines.length; j++) {
            final Path linePath = linesPaths[j];

            linePath.reset();

            if (linesVisibilityState[j] == ChartInputDataStats.VISIBILITY_STATE_OFF) {
                continue;
            }

            final float lineK = (float) linesVisibilityState[j] / ChartInputDataStats.VISIBILITY_STATE_ON;

            currMin = yMax;

            for (int i = xLeftIndex; i <= xRightIndex; i++) {
                tmpStackedSum[i] += inputData.LinesValues[j][i] * lineK;

                if (tmpStackedSum[i] < currMin) {
                    currMin = tmpStackedSum[i];
                }
            }

            linePath.moveTo(
                    inputData.XValues[xLeftIndex],
                    tmpStackedSum[xLeftIndex]
            );
            linePath.lineTo(
                    inputData.XValues[xLeftIndex] + rectWidth,
                    tmpStackedSum[xLeftIndex]
            );
            for (int i = xLeftIndex + 1; i <= xRightIndex; i++) {
                linePath.lineTo(
                        inputData.XValues[i],
                        tmpStackedSum[i]
                );
                linePath.lineTo(
                        inputData.XValues[i] + rectWidth,
                        tmpStackedSum[i]
                );
            }

            // right |
            linePath.lineTo(
                    inputData.XValues[xRightIndex] + rectWidth,
                    prevMin
            );
            // _
            linePath.lineTo(
                    inputData.XValues[xLeftIndex],
                    prevMin
            );
            // left |
            linePath.close();

            linePath.transform(matrix);

            prevMin = currMin;
        }
    }

    private void updateLines_AREA_Rect() {
        final int[] linesVisibilityState = inputDataStats.getLinesVisibilityState();

        final int[] stackedSum = inputDataStats.getStackedSum();
        assert stackedSum != null;

        int linePtsCount = xRightIndex - xLeftIndex + 1;

        final float rectWidth = area.width() / linePtsCount;
        //Log.d("CDD", String.format("linePtsCount = %d, rectWidth = %f", linePtsCount, rectWidth));

        int l = -1;
        for (int j = 0; j < linesLines.length; j++) {
            if (linesVisibilityState[j] == ChartInputDataStats.VISIBILITY_STATE_OFF) {
                continue;
            }

            final RectF[] lineRects = linesRects[j];
            final float lineK = (float) linesVisibilityState[j] / ChartInputDataStats.VISIBILITY_STATE_ON;

            if (l == -1) {
                for (int i = xLeftIndex; i <= xRightIndex; i++) {
                    lineRects[i].left = xToPixel(inputData.XValues[i]);
                    lineRects[i].top = yToPixel(inputData.LinesValues[j][i] * lineK / stackedSum[i] * 100f);
                    lineRects[i].bottom = area.bottom;
                }
            } else {
                final RectF[] prevLineRects = linesRects[l];

                for (int i = xLeftIndex; i <= xRightIndex; i++) {
                    lineRects[i].left = prevLineRects[i].left;
                    lineRects[i].top = prevLineRects[i].top - (inputData.LinesValues[j][i] * lineK / stackedSum[i] * 100f) * scaleY;
                    lineRects[i].bottom = prevLineRects[i].top;
                }
            }
            l = j;

            lineRects[xLeftIndex].right = lineRects[xLeftIndex].left + rectWidth;
            for (int i = xLeftIndex + 1; i <= xRightIndex; i++) {
                lineRects[i - 1].right = lineRects[i].left;
            }
            lineRects[xRightIndex].right = lineRects[xRightIndex].left + rectWidth;
        }
    }

    private void updateLines_AREA_Path() {
        final int[] linesVisibilityState = inputDataStats.getLinesVisibilityState();

        final int[] stackedSum = inputDataStats.getStackedSum();
        assert stackedSum != null;

        final @NotNull float[] tmpStackedSum = getTmpStackedSum();
        for (int i = xLeftIndex; i <= xRightIndex; i++) {
            tmpStackedSum[i] = 0;
        }

        final float xToPixelHelper = area.left/* + x * scaleX*/ - xLeftValue * scaleX;
        final float yToPixelHelper = area.bottom/* - y * scaleY*/ + yMin * scaleY;

        for (int j = 0; j < linesLines.length; j++) {
            final Path linePath = linesPaths[j];

            linePath.reset();

            if (linesVisibilityState[j] == ChartInputDataStats.VISIBILITY_STATE_OFF) {
                continue;
            }

            final float lineK = (float) linesVisibilityState[j] / ChartInputDataStats.VISIBILITY_STATE_ON;

            for (int i = xLeftIndex; i <= xRightIndex; i++) {
                tmpStackedSum[i] += inputData.LinesValues[j][i] * lineK;

            }

            linePath.moveTo(
                    xToPixelHelper + inputData.XValues[xLeftIndex] * scaleX,
                    yToPixelHelper - (tmpStackedSum[xLeftIndex] / stackedSum[xLeftIndex] * 100f) * scaleY
            );
            for (int i = xLeftIndex + 1; i <= xRightIndex; i++) {
                linePath.lineTo(
                        xToPixelHelper + inputData.XValues[i] * scaleX,
                        yToPixelHelper - (tmpStackedSum[i] / stackedSum[i] * 100f) * scaleY
                );
            }

            // right |
            linePath.lineTo(
                    xToPixelHelper + inputData.XValues[xRightIndex] * scaleX,
                    yToPixelHelper - yMin * scaleY
            );
            // _
            linePath.lineTo(
                    xToPixelHelper + inputData.XValues[xLeftIndex] * scaleX,
                    yToPixelHelper - yMin * scaleY
            );
            // left |
            linePath.close();
        }
    }

    private void updateLines_AREA_Path_Matrix() {
        final int[] linesVisibilityState = inputDataStats.getLinesVisibilityState();

        int lastVisibleLineIndex = -1;
        for (int j = linesLines.length - 1; j >= 0; j--) {
            if (linesVisibilityState[j] != ChartInputDataStats.VISIBILITY_STATE_OFF) {
                lastVisibleLineIndex = j;
                break;
            }
        }

        final int[] stackedSum = inputDataStats.getStackedSum();
        assert stackedSum != null;

        final @NotNull float[] tmpStackedSum = getTmpStackedSum();
        for (int i = xLeftIndex; i <= xRightIndex; i++) {
            tmpStackedSum[i] = 0;
        }

        float prevPercMin = 0f;

        for (int j = 0; j < linesLines.length; j++) {
            final Path linePath = linesPaths[j];

            linePath.reset();

            if (linesVisibilityState[j] == ChartInputDataStats.VISIBILITY_STATE_OFF) {
                continue;
            }

            float currPercMin = 100f;

            if (j != lastVisibleLineIndex) {
                final float lineK = (float) linesVisibilityState[j] / ChartInputDataStats.VISIBILITY_STATE_ON;

                for (int i = xLeftIndex; i <= xRightIndex; i++) {
                    tmpStackedSum[i] += inputData.LinesValues[j][i] * lineK;

                    final float perc = tmpStackedSum[i] / stackedSum[i] * 100f;
                    if (perc < currPercMin) {
                        currPercMin = perc;
                    }
                }

                linePath.moveTo(
                        inputData.XValues[xLeftIndex],
                        tmpStackedSum[xLeftIndex] / stackedSum[xLeftIndex] * 100f
                );
                for (int i = xLeftIndex + 1; i <= xRightIndex; i++) {
                    linePath.lineTo(
                            inputData.XValues[i],
                            tmpStackedSum[i] / stackedSum[i] * 100f
                    );
                }

                // right |
                linePath.lineTo(
                        inputData.XValues[xRightIndex],
                        prevPercMin
                );
                // _
                linePath.lineTo(
                        inputData.XValues[xLeftIndex],
                        prevPercMin
                );
                // left |
                linePath.close();
            } else {
                linePath.addRect(
                        inputData.XValues[xLeftIndex],
                        currPercMin,
                        inputData.XValues[xRightIndex],
                        prevPercMin,
                        Path.Direction.CW
                );
            }

            linePath.transform(matrix);

            prevPercMin = currPercMin;
        }
    }

    private @NotNull float[] getTmpStackedSum() {
        if (mTmpStackedSum == null) {
            mTmpStackedSum = new float[inputData.XValues.length];
        }
        return mTmpStackedSum;
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

    public float yToPixel(float y) {
        return area.bottom - (y - yMin) * scaleY;
    }

    private boolean getIsMarksUpdating() {
        return (axisLineCount > 0) && (xAxisTextCnv != null);
    }

    private static final long MSEC_PER_HOUR = 60 * 60 * 1000L;
    private static final long MSEC_PER_DAY = 24 * MSEC_PER_HOUR;

    private void updateXAxisMarks() {
        if (BuildConfig.DEBUG && (xAxisMarks == null)) throw new AssertionError();
        if (BuildConfig.DEBUG && (axisLineCount <= 0)) throw new AssertionError();
        if (BuildConfig.DEBUG && (xAxisTextCnv == null)) throw new AssertionError();

        xAxisMarks.clear();

        final float xSwing = Math.abs(xRightValue - xLeftValue);

        long stepValue = (long) (xSwing / axisLineCount);

        // beautify step
        if (stepValue > MSEC_PER_DAY) {
            stepValue = stepValue / MSEC_PER_DAY * MSEC_PER_DAY;
        } else {
            stepValue = stepValue / MSEC_PER_HOUR * MSEC_PER_HOUR;
        }

        if (BuildConfig.DEBUG && (stepValue == 0)) throw new AssertionError();

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
            final String text = xAxisTextCnv.toText(i);

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

        if (stepValue == 0) {
            return;
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

    public enum DrawLinesMode {
        PATH,
        PATH_REVERSE,
        LINES,
        RECT,
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
