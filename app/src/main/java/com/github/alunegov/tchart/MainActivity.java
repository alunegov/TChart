package com.github.alunegov.tchart;

import android.content.Context;
import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String CHART_DATA_CHARSET = "UTF8";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setTitle(getString(R.string.activity_title));

        final LinearLayout root = (LinearLayout) findViewById(R.id.root);
        assert root != null;

        final LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        try {
            final String json = ChartUtils.readStreamToString(getResources().openRawResource(R.raw.chart_data), CHART_DATA_CHARSET);

            final List<ChartInputData> charts = ChartInputDataMapper.load(json, new ChartInputDataMapper.ColorParser() {
                @Override
                public int parseColor(String color) {
                    return Color.parseColor(color);
                }
            });

            for (int i = 0; i < charts.size(); i++) {
                final View view = inflater.inflate(R.layout.telegram_chart_list_item, root, false);

                final TelegramChartView tc = (TelegramChartView) view.findViewById(R.id.telegram_chart);

                tc.setTitle(String.format(Locale.getDefault(), getString(R.string.chart_title_fmt), i + 1));
                tc.setInputData(charts.get(i));

                root.addView(tc);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
