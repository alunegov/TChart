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
    protected static final int NO_CURSOR = -1;

    // Grid Lines
    // Lighten Mask - FFFFFF, 50%  Darken Mask - 242F3E, 50%
    private static final int BAR_LINES_MASK_COLOR = Color.parseColor("#7FFFFFFF");

    // Cache the touch slop from the context that created the view.
    protected int mTouchSlop;

    protected ChartInputData inputData;
    protected ChartInputDataStats inputDataStats;

    protected ChartDrawData drawData;

    protected float lineWidth;

    // индекс точки с курсором
    protected int cursorIndex = NO_CURSOR;

    // настройки отрисовки линий
    protected Paint[] linesPaints;
    // настройки отрисовки линий, которые не под курсором
    private Paint[] linesFadedPaints;

    protected boolean horizontalMovement = false;

    private int[] tmpLinesVisibilityState;

    public AbsChartView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setInputData(@NotNull ChartInputData inputData, @NotNull ChartInputDataStats inputDataStats) {
        this.inputData = inputData;
        this.inputDataStats = inputDataStats;

        drawData = new ChartDrawData(inputData, inputDataStats);
        drawData.setXRange(inputData.XValues[0], inputData.XValues[inputData.XValues.length - 1], true);

        linesPaints = ChartUtils.makeLinesPaints(inputData.LinesColors, lineWidth, inputData.linesType == ChartInputData.LineType.LINE);
/*        if (inputData.linesType == ChartInputData.LineType.AREA) {
            for (int i = 0; i < linesPaints.length; i++) {
                linesPaints[i].setAntiAlias(true);
            }
        }*/

        if (inputData.linesType == ChartInputData.LineType.BAR) {
            final int barLinesMaskColor = ChartUtils.getThemedColor(getContext(), R.attr.tchart_bars_mask_color, BAR_LINES_MASK_COLOR);

            // https://www.wikiwand.com/en/Alpha_compositing#Alpha_blending
            // transparent SRC over opaque DST: OUTrgb = SRCrgb * SRCa + DSTrgb * (1 - SRCa)
            final float alpha = Color.alpha(barLinesMaskColor) / 255f;

            final int[] linesFadedColors = new int[inputData.LinesColors.length];
            for (int i = 0; i < inputData.LinesColors.length; i++) {
                final int r = Math.round(Color.red(barLinesMaskColor) * alpha + Color.red(inputData.LinesColors[i]) * (1 - alpha));
                final int g = Math.round(Color.green(barLinesMaskColor) * alpha + Color.green(inputData.LinesColors[i]) * (1 - alpha));
                final int b = Math.round(Color.blue(barLinesMaskColor) * alpha + Color.blue(inputData.LinesColors[i]) * (1 - alpha));

                linesFadedColors[i] = Color.rgb(r, g, b);
            }

            linesFadedPaints = ChartUtils.makeLinesPaints(linesFadedColors, lineWidth, false);
        }

        tmpLinesVisibilityState = new int[inputData.LinesValues.length];
    }

    public void getXRange(@NotNull float[] range) {
        drawData.getXRange(range);
    }

    public void getXRange(@NotNull long[] range) {
        drawData.getXRange(range);
    }

    public void updateLineVisibility(int lineIndex, boolean exceptLine, int state, boolean doUpdate, boolean doInvalidate) {
//        synchronized (lock) {
            if (drawData == null) {
                return;
            }

            drawData.updateLineVisibility(doUpdate);

            if (inputData.linesType == ChartInputData.LineType.LINE) {
                if (exceptLine) {
                    final int[] linesVisibilityState = inputDataStats.getLinesVisibilityState();
                    final int otherLinesState = ChartInputDataStats.VISIBILITY_STATE_ON - state;

                    for (int i = 0; i < linesPaints.length; i++) {
                        if (linesVisibilityState[i] != ChartInputDataStats.VISIBILITY_STATE_OFF) {
                            linesPaints[i].setAlpha(otherLinesState);
                        }
                    }
                }
                linesPaints[lineIndex].setAlpha(state);
            }

            if (doInvalidate) {
                invalidate();
                //postInvalidateDelayed(12);
            }
//        }
    }

    public void getYRange(int[] range) {
        drawData.getYRange(range);
    }

