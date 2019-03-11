package com.github.alunegov.tchart;

import org.jetbrains.annotations.NotNull;

// Данные графика, загружаемые из файла и передаваемые в TelegramChart
public class ChartInputData {
    // Значения по X (линия x)
    public long[] XValues;
    // Значения сигналов по Y  (линии line)
    public int[][] LinesValues;
    // Имена сигналов
    public String[] LinesNames;
    // Цвета сигналов
    public int[] LinesColors;

    public ChartInputData(int linesCount, int pointsCount) {
        assert linesCount > 0;
        assert pointsCount > 0;

        XValues = new long[pointsCount];
        LinesValues = new int[linesCount][pointsCount];
        LinesNames = new String[linesCount];
        LinesColors = new int[linesCount];
    }

    // Определение минимума и максимума по Y в указанном диапазоне X по всем сигналам
    public @NotNull int[] findYMinMax(int l, int r) {
        assert LinesValues.length > 0;
        assert l <= r;
        assert (0 <= l) && (r < LinesValues[0].length);

        int min = LinesValues[0][l];
        int max = min;

        for (int j = l; j <= r; j++) {
            for (int i = 0; i < LinesValues.length; i++) {
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
    public int findYAbsSwing(int l, int r) {
        int[] minMax = findYMinMax(l, r);
        assert minMax.length == 2;
        return Math.abs(minMax[1] - minMax[0]);
    }
}
