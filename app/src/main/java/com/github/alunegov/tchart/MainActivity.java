package com.github.alunegov.tchart;

import android.content.Context;
import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.*;
import android.widget.LinearLayout;

import java.io.*;
import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.NotNull;

public class MainActivity extends AppCompatActivity {
    private static final String IS_LIGHT_PREF = "isLight";

    private static final String CHART_DATA_CHARSET = "UTF8";

    private boolean isLight = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        isLight = getPreferences(MODE_PRIVATE)
                .getBoolean(IS_LIGHT_PREF, isLight);
        setTheme(isLight ? R.style.AppTheme_Light : R.style.AppTheme_Dark);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        setTitle(getString(R.string.activity_title));

        final LinearLayout root = (LinearLayout) findViewById(R.id.root);
        if (root == null) throw new AssertionError();

        final LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (inflater == null) throw new AssertionError();

        final ChartInputDataMapper.ResourceLoader resourceLoader = new ChartInputDataMapper.ResourceLoader() {
            @Override
            public String[] listResources(@NotNull String path) throws IOException {
                return getAssets().list(path);
            }

            @Override
            public InputStream openResource(@NotNull String fileName) throws IOException {
                return getAssets().open(fileName);
            }
        };

        final ChartInputDataMapper.ColorParser colorParser = new ChartInputDataMapper.ColorParser() {
            @Override
            public int parseColor(String color) {
                return Color.parseColor(color);
            }
        };

        try {
/*            final String json = ChartUtils.readStreamToString(getAssets().open("chart_data.json"), CHART_DATA_CHARSET);
            final List<ChartInputData> charts = ChartInputDataMapper.load(json, colorParser);*/

            final List<ChartInputData> charts = ChartInputDataMapper.load(resourceLoader, colorParser);

            for (int i = 0; i < charts.size(); i++) {
                final View view = inflater.inflate(R.layout.telegram_chart_list_item, root, false);

                final TelegramChartView tc = (TelegramChartView) view.findViewById(R.id.telegram_chart);

                tc.setTitle(String.format(Locale.getDefault(), getString(R.string.chart_title_fmt), i + 1));
                tc.setInputData(charts.get(i));

                root.addView(tc);
            }
        } catch (Exception e) {
            Log.e("MA", e.toString(), e);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.mode_menu) {
            getPreferences(MODE_PRIVATE)
                    .edit()
                    .putBoolean(IS_LIGHT_PREF, !isLight)
                    .apply();
            recreate();
            return true;
        }
        return false;
    }
}
