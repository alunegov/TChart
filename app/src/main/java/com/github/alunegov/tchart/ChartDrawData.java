package com.github.alunegov.tchart;

import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.*;

import android.util.Log;
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
    private int yLeftMin, yLeftMax, yRightMin, yRightMax;
    // коэффициент пересчета значений в пиксели
    private float scaleX, scaleYLeft, scaleYRight;
    // отображаемые данные линий (сигналов) в виде Path
    private Path[] linesPaths;
    private float[][] linesLines;
    // предыдущее обсчитанное значение курсора - запоминается в updateCursorPaths, используется в updateLines_BAR_Path_Matrix
    private int prevCursorIndex = AbsChartView.NO_CURSOR;
    // путь для отрисовки курсора в режиме BAR/PATH_REVERSE - столбик с курсором рисуется поверх области сигналов, задаваемой linesPaths
    private Path[] cursorPaths;
    // Метки для оцифровки осей
    private List<AxisMark> xAxisMarks, yAxisMarks;

    private final @NotNull Matrix matrixLeft = new Matrix();
    private final @NotNull Matrix matrixRight = new Matrix();
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
                //drawLinesMode = DrawLinesMode.RECT;
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

        cursorPaths = new Path[inputData.LinesValues.length];
        for (int i = 0; i < cursorPaths.length; i++) {
            cursorPaths[i] = new Path();
        }

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

        updateYRange();
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
        if (BuildConfig.DEBUG && (range.length != 4)) throw new AssertionError();

        range[0] = yLeftMin;
        range[1] = yLeftMax;
        range[2] = yRightMin;
        range[3] = yRightMax;
    }

    public void setYRange(int yLeftMin, int yLeftMax, int yRightMin, int yRightMax) {
        this.yLeftMin = yLeftMin;
        this.yLeftMax = yLeftMax;
        this.yRightMin = yRightMin;
        this.yRightMax = yRightMax;

        updateScalesAndMatrix();
        updateLinesAndAxis();
    }

    // по аналогии с updateLines_BAR_Path_Matrix
    public void updateCursorPaths(int cursorIndex) {
        prevCursorIndex = cursorIndex;

        if (cursorIndex == AbsChartView.NO_CURSOR || inputData.linesType != ChartInputData.LineType.BAR) {
            for (int j = 0; j < cursorPaths.length; j++) {
                cursorPaths[j].reset();
            }
            return;
        }

        final int[] linesVisibilityState = inputDataStats.getLinesVisibilityState();
        final boolean[] linesRightAlign = inputDataStats.getLinesRightAlign();

        float tmpStackedSum = 0;

        final int ptsCount = xRightIndex - xLeftIndex + 1;
        final float rectWidth = (float )(inputData.XValues[xRightIndex] - inputData.XValues[xLeftIndex]) / ptsCount;
        final float halfRectWidth = rectWidth / 2f;
        //Log.d("CDD", String.format("updateCursorPaths: ptsCount = %d, rectWidth = %f", ptsCount, rectWidth));

        float prevMin = yLeftMin;

        for (int j = 0; j < cursorPaths.length; j++) {
            if (BuildConfig.DEBUG && linesRightAlign[j]) throw new AssertionError();

            final Path cursorPath = cursorPaths[j];

            cursorPath.reset();

            if (linesVisibilityState[j] == ChartInputDataStats.VISIBILITY_STATE_OFF) {
                continue;
            }

            final float lineK = (float) linesVisibilityState[j] / ChartInputDataStats.VISIBILITY_STATE_ON;

            tmpStackedSum += inputData.LinesValues[j][cursorIndex] * lineK;

            cursorPath.addRect(
                    inputData.XValues[cursorIndex] - halfRectWidth,
                    tmpStackedSum,
                    inputData.XValues[cursorIndex] + halfRectWidth,
                    prevMin,
                    Path.Direction.CW
            );

            cursorPath.transform(matrixLeft);

            prevMin = tmpStackedSum;
        }
    }

    private final @NotNull int[] tmpYLeftMinMax = new int[2];
    private final @NotNull int[] tmpYRightMinMax = new int[2];
