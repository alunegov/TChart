package com.github.alunegov.tchart;

import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
//import android.util.Log;
import android.util.Log;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.jetbrains.annotations.NotNull;

public class TelegramChartView extends LinearLayout {
    private static final int DEF_TEXT_SIZE_SP = 17;
    private static final int DEF_AXIS_TEXT_SIZE_SP = 12;

    private TextView titleView;
    private TextView xRangeView;
    private MainChartView mainChartView;
    private PreviewChartView previewChartView;
    private LineNameListView lineNamesView;

    private MainChartView.XAxisConverter xRangeTextConverter;
    private final @NotNull long[] tmpXRange = new long[2];

    private final @NotNull ValueAnimator zoneChangeAnimator = new ValueAnimator();
    private boolean isPendingZoneChangeAnimation = false;
    private float zoneLeftValuePending, zoneRightValuePending;

    private final @NotNull ValueAnimator lineVisibilityAnimator = new ValueAnimator();
    private int lineVisibilityAnimation_lineIndex;
    private boolean lineVisibilityAnimation_exceptLine;

    private ChartInputDataStats inputDataStats;

    private final Handler h = new Handler();

    private int ymin_main1, ymax_main1, ymin_main2, ymax_main2, ymin_main3, ymax_main3;

    public TelegramChartView(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        setOrientation(VERTICAL);

        final LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (inflater == null) throw new AssertionError();

        inflater.inflate(R.layout.view_telegram_chart, this, true);

        titleView = (TextView) findViewById(R.id.title);
        xRangeView = (TextView) findViewById(R.id.x_range);
        mainChartView = (MainChartView) findViewById(R.id.main_chart);
        previewChartView = (PreviewChartView) findViewById(R.id.preview_chart);
        lineNamesView = (LineNameListView) findViewById(R.id.line_names);

        final String xRangeDateFormatTemplate = ChartUtils.getXRangeDateFormatTemplate(context);
        xRangeTextConverter = new MainChartView.XAxisConverter(xRangeDateFormatTemplate);

        previewChartView.setOnChangeListener(previewChartChangeListener);
        lineNamesView.setOnChangeListener(lineNamesChangeListener);

        //zoneChangeAnimator.setInterpolator(new FastOutSlowInInterpolator());
        //zoneChangeAnimator.setDuration(200);
        zoneChangeAnimator.addUpdateListener(zoneChangeAnimatorUpdateListener);

        //lineVisibilityAnimator.setInterpolator(new FastOutSlowInInterpolator());
        //lineVisibilityAnimator.setDuration(200);
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
            //mainChartView.setXRange(zoneLeftValue, zoneRightValue);

            zoneChangeAnimator.cancel();

            //Log.v("TCV", String.format("left = %f, right = %f, swing = %f onZoneChanged", zoneLeftValue, zoneRightValue, zoneRightValue - zoneLeftValue));

            if (zoneChangeAnimator.isStarted()) {
                //Log.v("TCV", "pending");
                zoneLeftValuePending = zoneLeftValue;
                zoneRightValuePending = zoneRightValue;
                isPendingZoneChangeAnimation = true;
                return;
            }

            //mainChartView.setXRange(zoneLeftValue, zoneRightValue);
            //invalidate();

            startZoneChangeAnimation(zoneLeftValue, zoneRightValue, true);

            // AnimatorListener.onAnimationEnd not working right - sometimes it not fires.
            // https://stackoverflow.com/a/18683419/2968990
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    //Log.v("TCV", String.format("run gotPending = %s", isPendingZoneChangeAnimation));
                    if (isPendingZoneChangeAnimation) {
                        isPendingZoneChangeAnimation = false;
                        startZoneChangeAnimation(zoneLeftValuePending, zoneRightValuePending, true);

                        h.postDelayed(this, zoneChangeAnimator.getDuration());
                    }
                }
            }, zoneChangeAnimator.getDuration());
        }
    };

    private final int[] tmpStartYRange = new int[4];
    private final int[] tmpStopYRange = new int[4];

    private void startZoneChangeAnimation(float zoneLeftValue, float zoneRightValue, boolean b) {
        final PropertyValuesHolder xl, xr;
        if (b) {
            final float[] startXRange = new float[2];
            mainChartView.getXRange(startXRange);
            xl = PropertyValuesHolder.ofFloat("xl", startXRange[0], zoneLeftValue);
            xr = PropertyValuesHolder.ofFloat("xr", startXRange[1], zoneRightValue);
        } else {
            xl = PropertyValuesHolder.ofFloat("xl", zoneLeftValue, zoneLeftValue);
            xr = PropertyValuesHolder.ofFloat("xr", zoneRightValue, zoneRightValue);
        }
        //Log.v("TCV", String.format("left = %f, right = %f, swing = %f startZoneChangeAnimation", zoneLeftValue, zoneRightValue, zoneRightValue - zoneLeftValue));

        mainChartView.calcAnimationRanges(zoneLeftValue, zoneRightValue, tmpStartYRange, tmpStopYRange);
        final PropertyValuesHolder yLeftMin = PropertyValuesHolder.ofInt("yLeftMin", tmpStartYRange[0], tmpStopYRange[0]);
        final PropertyValuesHolder yLeftMax = PropertyValuesHolder.ofInt("yLeftMax", tmpStartYRange[1], tmpStopYRange[1]);
        final PropertyValuesHolder yRightMin = PropertyValuesHolder.ofInt("yRightMin", tmpStartYRange[2], tmpStopYRange[2]);
        final PropertyValuesHolder yRightMax = PropertyValuesHolder.ofInt("yRightMax", tmpStartYRange[3], tmpStopYRange[3]);

        zoneChangeAnimator.setValues(xl, xr, yLeftMin, yLeftMax, yRightMin, yRightMax);
        zoneChangeAnimator.start();
    }

    private final @NotNull ValueAnimator.AnimatorUpdateListener zoneChangeAnimatorUpdateListener = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            final float xl = (float) animation.getAnimatedValue("xl");
            final float xr = (float) animation.getAnimatedValue("xr");
            final int yLeftMin = (int) animation.getAnimatedValue("yLeftMin");
            final int yLeftMax = (int) animation.getAnimatedValue("yLeftMax");
            final int yRightMin = (int) animation.getAnimatedValue("yRightMin");
            final int yRightMax = (int) animation.getAnimatedValue("yRightMax");
            //Log.v("TCV", String.format("left = %f, right = %f, swing = %f setXYRange at %d", xl, xr, xr - xl, animation.getCurrentPlayTime()));

            mainChartView.setXYRange(xl, xr, yLeftMin, yLeftMax, yRightMin, yRightMax);

            mainChartView.getXRange(tmpXRange);
            updateXRangeText(tmpXRange[0], tmpXRange[1]);
        }
    };

    private final LineNameListView.OnChangeListener lineNamesChangeListener = new LineNameListView.OnChangeListener() {
        @Override
        public void onCheckedChange(int lineIndex, boolean isChecked) {
            startLineVisibilityAnimation(lineIndex, false, isChecked);
        }

        @Override
        public void onLongClick(int lineIndex) {
            startLineVisibilityAnimation(lineIndex, true, true);
        }
    };

    private void startLineVisibilityAnimation(int lineIndex, boolean exceptLine, boolean isChecked) {
        //mainChartView.updateLineVisibility(lineIndex, isChecked, true);
        //previewChartView.updateLineVisibility(lineIndex, isChecked, true);

        lineVisibilityAnimator.cancel();

        final int startLineVisibilityState;
        if (exceptLine) {
            final int[] linesVisibilityState = inputDataStats.getLinesVisibilityState();
            startLineVisibilityState = linesVisibilityState[lineIndex];
        } else {
            startLineVisibilityState = isChecked ? ChartInputDataStats.VISIBILITY_STATE_OFF : ChartInputDataStats.VISIBILITY_STATE_ON;
        }
        final int stopLineVisibilityState = isChecked ? ChartInputDataStats.VISIBILITY_STATE_ON : ChartInputDataStats.VISIBILITY_STATE_OFF;

        mainChartView.calcAnimationRanges(lineIndex, exceptLine, stopLineVisibilityState, tmpStartYRange, tmpStopYRange);
        final PropertyValuesHolder yLeftMin_main = PropertyValuesHolder.ofInt("yLeftMin_main", tmpStartYRange[0], tmpStopYRange[0]);
        final PropertyValuesHolder yLeftMax_main = PropertyValuesHolder.ofInt("yLeftMax_main", tmpStartYRange[1], tmpStopYRange[1]);
        final PropertyValuesHolder yRightMin_main = PropertyValuesHolder.ofInt("yRightMin_main", tmpStartYRange[2], tmpStopYRange[2]);
        final PropertyValuesHolder yRightMax_main = PropertyValuesHolder.ofInt("yRightMax_main", tmpStartYRange[3], tmpStopYRange[3]);

        previewChartView.calcAnimationRanges(lineIndex, exceptLine, stopLineVisibilityState, tmpStartYRange, tmpStopYRange);
        final PropertyValuesHolder yLeftMin_preview = PropertyValuesHolder.ofInt("yLeftMin_preview", tmpStartYRange[0], tmpStopYRange[0]);
        final PropertyValuesHolder yLeftMax_preview = PropertyValuesHolder.ofInt("yLeftMax_preview", tmpStartYRange[1], tmpStopYRange[1]);
        final PropertyValuesHolder yRightMin_preview = PropertyValuesHolder.ofInt("yRightMin_preview", tmpStartYRange[2], tmpStopYRange[2]);
        final PropertyValuesHolder yRightMax_preview = PropertyValuesHolder.ofInt("yRightMax_preview", tmpStartYRange[3], tmpStopYRange[3]);

        final PropertyValuesHolder lineVisibilityState = PropertyValuesHolder.ofInt("lineVisibilityState", startLineVisibilityState, stopLineVisibilityState);

        // TODO: переход из/в состояние без графиков
        // TODO: если нет изменения по Y, не нужно вызывать setYRange
        //   (startYRange[0] == stopYRange[0]) && (startYRange[1] == stopYRange[1])
        lineVisibilityAnimation_lineIndex = lineIndex;
        lineVisibilityAnimation_exceptLine = exceptLine;

//            t = SystemClock.elapsedRealtime();

        lineVisibilityAnimator.setValues(yLeftMin_main, yLeftMax_main, yRightMin_main, yRightMax_main, yLeftMin_preview,
                yLeftMax_preview, yRightMin_preview, yRightMax_preview, lineVisibilityState);
        lineVisibilityAnimator.start();

                ymin_main1 = tmpStartYRange[0];
                ymax_main1 = tmpStartYRange[1];
                ymin_main2 = tmpStopYRange[0];
                ymax_main2 = tmpStopYRange[1];
                ymin_main3 = (tmpStopYRange[0] - tmpStartYRange[0]) / 40;
                ymax_main3 = (tmpStopYRange[1] - tmpStartYRange[1]) / 40;

                prevFrameTimeNanos = 0;
                bb = 0;

//                Choreographer.getInstance().postFrameCallbackDelayed(frameCallback, 0);

/*                postOnAnimationDelayed(new Runnable() {
                    @Override
                    public void run() {
                        boolean cond;
                        if (ymax_main3 > 0) {
                            cond = ymin_main1 < ymin_main2 || ymax_main1 < ymax_main2;
                        } else {
                            cond = ymin_main1 > ymin_main2 || ymax_main1 > ymax_main2;
                        }
                        if (cond) {
                            ymin_main1 += ymin_main3;
                            ymax_main1 += ymax_main3;

                            mainChartView.setYRange(ymin_main1, ymax_main1, true);
                            previewChartView.setYRange(ymin_main1, ymax_main1, true);

                            removeCallbacks(this);
                            postOnAnimationDelayed(this, 16);
                        } else {
                            previewChartView.useCachedLines();
                        }
                    }
                }, 16);*/
    }

    private static final long NanoSecPerMSec = 1000000L;
    private long prevFrameTimeNanos;
    private int bb = 0;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            //Log.d("TCV", String.format("Choreographer.doFrame at %d", frameTimeNanos));
            if (bb++ < 1) {
                Choreographer.getInstance().postFrameCallbackDelayed(frameCallback, 0);
                return;
            } else {
                bb = 0;
            }

            if (prevFrameTimeNanos != 0) {
                long elapsed = (frameTimeNanos - prevFrameTimeNanos) / NanoSecPerMSec;
                if (elapsed > 17) {
                    Log.d("TCV", String.format("elapsed ms %d", elapsed));
                }
                if (elapsed < 16) {
                    //Log.d("TCV", "Choreographer.doFrame in 16 ms");
                    Choreographer.getInstance().postFrameCallbackDelayed(frameCallback, 0);
                    return;
                }
            }
            prevFrameTimeNanos = frameTimeNanos;

            boolean cond;
            if (ymax_main3 > 0) {
                cond = ymin_main1 < ymin_main2 || ymax_main1 < ymax_main2;
            } else {
                cond = ymin_main1 > ymin_main2 || ymax_main1 > ymax_main2;
            }
            if (cond) {
                ymin_main1 += ymin_main3;
                ymax_main1 += ymax_main3;

                mainChartView.setYRange(ymin_main1, ymax_main1, 0, 0, true);
                previewChartView.setYRange(ymin_main1, ymax_main1, 0, 0, true);

                Choreographer.getInstance().postFrameCallbackDelayed(frameCallback, 0);
            } else {
//                previewChartView.useCachedLines();
            }
        }
    };

    private final @NotNull ValueAnimator.AnimatorUpdateListener lineVisibilityAnimatorUpdateListener = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