//    private Executor executor = Executors.newSingleThreadExecutor();
//    protected final @NotNull Object lock = new Object();

    public void setYRange(int yLeftMin, int yLeftMax, int yRightMin, int yRightMax, boolean doUpdateAndInvalidate) {
        if (drawData == null) {
            return;
        }

//        executor.execute(new QQ(yLeftMin, yLeftMax, doUpdateAndInvalidate));

        drawData.setYRange(yLeftMin, yLeftMax, yRightMin, yRightMax);

        if (doUpdateAndInvalidate) {
            invalidate();
            //postInvalidateDelayed(16);
        }
    }

/*    private class QQ implements Runnable {
        int yMin;
        int yMax;
        boolean doUpdateAndInvalidate;

        public QQ(int yMin, int yMax, boolean doUpdateAndInvalidate) {
            this.yMin = yMin;
            this.yMax = yMax;
            this.doUpdateAndInvalidate = doUpdateAndInvalidate;
        }

        @Override
        public void run() {
            synchronized (lock) {
                drawData.setYRange(yMin, yMax, 0, 0);
            }

            if (doUpdateAndInvalidate) {
                postInvalidateDelayed(0);
                try {
                    Thread.sleep(16);
                } catch (InterruptedException e) {

                }
            }
        }
    }*/

    private void calcYRangeAt(float xLeftValue, float xRightValue, @NotNull int[] range) {
        drawData.calcYRangeAt(xLeftValue, xRightValue, inputDataStats.getLinesVisibilityState(), range);
    }

    private void calcYRangeAt(float xLeftValue, float xRightValue, int lineIndex, boolean exceptLine, int state, @NotNull int[] range) {
        final int[] linesVisibilityState = inputDataStats.getLinesVisibilityState();

        if (exceptLine) {
            final int otherLinesState = ChartInputDataStats.VISIBILITY_STATE_ON - state;

            for (int i = 0; i < tmpLinesVisibilityState.length; i++) {
                if (linesVisibilityState[i] != ChartInputDataStats.VISIBILITY_STATE_OFF) {
                    tmpLinesVisibilityState[i] = otherLinesState;
                } else {
                    tmpLinesVisibilityState[i] = ChartInputDataStats.VISIBILITY_STATE_OFF;
                }
            }
        } else {
            System.arraycopy(linesVisibilityState, 0, tmpLinesVisibilityState, 0, linesVisibilityState.length);
        }
        tmpLinesVisibilityState[lineIndex] = state;

        drawData.calcYRangeAt(xLeftValue, xRightValue, tmpLinesVisibilityState, range);
    }

    // при изменении отображаемой зоны на графике
    public void calcAnimationRanges(float zoneLeftValue, float zoneRightValue, @NotNull int[] yStartRange, @NotNull int[] yStopRange) {
        drawData.getYRange(yStartRange);

        calcYRangeAt(zoneLeftValue, zoneRightValue, yStopRange);
    }

    private final @NotNull float[] tmpXRange = new float[2];

    // при включении/выключении графика
    public void calcAnimationRanges(int lineIndex, boolean exceptLine, int state, @NotNull int[] yStartRange, @NotNull int[] yStopRange) {
        drawData.getYRange(yStartRange);

        drawData.getXRange(tmpXRange);
        calcYRangeAt(tmpXRange[0], tmpXRange[1], lineIndex, exceptLine, state, yStopRange);
    }

    private final @NotNull int[] xIndexRange = new int[2];

    protected void drawLines(@NotNull Canvas canvas) {
        if (BuildConfig.DEBUG && (drawData == null)) throw new AssertionError();
        if (BuildConfig.DEBUG && (linesPaints == null)) throw new AssertionError();

        final int[] linesVisibilityState = inputDataStats.getLinesVisibilityState();

        drawData.getXRange(xIndexRange);

        final Path[] paths, cursorPaths;
        int pointsCount;
        boolean doCursor;

        switch (drawData.getDrawLinesMode()) {
            case PATH:
            case PATH_REVERSE:
                paths = drawData.getLinesPaths();
                if (BuildConfig.DEBUG && (paths.length != linesPaints.length)) throw new AssertionError();
                cursorPaths = drawData.getCursorPaths();

                doCursor = inputData.linesType == ChartInputData.LineType.BAR && cursorIndex != NO_CURSOR;

                final Paint[] paints;
                if (doCursor) {
                    paints = linesFadedPaints;
                } else {
                    paints = linesPaints;
                }

                if (drawData.getDrawLinesMode() == ChartDrawData.DrawLinesMode.PATH) {
                    for (int i = 0; i < paths.length; i++) {
                        if (linesVisibilityState[i] == ChartInputDataStats.VISIBILITY_STATE_OFF) {
                            continue;
                        }

                        canvas.drawPath(paths[i], paints[i]);
                    }

                    if (doCursor) {
                        for (int i = 0; i < paths.length; i++) {
                            if (linesVisibilityState[i] == ChartInputDataStats.VISIBILITY_STATE_OFF) {
                                continue;
                            }

                            canvas.drawPath(cursorPaths[i], linesPaints[i]);
                        }
                    }
                } else {
                    for (int i = paths.length - 1; i >= 0; i--) {
                        if (linesVisibilityState[i] == ChartInputDataStats.VISIBILITY_STATE_OFF) {
                            continue;
                        }

                        canvas.drawPath(paths[i], paints[i]);
                    }

                    if (doCursor) {
                        for (int i = paths.length - 1; i >= 0; i--) {
                            if (linesVisibilityState[i] == ChartInputDataStats.VISIBILITY_STATE_OFF) {
                                continue;
                            }

                            canvas.drawPath(cursorPaths[i], linesPaints[i]);
                        }
                    }
                }

                break;

            case LINES:
                pointsCount = xIndexRange[1] - xIndexRange[0] + 1;

                pointsCount = (pointsCount - 1) << 2;

                final float[][] lines = drawData.getLinesLines();
                if (BuildConfig.DEBUG && (lines.length != linesPaints.length)) throw new AssertionError();

                for (int i = 0; i < lines.length; i++) {
                    if (linesVisibilityState[i] == ChartInputDataStats.VISIBILITY_STATE_OFF) {
                        continue;
                    }

                    canvas.drawLines(lines[i], 0, pointsCount, linesPaints[i]);
                }

                break;

            case RECT:
                final RectF[][] rects = drawData.getLinesRects();
                if (BuildConfig.DEBUG && (rects.length != linesPaints.length)) throw new AssertionError();

                for (int j = 0; j < rects.length; j++) {
                    if (linesVisibilityState[j] == ChartInputDataStats.VISIBILITY_STATE_OFF) {
                        continue;
                    }

                    for (int i = xIndexRange[0]; i <= xIndexRange[1]; i++) {
                        doCursor = cursorIndex == NO_CURSOR || (inputData.linesType == ChartInputData.LineType.BAR && cursorIndex == i);

                        if (doCursor) {
                            canvas.drawRect(rects[j][i], linesPaints[j]);
                        } else {
                            canvas.drawRect(rects[j][i], linesFadedPaints[j]);
                        }
                    }
                }

                break;
        }
    }

/*    private final @NotNull float[] a1 = new float[500 * 4];
    private final @NotNull float[] a2 = new float[500 * 4];

    protected void drawLines2(@NotNull Canvas canvas) {
        final Matrix matrix = drawData.getMatrixLeft();

        drawData.getXRange(xIndexRange);
        final int linePtsCount = xIndexRange[1] - xIndexRange[0] + 1;

        final int[] linesVisibilityState = inputDataStats.getLinesVisibilityState();

        for (int j = 0; j < inputData.LinesValues.length; j++) {
            if (linesVisibilityState[j] == ChartInputDataStats.VISIBILITY_STATE_OFF) {
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
    }*/
}
