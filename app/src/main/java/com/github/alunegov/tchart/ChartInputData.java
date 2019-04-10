package com.github.alunegov.tchart;

import java.util.BitSet;

import org.jetbrains.annotations.NotNull;

// Данные графика, загружаемые из файла и передаваемые в TelegramChart
public class ChartInputData {
    public static final int FLAG_PERCENTAGE = 0;
    public static final int FLAG_STACKED = 1;
    public static final int FLAG_Y_SCALED = 2;

    // Значения по X (линия x)
    public long[] XValues;
    // Значения сигналов по Y (линии типа LinesType)
    public int[][] LinesValues;
    // Имена сигналов
    public String[] LinesNames;
    // Цвета сигналов
    public int[] LinesColors;
    // Тип линий
    public LineType linesType;
    //
    public BitSet flags;

    public int[] stackedSum = null;

    public ChartInputData(int linesCount, int pointsCount, LineType linesType) {
        this(linesCount, pointsCount, linesType, new BitSet());
    }

    public ChartInputData(int linesCount, int pointsCount, LineType linesType, @NotNull BitSet flags) {
        if (BuildConfig.DEBUG && (linesCount <= 0)) throw new AssertionError();
        if (BuildConfig.DEBUG && (pointsCount <= 0)) throw new AssertionError();

        XValues = new long[pointsCount];
        LinesValues = new int[linesCount][pointsCount];
        LinesNames = new String[linesCount];
        LinesColors = new int[linesCount];
        this.linesType = linesType;
        this.flags = flags;
    }

    public void updateStackedSum(@NotNull int[] linesVisibilityState) {
        if (stackedSum == null) {
            stackedSum = new int[XValues.length];
        }

        for (int i = 0; i < XValues.length; i++) {
            stackedSum[i] = 0;
        }

        for (int j = 0; j < LinesValues.length; j++) {
            if (linesVisibilityState[j] == ChartDrawData.VISIBILITY_STATE_OFF) {
                continue;
            }

            final float lineK = (float) linesVisibilityState[j] / ChartDrawData.VISIBILITY_STATE_ON;

            for (int i = 0; i < XValues.length; i++) {
                stackedSum[i] += (int) (LinesValues[j][i] * lineK);
            }
        }
    }

    // Определение минимума и максимума по Y в указанном диапазоне X по всем сигналам
    public @NotNull int[] findYMinMax(int l, int r, @NotNull int[] linesVisibilityState) {
        if (BuildConfig.DEBUG && (l > r)) throw new AssertionError();
        if (BuildConfig.DEBUG && (LinesValues.length <= 0)) throw new AssertionError();
        if (BuildConfig.DEBUG && ((l < 0) || (r >= LinesValues[0].length))) throw new AssertionError();

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        switch (linesType) {
            case LINE:
                for (int j = 0; j < LinesValues.length; j++) {
                    if (linesVisibilityState[j] == ChartDrawData.VISIBILITY_STATE_OFF) {
                        continue;
                    }

                    for (int i = l; i <= r; i++) {
                        int val = LinesValues[j][i];
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
                updateStackedSum(linesVisibilityState);

                for (int i = l; i <= r; i++) {
                    int val = stackedSum[i];
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

        return new int[]{min, max};
    }

    // Определение абсолютного размаха (максимум - минимум) по Y в указанном диапазоне X по всем сигналам
    public int findYAbsSwing(int l, int r, @NotNull int[] linesVisibilityState) {
        final int[] minMax = findYMinMax(l, r, linesVisibilityState);
        if (BuildConfig.DEBUG && (minMax.length != 2)) throw new AssertionError();

        return Math.abs(minMax[1] - minMax[0]);
    }

    public enum LineType {
        LINE,
        BAR,
        AREA,
    }
}
