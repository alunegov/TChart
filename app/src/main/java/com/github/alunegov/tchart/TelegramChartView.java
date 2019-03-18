package com.github.alunegov.tchart;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.jetbrains.annotations.NotNull;

public class TelegramChartView extends LinearLayout {
    private static final int DEF_TEXT_SIZE_SP = 21;
    private static final int DEF_AXIS_TEXT_SIZE_SP = 17;

    private TextView titleView;
    private MainChartView mainChartView;
    private PreviewChartView previewChartView;
    private LineNameListView lineNamesView;

    public TelegramChartView(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        setOrientation(VERTICAL);

        final LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        assert inflater != null;

        inflater.inflate(R.layout.view_telegram_chart, this, true);

        titleView = (TextView) findViewById(R.id.title);
        mainChartView = (MainChartView) findViewById(R.id.main_chart);
        previewChartView = (PreviewChartView) findViewById(R.id.preview_chart);
        lineNamesView = (LineNameListView) findViewById(R.id.line_names);

        previewChartView.setOnChangeListener(previewChartChangeListener);
        lineNamesView.setOnCheckedChangeListener(lineNamesCheckedChangeListener);

        final DisplayMetrics dm = context.getResources().getDisplayMetrics();
        final float defTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, DEF_TEXT_SIZE_SP, dm);
        final float defAxisTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, DEF_AXIS_TEXT_SIZE_SP, dm);

        TypedArray ta = context.getTheme().obtainStyledAttributes(attrs, R.styleable.TelegramChartView, 0, 0);
        try {
            CharSequence title = ta.getText(R.styleable.TelegramChartView_title);
            titleView.setText(title.toString());

            float px = ta.getDimension(R.styleable.TelegramChartView_title_text_size, defTextSize);
            setTitleTextSize(px);

            px = ta.getDimension(R.styleable.TelegramChartView_axis_text_size, defAxisTextSize);
            setAxisTextSize(px);

            px = ta.getDimension(R.styleable.TelegramChartView_line_name_text_size, defTextSize);
            setLineNameTextSize(px);
        } finally {
            ta.recycle();
        }
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

    public void setTitleTextSize(float px) {
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
    }

    public void setAxisTextSize(float px) {
        mainChartView.setAxisTextSize(px);
    }

    public void setLineNameTextSize(float px) {
        lineNamesView.setTextSize(px);
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
