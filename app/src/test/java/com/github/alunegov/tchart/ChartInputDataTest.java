package com.github.alunegov.tchart;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChartInputDataTest {
    @Test
    public void testCtor() {
        ChartInputData data = new ChartInputData(1, 4, ChartInputData.LineType.LINE);

        assertEquals(4, data.XValues.length);
        assertEquals(1, data.LinesValues.length);
        assertEquals(4, data.LinesValues[0].length);
        assertEquals(1, data.LinesNames.length);
        assertEquals(1, data.LinesColors.length);
    }

    @Test
    public void testFindYMinMax() {
        ChartInputData data = new ChartInputData(2, 4, ChartInputData.LineType.LINE);
        ChartInputDataStats dataStats = new ChartInputDataStats(data);

        data.LinesValues[0] = new int[] {3, 5, 7, 5};
        data.LinesValues[1] = new int[] {13, 15, 17, 15};

        int[] minMax = dataStats.findYMinMax(0, 3, new int[] {100, 100});
        assertEquals(3, minMax[0]);
        assertEquals(17, minMax[1]);

        minMax = dataStats.findYMinMax(1, 3, new int[] {100, 100});
        assertEquals(5, minMax[0]);
        assertEquals(17, minMax[1]);

        minMax = dataStats.findYMinMax(1, 1, new int[] {100, 100});
        assertEquals(5, minMax[0]);
        assertEquals(15, minMax[1]);
    }

    @Test
    public void testFindYAbsSwing() {
        ChartInputData data = new ChartInputData(2, 4, ChartInputData.LineType.LINE);
        ChartInputDataStats dataStats = new ChartInputDataStats(data);

        data.LinesValues[0] = new int[] {-3, -5, -7, -5};
        data.LinesValues[1] = new int[] {-13, -15, -17, -15};

        assertEquals(14, dataStats.findYAbsSwing(0, 3, new int[] {100, 100}));

        assertEquals(12, dataStats.findYAbsSwing(1, 3, new int[] {100, 100}));

        assertEquals(10, dataStats.findYAbsSwing(1, 1, new int[] {100, 100}));
    }
}
