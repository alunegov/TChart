package com.github.alunegov.tchart;

import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.jetbrains.annotations.NotNull;

public class TelegramChartView extends LinearLayout {
    private static final int DEF_TEXT_SIZE_SP = 17;
    private static final int DEF_AXIS_TEXT_SIZE_SP = 12;

    private TextView titleView;
    private MainChartView mainChartView;
    private PreviewChartView previewChartView;
    private LineNameListView lineNamesView;

    private final @NotNull ValueAnimator zoneChangeAnimator = new ValueAnimator();
    private boolean isPendingZoneChangeAnimation = false;
    private float zoneLeftValuePending, zoneRightValuePending;

    private final @NotNull ValueAnimator lineVisibilityAnimator = new ValueAnimator();
    private int lineVisibilityAnimation_lineIndex;
    private boolean lineVisibilityAnimation_isChecked;
    private boolean lineVisibilityAnimation_done = false;

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

        zoneChangeAnimator.setInterpolator(new LinearInterpolator());
        zoneChangeAnimator.setDuration(100);
        zoneChangeAnimator.addUpdateListener(zoneChangeAnimatorUpdateListener);

        lineVisibilityAnimator.setInterpolator(new LinearInterpolator());
        lineVisibilityAnimator.setDuration(200);
        lineVisibilityAnimator.addUpdateListener(lineVisibilityAnimatorUpdateListener);

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
//            mainChartView.setXRange(zoneLeftValue, zoneRightValue);

            //zoneChangeAnimator.cancel();

            //Log.v("TCV", String.format("left = %f, right = %f, swing = %f onZoneChanged", zoneLeftValue, zoneRightValue, zoneRightValue - zoneLeftValue));

            if (zoneChangeAnimator.isStarted()) {
                //Log.v("TCV", "pending");
                zoneLeftValuePending = zoneLeftValue;
                zoneRightValuePending = zoneRightValue;
                isPendingZoneChangeAnimation = true;
                return;
            }

            startZoneChangeAnimation(zoneLeftValue, zoneRightValue);

