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

    public enum LineType {
        LINE,
        BAR,
        AREA,
    }
}
