package com.github.alunegov.tchart;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChartInputDataStats {
    public static final int VISIBILITY_STATE_ON = 255;
    public static final int VISIBILITY_STATE_OFF = 0;

    private ChartInputData inputData;
    // состояние видимости линии (0 - не видима, 255 - видима)
    private int[] linesVisibilityState;
    private int[] stackedSum = null;
    private int[] tmpStackedSum = null;

    public ChartInputDataStats(ChartInputData inputData) {
        this.inputData = inputData;

        linesVisibilityState = new int[inputData.LinesValues.length];
        for (int i = 0; i < linesVisibilityState.length; i++) {
            linesVisibilityState[i] = VISIBILITY_STATE_ON;
        }

        if (inputData.linesType == ChartInputData.LineType.BAR || inputData.linesType == ChartInputData.LineType.AREA) {
            stackedSum = new int[inputData.XValues.length];
            internalUpdateStackedSum(stackedSum, linesVisibilityState);
        }
    }

    public @NotNull int[] getLinesVisibilityState() {
        return linesVisibilityState;
    }

    public void updateLineVisibility(int lineIndex, boolean exceptLine, int state) {
        if ((lineIndex < 0) || (inputData.LinesValues.length <= lineIndex)) {
            return;
        }

        if (exceptLine) {
            final int otherLinesState = VISIBILITY_STATE_ON - state;

            for (int i = 0; i < linesVisibilityState.length; i++) {
                if (linesVisibilityState[i] != VISIBILITY_STATE_OFF) {
                    linesVisibilityState[i] = otherLinesState;
                }
            }
        }
        linesVisibilityState[lineIndex] = state;

        if (inputData.linesType == ChartInputData.LineType.BAR || inputData.linesType == ChartInputData.LineType.AREA) {
            internalUpdateStackedSum(stackedSum, linesVisibilityState);
        }
    }

    // количество видимых линий (с не нулевым состоянием)
    public int getVisibleLinesCount() {
        int visibleLinesCount = 0;
        for (int j = 0; j < inputData.LinesValues.length; j++) {
            if (linesVisibilityState[j] != VISIBILITY_STATE_OFF) {
                visibleLinesCount++;
            }
        }

        return visibleLinesCount;
    }

    public int getLineVisibilityState(int lineIndex) {
        return linesVisibilityState[lineIndex];
    }

    public @Nullable int[] getStackedSum() {
        return stackedSum;
    }

    private void internalUpdateStackedSum(@NotNull int[] sum, @NotNull int[] linesVisibilityState) {
        for (int i = 0; i < inputData.XValues.length; i++) {
            sum[i] = 0;
        }

        for (int j = 0; j < inputData.LinesValues.length; j++) {
            if (linesVisibilityState[j] == VISIBILITY_STATE_OFF) {
                continue;
            }

            final float lineK = (float) linesVisibilityState[j] / VISIBILITY_STATE_ON;

            for (int i = 0; i < inputData.XValues.length; i++) {
                sum[i] += (int) (inputData.LinesValues[j][i] * lineK);
            }
        }
    }

    // Определение минимума и максимума по Y в указанном диапазоне X по всем сигналам
    public @NotNull int[] findYMinMax(int l, int r, @NotNull int[] linesVisibilityState) {
        if (BuildConfig.DEBUG && (l > r)) throw new AssertionError();
        if (BuildConfig.DEBUG && (inputData.LinesValues.length <= 0)) throw new AssertionError();
        if (BuildConfig.DEBUG && ((l < 0) || (r >= inputData.LinesValues[0].length))) throw new AssertionError();

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        switch (inputData.linesType) {
            case LINE:
                for (int j = 0; j < inputData.LinesValues.length; j++) {
                    if (linesVisibilityState[j] == VISIBILITY_STATE_OFF) {
                        continue;
                    }

                    for (int i = l; i <= r; i++) {
                        int val = inputData.LinesValues[j][i];
                        if (val < min) {
                            min = val;
                        }
                        if (val > max) {
                            max = val;
                        }
                    }
                }

                break;

            case BAR:
                if (tmpStackedSum == null) {
                    tmpStackedSum = new int[inputData.XValues.length];
                }

                internalUpdateStackedSum(tmpStackedSum, linesVisibilityState);

                for (int i = l; i <= r; i++) {
                    int val = tmpStackedSum[i];
                    if (val < min) {
                        min = val;
                    }
                    if (val > max) {
                        max = val;
                    }
                }

                break;

            case AREA:
                min = 0;
                max = 100;

                break;
        }

        return new int[] {min, max};
    }

    // Определение абсолютного размаха (максимум - минимум) по Y в указанном диапазоне X по всем сигналам
    public int findYAbsSwing(int l, int r, @NotNull int[] linesVisibilityState) {
        final int[] minMax = findYMinMax(l, r, linesVisibilityState);
        if (BuildConfig.DEBUG && (minMax.length != 2)) throw new AssertionError();

        return Math.abs(minMax[1] - minMax[0]);
    }
}