//    private final @NotNull YAxisMarksHelper tmpYMarksHelper = new YAxisMarksHelper();
//    private final @NotNull YAxisMarksHelper tmpYMarksHelper2 = new YAxisMarksHelper();

    private void calcYRangeAt(int xLeftIndex, int xRightIndex, @NotNull int[] linesVisibilityState, @NotNull int[] range) {
        if (BuildConfig.DEBUG && (range.length != 4)) throw new AssertionError();

        inputDataStats.findYMinMax(xLeftIndex, xRightIndex, false, linesVisibilityState, tmpYLeftMinMax);
        inputDataStats.findYMinMax(xLeftIndex, xRightIndex, true, linesVisibilityState, tmpYRightMinMax);

        switch (yMinMode) {
            case RANGE:
                range[0] = tmpYLeftMinMax[0];
                range[2] = tmpYRightMinMax[0];
                break;
            case ZERO:
                if (BuildConfig.DEBUG && (tmpYLeftMinMax[0] < 0)) throw new AssertionError();
                range[0] = 0;
                range[2] = 0;
                break;
            default:
                range[0] = 0;
                range[2] = 0;
        }

        range[1] = tmpYLeftMinMax[1];
        range[3] = tmpYRightMinMax[1];

        // добавляем к минимуму/максимуму часть размаха, чтобы снизу/сверху было немного места (так на ref, была видна пометка точки)
        // TODO: добавлять к минимуму не просто "часть размаха", а так, чтобы первая линия оцифровки была в "нулевом пикселе"
        if (mYRangeEnlarging) {
            final int yLeftDelta = Math.round(0.05f * (range[1] - range[0]));
            if (range[0] != 0) {
                range[0] -= yLeftDelta;
            }
            range[1] += yLeftDelta;

            final int yRightDelta = Math.round(0.05f * (range[3] - range[2]));
            if (range[2] != 0) {
                range[2] -= yRightDelta;
            }
            range[3] += yRightDelta;

/*            //
            if (areaSet) {
                calcYAxisMarksHelper(range[0], range[1], tmpYMarksHelper);

                Log.d("CDD", String.format("calcYRangeAt 1: yMin = %d, yMax = %d, swing = %d, tmpYMarksHelper - %s", range[0], range[1], range[1] - range[0], tmpYMarksHelper));

                if (tmpYMarksHelper.startValue > range[0]) {
                    tmpYMarksHelper.startValue -= tmpYMarksHelper.stepValue;
                }
                range[0] = tmpYMarksHelper.startValue;

                while (tmpYMarksHelper.startValue < range[1]) {
                    tmpYMarksHelper.startValue += tmpYMarksHelper.stepValue;
                }
                range[1] = tmpYMarksHelper.startValue;

                calcYAxisMarksHelper(range[0], range[1], tmpYMarksHelper2);

                Log.d("CDD", String.format("calcYRangeAt 2: yMin = %d, yMax = %d, swing = %d, tmpYMarksHelper - %s", range[0], range[1], range[1] - range[0], tmpYMarksHelper2));
            }*/
        }
    }

    public void calcYRangeAt(float xLeftValue, float xRightValue, @NotNull int[] linesVisibilityState, @NotNull int[] range) {
        final int xLeftIndexAt = findXLeftIndex(xLeftValue);
        final int xRightIndexAt = findXRightIndex(xRightValue, xLeftIndexAt);

        calcYRangeAt(xLeftIndexAt, xRightIndexAt, linesVisibilityState, range);
    }

    public @NotNull Path[] getLinesPaths() {
        return linesPaths;
    }

    public @NotNull float[][] getLinesLines() {
        return linesLines;
    }

    public @NotNull Path[] getCursorPaths() {
        return cursorPaths;
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

    private final @NotNull int[] tmpYRange = new int[4];

    private void updateYRange() {
        calcYRangeAt(xLeftIndex, xRightIndex, inputDataStats.getLinesVisibilityState(), tmpYRange);

        yLeftMin = tmpYRange[0];
        yLeftMax = tmpYRange[1];
        yRightMin = tmpYRange[2];
        yRightMax = tmpYRange[3];
    }

    private void updateScalesAndMatrix() {
        scaleX = area.width() / Math.abs(xRightValue - xLeftValue);
        scaleYLeft = area.height() / (float) Math.abs(yLeftMax - yLeftMin);
        scaleYRight = area.height() / (float) Math.abs(yRightMax - yRightMin);

        final float xToPixelHelper = area.left/* + x * scaleX*/ - xLeftValue * scaleX;

        final float yLeftToPixelHelper = area.bottom/* - y * scaleYLeft*/ + yLeftMin * scaleYLeft;
        matrixLeft.setScale(scaleX, -scaleYLeft);
        matrixLeft.postTranslate(xToPixelHelper, yLeftToPixelHelper);

        final float yRightToPixelHelper = area.bottom/* - y * scaleYRight*/ + yRightMin * scaleYRight;
        matrixRight.setScale(scaleX, -scaleYRight);
        matrixRight.postTranslate(xToPixelHelper, yRightToPixelHelper);
    }

    private boolean b1 = false;
    private boolean b2 = false;

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
        final boolean[] linesRightAlign = inputDataStats.getLinesRightAlign();

        final float xToPixelHelper = area.left/* + x * scaleX*/ - xLeftValue * scaleX;
        final float yToPixelHelper = area.bottom/* - y * scaleYLeft*/ + yLeftMin * scaleYLeft;

        for (int j = 0; j < linesPaths.length; j++) {
            if (BuildConfig.DEBUG && linesRightAlign[j]) throw new AssertionError();

            linesPaths[j].reset();

            if (linesVisibilityState[j] == ChartInputDataStats.VISIBILITY_STATE_OFF) {
                continue;
            }

            linesPaths[j].moveTo(
                    xToPixelHelper + inputData.XValues[xLeftIndex] * scaleX,
                    yToPixelHelper - inputData.LinesValues[j][xLeftIndex] * scaleYLeft
            );
            for (int i = xLeftIndex + 1; i <= xRightIndex; i++) {
                linesPaths[j].lineTo(
                        xToPixelHelper + inputData.XValues[i] * scaleX,
                        yToPixelHelper - inputData.LinesValues[j][i] * scaleYLeft
                );
            }
        }
    }

    private void updateLines_LINE_Lines() {
        final int[] linesVisibilityState = inputDataStats.getLinesVisibilityState();
        final boolean[] linesRightAlign = inputDataStats.getLinesRightAlign();

        final float xToPixelHelper = area.left/* + x * scaleX*/ - xLeftValue * scaleX;
        final float yToPixelHelper = area.bottom/* - y * scaleYLeft*/ + yLeftMin * scaleYLeft;

        for (int j = 0; j < linesLines.length; j++) {
            if (BuildConfig.DEBUG && linesRightAlign[j]) throw new AssertionError();

            if (linesVisibilityState[j] == ChartInputDataStats.VISIBILITY_STATE_OFF) {
                continue;
            }

            final float[] lineLines = linesLines[j];

            int k = 0;
            lineLines[k] = xToPixelHelper + inputData.XValues[xLeftIndex] * scaleX;
            lineLines[k + 1] = yToPixelHelper - inputData.LinesValues[j][xLeftIndex] * scaleYLeft;
            k += 2;
            for (int i = xLeftIndex + 1; i < xRightIndex; i++) {
                lineLines[k] = xToPixelHelper + inputData.XValues[i] * scaleX;
                lineLines[k + 1] = yToPixelHelper - inputData.LinesValues[j][i] * scaleYLeft;
                lineLines[k + 2] = lineLines[k];
                lineLines[k + 3] = lineLines[k + 1];
                k += 4;
            }
            lineLines[k] = xToPixelHelper + inputData.XValues[xRightIndex] * scaleX;
            lineLines[k + 1] = yToPixelHelper - inputData.LinesValues[j][xRightIndex] * scaleYLeft;

            if (BuildConfig.DEBUG && ((k + 2) != (xRightIndex - xLeftIndex + 1 - 1) * 4)) throw new AssertionError();
        }
    }

    private void updateLines_LINE_Lines_Matrix() {
        final int[] linesVisibilityState = inputDataStats.getLinesVisibilityState();
        final boolean[] linesRightAlign = inputDataStats.getLinesRightAlign();

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

            if (linesRightAlign[j]) {
                matrixRight.mapPoints(linesLines[j], 0, pts, 0, linePtsCount);
            } else {
                matrixLeft.mapPoints(linesLines[j], 0, pts, 0, linePtsCount);
            }

            if (BuildConfig.DEBUG && ((k + 2) != linePtsCount * 2)) throw new AssertionError();
        }
    }

    private void updateLines_BAR_Rect() {
        final int[] linesVisibilityState = inputDataStats.getLinesVisibilityState();
        final boolean[] linesRightAlign = inputDataStats.getLinesRightAlign();

        final int ptsCount = xRightIndex - xLeftIndex + 1;
        final float rectWidth = area.width() / ptsCount;
        //Log.d("CDD", String.format("updateLines_BAR_Rect: ptsCount = %d, rectWidth = %f", ptsCount, rectWidth));

        int l = -1;
        for (int j = 0; j < linesLines.length; j++) {
            if (BuildConfig.DEBUG && linesRightAlign[j]) throw new AssertionError();

            if (linesVisibilityState[j] == ChartInputDataStats.VISIBILITY_STATE_OFF) {
                continue;
            }

            final RectF[] lineRects = linesRects[j];

            final float lineK = (float) linesVisibilityState[j] / ChartInputDataStats.VISIBILITY_STATE_ON;

            if (l == -1) {
                for (int i = xLeftIndex; i <= xRightIndex; i++) {
                    lineRects[i].left = xToPixel(inputData.XValues[i]);
                    lineRects[i].top = yLeftToPixel(inputData.LinesValues[j][i] * lineK);
                    lineRects[i].bottom = area.bottom;
                }
            } else {
                final RectF[] prevLineRects = linesRects[l];

                for (int i = xLeftIndex; i <= xRightIndex; i++) {
                    lineRects[i].left = prevLineRects[i].left;
                    lineRects[i].top = prevLineRects[i].top - (inputData.LinesValues[j][i] * lineK) * scaleYLeft;
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

    // обновление курсора по аналогии с updateCursorPaths, предыдущее обсчитанное значение курсора запоминается там-же
    private void updateLines_BAR_Path_Matrix() {
        final int[] linesVisibilityState = inputDataStats.getLinesVisibilityState();
        final boolean[] linesRightAlign = inputDataStats.getLinesRightAlign();

        final @NotNull float[] tmpStackedSum = getTmpStackedSum();
        for (int i = xLeftIndex; i <= xRightIndex; i++) {
            tmpStackedSum[i] = 0;
        }

        final int ptsCount = xRightIndex - xLeftIndex + 1;
        final float rectWidth = (float) (inputData.XValues[xRightIndex] - inputData.XValues[xLeftIndex]) / ptsCount;
        final float halfRectWidth = rectWidth / 2f;
        //Log.d("CDD", String.format("updateLines_BAR_Path_Matrix: ptsCount = %d, rectWidth = %f", ptsCount, rectWidth));

        float prevMin = yLeftMin;
        float currMin;

        for (int j = 0; j < linesLines.length; j++) {
            if (BuildConfig.DEBUG && linesRightAlign[j]) throw new AssertionError();

            final Path linePath = linesPaths[j];
            final Path cursorPath = cursorPaths[j];

            linePath.reset();
            cursorPath.reset();

            if (linesVisibilityState[j] == ChartInputDataStats.VISIBILITY_STATE_OFF) {
                continue;
            }

            final float lineK = (float) linesVisibilityState[j] / ChartInputDataStats.VISIBILITY_STATE_ON;

            currMin = yLeftMax;

            for (int i = xLeftIndex; i <= xRightIndex; i++) {
                tmpStackedSum[i] += inputData.LinesValues[j][i] * lineK;

                if (tmpStackedSum[i] < currMin) {
                    currMin = tmpStackedSum[i];
                }
            }

            linePath.moveTo(
                    inputData.XValues[xLeftIndex] - halfRectWidth,
                    tmpStackedSum[xLeftIndex]
            );
            linePath.lineTo(
                    inputData.XValues[xLeftIndex] + halfRectWidth,
                    tmpStackedSum[xLeftIndex]
            );
            for (int i = xLeftIndex + 1; i <= xRightIndex; i++) {
                linePath.lineTo(
                        inputData.XValues[i - 1] + halfRectWidth,
                        tmpStackedSum[i]
                );
                linePath.lineTo(
                        inputData.XValues[i] + halfRectWidth,
                        tmpStackedSum[i]
                );
            }

            // right |
            linePath.lineTo(
                    inputData.XValues[xRightIndex] + halfRectWidth,
                    prevMin
            );
            // _
            linePath.lineTo(
                    inputData.XValues[xLeftIndex] - halfRectWidth,
                    prevMin
            );
            // left |
            linePath.close();

            linePath.transform(matrixLeft);

            // cursor
            if (prevCursorIndex != AbsChartView.NO_CURSOR) {
                cursorPath.addRect(
                        inputData.XValues[prevCursorIndex] - halfRectWidth,
                        tmpStackedSum[prevCursorIndex],
                        inputData.XValues[prevCursorIndex] + halfRectWidth,
                        prevMin,
                        Path.Direction.CW
                );

                cursorPath.transform(matrixLeft);
            }

            prevMin = currMin;
        }
    }

    private void updateLines_AREA_Rect() {
        final int[] linesVisibilityState = inputDataStats.getLinesVisibilityState();
        final boolean[] linesRightAlign = inputDataStats.getLinesRightAlign();

        final int[] stackedSum = inputDataStats.getStackedSum();
        assert stackedSum != null;

        int linePtsCount = xRightIndex - xLeftIndex + 1;

        final float rectWidth = area.width() / linePtsCount;
        //Log.d("CDD", String.format("linePtsCount = %d, rectWidth = %f", linePtsCount, rectWidth));

        int l = -1;
        for (int j = 0; j < linesLines.length; j++) {
            if (BuildConfig.DEBUG && linesRightAlign[j]) throw new AssertionError();

            if (linesVisibilityState[j] == ChartInputDataStats.VISIBILITY_STATE_OFF) {
                continue;
            }

            final RectF[] lineRects = linesRects[j];
            final float lineK = (float) linesVisibilityState[j] / ChartInputDataStats.VISIBILITY_STATE_ON;

            if (l == -1) {
                for (int i = xLeftIndex; i <= xRightIndex; i++) {
                    lineRects[i].left = xToPixel(inputData.XValues[i]);
                    lineRects[i].top = yLeftToPixel(inputData.LinesValues[j][i] * lineK / stackedSum[i] * 100f);
                    lineRects[i].bottom = area.bottom;
                }
            } else {
                final RectF[] prevLineRects = linesRects[l];

                for (int i = xLeftIndex; i <= xRightIndex; i++) {
                    lineRects[i].left = prevLineRects[i].left;
                    lineRects[i].top = prevLineRects[i].top - (inputData.LinesValues[j][i] * lineK / stackedSum[i] * 100f) * scaleYLeft;
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
        final boolean[] linesRightAlign = inputDataStats.getLinesRightAlign();

        final int[] stackedSum = inputDataStats.getStackedSum();
        assert stackedSum != null;

        final @NotNull float[] tmpStackedSum = getTmpStackedSum();
        for (int i = xLeftIndex; i <= xRightIndex; i++) {
            tmpStackedSum[i] = 0;
        }

        final float xToPixelHelper = area.left/* + x * scaleX*/ - xLeftValue * scaleX;
        final float yToPixelHelper = area.bottom/* - y * scaleYLeft*/ + yLeftMin * scaleYLeft;

        for (int j = 0; j < linesLines.length; j++) {
            if (BuildConfig.DEBUG && linesRightAlign[j]) throw new AssertionError();

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
                    yToPixelHelper - (tmpStackedSum[xLeftIndex] / stackedSum[xLeftIndex] * 100f) * scaleYLeft
            );
            for (int i = xLeftIndex + 1; i <= xRightIndex; i++) {
                linePath.lineTo(
                        xToPixelHelper + inputData.XValues[i] * scaleX,
                        yToPixelHelper - (tmpStackedSum[i] / stackedSum[i] * 100f) * scaleYLeft
                );
            }

            // right |
            linePath.lineTo(
                    xToPixelHelper + inputData.XValues[xRightIndex] * scaleX,
                    yToPixelHelper - yLeftMin * scaleYLeft
            );
            // _
            linePath.lineTo(
                    xToPixelHelper + inputData.XValues[xLeftIndex] * scaleX,
                    yToPixelHelper - yLeftMin * scaleYLeft
            );
            // left |
            linePath.close();
        }
    }

    private void updateLines_AREA_Path_Matrix() {
        final int[] linesVisibilityState = inputDataStats.getLinesVisibilityState();
        final boolean[] linesRightAlign = inputDataStats.getLinesRightAlign();

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
            if (BuildConfig.DEBUG && linesRightAlign[j]) throw new AssertionError();

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

            linePath.transform(matrixLeft);

            prevPercMin = currPercMin;
        }
    }

    private @NotNull float[] getTmpStackedSum() {
        if (mTmpStackedSum == null) {
            mTmpStackedSum = new float[inputData.XValues.length];
        }
        return mTmpStackedSum;
    }

    public @NotNull Matrix getMatrixLeft() {
        return matrixLeft;
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

    public float yLeftToPixel(float y) {
        return area.bottom - (y - yLeftMin) * scaleYLeft;
    }

    public float yRightToPixel(float y) {
        return area.bottom - (y - yRightMin) * scaleYRight;
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

            xAxisMarks.add(new AxisMark(x, text, null));

            i += stepValue;
        }
    }

    private final @NotNull YAxisMarksHelper yMarksHelper = new YAxisMarksHelper();

    private void updateYAxisMarks() {
        if (BuildConfig.DEBUG && (yAxisMarks == null)) throw new AssertionError();

        calcYAxisMarksHelper(yLeftMin, yLeftMax, yMarksHelper);

        //Log.d("CDD", String.format("updateYAxisMarks: yLeftMin = %d, yLeftMax = %d, yLeftMarksHelper - %s", yLeftMin, yLeftMax, yMarksHelper));

        yAxisMarks.clear();

        int i = yMarksHelper.startValue;
        for (float y = yMarksHelper.startPixel; y >= 0; y -= yMarksHelper.stepPixel) {
            final String text = String.valueOf(i);

            yAxisMarks.add(new AxisMark(y, text, null));

            i += yMarksHelper.stepValue;
        }
    }

    private void calcYAxisMarksHelper(int yMin, int yMax, @NotNull YAxisMarksHelper marksHelper) {
        if (BuildConfig.DEBUG && (axisLineCount <= 0)) throw new AssertionError();
        if (BuildConfig.DEBUG && !areaSet) throw new AssertionError();

        final float ySwing = Math.abs(yMax - yMin);
        final float scaleY = area.height() / ySwing;

        marksHelper.stepValue = Math.round(ySwing / axisLineCount);

        // beautify step
        int k = 0;
        while (marksHelper.stepValue >= 20) {
            marksHelper.stepValue /= 10;
            k++;
        }
        // значения (10, 19] при делении на 10 "дадут" 1, и линий окажется слишком много - равномерно распределяем их
        // между значениями 10, 15 и 20.
        if (marksHelper.stepValue > 10) {
            if (marksHelper.stepValue >= 18) {
                marksHelper.stepValue = 20;
            } else if (marksHelper.stepValue >= 14) {
                marksHelper.stepValue = 15;
            } else {
                marksHelper.stepValue = 10;
            }
        }
        while (k > 0) {
            marksHelper.stepValue *= 10;
            k--;
        }

        if (marksHelper.stepValue == 0) {
            return;
        }

        marksHelper.stepPixel = marksHelper.stepValue * scaleY;
        if (marksHelper.stepPixel <= 0) {
            return;
        }

        // начало отсчёта
        marksHelper.startValue = yMin / marksHelper.stepValue * marksHelper.stepValue;
        if (marksHelper.startValue < yMin) {
            marksHelper.startValue += marksHelper.stepValue;
        }

        final float yToPixelHelper = area.bottom/* - y * scaleY*/ + yMin * scaleY;
        marksHelper.startPixel = yToPixelHelper - marksHelper.startValue * scaleY;
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

    private static class YAxisMarksHelper {
        int stepValue;
        float stepPixel;

        int startValue;
        float startPixel;

        public String toString() {
            return String.format(Locale.getDefault(), "stepValue = %d, stepPixel = %f, startValue = %d, startPixel = %f",
                    stepValue, stepPixel, startValue, startPixel);
        }
    }

    public interface AxisTextConverter {
        @NotNull String toText(long value);
    }

    public static class AxisMark {
        private float position;
        private @NotNull String text;
        private @Nullable String textRight;

        public AxisMark(float position, @NotNull String text, @Nullable String textRegiht) {
            this.position = position;
            this.text = text;
            this.textRight = textRegiht;
        }

        public float getPosition() {
            return position;
        }

        public @NotNull String getText() {
            return text;
        }

        public @Nullable String getTextRight() {
            return textRight;
        }
    }
}
