package com.github.alunegov.tchart;

import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.LinearLayout;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.NotNull;

public class MainActivity extends AppCompatActivity {
    private static final String CHART_DATA_CHARSET = "UTF8";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setTitle("Statistics");

        LinearLayout root = (LinearLayout)findViewById(R.id.root);
        assert root != null;

        try {
            String json = readStreamToString(getResources().openRawResource(R.raw.chart_data), CHART_DATA_CHARSET);

            List<ChartInputData> charts = ChartInputDataMapper.load(json, new ChartInputDataMapper.ColorParser() {
                @Override
                public int parseColor(String color) {
                    return Color.parseColor(color);
                }
            });

            for (int i = 0; i < charts.size(); i++) {
                TelegramChart tc = new TelegramChart(this, null);
                tc.setTitle(String.format(Locale.getDefault(), "Followers #%d", i + 1));
                tc.setInputData(charts.get(i));

                root.addView(tc);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private @NotNull String readStreamToString(@NotNull InputStream stream, @NotNull String charsetName) throws IOException {
        int availBytes = stream.available();
        byte[] buffer = new byte[availBytes];
        int bytesRead = stream.read(buffer);
        if (bytesRead != availBytes) {
            throw new IOException("q");
        }
        return new String(buffer, charsetName);
    }
}
