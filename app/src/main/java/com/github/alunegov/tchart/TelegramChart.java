package com.github.alunegov.tchart;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.ListView;

import android.widget.TextView;
import org.jetbrains.annotations.NotNull;

public class TelegramChart extends LinearLayout {
    private TextView titleView;
    private MainChart mainChartView;
    private PreviewChart previewChartView;
    private ListView linesNamesList;

    public TelegramChart(Context context, AttributeSet attrs) {
        super(context, attrs);

        setOrientation(VERTICAL);

        titleView = new TextView(context, attrs);
        addView(titleView);

        mainChartView = new MainChart(context, attrs);
        addView(mainChartView);

        previewChartView = new PreviewChart(context, attrs);
        previewChartView.setChangeListener(new PreviewChart.ChangeListener() {
            @Override
            public void onZoneChanged(float zoneLeftValue, float zoneRightValue) {
                mainChartView.setXRange(zoneLeftValue, zoneRightValue);
            }
        });
        addView(previewChartView);

        //linesNamesList = new ListView(context, attrs);
        //addView(linesNamesList);
    }

    public void setTitle(@NotNull String title) {
        titleView.setText(title);
    }

    public void setInputData(@NotNull ChartInputData inputData) {
        mainChartView.setInputData(inputData);
        previewChartView.setInputData(inputData);
        //linesNamesList.setAdapter();
    }
}
