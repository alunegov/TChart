package com.github.alunegov.tchart;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.jetbrains.annotations.NotNull;

public abstract class AbsChartView extends View {
    // Cache the touch slop from the context that created the view.
    protected int mTouchSlop;

    protected ChartInputData inputData;

    protected ChartDrawData drawData;

    protected float lineWidth;

    // настройки отрисовки линий
    protected Paint[] linesPaints;

    protected boolean horizontalMovement = false;

    private int[] newLinesVisibilityState;

    public AbsChartView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setInputData(@NotNull ChartInputData inputData) {
        this.inputData = inputData;

        drawData = new ChartDrawData(inputData);
        drawData.setXRange(inputData.XValues[0], inputData.XValues[inputData.XValues.length - 1], true);

        linesPaints = ChartUtils.makeLinesPaints(inputData.LinesColors, lineWidth);

        newLinesVisibilityState = new int[inputData.LinesValues.length];
    }

    public void getXRange(@NotNull float[] range) {
        drawData.getXRange(range);
    }

    public void getXRange(@NotNull long[] range) {
        drawData.getXRange(range);
    }

    public void updateLineVisibility(int lineIndex, int state, boolean doUpdate) {
//        synchronized (lock) {
            if (drawData == null) {
                return;
            }

            drawData.updateLineVisibility(lineIndex, state, true);

            linesPaints[lineIndex].setAlpha(state);

            if (doUpdate) {
                invalidate();
                //postInvalidateDelayed(12);
            }
//        }
    }

    public void getYRange(int[] range) {
        drawData.getYRange(range);
    }

    private Executor executor = Executors.newSingleThreadExecutor();
    protected final @NotNull Object lock = new Object();

    public void setYRange(int yMin, int yMax, boolean doUpdate) {
        if (drawData == null) {
            return;
        }

//        executor.execute(new QQ(yMin, yMax, doUpdate));

        drawData.setYRange(yMin, yMax);

        if (doUpdate) {
            invalidate();
            //postInvalidateDelayed(16);
        }
    }

    private class QQ implements Runnable {
        int yMin;
        int yMax;
        boolean doUpdate;

        public QQ(int yMin, int yMax, boolean doUpdate) {
            this.yMin = yMin;
            this.yMax = yMax;
            this.doUpdate = doUpdate;
        }

        @Override
        public void run() {
            synchronized (lock) {
                drawData.setYRange(yMin, yMax);
            }

            if (doUpdate) {
                postInvalidateDelayed(0);
                try {
                    Thread.sleep(16);
                } catch (InterruptedException e) {

                }
            }
        }
    }

    private void calcYRangeAt(float xLeftValue, float xRightValue, @NotNull int[] range) {
        drawData.calcYRangeAt(xLeftValue, xRightValue, drawData.getLinesVisibilityState(), range);
    }

    private void calcYRangeAt(float xLeftValue, float xRightValue, int lineIndex, int state, @NotNull int[] range) {
        final int[] linesVisibilityState = drawData.getLinesVisibilityState();
        System.arraycopy(linesVisibilityState, 0, newLinesVisibilityState, 0, linesVisibilityState.length);

        newLinesVisibilityState[lineIndex] = state;

        drawData.calcYRangeAt(xLeftValue, xRightValue, newLinesVisibilityState, range);
    }

    // при изменении отображаемой зоны на графике
    public void calcAnimationRanges(float zoneLeftValue, float zoneRightValue, @NotNull int[] yStartRange, @NotNull int[] yStopRange) {
        drawData.getYRange(yStartRange);

        calcYRangeAt(zoneLeftValue, zoneRightValue, yStopRange);
    }

    private final @NotNull float[] xRange = new float[2];

    // при включении/выключении графика
    public void calcAnimationRanges(int lineIndex, int state, @NotNull int[] yStartRange, @NotNull int[] yStopRange) {
        drawData.getYRange(yStartRange);

        drawData.getXRange(xRange);
        calcYRangeAt(xRange[0], xRange[1], lineIndex, state, yStopRange);
    }

    protected void drawLines(@NotNull Canvas canvas) {
        assert drawData != null;
        assert linesPaints != null;

        final int[] linesVisibilityState = drawData.getLinesVisibilityState();

/*        //
        final Path[] paths = drawData.getLinesPaths();
        assert paths.length == linesPaints.length;
        for (int i = 0; i < paths.length; i++) {
            if (linesVisibilityState[i] == 0) {
                continue;
            }

            canvas.drawPath(paths[i], linesPaints[i]);
        }*/

        //
        final float[][] lines = drawData.getLinesLines();

        drawData.getXRange(xIndexRange);
        final int pointsCount = (xIndexRange[1] - xIndexRange[0] + 1 - 1) * 4;

        if (BuildConfig.DEBUG && (lines.length != linesPaints.length))
            throw new AssertionError();
        for (int i = 0; i < lines.length; i++) {
            if (linesVisibilityState[i] == 0) {
                continue;
            }

//            if (BuildConfig.DEBUG && (lines[i].length != pointsCount))
//                throw new AssertionError();
//            canvas.drawLines(lines[i], linesPaints[i]);

            canvas.drawLines(lines[i], 0, pointsCount, linesPaints[i]);
        }
    }

    private final @NotNull int[] xIndexRange = new int[2];
    private final @NotNull float[] a1 = new float[500 * 4];
    private final @NotNull float[] a2 = new float[500 * 4];

    protected void drawLines2(@NotNull Canvas canvas) {
        final Matrix matrix = drawData.getMatrix();

        drawData.getXRange(xIndexRange);
        final int linePtsCount = xIndexRange[1] - xIndexRange[0] + 1;

        final int[] linesVisibilityState = drawData.getLinesVisibilityState();

        for (int j = 0; j < inputData.LinesValues.length; j++) {
            if (linesVisibilityState[j] == 0) {
                continue;
            }

            int k = 0;
            a1[k] = inputData.XValues[xIndexRange[0]];
            a1[k + 1] = inputData.LinesValues[j][xIndexRange[0]];
            k += 2;
            for (int i = xIndexRange[0] + 1; i < xIndexRange[1]; i++) {
                a1[k] = inputData.XValues[i];
                a1[k + 1] = inputData.LinesValues[j][i];
                a1[k + 2] = a1[k];
                a1[k + 3] = a1[k + 1];
                k += 4;
            }
            a1[k] = inputData.XValues[xIndexRange[1]];
            a1[k + 1] = inputData.LinesValues[j][xIndexRange[1]];

            matrix.mapPoints(a2, 0, a1, 0, (linePtsCount - 1) << 1);

            canvas.drawLines(a2, 0, (linePtsCount - 1) << 2,  linesPaints[j]);
        }
    }
}
