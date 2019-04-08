package com.github.alunegov.tchart;

import java.util.BitSet;
import java.util.Set;

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

    // Определение минимума и максимума по Y в указанном диапазоне X по всем сигналам
    public @NotNull int[] findYMinMax(int l, int r, @NotNull Set<Integer> invisibleLinesIndexes) {
        if (BuildConfig.DEBUG && (LinesValues.length <= 0)) throw new AssertionError();
        if (BuildConfig.DEBUG && (l > r)) throw new AssertionError();
        if (BuildConfig.DEBUG && ((0 > l) || (r >= LinesValues[0].length))) throw new AssertionError();

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        for (int i = 0; i < LinesValues.length; i++) {
            if (invisibleLinesIndexes.contains(i)) {
                continue;
            }
            for (int j = l; j <= r; j++) {
                int val = LinesValues[i][j];
                if (val < min) {
                    min = val;
                }
                if (val > max) {
                    max = val;
                }
            }
        }

        return new int[] {min, max};
    }

    // Определение абсолютного размаха (максимум - минимум) по Y в указанном диапазоне X по всем сигналам
    public int findYAbsSwing(int l, int r, @NotNull Set<Integer> invisibleLinesIndexes) {
        final int[] minMax = findYMinMax(l, r, invisibleLinesIndexes);
        if (BuildConfig.DEBUG && (minMax.length != 2)) throw new AssertionError();

        return Math.abs(minMax[1] - minMax[0]);
    }

    public enum LineType {
        LINE,
        BAR,
        AREA,
    }
}
