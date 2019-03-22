package com.github.alunegov.tchart;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

public abstract class AbsChartView extends View {
    protected ChartInputData inputData;

    protected ChartDrawData drawData;

    protected float lineWidth;

    // настройки отрисовки линий
    protected Paint[] linesPaints;

    public AbsChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setInputData(@NotNull ChartInputData inputData) {
        this.inputData = inputData;

        drawData = new ChartDrawData(inputData);
        drawData.setXRange(inputData.XValues[0], inputData.XValues[inputData.XValues.length - 1], true);

        linesPaints = ChartUtils.makeLinesPaints(inputData.LinesColors, lineWidth);
    }

    public void getXRange(@NotNull float[] range) {
        drawData.getXRange(range);
    }

    public void updateLineVisibility(int lineIndex, boolean visible, boolean doUpdate) {
        if (drawData == null) {
            return;
        }

        drawData.updateLineVisibility(lineIndex, visible, doUpdate);

        if (doUpdate) {
            invalidate();
        }
    }

    public void getYRange(int[] range) {
        drawData.getYRange(range);
    }

    public void setYRange(int yMin, int yMax) {
        if (drawData == null) {
            return;
        }

        drawData.setYRange(yMin, yMax);

        invalidate();
    }

    public void calcYRangeAt(float xLeftValue, float xRightValue, @NotNull int[] range) {
        drawData.calcYRangeAt(xLeftValue, xRightValue, drawData.getInvisibleLinesIndexes(), range);
    }

    public void calcYRangeAt(float xLeftValue, float xRightValue, int lineIndex, boolean visible, @NotNull int[] range) {
        final @NotNull Set<Integer> newInvisibleLinesIndexes = new HashSet<>(drawData.getInvisibleLinesIndexes());

        ChartDrawData.updateLineVisibility(newInvisibleLinesIndexes, lineIndex, visible);

        drawData.calcYRangeAt(xLeftValue, xRightValue, newInvisibleLinesIndexes, range);
    }

    // при изменении отображаемой зоны на графике
    public void calcAnimationRanges(float zoneLeftValue, float zoneRightValue, @NotNull int[] yStartRange, @NotNull int[] yStopRange) {
        drawData.getYRange(yStartRange);

        calcYRangeAt(zoneLeftValue, zoneRightValue, yStopRange);
    }

    // при включении/выключении графика
    public void calcAnimationRanges(int lineIndex, boolean isChecked, @NotNull int[] yStartRange, @NotNull int[] yStopRange) {
        drawData.getYRange(yStartRange);

        final @NotNull float[] xRange = new float[2];
        drawData.getXRange(xRange);
        calcYRangeAt(xRange[0], xRange[1], lineIndex, isChecked, yStopRange);
    }

    protected void drawLines(@NotNull Canvas canvas) {
        assert drawData != null;
        assert linesPaints != null;

        final Path[] paths = drawData.getLinesPaths();
        final Set<Integer> invisibleLinesIndexes = drawData.getInvisibleLinesIndexes();

        assert paths.length == linesPaints.length;
        for (int i = 0; i < paths.length; i++) {
            if (!invisibleLinesIndexes.contains(i)) {
                canvas.drawPath(paths[i], linesPaints[i]);
            }
        }
    }
}
