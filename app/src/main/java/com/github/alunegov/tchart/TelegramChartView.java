package com.github.alunegov.tchart;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.jetbrains.annotations.NotNull;

public class TelegramChartView extends LinearLayout {
    private TextView titleView;
    private MainChartView mainChartView;
    private PreviewChartView previewChartView;
    private LineNameListView lineNamesView;

    public TelegramChartView(Context context, AttributeSet attrs) {
        super(context, attrs);

        init();

        TypedArray ta = context.getTheme().obtainStyledAttributes(attrs, R.styleable.TelegramChartView, 0, 0);
        try {
            titleView.setText(ta.getText(R.styleable.TelegramChartView_title));
        } finally {
            ta.recycle();
        }
    }

    private void init() {
        setOrientation(VERTICAL);

        final LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        inflater.inflate(R.layout.view_telegram_chart, this, true);

        titleView = (TextView) findViewById(R.id.title);
        mainChartView = (MainChartView) findViewById(R.id.main_chart);
        previewChartView = (PreviewChartView) findViewById(R.id.preview_chart);
        lineNamesView = (LineNameListView) findViewById(R.id.line_names);

        previewChartView.setOnChangeListener(previewChartChangeListener);
        lineNamesView.setOnCheckedChangeListener(lineNamesCheckedChangeListener);
    }

    private final PreviewChartView.OnChangeListener previewChartChangeListener = new PreviewChartView.OnChangeListener() {
        @Override
        public void onZoneChanged(float zoneLeftValue, float zoneRightValue) {
            mainChartView.setXRange(zoneLeftValue, zoneRightValue);
        }
    };

    private final LineNameListView.OnCheckedChangeListener lineNamesCheckedChangeListener = new LineNameListView.OnCheckedChangeListener() {
        @Override
        public void onCheckedChange(int lineIndex, boolean isChecked) {
            mainChartView.updateLineVisibility(lineIndex, isChecked);
            previewChartView.updateLineVisibility(lineIndex, isChecked);
        }
    };

    public void setTitle(@NotNull String title) {
        titleView.setText(title);
    }

    public void setInputData(@NotNull ChartInputData inputData) {
        final LineName[] linesNames = new LineName[inputData.LinesNames.length];
        for (int i = 0; i < inputData.LinesNames.length; i++) {
            linesNames[i] = new LineName(inputData.LinesNames[i], inputData.LinesColors[i]);
        }

        mainChartView.setInputData(inputData);
        previewChartView.setInputData(inputData);
        lineNamesView.setLineNames(linesNames);
    }
}
