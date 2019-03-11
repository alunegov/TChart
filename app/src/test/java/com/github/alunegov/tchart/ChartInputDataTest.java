package com.github.alunegov.tchart;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChartInputDataTest {
    @Test
    public void testCtor() throws Exception {
        ChartInputData data = new ChartInputData(1, 4);

        assertEquals(4, data.XValues.length);
        assertEquals(1, data.LinesValues.length);
        assertEquals(4, data.LinesValues[0].length);
        assertEquals(1, data.LinesNames.length);
        assertEquals(1, data.LinesColors.length);
    }

    @Test
    public void testFindYMinMax() throws Exception {
        ChartInputData data = new ChartInputData(2, 4);

        data.LinesValues[0] = new int[] {3, 5, 7, 5};
        data.LinesValues[1] = new int[] {13, 15, 17, 15};

        int[] minMax = data.findYMinMax(0, 3);
        assertEquals(3, minMax[0]);
        assertEquals(17, minMax[1]);

        minMax = data.findYMinMax(1, 3);
        assertEquals(5, minMax[0]);
        assertEquals(17, minMax[1]);

        minMax = data.findYMinMax(1, 1);
        assertEquals(5, minMax[0]);
        assertEquals(15, minMax[1]);
    }

    @Test
    public void testFindYAbsSwing() throws Exception {
        ChartInputData data = new ChartInputData(2, 4);

        data.LinesValues[0] = new int[] {-3, -5, -7, -5};
        data.LinesValues[1] = new int[] {-13, -15, -17, -15};

        assertEquals(14, data.findYAbsSwing(0, 3));

        assertEquals(12, data.findYAbsSwing(1, 3));

        assertEquals(10, data.findYAbsSwing(1, 1));
    }
}
