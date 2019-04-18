package com.github.alunegov.tchart;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ChartInputDataMapperTest {
    private final static int FAKE_COLOR = 0;

    @Test
    public void testLoad() throws Exception {
        ChartInputDataMapper.ColorParser colorParserMock = Mockito.mock(ChartInputDataMapper.ColorParser.class);
        when(colorParserMock.parseColor(anyString())).thenReturn(FAKE_COLOR);

        File jsonFile = new File(".\\src\\main\\assets\\", "chart_data.json");
        String json = ChartUtils.readFileToString(jsonFile, "UTF8");

        List<ChartInputData> l = ChartInputDataMapper.load(json, colorParserMock);

        assertEquals(5, l.size());

        ChartInputData c = l.get(0);
        assertEquals(112, c.XValues.length);
        assertEquals(2, c.LinesValues.length);
        assertEquals(112, c.LinesValues[0].length);
        assertEquals(112, c.LinesValues[1].length);
        assertEquals(2, c.LinesNames.length);
        assertEquals("#0", c.LinesNames[0]);
        assertEquals("#1", c.LinesNames[1]);
        assertEquals(2, c.LinesColors.length);
        assertEquals(FAKE_COLOR, c.LinesColors[0]);
        assertEquals(FAKE_COLOR, c.LinesColors[1]);

/*        StringBuilder sb = new StringBuilder();
        //sb.append("var points = [");
        c = l.get(4);
        for (int i = 0; i < c.XValues.length; i++) {
            //sb.append(String.format("{x:%d.0,y:%d.0},", c.XValues[i], c.LinesValues[0][i]));
            //sb.append(String.format("%f;%d\r\n", c.XValues[i] / 100000000f, c.LinesValues[0][i]));
            sb.append(String.format("%d %d\r\n", c.XValues[i], c.LinesValues[0][i]));
        }
        //sb.append("];");

        PrintWriter out = new PrintWriter("1.txt");
        out.println(sb.toString());
        out.close();*/
  }
}