/*            if ((t + 15) > SystemClock.elapsedRealtime()) {
                Log.d("TCV", String.format("skipped at %d < %d", SystemClock.elapsedRealtime(), t + 15));
                return;
            }
            t = SystemClock.elapsedRealtime();*/

            final int yLeftMin_main = (int) animation.getAnimatedValue("yLeftMin_main");
            final int yLeftMax_main = (int) animation.getAnimatedValue("yLeftMax_main");
            final int yRightMin_main = (int) animation.getAnimatedValue("yRightMin_main");
            final int yRightMax_main = (int) animation.getAnimatedValue("yRightMax_main");

            final int yLeftMin_preview = (int) animation.getAnimatedValue("yLeftMin_preview");
            final int yLeftMax_preview = (int) animation.getAnimatedValue("yLeftMax_preview");
            final int yRightMin_preview = (int) animation.getAnimatedValue("yRightMin_preview");
            final int yRightMax_preview = (int) animation.getAnimatedValue("yRightMax_preview");

            final int lineVisibilityState = (int) animation.getAnimatedValue("lineVisibilityState");
            //Log.d("TCV", String.format("ymin_main = %d, ymax_main = %d, lineVisibilityState = %d", ymin_main, ymax_main, lineVisibilityState));

            // порядок методов setYRange и updateLineVisibility важен, п.ч. setYRange сбрасывает кэш-картинку, а
            // previewChartView восстанавливает её по завершению анимации (lineVisibilityState) - сейчас это выключено.
            // НО при обновлении ChartDrawData.updateLineVisibility пересчитываются y (updateYRange), а нам это не
            // нужно - мы сами анимируем изменение.

            inputDataStats.updateLineVisibility(lineVisibilityAnimation_lineIndex, lineVisibilityAnimation_exceptLine,
                    lineVisibilityState);

            mainChartView.updateLineVisibility(lineVisibilityAnimation_lineIndex, lineVisibilityAnimation_exceptLine,
                    lineVisibilityState, false, false);
            previewChartView.updateLineVisibility(lineVisibilityAnimation_lineIndex, lineVisibilityAnimation_exceptLine,
                    lineVisibilityState, false, false);

            mainChartView.setYRange(yLeftMin_main, yLeftMax_main, yRightMin_main, yRightMax_main, true);
            previewChartView.setYRange(yLeftMin_preview, yLeftMax_preview, yRightMin_preview, yRightMax_preview, true);
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
        inputDataStats = new ChartInputDataStats(inputData);

        mainChartView.setInputData(inputData, inputDataStats);
        previewChartView.setInputData(inputData, inputDataStats);

        if (inputData.LinesValues.length > 1) {
            final LineName[] linesNames = new LineName[inputData.LinesNames.length];
            for (int i = 0; i < inputData.LinesNames.length; i++) {
                linesNames[i] = new LineName(inputData.LinesNames[i], inputData.LinesColors[i]);
            }

            lineNamesView.setLineNames(linesNames);
        } else {
            lineNamesView.setVisibility(GONE);
        }

        final float[] zone = new float[2];
        previewChartView.getZone(zone);
        mainChartView.setXRange(zone[0], zone[1]);

        mainChartView.getXRange(tmpXRange);
        updateXRangeText(tmpXRange[0], tmpXRange[1]);
    }

    private void updateXRangeText(long xLeft, long xRight) {
        final String text = String.format("%s - %s", xRangeTextConverter.toText(xLeft), xRangeTextConverter.toText(xRight));
        xRangeView.setText(text);
    }
}