            // AnimatorListener.onAnimationEnd not working right - sometimes it not fires.
            // https://stackoverflow.com/a/18683419/2968990
            final Handler h = new Handler();
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    //Log.v("TCV", String.format("run gotPending = %s", isPendingZoneChangeAnimation));
                    if (isPendingZoneChangeAnimation) {
                        isPendingZoneChangeAnimation = false;
                        startZoneChangeAnimation(zoneLeftValuePending, zoneRightValuePending);

                        h.postDelayed(this, zoneChangeAnimator.getDuration());
                    }
                }
            }, zoneChangeAnimator.getDuration());
        }
    };

    private void startZoneChangeAnimation(float zoneLeftValue, float zoneRightValue) {
        final float[] startXRange = new float[2];
        mainChartView.getXRange(startXRange);
        final PropertyValuesHolder xl = PropertyValuesHolder.ofFloat("xl", startXRange[0], zoneLeftValue);
        final PropertyValuesHolder xr = PropertyValuesHolder.ofFloat("xr", startXRange[1], zoneRightValue);
        //Log.v("TCV", String.format("left = %f, right = %f, swing = %f startZoneChangeAnimation", zoneLeftValue, zoneRightValue, zoneRightValue - zoneLeftValue));

        final int[] startYRange = new int[2];
        final int[] stopYRange = new int[2];
        mainChartView.calcAnimationRanges(zoneLeftValue, zoneRightValue, startYRange, stopYRange);
        final PropertyValuesHolder ymin = PropertyValuesHolder.ofInt("ymin", startYRange[0], stopYRange[0]);
        final PropertyValuesHolder ymax = PropertyValuesHolder.ofInt("ymax", startYRange[1], stopYRange[1]);

        zoneChangeAnimator.setValues(xl, xr, ymin, ymax);
        zoneChangeAnimator.start();
    }

    private final @NotNull ValueAnimator.AnimatorUpdateListener zoneChangeAnimatorUpdateListener = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            final float xl = (float) animation.getAnimatedValue("xl");
            final float xr = (float) animation.getAnimatedValue("xr");
            final int ymin = (int) animation.getAnimatedValue("ymin");
            final int ymax = (int) animation.getAnimatedValue("ymax");
            //Log.v("TCV", String.format("left = %f, right = %f, swing = %f setXYRange", xl, xr, xr - xl));

            mainChartView.setXYRange(xl, xr, ymin, ymax);
        }
    };

    // yMax + (int) (0.05 * (yMax - yMin)) при условии, что yMin == 0 (YMinMode.ZERO)
    private static final int MAGIC_NO_SIGNALS = 2040109466;

    private final LineNameListView.OnCheckedChangeListener lineNamesCheckedChangeListener = new LineNameListView.OnCheckedChangeListener() {
        @Override
        public void onCheckedChange(int lineIndex, boolean isChecked) {
//            mainChartView.updateLineVisibility(lineIndex, isChecked, true);
//            previewChartView.updateLineVisibility(lineIndex, isChecked, true);

            lineVisibilityAnimator.cancel();

            final int[] startYRange = new int[2];
            final int[] stopYRange = new int[2];

            mainChartView.calcAnimationRanges(lineIndex, isChecked, startYRange, stopYRange);
            final PropertyValuesHolder ymin_main = PropertyValuesHolder.ofInt("ymin_main", startYRange[0], stopYRange[0]);
            final PropertyValuesHolder ymax_main = PropertyValuesHolder.ofInt("ymax_main", startYRange[1], stopYRange[1]);

            previewChartView.calcAnimationRanges(lineIndex, isChecked, startYRange, stopYRange);
            final PropertyValuesHolder ymin_preview = PropertyValuesHolder.ofInt("ymin_preview", startYRange[0], stopYRange[0]);
            final PropertyValuesHolder ymax_preview = PropertyValuesHolder.ofInt("ymax_preview", startYRange[1], stopYRange[1]);

            if ((startYRange[1] == MAGIC_NO_SIGNALS) || (stopYRange[1] == MAGIC_NO_SIGNALS)) {
                // переход из/в состояние без графиков
                mainChartView.updateLineVisibility(lineIndex, isChecked, true);
                previewChartView.updateLineVisibility(lineIndex, isChecked, true);
            } else if ((startYRange[0] == stopYRange[0]) && (startYRange[1] == stopYRange[1])) {
                // нет изменения по Y
                mainChartView.updateLineVisibility(lineIndex, isChecked, true);
                previewChartView.updateLineVisibility(lineIndex, isChecked, true);
            } else {
                lineVisibilityAnimation_lineIndex = lineIndex;
                lineVisibilityAnimation_isChecked = isChecked;
                lineVisibilityAnimation_done = false;

                lineVisibilityAnimator.setValues(ymin_main, ymax_main, ymin_preview, ymax_preview);
                lineVisibilityAnimator.start();
            }
        }
    };

    private final @NotNull ValueAnimator.AnimatorUpdateListener lineVisibilityAnimatorUpdateListener = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            final int ymin_main = (int) animation.getAnimatedValue("ymin_main");
            final int ymax_main = (int) animation.getAnimatedValue("ymax_main");
            final int ymin_preview = (int) animation.getAnimatedValue("ymin_preview");
            final int ymax_preview = (int) animation.getAnimatedValue("ymax_preview");

            if (!lineVisibilityAnimation_done && (animation.getCurrentPlayTime() > (animation.getDuration() * 3 / 4))) {
                mainChartView.updateLineVisibility(lineVisibilityAnimation_lineIndex, lineVisibilityAnimation_isChecked, false);
                previewChartView.updateLineVisibility(lineVisibilityAnimation_lineIndex, lineVisibilityAnimation_isChecked, false);
                lineVisibilityAnimation_done = true;
            }

            mainChartView.setYRange(ymin_main, ymax_main);
            previewChartView.setYRange(ymin_preview, ymax_preview);
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

        final float[] zone = new float[2];
        previewChartView.getZone(zone);
        mainChartView.setXRange(zone[0], zone[1]);
    }
}
